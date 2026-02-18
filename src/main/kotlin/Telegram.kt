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
    val result: List<Update>? = null,
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
    val dynamicMessage = DynamicMessage()

    while (true) {
        Thread.sleep(2000)
        val responseString = telegramBotService.getUpdates(botToken, lastUpdateId)
        println(responseString)
        val response: Response = json.decodeFromString<Response>(responseString)
        if (response.result?.isEmpty() == true) continue
        val sortedUpdates = response.result?.sortedBy { it.updateId }
        sortedUpdates?.forEach { update ->
            try {
                handleUpdate(update, json, botToken, trainers, telegramBotService, dynamicMessage)
            } catch (e: Exception) {
                println("Ошибка обработки update ${update.updateId}: ${e.message}")
                e.printStackTrace()
            }
        }
        sortedUpdates?.last()?.updateId?.let { lastUpdateId = it + INCREASE_UPDATE_ID }
    }
}

fun handleUpdate(
    update: Update,
    json: Json,
    botToken: String,
    trainers: HashMap<Long, LearnWordsTrainer>,
    telegramBotService: TelegramBotService,
    dynamicMessage: DynamicMessage // Добавляем параметр
) {
    val message = update.message?.text
    val chatId: Long = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return
    val data = update.callbackQuery?.data
    val document = update.message?.document

    val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer("$chatId.txt") }

    if (document != null) {
        handleDocument(chatId, document, botToken, json, telegramBotService, trainer, dynamicMessage)
        return
    }

    when {
        message == RESPONSE_TO_COMMAND_HELLO -> {
            val messageId = telegramBotService.sendMenuMessage(botToken, chatId)
            messageId?.let {
                dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.MENU)
            }
        }

        message == RESPONSE_TO_COMMAND_START -> {
            val messageId = telegramBotService.sendMenuMessage(botToken, chatId)
            messageId?.let {
                dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.MENU)
            }
        }

        message == RESPONSE_TO_COMMAND_UNDO -> {
            handleUndo(chatId, botToken, telegramBotService, dynamicMessage)
        }

        data == STATISTICS_CALLBACK_DATA -> {
            showStatistics(chatId, botToken, trainer, telegramBotService, dynamicMessage)
        }

        data == LEARN_WORDS_CALLBACK_DATA -> {
            checkNextQuestionAndSend(trainer, telegramBotService, chatId, botToken, dynamicMessage)
        }

        data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true -> {
            handleAnswer(chatId, data, trainer, botToken, telegramBotService, dynamicMessage)
        }

        data == BACK_CALLBACK_DATA -> {
            val messageId = telegramBotService.sendMenuMessage(botToken, chatId)
            messageId?.let {
                dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.MENU)
            }
        }

        data == RESET_CALLBACK_DATA -> {
            trainer.resetProgress()
            showStatistics(chatId, botToken, trainer, telegramBotService, dynamicMessage)
        }
    }
}

fun handleDocument(
    chatId: Long,
    document: Document,
    botToken: String,
    json: Json,
    telegramBotService: TelegramBotService,
    trainer: LearnWordsTrainer,
    dynamicMessage: DynamicMessage
) {
    val getFile = telegramBotService.getFile(botToken, document.fileId)
    val response: GetFileResponse = json.decodeFromString(getFile)
    val targetFile = File(document.fileName)

    response.result?.let {
        if (!targetFile.exists()) {
            telegramBotService.downloadFile(botToken, document.fileName, it.filePath)
            trainer.loadDictionary(document.fileName)
            trainer.saveDictionary()
            val messageId = telegramBotService.sendMessage(botToken, chatId, "Слова успешно добавлены в словарь")
            messageId?.let { msgId ->
                dynamicMessage.addMessage(chatId, msgId, DynamicMessage.MessageType.WORD_LIST)
            }
        } else {
            val messageId = telegramBotService.sendMessage(botToken, chatId, "Такой файл уже есть!")
            messageId?.let { msgId ->
                dynamicMessage.addMessage(chatId, msgId, DynamicMessage.MessageType.WORD_LIST)
            }
        }
    } ?: run {
        val messageId = telegramBotService.sendMessage(botToken, chatId, "Ошибка загрузки файла")
        messageId?.let { msgId ->
            dynamicMessage.addMessage(chatId, msgId, DynamicMessage.MessageType.WORD_LIST)
        }
    }
}

fun createProgressBar(percent: Int, length: Int = 10): String {
    val filledCount = percent * length / 100
    val emptyCount = length - filledCount
    val filled = "█".repeat(filledCount)
    val empty = "░".repeat(emptyCount)
    return "$filled$empty $percent%"
}

fun showStatistics(
    chatId: Long,
    botToken: String,
    trainer: LearnWordsTrainer,
    telegramBotService: TelegramBotService,
    dynamicMessage: DynamicMessage
) {
    val statistics = trainer.getStatistics()
    val percent = statistics.percent.toIntOrNull() ?: 0
    val progressBar = createProgressBar(percent)

    val text = """
        📊 *СТАТИСТИКА ИЗУЧЕНИЯ*
        
        $progressBar
        
        ✅ Выучено: *${statistics.learnCount}* из *${statistics.totalCount}* слов
        📈 Прогресс: *$percent%*
        
        ─────────────────
        Используй /undo для возврата
    """.trimIndent()

    val lastMessageId = dynamicMessage.getLastMessageId(chatId)
    val lastMessageType = dynamicMessage.getLastMessageType(chatId)

    if (lastMessageId != null && lastMessageType != DynamicMessage.MessageType.STATISTICS) {
        val success = telegramBotService.editMessage(botToken, chatId, lastMessageId, text)
        if (success) {
            dynamicMessage.addMessage(chatId, lastMessageId, DynamicMessage.MessageType.STATISTICS)
        } else {
            val newMessageId = telegramBotService.sendMessage(botToken, chatId, text)
            newMessageId?.let {
                dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.STATISTICS)
            }
        }
    } else {
        val messageId = telegramBotService.sendMessage(botToken, chatId, text)
        messageId?.let {
            dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.STATISTICS)
        }
    }
}

