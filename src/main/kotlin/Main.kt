package org.example

import java.io.File

fun main() {
    val wordsFile = File("words.txt")
    if (!wordsFile.exists()) {
        println("Файл 'words.txt' не найден!")
        return
    }
    for (line in wordsFile.readLines())
        println(line)
}