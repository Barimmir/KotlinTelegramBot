package org.example

import java.io.File

fun main() {
    val wordsFile = File("words.txt")
    if (!wordsFile.exists()) {
        println("Файл 'words.txt' не найден!")
        return
    }
    val lines: List<String> = wordsFile.readLines()

    val dictionary = mutableListOf<Word>()

    for (line in lines) {
        val split = line.split("|")

        val word = Word(
            original = split[0].trim(),
            translation = split[1].trim(),
            correctAnswersCount = split[2].toIntOrNull() ?: 0
        )
        dictionary.add(word)
    }
    println(dictionary)
}