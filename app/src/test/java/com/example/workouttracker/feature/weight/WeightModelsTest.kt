package com.example.workouttracker.feature.weight

import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.feature.measurement.MinimumTrendMeasurements
import com.example.workouttracker.feature.measurement.replaceMeasurementDay
import com.example.workouttracker.feature.measurement.MeasurementHistoryDay
import com.example.workouttracker.feature.measurement.MeasurementHistoryDayCount
import com.example.workouttracker.feature.measurement.MeasurementHistoryRange
import com.example.workouttracker.feature.measurement.MeasurementTrendPoint
import com.example.workouttracker.feature.measurement.measurementHistoryWindow
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.measurement.withWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightModelsTest {
    @Test
    fun replaceWeightForDate_replacesOnlyTheSelectedDate() {
        val older = MeasurementRecord("2026-09-14", 1L, 84.0)
        val selected = MeasurementRecord("2026-09-15", 2L, 83.8)
        val newer = MeasurementRecord("2026-09-16", 3L, 83.7)

        val updated = replaceMeasurementDay(
            state = AppState(measurementLogs = listOf(newer, selected, older).asMeasurementLogs("weight")),
            measurementId = "weight",
            dateKey = "2026-09-15",
            timestampMillis = 4L,
            value = 83.6,
        ).recordsOf("weight")

        assertEquals(listOf("2026-09-16", "2026-09-15", "2026-09-14"), updated.map { it.dateKey })
        assertEquals(listOf(newer, MeasurementRecord("2026-09-15", 4L, 83.6), older), updated)
    }

    /**
     * The screen's save touches Weight's day alone: its records of that day become the one saved,
     * and another measurement's records of the same day, two alike among them, stay exactly as
     * they were, as does everything else the state holds.
     */
    @Test
    fun replaceWeightForDate_leavesAnotherMeasurementsRecordsOfTheDayAsTheyWere() {
        val newer = MeasurementLog("weight", "2026-09-16", 3L, 83.7)
        val other = MeasurementLog("msr_k2p9", "2026-09-15", 9L, 61.0)
        val otherEarlier = MeasurementLog("msr_k2p9", "2026-09-14", 1L, 60.0)
        val state = AppState(
            measurementLogs = listOf(
                newer,
                other,
                MeasurementLog("weight", "2026-09-15", 2L, 83.8),
                other,
                MeasurementLog("weight", "2026-09-15", 2L, 83.9),
                otherEarlier,
            ),
            timeLogs = listOf(TimeLog(ReadingTimeId, "2026-09-15", 5L)),
        ).withWeight()

        val saved = replaceMeasurementDay(state, "weight", "2026-09-15", 4L, 83.6)

        assertEquals(listOf(newer, other, other, MeasurementLog("weight", "2026-09-15", 4L, 83.6), otherEarlier), saved.measurementLogs)
        assertEquals(listOf(MeasurementRecord("2026-09-16", 3L, 83.7), MeasurementRecord("2026-09-15", 4L, 83.6)), saved.recordsOf("weight"))
        assertEquals(state.copy(measurementLogs = saved.measurementLogs), saved)

        // The first weight of a day that so far only the other measurement has a record on.
        val first = replaceMeasurementDay(state, "weight", "2026-09-14", 5L, 84.0)
        assertEquals(state.measurementLogs + MeasurementLog("weight", "2026-09-14", 5L, 84.0), first.measurementLogs)
    }

    @Test
    fun replaceWeightForDate_refusesADateThatIsNoDate() {
        val state = AppState(measurementLogs = listOf(MeasurementLog("weight", "2026-09-15", 2L, 83.8)))

        assertThrows(IllegalArgumentException::class.java) { replaceMeasurementDay(state, "weight", "2026-02-30", 4L, 83.6) }
    }

    @Test
    fun historyWindow_containsFourCompleteWeeksEndingToday() {
        val window = measurementHistoryWindow(
            logs = emptyList(),
            todayKey = "2026-03-02",
        )

        assertEquals(MeasurementHistoryDayCount, window.days.size)
        assertEquals("2026-02-03", window.days.first().dateKey)
        assertEquals("2026-03-02", window.days.last().dateKey)
        assertEquals((0 until MeasurementHistoryDayCount).toList(), window.days.map { it.dayIndex })
        window.days.forEach { assertNull(it.log) }
        assertEquals(emptyList<MeasurementTrendPoint>(), window.trendPoints)
    }

    @Test
    fun historyWindow_keepsSparseMeasurementsAtTheirTrueDayPositions() {
        val beforeWindow = MeasurementRecord("2026-08-19", 10L, 84.0)
        val firstDay = MeasurementRecord("2026-08-20", 20L, 83.8)
        val middleDay = MeasurementRecord("2026-09-01", 30L, 83.1)
        val today = MeasurementRecord("2026-09-16", 40L, 82.7)
        val afterWindow = MeasurementRecord("2026-09-17", 50L, 82.5)

        val window = measurementHistoryWindow(
            logs = listOf(afterWindow, middleDay, beforeWindow, today, firstDay),
            todayKey = "2026-09-16",
        )

        assertEquals(listOf(0, 12, 27), window.recordedDays.map { it.dayIndex })
        assertEquals(listOf(firstDay, middleDay, today), window.recordedDays.map { it.log })
        assertNull(window.days[1].log)
        assertNull(window.days[26].log)
        assertEquals(listOf(0, 12, 27), window.trendPoints.map { it.dayIndex })
        // Four readings are enough to average, so the line carries the trend seeded before the
        // window and stepped once per skipped day, not the raw readings.
        assertTrue(window.smoothed)
        listOf(83.9818, 83.381, 82.863).zip(window.trendPoints) { expected, point ->
            assertEquals(expected, point.value, 0.0001)
        }
    }

    @Test
    fun historyWindow_showsOnlyMeasurementsUntilThereAreEnoughToAverage() {
        val logs = (0 until MinimumTrendMeasurements).map {
            MeasurementRecord("2026-09-1${4 + it}", it.toLong(), 84.0 + it)
        }

        val tooFew = measurementHistoryWindow(logs.dropLast(1), todayKey = "2026-09-16")
        val enough = measurementHistoryWindow(logs, todayKey = "2026-09-16")

        assertEquals(false, tooFew.smoothed)
        assertEquals(listOf(84.0, 85.0), tooFew.trendPoints.map { it.value }.take(2))
        assertTrue(tooFew.days.all { it.trendValue == null })
        assertTrue(enough.smoothed)
        assertTrue(enough.recordedDays.all { it.trendValue != null })
    }

    @Test
    fun historyWindow_holdsTheTrendOnDaysThatWereNotWeighed() {
        val window = measurementHistoryWindow(
            logs = listOf(
                MeasurementRecord("2026-09-10", 1L, 84.0),
                MeasurementRecord("2026-09-11", 2L, 84.0),
                MeasurementRecord("2026-09-12", 3L, 84.0),
            ),
            todayKey = "2026-09-16",
        )

        val weighed = window.days.single { it.dateKey == "2026-09-12" }
        assertEquals(84.0, weighed.trendValue!!, 0.0001)
        assertEquals(84.0, window.days.last().trendValue!!, 0.0001)
        // Nothing is known before the first reading, so no day before it claims a trend.
        assertNull(window.days.first().trendValue)
    }

    @Test
    fun historyWindow_rangesReachBackAsFarAsTheyPromise() {
        val logs = listOf(MeasurementRecord("2024-01-01", 1L, 90.0), MeasurementRecord("2026-09-16", 2L, 84.0))

        val fourWeeks = measurementHistoryWindow(logs, "2026-09-16", MeasurementHistoryRange.FourWeeks)
        val threeMonths = measurementHistoryWindow(logs, "2026-09-16", MeasurementHistoryRange.ThreeMonths)
        val all = measurementHistoryWindow(logs, "2026-09-16", MeasurementHistoryRange.All)

        assertEquals(MeasurementHistoryDayCount, fourWeeks.days.size)
        assertEquals(91, threeMonths.days.size)
        assertEquals("2024-01-01", all.days.first().dateKey)
        assertEquals("2026-09-16", all.days.last().dateKey)
    }

    @Test
    fun historyWindow_allRangeStillHasAWindowWithoutAnyMeasurement() {
        val window = measurementHistoryWindow(emptyList(), "2026-09-16", MeasurementHistoryRange.All)

        assertEquals(MeasurementHistoryDayCount, window.days.size)
    }

    @Test
    fun historyWindow_usesTheLatestMeasurementWhenLegacyDataHasDuplicates() {
        val earlier = MeasurementRecord("2026-09-16", 100L, 82.9)
        val latest = MeasurementRecord("2026-09-16", 200L, 82.7)

        val window = measurementHistoryWindow(
            logs = listOf(latest, earlier),
            todayKey = "2026-09-16",
        )

        assertEquals(1, window.recordedDays.size)
        assertSame(latest, window.recordedDays.single().log)
    }

    @Test
    fun historyWindow_carriesTheLastKnownWeightAcrossAWindowWithoutMeasurements() {
        val window = measurementHistoryWindow(
            logs = listOf(MeasurementRecord("2026-07-23", 100L, 88.75)),
            todayKey = "2026-09-16",
        )

        assertEquals(emptyList<MeasurementHistoryDay>(), window.recordedDays)
        assertEquals(listOf(0, 27), window.trendPoints.map { it.dayIndex })
        assertEquals(listOf(88.75, 88.75), window.trendPoints.map { it.value })
    }

    @Test
    fun historyWindow_carriesTheLatestMeasurementForwardToToday() {
        val window = measurementHistoryWindow(
            logs = listOf(
                MeasurementRecord("2026-08-01", 100L, 84.0),
                MeasurementRecord("2026-09-02", 200L, 83.5),
            ),
            todayKey = "2026-09-16",
        )

        assertEquals(listOf(0, 13, 27), window.trendPoints.map { it.dayIndex })
        assertEquals(listOf(84.0, 83.5, 83.5), window.trendPoints.map { it.value })
    }

    @Test(expected = IllegalArgumentException::class)
    fun historyWindow_rejectsImpossibleTodayDate() {
        measurementHistoryWindow(
            logs = emptyList(),
            todayKey = "2026-02-30",
        )
    }
}
