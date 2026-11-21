package com.example.workouttracker.feature.questions

import com.example.workouttracker.domain.questions.eatingEntry
import android.content.Context
import android.os.Parcel
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.questions.Answer
import com.example.workouttracker.domain.questions.Question
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.TestQuestionsMadeOn
import com.example.workouttracker.domain.questions.testEatingQuestions
import com.example.workouttracker.domain.questions.testMadeQuestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuestionsModelsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val eating = testEatingQuestions()

    /** A made view of three questions, of two, three and five answers, the first with a help text. */
    private val sleep = testMadeQuestions(
        questions = listOf(
            Question("qn_rest", "How rested do you feel?", listOf(Answer("an_low", "Not at all"), Answer("an_high", "Fully")), help = "Think of the whole night."),
            Question("qn_mood", "How was your mood?", listOf(Answer("an_bad", "Low"), Answer("an_ok", "Okay"), Answer("an_good", "High"))),
            Question("qn_hours", "How long did you sleep?", (4..8).map { Answer("an_h$it", "$it h") }),
        ),
    )
    private val sleepWords = QuestionsWords(sleep)

    @Test
    fun historyUsesFourWholeWeeksWithTodayInTheLastRow() {
        val window = questionsHistoryWindow(emptyList(), "2026-03-02")

        assertEquals(28, window.days.size)
        assertEquals("2026-02-09", window.days.first().dateKey)
        assertEquals("2026-03-02", window.days[21].dateKey)
        assertEquals("2026-03-08", window.days.last().dateKey)
    }

    /** A day holds the one entry its view has for it; an entry dated after today is not shown. */
    @Test
    fun historyHoldsEachDaysEntryAndHidesOneDatedAhead() {
        val saved = reflection("saved", quality = "poor", amount = null)
        val ahead = saved.copy(id = "ahead", dateKey = "2026-09-18")

        val window = questionsHistoryWindow(listOf(ahead, saved), "2026-09-16")

        assertEquals(saved, window.days.first { it.dateKey == "2026-09-15" }.entry)
        assertNull(window.days.first { it.dateKey == "2026-09-18" }.entry)
        assertNull(window.days.first { it.dateKey == "2026-09-14" }.entry)
    }

    @Test
    fun loggedButUnratedDayIsSpokenAsNotSure() {
        val day = QuestionsHistoryDay("2026-09-15", reflection("unrated", quality = null, amount = "a_little_less"))

        assertEquals("not sure; Level 2", questionsHistoryStatus(day, eating.words()).resolve(resources))
    }

    @Test
    fun historyDescriptionUsesWordsWithoutCombiningAnswersIntoAScore() {
        val day = QuestionsHistoryDay("2026-09-15", reflection("one", quality = "very_good", amount = "a_little_more"))

        assertEquals("Level 4; Level 4", questionsHistoryStatus(day, eating.words()).resolve(resources))
        assertEquals("Level 1; not sure", questionsHistoryStatus(QuestionsHistoryDay("2026-09-15", reflection("poor", "poor", null)), eating.words()).resolve(resources))
        assertEquals("not sure; not sure", questionsHistoryStatus(QuestionsHistoryDay("2026-09-15", reflection("none", null, null)), eating.words()).resolve(resources))
        assertEquals("no answers", questionsHistoryStatus(QuestionsHistoryDay("2026-09-15", null), eating.words()).resolve(resources))
    }

    /** A made view's day says each question's answer as it was typed, in the view's order, and "not sure" where it has none it still shows. */
    @Test
    fun aMadeViewsDayIsSpokenInItsOwnAnswersAndGenericWords() {
        fun status(answers: Map<String, String>?) =
            questionsHistoryStatus(QuestionsHistoryDay("2026-10-14", answers?.let { QuestionEntry("day", sleep.id, "2026-10-14", 1, it) }), sleepWords).resolve(resources)

        assertEquals("Fully; Okay; 6 h", status(mapOf("qn_rest" to "an_high", "qn_mood" to "an_ok", "qn_hours" to "an_h6")))
        assertEquals("not sure; High; not sure", status(mapOf("qn_mood" to "an_good")))
        // An answer the question no longer has, and one to a question the view no longer has, are not said.
        assertEquals("not sure; not sure; 4 h", status(mapOf("qn_rest" to "an_mid", "qn_gone" to "an_any", "qn_hours" to "an_h4")))
        assertEquals("not sure; not sure; not sure", status(emptyMap()))
        assertEquals("no answers", status(null))
    }

    @Test
    fun draftConversionPreservesMissingAndNeutralAsDifferentValues() {
        val missingAmount = reflection("missing", quality = "good", amount = null).toDraft(eating.scales)
        val neutralAmount = reflection("neutral", quality = "good", amount = "about_the_same").toDraft(eating.scales)

        assertEquals(QuestionAnswer.NotSure, missingAmount.answerTo("amount"))
        assertEquals(QuestionAnswer.Answered("about_the_same"), neutralAmount.answerTo("amount"))
        assertEquals(QuestionAnswer.Answered("good"), neutralAmount.answerTo("quality"))
    }

    /** A saved day opens with what it holds for the questions the view asks; what it holds for a removed question or answer is not shown, and a save stores only what is shown. */
    @Test
    fun aSavedDayOpensWithTheAnswersTheViewStillHas() {
        val entry = QuestionEntry("day", sleep.id, "2026-10-14", 1, mapOf("qn_rest" to "an_high", "qn_mood" to "an_removed", "qn_gone" to "an_any"), "Slept in")

        val draft = entry.toDraft(sleep.scales)

        assertEquals(QuestionsDraft("2026-10-14", mapOf("qn_rest" to QuestionAnswer.Answered("an_high"), "qn_mood" to QuestionAnswer.NotSure, "qn_hours" to QuestionAnswer.NotSure), "Slept in"), draft)
        assertEquals(mapOf("qn_rest" to "an_high"), draft.storedAnswers)
        assertEquals(QuestionAnswer.Unanswered, draft.answerTo("qn_gone"))
    }

    @Test
    fun aFreshFormHasNothingSelectedAndStoresNothing() {
        val draft = QuestionsDraft("2026-09-15")

        assertEquals(QuestionAnswer.Unanswered, draft.answerTo("quality"))
        assertEquals(QuestionAnswer.Unanswered, draft.answerTo("amount"))
        assertNull(draft.answerTo("quality").storedAnswerId)
        assertEquals(emptyMap<String, String>(), draft.storedAnswers)
    }

    /** An answer taken back leaves the form equal to a fresh one, so an untouched form can still follow the day. */
    @Test
    fun aFormWithEveryAnswerTakenBackIsAFreshFormAgain() {
        val fresh = QuestionsDraft("2026-09-15")
        val picked = fresh.answering("quality", QuestionAnswer.Answered("good")).answering("amount", QuestionAnswer.NotSure)
        assertEquals(mapOf("quality" to "good"), picked.storedAnswers)

        assertEquals(fresh, picked.answering("quality", QuestionAnswer.Unanswered).answering("amount", QuestionAnswer.Unanswered))
    }

    @Test
    fun pickingAValueSelectsItAndTappingItAgainReturnsToUnanswered() {
        val picked = toggleAnswer(QuestionAnswer.Unanswered, "good")
        assertEquals(QuestionAnswer.Answered("good"), picked)
        assertEquals("good", picked.storedAnswerId)

        assertEquals(QuestionAnswer.Answered("very_good"), toggleAnswer(picked, "very_good"))
        assertEquals(QuestionAnswer.Unanswered, toggleAnswer(picked, "good"))
    }

    @Test
    fun notSureIsItsOwnSelectionThatReplacesAValueAndStoresNothing() {
        val picked = QuestionAnswer.Answered("good")
        val notSure = toggleNotSure(picked)

        assertEquals(QuestionAnswer.NotSure, notSure)
        assertNull(notSure.storedAnswerId)
        assertEquals(QuestionAnswer.Unanswered, toggleNotSure(notSure))
        assertEquals(QuestionAnswer.Answered("poor"), toggleAnswer(notSure, "poor"))
    }

    @Test
    fun aSavedBlankAnswerLoadsAsNotSureWhileAnUnsavedDayStaysUnanswered() {
        assertEquals(QuestionAnswer.NotSure, questionAnswerFor(null, hasEntry = true))
        assertEquals(QuestionAnswer.Unanswered, questionAnswerFor(null, hasEntry = false))
        assertEquals(QuestionAnswer.Answered("excellent"), questionAnswerFor("excellent", hasEntry = true))
    }

    /** The screen opens on the day today asks about: today, or yesterday for a view about yesterday, and never a day before the view was made. */
    /** A day opens on its entry where it has one and with nothing picked where it has none, and a form is untouched until something in it differs. */
    @Test
    fun aFormOpensOnTheDaysEntryAndKnowsWhetherItStillHoldsIt() {
        val entry = QuestionEntry("sleep-15", sleep.id, "2026-09-15", 7, mapOf("qn_rest" to "an_high", "qn_gone" to "an_any"), "Slept in")
        val state = AppState(questions = listOf(eating, sleep), questionEntries = listOf(entry, entry.copy(id = "eating-15", viewId = eating.id, answers = emptyMap())))

        val answered = state.questionsForm(sleep, "2026-09-15")
        assertEquals(QuestionsForm(entry.toDraft(sleep.scales), opened = entry), answered)
        assertTrue(answered.isUntouched(sleep.scales))
        assertTrue(answered.isSaved(sleep.scales))

        val changed = answered.copy(draft = answered.draft.answering("qn_mood", QuestionAnswer.Answered("an_ok")))
        assertFalse(changed.isUntouched(sleep.scales))
        assertFalse(changed.isSaved(sleep.scales))
        assertTrue(changed.copy(draft = changed.draft.answering("qn_mood", QuestionAnswer.NotSure)).isSaved(sleep.scales))

        val empty = state.questionsForm(sleep, "2026-09-14")
        assertEquals(QuestionsForm(QuestionsDraft("2026-09-14")), empty)
        assertTrue(empty.isUntouched(sleep.scales))
        // Nothing is saved on a day without an entry, however little the form holds.
        assertFalse(empty.isSaved(sleep.scales))
        assertFalse(empty.copy(draft = empty.draft.copy(note = "x")).isUntouched(sleep.scales))
    }

    /**
     * A form is kept across process death as it is: the day, what was picked with "Not sure" apart
     * from nothing picked, the note as typed, and the entry it opened on equal to the stored one,
     * what it holds for a removed question included. What is saved goes through a parcel, as
     * Android keeps it.
     */
    @Test
    fun aFormComesBackFromItsSavedStateAsItWas() {
        val entry = QuestionEntry("sleep-15", sleep.id, "2026-09-15", 7, mapOf("qn_rest" to "an_high", "qn_gone" to "an_any"), "Slept in")
        val forms = listOf(
            QuestionsForm(QuestionsDraft("2026-09-14")),
            QuestionsForm(QuestionsDraft("2026-09-1", mapOf("qn_rest" to QuestionAnswer.Answered("an_low"), "qn_mood" to QuestionAnswer.NotSure), note = "  Woke early ")),
            QuestionsForm(entry.toDraft(sleep.scales), opened = entry),
            QuestionsForm(entry.toDraft(sleep.scales).answering("qn_hours", QuestionAnswer.Answered("an_h6")).copy(note = ""), opened = entry.copy(note = null, answers = emptyMap())),
        )

        forms.forEach { form ->
            val saved = requireNotNull(with(QuestionsFormSaver) { SaverScope { true }.save(form) })
            val parcel = Parcel.obtain()
            parcel.writeValue(saved)
            parcel.setDataPosition(0)
            val kept = requireNotNull(parcel.readValue(javaClass.classLoader))
            parcel.recycle()

            assertEquals(form, QuestionsFormSaver.restore(kept))
        }
    }

    @Test
    fun theDefaultDayIsTheOneTodayAsksAbout() {
        assertEquals("2025-12-31", eating.defaultDateKey("2026-01-01"))
        assertEquals("2024-02-29", eating.defaultDateKey("2024-03-01"))

        val aboutToday = testMadeQuestions()
        val aboutYesterday = testMadeQuestions(about = AboutDay.Yesterday)
        val made = TestQuestionsMadeOn
        assertEquals(made.plusDays(3).toString(), aboutToday.defaultDateKey(made.plusDays(3).toString()))
        assertEquals(made.plusDays(2).toString(), aboutYesterday.defaultDateKey(made.plusDays(3).toString()))
        // Made today about yesterday, it does not ask about a day it did not exist on.
        assertEquals(made.toString(), aboutYesterday.defaultDateKey(made.toString()))
        assertEquals(made.toString(), aboutYesterday.defaultDateKey(made.plusDays(1).toString()))
    }

    /** A made view's words are its own text as typed and the kind's generic words; none is Eating's. */
    @Test
    fun aMadeViewSpeaksItsOwnTextAndNoWordOfEatings() {
        assertEquals(listOf("qn_rest", "qn_mood", "qn_hours"), sleepWords.questions.map { it.id })
        assertEquals(listOf("How rested do you feel?", "How was your mood?", "How long did you sleep?"), sleepWords.questions.map { it.text.resolve(resources) })
        assertEquals(listOf("Not at all", "Fully"), sleepWords.questions.first().answers.map { it.label.resolve(resources) })
        assertEquals(listOf("an_low", "an_high"), sleepWords.questions.first().answers.map { it.id })
        assertEquals("Help with this question", sleepWords.questions.first().help?.description?.resolve(resources))
        assertEquals(listOf(null, null), sleepWords.questions.drop(1).map { it.help })

        val said = listOf(
            sleepWords.notSure, sleepWords.moreOptions, sleepWords.noteLabel, sleepWords.save, sleepWords.saveChanges,
            sleepWords.saved, sleepWords.saveFailed, sleepWords.removalFailed, sleepWords.dayWithoutEntry, sleepWords.selectDay, sleepWords.openDay,
        ).map { it.resolve(resources) }
        assertEquals(
            listOf(
                "Not sure", "More options", "Note (optional)", "Save", "Save changes",
                "Saved", "Couldn't save the answers. Try again.", "Couldn't delete the answers. Try again.", "no answers", "Select date", "Open answers",
            ),
            said,
        )
        said.forEach { sentence -> listOf("eating", "reflection").forEach { word -> assertEquals(sentence, false, sentence.contains(word, ignoreCase = true)) } }
    }

    private fun reflection(id: String, quality: String?, amount: String?) =
        eatingEntry(id = id, dateKey = "2026-09-15", timestampMillis = 100L, quality = quality, amount = amount)
}
