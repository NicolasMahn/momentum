package com.example.workouttracker.domain.measurement

import com.example.workouttracker.domain.AppState

/**
 * One number logged for one measurement on one day, as the tracker state stores it. [value] is the
 * number as it was logged; what it counts, the unit its measurement has, is the measurement's
 * business and not the record's.
 *
 * Nothing about a record is judged where it is stored: a day may hold several, two may be alike in
 * everything, and a value the screen would not take or a date after today is a record like any
 * other. One naming a measurement no configuration knows is kept as it is.
 */
data class MeasurementLog(
    val measurementId: String,
    val dateKey: String,
    val timestampMillis: Long,
    val value: Double,
)

/**
 * [logs] with what [measurementId] holds for [dateKey] replaced by one record. It touches that
 * measurement's day alone: every other record stays as it was, another measurement's of the same
 * day too. The list stays in the order storage keeps, by date and newest first.
 */
fun replaceMeasurementForDate(
    logs: List<MeasurementLog>,
    measurementId: String,
    dateKey: String,
    timestampMillis: Long,
    value: Double,
): List<MeasurementLog> =
    (deleteMeasurementForDate(logs, measurementId, dateKey) + MeasurementLog(measurementId, dateKey, timestampMillis, value))
        .sortedByDescending { it.dateKey }

/** [logs] without what [measurementId] holds for [dateKey]; every other record stays as it was. */
fun deleteMeasurementForDate(logs: List<MeasurementLog>, measurementId: String, dateKey: String): List<MeasurementLog> =
    logs.filterNot { it.measurementId == measurementId && it.dateKey == dateKey }

/** A measurement's recorded value without repeating the view ID supplied by its reader. */
data class MeasurementRecord(val dateKey: String, val timestampMillis: Long, val value: Double)

/** Records of one measurement, in storage order, with its own unit supplied by its configuration. */
fun AppState.recordsOf(measurementId: String): List<MeasurementRecord> =
    measurementLogs.filter { it.measurementId == measurementId }
        .map { MeasurementRecord(it.dateKey, it.timestampMillis, it.value) }

/** Whether [dateKey] holds a record of [measurementId], whatever its number and however many. */
fun AppState.isMeasurementLogged(measurementId: String, dateKey: String): Boolean =
    measurementLogs.any { it.measurementId == measurementId && it.dateKey == dateKey }

/** A view-local record stored under its measurement's stable ID. */
fun MeasurementRecord.asMeasurementLog(measurementId: String) = MeasurementLog(measurementId, dateKey, timestampMillis, value)

/** View-local records stored under the measurement supplied by their owner. */
fun List<MeasurementRecord>.asMeasurementLogs(measurementId: String): List<MeasurementLog> = map { it.asMeasurementLog(measurementId) }
