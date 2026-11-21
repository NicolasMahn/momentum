package com.example.workouttracker.feature.logactivity.gym

import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.activityRules
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.nextRoutineDay
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.Routine
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import com.example.workouttracker.feature.logactivity.RecordActivityResult
import com.example.workouttracker.feature.logactivity.recordActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading

class WorkoutStateTransitionsTest {
    @Test
    fun deletingWorkoutSessionRemovesOnlyExactSessionAndPreservesUnrelatedData() {
        val target = WorkoutSession(
            id = "target-session",
            activityId = "gym",
            day = "B",
            dateKey = "2026-09-14",
            timestampMillis = 200L,
            exerciseLogs = listOf(
                ExerciseLog("pull_ups", "Pull Ups", listOf(LoggedSet(-15.0, 8.0))),
            ),
        )
        val other = WorkoutSession(
            id = "other-session",
            activityId = "gym",
            day = "A",
            dateKey = DATE_KEY,
            timestampMillis = 300L,
            exerciseLogs = listOf(
                ExerciseLog("bench_press", "Bench Press", listOf(LoggedSet(22.5, 10.0))),
            ),
        )
        val weights = listOf(MeasurementRecord(DATE_KEY, 400L, 88.45)).asMeasurementLogs("weight")
        val initial = stateWithActivity().copy(
            measurementLogs = weights,
            sessions = listOf(other, target),
        )

        val updated = initial.withDeletedWorkoutSession(target.id)

        assertEquals(listOf(other), updated.sessions)
        assertSame(other, updated.sessions.single())
        assertSame(weights, updated.measurementLogs)
        assertActivityDataIsUnchanged(initial, updated)
    }

    @Test
    fun everyPlanCanSaveEditAndRemoveASetWithoutChangingActivityData() {
        gym.routine!!.days.forEachIndexed { index, day ->
            val initial = stateWithActivity()
            val exercise = day.exercises.first()
            val savedSet = LoggedSet(weightKg = 20.0 + index, reps = 8.0)
            val editedSet = LoggedSet(weightKg = 22.5 + index, reps = 9.0)

            val saved = initial.withSavedWorkoutSet(
                activity = gym,
                day = day,
                exercise = exercise.planned,
                setIndex = 0,
                set = savedSet,
                todayKey = DATE_KEY,
                timestampMillis = 100L + index,
            )
            val edited = saved.withSavedWorkoutSet(
                activity = gym,
                day = day,
                exercise = exercise.planned,
                setIndex = 0,
                set = editedSet,
                todayKey = DATE_KEY,
                timestampMillis = 200L + index,
            )

            assertActivityDataIsUnchanged(initial, saved)
            assertActivityDataIsUnchanged(initial, edited)
            assertEquals(1, edited.sessions.size)
            assertEquals("$DATE_KEY-${day.id}", edited.sessions.single().id)
            assertEquals(gym.id, edited.sessions.single().activityId)
            assertEquals(day.id, edited.sessions.single().day)
            assertEquals(200L + index, edited.sessions.single().timestampMillis)
            assertEquals(
                listOf(editedSet),
                edited.sessions.single().exerciseLogs.first { it.exerciseId == exercise.id }.sets,
            )

            val removed = edited.withRemovedWorkoutSet(
                activity = gym,
                day = day,
                exercise = exercise.planned,
                setIndex = 0,
                todayKey = DATE_KEY,
                timestampMillis = 300L + index,
            )

            assertActivityDataIsUnchanged(initial, removed)
            assertTrue(removed.sessions.isEmpty())
        }
    }

