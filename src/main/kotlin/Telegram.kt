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
    DatabaseInitializer.initializeDatabase()
    val botToken = args[0]
    var lastUpdateId = 0L
    val telegramBotService = TelegramBotService()
    val trainers = HashMap<Long, LearnWordsTrainerDataBase>()
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
    trainers: HashMap<Long, LearnWordsTrainerDataBase>,
    telegramBotService: TelegramBotService,
    dynamicMessage: DynamicMessage
) {
    val message = update.message?.text
    val chatId: Long = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return
    val data = update.callbackQuery?.data
    val document = update.message?.document

    val trainer = trainers.getOrPut(chatId) {
        val userDictionary = DatabaseUserDictionary(chatId)
        LearnWordsTrainerDataBase(userDictionary)
    }

    if (document != null) {
        handleDocument(chatId, document, botToken, json, telegramBotService, trainer, dynamicMessage)
        return
    }

    when {
        message == RESPONSE_TO_COMMAND_HELLO -> {
            showMenu(chatId, botToken, telegramBotService, dynamicMessage)
        }

        message == RESPONSE_TO_COMMAND_START -> {
            showMenu(chatId, botToken, telegramBotService, dynamicMessage)
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
            showMenu(chatId, botToken, telegramBotService, dynamicMessage)
        }

        data == RESET_CALLBACK_DATA -> {
            trainer.resetProgress()
            showResetConfirmation(chatId, botToken, telegramBotService, dynamicMessage)
        }
    }
}

fun handleDocument(
    chatId: Long,
    document: Document,
    botToken: String,
    json: Json,
    telegramBotService: TelegramBotService,
    trainer: LearnWordsTrainerDataBase,
    dynamicMessage: DynamicMessage
) {
    val getFile = telegramBotService.getFile(botToken, document.fileId)
    val response: GetFileResponse = json.decodeFromString(getFile)
    val targetFile = File(document.fileName)
    response.result?.let {
        telegramBotService.downloadFile(botToken, document.fileName, it.filePath)
        trainer.updateDictionary(targetFile)

        val message = if (targetFile.exists()) "Файл обновлен" else "Слова добавлены"
        telegramBotService.sendMessage(botToken, chatId, message)?.let { msgId ->
            dynamicMessage.addMessage(chatId, msgId, DynamicMessage.MessageType.WORD_LIST)
        }
    } ?: run {
        telegramBotService.sendMessage(botToken, chatId, "Ошибка загрузки")?.let { msgId ->
            dynamicMessage.addMessage(chatId, msgId, DynamicMessage.MessageType.WORD_LIST)
        }
    }
}

fun showMenu(
    chatId: Long,
    botToken: String,
    telegramBotService: TelegramBotService,
    dynamicMessage: DynamicMessage
) {
    val inlineKeyboard = listOf(
        listOf(
            InlineKeyboard(LEARN_WORDS_CALLBACK_DATA, "Изучать слова"),
            InlineKeyboard(STATISTICS_CALLBACK_DATA, "Статистика")
        ),
        listOf(InlineKeyboard(RESET_CALLBACK_DATA, "Сбросить статистику"))
    )
    val replyMarkup = ReplyMarkup(inlineKeyboard)
    
    val lastMessageId = dynamicMessage.getLastMessageId(chatId)
    
    if (lastMessageId != null) {
        val success = telegramBotService.editMessage(
            botToken,
            chatId,
            lastMessageId,
            "Основное меню",
            "Markdown",
            replyMarkup
        )
        if (success) {
            dynamicMessage.addMessage(chatId, lastMessageId, DynamicMessage.MessageType.MENU)
        } else {
            val newMessageId = telegramBotService.sendMenuMessage(botToken, chatId)
            newMessageId?.let {
                dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.MENU)
            }
        }
    } else {
        val messageId = telegramBotService.sendMenuMessage(botToken, chatId)
        messageId?.let {
            dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.MENU)
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

fun showResetConfirmation(
    chatId: Long,
    botToken: String,
    telegramBotService: TelegramBotService,
    dynamicMessage: DynamicMessage
) {
    val text = """
        ✅ *СТАТИСТИКА СБРОШЕНА*
        
        Весь прогресс обучения был обнулён.
        Теперь можно начать изучение слов заново!
        
        ─────────────────
    """.trimIndent()

    val inlineKeyboard = listOf(
        listOf(InlineKeyboard(BACK_CALLBACK_DATA, "↩️ Назад"))
    )
    val replyMarkup = ReplyMarkup(inlineKeyboard)

    val lastMessageId = dynamicMessage.getLastMessageId(chatId)

    if (lastMessageId != null) {
        val success = telegramBotService.editMessage(
            botToken,
            chatId,
            lastMessageId,
            text,
            "Markdown",
            replyMarkup
        )
        if (success) {
            dynamicMessage.addMessage(chatId, lastMessageId, DynamicMessage.MessageType.STATISTICS)
        } else {
            val newMessageId = telegramBotService.sendMessage(botToken, chatId, text, "Markdown", replyMarkup)
            newMessageId?.let {
                dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.STATISTICS)
            }
        }
    } else {
        val messageId = telegramBotService.sendMessage(botToken, chatId, text, "Markdown", replyMarkup)
        messageId?.let {
            dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.STATISTICS)
        }
    }
}

