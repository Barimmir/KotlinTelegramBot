import org.example.LearnWordsTrainer
import org.example.Statistics
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnWordsTrainerTest {

    @Test
    fun `test statistics with 4 words of 7`() {
        val trainer = LearnWordsTrainer("src/test/4_words_of_7.txt")
        assertEquals(
            Statistics(7, 4, "57"),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test statistics with corrupted file`() {
        val trainer = LearnWordsTrainer("src/test/corrupted_file.txt")
        assertEquals(
            Statistics(0, 0, "0"),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test getNextQuestion() with 5 unlearned words`() {
        val trainer = LearnWordsTrainer("src/test/5_unlearned_words.txt")
        val question = trainer.getNextQuestion()
        assertNotNull(question)
        assertNotNull(question.variants)
        assertEquals(4, question.variants.size)
        assertNotNull(question.correctAnswer)
        assertNotNull(question.listAskAnswer)
        assertNotNull(question.askAnswer)
        assertEquals(4, question.askAnswer.size)
    }

    @Test
    fun `test getNextQuestion() with 1 unlearned words`() {
        val trainer = LearnWordsTrainer("src/test/1_unlearned_words.txt")
        val question = trainer.getNextQuestion()
        assertNotNull(question)
    }

    @Test
    fun `test getNextQuestion() with all unlearned words`() {
        val trainer = LearnWordsTrainer("src/test/all_words_learned.txt")
        val question = trainer.getNextQuestion()
        assertNull(question)
    }

    @Test
    fun `test checkAnswer() with true`() {
        val trainer = LearnWordsTrainer("src/test/true_words.txt")
        val question = trainer.getNextQuestion()
        val correctAnswerId: Int? =
            (question?.askAnswer?.indexOf(question.correctAnswer.translation))
        val checkAnswer = correctAnswerId?.let { trainer.checkAnswer(it) }
        assertTrue { checkAnswer ?: false }
    }

    @Test
    fun `test checkAnswer() with false`() {
        val trainer = LearnWordsTrainer("src/test/false_words.txt")
        val nonCorrectAnswer = trainer.getNextQuestion()
        val wrongAnswerId = nonCorrectAnswer?.let { q ->
            q.askAnswer.withIndex()
                .find { (_, answer) -> answer != q.correctAnswer.translation }
                ?.index
        }
        val checkAnswer = wrongAnswerId?.let { trainer.checkAnswer(it) }
        assertFalse { checkAnswer ?: true }
    }

    @Test
    fun `test resetProgress() with 2 words in dictionary`() {
        val trainer = LearnWordsTrainer("src/test/2_words_in_dictionary.txt")
        trainer.resetProgress()
        assertEquals(Statistics(2, 0, "0"), trainer.getStatistics())
    }
}