    @Test
    fun everyPlanCanEmptyOneExerciseWithoutRemovingOtherDetailedSets() {
        gym.routine!!.days.forEachIndexed { index, day ->
            val firstExercise = day.exercises[0]
            val secondExercise = day.exercises[1]
            val initial = stateWithActivity()
                .withSavedWorkoutSet(
                    activity = gym,
                    day = day,
                    exercise = firstExercise.planned,
                    setIndex = 0,
                    set = LoggedSet(weightKg = 30.0, reps = 8.0),
                    todayKey = DATE_KEY,
                    timestampMillis = 400L + index,
                )
                .withSavedWorkoutSet(
                    activity = gym,
                    day = day,
                    exercise = secondExercise.planned,
                    setIndex = 0,
                    set = LoggedSet(weightKg = 40.0, reps = 10.0),
                    todayKey = DATE_KEY,
                    timestampMillis = 500L + index,
                )

            val firstCleared = initial.withReplacedWorkoutSets(
                activity = gym,
                day = day,
                exercise = firstExercise.planned,
                sets = emptyList(),
                todayKey = DATE_KEY,
                timestampMillis = 600L + index,
            )

            assertActivityDataIsUnchanged(initial, firstCleared)
            assertEquals(1, firstCleared.sessions.size)
            assertTrue(
                firstCleared.sessions.single().exerciseLogs
                    .first { it.exerciseId == firstExercise.id }
                    .sets
                    .isEmpty(),
            )
            assertEquals(
                1,
                firstCleared.sessions.single().exerciseLogs
                    .first { it.exerciseId == secondExercise.id }
                    .sets
                    .size,
            )

            val allCleared = firstCleared.withReplacedWorkoutSets(
                activity = gym,
                day = day,
                exercise = secondExercise.planned,
                sets = emptyList(),
                todayKey = DATE_KEY,
                timestampMillis = 700L + index,
            )

            assertActivityDataIsUnchanged(initial, allCleared)
            assertTrue(allCleared.sessions.isEmpty())
        }
    }

    @Test
    fun arbitraryActivityAndEditedDetailedWorkoutSurviveCodecReloadTogether() {
        val activityState = recordActivity(
            state = AppState(),
            rawName = "Bouldering",
            dateKey = DATE_KEY,
            durationMinutes = 75,
            notes = "Technical session",
            newActivityId = "activity-type-1",
            newEntryId = "activity-entry-1",
            timestampMillis = 1_000L,
        ).successState()
        val day = gym.day("B")!!
        val exercise = day.exercises.first()
        val firstSet = LoggedSet(weightKg = -15.0, reps = 8.0)
        val editedSet = LoggedSet(weightKg = -11.0, reps = 9.0)
        val withWorkout = activityState
            .withSavedWorkoutSet(
                activity = gym,
                day = day,
                exercise = exercise.planned,
                setIndex = 0,
                set = firstSet,
                todayKey = DATE_KEY,
                timestampMillis = 2_000L,
            )
            .withSavedWorkoutSet(
                activity = gym,
                day = day,
                exercise = exercise.planned,
                setIndex = 0,
                set = editedSet,
                todayKey = DATE_KEY,
                timestampMillis = 3_000L,
            )
            .withWorkoutDuration(
                activity = gym,
                day = day,
                todayKey = DATE_KEY,
                durationMinutes = 80,
            )

        val stored = withWorkout.withWeight().withEating().withReading().withActivity().let { it.copy(activity = it.activityRules) }
        val reloaded = AppStateJsonCodec.decodeVersion32(AppStateJsonCodec.encode(stored))

        assertEquals(stored, reloaded)
        assertEquals("Bouldering", reloaded.activityEntries.single().name)
        assertEquals(75, reloaded.activityEntries.single().durationMinutes)
        assertEquals("B", reloaded.sessions.single().day)
        assertEquals(80, reloaded.sessions.single().durationMinutes)
        assertEquals(
            listOf(editedSet),
            reloaded.sessions.single().exerciseLogs.first { it.exerciseId == exercise.id }.sets,
        )
    }

