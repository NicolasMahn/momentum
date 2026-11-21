package com.example.workouttracker.feature.logactivity.gym

import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.pluralText
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutQueriesTest {
    private val gym = SeededGym
    private val plan = gym.routine!!
    private val day = plan.day("A")!!
    private val bench = day.exercises.first { it.id == "bench_press" }
    private val squat = day.exercises.first { it.id == "back_squat" }

    private fun session(at: Long, vararg logs: Pair<String, List<LoggedSet>>, dateKey: String = "2026-09-0$at") = WorkoutSession(
        id = "s$at", activityId = "gym", day = "A", dateKey = dateKey, timestampMillis = at,
        exerciseLogs = logs.map { (id, sets) -> ExerciseLog(id, id, sets) },
    )

    /** One sort serves every exercise, whatever order the history is stored in. */
    @Test fun eachExerciseTakesTheNewestSessionThatLoggedASetOfIt() {
        val sessions = listOf(
            session(1, "bench_press" to listOf(LoggedSet(20.0, 10.0)), "back_squat" to listOf(LoggedSet(50.0, 8.0), LoggedSet(60.0, 10.0))),
            session(3, "bench_press" to listOf(LoggedSet(25.0, 8.0))),
            // The newest session opened squat without a set, so it says nothing about squat.
            session(4, "back_squat" to emptyList()),
            session(2, "bench_press" to listOf(LoggedSet(30.0, 6.0))),
        )
        listOf(sessions, sessions.reversed(), sessions.sortedByDescending { it.timestampMillis }).forEach { order ->
            val summaries = latestWorkoutSummaries(AppState(sessions = order), day.exercises.map { it.planned })
            assertEquals(ExerciseWorkoutSummary(25.0, 8.0, setCount = 1, fromHistory = true), summaries.getValue(bench.id))
            assertEquals(ExerciseWorkoutSummary(55.0, 9.0, setCount = 2, fromHistory = true), summaries.getValue(squat.id))
            val row = day.exercises.first { it.id == "row" }
            assertEquals(
                ExerciseWorkoutSummary(row.weight!!.startKg, row.startReps.toDouble(), setCount = 1, fromHistory = false),
                summaries.getValue(row.id),
            )
            assertEquals(day.exercises.map { it.id }.toSet(), summaries.keys)
        }
    }

    /**
     * "Last" is the session of the latest date, not the one saved last: a session's timestamp moves
     * with every save. Mobility's Desk break recorded Shoulder circles, done without a weight, and
     * Shoulder press, done with one, on May 24 and May 27; May 24 was corrected on June 6, the day a
     * new session opens. "Last", the new session's starting weight and "Increase weight" all stay
     * with May 27.
     */
    @Test fun lastFollowsTheLatestDateNotTheLatestSave() {
        val circles = RoutineExercise("ex_circles", "Shoulder circles", 1, 5..12, weight = null, startReps = 8)
        val press = RoutineExercise("ex_press", "Shoulder press", 1, 5..9, ExerciseWeight(4.0, 1.0), startReps = 6)
        val deskBreak = RoutineDay("day_desk", "Desk break", listOf(circles, press))
        val mobility = ListedActivity("act_mobility", "Mobility", ViewIcon.Bolt, routine = Routine(listOf(deskBreak)))
        val exercises = deskBreak.exercises.map { it.planned }
        fun AppState.logged(dateKey: String, at: Long, reps: Double, kg: Double) = this
            .withReplacedWorkoutSets(mobility, deskBreak, circles.planned, listOf(LoggedSet(null, reps)), dateKey, timestampMillis = at)
            .withReplacedWorkoutSets(mobility, deskBreak, press.planned, listOf(LoggedSet(kg, reps)), dateKey, timestampMillis = at)
        val history = AppState()
            .logged("2026-05-24", at = 1, reps = 8.0, kg = 5.0).withWorkoutDuration(mobility, deskBreak, "2026-05-24", durationMinutes = 3)
            .logged("2026-05-27", at = 2, reps = 9.0, kg = 6.0).withWorkoutDuration(mobility, deskBreak, "2026-05-27", durationMinutes = 9)
        val corrected = history.logged("2026-05-24", at = 3, reps = 7.0, kg = 5.0)
        val may24 = corrected.sessionFor(mobility, deskBreak, "2026-05-24")!!
        assertEquals(3L, may24.timestampMillis)
        assertEquals(3, may24.durationMinutes)

        listOf(history, corrected).forEach { state ->
            val last = latestWorkoutSummaries(state, exercises)
            assertEquals(ExerciseWorkoutSummary(null, 9.0, setCount = 1, fromHistory = true), last.getValue(circles.id))
            assertEquals(ExerciseWorkoutSummary(6.0, 9.0, setCount = 1, fromHistory = true), last.getValue(press.id))
            assertTrue(shouldIncreaseWeight(last.getValue(press.id), press.planned))
            val newSession = createDraft(mobility, deskBreak, exercises, state, "2026-06-06", Locale.GERMAN)
            assertEquals(SetDraft("6", ""), newSession.setsByExercise.getValue(press.id).single())
        }

        // Of two sessions on one date the one saved last is newest, in whichever order they are
        // stored, and a later date that opened the exercise without a set says nothing about it.
        fun on(dateKey: String, day: String, at: Long, vararg logs: ExerciseLog) = WorkoutSession("$dateKey-$day", mobility.id, day, dateKey, at, logs.toList())
        fun circlesDone(vararg reps: Double) = ExerciseLog(circles.id, circles.label, reps.map { LoggedSet(null, it) })
        val stored = listOf(
            on("2026-05-27", "day_desk", at = 20, circlesDone(9.0)),
            on("2026-05-27", "day_evening", at = 21, circlesDone(10.0)),
            on("2026-05-28", "day_desk", at = 1, circlesDone(), ExerciseLog(press.id, press.label, listOf(LoggedSet(6.0, 9.0)))),
            on("2026-05-24", "day_desk", at = 99, circlesDone(7.0)),
        )
        listOf(stored, stored.reversed()).forEach { order ->
            assertEquals(10.0, latestWorkoutSummaries(AppState(sessions = order), exercises).getValue(circles.id)!!.averageReps, 0.0)
        }
    }

    /**
     * A recorded session is edited with every exercise it recorded: the day's own with the larger of
     * its recorded sets and its sets now, then a removed one with its configuration and one the plan
     * never knew with its recorded name and rows; an empty log of a stranger is nothing recorded.
     */
    @Test fun aSessionIsEditedWithEveryExerciseItRecordedAndNeverCutToThePlan() {
        val recorded = session(
            7,
            "bench_press" to List(5) { LoggedSet(20.0, 8.0) },
            "pull_ups" to listOf(LoggedSet(-15.0, 8.0)),
            "ex_gone" to listOf(LoggedSet(10.0, 12.0), LoggedSet(10.0, 11.0)),
            "ex_empty" to emptyList(),
            dateKey = "2026-09-07",
        )
        val pullUps = plan.exercise("pull_ups")!!
        val state = AppState(sessions = listOf(recorded))

        val exercises = state.sessionExercises(gym, day, "2026-09-07")

        assertEquals(day.retainedExercises.map { it.id } + listOf("pull_ups", "ex_gone"), exercises.map { it.id })
        assertEquals(SessionExercise("bench_press", bench.label, rows = 5, configured = bench), exercises.first())
        assertEquals(SessionExercise("back_squat", squat.label, rows = squat.sets, configured = squat), exercises[1])
        assertEquals(SessionExercise("pull_ups", pullUps.label, rows = pullUps.sets, configured = pullUps), exercises.first { it.id == "pull_ups" })
        assertEquals(SessionExercise("ex_gone", "ex_gone", rows = 2, configured = null), exercises.last())
        // A new session starts with the plan's exercises and counts.
        assertEquals(day.retainedExercises.map { it.planned }, state.sessionExercises(gym, day, "2026-09-08"))
    }

    /** "Increase weight" shows only after an earlier session of the exercise, and only where the plan gives it a rep range. */
    @Test fun increaseWeightNeedsAnEarlierSessionAndARepRange() {
        val fresh = latestWorkoutSummaries(AppState(), day.exercises.map { it.planned })
        assertFalse(fresh.getValue(bench.id)!!.fromHistory)
        assertFalse(shouldIncreaseWeight(fresh.getValue(bench.id), bench.planned))

        val atTheTop = AppState(sessions = listOf(session(1, "bench_press" to listOf(LoggedSet(20.0, 10.0), LoggedSet(20.0, 10.0)))))
        assertTrue(shouldIncreaseWeight(latestWorkoutSummaries(atTheTop, listOf(bench.planned)).getValue(bench.id), bench.planned))
        val below = AppState(sessions = listOf(session(1, "bench_press" to listOf(LoggedSet(20.0, 10.0), LoggedSet(20.0, 9.0)))))
        assertFalse(shouldIncreaseWeight(latestWorkoutSummaries(below, listOf(bench.planned)).getValue(bench.id), bench.planned))

        // An exercise the plan never knew has no rep range and, without a session, no summary at all.
        val stranger = SessionExercise("ex_gone", "Leg press", rows = 2, configured = null)
        assertNull(latestWorkoutSummaries(AppState(), listOf(stranger)).getValue(stranger.id))
        val trained = AppState(sessions = listOf(session(1, "ex_gone" to listOf(LoggedSet(80.0, 20.0)))))
        val summary = latestWorkoutSummaries(trained, listOf(stranger)).getValue(stranger.id)
        assertEquals(ExerciseWorkoutSummary(80.0, 20.0, setCount = 1, fromHistory = true), summary)
        assertFalse(shouldIncreaseWeight(summary, stranger))
    }

    /**
     * After a session recorded without weights, a weight turned on at a 20 kg start shows the
     * average as reps alone, since no set recorded one, and offers no "Increase weight" though
     * the reps reached the top; the starting weight is never shown as recorded.
     */
    @Test fun afterAWeightlessSessionAWeightTurnedOnShowsRepsAloneAndNoIncrease() {
        val weighted = RoutineExercise("ex_campus", "Campus board", 3, 5..8, ExerciseWeight(20.0, 2.5), startReps = 6).planned
        val trained = AppState(sessions = listOf(session(1, "ex_campus" to List(3) { LoggedSet(null, 8.0) })))

        val summary = latestWorkoutSummaries(trained, listOf(weighted)).getValue("ex_campus")
        assertEquals(ExerciseWorkoutSummary(averageWeightKg = null, averageReps = 8.0, setCount = 3, fromHistory = true), summary)
        assertEquals(pluralText(R.plurals.activity_gym_reps, 8, UiText.Number(8.0)), formatAverageWorkoutSummary(requireNotNull(summary)))
        assertFalse(shouldIncreaseWeight(summary, weighted))

        // With a weight recorded at the top of the range the increase is offered, as ever.
        val withWeights = AppState(sessions = listOf(session(2, "ex_campus" to List(3) { LoggedSet(20.0, 8.0) })))
        assertTrue(shouldIncreaseWeight(latestWorkoutSummaries(withWeights, listOf(weighted)).getValue("ex_campus"), weighted))
        assertEquals(ExerciseWorkoutSummary(20.0, 6.0, setCount = 1, fromHistory = false), latestWorkoutSummaries(AppState(), listOf(weighted)).getValue("ex_campus"))
    }
}
