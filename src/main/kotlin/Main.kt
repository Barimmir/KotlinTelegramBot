package org.example

fun main() {
    val trainer = try {
        LearnWordsTrainer()
    } catch (e: Exception) {
        println("Невозможно загрузить словарь: ${e.message}")
        return
    }

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
                val question = trainer.getNextQuestion()
                if (question == null) {
                    println("Все слова в словаре выучены")
                    return
                }
                while (true) {
                    println(question.asConsoleString())
                    val userInputAsk = readln().trim()
                    while (userInputAsk !in (ZERO_TO_EXIT..question.askAnswer.size).toString()) {
                        println("Введите число от $INCREASE_THE_INDEX_IN_THE_LIST до ${question.askAnswer.size} или $ZERO_TO_EXIT!")
                        return
                    }
                    val userInputAskInt = userInputAsk.toInt()
                    if (userInputAskInt >= INCREASE_THE_INDEX_IN_THE_LIST && userInputAskInt <= question.askAnswer.size) {
                        if (trainer.checkAnswer(userInputAsk.toInt())) {
                            println("Правильно")
                        } else {
                            println("Неправильно! ${question.correctAnswer.original} - это ${question.correctAnswer.translation}")
                        }
                    }
                    if (userInputAskInt == ZERO_TO_EXIT) {
                        break
                    }
                }
            }

            "2" -> {
                val statistics = trainer.getStatistics()
                println("Выучено ${statistics.learnCount} из ${statistics.totalCount} слов | ${statistics.percent}%")
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

const val NEED_COUNT_TO_LEARN = 3
const val MAX_PERCENTAGE = 100
const val NUMBER_OF_WORDS_TO_LEARN = 4
const val INCREASE_THE_INDEX_IN_THE_LIST = 1
const val ZERO_TO_EXIT = 0