    @Test
    fun replacingExerciseSetsAtomicallyDoesNotDuplicateShiftedRows() {
        val day = gym.day("A")!!
        val exercise = day.exercises.first()
        val otherExercise = day.exercises[1]
        val originalSets = listOf(
            LoggedSet(20.0, 8.0),
            LoggedSet(21.0, 9.0),
            LoggedSet(22.0, 10.0),
        )
        val preservedOtherSet = LoggedSet(60.0, 8.0)
        var state = stateWithActivity()
        originalSets.forEachIndexed { index, set ->
            state = state.withSavedWorkoutSet(
                gym,
                day,
                exercise.planned,
                index,
                set,
                DATE_KEY,
                timestampMillis = 100L + index,
            )
        }
        state = state.withSavedWorkoutSet(
            gym,
            day,
            otherExercise.planned,
            0,
            preservedOtherSet,
            DATE_KEY,
            timestampMillis = 200L,
        )

        val replacement = listOf(
            LoggedSet(22.0, 9.0),
            LoggedSet(23.0, 10.0),
        )
        val updated = state.withReplacedWorkoutSets(
            activity = gym,
            day = day,
            exercise = exercise.planned,
            sets = replacement,
            todayKey = DATE_KEY,
            timestampMillis = 300L,
        )

        assertActivityDataIsUnchanged(state, updated)
        val logs = updated.sessions.single().exerciseLogs
        assertEquals(replacement, logs.single { it.exerciseId == exercise.id }.sets)
        assertEquals(listOf(preservedOtherSet), logs.single { it.exerciseId == otherExercise.id }.sets)
    }

    /**
     * A recorded session is a fact the plan does not limit: an exercise with more sets than the
     * plan asks keeps and rewrites all of them, a removed exercise's and an unknown exercise's
     * sets are rewritten and cleared where the session recorded them, and a log the plan no longer
     * names is kept through every change of another exercise.
     */
    @Test
    fun aRecordedSessionKeepsEveryExerciseAndEverySetThroughItsEdits() {
        val day = gym.day("A")!!
        val bench = day.exercises.first()
        val recorded = WorkoutSession(
            id = "old", activityId = gym.id, day = day.id, dateKey = DATE_KEY, timestampMillis = 10L,
            exerciseLogs = listOf(
                ExerciseLog(bench.id, bench.label, List(5) { LoggedSet(20.0, 8.0) }),
                ExerciseLog("pull_ups", "Pull Ups", listOf(LoggedSet(-15.0, 8.0))),
                ExerciseLog("ex_gone", "Leg press", listOf(LoggedSet(80.0, 12.0), LoggedSet(80.0, 10.0))),
            ),
        )
        val state = stateWithActivity().copy(sessions = listOf(recorded))
        val exercises = state.sessionExercises(gym, day, DATE_KEY)
        val five = exercises.first { it.id == bench.id }
        val stranger = exercises.first { it.id == "ex_gone" }
        assertEquals(5, five.rows)

        val fiveRewritten = state.withReplacedWorkoutSets(gym, day, five, List(5) { LoggedSet(22.5, 7.0) }, DATE_KEY, timestampMillis = 20L)
        assertEquals(List(5) { LoggedSet(22.5, 7.0) }, fiveRewritten.sessions.single().exerciseLogs.first { it.exerciseId == bench.id }.sets)
        assertEquals("old", fiveRewritten.sessions.single().id)
        assertSame(state, state.withReplacedWorkoutSets(gym, day, five, List(6) { LoggedSet(22.5, 7.0) }, DATE_KEY))

        val strangerRewritten = fiveRewritten.withReplacedWorkoutSets(gym, day, stranger, listOf(LoggedSet(85.0, 10.0), LoggedSet(85.0, 9.0)), DATE_KEY, timestampMillis = 30L)
        val logs = strangerRewritten.sessions.single().exerciseLogs
        assertEquals(listOf(LoggedSet(85.0, 10.0), LoggedSet(85.0, 9.0)), logs.first { it.exerciseId == "ex_gone" }.sets)
        assertEquals("Leg press", logs.first { it.exerciseId == "ex_gone" }.exerciseName)
        assertEquals(listOf(LoggedSet(-15.0, 8.0)), logs.first { it.exerciseId == "pull_ups" }.sets)
        assertEquals(day.retainedExercises.map { it.id } + listOf("pull_ups", "ex_gone"), logs.map { it.exerciseId })

        val strangerCleared = strangerRewritten.withReplacedWorkoutSets(gym, day, stranger, emptyList(), DATE_KEY, timestampMillis = 40L)
        assertEquals(emptyList<LoggedSet>(), strangerCleared.sessions.single().exerciseLogs.first { it.exerciseId == "ex_gone" }.sets)
        assertActivityDataIsUnchanged(state, strangerCleared)
    }

