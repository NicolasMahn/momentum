package com.example.workouttracker.feature.measurement

import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementTrendWindowTest {
    @Test
    fun aShorterSpanFollowsAChangeSoonerAndLeavesTheRecordsAsTheyAre() {
        val steady = (1..14).map { day -> record(day, 80.0) }
        val dropped = (15..21).map { day -> record(day, 78.0) }
        val logs = steady + dropped

        val week = measurementHistoryWindow(logs, "2026-09-21", trendDays = 7)
        val threeWeeks = measurementHistoryWindow(logs, "2026-09-21", trendDays = 21)

        assertEquals(week.recordedDays.map { it.log }, threeWeeks.recordedDays.map { it.log })
        val weekLatest = week.trendPoints.last().value
        val threeWeeksLatest = threeWeeks.trendPoints.last().value
        assertTrue(weekLatest < threeWeeksLatest)
        assertTrue(threeWeeksLatest in 78.0..80.0)
    }

    @Test
    fun aRefusedValueKeepsItsDotButMovesNoTrend() {
        val logs = listOf(
            record(1, 80.0),
            record(2, 81.0),
            // The day's latest record decides it, so the earlier 82 is not fallen back to.
            record(3, 82.0, timestamp = 1),
            record(3, 0.0, timestamp = 2),
            record(4, 82.0),
        )
        val window = measurementHistoryWindow(logs, "2026-09-04", valuePolicy = MeasurementValuePolicy.Positive)
        val withoutTheDay = measurementHistoryWindow(logs.filterNot { it.dateKey == "2026-09-03" }, "2026-09-04")

        assertEquals(0.0, window.days.first { it.dateKey == "2026-09-03" }.log!!.value, 0.0)
        assertTrue(window.trendPoints.none { it.dateKey == "2026-09-03" })
        assertEquals(withoutTheDay.trendPoints.last().value, window.trendPoints.last().value, 0.0)
    }

    @Test
    fun theTrendAtTheWindowEdgeIsSeededByEarlierRecords() {
        val logs = listOf(record(1, 100.0), record(2, 100.0), record(3, 100.0), record(30, 80.0))
        // Four weeks up to 2026-10-05 begin on 2026-09-08, after the first three records.
        val window = measurementHistoryWindow(logs, "2026-10-05")

        assertNull(window.days.first().log)
        assertNotNull(window.days.first().trendValue)
        assertEquals(100.0, window.trendPoints.first().value, 0.0)
    }

    private fun record(day: Int, value: Double, timestamp: Long = day.toLong()) =
        MeasurementRecord("2026-09-${day.toString().padStart(2, '0')}", timestamp, value)
}
