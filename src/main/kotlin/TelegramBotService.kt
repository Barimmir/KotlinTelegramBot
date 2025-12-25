package org.example

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class TelegramBotService {
    private val httpClient = HttpClient.newBuilder().build()

    fun getUpdates(botToken: String, updatesId: Int): String {
        val urlGetUpdates = "$TELEGRAM_BOT_API$botToken/getUpdates?offset=$updatesId"
        val request = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun sendMessage(botToken: String, chatId: String, message: String): String {
        val urlSendMessage = "$TELEGRAM_BOT_API$botToken/sendMessage"
        val requestBody = """
        {
            "chat_id": $chatId,
            "text": "$message"
        }
    """.trimIndent()
        val request =
            HttpRequest.newBuilder().uri(URI.create(urlSendMessage)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun sendMenuMessage(botToken: String, chatId: String): String {
        val urlSendMessage = "$TELEGRAM_BOT_API$botToken/sendMessage"
        val sendMenuBody = """{
        "chat_id": $chatId,
            "text": "Основное меню",
            "reply_markup": {
                "inline_keyboard": [
                    [
                        {
                            "text": "Изучить слова",
                            "callback_data": "$LEARN_WORDS_CALLBACK_DATA"
                        },
                        {
                            "text": "Статистика",
                            "callback_data": "$STATISTICS_CALLBACK_DATA"
                        }
                    ]
                ]
            }
        }
            """.trimIndent()
        val request =
            HttpRequest.newBuilder().uri(URI.create(urlSendMessage)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(sendMenuBody)).build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }
}

const val TELEGRAM_BOT_API = "https://api.telegram.org/bot"
const val LEARN_WORDS_CALLBACK_DATA = "learn_words_clicked"
const val STATISTICS_CALLBACK_DATA = "statistic_clicked"