fun handleAnswer(
    chatId: Long,
    data: String,
    trainer: LearnWordsTrainer,
    botToken: String,
    telegramBotService: TelegramBotService,
    dynamicMessage: DynamicMessage
) {
    val userAnswerIndex = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toInt()
    val isCorrect = trainer.checkAnswer(userAnswerIndex)
    val currentQuestion = trainer.getCurrentQuestion()

    val resultText = if (isCorrect) {
        "✅ *Правильно!*\n\n${currentQuestion?.correctAnswer?.original} — *${currentQuestion?.correctAnswer?.translation}*"
    } else {
        "❌ *Неправильно!*\n\n${currentQuestion?.correctAnswer?.original} — *${currentQuestion?.correctAnswer?.translation}*"
    }
    val lastMessageId = dynamicMessage.getLastMessageId(chatId)
    if (lastMessageId != null) {
        val success = telegramBotService.editMessage(botToken, chatId, lastMessageId, resultText)
        if (success) {
            dynamicMessage.addMessage(chatId, lastMessageId, DynamicMessage.MessageType.ANSWER_RESULT)
            println("Показан результат ответа в сообщении $lastMessageId")
        } else {
            val newMessageId = telegramBotService.sendMessage(botToken, chatId, resultText)
            newMessageId?.let {
                dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.ANSWER_RESULT)
            }
        }
        Thread.sleep(1500)
    }
    showStatistics(chatId, botToken, trainer, telegramBotService, dynamicMessage)
}

fun handleUndo(
    chatId: Long,
    botToken: String,
    telegramBotService: TelegramBotService,
    dynamicMessage: DynamicMessage
) {
    val previousMessage = dynamicMessage.undo(chatId)

    if (previousMessage != null) {
        when (previousMessage.messageType) {
            DynamicMessage.MessageType.MENU -> {
                val inlineKeyboard = listOf(
                    listOf(
                        InlineKeyboard(LEARN_WORDS_CALLBACK_DATA, "Изучать слова"),
                        InlineKeyboard(STATISTICS_CALLBACK_DATA, "Статистика")
                    ),
                    listOf(InlineKeyboard(RESET_CALLBACK_DATA, "Сбросить статистику"))
                )
                val replyMarkup = ReplyMarkup(inlineKeyboard)
                val success = telegramBotService.editMessage(
                    botToken,
                    chatId,
                    previousMessage.messageId,
                    "Основное меню",
                    "Markdown",
                    replyMarkup
                )
                if (success) {
                    dynamicMessage.addMessage(chatId, previousMessage.messageId, DynamicMessage.MessageType.MENU)
                } else {
                    val newMessageId = telegramBotService.sendMenuMessage(botToken, chatId)
                    newMessageId?.let {
                        dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.MENU)
                    }
                }
            }

            DynamicMessage.MessageType.STATISTICS -> {
                val text = "↩️ Возврат к предыдущему состоянию.\nНажми «Статистика» для просмотра."
                val success = telegramBotService.editMessage(botToken, chatId, previousMessage.messageId, text)
                if (!success) {
                    val newMessageId = telegramBotService.sendMessage(botToken, chatId, text)
                    newMessageId?.let {
                        dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.NONE)
                    }
                }
                println("Откат к статистике для чата $chatId")
            }

            else -> {
                val text = "↩️ Возврат к предыдущему сообщению"
                val success = telegramBotService.editMessage(botToken, chatId, previousMessage.messageId, text)
                if (!success) {
                    val newMessageId = telegramBotService.sendMessage(botToken, chatId, text)
                    newMessageId?.let {
                        dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.NONE)
                    }
                }
            }
        }
    } else {
        val messageId = telegramBotService.sendMessage(botToken, chatId, "⚠️ Нет предыдущего сообщения для отката")
        messageId?.let {
            dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.NONE)
        }
    }
}

fun checkNextQuestionAndSend(
    trainer: LearnWordsTrainer,
    telegramBotService: TelegramBotService,
    chatId: Long,
    botToken: String,
    dynamicMessage: DynamicMessage
): Question? {
    val question = trainer.getNextQuestion()

    if (question != null) {
        telegramBotService.sendQuestion(botToken, chatId, question, trainer)
    } else {
        val text = "🎉 *ПОЗДРАВЛЯЕМ!*\n\nВсе слова успешно выучены!"
        val messageId = telegramBotService.sendMessage(botToken, chatId, text)
        messageId?.let {
            dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.WORD_LIST)
        }
    }

    return question
}

const val INCREASE_UPDATE_ID = 1
const val RESPONSE_TO_COMMAND_HELLO = "Hello"
const val RESPONSE_TO_COMMAND_START = "/start"
const val RESPONSE_TO_COMMAND_UNDO = "/undo"