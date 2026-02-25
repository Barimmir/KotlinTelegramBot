import org.example.DatabaseUserDictionary
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class DatabaseSecurityTest {
    private val testDbUrl = "jdbc:sqlite:test_security_${System.currentTimeMillis()}.db"
    private val testChatId = 228322228L
    private lateinit var dictionary: DatabaseUserDictionary

    @Before
    fun setUp() {
        cleanupDatabaseFile()
        DriverManager.getConnection(testDbUrl).use { conn ->
            conn.createStatement().executeUpdate("PRAGMA foreign_keys = ON")
            conn.createStatement().executeUpdate(
                """
                CREATE TABLE words (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    text VARCHAR UNIQUE,
                    translate VARCHAR
                )
                """
            )
            conn.createStatement().executeUpdate(
                """
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    chat_id INTEGER UNIQUE
                )
                """
            )
            conn.createStatement().executeUpdate(
                """
                CREATE TABLE user_answers (
                    user_id INTEGER,
                    word_id INTEGER,
                    correct_answer_count INTEGER DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id, word_id),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
                )
                """
            )
        }
        dictionary = DatabaseUserDictionary(testChatId, dbUrl = testDbUrl)
        dictionary.importWords(
            listOf(
                "hello" to "привет",
                "dog" to "собака",
                "cat" to "кошка"
            )
        )
    }

    private fun cleanupDatabaseFile() {
        try {
            val dbFile = File(testDbUrl.removePrefix("jdbc:sqlite:"))
            if (dbFile.exists()) {
                dbFile.delete()
            }
        } catch (e: Exception) {
            println(e.message)
        }
    }

    @Test
    fun `test SQL injection in importWords`() {
        val initialSize = dictionary.getSize()
        assertEquals(3, initialSize)
        val maliciousWords = listOf(
            "'; DROP TABLE words; --" to "перевод",
            "hello" to "'; DROP TABLE users; --",
            "' UNION SELECT * FROM users --" to "world",
            "test" to "'; DELETE FROM words WHERE 1=1; --",
            "<script>alert('xss')</script>" to "xss",
            "normal" to "'; INSERT INTO users (chat_id) VALUES (666) --"
        )
        dictionary.importWords(maliciousWords)
        assertEquals(initialSize, dictionary.getSize())
        DriverManager.getConnection(testDbUrl).use { conn ->
            val query = conn.prepareStatement(
                "SELECT COUNT(*) as cnt FROM words WHERE text LIKE ? OR translate LIKE ?"
            )
            query.setString(1, "%;%")
            query.setString(2, "%;%")
            val rs = query.executeQuery()
            rs.next()
            assertEquals(0, rs.getInt("cnt"))
        }
    }

    @Test
    fun `test resetProgress with malicious input`() {
        assertEquals(3, dictionary.getSize())
        dictionary.setCorrectAnswersCount("hello", 3)
        dictionary.setCorrectAnswersCount("dog", 1)
        dictionary.setCorrectAnswersCount("cat", 0)
        DriverManager.getConnection(testDbUrl).use { conn ->
            val rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) as cnt FROM user_answers WHERE correct_answer_count >= 3"
            )
            rs.next()
            assertEquals(1, rs.getInt("cnt"))
        }
        assertEquals(1, dictionary.getNumOfLearnedWords())
        dictionary.resetUserProgress()
        assertEquals(0, dictionary.getNumOfLearnedWords())
        assertEquals(3, dictionary.getSize())
    }

    @Test
    fun `test getUnlearnedWords with malicious input`() {
        assertEquals(3, dictionary.getSize())
        dictionary.setCorrectAnswersCount("hello", 3)
        dictionary.setCorrectAnswersCount("dog", 1)
        dictionary.setCorrectAnswersCount("cat", 0)
        val result = dictionary.getUnlearnedWords()
        assertEquals(2, result.size)
        assertTrue(result.any { it.original == "dog" })
        assertTrue(result.any { it.original == "cat" })
        assertFalse(result.any { it.original == "hello" })
        assertEquals(3, dictionary.getSize())
    }
}