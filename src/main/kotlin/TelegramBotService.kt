@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package org.example

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Random

interface TelegramRequest

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("text")
    val text: String,
    @SerialName("parse_mode")
    val parseMode: String? = null,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null,
) : TelegramRequest

@Serializable
data class ReplyMarkup(
    @SerialName("inline_keyboard")
    val inlineKeyboard: List<List<InlineKeyboard>>? = null,
)

@Serializable
data class InlineKeyboard(
    @SerialName("callback_data")
    val callbackData: String,
    @SerialName("text")
    val text: String,
)

@Serializable
data class GetFileRequest(
    @SerialName("file_id")
    val fileId: String,
) : TelegramRequest

@Serializable
data class Photo(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String? = null,
    @SerialName("file_size")
    val fileSize: Long? = null,
    @SerialName("width")
    val width: Int? = null,
    @SerialName("height")
    val height: Int? = null,
)

@Serializable
data class SendPhotoResponse(
    @SerialName("photo")
    val result: List<Photo>? = null,
)

@Serializable
data class SendPhotoRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("photo")
    val photo: String,
    @SerialName("caption")
    val caption: String,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null
) : TelegramRequest

@Serializable
data class EditMessageTextRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("message_id")
    val messageId: Long,
    @SerialName("text")
    val text: String,
    @SerialName("parse_mode")
    val parseMode: String? = null,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null
) : TelegramRequest

@Serializable
data class SendMessageResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("result")
    val result: MessageResult
)

@Serializable
data class MessageResult(
    @SerialName("message_id")
    val messageId: Long
)

@Serializable
data class EditMessageResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("result")
    val result: MessageResult? = null,
    @SerialName("error_code")
    val errorCode: Int? = null,
    @SerialName("description")
    val description: String? = null
)

class TelegramBotService {
    private val httpClient = HttpClient.newBuilder().build()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun getUpdates(botToken: String, updatesId: Long): String {
        val urlGetUpdates = "$TELEGRAM_BOT_API$botToken/getUpdates?offset=$updatesId"
        val request = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        return handlingNetworkErrors(request)
    }

    fun editMessage(
        botToken: String,
        chatId: Long,
        messageId: Long,
        message: String,
        parseMode: String = "Markdown",
        replyMarkup: ReplyMarkup? = null
    ): Boolean {
        val requestBody = EditMessageTextRequest(
            chatId = chatId,
            messageId = messageId,
            text = message,
            parseMode = parseMode,
            replyMarkup = replyMarkup
        )
        val response = sendJsonRequest(botToken, "editMessageText", requestBody)
        return try {
            val jsonResponse = json.decodeFromString<EditMessageResponse>(response)

            when {
                jsonResponse.ok -> true
                jsonResponse.description?.contains("MESSAGE_NOT_MODIFIED") == true -> {
                    true
                }

                jsonResponse.description?.contains("message can't be edited") == true -> {
                    false
                }

                else -> {
                    false
                }
            }
        } catch (e: Exception) {
            println("${e.message}")
            false
        }
    }


    fun sendMessage(
        botToken: String,
        chatId: Long,
        message: String,
        parseMode: String = "Markdown",
        replyMarkup: ReplyMarkup? = null
    ): Long? {
        val requestBody = SendMessageRequest(chatId, message, parseMode, replyMarkup)

        val response = sendJsonRequest(botToken, "sendMessage", requestBody)
        return try {
            json.decodeFromString<SendMessageResponse>(response).result.messageId
        } catch (e: Exception) {
            null
        }
    }

