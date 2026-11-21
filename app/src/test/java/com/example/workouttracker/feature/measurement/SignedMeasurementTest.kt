package com.example.workouttracker.feature.measurement

import com.example.workouttracker.core.format.parseTypedNumber
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DayChangeResult
import com.example.workouttracker.domain.changingDay
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SignedMeasurementTest {
    @Test fun genericAdjustmentCrossesZeroAndRejectsOverflowWithoutChangingTheSavedRecord() {
        val record = MeasurementRecord("2026-10-09", 1L, 0.1)
        val draft = measurementDraftForDate(listOf(record), record.dateKey, Locale.GERMANY)
        val zero = requireNotNull(draft.adjusted(-0.1, Locale.GERMANY, ::parseTypedNumber))
        assertEquals("0", zero.valueText)
        val negative = requireNotNull(zero.adjusted(-0.1, Locale.GERMANY, ::parseTypedNumber))
        assertEquals("-0,1", negative.valueText)
        assertEquals(record, negative.originalLog)
        assertEquals(draft.dateKey, negative.dateKey)
        assertTrue(negative.isDirty)
        assertNull(draft.adjusted(-0.1, Locale.GERMANY, ::parseTypedNumber, MeasurementValuePolicy.Positive))
        assertNull(draft.copy(valueText = Double.MAX_VALUE.toString()).adjusted(Double.MAX_VALUE, Locale.ENGLISH, ::parseTypedNumber))
    }

    @Test fun saveCommandUsesTheCurrentPolicyAndDoesNotRestrictHistoricalStorage() {
        val generic = testMadeMeasurement()
        val weight = testWeightMeasurement()
        val state = AppState(measurements = listOf(generic, weight))
        listOf(-3.0, 0.0).forEach { value ->
            val change = MeasurementDayChange.Save(generic.id, "2026-10-09", null, value)
            val stored = state.changingDay(change, 123L) { true } as DayChangeResult.Stored
            assertEquals(value, stored.after.measurementLogs.single().value, 0.0)
            assertEquals(DayChangeResult.Failed, state.changingDay(change.copy(measurementId = weight.id), 123L) { error("must not save") })
        }
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).forEach { value ->
            assertEquals(DayChangeResult.Failed, state.changingDay(MeasurementDayChange.Save(generic.id, "2026-10-09", null, value), 123L) { error("must not save") })
        }
    }

    @Test fun signedAndZeroRecordsRemainOnTheChartAndContributeToItsTrend() {
        val records = listOf(
            MeasurementRecord("2026-10-07", 1L, -3.0),
            MeasurementRecord("2026-10-08", 2L, 0.0),
            MeasurementRecord("2026-10-09", 3L, 6.0),
        )
        val window = measurementHistoryWindow(records, "2026-10-09")
        assertEquals(records, window.recordedDays.map { it.log })
        assertEquals(listOf("2026-10-07", "2026-10-08", "2026-10-09"), window.trendPoints.map { it.dateKey })
        assertTrue(window.smoothed)
        // Under a positive policy the -3 and the 0 keep their dots and move no line.
        val positive = measurementHistoryWindow(records, "2026-10-09", valuePolicy = MeasurementValuePolicy.Positive)
        assertEquals(records, positive.recordedDays.map { it.log })
        assertEquals(listOf(6.0), positive.trendPoints.map { it.value })
        val geometry = measurementChartGeometry(window)
        assertTrue(geometry.axis.minimum < -3.0)
        assertTrue(geometry.axis.maximum > 6.0)
        assertTrue(geometry.linePoints.all { it.yFraction in 0.0..1.0 })
    }

    @Test fun axesAndPointsStayFiniteForNegativeAndMixedFiniteExtremes() {
        listOf(
            listOf(-3.0, 0.0, 6.0),
            listOf(-Double.MAX_VALUE),
            listOf(-Double.MAX_VALUE, 0.0, Double.MAX_VALUE),
        ).forEach { values ->
            val axis = measurementChartAxis(values)
            assertTrue(axis.minimum.isFinite() && axis.maximum.isFinite())
            assertTrue(axis.minimum < axis.maximum)
            assertTrue(values.all { it >= axis.minimum && it <= axis.maximum })
            val points = measurementChartLinePoints(values.mapIndexed { index, value -> MeasurementTrendPoint("", index, value) }, axis, values.lastIndex)
            assertTrue(points.all { it.yFraction.isFinite() && it.yFraction in 0.0..1.0 })
            assertTrue(points.zipWithNext().all { (lower, higher) -> lower.yFraction >= higher.yFraction })
        }
    }
}
