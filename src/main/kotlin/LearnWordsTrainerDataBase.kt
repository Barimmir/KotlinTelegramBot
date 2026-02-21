package org.example

import java.io.File


class LearnWordsTrainerDataBase(
    private val userDictionary: IUserDictionary,
    private val numberOfWordsToLearn: Int = NUMBER_OF_WORDS_TO_LEARN
) {
    private var question: Question? = null

    fun updateDictionary(wordsFile: File) {
        val words = mutableListOf<Pair<String, String>>()
        val lines = wordsFile.readLines()
        for (line in lines) {
            val split = line.split("|")
            if (split.size >= 2) {
                words.add(split[0].trim() to split[1].trim())
            }
        }
        userDictionary.importWords(words)
    }

    fun getCurrentQuestion(): Question? = question

    fun getStatistics(): Statistics {
        val totalCount = userDictionary.getSize()
        val learnCount = userDictionary.getNumOfLearnedWords()
        val percent = if (totalCount == 0) "0" else (learnCount * 100 / totalCount).toString()

        return Statistics(
            totalCount,
            learnCount,
            percent
        )
    }

    fun getNextQuestion(): Question? {
        val notLearnedList = userDictionary.getUnlearnedWords()
        if (notLearnedList.isEmpty()) return null

        val questionWords = notLearnedList.shuffled().take(numberOfWordsToLearn)
        val correctAnswer = questionWords.random()
        val listAskAnswer = questionWords.map { it.translation }
        var askAnswer = listAskAnswer.shuffled().take(numberOfWordsToLearn)

        if (askAnswer.size < numberOfWordsToLearn) {
            val needToAddWord = numberOfWordsToLearn - askAnswer.size
            val learnedList = userDictionary.getLearnedWords()
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

        val correctAnswerId = question?.askAnswer?.indexOf(question?.correctAnswer?.translation)

        if (correctAnswerId == userInputAskInt) {
            val word = question?.correctAnswer
            word?.let {
                userDictionary.setCorrectAnswersCount(it.original, it.correctAnswersCount + 1)
            }
            return true
        }
        return false
    }

    fun resetProgress() {
        userDictionary.resetUserProgress()
    }
}
