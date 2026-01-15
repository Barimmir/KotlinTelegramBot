package org.example

fun main(args: Array<String>) {
    val botToken = args[0]
    var updateId = 0
    val telegramBotService = TelegramBotService()
    val trainer = LearnWordsTrainer()
    val updateIdRegex: Regex = "\"update_id\":\\s*(\\d+)".toRegex()
    val messageTextRegex: Regex = "\"text\":\"(.*?)\"".toRegex()
    val chatIdRegex: Regex = "\"chat\":\\{\"id\":(\\d+)".toRegex()
    val dataRegex: Regex = "\"data\":\"(.*?)\"".toRegex()

    while (true) {
        Thread.sleep(2000)
        val updates = telegramBotService.getUpdates(botToken, updateId + INCREASE_UPDATE_ID)
        updateId = updateIdRegex.find(updates)?.groups?.get(1)?.value?.toInt() ?: 0
        val message = messageTextRegex.find(updates)?.groups?.get(1)?.value
        val chatId = chatIdRegex.findAll(updates).lastOrNull()?.groups?.get(1)?.value.toString()
        val data = dataRegex.find(updates)?.groups?.get(1)?.value
        val sendMenu = telegramBotService.sendMenuMessage(botToken, chatId)

        if (message == RESPONSE_TO_COMMAND_HELLO) {
            val sendMessageResult = telegramBotService.sendMessage(botToken, chatId, message = "Hello")
            println(sendMessageResult)
            println(sendMenu)
        }
        if (message == RESPONSE_TO_COMMAND_START) {
            println(sendMenu)
        }
        if (data == STATISTICS_CALLBACK_DATA) {
            val statistics = trainer.getStatistics()
            val sendStatistic =
                telegramBotService.sendMessage(
                    botToken,
                    chatId,
                    message = "Выучено ${statistics.learnCount} из ${statistics.totalCount} слов | ${statistics.percent}%"
                )
            println(sendStatistic)
        }
        if (data == LEARN_WORDS_CALLBACK_DATA) {
            val sendQuestion = checkNextQuestionAndSand(trainer, telegramBotService, chatId, botToken)
            println(sendQuestion)
        }
        if (message == BACK_CALLBACK_DATA) {
            println(sendMenu)
        }
    }
}

fun checkNextQuestionAndSand(
    trainer: LearnWordsTrainer,
    telegramBotService: TelegramBotService,
    chatId: String,
    botToken: String
): String {
    val question = trainer.getNextQuestion()
    if (question == null) {
        println("Все слова выучены!")
    }
    return telegramBotService.sendQuestion(botToken, chatId, question!!)
}

const val INCREASE_UPDATE_ID = 1
const val RESPONSE_TO_COMMAND_HELLO = "Hello"
const val RESPONSE_TO_COMMAND_START = "/start"