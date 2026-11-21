package com.example.workouttracker.domain.questions

import com.example.workouttracker.domain.questions.scalesOf

import com.example.workouttracker.domain.compatibility.EatingScales

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.starterChecklist
import com.example.workouttracker.domain.measurement.starterMeasurement
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.withoutLeftoverEntries
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** A questions view a person makes: the ID it is given, how it is added, and what its edits and its questions' IDs are read against. */
class MadeQuestionsTest {
    private val today = LocalDate.parse("2026-10-14")
    private val sleep = testMadeQuestions(questions = listOf(testQuestion(id = "qn_rest", answers = 2), testQuestion(id = "qn_mood", answers = 3)))

    /**
     * An ID is taken if anything names it: a configuration, a stored entry of a view no
     * configuration knows, whose answers a new view under that ID would be handed, or an entry
     * among the hidden views of the visibility settings, under which it would start out hidden.
     */
    @Test fun aNewViewsIdIsOneThatNoConfigurationNoEntryAndNoHiddenEntryNames() {
        val state = AppState(questionEntries = listOf(QuestionEntry("gone", "qst_bbbb", "2026-10-13", 1L))).withEating()
            .let { it.copy(questions = it.questions + testMadeQuestions(id = "qst_aaaa")) }
        assertEquals(setOf("eating", "qst_aaaa", "qst_bbbb"), state.namedQuestionsIds)

        // Draws "aaaa", then "bbbb", then "cccc", then "dddd".
        assertEquals("qst_cccc", state.freshQuestionsId(random = Repeating(0, 1, 2, 3)))
        assertEquals("qst_dddd", state.freshQuestionsId(hidden = setOf("qst_cccc"), random = Repeating(0, 1, 2, 3)))
        // Whatever is drawn is an ID a made view may have.
        repeat(20) { assertTrue(testMadeQuestions(id = AppState().withEating().freshQuestionsId()).id.matches(Regex("qst_[a-z2-9]{4}"))) }
    }

    /** Adding is refused under an ID anything names, so a creation retried or restored is never made twice. */
    @Test fun addingRefusesAnIdAConfigurationAnEntryOrAHiddenEntryNames() {
        val state = AppState(questionEntries = listOf(QuestionEntry("gone", "qst_gone", "2026-10-13", 1L))).withEating()

        val added = state.addQuestions(sleep)
        assertEquals(listOf(testEatingQuestions(), sleep), added.questions)
        assertEquals(state.copy(questions = added.questions), added)

        assertThrows(IllegalArgumentException::class.java) { added.addQuestions(sleep) }
        assertThrows(IllegalArgumentException::class.java) { added.addQuestions(sleep.copy(label = "Another")) }
        assertThrows(IllegalArgumentException::class.java) { state.addQuestions(testMadeQuestions(id = "qst_gone")) }
        assertThrows(IllegalArgumentException::class.java) { state.addQuestions(sleep, hidden = setOf(sleep.id)) }
        assertEquals(2, state.addQuestions(sleep, hidden = setOf("qst_m7xd")).questions.size)
    }

    /**
     * A question's ID, and an answer's, is never given out again within its parent: what the
     * view's entries still hold for a removed question or answer names it as its configuration
     * would, so nothing recorded for it is ever read as a new one's.
     */
    @Test fun aRemovedQuestionsIdAndARemovedAnswersStayNamedByTheEntriesThatHoldThem() {
        val state = AppState(
            questionEntries = listOf(
                QuestionEntry("a", sleep.id, "2026-10-13", 1L, mapOf("qn_rest" to "a9", "qn_gone" to "a1")),
                QuestionEntry("b", sleep.id, "2026-10-12", 1L, mapOf("qn_mood" to "a3")),
                QuestionEntry("c", "eating", "2026-10-12", 1L, mapOf("quality" to "good")),
                QuestionEntry("d", "qst_m7xd", "2026-10-12", 1L, mapOf("qn_other" to "a7")),
            ),
        ).withEating().let { it.copy(questions = it.questions + sleep) }

        assertEquals(setOf("qn_rest", "qn_mood", "qn_gone"), state.namedQuestionIds(sleep.id))
        assertEquals(setOf("a1", "a2", "a9"), state.namedAnswerIds(sleep.id, "qn_rest"))
        assertEquals(setOf("a1", "a2", "a3"), state.namedAnswerIds(sleep.id, "qn_mood"))
        assertEquals(setOf("a1"), state.namedAnswerIds(sleep.id, "qn_gone"))
        assertEquals(setOf("qn_other"), state.namedQuestionIds("qst_m7xd"))
    }

