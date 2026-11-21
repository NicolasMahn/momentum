package com.example.workouttracker.feature.logactivity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.CyclingActivityId
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.applyingEdits
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.Routine
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.checklist.ViewIcon
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A duration belongs to the record it was recorded with or typed for, as the record's sets do: a
 * session opens on its own minutes whether it is reached through the day's records or through its
 * activity's days, finishing it untouched keeps them, and leaving it leaves them behind instead of
 * carrying them to another day or session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class ActivitySessionDurationTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = "2026-11-17"
    private val sunday = "2026-11-15"
    private val monday = "2026-11-16"
    private val thursday = "2026-11-12"

    /** Mobility, listed fifth and so under Other, with one routine day. */
    private val configuration = testActivityConfiguration().applyingEdits(
        listOf(
            ActivityEdit.AddActivity(
                ListedActivity(
                    "act_mobility", "Mobility", ViewIcon.Bolt,
                    routine = Routine(
                        listOf(
                            RoutineDay(
                                "day_desk", "Desk break",
                                listOf(
                                    RoutineExercise("ex_hips", "Hip circles", 1, 8..12, weight = null, startReps = 8),
                                    RoutineExercise("ex_wall", "Wall slides", 1, 8..12, weight = null, startReps = 10),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        LocalDate.parse(today),
    ).copy(logsDuration = true)

    private fun deskBreak(dateKey: String, timestampMillis: Long, hips: Double, wall: Double, minutes: Int) = WorkoutSession(
        id = "$dateKey-day_desk", activityId = "act_mobility", day = "day_desk", dateKey = dateKey, timestampMillis = timestampMillis,
        exerciseLogs = listOf(
            ExerciseLog("ex_hips", "Hip circles", listOf(LoggedSet(null, hips))),
            ExerciseLog("ex_wall", "Wall slides", listOf(LoggedSet(null, wall))),
        ),
        durationMinutes = minutes,
    )

    private val sundaySession = deskBreak(sunday, timestampMillis = 300L, hips = 9.0, wall = 10.0, minutes = 9)
    private val thursdaySession = deskBreak(thursday, timestampMillis = 100L, hips = 8.0, wall = 8.0, minutes = 3)
    private val recorded = listOf(sundaySession, thursdaySession)
    private var state by mutableStateOf(AppState(activity = configuration, sessions = recorded))
    private val restoration by lazy { StateRestorationTester(compose) }

    private fun show() {
        restoration.setContent {
            WorkoutTrackerTheme {
                ActivityLogScreen(state = state, onStateChange = { state = it; true }, onWorkoutFinished = {}, todayKey = today)
            }
        }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun input(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun openMobilityOn(dateKey: String) {
        click("activity_choice_custom")
        click("activity_other_act_mobility")
        click("activity_history_$dateKey")
    }

    private fun sessionOn(dateKey: String): WorkoutSession = state.sessions.single { it.dateKey == dateKey }

    /** The way that lost Sunday's minutes: through Mobility's days, not the day's records. */
    @Test
    fun aSessionOpensOnItsMinutesFromEitherPathAndFinishingItUntouchedKeepsThem() {
        show()
        openMobilityOn(sunday)
        click("gym_plan_day_desk")
        assertEquals("9", input("workout_ex_hips_reps_0"))
        assertEquals("10", input("workout_ex_wall_reps_0"))
        assertEquals("9", input("activity_duration"))

        // The screen recreated keeps whose minutes the field holds.
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("9", input("activity_duration"))
        click("finish_workout")
        assertEquals(recorded, state.sessions)

        click("activity_history_$sunday")
        click("activity_entry_edit_workout:${sundaySession.id}")
        assertEquals("9", input("activity_duration"))
        click("finish_workout")
        assertEquals(recorded, state.sessions)
    }

    /** Monday was never logged, so it opens empty however the form came there, and stays without minutes. */
    @Test
    fun aSessionsMinutesStayBehindWhenAnotherDayIsChosen() {
        show()
        click("activity_history_$sunday")
        click("activity_entry_edit_workout:${sundaySession.id}")
        assertEquals("9", input("activity_duration"))

        click("activity_history_$monday")
        assertEquals("", input("activity_duration"))
        click("gym_plan_day_desk")
        assertEquals("", input("activity_duration"))
        type("workout_ex_hips_reps_0", "8")
        click("finish_workout")
        assertEquals(null, sessionOn(monday).durationMinutes)
        assertEquals(listOf(sundaySession, thursdaySession), state.sessions.filter { it.dateKey != monday })
    }

    /** Leaving a session for its activity's days or for an activity without a routine leaves its minutes too. */
    @Test
    fun aSessionsMinutesStayBehindWhenThePlanOrTheActivityChanges() {
        show()
        click("activity_history_$sunday")
        click("activity_entry_edit_workout:${sundaySession.id}")
        click("gym_change_plan")
        assertEquals("", input("activity_duration"))

        click("gym_plan_day_desk")
        assertEquals("9", input("activity_duration"))
        click("activity_choice_cycling")
        assertEquals("", input("activity_duration"))
        click("activity_save")
        assertEquals(null, state.activityEntries.single { it.activityId == CyclingActivityId && it.dateKey == sunday }.durationMinutes)
        assertEquals(recorded, state.sessions)
    }

    /**
     * Minutes typed before a day is chosen are for the session it opens. A session that recorded
     * minutes of its own opens on those, as it does from the day's records.
     */
    @Test
    fun minutesTypedBeforeTheDayIsChosenGoToItsNewSession() {
        show()
        openMobilityOn(monday)
        type("activity_duration", "20")
        click("gym_plan_day_desk")
        assertEquals("20", input("activity_duration"))
        type("workout_ex_hips_reps_0", "8")
        click("finish_workout")
        assertEquals(20, sessionOn(monday).durationMinutes)

        click("activity_history_$sunday")
        type("activity_duration", "20")
        click("gym_plan_day_desk")
        assertEquals("9", input("activity_duration"))
        click("finish_workout")
        assertEquals(sundaySession, sessionOn(sunday))
    }

    @Test
    fun anOpenSessionsMinutesCanBeChangedOrCleared() {
        show()
        openMobilityOn(sunday)
        click("gym_plan_day_desk")
        type("activity_duration", "12")
        click("finish_workout")
        assertEquals(sundaySession.copy(durationMinutes = 12), sessionOn(sunday))

        click("gym_plan_day_desk")
        assertEquals("12", input("activity_duration"))
        type("activity_duration", "")
        click("finish_workout")
        assertEquals(sundaySession.copy(durationMinutes = null), sessionOn(sunday))
        assertEquals(thursdaySession, sessionOn(thursday))
    }
}
