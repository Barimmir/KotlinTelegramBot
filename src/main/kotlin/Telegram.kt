package org.example

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun main(args: Array<String>) {
    val botToken = args[0]
    var updateId = 0

    while (true) {
        Thread.sleep(2000)
        val updates = getUpdates(botToken, updateId)
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
    }
}

fun getUpdates(botToken: String, updatesId: Int): String {
    val urlGetUpdates = "https://api.telegram.org/bot$botToken/getUpdates?offset=$updatesId"
    val client = HttpClient.newBuilder().build()
    val request = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}