    /** A page's edits go over the stored configuration of the view they name; a state without it is left as it is. */
    @Test fun editsAreAppliedToTheStoredViewTheyName() {
        val state = AppState().withEating().addQuestions(sleep)
        val edits = listOf(QuestionsEdit.Rename("Nights"), QuestionsEdit.JoinRewards, QuestionsEdit.RemoveQuestion("qn_mood"))

        val edited = state.applyingQuestionsEdits(sleep.id, edits, today)

        assertEquals(sleep.applyingEdits(edits, today), edited.questions.last())
        assertEquals(testEatingQuestions(), edited.questions.first())
        assertSame(state, state.applyingQuestionsEdits("qst_gone", edits, today))
        assertSame(state, state.applyingQuestionsEdits(sleep.id, listOf(QuestionsEdit.Rename("Sleep")), today))
        // Stored prompts supply their answer scales; an absent view has no phantom scale.
        assertEquals(listOf(QuestionScale("qn_rest", listOf("a1", "a2"))), edited.scalesOf(sleep.id))
        assertEquals(EatingScales, state.scalesOf("eating"))
        assertEquals(emptyList<QuestionScale>(), AppState().scalesOf("eating"))
        assertEquals(emptyList<QuestionScale>(), state.scalesOf("qst_gone"))
    }

