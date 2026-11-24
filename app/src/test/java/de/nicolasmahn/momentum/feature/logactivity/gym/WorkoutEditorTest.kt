package de.nicolasmahn.momentum.feature.logactivity.gym

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.BoulderingActivityId
import de.nicolasmahn.momentum.domain.activity.ListedActivity
import de.nicolasmahn.momentum.domain.activity.routine.ExerciseLog
import de.nicolasmahn.momentum.domain.activity.routine.ExerciseWeight
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.activity.routine.Routine
import de.nicolasmahn.momentum.domain.activity.routine.RoutineDay
import de.nicolasmahn.momentum.domain.activity.routine.RoutineExercise
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import de.nicolasmahn.momentum.domain.activity.testActivityConfiguration
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor on an exercise done without a weight, beside one done with: what its rows show and
 * what its presets write, and what Finish leaves alone. The real editor over a routine held here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class WorkoutEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private val campus = RoutineExercise("ex_campus", "Campus board", 3, 5..8, weight = null, startReps = 6)
    private val hang = RoutineExercise("ex_hang", "Hangboard", 3, 5..8, ExerciseWeight(20.0, 2.5), startReps = 6)
    private val session = RoutineDay("day_s", "Session", listOf(campus, hang))
    private val bouldering = ListedActivity(BoulderingActivityId, "Bouldering", ViewIcon.Bouldering, routine = Routine(listOf(session)))
    private val configuration = testActivityConfiguration().let { rules -> rules.copy(activities = rules.activities.map { if (it.id == BoulderingActivityId) bouldering else it }) }

    private var state by mutableStateOf(AppState())
    private var writes = 0
    private var finishedWith: Int? = null

    private fun show(sessions: List<WorkoutSession> = emptyList()) {
        state = AppState(activity = configuration, sessions = sessions)
        compose.setContent {
            WorkoutTrackerTheme {
                // With the room the Activity screen leaves it.
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GymWorkoutEditor(
                        configuration = configuration,
                        activity = bouldering,
                        selectedDay = session.id,
                        dateKey = Today,
                        durationMinutes = null,
                        durationIsValid = true,
                        state = state,
                        onStateChange = { state = it; writes += 1; true },
                        onFinished = { finishedWith = it },
                    )
                }
            }
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
        compose.waitForIdle()
    }

    private fun input(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun storedSets(exerciseId: String): List<LoggedSet> =
        state.sessionFor(bouldering, session, Today)?.exerciseLogs?.first { it.exerciseId == exerciseId }?.sets.orEmpty()

    /** Reps alone, with "Last" and no step; the exercise beside it keeps its field, its steps and the pencil. "Last" writes the starting reps without a weight. */
    @Test
    fun anExerciseDoneWithoutAWeightShowsRepsAloneAndLastFillsThem() {
        show()
        compose.onNodeWithTag("workout_ex_campus_weight_0").assertDoesNotExist()
        compose.onNodeWithTag("workout_ex_campus_reps_0").assertExists()
        compose.onNodeWithTag("workout_ex_campus_reps_2").assertExists()
        compose.onNodeWithTag("workout_ex_campus_weight_up").assertDoesNotExist()
        compose.onNodeWithTag("workout_ex_campus_use_last").assertExists()
        compose.onNodeWithTag("workout_ex_campus_step").assertDoesNotExist()
        compose.onNodeWithTag("workout_ex_hang_weight_0").assertExists()
        compose.onNodeWithTag("workout_ex_hang_weight_up").assertExists()
        compose.onNodeWithTag("workout_ex_hang_step").assertExists()
        assertEquals("20", input("workout_ex_hang_weight_0"))

        click("workout_ex_campus_use_last")
        assertEquals(List(3) { LoggedSet(weightKg = null, reps = 6.0) }, storedSets("ex_campus"))
        assertEquals(1, writes)
    }

    /**
     * A set recorded without a weight on an exercise done with one since shows an empty field,
     * never the fallback, which only the empty row takes; finished untouched, the session is left
     * as it was and counts its recorded sets.
     */
    @Test
    fun aSetRecordedWithoutAWeightShowsAnEmptyFieldAndFinishingUntouchedWritesNothing() {
        val recorded = WorkoutSession(
            id = "$Today-day_s", activityId = BoulderingActivityId, day = session.id, dateKey = Today, timestampMillis = 1L,
            exerciseLogs = listOf(ExerciseLog("ex_hang", "Hangboard", listOf(LoggedSet(null, 6.0), LoggedSet(null, 6.0)))),
        )
        show(listOf(recorded))
        assertEquals("", input("workout_ex_hang_weight_0"))
        assertEquals("6", input("workout_ex_hang_reps_0"))
        assertEquals("", input("workout_ex_hang_weight_1"))
        assertEquals("20", input("workout_ex_hang_weight_2"))
        assertEquals("", input("workout_ex_hang_reps_2"))

        click("finish_workout")
        assertEquals(0, writes)
        assertEquals(2, finishedWith)
        assertEquals(listOf(recorded), state.sessions)
        compose.onNodeWithTag("finish_workout_error").assertDoesNotExist()
    }

    /** A recorded weight on an exercise done without one now is written back exactly as it was when its reps are retyped; the new row records none. */
    @Test
    fun aRecordedWeightTravelsUnseenThroughARetypedSet() {
        val recorded = WorkoutSession(
            id = "$Today-day_s", activityId = BoulderingActivityId, day = session.id, dateKey = Today, timestampMillis = 1L,
            exerciseLogs = listOf(ExerciseLog("ex_campus", "Campus board", listOf(LoggedSet(22.125, 6.0)))),
        )
        show(listOf(recorded))
        compose.onNodeWithTag("workout_ex_campus_weight_0").assertDoesNotExist()
        assertEquals("6", input("workout_ex_campus_reps_0"))

        compose.onNodeWithTag("workout_ex_campus_reps_0").performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("7")) }
        compose.onNodeWithTag("workout_ex_campus_reps_1").performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("5")) }
        compose.waitForIdle()
        click("finish_workout")
        assertEquals(listOf(LoggedSet(22.125, 7.0), LoggedSet(null, 5.0)), storedSets("ex_campus"))
        assertEquals(2, finishedWith)
    }

    /**
     * "Last" on an exercise done without a weight fills reps and keeps the weight a recorded set
     * holds unseen, exactly as recorded, though a field would show it rounded; the rows it adds
     * record none.
     */
    @Test
    fun lastKeepsTheWeightARecordedSetHoldsUnseen() {
        val recorded = WorkoutSession(
            id = "$Today-day_s", activityId = BoulderingActivityId, day = session.id, dateKey = Today, timestampMillis = 1L,
            exerciseLogs = listOf(ExerciseLog("ex_campus", "Campus board", listOf(LoggedSet(22.125, 8.0)))),
        )
        show(listOf(recorded))
        click("workout_ex_campus_use_last")
        compose.onNodeWithTag("workout_save_error_ex_campus").assertDoesNotExist()
        assertEquals(listOf(LoggedSet(22.125, 6.0), LoggedSet(null, 6.0), LoggedSet(null, 6.0)), storedSets("ex_campus"))
    }

    /**
     * "Last" is the workout of the latest date, not the one saved last: the earlier day, corrected
     * after the later one, carries the later timestamp. A new session shows the later day's
     * averages, offers "Increase weight" from it and starts an empty row from its weight; "Last"
     * writes its reps, and what this session saves never becomes its own "Last".
     */
    @Test
    fun lastIsTheWorkoutOfTheLatestDateNotTheLatestSave() {
        fun earlier(dateKey: String, at: Long, campusReps: Double, hang: LoggedSet) = WorkoutSession(
            id = "$dateKey-day_s", activityId = BoulderingActivityId, day = session.id, dateKey = dateKey, timestampMillis = at,
            exerciseLogs = listOf(ExerciseLog("ex_campus", "Campus board", List(3) { LoggedSet(null, campusReps) }), ExerciseLog("ex_hang", "Hangboard", List(3) { hang })),
        )
        show(listOf(earlier("2026-10-07", at = 20L, campusReps = 8.0, LoggedSet(25.0, 8.0)), earlier("2026-10-04", at = 30L, campusReps = 6.0, LoggedSet(22.5, 6.0))))
        compose.onNode(hasText("Last avg 8 reps")).assertExists()
        compose.onNode(hasText("Last avg 25 kg × 8")).assertExists()
        compose.onNode(hasText("Increase weight")).assertExists()
        assertEquals("25", input("workout_ex_hang_weight_0"))

        click("workout_ex_campus_use_last")
        assertEquals(List(3) { LoggedSet(null, 8.0) }, storedSets("ex_campus"))
        type("workout_ex_campus_reps_0", "5")
        compose.onNodeWithTag("workout_ex_campus_reps_0").performImeAction()
        compose.waitForIdle()
        assertEquals(listOf(5.0, 8.0, 8.0), storedSets("ex_campus").map { it.reps })
        compose.onNode(hasText("Last avg 8 reps")).assertExists()
    }

    /** A forgotten earlier day filled in late shows the workout before it as "Last", never one after it. */
    @Test
    fun anEarlierDayFilledInLateShowsTheWorkoutBeforeItAsLast() {
        fun on(dateKey: String, at: Long, campusReps: Double) = WorkoutSession(
            id = "$dateKey-day_s", activityId = BoulderingActivityId, day = session.id, dateKey = dateKey, timestampMillis = at,
            exerciseLogs = listOf(ExerciseLog("ex_campus", "Campus board", List(3) { LoggedSet(null, campusReps) })),
        )
        show(listOf(on("2026-10-07", at = 10L, campusReps = 8.0), on("2026-10-21", at = 20L, campusReps = 5.0)))
        compose.onNode(hasText("Last avg 8 reps")).assertExists()
    }

    /** A set corrected from 6 to 7 and saved, then back to 6, is written as 6 on Finish, though its row reads as the editor opened it. */
    @Test
    fun aCorrectionBackToTheOpeningRowsAfterASaveIsWrittenOnFinish() {
        val recorded = WorkoutSession(
            id = "$Today-day_s", activityId = BoulderingActivityId, day = session.id, dateKey = Today, timestampMillis = 1L,
            exerciseLogs = listOf(ExerciseLog("ex_hang", "Hangboard", listOf(LoggedSet(20.0, 6.0)))),
        )
        show(listOf(recorded))
        type("workout_ex_hang_reps_0", "7")
        compose.onNodeWithTag("workout_ex_hang_reps_0").performImeAction()
        compose.waitForIdle()
        assertEquals(listOf(LoggedSet(20.0, 7.0)), storedSets("ex_hang"))

        type("workout_ex_hang_reps_0", "6")
        click("finish_workout")
        assertEquals(listOf(LoggedSet(20.0, 6.0)), storedSets("ex_hang"))
        assertEquals(1, finishedWith)
    }

    /**
     * Sets recorded without a weight on an exercise done with one since stay what they were beside
     * a new set: a third set with a weight is saved and finished with them, unchanged.
     */
    @Test
    fun setsRecordedWithoutAWeightAreKeptBesideANewWeightedSet() {
        show(listOf(hangRecordedWithoutWeights()))
        type("workout_ex_hang_reps_2", "7")
        compose.onNodeWithTag("workout_ex_hang_reps_2").performImeAction()
        compose.waitForIdle()
        compose.onNodeWithTag("workout_save_error_ex_hang").assertDoesNotExist()
        assertEquals(listOf(LoggedSet(null, 6.0), LoggedSet(null, 6.0), LoggedSet(20.0, 7.0)), storedSets("ex_hang"))

        click("finish_workout")
        compose.onNodeWithTag("finish_workout_error").assertDoesNotExist()
        assertEquals(3, finishedWith)
        assertEquals(listOf(LoggedSet(null, 6.0), LoggedSet(null, 6.0), LoggedSet(20.0, 7.0)), storedSets("ex_hang"))
    }

    /** A recorded set whose reps are retyped is a new set, so on an exercise done with a weight it asks for one before it is written. */
    @Test
    fun aRecordedSetRetypedIsANewSetAndAsksForItsWeight() {
        show(listOf(hangRecordedWithoutWeights()))
        type("workout_ex_hang_reps_0", "8")
        click("finish_workout")
        compose.onNodeWithTag("finish_workout_error").assertExists()
        assertEquals(null, finishedWith)
        assertEquals(listOf(LoggedSet(null, 6.0), LoggedSet(null, 6.0)), storedSets("ex_hang"))

        type("workout_ex_hang_weight_0", "20")
        click("finish_workout")
        assertEquals(listOf(LoggedSet(20.0, 8.0), LoggedSet(null, 6.0)), storedSets("ex_hang"))
        assertEquals(2, finishedWith)
    }

    /**
     * The pencil changes the routine's step where the presets were: a step the routine cannot have
     * is refused in the field's label and stores nothing; a stored one is the routine's, and − and
     * + add it. What was typed in a set and not saved yet stays through the change.
     */
    @Test
    fun theStepIsChangedInPlaceAndKeepsWhatWasTyped() {
        show()
        type("workout_ex_hang_reps_1", "7")
        click("workout_ex_hang_step")
        compose.onNodeWithTag("workout_ex_hang_weight_up").assertDoesNotExist()
        assertEquals("2.5", input("workout_ex_hang_step_field"))

        val before = writes
        type("workout_ex_hang_step_field", "25")
        click("workout_ex_hang_step_save")
        assertEquals(before, writes)
        compose.onNodeWithTag("workout_ex_hang_step_field").assertExists()

        type("workout_ex_hang_step_field", "1,25")
        click("workout_ex_hang_step_save")
        assertEquals(1.25, state.activity!!.routineExercise("ex_hang")!!.weight!!.stepKg, 0.0)
        compose.onNodeWithTag("workout_ex_hang_step_field").assertDoesNotExist()
        assertEquals("7", input("workout_ex_hang_reps_1"))

        click("workout_ex_hang_weight_up")
        assertEquals("21.25", input("workout_ex_hang_weight_0"))
    }

    /** On a 320 dp phone in German a weighted exercise's four presets stand in two rows of two, every label whole. */
    @Test
    @Config(qualifiers = "de-w320dp-h1600dp-xhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun onANarrowPhoneInGermanThePresetsStandInTwoRowsReadWhole() {
        show()
        val presets = listOf("use_last" to "Zuvor", "weight_down" to "2,5 kg", "weight_up" to "2,5 kg").map { (preset, label) -> "workout_ex_hang_$preset" to label }
        presets.forEach { (tag, label) -> assertReadWhole(tag, label) }
        assertEquals(top("workout_ex_hang_use_last"), top("workout_ex_hang_weight_down"), 0f)
        assertTrue(top("workout_ex_hang_weight_up") > top("workout_ex_hang_use_last"))
        assertEquals(top("workout_ex_hang_weight_up"), top("workout_ex_hang_step"), 0f)
    }

    /** Where every label fits, the three labelled presets and the pencil share one row. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whereTheyFitThePresetsShareOneRow() {
        show()
        listOf("use_last" to "Last", "weight_down" to "2.5 kg", "weight_up" to "2.5 kg").forEach { (preset, label) -> assertReadWhole("workout_ex_hang_$preset", label) }
        val row = top("workout_ex_hang_use_last")
        listOf("weight_down", "weight_up", "step").forEach { assertEquals(it, row, top("workout_ex_hang_$it"), 0f) }
    }

    private fun top(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    private fun assertReadWhole(tag: String, label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.single()
        assertEquals(label, label.length, layout.getLineEnd(0, visibleEnd = true))
        assertTrue("$label ends at ${layout.getLineRight(0)} px in ${layout.size.width} px", layout.getLineRight(0) <= layout.size.width)
    }

    private fun hangRecordedWithoutWeights() = WorkoutSession(
        id = "$Today-day_s", activityId = BoulderingActivityId, day = session.id, dateKey = Today, timestampMillis = 1L,
        exerciseLogs = listOf(ExerciseLog("ex_hang", "Hangboard", listOf(LoggedSet(null, 6.0), LoggedSet(null, 6.0)))),
    )

    private companion object {
        const val Today = "2026-10-14"
    }
}
