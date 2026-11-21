package com.example.workouttracker.persistence

import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.persistence.backup.BackupArchive
import com.example.workouttracker.persistence.backup.BackupSnapshot
import com.example.workouttracker.persistence.backup.validateRestoreCompatibility
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesHolidayRepositoryTest {
    @Test
    fun holidaysAndSickDaysSurviveVerifiedArchiveAndRestore() {
        val store = MemoryStore()
        val plan = HolidaySchedule()
            .withHoliday(DayRange(LocalDate.parse("2026-12-28"), LocalDate.parse("2027-01-05")))
            .withSickDays(DayRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02")))
            .withSickDays(DayRange(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-05")))
        assertTrue(PreferencesHolidayRepository(store).save(plan))
        val archive = BackupArchive.encode(snapshot(store.values.toMap()))

        val restored = BackupArchive.decode(archive)
        validateRestoreCompatibility(restored)

        assertEquals(plan, PreferencesHolidayRepository(MemoryStore(restored.stores.getValue("workout_tracker"))).load())
        assertEquals(5, plan.holidayDaysIn(2027))
        assertEquals(3, plan.sickDaysIn(2026))
    }

    @Test
    fun oldAndFuturePlansRequireExternalConversion() {
        listOf(
            """{"version":1,"ranges":[{"start":"2026-12-28","end":"2027-01-05"}],"annualAllowanceOverrides":{"2026":25}}""",
            """{"version":3,"ranges":[],"sickRanges":[]}""",
        ).forEach { raw ->
            val stored = mapOf(PreferencesHolidayRepository.SettingsKey to raw)
            assertRejected(snapshot(stored))
            val store = MemoryStore(stored)
            assertTrue(runCatching { PreferencesHolidayRepository(store).load() }.isFailure)
            assertEquals(stored, store.values)
        }
    }

    @Test
    fun failedWriteAndInvalidRestoreAreObservable() {
        val store = MemoryStore(acceptWrites = false)
        assertFalse(PreferencesHolidayRepository(store).save(HolidaySchedule().withSickDays(DayRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-01")))))
        assertEquals(HolidaySchedule(), PreferencesHolidayRepository(store).load())
        // Version 2 has no allowance, overlapping sick days are no plan, and version 3 does not exist yet.
        listOf(
            """{"version":2,"ranges":[],"annualAllowanceOverrides":{}}""",
            """{"version":2,"ranges":[],"sickRanges":[{"start":"2026-10-01","end":"2026-10-03"},{"start":"2026-10-03","end":"2026-10-04"}]}""",
            """{"version":3,"ranges":[],"sickRanges":[]}""",
        ).forEach { raw -> assertRejected(snapshot(mapOf(PreferencesHolidayRepository.SettingsKey to raw))) }
        assertRejected(snapshot(mapOf("holiday_settings_v2" to HolidayScheduleCodec.encode(HolidaySchedule()))))
    }

    private fun snapshot(tracker: Map<String, String>) = BackupSnapshot(mapOf(
        "workout_tracker" to tracker,
        "momentum_reminders" to emptyMap(),
        "momentum_feature_visibility" to emptyMap(),
    ))

    private fun assertRejected(snapshot: BackupSnapshot) {
        var rejected = false
        try {
            validateRestoreCompatibility(snapshot)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private class MemoryStore(
        val values: MutableMap<String, String> = mutableMapOf(),
        var acceptWrites: Boolean = true,
    ) : TrackerStringStore {
        constructor(values: Map<String, String>) : this(values.toMutableMap())

        override fun keys(): Set<String> = values.keys.toSet()
        override fun read(key: String) = values[key]
        override fun writeAll(values: Map<String, String>): Boolean = values.all { (key, value) -> write(key, value) }

        override fun write(key: String, value: String): Boolean {
            if (!acceptWrites) return false
            values[key] = value
            return true
        }
    }
}
