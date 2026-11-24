package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.activity.routine.ExerciseLog
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppReminderCompletionTest {
    /** Anything logged on the day the reminder asked about answers it: an entry, or a session with a set; a record of another day does not. */
    @Test
    fun activityCancellationRequiresAnEntryOrASessionWithASetOnTheTargetDate() {
        val before = AppState()
        val entry = ActivityEntry("e1", "bouldering", "Bouldering", "2026-09-17", 1L)
        fun session(dateKey: String, sets: List<LoggedSet>) = WorkoutSession("s-$dateKey", "gym", "A", dateKey, 2L, listOf(ExerciseLog("bench_press", "Bench Press", sets)))

        assertTrue(activityWasLoggedForDate(before, before.copy(activityEntries = listOf(entry)), "2026-09-17"))
        assertTrue(activityWasLoggedForDate(before, before.copy(sessions = listOf(session("2026-09-17", listOf(LoggedSet(20.0, 8.0))))), "2026-09-17"))
        assertFalse(activityWasLoggedForDate(before, before.copy(sessions = listOf(session("2026-09-17", emptyList()))), "2026-09-17"))
        assertFalse(activityWasLoggedForDate(before, before.copy(activityEntries = listOf(entry.copy(dateKey = "2026-09-16"))), "2026-09-17"))
        // A day already logged answers nothing again until something on it changes.
        val logged = before.copy(activityEntries = listOf(entry))
        assertFalse(activityWasLoggedForDate(logged, logged, "2026-09-17"))
        assertTrue(activityWasLoggedForDate(logged, logged.copy(activityEntries = listOf(entry.copy(durationMinutes = 30))), "2026-09-17"))
        assertFalse(activityWasLoggedForDate(logged, AppState(), "2026-09-17"))
    }
}
