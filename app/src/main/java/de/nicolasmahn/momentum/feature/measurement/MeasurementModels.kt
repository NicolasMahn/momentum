package de.nicolasmahn.momentum.feature.measurement

import androidx.annotation.StringRes
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.time.isValidDateKey
import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.core.time.requireValidDateKey
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.measurement.replaceMeasurementForDate
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import de.nicolasmahn.momentum.domain.measurement.MeasurementValuePolicy
import de.nicolasmahn.momentum.domain.measurement.IndexedMeasurement
import de.nicolasmahn.momentum.domain.measurement.DefaultMeasurementTrendDays
import de.nicolasmahn.momentum.domain.measurement.measurementTrendSeries
import de.nicolasmahn.momentum.domain.measurement.measurementTrendSmoothing
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal const val MeasurementHistoryDayCount = 28

/** Under this many readings an average is just the readings again, so the chart shows only them. */
internal const val MinimumTrendMeasurements = 3

/** How far the chart can be asked to look back; months of data stay readable, decades do not. */
private const val MaximumHistoryDayCount = 1830

private const val ThreeMonthDayCount = 91
private const val MillisPerDay = 24L * 60L * 60L * 1000L

/** How far back the chart looks. "All" stretches to the first reading, never past the limit. */
internal enum class MeasurementHistoryRange(@param:StringRes val label: Int) {
    FourWeeks(R.string.measurement_range_four_weeks),
    ThreeMonths(R.string.measurement_range_three_months),
    All(R.string.measurement_range_all),
}

internal data class MeasurementHistoryDay(
    val dateKey: String,
    val dayIndex: Int,
    val log: MeasurementRecord?,
    /** The smoothed value in force on this day, or null while a trend would say nothing. */
    val trendValue: Double? = null,
)

internal data class MeasurementTrendPoint(
    val dateKey: String,
    val dayIndex: Int,
    val value: Double,
)

internal data class MeasurementHistoryWindow(
    val days: List<MeasurementHistoryDay>,
    /** The line the chart draws: the trend once [smoothed], otherwise the measurements themselves. */
    val trendPoints: List<MeasurementTrendPoint>,
    val smoothed: Boolean = false,
) {
    val recordedDays: List<MeasurementHistoryDay> = days.filter { it.log != null }
}

/**
 * [state] with what [measurementId] holds for [dateKey] replaced by the one record saved now. Only
 * that measurement's day is touched: every other record stays as it was, another measurement's of
 * the same day too.
 */
internal fun replaceMeasurementDay(
    state: AppState,
    measurementId: String,
    dateKey: String,
    timestampMillis: Long,
    value: Double,
): AppState {
    requireValidDateKey(dateKey, subject = "Measurement date")

    return state.copy(
        measurementLogs = replaceMeasurementForDate(state.measurementLogs, measurementId, dateKey, timestampMillis, value),
    )
}

/**
 * The chart's days up to today with the trend that follows [trendDays]. Every record shows as a
 * dot; only a value [valuePolicy] accepts moves the trend, which is an estimate.
 */
internal fun measurementHistoryWindow(
    logs: List<MeasurementRecord>,
    todayKey: String,
    range: MeasurementHistoryRange = MeasurementHistoryRange.FourWeeks,
    trendDays: Int = DefaultMeasurementTrendDays,
    valuePolicy: MeasurementValuePolicy = MeasurementValuePolicy.Finite,
): MeasurementHistoryWindow {
    val today = requireValidDateKey(todayKey, subject = "Today")
    return measurementHistoryWindow(logs, todayKey, historyDayCount(logs, today, range), trendDays, valuePolicy)
}

