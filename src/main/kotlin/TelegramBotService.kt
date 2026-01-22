package org.example

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("text")
    val text: String,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null,
)

@Serializable
data class ReplyMarkup(
    @SerialName("inline_keyboard")
    val inlineKeyboard: List<List<InlineKeyboard>>
)

@Serializable
data class InlineKeyboard(
    @SerialName("callback_data")
    val callbackData: String,
    @SerialName("text")
    val text: String,
)

class TelegramBotService {
    private val httpClient = HttpClient.newBuilder().build()

    fun getUpdates(botToken: String, updatesId: Long): String {
        val urlGetUpdates = "$TELEGRAM_BOT_API$botToken/getUpdates?offset=$updatesId"
        val request = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        return handlingNetworkErrors(request)
    }

    fun sendMessage(json: Json, botToken: String, chatId: Long, message: String): String {
        val requestBody = SendMessageRequest(
            chatId, message,
        )
        return sendJsonRequest(json, botToken, "sendMessage", requestBody)
    }

    fun sendMenuMessage(json: Json, botToken: String, chatId: Long): String {
        val requestBody = SendMessageRequest(
            chatId, "Основное меню", ReplyMarkup(
                listOf(
                    listOf(
                        InlineKeyboard(LEARN_WORDS_CALLBACK_DATA, "Изучать слова"),
                        InlineKeyboard(
                            STATISTICS_CALLBACK_DATA, "Статистика"
                        )
                    ),
                    listOf(InlineKeyboard(RESET_CALLBACK_DATA, "Сбросить статистику"))
                )
            )
        )
        return sendJsonRequest(json, botToken, "sendMessage", requestBody)
    }

    fun sendQuestion(json: Json, botToken: String, chatId: Long, question: Question): String {
        val answerButtons =
            question.askAnswer.mapIndexed { index, word ->
                listOf(
                    InlineKeyboard(
                        "$CALLBACK_DATA_ANSWER_PREFIX$index",
                        word
                    )
                )
            }
        val allButtons = answerButtons.toMutableList()
        allButtons.add(listOf(InlineKeyboard(BACK_CALLBACK_DATA, "назад")))
        val replyMarkup = ReplyMarkup(allButtons)
        val requestBody = SendMessageRequest(
            chatId,
            question.correctAnswer.original,
            replyMarkup
        )
        return sendJsonRequest(json, botToken, "sendMessage", requestBody)
    }

    private fun sendJsonRequest(json: Json, botToken: String, method: String, requestBody: SendMessageRequest): String {
        val url = "$TELEGRAM_BOT_API$botToken/$method"
        val requestBodyString = json.encodeToString(requestBody)
        val request =
            HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyString)).build()
        return handlingNetworkErrors(request)
    }

    fun handlingNetworkErrors(request: HttpRequest): String {
        repeat(3) { attempt ->
            try {
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                return response.body()
            } catch (e: IOException) {
                println("Ошибка сети, попытка ${attempt + 1}/3: ${e.message}")
                if (attempt == 2) throw e
                Thread.sleep(2000)
            }
        }
        return "Error"
    }
}

const val TELEGRAM_BOT_API = "https://api.telegram.org/bot"
const val LEARN_WORDS_CALLBACK_DATA = "learn_words_clicked"
const val STATISTICS_CALLBACK_DATA = "statistic_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"
const val BACK_CALLBACK_DATA = "back_clicked"
const val RESET_CALLBACK_DATA = "reset_clicked"