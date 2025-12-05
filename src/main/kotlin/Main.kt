package org.example

import java.io.File
import java.util.Dictionary

fun main() {
    val dictionary = loadDictionary()
    while (true) {
        println(
            "========= МЕНЮ =========\n" +
                    "1 - Учить слова \n" +
                    "2 - Статистика\n" +
                    "0 - Выход"
        )
        val userInputMenu = readln().trim()
        when (userInputMenu) {
            "1" -> {
                val notLearnedList = dictionary.filter { it.correctAnswersCount <= NEED_COUNT_TO_LEARN }
                if (notLearnedList.isEmpty()) {
                    println("Все слова в словаре выучены")
                    return
                }
                val questionWords = notLearnedList.take(NUMBER_OF_WORDS_TO_LEARN).shuffled()
                val correctAnswer = questionWords.random()
                val listAskAnswer = questionWords.map { it.translation }
                val askAnswer = listAskAnswer.shuffled().take(NUMBER_OF_WORDS_TO_LEARN)
                if (askAnswer.size != NUMBER_OF_WORDS_TO_LEARN) {
                    val needToAddWord = NUMBER_OF_WORDS_TO_LEARN - askAnswer.size
                    val learnedList = dictionary.filter { it.correctAnswersCount >= NEED_COUNT_TO_LEARN }
                    val takeNeedWord = learnedList.shuffled().take(needToAddWord)
                    askAnswer + takeNeedWord
                }
                println("\n${correctAnswer.original}:")
                askAnswer.forEachIndexed { index, askInAnswer ->
                    println("${index + INCREASE_THE_INDEX_IN_THE_LIST} - $askInAnswer")
                }
                println(
                    "----------\n" +
                            "0 - Меню"
                )
                val correctAnswerId =
                    (askAnswer.indexOf(correctAnswer.translation) + INCREASE_THE_INDEX_IN_THE_LIST)
                val userInputAsk = readln().trim()
                when (userInputAsk) {
                    "1", "2", "3", "4" -> {
                        val userInputAskInt = userInputAsk.toInt()
                        if (userInputAskInt == correctAnswerId) {
                            println("Правильно")
                            correctAnswer.correctAnswersCount++
                            saveDictionary(dictionary)
                        } else {
                            println("Неправильно! ${correctAnswer.original} - это ${correctAnswer.translation}")
                        }
                    }

                    "0" -> println()
                    else -> {
                        println("Введите 1,2,3,4 или 0")
                        continue
                    }
                }
            }

            "2" -> {
                val totalCount = dictionary.size
                val listLearnCount = dictionary.filter { it.correctAnswersCount >= NEED_COUNT_TO_LEARN }
                val learnCount = listLearnCount.size
                val percent = ((learnCount / totalCount) * MAX_PERCENTAGE).toString()
                println("Выучено $learnCount из $totalCount слов | $percent%")
                println()
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

fun saveDictionary(dictionary: MutableList<Word>) {
    val wordsFile = File("words.txt")
    val lines = mutableListOf<String>()
    for (word in dictionary) {
        lines.add("${word.original}|${word.translation}|${word.correctAnswersCount}")
    }
    wordsFile.writeText(lines.toString())
}

const val NEED_COUNT_TO_LEARN = 3
const val MAX_PERCENTAGE = 100
const val NUMBER_OF_WORDS_TO_LEARN = 4
const val INCREASE_THE_INDEX_IN_THE_LIST = 1