fun showStatistics(
    chatId: Long,
    botToken: String,
    trainer: LearnWordsTrainerDataBase,
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
    """.trimIndent()

    val inlineKeyboard = listOf(
        listOf(InlineKeyboard(BACK_CALLBACK_DATA, "↩️ Назад"))
    )
    val replyMarkup = ReplyMarkup(inlineKeyboard)

    val lastMessageId = dynamicMessage.getLastMessageId(chatId)

    if (lastMessageId != null) {
        val success = telegramBotService.editMessage(
            botToken,
            chatId,
            lastMessageId,
            text,
            "Markdown",
            replyMarkup
        )
        if (success) {
            dynamicMessage.addMessage(chatId, lastMessageId, DynamicMessage.MessageType.STATISTICS)
        } else {
            val newMessageId = telegramBotService.sendMessage(botToken, chatId, text, "Markdown", replyMarkup)
            newMessageId?.let {
                dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.STATISTICS)
            }
        }
    } else {
        val messageId = telegramBotService.sendMessage(botToken, chatId, text, "Markdown", replyMarkup)
        messageId?.let {
            dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.STATISTICS)
        }
    }
}

fun handleAnswer(
    chatId: Long,
    data: String,
    trainer: LearnWordsTrainerDataBase,
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
    checkNextQuestionAndSend(trainer, telegramBotService, chatId, botToken, dynamicMessage)
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
                showMenu(chatId, botToken, telegramBotService, dynamicMessage)
            }

            DynamicMessage.MessageType.STATISTICS -> {
                showStatistics(chatId, botToken, LearnWordsTrainerDataBase(DatabaseUserDictionary(chatId)), telegramBotService, dynamicMessage)
            }

            else -> {
                showMenu(chatId, botToken, telegramBotService, dynamicMessage)
            }
        }
    } else {
        showMenu(chatId, botToken, telegramBotService, dynamicMessage)
    }
}

fun checkNextQuestionAndSend(
    trainer: LearnWordsTrainerDataBase,
    telegramBotService: TelegramBotService,
    chatId: Long,
    botToken: String,
    dynamicMessage: DynamicMessage
): Question? {
    val question = trainer.getNextQuestion()
    val lastMessageId = dynamicMessage.getLastMessageId(chatId)
    
    if (question != null) {
        if (lastMessageId != null) {
            val messageId = telegramBotService.sendQuestion(botToken, chatId, question, lastMessageId)
            if (messageId != null) {
                dynamicMessage.addMessage(chatId, messageId, DynamicMessage.MessageType.QUESTION)
                println("Отправлен вопрос для чата $chatId (messageId: $messageId)")
            }
        } else {
            val messageId = telegramBotService.sendQuestion(botToken, chatId, question)
            messageId?.let {
                dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.QUESTION)
                println("Отправлен новый вопрос для чата $chatId (messageId: $it)")
            }
        }
    } else {
        val text = "🎉 *ПОЗДРАВЛЯЕМ!*\n\nВсе слова успешно выучены!"
        val inlineKeyboard = listOf(
            listOf(InlineKeyboard(BACK_CALLBACK_DATA, "↩️ Назад"))
        )
        val replyMarkup = ReplyMarkup(inlineKeyboard)
        
        if (lastMessageId != null) {
            val success = telegramBotService.editMessage(
                botToken,
                chatId,
                lastMessageId,
                text,
                "Markdown",
                replyMarkup
            )
            if (success) {
                dynamicMessage.addMessage(chatId, lastMessageId, DynamicMessage.MessageType.WORD_LIST)
            }
        } else {
            val messageId = telegramBotService.sendMessage(botToken, chatId, text, "Markdown", replyMarkup)
            messageId?.let {
                dynamicMessage.addMessage(chatId, it, DynamicMessage.MessageType.WORD_LIST)
            }
        }
    }
    return question
}

const val INCREASE_UPDATE_ID = 1
const val RESPONSE_TO_COMMAND_HELLO = "Hello"
const val RESPONSE_TO_COMMAND_START = "/start"
const val RESPONSE_TO_COMMAND_UNDO = "/undo"