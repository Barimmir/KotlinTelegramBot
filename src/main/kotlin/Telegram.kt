package org.example

fun main(args: Array<String>) {
    val botToken = args[0]
    var updateId = 0
    val telegramBotService = TelegramBotService()

    while (true) {
        Thread.sleep(2000)
        val updates = telegramBotService.getUpdates(botToken, updateId + INCREASE_UPDATE_ID)
        println(updates)
        val updateIdRegex: Regex = "\"update_id\":\\s*(\\d+)".toRegex()
        val matchResultUpdateId = updateIdRegex.find(updates)
        val groupsUpdateId = matchResultUpdateId?.groups
        updateId = groupsUpdateId?.get(1)?.value?.toInt() ?: 0
        println(updateId)

        val messageTextRegex: Regex = "\"text\":\"(.*?)\"".toRegex()
        val matchResultText = messageTextRegex.find(updates)
        val groupsText = matchResultText?.groups
        val text = groupsText?.get(1)?.value
        println(text)

        val chatIdRegex: Regex = """"chat"\s*:\s*\{[^}]*"id"\s*:\s*(\d+)""".toRegex()
        val matchResultChatId = chatIdRegex.find(updates)
        val groupsChatId = matchResultChatId?.groups
        val chatId = groupsChatId?.get(1)?.value
        println(chatId)
        if (text == "Hello") {
            val askUser = "Hello"
            val sendMessageResult = telegramBotService.sendMessage(botToken, chatId, text = askUser)
            println(sendMessageResult)
        }
    }
}

const val INCREASE_UPDATE_ID = 1