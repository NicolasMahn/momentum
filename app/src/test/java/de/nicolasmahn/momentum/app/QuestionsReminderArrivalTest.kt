package de.nicolasmahn.momentum.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.questions.QuestionEntry
import de.nicolasmahn.momentum.domain.questions.testMadeQuestions
import de.nicolasmahn.momentum.domain.questions.testQuestion
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.feature.weight.selectedHistoryDay
import de.nicolasmahn.momentum.feature.weight.transcript
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity

/**
 * A tap on a questions view's reminder opens that view on the day the reminder asked about, once,
 * as a measurement's does. A day saved or removed on its screen says which view and which day, so
 * the reminder about that day can be answered, Eating's as any other's.
 */
@RunWith(RobolectricTestRunner::class)
class QuestionsReminderArrivalTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = currentDateKey()
    private val yesterday = LocalDate.parse(today).minusDays(1).toString()
    private val sleep = testMadeQuestions(madeOn = LocalDate.parse(today).minusDays(30))
    private val mood = testMadeQuestions(id = "qst_m00d", label = "Mood", questions = listOf(testQuestion(id = "qn_mood", answers = 3)), madeOn = LocalDate.parse(today).minusDays(30))
    private var state by mutableStateOf(
        AppState(
            questionEntries = listOf(
                QuestionEntry("sleep-y", sleep.id, yesterday, 1L, mapOf("qn_rest" to "a4")),
                QuestionEntry("mood-y", mood.id, yesterday, 3L, mapOf("qn_mood" to "a2")),
            ),
        ).withWeight().withEating().withReading().withActivity().let { it.copy(questions = it.questions + sleep + mood) },
    )
    private var request by mutableStateOf<AppNavigationRequest?>(null)
    private var consumed = 0

    /** Each day stored: the view, the day, and that view's entries of the day before and after. */
    private val logged = mutableListOf<List<Any>>()

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                onStateChange = { state = it; true },
                onActivitySave = ActivitySaveNotExpected,
                requestedNavigation = request,
                onDestinationConsumed = {
                    request = null
                    consumed += 1
                },
                onDayLogged = { view, dateKey, before, after ->
                    fun AppState.answers() = questionEntries.filter { it.viewId == view.id && it.dateKey == dateKey }.map { it.answers }
                    logged += listOf(view, dateKey, before.answers(), after.answers())
                },
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun tapReminder(viewId: String, dateKey: String?) {
        request = AppNavigationRequest(AppDestination.Questions, loggedViewId = viewId, loggedDateKey = dateKey)
        compose.waitForIdle()
    }

    /** The day the view's form is on, which its calendar shows as selected. */
    private fun date(viewId: String): String = compose.transcript().selectedHistoryDay("questions_$viewId")

    @Test
    fun aTapOpensTheViewOnTheDayItsReminderAskedAboutOnce() {
        compose.setContent { App() }

        tapReminder(sleep.id, yesterday)

        assertEquals(yesterday, date(sleep.id))
        // A day other than the one the screen opens on is named, so its answers are not taken for today's.
        compose.onNodeWithTag("questions_${sleep.id}_selected_day").assertExists()
        compose.onNodeWithTag("questions_${sleep.id}_qn_rest_3").assertIsSelected()
        assertEquals(1, consumed)

        // The day is used up: opened again from its tile, the view is on the day today asks about.
        click("top_back")
        click("home_questions_${sleep.id}")
        assertEquals(today, date(sleep.id))
        compose.onNodeWithTag("questions_${sleep.id}_selected_day").assertDoesNotExist()

        // A request that names no day opens that day too, and another view's request opens that view.
        click("top_back")
        tapReminder(mood.id, null)
        assertEquals(today, date(mood.id))
        tapReminder(mood.id, yesterday)
        compose.onNodeWithTag("questions_${mood.id}_qn_mood_1").assertIsSelected()
    }

    @Test
    fun aRequestForAViewThatIsGoneFallsBackAndItsDayIsForgotten() {
        compose.setContent { App() }

        tapReminder("qst_gone", yesterday)

        compose.onNodeWithTag("home_settings").assertExists()
        assertEquals(1, consumed)
        click("home_questions_${sleep.id}")
        assertEquals(today, date(sleep.id))
    }

    @Test
    fun aSavedDayTellsWhichViewAndWhichDayEatingsToo() {
        compose.setContent { App() }
        val ref = LoggedViewRef(LoggedViewKind.Questions, sleep.id)

        tapReminder(sleep.id, yesterday)
        click("questions_${sleep.id}_qn_rest_0")
        click("questions_${sleep.id}_save")
        assertEquals(listOf(listOf(ref, yesterday, listOf(mapOf("qn_rest" to "a4")), listOf(mapOf("qn_rest" to "a1")))), logged)

        // The removal of a day is told too: whoever answers a reminder reads what is stored.
        click("questions_${sleep.id}_history_$yesterday")
        click("questions_${sleep.id}_delete")
        click("questions_${sleep.id}_delete")
        assertEquals(listOf(ref, yesterday, listOf(mapOf("qn_rest" to "a1")), emptyList<Map<String, String>>()), logged.last())
        assertEquals(2, logged.size)

        click("top_back")
        click("home_questions_eating")
        click("questions_eating_quality_2")
        click("questions_eating_save")
        assertEquals(3, logged.size)
        assertEquals(LoggedViewRef(LoggedViewKind.Questions, "eating"), logged.last()[0])
        assertEquals(1, state.questionEntries.count { it.viewId == "eating" })
    }
}
