package de.nicolasmahn.momentum.feature.questions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.changingDay
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.questions.Answer
import de.nicolasmahn.momentum.domain.questions.Question
import de.nicolasmahn.momentum.domain.questions.QuestionEntry
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration
import de.nicolasmahn.momentum.domain.questions.testEatingQuestions
import de.nicolasmahn.momentum.domain.questions.testMadeQuestions
import de.nicolasmahn.momentum.feature.weight.selectedHistoryDay
import de.nicolasmahn.momentum.feature.weight.transcript
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The questions screen for a view a person made, and for two views side by side: its own questions,
 * answers and help as typed among the kind's generic words, its tiles tagged by question and
 * place, each view reading and writing its own entries alone, a day that has answers showing them
 * wherever it is selected, a form that belongs to the view's ID and the selected day, and the
 * line that names that day while it is not the one the screen opens on.
 */
@RunWith(RobolectricTestRunner::class)
class QuestionsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = "2026-10-21"
    private val eating = testEatingQuestions()
    private val eatingTag = eating.testTagPrefix

    /** Three questions of two, three and five answers, the first with a help text. */
    private val sleep = testMadeQuestions(
        questions = listOf(
            Question("qn_rest", "How rested do you feel?", listOf(Answer("an_low", "Not at all"), Answer("an_high", "Fully")), help = "Think of the whole night.\nNot only the morning."),
            Question("qn_mood", "How was your mood?", listOf(Answer("an_bad", "Low"), Answer("an_ok", "Okay"), Answer("an_good", "High"))),
            Question("qn_hours", "How long did you sleep?", (4..8).map { Answer("an_h$it", "$it h") }),
        ),
        madeOn = LocalDate.parse("2026-10-01"),
    )
    private val tag = "questions_qst_k2p9"

    /** Sleep and Eating answered on the same days, with an entry of a view nothing knows among them. */
    private val answered = AppState(
        questions = listOf(eating, sleep),
        questionEntries = listOf(
            QuestionEntry("sleep-20", sleep.id, "2026-10-20", 9, mapOf("qn_rest" to "an_high", "qn_mood" to "an_ok", "qn_hours" to "an_h6"), "Slept in"),
            QuestionEntry("eating-20", "eating", "2026-10-20", 9, mapOf("quality" to "poor")),
            QuestionEntry("gone-20", "qst_gone", "2026-10-20", 99, mapOf("qn_rest" to "an_low")),
            // The mood named an answer since removed, and one question of that day is gone altogether.
            QuestionEntry("sleep-19", sleep.id, "2026-10-19", 2, mapOf("qn_mood" to "an_great", "qn_gone" to "an_any", "qn_hours" to "an_h8")),
            QuestionEntry("eating-19", "eating", "2026-10-19", 2, mapOf("quality" to "excellent", "amount" to "much_more")),
        ),
    )

    private var state by mutableStateOf(answered)
    private var shown by mutableStateOf<QuestionsConfiguration?>(null)
    private var todayKey by mutableStateOf(today)

    /** The time a save is stored at, later than every entry here. */
    private var clock = 1_000L

    /** The screen is shown through this, so a test can take the process away and bring the saved state back. */
    private val restoration by lazy { StateRestorationTester(compose) }

    private fun restore() {
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
    }

    private fun show(view: QuestionsConfiguration, entries: AppState = answered, day: () -> String = { todayKey }) {
        state = entries
        shown = view
        restoration.setContent {
            WorkoutTrackerTheme {
                shown?.let { view ->
                    QuestionsScreen(
                        state = state,
                        view = view,
                        words = view.words(),
                        onDayChange = { change -> state.changingDay(change, timestampMillis = clock++) { updated -> state = updated; true } },
                        todayKey = day(),
                        testTagPrefix = view.testTagPrefix,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** A day is picked the way a person picks it: by its cell in the calendar. */
    private fun select(dateKey: String) = click("${tag}_history_$dateKey")

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun said(): List<String> = compose.transcript()
    private fun line(tag: String): String = said().single { it.startsWith("[$tag]") }

    /** The day the form is on, which the calendar shows as selected. */
    private fun selectedDay(prefix: String = tag): String = said().selectedHistoryDay(prefix)

    /** The line that names the selected day, or null on the day the screen opens on, where it is absent. */
    private fun dayLine(prefix: String = tag): String? = said().singleOrNull { it.startsWith("[${prefix}_selected_day]") }

    /** The Save button's state and the word on it. */
    private fun save(): String = said().let { said -> said.indexOfFirst { it.startsWith("[${tag}_save]") }.let { "${said[it]} ${said[it + 1]}" } }
    private val toSave = "[${tag}_save] state=\"Not completed\" text=\"Save\""
    private val toSaveChanges = "[${tag}_save] state=\"Not completed\" text=\"Save changes\""
    private val complete = "[${tag}_save] state=\"Completed\" disabled text=\"Saved\""
    private fun entriesOf(view: QuestionsConfiguration) = state.questionEntries.filter { it.viewId == view.id }

    @Test
    fun aMadeViewsScreenSpeaksItsOwnTextAmongTheKindsWords() {
        show(sleep)
        assertEquals(
            listOf(
                "text=\"How rested do you feel?\"",
                "spoken=\"Help with this question\"",
                "[${tag}_qn_rest_0] not selected",
                "text=\"Not at all\"",
                "[${tag}_qn_rest_1] not selected",
                "text=\"Fully\"",
                "[${tag}_qn_rest_not_sure] not selected",
                "text=\"Not sure\"",
                "text=\"How was your mood?\"",
                "[${tag}_qn_mood_0] not selected",
                "text=\"Low\"",
                "[${tag}_qn_mood_1] not selected",
                "text=\"Okay\"",
                "[${tag}_qn_mood_2] not selected",
                "text=\"High\"",
                "[${tag}_qn_mood_not_sure] not selected",
                "text=\"Not sure\"",
                "text=\"How long did you sleep?\"",
                "[${tag}_qn_hours_0] not selected",
                "text=\"4 h\"",
                "[${tag}_qn_hours_1] not selected",
                "text=\"5 h\"",
                "[${tag}_qn_hours_2] not selected",
                "text=\"6 h\"",
                "[${tag}_qn_hours_3] not selected",
                "text=\"7 h\"",
                "[${tag}_qn_hours_4] not selected",
                "text=\"8 h\"",
                "[${tag}_qn_hours_not_sure] not selected",
                "text=\"Not sure\"",
                "text=\"More options\"",
                "spoken=\"Show More options\"",
                "[${tag}_save] state=\"Not completed\"",
                "text=\"Save\"",
                "[${tag}_delete] spoken=\"Remove the answers for Wed, Oct 21\" disabled",
            ),
            said().takeWhile { it != "spoken=\"Monday\"" },
        )
        // The calendar says each question's answer in the view's own words; what the view no longer has is not said.
        assertEquals("[${tag}_history_2026-10-20] spoken=\"Tue, Oct 20: Fully; Okay; 6 h. Open answers.\" not selected click=\"Open answers\"", line("${tag}_history_2026-10-20"))
        assertEquals("[${tag}_history_2026-10-19] spoken=\"Mon, Oct 19: not sure; not sure; 8 h. Open answers.\" not selected click=\"Open answers\"", line("${tag}_history_2026-10-19"))
        assertEquals("[${tag}_history_2026-10-18] spoken=\"Sun, Oct 18: no answers. Select date.\" not selected click=\"Select date\"", line("${tag}_history_2026-10-18"))
        assertEquals("[${tag}_history_2026-10-21] spoken=\"Today, Wed, Oct 21: no answers. Select date.\" selected click=\"Select date\"", line("${tag}_history_2026-10-21"))
        // No word of Eating's is on it.
        said().forEach { said -> listOf("reflection", "eating", "Explain").forEach { word -> assertFalse(said, said.contains(word, ignoreCase = true)) } }
    }

    @Test
    @Config(qualifiers = "de")
    fun aMadeViewsScreenSpeaksGermanAroundTheTextAsTyped() {
        show(sleep)
        val form = said().takeWhile { it != "spoken=\"Montag\"" }
        assertEquals(
            listOf("text=\"How rested do you feel?\"", "spoken=\"Hilfe zu dieser Frage\"", "text=\"Not at all\"", "text=\"Fully\"", "text=\"Weiß nicht\""),
            form.filterNot { it.startsWith("[") }.take(5),
        )
        assertTrue("text=\"Weitere Optionen\"" in form)
        assertTrue("text=\"Speichern\"" in form)
        assertEquals("[${tag}_delete] spoken=\"Antworten für Mi., 21. Okt. entfernen\" disabled", line("${tag}_delete"))
        assertEquals("[${tag}_history_2026-10-19] spoken=\"Mo., 19. Okt.: weiß nicht; weiß nicht; 8 h. Antworten öffnen.\" not selected click=\"Antworten öffnen\"", line("${tag}_history_2026-10-19"))
        assertEquals("[${tag}_history_2026-10-18] spoken=\"So., 18. Okt.: keine Antworten. Datum wählen.\" not selected click=\"Datum wählen\"", line("${tag}_history_2026-10-18"))
        select("2026-10-19")
        assertEquals("[${tag}_selected_day] text=\"Mo., 19. Okt.\"", dayLine())
    }

    /**
     * The day the screen opens on is not named: the screen means it. Any other day is named above
     * the questions, so a correction is never taken for the day's own answers, and the line is
     * gone again, node and all, back on the opening day.
     */
    @Test
    fun theSelectedDayIsNamedOnlyWhileItIsNotTheDayTheScreenOpensOn() {
        show(sleep)
        assertEquals("2026-10-21", selectedDay())
        compose.onNodeWithTag("${tag}_selected_day").assertDoesNotExist()

        select("2026-10-19")
        assertEquals("2026-10-19", selectedDay())
        assertEquals("[${tag}_selected_day] text=\"Mon, Oct 19\"", dayLine())

        select("2026-10-21")
        compose.onNodeWithTag("${tag}_selected_day").assertDoesNotExist()
    }

    /** Only a question with a help text has the button, and the dialog shows that text as typed. */
    @Test
    fun aQuestionsHelpOpensItsTextAsTyped() {
        show(sleep)
        compose.onNodeWithTag("${tag}_qn_rest_help").assertExists()
        compose.onNodeWithTag("${tag}_qn_mood_help").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_qn_hours_help").assertDoesNotExist()

        click("${tag}_qn_rest_help")
        compose.onNodeWithTag("${tag}_qn_rest_help_dialog").assertExists()
        assertTrue("text=\"Think of the whole night.\nNot only the morning.\"" in said())
        assertEquals("text=\"Close\"", said().last())

        click("${tag}_qn_rest_help_close")
        compose.onNodeWithTag("${tag}_qn_rest_help_dialog").assertDoesNotExist()
    }

    /** A day is answered and saved, stays on show as it was stored, is changed and removed, each time against the entries as they are. */
    @Test
    fun aDayIsAnsweredSavedChangedAndRemoved() {
        show(sleep)
        click("${tag}_qn_rest_1")
        click("${tag}_qn_mood_not_sure")
        click("${tag}_qn_hours_2")
        assertEquals("[${tag}_qn_rest_1] selected", line("${tag}_qn_rest_1"))
        assertEquals("[${tag}_qn_mood_not_sure] selected", line("${tag}_qn_mood_not_sure"))
        assertEquals(toSave, save())
        click("${tag}_save")

        val saved = entriesOf(sleep).first()
        assertEquals(QuestionEntry(saved.id, sleep.id, "2026-10-21", 1_000L, mapOf("qn_rest" to "an_high", "qn_hours" to "an_h6")), saved)
        assertEquals(3, entriesOf(sleep).size)
        // The form stays on the saved day with what was saved, and Save is complete while it holds that.
        assertEquals("2026-10-21", selectedDay())
        assertEquals("[${tag}_qn_rest_1] selected", line("${tag}_qn_rest_1"))
        assertEquals("[${tag}_qn_mood_not_sure] selected", line("${tag}_qn_mood_not_sure"))
        assertEquals("[${tag}_qn_hours_2] selected", line("${tag}_qn_hours_2"))
        assertEquals(complete, save())
        compose.onNodeWithTag("${tag}_save_check", useUnmergedTree = true).assertExists()
        assertEquals("[${tag}_delete] spoken=\"Remove the answers for Wed, Oct 21\"", line("${tag}_delete"))

        // A change makes Save the action again, and taking the change back makes the day complete again.
        click("${tag}_qn_rest_0")
        assertEquals(toSaveChanges, save())
        click("${tag}_qn_rest_1")
        assertEquals(complete, save())

        click("${tag}_qn_rest_0")
        click("${tag}_qn_mood_2")
        click("${tag}_qn_hours_2")
        click("${tag}_save")
        assertEquals(QuestionEntry(saved.id, sleep.id, "2026-10-21", 1_001L, mapOf("qn_rest" to "an_low", "qn_mood" to "an_good")), entriesOf(sleep).first())
        // The answer taken back is stored as none, which the saved day shows as "Not sure".
        assertEquals("[${tag}_qn_hours_not_sure] selected", line("${tag}_qn_hours_not_sure"))
        assertEquals(complete, save())

        click("${tag}_delete")
        assertEquals(3, entriesOf(sleep).size)
        click("${tag}_delete")
        assertEquals(listOf("sleep-20", "sleep-19"), entriesOf(sleep).map { it.id })
        // The day stays selected, with nothing picked and nothing left to remove.
        assertEquals("2026-10-21", selectedDay())
        assertEquals("[${tag}_qn_rest_0] not selected", line("${tag}_qn_rest_0"))
        assertEquals("[${tag}_qn_hours_not_sure] not selected", line("${tag}_qn_hours_not_sure"))
        assertEquals(toSave, save())
        assertEquals("[${tag}_delete] spoken=\"Remove the answers for Wed, Oct 21\" disabled", line("${tag}_delete"))
        // Eating's entries and the unknown view's were never touched.
        assertEquals(answered.questionEntries.filter { it.viewId != sleep.id }.toSet(), state.questionEntries.filter { it.viewId != sleep.id }.toSet())
    }

    /**
     * A saved day shows what it holds for the questions and answers the view still has. A save of
     * another question leaves the rest: what the day holds for a question removed since, and the
     * mood's answer removed since, which the form shows as "Not sure" and the person never touched.
     */
    @Test
    fun aSavedDayShowsOnlyWhatTheViewStillHasAndASaveLeavesTheRest() {
        show(sleep)
        select("2026-10-19")
        assertEquals("[${tag}_qn_rest_not_sure] selected", line("${tag}_qn_rest_not_sure"))
        assertEquals("[${tag}_qn_mood_not_sure] selected", line("${tag}_qn_mood_not_sure"))
        assertEquals("[${tag}_qn_hours_4] selected", line("${tag}_qn_hours_4"))
        // It is complete as it stands: what it holds for what the view no longer has is nothing to save.
        assertEquals(complete, save())

        click("${tag}_qn_rest_0")
        click("${tag}_save")

        assertEquals(
            QuestionEntry("sleep-19", sleep.id, "2026-10-19", 1_000L, mapOf("qn_mood" to "an_great", "qn_gone" to "an_any", "qn_rest" to "an_low", "qn_hours" to "an_h8")),
            entriesOf(sleep).single { it.dateKey == "2026-10-19" },
        )
        assertEquals("[${tag}_qn_mood_not_sure] selected", line("${tag}_qn_mood_not_sure"))
        assertEquals(complete, save())
    }

    /**
     * The screen opens on the day's answers where the day today asks about has them, with the note
     * and the check, and never with nothing picked over a day that is answered.
     */
    @Test
    fun anAnsweredDayShowsItsAnswersAsTheScreenOpens() {
        show(sleep, answered.copy(questionEntries = answered.questionEntries + QuestionEntry("sleep-21", sleep.id, "2026-10-21", 5, mapOf("qn_mood" to "an_ok"), "Windy night")))

        assertEquals("[${tag}_qn_rest_not_sure] selected", line("${tag}_qn_rest_not_sure"))
        assertEquals("[${tag}_qn_mood_1] selected", line("${tag}_qn_mood_1"))
        assertEquals("[${tag}_qn_hours_not_sure] selected", line("${tag}_qn_hours_not_sure"))
        assertTrue("text=\"Windy night\"" in said())
        assertEquals(complete, save())
        compose.onNodeWithTag("${tag}_save_check", useUnmergedTree = true).assertExists()
        assertEquals("[${tag}_delete] spoken=\"Remove the answers for Wed, Oct 21\"", line("${tag}_delete"))

        // What is changed is saved into the day's own entry.
        click("${tag}_qn_rest_1")
        assertEquals(toSaveChanges, save())
        click("${tag}_save")
        assertEquals(
            QuestionEntry("sleep-21", sleep.id, "2026-10-21", 1_000L, mapOf("qn_rest" to "an_high", "qn_mood" to "an_ok"), "Windy night"),
            entriesOf(sleep).single { it.dateKey == "2026-10-21" },
        )
        assertEquals(complete, save())

        // A day without answers is left by the calendar and found again as it was stored.
        select("2026-10-18")
        select("2026-10-21")
        assertEquals("[${tag}_qn_rest_1] selected", line("${tag}_qn_rest_1"))
        assertEquals(complete, save())
    }

    /**
     * A calendar tap opens that day as it is, its answers or nothing picked. What was picked and
     * not saved belongs to the day it was picked for and goes nowhere else: the tap asks first, the
     * day tapped next shows only its own, and the day left is as it was stored.
     */
    @Test
    fun aCalendarTapOpensTheDayAsItIsAndTakesNothingAlong() {
        show(sleep)
        select("2026-10-20")
        assertEquals("[${tag}_qn_rest_1] selected", line("${tag}_qn_rest_1"))
        assertEquals(complete, save())

        // From a day on show as it is stored, an empty day is empty.
        select("2026-10-18")
        assertEquals("2026-10-18", selectedDay())
        assertEquals("[${tag}_selected_day] text=\"Sun, Oct 18\"", dayLine())
        assertTrue(said().filter { it.startsWith("[${tag}_qn_") }.none { it.endsWith("] selected") })
        assertTrue("text=\"More options\"" in said())
        assertEquals(toSave, save())
        assertEquals("[${tag}_delete] spoken=\"Remove the answers for Sun, Oct 18\" disabled", line("${tag}_delete"))

        // What is picked there and not saved is asked about, and once discarded it does not go along
        // to another day, nor is it kept for its own.
        click("${tag}_qn_rest_0")
        select("2026-10-17")
        click("${tag}_discard_confirm")
        assertEquals("[${tag}_qn_rest_0] not selected", line("${tag}_qn_rest_0"))
        assertEquals(toSave, save())
        select("2026-10-18")
        assertEquals("[${tag}_qn_rest_0] not selected", line("${tag}_qn_rest_0"))

        // A saved day that was changed and left is found again as it was stored.
        select("2026-10-20")
        click("${tag}_qn_rest_0")
        assertEquals(toSaveChanges, save())
        select("2026-10-15")
        click("${tag}_discard_confirm")
        assertTrue(said().filter { it.startsWith("[${tag}_qn_") }.none { it.endsWith("] selected") })
        assertTrue("text=\"More options\"" in said())
        select("2026-10-20")
        assertEquals("[${tag}_qn_rest_1] selected", line("${tag}_qn_rest_1"))
        assertEquals(complete, save())
        assertEquals(answered.questionEntries, state.questionEntries)
    }

    /**
     * An entry stored from elsewhere is shown by a form nothing was picked in. A form something was
     * picked in is left as it is, and its Save is refused by the store, which decides against the
     * entry the form opened on: the screen says so under the button and writes over nothing.
     */
    @Test
    fun anEntryStoredElsewhereIsShownByAnUntouchedFormAndNeverSavedOverByAStartedOne() {
        show(sleep)
        val elsewhere = QuestionEntry("sleep-21", sleep.id, "2026-10-21", 5, mapOf("qn_rest" to "an_low"))
        state = state.copy(questionEntries = state.questionEntries + elsewhere)
        compose.waitForIdle()
        assertEquals("[${tag}_qn_rest_0] selected", line("${tag}_qn_rest_0"))
        assertEquals(complete, save())

        click("${tag}_qn_rest_1")
        val changed = elsewhere.copy(timestampMillis = 6, answers = mapOf("qn_hours" to "an_h4"))
        state = state.copy(questionEntries = state.questionEntries - elsewhere + changed)
        compose.waitForIdle()
        assertEquals("[${tag}_qn_rest_1] selected", line("${tag}_qn_rest_1"))
        assertEquals("[${tag}_qn_hours_0] not selected", line("${tag}_qn_hours_0"))

        click("${tag}_save")
        assertEquals(changed, entriesOf(sleep).single { it.dateKey == "2026-10-21" })
        assertEquals("[${tag}_persistence_error] text=\"Couldn't save the answers. Try again.\"", line("${tag}_persistence_error"))
        assertEquals("2026-10-21", selectedDay())
    }

    /**
     * What is picked and typed and not saved survives process death with the day it belongs to: on
     * a day without answers, where it is then saved as that day's entry, and on a day that has
     * them, where it comes back as a change of that entry and is saved into it.
     */
    @Test
    fun whatIsPickedAndTypedSurvivesProcessDeath() {
        show(sleep)
        select("2026-10-18")
        click("${tag}_qn_rest_1")
        click("${tag}_qn_mood_not_sure")
        click("${tag}_more_options")
        compose.onNodeWithTag("${tag}_note").performTextReplacement("Woke early")
        compose.waitForIdle()

        restore()
        assertEquals("2026-10-18", selectedDay())
        assertEquals("[${tag}_qn_rest_1] selected", line("${tag}_qn_rest_1"))
        assertEquals("[${tag}_qn_mood_not_sure] selected", line("${tag}_qn_mood_not_sure"))
        assertEquals("[${tag}_qn_hours_not_sure] not selected", line("${tag}_qn_hours_not_sure"))
        // The note is where it was being typed, in the field left open.
        assertEquals("[${tag}_note] field=\"Woke early\"", line("${tag}_note"))
        assertEquals(toSave, save())
        click("${tag}_save")
        val saved = entriesOf(sleep).single { it.dateKey == "2026-10-18" }
        assertEquals(QuestionEntry(saved.id, sleep.id, "2026-10-18", 1_000L, mapOf("qn_rest" to "an_high"), "Woke early"), saved)

        select("2026-10-20")
        click("${tag}_qn_rest_0")
        restore()
        assertEquals("2026-10-20", selectedDay())
        assertEquals("[${tag}_qn_rest_0] selected", line("${tag}_qn_rest_0"))
        assertEquals("[${tag}_qn_mood_1] selected", line("${tag}_qn_mood_1"))
        assertEquals("[${tag}_qn_hours_2] selected", line("${tag}_qn_hours_2"))
        assertEquals(toSaveChanges, save())
        // The entry it opened on came back too: the store saves into it and refuses nothing.
        click("${tag}_save")
        assertEquals(
            QuestionEntry("sleep-20", sleep.id, "2026-10-20", 1_001L, mapOf("qn_rest" to "an_low", "qn_mood" to "an_ok", "qn_hours" to "an_h6"), "Slept in"),
            entriesOf(sleep).single { it.dateKey == "2026-10-20" },
        )

        // A day on show as it is stored stays selected, complete.
        restore()
        assertEquals("2026-10-20", selectedDay())
        assertEquals(complete, save())
    }

    /** One policy for every questions view: Eating's form is kept as a made view's is, with the reflection it opened on. */
    @Test
    fun eatingsFormSurvivesProcessDeathAsAMadeViewsDoes() {
        show(eating)
        assertEquals("2026-10-20", selectedDay(eatingTag))
        click("${eatingTag}_quality_3")
        click("${eatingTag}_amount_1")

        restore()
        assertEquals("2026-10-20", selectedDay(eatingTag))
        assertEquals("[${eatingTag}_quality_3] selected", line("${eatingTag}_quality_3"))
        assertEquals("[${eatingTag}_quality_0] not selected", line("${eatingTag}_quality_0"))
        assertEquals("[${eatingTag}_amount_1] selected", line("${eatingTag}_amount_1"))
        assertEquals("[${eatingTag}_save] state=\"Not completed\"", line("${eatingTag}_save"))
        assertTrue("text=\"Save changes\"" in said())

        click("${eatingTag}_save")
        assertEquals(
            QuestionEntry("eating-20", "eating", "2026-10-20", 1_000L, mapOf("quality" to "very_good", "amount" to "a_little_less")),
            entriesOf(eating).single { it.dateKey == "2026-10-20" },
        )
        assertEquals("[${eatingTag}_save] state=\"Completed\" disabled", line("${eatingTag}_save"))
    }

    /**
     * A screen brought back after its process died still knows the day it had followed. So an
     * untouched form that was opened on an earlier day moves to the day today asks about, where
     * it would otherwise take the new day's answers for the old date; a started form keeps its
     * day and what was picked.
     */
    @Test
    fun anUntouchedFormBroughtBackOnALaterDayMovesOnAndAStartedOneStays() {
        // Not snapshot state: the day changes while nothing is composed, as it does for a process that died.
        var dayAtStart = today
        show(eating, day = { dayAtStart })
        assertEquals("2026-10-20", selectedDay(eatingTag))

        dayAtStart = "2026-10-23"
        restore()
        assertEquals("2026-10-22", selectedDay(eatingTag))
        assertEquals(null, dayLine(eatingTag))

        click("${eatingTag}_quality_3")
        dayAtStart = "2026-10-24"
        restore()
        assertEquals("2026-10-22", selectedDay(eatingTag))
        assertEquals("[${eatingTag}_quality_3] selected", line("${eatingTag}_quality_3"))
        // The day today asks about has moved on without it, so the form now names the day it is on.
        assertEquals("[${eatingTag}_selected_day] text=\"Thu, Oct 22\"", dayLine(eatingTag))
    }

    /** A view about yesterday opens on yesterday and follows the day while its form is untouched; one about today opens on today. */
    @Test
    fun theFormOpensOnTheDayTodayAsksAboutAndFollowsItUntouched() {
        val lookingBack = sleep.copy(about = Dated.always(AboutDay.Yesterday))
        show(lookingBack, answered.copy(questions = listOf(eating, lookingBack)))
        assertEquals("2026-10-20", selectedDay())
        assertEquals(null, dayLine())
        // Yesterday is answered and shown so; a day on show as it is stored is untouched, and moves on.
        assertEquals("[${tag}_qn_rest_1] selected", line("${tag}_qn_rest_1"))
        todayKey = "2026-10-22"
        compose.waitForIdle()
        assertEquals("2026-10-21", selectedDay())
        assertEquals("[${tag}_qn_rest_1] not selected", line("${tag}_qn_rest_1"))

        // A started form stays on the day its answers were given for.
        click("${tag}_qn_rest_1")
        todayKey = "2026-10-23"
        compose.waitForIdle()
        assertEquals("2026-10-21", selectedDay())
        assertEquals("[${tag}_qn_rest_1] selected", line("${tag}_qn_rest_1"))
        assertEquals("[${tag}_selected_day] text=\"Wed, Oct 21\"", dayLine())
    }

    /** Each view reads its own entries, wears its own tags and keeps its own form: showing another view starts from that view's entries. */
    @Test
    fun eachViewReadsItsOwnEntriesAndKeepsItsOwnForm() {
        show(sleep)
        click("${tag}_qn_rest_1")
        assertEquals("[${tag}_qn_rest_1] selected", line("${tag}_qn_rest_1"))

        shown = eating
        compose.waitForIdle()
        compose.onNodeWithTag("${tag}_save").assertDoesNotExist()
        assertEquals("2026-10-20", selectedDay(eatingTag))
        assertEquals("[${eatingTag}_history_2026-10-19] spoken=\"Mon, Oct 19: Level 5; Level 5. Open answers.\" not selected click=\"Open answers\"", line("${eatingTag}_history_2026-10-19"))
        // Eating opens on yesterday's reflection, its own, with nothing of the other view's form.
        assertEquals("[${eatingTag}_quality_0] selected", line("${eatingTag}_quality_0"))
        assertEquals("[${eatingTag}_amount_not_sure] selected", line("${eatingTag}_amount_not_sure"))

        shown = sleep
        compose.waitForIdle()
        assertEquals("2026-10-21", selectedDay())
        assertEquals("[${tag}_qn_rest_1] not selected", line("${tag}_qn_rest_1"))
    }
}
