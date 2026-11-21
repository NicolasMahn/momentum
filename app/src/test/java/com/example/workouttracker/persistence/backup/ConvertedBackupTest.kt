package com.example.workouttracker.persistence.backup

import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.persistence.TrackerStringStore
import com.example.workouttracker.persistence.trackerRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ConvertedBackupTest {
    @Test fun convertedPersonalFactsSurviveTheActualAppCodecStoreAndBackup() {
        val path = System.getenv("MOMENTUM_CONVERTED_BACKUP")
        assumeTrue("Set MOMENTUM_CONVERTED_BACKUP to validate an external conversion", path != null)
        val snapshot = BackupArchive.decode(File(requireNotNull(path)).readBytes())
        validateRestoreCompatibility(snapshot)
        val original = snapshot.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME)
        val store = MemoryStore(original)
        val repository = trackerRepository(store)
        val before = repository.loadIfReadable()
        assertNotNull(before)
        assertTrue(repository.save(requireNotNull(before)))
        assertEquals(before, repository.loadIfReadable())

        val saved = BackupSnapshot(snapshot.stores + (PreferencesTrackerRepository.PREFERENCES_NAME to store.values.toMap()))
        val restored = BackupArchive.decode(BackupArchive.encode(saved))
        validateRestoreCompatibility(restored)
        assertEquals(saved, restored)
        assertEquals(before, trackerRepository(MemoryStore(restored.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME))).loadIfReadable())
        assertEquals(original.keys, store.values.keys)
    }

    private class MemoryStore(initial: Map<String, String>) : TrackerStringStore {
        val values = initial.toMutableMap()
        override fun keys(): Set<String> = values.keys.toSet()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String): Boolean = writeAll(mapOf(key to value))
        override fun writeAll(values: Map<String, String>): Boolean {
            this.values.putAll(values)
            return true
        }
        override fun remove(keys: Set<String>): Boolean = true.also { values -= keys }
    }
}
