package com.example.workouttracker.feature.weight

import com.example.workouttracker.domain.measurement.MeasurementValuePolicy

import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.feature.measurement.deleteMeasurementDay
import com.example.workouttracker.feature.measurement.measurementDraftForDate
import com.example.workouttracker.feature.measurement.words
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.core.format.parseMetricNumber
import com.example.workouttracker.core.format.parseTypedNumber
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.measurement.MeasurementRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WeightDraftTest {
    /** How Weight's field reads what is typed while its unit is kilograms: the words it speaks say. */
    private val weightsField: (String) -> Double? = testWeightMeasurement().words()::parse

    @Test
    fun loadingHistoricalDayUsesThatDaysLatestRecordAndDoesNotChangeLogs() {
        val older = MeasurementRecord("2026-09-15", 1, 82.0)
        val selected = MeasurementRecord("2026-09-15", 2, 83.5)
        val future = MeasurementRecord("2026-09-16", 3, 90.0)
        val logs = listOf(older, selected, future)
        val draft = measurementDraftForDate(logs, "2026-09-15", German)
        assertEquals("83,5", draft.valueText)
        assertEquals(selected, draft.originalLog)
        assertFalse(draft.isDirty)
        assertEquals(listOf(older, selected, future), logs)
    }

    @Test
    fun missingDayPrefillsOnlyFromEarlierRecordAndEmptyHistoryHasNoInventedWeight() {
        val logs = listOf(MeasurementRecord("2026-09-14", 1, 80.0), MeasurementRecord("2026-09-16", 2, 90.0))
        assertEquals("80", measurementDraftForDate(logs, "2026-09-15", German).valueText)
        assertEquals("", measurementDraftForDate(logs, "2026-09-13", German).valueText)
        assertEquals("", measurementDraftForDate(emptyList(), "2026-09-15", German).valueText)
    }

    @Test
    fun adjustmentsAffectOnlyDraftAndKeepOriginalRecordForExplicitSave() {
        val record = MeasurementRecord("2026-09-15", 1, 83.5)
        val draft = measurementDraftForDate(listOf(record), record.dateKey, German)
        val adjusted = requireNotNull(draft.adjusted(0.1, German, weightsField, valuePolicy = MeasurementValuePolicy.Positive))
        assertEquals("83,6", adjusted.valueText)
        assertEquals(record, adjusted.originalLog)
        assertEquals(draft.dateKey, adjusted.dateKey)
        assertTrue(adjusted.isDirty)
        assertEquals("83,5", draft.valueText)
    }

    @Test
    fun adjustmentsDoNotReplaceInvalidOrBlankDraftWithFallbackWeight() {
        val draft = measurementDraftForDate(emptyList(), "2026-09-15", German)
        assertNull(draft.adjusted(0.1, German, weightsField, valuePolicy = MeasurementValuePolicy.Positive))
        assertNull(draft.copy(valueText = "invalid").adjusted(0.1, German, weightsField, valuePolicy = MeasurementValuePolicy.Positive))
        assertNull(draft.copy(valueText = "0.05").adjusted(-0.1, German, weightsField, valuePolicy = MeasurementValuePolicy.Positive))
        assertNull(draft.copy(valueText = "NaN").adjusted(0.1, German, weightsField, valuePolicy = MeasurementValuePolicy.Positive))
    }

    /**
     * One step is whatever the measurement's buttons add, read as its field is read and written as
     * every number is: steps of 1 and of 0.01 beside Weight's 0.1, and a configured unit suffix
     * remains editable while a step is applied.
     */
    @Test
    fun aStepOfAnySizeIsTakenFromWhatTheMeasurementsFieldReads() {
        val draft = measurementDraftForDate(listOf(MeasurementRecord("2026-09-15", 1, 83.5)), "2026-09-15", Locale.ENGLISH)
        assertEquals("84.5", requireNotNull(draft.adjusted(1.0, Locale.ENGLISH, ::parseTypedNumber, valuePolicy = MeasurementValuePolicy.Positive)).valueText)
        assertEquals("82.5", requireNotNull(draft.adjusted(-1.0, Locale.ENGLISH, ::parseTypedNumber, valuePolicy = MeasurementValuePolicy.Positive)).valueText)
        assertEquals("83.51", requireNotNull(draft.adjusted(0.01, Locale.ENGLISH, ::parseTypedNumber, valuePolicy = MeasurementValuePolicy.Positive)).valueText)
        assertEquals("83,49", requireNotNull(draft.adjusted(-0.01, German, ::parseTypedNumber, valuePolicy = MeasurementValuePolicy.Positive)).valueText)
        assertEquals("83.6", requireNotNull(draft.adjusted(0.1, Locale.ENGLISH, ::parseTypedNumber, valuePolicy = MeasurementValuePolicy.Positive)).valueText)
        // A number typed with more digits than the step keeps them through a step, as far as a number is shown.
        assertEquals("88.46", requireNotNull(draft.copy(valueText = "87.456").adjusted(1.0, Locale.ENGLISH, ::parseTypedNumber, valuePolicy = MeasurementValuePolicy.Positive)).valueText)
        // Nothing at or below zero is stepped to, whatever the step.
        assertNull(draft.copy(valueText = "1").adjusted(-1.0, Locale.ENGLISH, ::parseTypedNumber, valuePolicy = MeasurementValuePolicy.Positive))
        assertNull(draft.copy(valueText = "0.01").adjusted(-0.01, Locale.ENGLISH, ::parseTypedNumber, valuePolicy = MeasurementValuePolicy.Positive))

        val withUnit = draft.copy(valueText = "83.5 KG")
        assertEquals("83.6", requireNotNull(withUnit.adjusted(0.1, Locale.ENGLISH, weightsField, valuePolicy = MeasurementValuePolicy.Positive)).valueText)
        assertEquals("83.6", requireNotNull(withUnit.adjusted(0.1, Locale.ENGLISH, ::parseMetricNumber, valuePolicy = MeasurementValuePolicy.Positive)).valueText)
        assertNull(withUnit.adjusted(0.1, Locale.ENGLISH, ::parseTypedNumber, valuePolicy = MeasurementValuePolicy.Positive))
    }

    @Test
    fun theFieldFollowsTheLanguageAndADraftSurvivesASwitch() {
        val record = MeasurementRecord("2026-09-15", 1, 83.5)
        assertEquals("83.5", measurementDraftForDate(listOf(record), record.dateKey, Locale.ENGLISH).valueText)

        // Typed in German, adjusted after switching to English: the value carries over, not the text.
        val german = measurementDraftForDate(listOf(record), record.dateKey, German)
        assertEquals("83.6", requireNotNull(german.adjusted(0.1, Locale.ENGLISH, weightsField, valuePolicy = MeasurementValuePolicy.Positive)).valueText)
    }

    @Test
    fun deletingSelectedDatePreservesEveryOtherRecordAndInputCollection() {
        val selected = MeasurementRecord("2026-09-15", 1, 83.5)
        val other = MeasurementRecord("2026-09-16", 2, 84.0)
        val logs = listOf(selected, other)
        val state = AppState(measurementLogs = logs.asMeasurementLogs("weight"))
        assertEquals(listOf(other), deleteMeasurementDay(state, "weight", selected.dateKey).recordsOf("weight"))
        assertEquals(listOf(selected, other), logs)
        assertEquals(listOf(selected, other), state.recordsOf("weight"))
    }

    /**
     * The screen's removal touches Weight's day alone: every record Weight has of that day goes,
     * and another measurement's records of the same day, two alike among them, stay exactly as
     * they were, as does everything else the state holds.
     */
    @Test
    fun deletingSelectedDateLeavesAnotherMeasurementsRecordsOfTheDayAsTheyWere() {
        val newer = MeasurementLog("weight", "2026-09-16", 3, 84.0)
        val other = MeasurementLog("msr_k2p9", "2026-09-15", 9, 61.0)
        val otherEarlier = MeasurementLog("msr_k2p9", "2026-09-14", 1, 60.0)
        val state = AppState(
            measurementLogs = listOf(
                newer,
                other,
                MeasurementLog("weight", "2026-09-15", 1, 83.5),
                other,
                MeasurementLog("weight", "2026-09-15", 2, 83.6),
                otherEarlier,
            ),
            timeLogs = listOf(TimeLog(ReadingTimeId, "2026-09-15", 5)),
        ).withWeight()

        val removed = deleteMeasurementDay(state, "weight", "2026-09-15")

        assertEquals(listOf(newer, other, other, otherEarlier), removed.measurementLogs)
        assertEquals(listOf(MeasurementRecord("2026-09-16", 3, 84.0)), removed.recordsOf("weight"))
        assertEquals(state.copy(measurementLogs = removed.measurementLogs), removed)
        // A day only the other measurement has a record on has nothing of Weight's to remove.
        assertEquals(state, deleteMeasurementDay(state, "weight", "2026-09-14"))
    }
}

private val German = Locale.GERMANY
