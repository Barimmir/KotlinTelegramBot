package org.example

import java.sql.DriverManager
import java.sql.SQLException

class DatabaseUserDictionary(
    private val chatId: Long,
    private val learningThreshold: Int = NEED_COUNT_TO_LEARN,
    private val dbUrl: String = "jdbc:sqlite:data.db",
) : IUserDictionary {
    private val userId: Int = run {
        val conn = DriverManager.getConnection(dbUrl)
        conn.use { connection ->
            val insertUser = connection.prepareStatement(
                "INSERT OR IGNORE INTO users (chat_id) VALUES (?)"
            )
            insertUser.setLong(1, chatId)
            insertUser.executeUpdate()
            val query = connection.prepareStatement("SELECT id FROM users WHERE chat_id = ?")
            query.setLong(1, chatId)
            val rs = query.executeQuery()
            if (rs.next()) {
                rs.getInt("id")
            } else {
                throw SQLException("Chat_id $chatId не найден")
            }
        }
    }

    private val dangerousPatterns = listOf(
        "'--", "';", "' OR", "' AND", "' UNION", "' SELECT",
        "' DELETE", "' DROP", "' INSERT", "' UPDATE", "1=1",
        "--", "/*", "*/", "xp_cmdshell", "exec", "execute"
    )

    private fun validateWord(word: String): String {
        val trimmed = word.trim()
        if (trimmed.length > 50) {
            throw IllegalArgumentException("Слово слишком длинное: ${trimmed.length} символов")
        }
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Слово не может быть пустым")
        }
        val allowedPattern = Regex("^[a-zA-Zа-яА-Я0-9\\s]+(?:[-'][a-zA-Zа-яА-Я0-9\\s]+)*$")
        if (!allowedPattern.matches(trimmed)) {
            logSuspiciousActivity(trimmed, "validateWord")
            throw IllegalArgumentException("Недопустимые символы в слове: $trimmed")
        }
        val lowerWord = trimmed.lowercase()
        dangerousPatterns.forEach { pattern ->
            if (lowerWord.contains(pattern)) {
                logSuspiciousActivity(trimmed, "validateWord-dangerous")
                throw IllegalArgumentException("Обнаружено опасное слово")
            }
        }
        return trimmed
    }

    private fun logSuspiciousActivity(input: String, context: String) {
        val containsSuspicious = dangerousPatterns.any {
            input.lowercase().contains(it)
        }
        if (containsSuspicious) {
            println("Что-то странное вводит это чипушила")
        }
    }

    override fun getNumOfLearnedWords(): Int {
        DriverManager.getConnection(dbUrl).use { connection ->
            val query = connection.prepareStatement(
                """
                SELECT COUNT(*) as count 
                FROM user_answers 
                WHERE user_id = ? AND correct_answer_count >= ?
                """
            )
            query.setInt(1, userId)
            query.setInt(2, learningThreshold)
            val rs = query.executeQuery()
            return if (rs.next()) rs.getInt("count") else 0
        }
    }

    override fun getSize(): Int {
        DriverManager.getConnection(dbUrl).use { connection ->
            val query = connection.prepareStatement("SELECT COUNT(*) as count FROM words")
            val rs = query.executeQuery()
            return if (rs.next()) rs.getInt("count") else 0
        }
    }

    override fun getLearnedWords(): List<Word> {
        DriverManager.getConnection(dbUrl).use { connection ->
            val query = connection.prepareStatement(
                """
                SELECT w.text, w.translate, ua.correct_answer_count
                FROM words w
                JOIN user_answers ua ON w.id = ua.word_id
                WHERE ua.user_id = ? AND ua.correct_answer_count >= ?
                """
            )
            query.setInt(1, userId)
            query.setInt(2, learningThreshold)
            val rs = query.executeQuery()
            val words = mutableListOf<Word>()
            while (rs.next()) {
                words.add(
                    Word(
                        original = rs.getString("text"),
                        translation = rs.getString("translate"),
                        correctAnswersCount = rs.getInt("correct_answer_count")
                    )
                )
            }
            return words
        }
    }

    override fun getUnlearnedWords(): List<Word> {
        DriverManager.getConnection(dbUrl).use { connection ->
            val query = connection.prepareStatement(
                """
                SELECT w.text, w.translate, COALESCE(ua.correct_answer_count, 0) as correct_count
                FROM words w
                LEFT JOIN user_answers ua ON w.id = ua.word_id AND ua.user_id = ?
                WHERE COALESCE(ua.correct_answer_count, 0) < ?
                """
            )
            query.setInt(1, userId)
            query.setInt(2, learningThreshold)
            val rs = query.executeQuery()
            val words = mutableListOf<Word>()
            while (rs.next()) {
                words.add(
                    Word(
                        original = rs.getString("text"),
                        translation = rs.getString("translate"),
                        correctAnswersCount = rs.getInt("correct_count")
                    )
                )
            }
            return words
        }
    }

    override fun setCorrectAnswersCount(word: String, correctAnswersCount: Int) {
        logSuspiciousActivity(word, "setCorrectAnswersCount")
        val validatedWord = validateWord(word)
        DriverManager.getConnection(dbUrl).use { connection ->
            val wordQuery = connection.prepareStatement("SELECT id FROM words WHERE text = ?")
            wordQuery.setString(1, validatedWord)
            val wordRs = wordQuery.executeQuery()
            if (wordRs.next()) {
                val wordId = wordRs.getInt("id")
                val upsertQuery = connection.prepareStatement(
                    """
                    INSERT INTO user_answers (user_id, word_id, correct_answer_count, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(user_id, word_id) 
                    DO UPDATE SET correct_answer_count = excluded.correct_answer_count, 
                                  updated_at = CURRENT_TIMESTAMP
                    """
                )
                upsertQuery.setInt(1, userId)
                upsertQuery.setInt(2, wordId)
                upsertQuery.setInt(3, correctAnswersCount)
                upsertQuery.executeUpdate()
            }
        }
    }

    override fun importWords(words: List<Pair<String, String>>) {
        if (words.isEmpty()) return
        val validWords = words.filter { (original, translation) ->
            try {
                validateWord(original)
                validateWord(translation)
                true
            } catch (e: IllegalArgumentException) {
                logSuspiciousActivity(original, "importWords-filtered")
                logSuspiciousActivity(translation, "importWords-filtered")
                false
            }
        }
        if (validWords.isEmpty()) return
        DriverManager.getConnection(dbUrl).use { connection ->
            try {
                connection.autoCommit = false
                val insertStatement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO words (text, translate) VALUES (?, ?)"
                )
                for ((original, translation) in validWords) {
                    insertStatement.setString(1, original)
                    insertStatement.setString(2, translation)
                    insertStatement.addBatch()
                }
                insertStatement.executeBatch()
                connection.commit()
            } catch (e: SQLException) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun resetUserProgress() {
        DriverManager.getConnection(dbUrl).use { connection ->
            val deleteQuery = connection.prepareStatement("DELETE FROM user_answers WHERE user_id = ?")
            deleteQuery.setInt(1, userId)
            deleteQuery.executeUpdate()
        }
    }
}