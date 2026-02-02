package org.example

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Update(
    @SerialName("update_id")
    val updateId: Long,
    @SerialName("message")
    val message: Message? = null,
    @SerialName("callback_query")
    val callbackQuery: CallbackQuery? = null,
)

@Serializable
data class Response(
    @SerialName("result")
    val result: List<Update>,
)

@Serializable
data class Document(
    @SerialName("file_name")
    val fileName: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Long,
)

@Serializable
data class Message(
    @SerialName("text")
    val text: String? = null,
    @SerialName("chat")
    val chat: Chat,
    @SerialName("document")
    val document: Document? = null,
)

@Serializable
data class CallbackQuery(
    @SerialName("data")
    val data: String? = null,
    @SerialName("message")
    val message: Message? = null,
)

@Serializable
data class Chat(
    @SerialName("id")
    val id: Long,
)

@Serializable
data class GetFileResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("result")
    val result: TelegramFile? = null,
)

@Serializable
data class TelegramFile(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Long,
    @SerialName("file_path")
    val filePath: String,
)

fun main(args: Array<String>) {
    val botToken = args[0]
    var lastUpdateId = 0L
    val telegramBotService = TelegramBotService()
    val trainers = HashMap<Long, LearnWordsTrainer>()
    val json = Json { ignoreUnknownKeys = true }

    while (true) {
        Thread.sleep(2000)
        val responseString = telegramBotService.getUpdates(botToken, lastUpdateId)
        println(responseString)
        val response: Response = json.decodeFromString<Response>(responseString)
        if (response.result.isEmpty()) continue
        val sortedUpdates = response.result.sortedBy { it.updateId }
        sortedUpdates.forEach { handleUpdate(it, json, botToken, trainers, telegramBotService) }
        lastUpdateId = sortedUpdates.last().updateId + INCREASE_UPDATE_ID

    }
}

fun handleUpdate(
    update: Update,
    json: Json,
    botToken: String,
    trainers: HashMap<Long, LearnWordsTrainer>,
    telegramBotService: TelegramBotService,
) {

    val message = update.message?.text
    val chatId: Long = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return
    val data = update.callbackQuery?.data
    val document = update.message?.document

    val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer("$chatId.txt") }

    if (document != null) {
        val getFile = telegramBotService.getFile(json, botToken, document.fileId)
        val response: GetFileResponse = json.decodeFromString(getFile)
        val targetFile = File(document.fileName)
        response.result?.let {
            if (!targetFile.exists()) {
                telegramBotService.downloadFile(botToken, document.fileName, it.filePath)
            } else {
                val sendMessageResult = telegramBotService.sendMessage(json, botToken, chatId, "Такой файл уже есть!")
                println(sendMessageResult)
            }
        }
        trainer.loadDictionary(document.fileName)
        trainer.saveDictionary()
        val sendMessageResult =
            telegramBotService.sendMessage(json, botToken, chatId, "Слова успешно добавлены в словарь")
        println(sendMessageResult)
    }

    if (message == RESPONSE_TO_COMMAND_HELLO) {
        val sendMessageResult = telegramBotService.sendMessage(json, botToken, chatId, "Hello")
        println(sendMessageResult)
    }
    if (message == RESPONSE_TO_COMMAND_START) {
        val sendMenu = telegramBotService.sendMenuMessage(json, botToken, chatId)
        println(sendMenu)
    }
    if (data == STATISTICS_CALLBACK_DATA) {
        val statistics = trainer.getStatistics()
        val sendStatistic =
            telegramBotService.sendMessage(
                json, botToken,
                chatId,
                "Выучено ${statistics.learnCount} из ${statistics.totalCount} слов | ${statistics.percent}%"
            )
        println(sendStatistic)
    }
    if (data == LEARN_WORDS_CALLBACK_DATA) {
        checkNextQuestionAndSend(json, trainer, telegramBotService, chatId, botToken)
    }
    if (data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true) {
        val userAnswerIndex = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toInt()
        val resultAsk = trainer.checkAnswer(userAnswerIndex)
        if (resultAsk) {
            val messageWin = telegramBotService.sendMessage(json, botToken, chatId, "Правильно!")
            println(messageWin)
            checkNextQuestionAndSend(json, trainer, telegramBotService, chatId, botToken)
        } else {
            val messageLose = telegramBotService.sendMessage(
                json, botToken,
                chatId,
                "Неправильно! ${trainer.getCurrentQuestion()?.correctAnswer?.original} - это ${trainer.getCurrentQuestion()?.correctAnswer?.translation}"
            )
            println(messageLose)
            checkNextQuestionAndSend(json, trainer, telegramBotService, chatId, botToken)
        }
    }
    if (data == BACK_CALLBACK_DATA) {
        val sendMenu = telegramBotService.sendMenuMessage(json, botToken, chatId)
        println(sendMenu)
    }
    if (data == RESET_CALLBACK_DATA) {
        trainer.resetProgress()
        telegramBotService.sendMessage(json, botToken, chatId, "Прогресс сброшен")
    }
}

fun checkNextQuestionAndSend(
    json: Json,
    trainer: LearnWordsTrainer,
    telegramBotService: TelegramBotService,
    chatId: Long,
    botToken: String
): Question? {
    val question = trainer.getNextQuestion()
    if (question != null) {
        telegramBotService.sendQuestion(json, botToken, chatId, question)
    } else {
        telegramBotService.sendMessage(json, botToken, chatId, "Все слова выучены!")
    }
    return question
}

const val INCREASE_UPDATE_ID = 1
const val RESPONSE_TO_COMMAND_HELLO = "Hello"
const val RESPONSE_TO_COMMAND_START = "/start"