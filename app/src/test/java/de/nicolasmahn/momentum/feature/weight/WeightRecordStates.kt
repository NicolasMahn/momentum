package de.nicolasmahn.momentum.feature.weight

import de.nicolasmahn.momentum.core.time.isValidDateKey
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.testActivityConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement

/** Screen fixtures contain current facts directly, independently of the persistence codec. */
internal class WeightRecordState(val name: String, vararg candidates: MeasurementLog) {
    // Invalid candidates exercise the fixture boundary; production storage refuses them instead.
    // Duplicate facts and stable ordering within a day remain visible to the screen's readers.
    val state = AppState(
        activity = testActivityConfiguration(),
        measurements = listOf(testWeightMeasurement()),
        measurementLogs = candidates.filter { isValidDateKey(it.dateKey) && it.timestampMillis >= 0 && it.value.isFinite() }
            .sortedByDescending { it.dateKey },
    )
}

private fun record(dateKey: String, timestampMillis: Long, value: Double) = MeasurementLog("weight", dateKey, timestampMillis, value)

internal const val WeightRecordStatesToday = "2026-10-09"

internal val WeightRecordStates = listOf(
    WeightRecordState(
        "several records of one day, two at the same time",
        record("2026-10-08", 5, 70.0),
        record("2026-10-08", 9, 82.0),
        record("2026-10-08", 9, 85.0),
        record("2026-10-07", 2, 80.0),
        record("2026-10-06", 1, 78.0),
    ),
    WeightRecordState(
        "two identical records",
        record("2026-10-08", 7, 81.5),
        record("2026-10-08", 7, 81.5),
    ),
    WeightRecordState(
        "a record without a time",
        record("2026-10-08", 0, 81.5),
        record("2026-10-08", 4, 79.0),
        record("2026-10-07", 0, 80.0),
    ),
    WeightRecordState(
        "a value of zero and one below",
        record("2026-10-08", 3, 0.0),
        record("2026-10-08", 2, 81.0),
        record("2026-10-07", 1, -1.5),
        record("2026-10-06", 1, 80.0),
    ),
    WeightRecordState(
        "a date in the future",
        record("2026-10-12", 6, 90.0),
        record("2026-10-08", 1, 80.0),
    ),
    WeightRecordState(
        "only a date in the future",
        record("2026-10-12", 6, 90.0),
    ),
    WeightRecordState(
        "records stored out of date order",
        record("2026-10-06", 1, 78.0),
        record("2026-10-08", 3, 82.0),
        record("2026-10-07", 2, 80.0),
        record("2026-10-08", 3, 84.0),
    ),
    WeightRecordState(
        "a damaged entry between good ones",
        record("2026-10-08", 3, 82.0),
        record("", 4, 83.0),
        record("", 5, 84.0),
        record("2026-10-08", 6, Double.NaN),
        record("2026-10-08", 7, Double.NaN),
        record("", 0, Double.NaN),
        record("2026-10-07", 2, 80.5),
    ),
    WeightRecordState(
        "no records",
    ),
)

/** Includes one day before any fact and one after today. */
internal val WeightRecordStatesDays = listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-12")
