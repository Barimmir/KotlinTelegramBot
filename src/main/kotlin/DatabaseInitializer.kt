package org.example

import java.sql.DriverManager

object DatabaseInitializer {
    @JvmStatic
    fun initializeDatabase() {
        DriverManager.getConnection("jdbc:sqlite:data.db").use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate("PRAGMA foreign_keys = ON")
            statement.executeUpdate(
                """
                      CREATE TABLE IF NOT EXISTS "words" (
                          "id" integer PRIMARY KEY AUTOINCREMENT,
                          "text" varchar,
                          "translate" varchar
                      );
              """.trimIndent()
            )
            statement.executeUpdate(
                """
                    CREATE TABLE IF NOT EXISTS "users"(
                    "id" integer PRIMARY KEY AUTOINCREMENT,
                    "username" varchar,
                    "created_at" timestamp,
                    "chat_id" integer UNIQUE
                )
            """.trimIndent()
            )
            statement.executeUpdate(
                """
                    CREATE TABLE IF NOT EXISTS "user_answers"(
                    "user_id" integer,
                    "word_id" integer,
                    "correct_answer_count" integer,
                    "updated_at" timestamp,
                    PRIMARY KEY (user_id, word_id),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
                )
            """.trimIndent()
            )
        }
    }
}