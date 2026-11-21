package com.example.workouttracker.feature.measurement

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DayChange
import com.example.workouttracker.domain.DayChangeRefusal
import com.example.workouttracker.domain.measurement.recordsOf
import com.example.workouttracker.domain.measurement.MeasurementRecord

/**
 * What a measurement's screen asks to be stored for one day: a number to save or the day's record
 * to remove. It names the measurement and the day, and holds the record the draft opened on
 * ([opened], null for a day without one), so it can be decided against the records as they are
 * stored at that moment and never against the state the screen happened to be shown.
 */
internal sealed interface MeasurementDayChange : DayChange {
    val measurementId: String
    val dateKey: String
    val opened: MeasurementRecord?

    data class Save(
        override val measurementId: String,
        override val dateKey: String,
        override val opened: MeasurementRecord?,
        val value: Double,
    ) : MeasurementDayChange

    data class Remove(
        override val measurementId: String,
        override val dateKey: String,
        override val opened: MeasurementRecord?,
    ) : MeasurementDayChange

    /**
     * The measurement must still exist, by its configuration, and the day's record must still be
     * the one the draft opened on. A draft or notification reply that outlives deletion must not
     * recreate that tracker or overwrite another record.
     */
    override fun refusalIn(state: AppState): DayChangeRefusal? = when {
        state.measurements.none { it.id == measurementId } -> DayChangeRefusal.Deleted
        state.measurementDayRecord(measurementId, dateKey) != opened -> DayChangeRefusal.ChangedElsewhere
        else -> null
    }

    override fun appliedTo(state: AppState, timestampMillis: Long): AppState = when (this) {
        is Save -> {
            val measurement = state.measurements.first { it.id == measurementId }
            require(measurement.acceptsValue(value)) { "Value is outside the measurement policy." }
            replaceMeasurementDay(state, measurementId, dateKey, timestampMillis, value)
        }
        is Remove -> deleteMeasurementDay(state, measurementId, dateKey)
    }
}

/** The day's record as the screen, its draft and its chart choose it among several: the latest time, the first in the list winning a tie. */
internal fun AppState.measurementDayRecord(measurementId: String, dateKey: String): MeasurementRecord? =
    recordsOf(measurementId).filter { it.dateKey == dateKey }.maxByOrNull { it.timestampMillis }
