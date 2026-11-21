package com.example.workouttracker.persistence

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.persistence.backup.BackupArchive
import com.example.workouttracker.persistence.backup.BackupSnapshot
import com.example.workouttracker.persistence.backup.validateRestoreCompatibility
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedMeasurementPersistenceTest {
    @Test fun signedGenericRecordsAndTheirPolicySurviveTheRepositoryAndVerifiedArchive() {
        val generic = testMadeMeasurement(label = "Temperature", unit = "°C")
        val weight = testWeightMeasurement()
        val state = AppState(
            measurements = listOf(weight, generic),
            measurementLogs = listOf(
                MeasurementLog(generic.id, "2026-10-09", 5L, -2.5),
                MeasurementLog(weight.id, "2026-10-09", 4L, 82.0),
                MeasurementLog(generic.id, "2026-10-08", 3L, 0.0),
                MeasurementLog(generic.id, "2026-10-07", 2L, 3.5),
                // Historical Weight records remain facts even when entry would refuse them.
                MeasurementLog(weight.id, "2026-10-06", 1L, -1.0),
            ),
        ).withActivity()
        val store = MemoryStore()
        assertTrue(trackerRepository(store).save(state))
        val raw = store.values.getValue(PreferencesTrackerRepository.STATE_V30_KEY)
        val encoded = JSONObject(raw)
        assertEquals(30, encoded.getInt("schemaVersion"))
        encoded.getJSONArray("measurements").let { measurements ->
            (0 until measurements.length()).forEach { assertFalse(measurements.getJSONObject(it).has("valuePolicy")) }
        }
        assertEquals(state, AppStateJsonCodec.decodeVersion30(raw))
        assertFalse(AppStateJsonCodec.lostRecords(raw, state))
        assertEquals(raw, AppStateJsonCodec.encode(AppStateJsonCodec.decodeVersion30(raw)))
        val snapshot = BackupSnapshot(BackupArchive.StoreNames.associateWith { name ->
            if (name == "workout_tracker") store.values.toMap() else emptyMap()
        })
        val restored = BackupArchive.decode(BackupArchive.encode(snapshot))
        validateRestoreCompatibility(restored)
        assertEquals(snapshot, restored)
        val read = trackerRepository(MemoryStore(restored.stores.getValue("workout_tracker"))).load()
        assertEquals(state, read)
        assertEquals(MeasurementValuePolicy.Positive, read.measurements.first().valuePolicy)
        assertEquals(MeasurementValuePolicy.Finite, read.measurements.last().valuePolicy)
    }

    @Test fun configurationEncodingRejectsPoliciesTheUnchangedWireShapeCannotRecover() {
        listOf(
            testWeightMeasurement().copy(valuePolicy = MeasurementValuePolicy.Finite),
            testMadeMeasurement().copy(valuePolicy = MeasurementValuePolicy.Positive),
        ).forEach { view ->
            assertThrows(IllegalArgumentException::class.java) { AppStateJsonCodec.encodeMeasurement(view) }
            assertThrows(IllegalArgumentException::class.java) { AppStateJsonCodec.encode(AppState(measurements = listOf(view))) }
            val store = MemoryStore()
            assertFalse(trackerRepository(store).save(AppState(measurements = listOf(view)).withActivity()))
            assertTrue(store.values.isEmpty())
        }
    }

    private class MemoryStore(initial: Map<String, String> = emptyMap()) : TrackerStringStore {
        val values = initial.toMutableMap()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String): Boolean = true.also { values[key] = value }
        override fun writeAll(values: Map<String, String>): Boolean = true.also { this.values.putAll(values) }
    }
}