    /**
     * A session whose only sets belong to an exercise the day no longer names, or never knew, is
     * dropped when they are cleared, while the editor keeps the exercise's rows. What is saved for
     * it again is found: the new session holds its log under its recorded name, beside the day's
     * own, and removing that set drops the session once more.
     */
    @Test
    fun emptyingAStrangerAndSavingItAgainRecreatesItsLog() {
        val day = gym.day("A")!!
        val recorded = WorkoutSession(
            id = "old", activityId = gym.id, day = day.id, dateKey = DATE_KEY, timestampMillis = 10L,
            exerciseLogs = listOf(ExerciseLog("ex_gone", "Leg press", listOf(LoggedSet(80.0, 12.0), LoggedSet(80.0, 10.0)))),
        )
        val state = stateWithActivity().copy(sessions = listOf(recorded))
        val stranger = state.sessionExercises(gym, day, DATE_KEY).first { it.id == "ex_gone" }
        assertEquals(2, stranger.rows)

        val cleared = state.withReplacedWorkoutSets(gym, day, stranger, emptyList(), DATE_KEY, timestampMillis = 20L)
        assertEquals(emptyList<WorkoutSession>(), cleared.sessions)

        val savedAgain = cleared.withSavedWorkoutSet(gym, day, stranger, setIndex = 0, set = LoggedSet(85.0, 10.0), todayKey = DATE_KEY, timestampMillis = 30L)
        val session = savedAgain.sessions.single()
        assertEquals("$DATE_KEY-${day.id}", session.id)
        assertEquals(gym.id, session.activityId)
        assertEquals(day.retainedExercises.map { it.id } + "ex_gone", session.exerciseLogs.map { it.exerciseId })
        assertEquals(ExerciseLog("ex_gone", "Leg press", listOf(LoggedSet(85.0, 10.0))), session.exerciseLogs.last())
        assertEquals(1, session.totalLoggedSets)
        assertEquals(session, cleared.withReplacedWorkoutSets(gym, day, stranger, listOf(LoggedSet(85.0, 10.0)), DATE_KEY, timestampMillis = 30L).sessions.single())
        assertActivityDataIsUnchanged(state, savedAgain)

        assertEquals(emptyList<WorkoutSession>(), savedAgain.withRemovedWorkoutSet(gym, day, stranger, setIndex = 0, todayKey = DATE_KEY).sessions)
        // Nothing to empty or remove in a session that is not there.
        assertSame(cleared, cleared.withReplacedWorkoutSets(gym, day, stranger, emptyList(), DATE_KEY))
        assertSame(cleared, cleared.withRemovedWorkoutSet(gym, day, stranger, setIndex = 0, todayKey = DATE_KEY))
    }

    /** A session of another activity's routine is its own, one per activity, day and date, beside the gym's of the same date; a weightless exercise records sets without a weight. */
    @Test
    fun aSessionOfAnotherActivitysRoutineStandsBesideTheGymsOfTheSameDate() {
        val yoga = ListedActivity("act_yoga", "Yoga", ViewIcon.Bolt, routine = Routine(listOf(RoutineDay("day_flow", "Flow", listOf(RoutineExercise("ex_sun", "Sun salutation", 2, 5..10, null, 5))))))
        val state = stateWithActivity().let { it.copy(activity = it.activity!!.copy(activities = it.activity.activities + yoga)) }
        val flow = yoga.day("day_flow")!!
        val sun = flow.exercises.single().planned
        val gymDay = gym.day("A")!!

        val both = state
            .withSavedWorkoutSet(yoga, flow, sun, setIndex = 0, set = LoggedSet(null, 8.0), todayKey = DATE_KEY, timestampMillis = 10L)
            .withSavedWorkoutSet(gym, gymDay, gymDay.exercises.first().planned, setIndex = 0, set = LoggedSet(20.0, 8.0), todayKey = DATE_KEY, timestampMillis = 20L)

        assertEquals(listOf("$DATE_KEY-A" to "gym", "$DATE_KEY-day_flow" to "act_yoga"), both.sessions.map { it.id to it.activityId })
        assertEquals(listOf(LoggedSet(null, 8.0)), both.sessionFor(yoga, flow, DATE_KEY)!!.exerciseLogs.single().sets)
        assertActivityDataIsUnchanged(state, both)
    }

