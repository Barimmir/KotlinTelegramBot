package org.example

import java.io.File

data class Statistics(
    val totalCount: Int,
    val listLearnCount: List<Word>,
    val learnCount: Int,
    val percent: String,
)

data class Question(
    val variants: List<Word>,
    val correctAnswer: Word,
    val listAskAnswer: List<String>,
    val askAnswer: List<String>,
)

class LearnWordsTrainer {
    private var question: Question? = null
    val dictionary = loadDictionary()

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
        wordsFile.writeText(lines.joinToString("\n"))
    }

    fun getStatistics(): Statistics {
        val totalCount = dictionary.size
        val listLearnCount = dictionary.filter { it.correctAnswersCount >= NEED_COUNT_TO_LEARN }
        val learnCount = listLearnCount.size
        val percent = ((learnCount / totalCount) * MAX_PERCENTAGE).toString()
        return Statistics(
            totalCount,
            listLearnCount,
            learnCount,
            percent
        )
    }

    fun getNextQuestion(): Question? {
        val notLearnedList = dictionary.filter { it.correctAnswersCount <= NEED_COUNT_TO_LEARN }
        if (notLearnedList.isEmpty()) return null
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
            (question?.askAnswer?.indexOf(question?.correctAnswer?.translation)?.plus(INCREASE_THE_INDEX_IN_THE_LIST))
        if (correctAnswerId == userInputAskInt) {
            question?.correctAnswer?.correctAnswersCount++
            saveDictionary(dictionary)
            return true
        } else {
            return false
        }
    }
}


