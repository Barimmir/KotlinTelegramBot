package org.example

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Word(
    val original: String,
    val translation: String,
    var correctAnswersCount: Int,
    val photoClue: String = "",
    var photoFileId: String = ""
)

@Serializable
data class Statistics(
    val totalCount: Int,
    val learnCount: Int,
    val percent: String,
)

@Serializable
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
    var dictionary = loadDictionary(fileName)

    fun getCurrentQuestion(): Question? = question

    fun loadDictionary(fileName: String): MutableList<Word> {
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
                correctAnswersCount = split[2].toIntOrNull() ?: ZERO_TO_EXIT,
                photoClue = split.getOrNull(3)?.trim() ?: "",
                photoFileId = split.getOrNull(4)?.trim() ?: ""
            )
            dictionary.add(word)
        }
        return dictionary
    }

    fun saveDictionary() {
        val wordsFile = File(fileName)
        val lines = mutableListOf<String>()
        for (word in dictionary) {
            lines.add("${word.original}|${word.translation}|${word.correctAnswersCount}|${word.photoClue}")
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

const val NEED_COUNT_TO_LEARN = 3
const val MAX_PERCENTAGE = 100
const val NUMBER_OF_WORDS_TO_LEARN = 4
const val ZERO_TO_EXIT = 0

