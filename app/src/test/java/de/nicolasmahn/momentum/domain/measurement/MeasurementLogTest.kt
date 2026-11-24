package de.nicolasmahn.momentum.domain.measurement

import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId

import de.nicolasmahn.momentum.domain.measurement.recordsOf

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementLogTest {
    private val stored = listOf(
        MeasurementLog("msr_k2p9", "2026-10-08", 12, 61.0),
        MeasurementLog(WeightMeasurementId, "2026-10-08", 9, 82.0),
        MeasurementLog(WeightMeasurementId, "2026-10-08", 9, 82.0),
        MeasurementLog("msr_k2p9", "2026-10-08", 3, 60.5),
        MeasurementLog(WeightMeasurementId, "2026-10-07", 0, 0.0),
        MeasurementLog("msr_k2p9", "2026-10-05", 1, 60.0),
    )

    /** The readers of Weight see its records alone, in stored order, two alike still two and no value judged. */
    @Test fun weightLogsAreTheStoredRecordsOfWeightInTheirOrder() {
        val state = AppState(measurementLogs = stored)

        assertEquals(
            listOf(MeasurementRecord("2026-10-08", 9, 82.0), MeasurementRecord("2026-10-08", 9, 82.0), MeasurementRecord("2026-10-07", 0, 0.0)),
            state.recordsOf("weight"),
        )
        assertEquals(stored, state.measurementLogs)
        assertEquals(emptyList<MeasurementRecord>(), AppState(measurementLogs = stored.filter { it.measurementId != WeightMeasurementId }).recordsOf("weight"))
    }

    @Test fun recordSelectionUsesTheCurrentStatesRecordsAndDoesNotChangeAnEarlierState() {
        val state = AppState(measurementLogs = stored)
        val first = state.recordsOf("weight")
        val added = MeasurementRecord("2026-10-09", 4, 83.0)
        val changed = state.copy(measurementLogs = listOf(added.asMeasurementLog("weight")) + stored)

        assertEquals(listOf(added) + first, changed.recordsOf("weight"))
        assertEquals(first, state.recordsOf("weight"))
        assertEquals(first, state.withWeight().recordsOf("weight"))
        assertEquals(stored, state.measurementLogs)
    }

    /**
     * Replacing a measurement's day touches that measurement's day alone: what it held for the day
     * becomes the one record, and every other record stays as it was and where it was among the
     * others, another measurement's of the same day too.
     */
    @Test fun replacingAMeasurementsDayLeavesEveryOtherRecordAsItWas() {
        val replaced = replaceMeasurementForDate(stored, WeightMeasurementId, "2026-10-08", 20, 83.5)

        assertEquals(
            listOf(
                MeasurementLog("msr_k2p9", "2026-10-08", 12, 61.0),
                MeasurementLog("msr_k2p9", "2026-10-08", 3, 60.5),
                MeasurementLog(WeightMeasurementId, "2026-10-08", 20, 83.5),
                MeasurementLog(WeightMeasurementId, "2026-10-07", 0, 0.0),
                MeasurementLog("msr_k2p9", "2026-10-05", 1, 60.0),
            ),
            replaced,
        )
        assertEquals(stored.filter { it.measurementId == "msr_k2p9" }, replaced.filter { it.measurementId == "msr_k2p9" })

        // The other measurement's own day is replaced the same way, and Weight's records of it stay.
        val othersDay = replaceMeasurementForDate(stored, "msr_k2p9", "2026-10-08", 20, 62.0)
        assertEquals(stored.filter { it.measurementId == WeightMeasurementId }, othersDay.filter { it.measurementId == WeightMeasurementId })
        assertEquals(
            listOf(MeasurementLog("msr_k2p9", "2026-10-08", 20, 62.0), MeasurementLog("msr_k2p9", "2026-10-05", 1, 60.0)),
            othersDay.filter { it.measurementId == "msr_k2p9" },
        )

        // A day nothing was recorded on gets its record in date order, newest first.
        assertEquals(
            listOf("2026-10-08", "2026-10-08", "2026-10-08", "2026-10-08", "2026-10-07", "2026-10-06", "2026-10-05"),
            replaceMeasurementForDate(stored, WeightMeasurementId, "2026-10-06", 5, 81.0).map { it.dateKey },
        )
    }

    /** Removing a measurement's day takes every record it held for that day and nothing else. */
    @Test fun deletingAMeasurementsDayLeavesEveryOtherRecordAsItWas() {
        assertEquals(
            listOf(
                MeasurementLog("msr_k2p9", "2026-10-08", 12, 61.0),
                MeasurementLog("msr_k2p9", "2026-10-08", 3, 60.5),
                MeasurementLog(WeightMeasurementId, "2026-10-07", 0, 0.0),
                MeasurementLog("msr_k2p9", "2026-10-05", 1, 60.0),
            ),
            deleteMeasurementForDate(stored, WeightMeasurementId, "2026-10-08"),
        )
        assertEquals(stored, deleteMeasurementForDate(stored, WeightMeasurementId, "2026-10-05"))
        assertEquals(stored, deleteMeasurementForDate(stored, "msr_zzzz", "2026-10-08"))
    }

    /** A weight record is stored under Weight's ID with its day, its time and its number, and read back as it was. */
    @Test fun aWeightRecordIsStoredAsARecordOfWeightAndReadBackAsItWas() {
        val records = listOf(MeasurementRecord("2026-10-08", 9, 82.0), MeasurementRecord("2026-10-08", 9, 82.0), MeasurementRecord("2026-10-07", 0, -1.5))

        assertEquals(MeasurementLog("weight", "2026-10-08", 9, 82.0), records.first().asMeasurementLog("weight"))
        assertEquals(
            listOf(MeasurementLog("weight", "2026-10-08", 9, 82.0), MeasurementLog("weight", "2026-10-08", 9, 82.0), MeasurementLog("weight", "2026-10-07", 0, -1.5)),
            records.asMeasurementLogs("weight"),
        )
        assertEquals(records, AppState(measurementLogs = records.asMeasurementLogs("weight")).recordsOf("weight"))
    }
}
