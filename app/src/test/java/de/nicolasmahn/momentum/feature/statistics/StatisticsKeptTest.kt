package de.nicolasmahn.momentum.feature.statistics

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.care.everyCareAction
import de.nicolasmahn.momentum.domain.care.tick
import de.nicolasmahn.momentum.domain.care.testCareChecklist
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.compatibility.CareChecklistId
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.time.testMadeTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Statistics count what a view's rule kept, not the days that hold a record. Both cases are
 * weeks up to Thursday 2026-11-12.
 */
class StatisticsKeptTest {
    private val today = "2026-11-12"

    @Test fun aForgottenEveningBrushingIsAMissedDay() {
        val days = generateSequence(LocalDate.parse("2026-11-02")) { it.plusDays(1) }.takeWhile { it.toString() < today }
        val state = AppState(
            checklists = listOf(testCareChecklist()),
            checklistDays = days.map { day ->
                val actions = everyCareAction(day.toString())
                    // On 2026-11-04 the evening was forgotten: the morning's ticks are the day's only record.
                    .filter { day.toString() != "2026-11-04" || it.session == LegacyCareSession.MORNING }
                ChecklistDay(CareChecklistId, day.toString(), 0L, actions.mapTo(mutableSetOf()) { it.tick })
            }.toList(),
        )

        val care = statisticsSummary(state, today, StatisticsRange.FourWeeks).consistency.single()

        assertEquals(WeekConsistency(kept = 6, counted = 7), care.weeks[care.weeks.size - 2])
        // The row's number waits for this week's Sunday; its three kept days only fill its bar.
        assertEquals(6 to 7, care.kept to care.counted)
        assertEquals(3 to 3, care.weeks.last().let { it.kept to it.counted })
    }

    @Test fun aTimerIsJudgedByTheWeekAndItsBarIsTheShareOfTheGoal() {
        val reading = testMadeTime(id = "tim_reading", label = "Reading", goal = 60)
        val minutes = mapOf("2026-10-24" to 20, "2026-10-27" to 10, "2026-11-01" to 55, "2026-11-05" to 60, "2026-11-10" to 25)
        val state = AppState(
            time = listOf(reading),
            timeLogs = minutes.map { (day, value) -> TimeLog(reading.id, day, 0L, minutes = value) },
        )

        val row = statisticsSummary(state, today, StatisticsRange.FourWeeks).consistency.single()

        assertEquals(ConsistencyUnit.Weeks, row.unit)
        assertEquals(
            listOf(
                WeekConsistency(kept = 0, counted = 1, fill = 20f / 60, minutes = WeekMinutes(20, 60)),
                WeekConsistency(kept = 1, counted = 1, fill = 1f, minutes = WeekMinutes(65, 60)),
                WeekConsistency(kept = 1, counted = 1, fill = 1f, minutes = WeekMinutes(60, 60)),
                // The running week is not judged yet; its bar shows how far it has come.
                WeekConsistency(kept = 0, counted = 0, fill = 25f / 60, minutes = WeekMinutes(25, 60), running = true),
            ),
            row.weeks,
        )
        // The average of the judged bars: 20 of 60 minutes, then two whole goals.
        assertEquals((20.0 / 60 + 2) / 3, row.rate!!, 1e-6)
    }
}
