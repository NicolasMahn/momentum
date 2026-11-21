package com.example.workouttracker.domain.measurement

import kotlin.math.pow

/** A value placed on a day line; gaps remain real elapsed days. */
data class IndexedMeasurement(val dayIndex: Int, val value: Double)
data class MeasurementTrendValue(val dayIndex: Int, val trendValue: Double)

/**
 * How many days a measurement's trend follows unless it is set otherwise: three weeks, close to
 * the tenth a day that weight-tracking settled on (The Hacker's Diet, Libra, TrendWeight). Day to
 * day a scale mostly measures water, so one reading may nudge the trend but never define it.
 */
const val DefaultMeasurementTrendDays = 21

/** The shortest and longest span a trend can follow. */
val MeasurementTrendDayRange = 2..90

/**
 * The daily smoothing of a trend that follows [days], as an exponential moving average's span
 * defines it (`2 / (days + 1)`): its readings are on average as old as those of a plain average
 * over the same days, so "21 days" lags like a three-week average and moves without its jumps.
 */
fun measurementTrendSmoothing(days: Int): Double = 2.0 / (days + 1)

/**
 * An exponentially weighted moving average over the measurements, seeded with the first one.
 *
 * The step is time aware: a reading taken after a skipped day counts for as many daily steps as it
 * stands in for (`1 - (1 - smoothing)^days`), so a gap neither freezes the trend nor lets a late
 * reading count as a single day. Feed it the full history; a trend started mid-window is wrong at
 * its left edge.
 */
fun measurementTrendSeries(
    measurements: List<IndexedMeasurement>,
    smoothing: Double = measurementTrendSmoothing(DefaultMeasurementTrendDays),
): List<MeasurementTrendValue> {
    val ordered = measurements.sortedBy { it.dayIndex }
    if (ordered.isEmpty()) return emptyList()
    val retention = (1.0 - smoothing).coerceIn(0.0, 1.0)
    var trend = ordered.first().value
    var previousDay = ordered.first().dayIndex
    return ordered.mapIndexed { index, measurement ->
        if (index > 0) {
            val days = (measurement.dayIndex - previousDay).coerceAtLeast(1)
            val weight = 1.0 - retention.pow(days)
            val difference = measurement.value - trend
            trend = if (difference.isFinite()) trend + weight * difference
            else (1.0 - weight) * trend + weight * measurement.value
            previousDay = measurement.dayIndex
        }
        MeasurementTrendValue(measurement.dayIndex, trend)
    }
}
