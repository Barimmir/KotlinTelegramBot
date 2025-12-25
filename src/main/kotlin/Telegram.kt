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
        val chatId = chatIdRegex.findAll(updates).lastOrNull()?.groups?.get(1)?.value
        val data = dataRegex.find(updates)?.groups?.get(1)?.value

        if (message == RESPONSE_TO_COMMAND_HELLO && chatId != null) {
            val sendMessageResult = telegramBotService.sendMessage(botToken, chatId, message = "Hello")
            println(sendMessageResult)
        }
        if (message == RESPONSE_TO_COMMAND_START && chatId != null) {
            val sendMenu = telegramBotService.sendMenuMessage(botToken, chatId)
            println(sendMenu)
        }
        if (data == STATISTICS_CALLBACK_DATA && chatId != null) {
            val statistics = trainer.getStatistics()
            val sendStatistic =
                telegramBotService.sendMessage(
                    botToken,
                    chatId,
                    message = "Выучено ${statistics.learnCount} из ${statistics.totalCount} слов | ${statistics.percent}%"
                )
            println(sendStatistic)
        }
        if (data == LEARN_WORDS_CALLBACK_DATA && chatId != null) {
            val sandLearnWords = telegramBotService.sendMessage(botToken, chatId, message = "Выбрано изучение слов")
            println(sandLearnWords)
        }
    }
}

const val INCREASE_UPDATE_ID = 1
const val RESPONSE_TO_COMMAND_HELLO = "Hello"
const val RESPONSE_TO_COMMAND_START = "/start"