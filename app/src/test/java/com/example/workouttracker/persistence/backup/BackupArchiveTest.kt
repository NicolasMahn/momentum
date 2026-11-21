package com.example.workouttracker.persistence.backup

import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.persistence.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.example.workouttracker.persistence.rows.MemoryRowStore

class BackupArchiveTest {
    @Test fun currentSnapshotLoadsThroughEveryRealRepositoryAfterAnArchiveRoundTrip() {
        val snapshot = BackupArchive.decode(BackupArchive.encode(CurrentBackupFixture.snapshot()))
        validateRestoreCompatibility(snapshot)
        val tracker = SnapshotStringStore(snapshot.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME))
        val defaults = object : ConfigurationDefaults {
            override fun activity(): ActivityConfiguration = requireNotNull(CurrentBackupFixture.state.activity)
        }
        assertEquals(CurrentBackupFixture.state, PreferencesTrackerRepository(tracker, defaults = defaults, rows = MemoryRowStore(), keepBeforeRows = { true }).loadIfReadable())
        assertEquals(CurrentBackupFixture.holidays, PreferencesHolidayRepository(tracker).load())
        assertEquals(CurrentBackupFixture.passChoices, PreferencesPassChoiceRepository(tracker).load())
        assertEquals(CurrentBackupFixture.ruleStart, PreferencesHabitRuleStartRepository(tracker).loadOrStart(CurrentBackupFixture.ruleStart.plusDays(1)))
        assertEquals(CurrentBackupFixture.reminders, PreferencesReminderSettingsRepository(SnapshotStringStore(snapshot.stores.getValue(PreferencesReminderSettingsRepository.PREFERENCES_NAME))).load())
        assertEquals(CurrentBackupFixture.visibility, PreferencesFeatureVisibilityRepository(SnapshotStringStore(snapshot.stores.getValue(PreferencesFeatureVisibilityRepository.PREFERENCES_NAME))).load())
        assertEquals(CurrentBackupFixture.snapshot(), snapshot)
    }

    @Test fun rescueArchivesKeepDamagedBytesButCannotBeRestoredAsCurrentData() {
        val damaged = emptySnapshot().copy(stores = emptySnapshot().stores + ("workout_tracker" to mapOf("state_v32" to "{broken", "unreadable_state_v7" to "private history")))
        val copy = BackupArchive.decode(BackupArchive.encode(damaged))
        assertEquals(damaged, copy)
        rejected { validateRestoreCompatibility(copy) }
    }

    @Test
    fun emptyStoresRemainExplicitAndArchiveIsReadable() {
        val snapshot = emptySnapshot()
        val first = BackupArchive.encode(snapshot)
        val second = BackupArchive.encode(snapshot)
        assertTrue(first.toString(Charsets.UTF_8).contains("createdAt"))
        assertEquals(snapshot, BackupArchive.decode(first))
        assertEquals(snapshot, BackupArchive.decode(second))
    }

    @Test
    fun corruptionAndTruncationNeverReturnPartialSnapshot() {
        val bytes = BackupArchive.encode(emptySnapshot())
        listOf(0, 1, bytes.size / 2, bytes.size - 1).forEach { length ->
            rejected { BackupArchive.decode(bytes.copyOf(length)) }
        }
        val changed = JSONObject(bytes.toString(Charsets.UTF_8))
        changed.getJSONObject("stores").getJSONObject("workout_tracker").put("state_v32", "changed")
        rejected { BackupArchive.decode(changed.toString().toByteArray()) }
        rejected { BackupArchive.decode(bytes + "trailing".toByteArray()) }
        rejected { BackupArchive.decode(bytes + byteArrayOf(0)) }
    }

    @Test
    fun unsupportedOrMistypedMetadataIsRejected() {
        val bytes = BackupArchive.encode(emptySnapshot())
        listOf("version" to 2, "version" to "1", "format" to "other", "createdAt" to "yesterday", "sha256" to "invalid").forEach { (key, value) ->
            val changed = JSONObject(bytes.toString(Charsets.UTF_8)).put(key, value)
            rejected { BackupArchive.decode(changed.toString().toByteArray()) }
        }
        val extra = JSONObject(bytes.toString(Charsets.UTF_8)).put("extra", true)
        rejected { BackupArchive.decode(extra.toString().toByteArray()) }
    }

    @Test
    fun invalidStoreNamesAndValuesAreRejected() {
        val bytes = BackupArchive.encode(emptySnapshot())
        val transformations: List<(JSONObject) -> Unit> = listOf(
            { it.remove("workout_tracker") },
            { it.put("unknown_store", JSONObject()) },
            { it.put("workout_tracker", "not an object") },
            { it.put("workout_tracker", JSONObject().put("state_v32", 7)) },
            { it.put("workout_tracker", JSONObject().put("state_v32", JSONObject.NULL)) },
        )
        transformations.forEach { transform ->
            val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
            transform(envelope.getJSONObject("stores"))
            rejected { BackupArchive.decode(envelope.toString().toByteArray()) }
        }
        rejected { BackupArchive.encode(BackupSnapshot(emptyMap())) }
        rejected { BackupArchive.encode(emptySnapshot().copy(stores = emptySnapshot().stores + ("unknown" to emptyMap()))) }
    }

    @Test
    fun oversizedArchivesAreRejected() {
        rejected { BackupArchive.decode(ByteArray(BackupArchive.MaxArchiveBytes + 1)) }
        val huge = emptySnapshot().copy(stores = emptySnapshot().stores + ("workout_tracker" to mapOf("large" to "x".repeat(BackupArchive.MaxArchiveBytes))))
        rejected { BackupArchive.encode(huge) }
    }

    @Test
    fun checksumIsNotAuthenticationAnyoneCanProduceAnEditedValidArchive() {
        val original = emptySnapshot()
        val changed = original.copy(stores = original.stores + ("workout_tracker" to mapOf("state_v32" to "deliberately edited")))
        val rewritten = BackupArchive.encode(changed)
        assertEquals(changed, BackupArchive.decode(rewritten))
        assertFalse(original == BackupArchive.decode(rewritten))
    }

    @Test
    fun readableViewCannotContradictAuthoritativeStrings() {
        val encoded = BackupArchive.encode(emptySnapshot())
        val envelope = JSONObject(encoded.toString(Charsets.UTF_8))
        envelope.getJSONObject("readableData").getJSONObject("workout_tracker").put("misleading", "value")
        rejected { BackupArchive.decode(envelope.toString().toByteArray()) }
    }

    private fun emptySnapshot() = BackupSnapshot(BackupArchive.StoreNames.associateWith { emptyMap() })

    private fun rejected(action: () -> Any?) {
        assertTrue("Invalid archive should fail before returning data", runCatching(action).isFailure)
    }

}
