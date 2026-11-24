package de.nicolasmahn.momentum.domain.activity

import de.nicolasmahn.momentum.domain.activity.routine.SeededGymDayIds
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ActivityDaysForWeekTest {
    @Test
    fun multipleActivitiesOnOneDateCountAsOneActualDay() {
        val entries = listOf(
            ActivityEntry("1", "run", "Run", "2026-09-14", 1L),
            ActivityEntry("2", "walk", "Walk", "2026-09-14", 2L),
            ActivityEntry("3", "run", "Run", "2026-09-16", 3L),
            ActivityEntry("4", "run", "Run", "2026-09-21", 4L),
        )
        val gym = listOf(WorkoutSession("5", "gym", "A", "2026-09-16", 5L, emptyList()))
        assertEquals(2, activityDaysForWeek(entries, gym, LocalDate.parse("2026-09-14")))
    }
}
