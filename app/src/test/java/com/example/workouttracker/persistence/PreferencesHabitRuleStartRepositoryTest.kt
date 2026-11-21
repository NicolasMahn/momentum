package com.example.workouttracker.persistence

import com.example.workouttracker.persistence.backup.BackupArchive
import com.example.workouttracker.persistence.backup.BackupSnapshot
import com.example.workouttracker.persistence.backup.validateRestoreCompatibility
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesHabitRuleStartRepositoryTest {
    @Test fun firstStartSurvivesVerifiedBackupAndRestore() {
        val store = MemoryStore()
        val first = LocalDate.parse("2026-10-05")
        val repository = PreferencesHabitRuleStartRepository(store)
        assertEquals(first, repository.loadOrStart(first))
        assertEquals(first, repository.loadOrStart(first.plusDays(30)))
        val archive = BackupArchive.encode(BackupSnapshot(mapOf(
            "workout_tracker" to store.values.toMap(),
            "momentum_reminders" to emptyMap(),
            "momentum_feature_visibility" to emptyMap(),
        )))
        val restored = BackupArchive.decode(archive)
        validateRestoreCompatibility(restored)
        assertEquals(first, PreferencesHabitRuleStartRepository(MemoryStore(restored.stores.getValue("workout_tracker").toMutableMap()))
            .loadOrStart(first.plusDays(60)))
    }

    @Test fun invalidOrUnknownRuleStartIsRejectedOnRestore() {
        for ((key, value) in listOf(
            "habit_rules_started_v2" to "2026-10-05",
            PreferencesHabitRuleStartRepository.SettingsKey to "2026-99-99",
        )) {
            val snapshot = BackupSnapshot(mapOf(
                "workout_tracker" to mapOf(key to value),
                "momentum_reminders" to emptyMap(),
                "momentum_feature_visibility" to emptyMap(),
            ))
            var rejected = false
            try { validateRestoreCompatibility(snapshot) } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
        }
    }

    private class MemoryStore(val values: MutableMap<String, String> = mutableMapOf()) : TrackerStringStore {
        override fun keys(): Set<String> = values.keys.toSet()
        override fun read(key: String) = values[key]
        override fun writeAll(values: Map<String, String>): Boolean = values.all { (key, value) -> write(key, value) }

        override fun write(key: String, value: String): Boolean { values[key] = value; return true }
    }
}