    fun sendMenuMessage(botToken: String, chatId: Long, parseMode: String = "Markdown"): Long? {
        val requestBody = SendMessageRequest(
            chatId, "Основное меню", parseMode, ReplyMarkup(
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
        val response = sendJsonRequest(botToken, "sendMessage", requestBody)
        return try {
            json.decodeFromString<SendMessageResponse>(response).result.messageId
        } catch (e: Exception) {
            null
        }
    }

    fun sendQuestion(
        botToken: String,
        chatId: Long,
        question: Question,
    ): Long? {
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
        val caption = "Выбери правильный перевод\n${question.correctAnswer.original}:"
        val photoPath = question.correctAnswer.photoClue
        if (question.correctAnswer.photoFileId.isNotEmpty()) {
            val response = sendPhotoByFileId(botToken, chatId, question.correctAnswer.photoFileId, caption, replyMarkup)
            return try {
                json.decodeFromString<SendMessageResponse>(response).result.messageId
            } catch (e: Exception) {
                null
            }
        }

        if (photoPath.isNotEmpty()) {
            val photoFile = File(photoPath)
            if (photoFile.exists()) {
                val (responseBody, fileId) = sendPhoto(photoFile, chatId, botToken)
                fileId?.let {
                    question.correctAnswer.photoFileId = it
                }
                return try {
                    json.decodeFromString<SendMessageResponse>(responseBody ?: "").result.messageId
                } catch (e: Exception) {
                    null
                }
            }
        }

        val requestBody = SendMessageRequest(chatId, caption, "Markdown", replyMarkup)
        val response = sendJsonRequest(botToken, "sendMessage", requestBody)
        return try {
            json.decodeFromString<SendMessageResponse>(response).result.messageId
        } catch (e: Exception) {
            null
        }
    }

    fun sendQuestion(
        botToken: String,
        chatId: Long,
        question: Question,
        messageId: Long
    ): Long? {
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
        val caption = "Выбери правильный перевод\n${question.correctAnswer.original}:"
        
        val success = editMessage(botToken, chatId, messageId, caption, "Markdown", replyMarkup)
        return if (success) messageId else null
    }

    fun getFile(botToken: String, fileId: String): String {
        val requestBody = GetFileRequest(fileId)
        return sendJsonRequest(botToken, "getFile", requestBody)
    }

    fun downloadFile(botToken: String, fileName: String, filePath: String) {
        val urlGetFile = "https://api.telegram.org/file/bot$botToken/$filePath"
        val request = HttpRequest
            .newBuilder()
            .uri(URI.create(urlGetFile))
            .GET()
            .build()
        val response: HttpResponse<InputStream> = HttpClient
            .newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofInputStream())
        val body: InputStream = response.body()
        body.use { it.copyTo(File(fileName).outputStream(), 16 * 1024) }
    }

    fun sendPhoto(
        file: File,
        chatId: Long,
        botToken: String,
        hasSpoiler: Boolean = false
    ): Pair<String?, String?> {
        val data: MutableMap<String, Any> = LinkedHashMap()
        data["chat_id"] = chatId.toString()
        data["photo"] = file
        if (hasSpoiler) {
            data["has_spoiler"] = "true"
        }

        val boundary: String = BigInteger(35, Random()).toString()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$TELEGRAM_BOT_API$botToken/sendPhoto"))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(createMultipartBody(boundary, data))
            .build()

        val client: HttpClient = HttpClient.newBuilder().build()

        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            val responseBody = response.body()
            val fileId = try {
                val photoResponse = json.decodeFromString<SendPhotoResponse>(responseBody)
                photoResponse.result?.firstOrNull()?.fileId
            } catch (e: Exception) {
                null
            }

            return Pair(responseBody, fileId)
        } catch (e: Exception) {
            return Pair(null, null)
        }
    }

    private fun sendPhotoByFileId(
        botToken: String, chatId: Long, fileId: String,
        caption: String, replyMarkup: ReplyMarkup
    ): String {
        val requestBody = SendPhotoRequest(chatId, fileId, caption, replyMarkup)
        return sendJsonRequest(botToken, "sendPhoto", requestBody)
    }

    private fun sendJsonRequest(botToken: String, method: String, requestBody: TelegramRequest): String {
        val url = "$TELEGRAM_BOT_API$botToken/$method"
        val requestBodyString = when (requestBody) {
            is SendMessageRequest -> json.encodeToString(requestBody)
            is SendPhotoRequest -> json.encodeToString(requestBody)
            is GetFileRequest -> json.encodeToString(requestBody)
            is EditMessageTextRequest -> json.encodeToString(requestBody)
            else -> throw IllegalArgumentException("Unknown request body type: ${requestBody::class.simpleName}")
        }
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

private fun createMultipartBody(boundary: String, data: Map<String, Any>): HttpRequest.BodyPublisher {
    val byteArrays = ArrayList<ByteArray>()
    val separator = "--$boundary\r\n".toByteArray(StandardCharsets.UTF_8)
    val newline = "\r\n".toByteArray(StandardCharsets.UTF_8)

    for ((key, value) in data) {
        byteArrays.add(separator)

        when (value) {
            is File -> {
                val file = value
                val filename = file.name
                val mimeType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"

                val header = "Content-Disposition: form-data; name=\"$key\"; filename=\"$filename\"\r\n" +
                        "Content-Type: $mimeType\r\n\r\n"
                byteArrays.add(header.toByteArray(StandardCharsets.UTF_8))
                byteArrays.add(Files.readAllBytes(file.toPath()))
                byteArrays.add(newline)
            }

            else -> {
                val header = "Content-Disposition: form-data; name=\"$key\"\r\n\r\n"
                byteArrays.add(header.toByteArray(StandardCharsets.UTF_8))
                byteArrays.add(value.toString().toByteArray(StandardCharsets.UTF_8))
                byteArrays.add(newline)
            }
        }
    }

    byteArrays.add("--${boundary}--".toByteArray(StandardCharsets.UTF_8))

    return HttpRequest.BodyPublishers.ofByteArrays(byteArrays)
}

const val TELEGRAM_BOT_API = "https://api.telegram.org/bot"
const val LEARN_WORDS_CALLBACK_DATA = "learn_words_clicked"
const val STATISTICS_CALLBACK_DATA = "statistic_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"
const val BACK_CALLBACK_DATA = "back_clicked"
const val RESET_CALLBACK_DATA = "reset_clicked"