package org.example

import java.sql.DriverManager
import java.sql.SQLException

class DatabaseUserDictionary(
    private val chatId: Long,
    private val learningThreshold: Int = NEED_COUNT_TO_LEARN,
    private val dbUrl: String = "jdbc:sqlite:data.db",
) : IUserDictionary {

    private val userId: Int by lazy {
        ensureUserExists()
        fetchUserIdFromDb()
    }

    private fun ensureUserExists() {
        DriverManager.getConnection(dbUrl).use { connection ->
            val insertUser = connection.prepareStatement(
                "INSERT OR IGNORE INTO users (chat_id) VALUES (?)"
            )
            insertUser.setLong(1, chatId)
            insertUser.executeUpdate()
        }
    }

    private fun fetchUserIdFromDb(): Int {
        DriverManager.getConnection(dbUrl).use { connection ->
            val query = connection.prepareStatement("SELECT id FROM users WHERE chat_id = ?")
            query.setLong(1, chatId)
            val rs = query.executeQuery()
            if (rs.next()) {
                return rs.getInt("id")
            }
            throw SQLException("Chat_id $chatId не найден")
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
            val rs = connection.createStatement().executeQuery("SELECT COUNT(*) as count FROM words")
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
        DriverManager.getConnection(dbUrl).use { connection ->
            val wordQuery = connection.prepareStatement("SELECT id FROM words WHERE text = ?")
            wordQuery.setString(1, word)
            val wordRs = wordQuery.executeQuery()
            if (wordRs.next()) {
                val wordId = wordRs.getInt("id")
                val updateQuery = connection.prepareStatement(
                    """
                    INSERT INTO user_answers (user_id, word_id, correct_answer_count, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(user_id, word_id) 
                    DO UPDATE SET correct_answer_count = ?, updated_at = CURRENT_TIMESTAMP
                    """
                )
                updateQuery.setInt(1, userId)
                updateQuery.setInt(2, wordId)
                updateQuery.setInt(3, correctAnswersCount)
                updateQuery.setInt(4, correctAnswersCount)
                updateQuery.executeUpdate()
            }
        }
    }

    override fun importWords(words: List<Pair<String, String>>) {
        if (words.isEmpty()) return
        DriverManager.getConnection(dbUrl).use { connection ->
            try {
                connection.autoCommit = false
                val insertStatement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO words (text, translate) VALUES (?, ?)"
                )
                for ((original, translation) in words) {
                    insertStatement.setString(1, original)
                    insertStatement.setString(2, translation)
                    insertStatement.addBatch()
                }
                insertStatement.executeBatch()
                connection.commit()
            } catch (e: SQLException) {
                connection.rollback()
                e.printStackTrace()
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
