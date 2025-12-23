package org.example

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class TelegramBotService {
    fun getUpdates(botToken: String, updatesId: Int): String {
        val urlGetUpdates = "https://api.telegram.org/bot$botToken/getUpdates?offset=$updatesId"
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun sendMessage(botToken: String, chatId: String, message: String): String {
        val urlSendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
        val requestBody = """
        {
            "chat_id": $chatId,
            "text": "$message"
        }
    """.trimIndent()
        val client = HttpClient.newBuilder().build()
        val request =
            HttpRequest.newBuilder().uri(URI.create(urlSendMessage)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun sendMenuMessage(botToken: String, chatId: String): String {
        val urlSendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
        val sendMenuBody = """{
        "chat_id": $chatId,
            "text": "Основное меню",
            "reply_markup": {
                "inline_keyboard": [
                    [
                        {
                            "text": "Изучить слова",
                            "callback_data": "data1"
                        },
                        {
                            "text": "Статистика",
                            "callback_data": "data2"
                        }
                    ]
                ]
            }
        }
            """.trimIndent()
        val client = HttpClient.newBuilder().build()
        val request =
            HttpRequest.newBuilder().uri(URI.create(urlSendMessage)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(sendMenuBody)).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }
}