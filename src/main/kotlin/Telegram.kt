package org.example

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
data class Message(
    @SerialName("text")
    val text: String? = null,
    @SerialName("chat")
    val chat: Chat,
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

fun main(args: Array<String>) {
    val botToken = args[0]
    var lastUpdateId = 0L
    val telegramBotService = TelegramBotService()
    val trainer = LearnWordsTrainer()
    var question: Question? = null
    val json = Json { ignoreUnknownKeys = true }

    while (true) {
        Thread.sleep(2000)
        val responseString = telegramBotService.getUpdates(botToken, lastUpdateId)
        println(responseString)
        val response: Response = json.decodeFromString<Response>(responseString)
        val update = response.result
        val firstUpdate = update.firstOrNull() ?: continue
        val updateId = firstUpdate.updateId
        lastUpdateId = updateId + INCREASE_UPDATE_ID
        val message = firstUpdate.message?.text
        val chatId: Long? = firstUpdate.message?.chat?.id ?: firstUpdate.callbackQuery?.message?.chat?.id
        val data = firstUpdate.callbackQuery?.data

        if (message == RESPONSE_TO_COMMAND_HELLO && chatId != null) {
            val sendMessageResult = telegramBotService.sendMessage(json, botToken, chatId, "Hello")
            println(sendMessageResult)
        }
        if (message == RESPONSE_TO_COMMAND_START && chatId != null) {
            val sendMenu = telegramBotService.sendMenuMessage(json, botToken, chatId)
            println(sendMenu)
        }
        if (data == STATISTICS_CALLBACK_DATA && chatId != null) {
            val statistics = trainer.getStatistics()
            val sendStatistic =
                telegramBotService.sendMessage(
                    json, botToken,
                    chatId,
                    "Выучено ${statistics.learnCount} из ${statistics.totalCount} слов | ${statistics.percent}%"
                )
            println(sendStatistic)
        }
        if (data == LEARN_WORDS_CALLBACK_DATA && chatId != null) {
            question = checkNextQuestionAndSend(json, trainer, telegramBotService, chatId, botToken)
            println(question)
        }
        if (data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true && chatId != null) {
            val userAnswerIndex = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toInt()
            val resultAsk = trainer.checkAnswer(userAnswerIndex)
            if (resultAsk) {
                val messageWin = telegramBotService.sendMessage(json, botToken, chatId, "Правильно!")
                println(messageWin)
                question = checkNextQuestionAndSend(json, trainer, telegramBotService, chatId, botToken)
                println(question)
            } else {
                val messageLose = telegramBotService.sendMessage(
                    json, botToken,
                    chatId,
                    "Неправильно! ${question?.correctAnswer?.original} - это ${question?.correctAnswer?.translation}"
                )
                println(messageLose)
                question = checkNextQuestionAndSend(json, trainer, telegramBotService, chatId, botToken)
                println(question)
            }
        }
        if (data == BACK_CALLBACK_DATA && chatId != null) {
            val sendMenu = telegramBotService.sendMenuMessage(json, botToken, chatId)
            println(sendMenu)
        }
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