    /**
     * Correcting an earlier day keeps its session on that day, with its ID and minutes, and saves
     * it later than a newer day's; the next routine day still follows the newer day. A 7-day
     * routine had Wednesday's B and Saturday's G, and Wednesday's last reps were corrected on Sunday.
     */
    @Test
    fun correctingAnEarlierDayLeavesTheNextRoutineDayAfterTheNewestDate() {
        val mobility = ListedActivity("act_mobility", "Mobility", ViewIcon.Bolt, routine = Routine(('A'..'G').map(Char::toString).map {
            RoutineDay("day_$it", it, listOf(RoutineExercise("ex_$it", "Stretch $it", 2, 5..10, null, 5)))
        }))
        val b = mobility.day("day_B")!!
        val g = mobility.day("day_G")!!
        val wednesday = "2026-10-07"
        val saturday = "2026-10-10"
        fun AppState.logged(day: RoutineDay, dateKey: String, at: Long) =
            withReplacedWorkoutSets(mobility, day, day.exercises.single().planned, listOf(LoggedSet(null, 8.0), LoggedSet(null, 8.0)), dateKey, timestampMillis = at)
        val state = stateWithActivity().let { it.copy(activity = it.activity!!.copy(activities = it.activity.activities + mobility)) }
        val logged = state
            .logged(b, wednesday, at = 10L).withWorkoutDuration(mobility, b, wednesday, durationMinutes = 20)
            .logged(g, saturday, at = 20L)
        assertEquals("A", mobility.nextRoutineDay(logged.sessions)!!.label)

        val stretch = logged.sessionExercises(mobility, b, wednesday).single()
        val corrected = logged.withReplacedWorkoutSets(mobility, b, stretch, listOf(LoggedSet(null, 8.0), LoggedSet(null, 9.0)), wednesday, timestampMillis = 30L)

        val before = logged.sessionFor(mobility, b, wednesday)!!
        val after = corrected.sessionFor(mobility, b, wednesday)!!
        assertEquals(listOf(before.id, wednesday, 20), listOf(after.id, after.dateKey, after.durationMinutes))
        assertTrue(after.timestampMillis > corrected.sessionFor(mobility, g, saturday)!!.timestampMillis)
        assertEquals("A", mobility.nextRoutineDay(corrected.sessions)!!.label)
    }

    private val gym = testActivityConfiguration().activity("gym")!!

    private fun stateWithActivity(): AppState {
        val bowling = ListedActivity("activity-type-1", "Bowling", ViewIcon.Bolt)
        val entry = ActivityEntry(
            id = "activity-entry-1",
            activityId = bowling.id,
            name = bowling.label,
            dateKey = DATE_KEY,
            timestampMillis = 20L,
            durationMinutes = 60,
            notes = "League night",
        )
        return AppState(
            activity = testActivityConfiguration().let { it.copy(activities = it.activities + bowling) },
            activityEntries = listOf(entry),
        )
    }

    private fun assertActivityDataIsUnchanged(expected: AppState, actual: AppState) {
        assertSame(expected.activity, actual.activity)
        assertSame(expected.activityEntries, actual.activityEntries)
        assertEquals(expected.activity, actual.activity)
        assertEquals(expected.activityEntries, actual.activityEntries)
    }

    private fun RecordActivityResult.successState(): AppState {
        assertTrue(this is RecordActivityResult.Success)
        return (this as RecordActivityResult.Success).state
    }

    private companion object {
        const val DATE_KEY = "2026-09-15"
    }
}
