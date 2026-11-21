package com.example.workouttracker.persistence

import com.example.workouttracker.domain.reminder.MoCheckInSettings
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PreferencesReminderSettingsRepositoryTest {
    private val configured = ReminderSettings(
        workout = WorkoutReminderSettings(true, ReminderTime(18, 35)),
        moCheckIn = MoCheckInSettings(false, ReminderTime(21, 10)),
        fallbackAlarmTime = ReminderTime(7, 15),
        fixedBedtime = ReminderTime(23, 45),
    )

    @Test fun missingSettingsUseDefaultsWithoutWriting() {
        val store = Store()
        assertEquals(ReminderSettings(), PreferencesReminderSettingsRepository(store).load())
        assertTrue(store.values.isEmpty())
    }

    @Test fun allCurrentSettingsRoundTripIncludingNullableBedtime() {
        val store = Store()
        val repository = PreferencesReminderSettingsRepository(store)
        listOf(configured, configured.copy(fixedBedtime = null)).forEach { settings ->
            assertTrue(repository.save(settings))
            assertEquals(settings, repository.load())
            assertEquals(setOf(PreferencesReminderSettingsRepository.SETTINGS_KEY), store.values.keys)
            assertEquals(13, JSONObject(store.values.getValue(PreferencesReminderSettingsRepository.SETTINGS_KEY)).getInt("version"))
        }
    }

    @Test fun olderKeysAreNeverReadOrMigrated() {
        val store = Store().apply {
            (1..12).forEach { values["settings_v$it"] = ReminderSettingsCodec.encode(configured) }
        }
        val before = store.values.toMap()
        assertEquals(ReminderSettings(), PreferencesReminderSettingsRepository(store).load())
        assertEquals(before, store.values)
    }

    @Test fun oldFutureAndMalformedCurrentPayloadsRemainUntouched() {
        val current = ReminderSettingsCodec.encode(configured)
        val invalid = listOf(
            "{broken", "null", current.replace("\"version\":13", "\"version\":12"),
            current.replace("\"version\":13", "\"version\":14"),
            JSONObject(current).put("weight", JSONObject().put("enabled", true)).toString(),
            JSONObject(current).apply { getJSONObject("workout").put("days", "MON") }.toString(),
            JSONObject(current).apply { getJSONObject("fallbackAlarm").put("hour", 25) }.toString(),
            JSONObject(current).apply { getJSONObject("moCheckIn").put("enabled", "true") }.toString(),
            JSONObject(current).apply { getJSONObject("workout").put("minute", 1.5) }.toString(),
        )
        invalid.forEach { raw ->
            val store = Store().apply { values[PreferencesReminderSettingsRepository.SETTINGS_KEY] = raw }
            assertNull(raw, ReminderSettingsCodec.decode(raw))
            assertEquals(raw, ReminderSettings(), PreferencesReminderSettingsRepository(store).load())
            assertEquals(raw, store.values.getValue(PreferencesReminderSettingsRepository.SETTINGS_KEY))
        }
    }

    @Test fun failedSaveKeepsThePersistedConfiguration() {
        val store = Store()
        val repository = PreferencesReminderSettingsRepository(store)
        assertTrue(repository.save(configured))
        store.acceptWrites = false
        assertFalse(repository.save(ReminderSettings()))
        assertEquals(configured, repository.load())
    }

    private class Store : TrackerStringStore {
        val values = mutableMapOf<String, String>()
        var acceptWrites = true
        override fun keys(): Set<String> = values.keys.toSet()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String): Boolean {
            if (!acceptWrites) return false
            values[key] = value
            return true
        }
        override fun writeAll(values: Map<String, String>): Boolean =
            if (acceptWrites) { this.values.putAll(values); true } else false
    }
}
