package org.example

import java.io.File

fun main() {
    val dictionary = loadDictionary()
    while (true) {
        println(
            "========= МЕНЮ =========\n" +
                    "1 - Учить слова \n" +
                    "2 - Статистика\n" +
                    "0 - Выход"
        )
        val userInput = readln().trim()
        when (userInput) {
            "1" -> println("Выбран пункт меню 'Учить слова'")
            "2" -> {
                val totalCount = dictionary.size
                val listLearnCount = dictionary.filter { it.correctAnswersCount >= NEED_COUNT_TO_LEARN }
                val learnCount = listLearnCount.size
                val percent = ((learnCount / totalCount) * MAX_PERCENTAGE).toString()
                println("Выучено $learnCount из $totalCount слов | $percent%")
            }

            "0" -> {
                println("Выход из программы")
                return
            }

            else -> {
                println("Введите '1','2' или '0'")
                continue
            }
        }
    }
}

fun loadDictionary(): MutableList<Word> {
    val wordsFile = File("words.txt")

    if (!wordsFile.exists()) {
        println("Файл 'words.txt' не найден!")
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
    return dictionary
}

const val NEED_COUNT_TO_LEARN = 3
const val MAX_PERCENTAGE = 100