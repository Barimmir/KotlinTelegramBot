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
import java.nio.file.Path
import java.util.Random

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

@Serializable
data class GetFileRequest(
    @SerialName("file_id")
    val fileId: String,
)

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

class TelegramBotService {
    private val httpClient = HttpClient.newBuilder().build()
    private val json = Json { ignoreUnknownKeys = true }

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

    fun sendQuestion(
        json: Json,
        botToken: String,
        chatId: Long,
        question: Question,
        trainer: LearnWordsTrainer
    ): String {
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

        val caption = "Выбери правильный перевод\n${question.correctAnswer.original}"
        val photoPath = question.correctAnswer.photoClue
        if (question.correctAnswer.photoFileId.isNotEmpty()) {
            return sendPhotoByFileId(json, botToken, chatId, question.correctAnswer.photoFileId, caption, replyMarkup)
        }
        if (photoPath.isNotEmpty()) {
            val photoFile = File(photoPath)
            if (photoFile.exists()) {
                val (_, fileId) = sendPhoto(photoFile, chatId, botToken)
                fileId?.let {
                    question.correctAnswer.photoFileId = it
                    trainer.saveDictionary()
                }
            }
        }
        val requestBody = SendMessageRequest(chatId, caption, replyMarkup)
        return sendJsonRequest(json, botToken, "sendMessage", requestBody)
    }

    fun getFile(json: Json, botToken: String, fileId: String): String {
        val requestBody = GetFileRequest(fileId)
        return sendJsonRequest(json, botToken, "getFile", requestBody)
    }

    fun downloadFile(botToken: String, fileName: String, filePath: String) {
        val urlGetFile = "$TELEGRAM_BOT_API$botToken/$filePath"
        println(urlGetFile)
        val request = HttpRequest
            .newBuilder()
            .uri(URI.create(urlGetFile))
            .GET()
            .build()

        val response: HttpResponse<InputStream> = HttpClient
            .newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofInputStream());

        println("status code: " + response.statusCode());
        val body: InputStream = response.body()
        body.use { it.copyTo(File(fileName).outputStream(), 16 * 1024) }
    }

    fun sendPhoto(file: File, chatId: Long, botToken: String, hasSpoiler: Boolean = false): Pair<String?, String?> {
        val data: MutableMap<String, Any> = LinkedHashMap()
        data["chat_id"] = chatId.toString()
        data["photo"] = file
        data["has_spoiler"] = hasSpoiler
        val boundary: String = BigInteger(35, Random()).toString()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$TELEGRAM_BOT_API$botToken/sendPhoto"))
            .postMultipartFormData(boundary, data)
            .build()
        val client: HttpClient = HttpClient.newBuilder().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val responseBody = response.body()
        val fileId = try {
            val photoResponse = json.decodeFromString<SendPhotoResponse>(responseBody)
            photoResponse.result?.firstOrNull()?.fileId
        } catch (e: Exception) {
            null
        }

        return Pair(responseBody, fileId)
    }

    private fun sendPhotoByFileId(
        json: Json, botToken: String, chatId: Long, fileId: String,
        caption: String, replyMarkup: ReplyMarkup
    ): String {
        val requestBody = mapOf(
            "chat_id" to chatId,
            "photo" to fileId,
            "caption" to caption,
            "reply_markup" to replyMarkup
        )

        val requestBodyString = json.encodeToString(requestBody)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$TELEGRAM_BOT_API$botToken/sendPhoto"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()

        return handlingNetworkErrors(request)
    }

    private fun sendJsonRequest(json: Json, botToken: String, method: String, requestBody: Any): String {
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

private fun HttpRequest.Builder.postMultipartFormData(boundary: String, data: Map<String, Any>): HttpRequest.Builder {
    val byteArrays = ArrayList<ByteArray>()
    val separator = "--$boundary\r\nContent-Disposition: form-data; name=".toByteArray(StandardCharsets.UTF_8)

    for (entry in data.entries) {
        byteArrays.add(separator)
        when (entry.value) {
            is File -> {
                val file = entry.value as File
                val path = Path.of(file.toURI())
                val mimeType = Files.probeContentType(path)
                byteArrays.add(
                    "\'${entry.key}\'; filename=\'${path.fileName}\'\r\nContent-Type: $mimeType\r\n\r\n".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                byteArrays.add(Files.readAllBytes(path))
                byteArrays.add("\r\n".toByteArray(StandardCharsets.UTF_8))
            }

            else -> byteArrays.add("\'${entry.key}\'\r\n\r\n${entry.value}\r\n".toByteArray(StandardCharsets.UTF_8))
        }
    }
    byteArrays.add("--$boundary--".toByteArray(StandardCharsets.UTF_8))

    this.header("Content-Type", "multipart/form-data;boundary=$boundary")
        .POST(HttpRequest.BodyPublishers.ofByteArrays(byteArrays))
    return this
}

const val TELEGRAM_BOT_API = "https://api.telegram.org/bot"
const val LEARN_WORDS_CALLBACK_DATA = "learn_words_clicked"
const val STATISTICS_CALLBACK_DATA = "statistic_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"
const val BACK_CALLBACK_DATA = "back_clicked"
const val RESET_CALLBACK_DATA = "reset_clicked"