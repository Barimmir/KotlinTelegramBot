package org.example

data class Word(
    val original: String,
    val translation: String,
    var correctAnswersCount: Int,
)

fun Question.asConsoleString(): String {
    println("\n${this.correctAnswer.original}:")
    this.askAnswer.forEachIndexed { index, askInAnswer ->
        println("${index + INCREASE_THE_INDEX_IN_THE_LIST} - $askInAnswer")
    }
    println(
        "----------\n" +
                "0 - Меню"
    )
    return String()
}