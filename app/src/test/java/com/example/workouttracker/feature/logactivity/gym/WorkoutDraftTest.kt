package com.example.workouttracker.feature.logactivity.gym

import com.example.workouttracker.core.format.formatMetricNumber
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.SeededGym
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.ExerciseWeight
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.Routine
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.checklist.ViewIcon
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutDraftTest {
    private val swedish = Locale.forLanguageTag("sv")

    @Test
    fun theLoadButtonsAdjustAValueWrittenWithARealMinusSign() {
        val draft = WorkoutDraft(mapOf("pull_up" to listOf(SetDraft(weightText = formatted(-3.0)))))

        val adjusted = draft.adjustExerciseWeight("pull_up", 2.5, swedish)

        assertEquals(formatted(-0.5), adjusted.setsByExercise.getValue("pull_up").single().weightText)
    }

    @Test
    fun anEmptyFieldStartsFromZeroButUnreadableTextIsLeftAlone() {
        val draft = WorkoutDraft(mapOf("row" to listOf(SetDraft(weightText = ""), SetDraft(weightText = "abc"))))

        val adjusted = draft.adjustExerciseWeight("row", 2.5, swedish)

        assertEquals(listOf("2,5", "abc"), adjusted.setsByExercise.getValue("row").map(SetDraft::weightText))
    }

    /**
     * A recorded session opens with every set it recorded: a fourth and fifth set stay as rows of a
     * three-set exercise, an exercise the routine never knew has its recorded rows and no weight to
     * start an empty one from, and a new row of a known exercise starts from the last recorded weight.
     */
    @Test
    fun theDraftHoldsEveryRecordedSetAndStartsEmptyRowsFromTheLastWeight() {
        val day = SeededGym.day("A")!!
        val bench = day.exercises.first { it.id == "bench_press" }
        val recorded = WorkoutSession(
            "s", "gym", "A", "2026-09-07", 1L,
            listOf(
                ExerciseLog("bench_press", "Bench Press", listOf(LoggedSet(20.0, 8.0), LoggedSet(20.0, 8.0), LoggedSet(20.0, 8.0), LoggedSet(22.5, 6.0), LoggedSet(22.5, 5.0))),
                ExerciseLog("ex_gone", "Leg press", listOf(LoggedSet(80.0, 12.0))),
            ),
        )
        val state = AppState(sessions = listOf(recorded))
        val exercises = state.sessionExercises(SeededGym, day, "2026-09-07")

        val draft = createDraft(SeededGym, day, exercises, state, "2026-09-07", swedish)

        assertEquals(5, draft.setsByExercise.getValue("bench_press").size)
        assertEquals(listOf("20", "20", "20", "22,5", "22,5"), draft.setsByExercise.getValue("bench_press").map(SetDraft::weightText))
        assertEquals(listOf("8", "8", "8", "6", "5"), draft.setsByExercise.getValue("bench_press").map(SetDraft::repsText))
        assertEquals(listOf(SetDraft("80", "12", recorded = LoggedSet(80.0, 12.0))), draft.setsByExercise.getValue("ex_gone"))
        // The other exercises of the day start from their starting weight, with nothing recorded.
        val squat = day.exercises.first { it.id == "back_squat" }
        assertEquals(List(squat.sets) { SetDraft(formatted(squat.weight!!.startKg), "") }, draft.setsByExercise.getValue("back_squat"))
        assertEquals(bench.sets, 3)
    }

    /**
     * An exercise's recorded sets are facts the editor carries through a change of its weight.
     * Done without a weight since, a recorded set keeps its weight in the row unseen and writes
     * it back when its reps are retyped, while a new row starts from nothing; done with a weight
     * again, a set recorded without one shows an empty field, and only a new row takes the
     * fallback.
     */
    @Test
    fun anExerciseEditedWeightedThenWeightlessThenWeightedAgainKeepsEveryRecordedWeight() {
        val weighted = RoutineExercise("ex_hang", "Hangboard", 3, 5..8, ExerciseWeight(20.0, 2.5), 6)
        val weightless = weighted.copy(weight = null)
        fun activity(exercise: RoutineExercise) = ListedActivity("act_b", "Bouldering", ViewIcon.Bouldering, routine = Routine(listOf(RoutineDay("day_s", "Session", listOf(exercise)))))
        fun draftOf(exercise: RoutineExercise, state: AppState): List<SetDraft> {
            val activity = activity(exercise)
            val day = activity.day("day_s")!!
            return createDraft(activity, day, state.sessionExercises(activity, day, "2026-09-07"), state, "2026-09-07", swedish).setsByExercise.getValue("ex_hang")
        }
        fun AppState.replaced(exercise: RoutineExercise, sets: List<LoggedSet>) = withReplacedWorkoutSets(activity(exercise), activity(exercise).day("day_s")!!, exercise.planned, sets, "2026-09-07")

        val recordedWeighted = AppState().replaced(weighted, listOf(LoggedSet(20.0, 8.0), LoggedSet(22.5, 6.0)))
        assertEquals(
            listOf(SetDraft("", "8", LoggedSet(20.0, 8.0), unseenWeightKg = 20.0), SetDraft("", "6", LoggedSet(22.5, 6.0), unseenWeightKg = 22.5), SetDraft("", "")),
            draftOf(weightless, recordedWeighted),
        )

        val retyped = listOf(SetDraft(repsText = "9", unseenWeightKg = 20.0), SetDraft(repsText = "6", unseenWeightKg = 22.5), SetDraft(repsText = "7")).map { requireNotNull(it.toLoggedSetOrNull(weighted = false)) }
        assertEquals(listOf(LoggedSet(20.0, 9.0), LoggedSet(22.5, 6.0), LoggedSet(null, 7.0)), retyped)
        val recordedWeightless = recordedWeighted.replaced(weightless, retyped)

        // Weighted again, with a fourth set asked: the recorded weights show, the set without one
        // an empty field, and the empty row the average of the session's recorded weights.
        assertEquals(
            listOf(SetDraft("20", "9", LoggedSet(20.0, 9.0)), SetDraft("22,5", "6", LoggedSet(22.5, 6.0)), SetDraft("", "7", LoggedSet(null, 7.0)), SetDraft("21,25", "")),
            draftOf(weighted.copy(sets = 4), recordedWeightless),
        )
        assertEquals(retyped, recordedWeightless.sessions.single().exerciseLogs.single().sets)
    }

    /** "Last" fills the reps of the newest session and its weight only where the exercise has a weight to record. */
    @Test
    fun lastFillsRepsAloneOnAnExerciseDoneWithoutAWeight() {
        val last = ExerciseWorkoutSummary(averageWeightKg = 20.0, averageReps = 6.0, setCount = 3, fromHistory = true).asLoggedSet()
        val weightless = RoutineExercise("ex_hang", "Hangboard", 2, 5..8, weight = null, startReps = 6).planned
        val weighted = RoutineExercise("ex_hang", "Hangboard", 2, 5..8, ExerciseWeight(20.0, 2.5), startReps = 6).planned

        assertEquals(listOf(SetDraft("", "6"), SetDraft("", "6")), WorkoutDraft(emptyMap()).fillExerciseWith(weightless, last, swedish).setsByExercise.getValue("ex_hang"))
        assertEquals(listOf(SetDraft("20", "6"), SetDraft("20", "6")), WorkoutDraft(emptyMap()).fillExerciseWith(weighted, last, swedish).setsByExercise.getValue("ex_hang"))
    }

    private fun formatted(value: Double) =
        formatMetricNumber(value, swedish)
}
