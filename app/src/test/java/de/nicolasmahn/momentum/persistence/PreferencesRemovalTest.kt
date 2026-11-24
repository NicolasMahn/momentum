package de.nicolasmahn.momentum.persistence

import android.content.SharedPreferences
import de.nicolasmahn.momentum.persistence.backup.CurrentBackupFixture
import de.nicolasmahn.momentum.persistence.json.AppStateJsonCodec
import de.nicolasmahn.momentum.persistence.rows.MemoryRowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The document's removal after an import, against preferences that behave as Android's do: memory
 * changes before the file, a failed write leaves the file as it was, and removing a key memory no
 * longer holds reports success without writing.
 */
class PreferencesRemovalTest {
    private val state = CurrentBackupFixture.state
    private val document = mapOf(
        PreferencesTrackerRepository.STATE_V32_KEY to AppStateJsonCodec.encode(state),
        PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY to RecordedTimeSessionsCodec.encode(state.recordedTimeSessions),
    )

    @Test
    fun aRemovalThatFailedIsRetriedUntilTheFileChanges() {
        val preferences = AndroidLikePreferences(document)
        val rows = MemoryRowStore()
        fun repository() = trackerRepository(SharedPreferencesTrackerStringStore(preferences), rows = rows)

        preferences.failNextCommit = true
        assertEquals(state, repository().loadIfReadable())
        // The file still holds the document, and the marker still names it.
        assertEquals(document, preferences.disk)
        assertEquals(fingerprint(document), rows.meta(PreferencesTrackerRepository.ImportedKey))

        // The next read finds the document in memory again, so its removal writes the file.
        assertEquals(state, repository().loadIfReadable())
        assertEquals(emptyMap<String, Any?>(), preferences.disk)
        assertNull(rows.meta(PreferencesTrackerRepository.ImportedKey))

        preferences.restart()
        assertEquals(state, repository().loadIfReadable())
        assertTrue(repository().save(state))
    }

    private class AndroidLikePreferences(initial: Map<String, String>) : SharedPreferences {
        val disk = initial.toMutableMap<String, Any?>()
        private var memory = disk.toMutableMap()
        var failNextCommit = false

        fun restart() { memory = disk.toMutableMap() }

        override fun getAll(): MutableMap<String, *> = memory.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = memory[key] as String? ?: defValue
        override fun contains(key: String): Boolean = key in memory
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = error("unused")
        override fun getInt(key: String, defValue: Int) = error("unused")
        override fun getLong(key: String, defValue: Long) = error("unused")
        override fun getFloat(key: String, defValue: Float) = error("unused")
        override fun getBoolean(key: String, defValue: Boolean) = error("unused")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        inner class Editor : SharedPreferences.Editor {
            private val puts = mutableMapOf<String, String>()
            private val removals = mutableSetOf<String>()
            private var clear = false
            override fun putString(key: String, value: String?) = apply { puts[key] = requireNotNull(value) }
            override fun remove(key: String) = apply { removals += key }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean {
                val before = memory.toMap()
                if (clear) memory.clear()
                memory -= removals
                memory += puts
                // Android writes the file only when memory changed, and reports success either way.
                if (memory == before) return true
                if (failNextCommit) { failNextCommit = false; return false }
                disk.clear(); disk += memory
                return true
            }
            override fun apply() { commit() }
            override fun putStringSet(key: String, values: MutableSet<String>?) = error("unused")
            override fun putInt(key: String, value: Int) = error("unused")
            override fun putLong(key: String, value: Long) = error("unused")
            override fun putFloat(key: String, value: Float) = error("unused")
            override fun putBoolean(key: String, value: Boolean) = error("unused")
        }
    }
}