/** The same window for a reader that brings its own span, such as Statistics' range switch. */
internal fun measurementHistoryWindow(
    logs: List<MeasurementRecord>,
    todayKey: String,
    dayCount: Int,
    trendDays: Int = DefaultMeasurementTrendDays,
    valuePolicy: MeasurementValuePolicy = MeasurementValuePolicy.Finite,
): MeasurementHistoryWindow {
    require(dayCount > 0) { "Measurement history must cover at least one day." }
    val today = requireValidDateKey(todayKey, subject = "Today")
    // A rolling window, not a week grid: the chart's axis is time itself, so it ends on today.
    val windowStart = today.minusDays(dayCount - 1L)
    val dateKeys = List(dayCount) { index -> windowStart.plusDays(index.toLong()).toString() }
    // One reading per day: a second weighing on the same date replaces the earlier one.
    val latestLogByDate = logs
        .asSequence()
        .filter { isValidDateKey(it.dateKey) }
        .groupBy { it.dateKey }
        .mapValues { (_, sameDayLogs) -> sameDayLogs.maxBy { it.timestampMillis } }

    // The trend runs over the whole history, including readings older than the window: started at
    // the window's left edge it would be seeded with one reading and wrong exactly where it shows.
    // A day's latest record decides it first; a value the policy refuses then moves no trend.
    val measurements = latestLogByDate.values
        .filter { valuePolicy.accepts(it.value) }
        .map {
            IndexedMeasurement(
                dayIndex = daysBetween(windowStart, parseDateKey(it.dateKey)!!),
                value = it.value,
            )
        }
        .filter { it.dayIndex <= dateKeys.lastIndex }
        .sortedBy { it.dayIndex }
    val trendSeries = measurementTrendSeries(measurements, measurementTrendSmoothing(trendDays))
    val smoothed = measurements.size >= MinimumTrendMeasurements

    var cursor = 0
    var carriedTrend: Double? = null
    val days = dateKeys.mapIndexed { index, dateKey ->
        while (cursor < trendSeries.size && trendSeries[cursor].dayIndex <= index) {
            carriedTrend = trendSeries[cursor].trendValue
            cursor++
        }
        MeasurementHistoryDay(
            dateKey = dateKey,
            dayIndex = index,
            log = latestLogByDate[dateKey],
            trendValue = if (smoothed) carriedTrend else null,
        )
    }

    // The line turns at measured days; between them it is an estimate the chart draws differently.
    val vertices = days.filter { day -> day.log?.value?.let(valuePolicy::accepts) == true }
    val edgeValue = if (smoothed) {
        days.first().trendValue
    } else {
        measurements.lastOrNull { it.dayIndex < 0 }?.value
    }
    val trendPoints = buildList {
        if (edgeValue != null && vertices.firstOrNull()?.dayIndex != 0) {
            add(MeasurementTrendPoint(dateKeys.first(), 0, edgeValue))
        }
        vertices.forEach { day ->
            add(
                MeasurementTrendPoint(
                    dateKey = day.dateKey,
                    dayIndex = day.dayIndex,
                    value = if (smoothed) day.trendValue!! else day.log!!.value,
                ),
            )
        }
        lastOrNull()?.takeIf { it.dayIndex < days.lastIndex }?.let { latestPoint ->
            add(MeasurementTrendPoint(dateKeys.last(), days.lastIndex, latestPoint.value))
        }
    }

    return MeasurementHistoryWindow(days = days, trendPoints = trendPoints, smoothed = smoothed)
}

private fun historyDayCount(logs: List<MeasurementRecord>, today: LocalDate, range: MeasurementHistoryRange): Int =
    when (range) {
        MeasurementHistoryRange.FourWeeks -> MeasurementHistoryDayCount
        MeasurementHistoryRange.ThreeMonths -> ThreeMonthDayCount
        MeasurementHistoryRange.All -> {
            val earliest = logs.mapNotNull { parseDateKey(it.dateKey) }.minOrNull()
            val span = earliest?.let { daysBetween(it, today) + 1 } ?: MeasurementHistoryDayCount
            span.coerceIn(MeasurementHistoryDayCount, MaximumHistoryDayCount)
        }
    }

private fun daysBetween(from: LocalDate, to: LocalDate): Int =
    ChronoUnit.DAYS.between(from, to).toInt()
