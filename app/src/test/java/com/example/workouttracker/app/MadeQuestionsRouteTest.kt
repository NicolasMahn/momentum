package com.example.workouttracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.QuestionEntryOrder
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.applyingQuestionsEdits
import com.example.workouttracker.domain.questions.deleteQuestions
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.questions.testQuestion
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.weight.transcript
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

/**
 * A questions view a person made has a tile on Home that opens its screen, as a made measurement
 * has: two views are two places, the one selected survives process death, and one that is gone or
 * hidden falls back to Home. In the streak its tile wears the socket and the coin for the day today
 * asks about, and the token page has its bank.
 */
@RunWith(RobolectricTestRunner::class)
class MadeQuestionsRouteTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.parse(currentDateKey())
    private val sleep = testMadeQuestions(madeOn = today.minusDays(30))
    private val mood = testMadeQuestions(
        id = "qst_m00d",
        label = "Mood",
        questions = listOf(testQuestion(id = "qn_mood", text = "How was your mood?", answers = 3)),
        about = AboutDay.Yesterday,
        madeOn = today.minusDays(30),
    )
    private val waist = testMadeMeasurement()
    private val made = AppState().withWeight().withEating().withReading().withActivity().let { it.copy(measurements = it.measurements + waist, questions = it.questions + sleep + mood) }
    private var state by mutableStateOf(made)
    private var visibility by mutableStateOf(FeatureVisibilitySettings())

    @Composable
    private fun App() {
        WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected, featureVisibility = visibility) }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun said(text: String): Boolean = "text=\"$text\"" in compose.transcript()

    /** Where a tile sits in reading order: its row, then its column. */
    private fun place(tag: String): Pair<Float, Float> = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.let { it.y to it.x }

    @Test
    fun eachMadeQuestionsViewHasATileAfterTheMeasurementsThatWearsNoStatus() {
        compose.setContent { App() }

        val tiles = listOf("home_measurement_weight", "home_measurement_${waist.id}", "home_questions_${sleep.id}", "home_questions_${mood.id}")
        val places = tiles.map(::place)
        assertEquals(places.sortedWith(compareBy({ it.first }, { it.second })), places)
        assertTrue(said("Sleep") && said("Mood"))

        // Outside the streak a tile says nothing of today, answered or not.
        val status = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
        compose.onNodeWithTag("home_questions_${sleep.id}").assert(!status)
        compose.onNodeWithTag("home_questions_${mood.id}").assert(!status)
    }

    /** Each opens on the day today asks about and stores under its own ID: today for one about today, yesterday for one about yesterday. */
    @Test
    fun twoMadeViewsAreTwoPlacesEachWithItsOwnQuestionsAndItsOwnDay() {
        compose.setContent { App() }

        click("home_questions_${sleep.id}")
        assertTrue(compose.transcript().toString(), said("Sleep") && said("How rested do you feel?"))
        click("questions_${sleep.id}_qn_rest_2")
        click("questions_${sleep.id}_save")
        assertEquals(listOf(Triple(sleep.id, today.toString(), mapOf("qn_rest" to "a3"))), state.questionEntries.map { Triple(it.viewId, it.dateKey, it.answers) })

        click("top_back")
        click("home_questions_${mood.id}")
        val shown = compose.transcript()
        assertTrue(shown.toString(), said("Mood") && said("How was your mood?"))
        assertFalse(shown.toString(), shown.any { "How rested" in it })
        click("questions_${mood.id}_qn_mood_0")
        click("questions_${mood.id}_save")
        assertEquals(
            setOf(Triple(sleep.id, today.toString(), mapOf("qn_rest" to "a3")), Triple(mood.id, today.minusDays(1).toString(), mapOf("qn_mood" to "a1"))),
            state.questionEntries.map { Triple(it.viewId, it.dateKey, it.answers) }.toSet(),
        )
    }

    @Test
    fun theOpenViewSurvivesProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        click("home_questions_${mood.id}")
        click("questions_${mood.id}_qn_mood_2")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        val shown = compose.transcript()
        assertTrue(shown.toString(), said("How was your mood?"))
        assertFalse(shown.toString(), shown.any { "How rested" in it })
        // The answer picked and not saved came back with the view, and nothing was stored for it.
        assertTrue(shown.toString(), "[questions_${mood.id}_qn_mood_2] selected" in shown)
        assertEquals(emptyList<QuestionEntry>(), state.questionEntries.filter { it.viewId == mood.id })
    }

    /** A measurement and a questions view under one ID are two places: the destination says which kind is open. */
    @Test
    fun aViewThatIsGoneOrHiddenFallsBackToHomeAndAMeasurementOfItsIdIsAnotherPlace() {
        val twin = testMadeMeasurement(id = sleep.id, label = "Twin")
        state = made.copy(measurements = made.measurements + twin)
        compose.setContent { App() }

        click("home_measurement_${twin.id}")
        compose.onNodeWithTag("measurement_value").assertExists()
        click("top_back")
        click("home_questions_${sleep.id}")
        compose.onNodeWithTag("questions_${sleep.id}_save").assertExists()
        compose.onNodeWithTag("measurement_value").assertDoesNotExist()

        // Hidden, its screen closes and its tile is gone; the measurement of its ID keeps its own.
        visibility = FeatureVisibilitySettings().hiding(sleep.ref)
        compose.waitForIdle()
        compose.onNodeWithTag("questions_${sleep.id}_save").assertDoesNotExist()
        compose.onNodeWithTag("home_questions_${sleep.id}").assertDoesNotExist()
        compose.onNodeWithTag("home_measurement_${twin.id}").assertExists()
        compose.onNodeWithTag("home_questions_${mood.id}").assertExists()

        // Deleted while its screen is open, it falls back to Home too.
        click("home_questions_${mood.id}")
        compose.onNodeWithTag("questions_${mood.id}_save").assertExists()
        state = state.deleteQuestions(mood.id)
        compose.waitForIdle()
        compose.onNodeWithTag("questions_${mood.id}_save").assertDoesNotExist()
        compose.onNodeWithTag("home_questions_${mood.id}").assertDoesNotExist()
        compose.onNodeWithTag("home_settings").assertExists()
    }

    /** The views in the streak since a week ago, each answered for the seven days before today, and for [answered] days more. */
    private fun joined(vararg answered: LocalDate): AppState = made
        .copy(
            questions = made.questions.map { it.copy(mo = TrackerMoPolicy(enabled = true)) },
            questionEntries = listOf(sleep, mood).flatMap { view ->
                ((1L..7L).map(today::minusDays) + answered).distinct().map { day -> QuestionEntry("${view.id}-$day", view.id, day.toString(), 1) }
            }.sortedWith(QuestionEntryOrder),
        )
        .applyingQuestionsEdits(sleep.id, listOf(QuestionsEdit.JoinRewards), today.minusDays(7))
        .applyingQuestionsEdits(mood.id, listOf(QuestionsEdit.JoinRewards), today.minusDays(7))

    /** What TalkBack hears of a tile's badge, or null while it wears none. */
    private fun spokenBadge(tile: String): String? {
        compose.onNodeWithTag(tile).assertExists()
        return listOf("Still open today", "Done today", "Kept today").singleOrNull { words ->
            compose.onAllNodes(hasContentDescription(words) and hasAnyAncestor(hasTestTag(tile)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * In the streak a tile wears the socket until the day today asks about is answered and the coin
     * from then on: today's entry for a view about today, yesterday's for one about yesterday. The
     * token page has a row for each, after a measurement's.
     */
    @Test
    fun aJoinedViewsTileWearsTheSocketAndTheCoinAndTheTokenPageHasItsRow() {
        compose.setContent { App() }
        val sleepTile = "home_questions_${sleep.id}"
        val moodTile = "home_questions_${mood.id}"
        assertNull(spokenBadge(sleepTile))

        // Yesterday is answered, today is not: the view about yesterday is done, the one about today open.
        state = joined()
        compose.waitForIdle()
        assertEquals("Still open today", spokenBadge(sleepTile))
        assertEquals("Done today", spokenBadge(moodTile))

        state = joined(today)
        compose.waitForIdle()
        assertEquals("Done today", spokenBadge(sleepTile))

        // Yesterday's entry removed, the view about yesterday is open again, whatever today holds.
        state = joined(today).let { it.copy(questionEntries = it.questionEntries.filterNot { entry -> entry.viewId == mood.id && entry.dateKey == today.minusDays(1).toString() }) }
        compose.waitForIdle()
        assertEquals("Still open today", spokenBadge(moodTile))

        // The token page: each bank under its name. Seven kept days since joining earned the second token.
        state = joined()
        compose.waitForIdle()
        click("home_streak")
        val sleepBank = hasContentDescription("Sleep: 2 tokens. next token: 0/7 days")
        compose.onNodeWithTag("streak_questions_${sleep.id}", useUnmergedTree = true)
            .assert(sleepBank or hasAnyAncestor(sleepBank))
        compose.onNodeWithTag("streak_questions_${mood.id}").assertExists()

        // Hidden or out of the streak, a view has no row.
        visibility = FeatureVisibilitySettings().hiding(sleep.ref)
        state = state.applyingQuestionsEdits(mood.id, listOf(QuestionsEdit.LeaveRewards), today)
        compose.waitForIdle()
        compose.onNodeWithTag("streak_questions_${sleep.id}").assertDoesNotExist()
        compose.onNodeWithTag("streak_questions_${mood.id}").assertDoesNotExist()
    }
}
