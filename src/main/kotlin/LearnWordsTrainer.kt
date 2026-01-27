package org.example

import java.io.File

data class Word(
    val original: String,
    val translation: String,
    var correctAnswersCount: Int,
)

data class Statistics(
    val totalCount: Int,
    val learnCount: Int,
    val percent: String,
)

data class Question(
    val variants: List<Word>,
    val correctAnswer: Word,
    val listAskAnswer: List<String>,
    val askAnswer: List<String>,
)

class LearnWordsTrainer(
    private val fileName: String = "words.txt",
) {
    private var question: Question? = null
    val dictionary = loadDictionary()

    fun getCurrentQuestion(): Question? = question

    fun loadDictionary(): MutableList<Word> {
        val wordsFile = File(fileName)

        if (!wordsFile.exists()) {
            File("words.txt").copyTo(wordsFile)
        }

        val lines: List<String> = wordsFile.readLines()

        val dictionary = mutableListOf<Word>()

        for (line in lines) {
            val split = line.split("|")

            val word = Word(
                original = split[0].trim(),
                translation = split[1].trim(),
                correctAnswersCount = split[2].toIntOrNull() ?: ZERO_TO_EXIT
            )
            dictionary.add(word)
        }
        return dictionary
    }

    fun saveDictionary() {
        val wordsFile = File(fileName)
        val lines = mutableListOf<String>()
        for (word in dictionary) {
            lines.add("${word.original}|${word.translation}|${word.correctAnswersCount}")
        }
        wordsFile.writeText(lines.joinToString("\n"))
    }

    fun getStatistics(): Statistics {
        val totalCount = dictionary.size
        val learnCount = dictionary.filter { it.correctAnswersCount >= NEED_COUNT_TO_LEARN }.size
        val percent = if (totalCount == ZERO_TO_EXIT) "0"
        else (learnCount * MAX_PERCENTAGE / totalCount).toString()
        return Statistics(
            totalCount,
            learnCount,
            percent
        )
    }

    fun getNextQuestion(): Question? {
        val notLearnedList = dictionary.filter { it.correctAnswersCount < NEED_COUNT_TO_LEARN }
        if (notLearnedList.isEmpty()) return null
        val questionWords = notLearnedList.shuffled().take(NUMBER_OF_WORDS_TO_LEARN)
        val correctAnswer = questionWords.random()
        val listAskAnswer = questionWords.map { it.translation }
        var askAnswer = listAskAnswer.shuffled().take(NUMBER_OF_WORDS_TO_LEARN)
        if (askAnswer.size != NUMBER_OF_WORDS_TO_LEARN) {
            val needToAddWord = NUMBER_OF_WORDS_TO_LEARN - askAnswer.size
            val learnedList = dictionary.filter { it.correctAnswersCount >= NEED_COUNT_TO_LEARN }
            val takeNeedWord = learnedList.shuffled().take(needToAddWord)
            val takeNeedWordListString = takeNeedWord.map { it.translation }
            askAnswer = (askAnswer + takeNeedWordListString).shuffled()
        }
        question = Question(
            variants = questionWords,
            correctAnswer = correctAnswer,
            listAskAnswer = listAskAnswer,
            askAnswer = askAnswer
        )
        return question
    }

    fun checkAnswer(userInputAskInt: Int): Boolean {
        if (question == null) return false
        val correctAnswerId =
            (question?.askAnswer?.indexOf(question?.correctAnswer?.translation))
        if (correctAnswerId == userInputAskInt) {
            question?.correctAnswer?.correctAnswersCount++
            saveDictionary()
            return true
        } else {
            return false
        }
    }

    fun resetProgress() {
        dictionary.forEach { it.correctAnswersCount = 0 }
        saveDictionary()
    }
}

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

const val NEED_COUNT_TO_LEARN = 3
const val MAX_PERCENTAGE = 100
const val NUMBER_OF_WORDS_TO_LEARN = 4
const val INCREASE_THE_INDEX_IN_THE_LIST = 1
const val ZERO_TO_EXIT = 0

