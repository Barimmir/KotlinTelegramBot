package org.example

data class Word(
    val original: String,
    val translation: String,
    var correctAnswersCount: Int? = null,
)