    /** Outside the streak is the absence of a bank, and it starts about today, still due on holiday, with its reminder off and one question. */
    @Test fun aStarterIsTheJournalAboutTodayOutsideTheStreakWithItsOneQuestion() {
        val question = starterQuestion("Question", listOf("Very bad", "Bad", "Okay", "Good", "Very good"))
        val starter = AppState().withEating().starterQuestions("Questions", question, today)

        assertEquals("Questions", starter.label)
        assertEquals(ViewIcon.Journal, starter.icon)
        assertEquals(listOf(question), starter.questions)
        assertEquals(Dated.always(AboutDay.Today), starter.about)
        assertNull(starter.rewards)
        assertEquals(Dated.always(WhenAway.Due), starter.whenAway)
        assertEquals(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false), starter.checkpoint)
        assertEquals(today, starter.madeOn)
        assertTrue(starter.id.matches(Regex("qst_[a-z2-9]{4}")))
        // A hidden entry names an ID too: it is never drawn.
        assertEquals("qst_bbbb", AppState().withEating().starterQuestions("Questions", question, today, hidden = setOf("qst_aaaa"), random = Repeating(0, 1)).id)
    }

    /** The color is the first starter color nothing wears, over checklists, measurements and questions views together. */
    @Test fun aStarterWearsTheFirstStarterColorNoViewWears() {
        val question = testQuestion()
        val bare = AppState().withEating()
        val first = bare.starterQuestions("One", question, today).hue
        assertEquals(bare.starterChecklist("List", today).hue, first)

        val checklist = bare.starterChecklist("List", today)
        val measurement = bare.copy(checklists = listOf(checklist)).starterMeasurement("Waist")
        val worn = bare.copy(checklists = listOf(checklist), measurements = listOf(measurement))
        val third = worn.starterQuestions("Three", question, today)
        assertFalse(third.hue in setOf(checklist.hue, measurement.hue))
        assertFalse(worn.addQuestions(third).starterQuestions("Four", question, today).hue in setOf(checklist.hue, measurement.hue, third.hue))
    }

    /** A new question's ID is none its view is named by, and its answers each have an ID of their own, in the order of their labels. */
    @Test fun aStarterQuestionHasAnAnswerForEachLabelUnderIdsOfItsOwn() {
        // Draws "aaaa" for the question, which is taken, then "bbbb"; the answers draw "aaaa", then "aaaa" again and "bbbb".
        val question = starterQuestion("How was it?", listOf("Bad", "Good"), takenQuestionIds = setOf("qn_aaaa"), random = Repeating(0, 1, 0, 0, 1))

        assertEquals(Question("qn_bbbb", "How was it?", listOf(Answer("an_aaaa", "Bad"), Answer("an_bbbb", "Good"))), question)
        assertEquals(Answer("an_cccc", "Okay"), starterAnswer("Okay", takenAnswerIds = question.answers.map { it.id }, random = Repeating(0, 1, 2)))
    }

    /** Deleting takes the configuration and every entry that names it, and nothing else. Eating is built in and stays. */
    @Test fun deletingRemovesTheViewAndEveryEntryOfIt() {
        val mood = testMadeQuestions(id = "qst_m00d", label = "Mood")
        val others = listOf(QuestionEntry("m", mood.id, "2026-10-13", 1L), QuestionEntry("e", "eating", "2026-10-13", 1L, mapOf("quality" to "good")))
        val state = AppState(
            questionEntries = listOf(QuestionEntry("later", sleep.id, "2026-12-24", 1L), others[0], QuestionEntry("s", sleep.id, "2026-10-13", 1L, mapOf("qn_rest" to "a1")), others[1]),
        ).withEating().let { it.copy(questions = it.questions + sleep + mood) }
        assertEquals(2, state.answeredDaysOf(sleep.id))
        assertEquals(1, state.answeredDaysOf(mood.id))

        val deleted = state.deleteQuestions(sleep.id)

        assertEquals(listOf(testEatingQuestions(), mood), deleted.questions)
        assertEquals(others, deleted.questionEntries)
        assertThrows(IllegalArgumentException::class.java) { deleted.deleteQuestions(sleep.id) }
        // Eating is deleted like any other, with every entry of its own.
        val withoutEating = state.deleteQuestions("eating")
        assertEquals(listOf(sleep, mood), withoutEating.questions)
        assertEquals(state.questionEntries.filterNot { it.viewId == "eating" }, withoutEating.questionEntries)
    }

    /**
     * A delete whose second write failed leaves a hidden entry behind. It hides nothing, and whoever
     * saves the visibility settings with the configurations at hand drops it, a measurement's and a
     * questions view's alike, each among its own kind.
     */
    @Test fun aHiddenEntryNoConfigurationHasIsDroppedWithTheNextVisibilitySave() {
        val state = AppState().withEating().addQuestions(sleep)
        val visibility = FeatureVisibilitySettings(hiddenChecklists = setOf("qst_gone"), hiddenMeasurements = setOf(sleep.id), hiddenQuestions = setOf(sleep.id, "qst_gone"))

        assertEquals(FeatureVisibilitySettings(hiddenQuestions = setOf(sleep.id)), state.withoutLeftoverEntries(visibility))
        assertFalse(visibility.shows(sleep.ref))
        assertEquals(FeatureVisibilitySettings(hiddenChecklists = setOf("qst_gone"), hiddenMeasurements = setOf(sleep.id), hiddenQuestions = setOf("qst_gone")), visibility.forgetting(sleep.ref))
    }

    /** Draws the same letter for each of an ID's four characters, one letter after another per ID. */
    private class Repeating(private vararg val letters: Int) : Random() {
        private var drawn = 0
        override fun nextBits(bitCount: Int): Int = error("An ID is drawn a character at a time.")
        override fun nextInt(until: Int): Int = letters[(drawn++ / 4) % letters.size]
    }
}
