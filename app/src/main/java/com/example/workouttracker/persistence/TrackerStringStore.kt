package com.example.workouttracker.persistence

import android.content.SharedPreferences
import android.content.Context
import com.example.workouttracker.persistence.backup.DurableBackupManager
import com.example.workouttracker.persistence.backup.PersonalDataLock

internal interface TrackerStringStore {
    fun keys(): Set<String>

    fun read(key: String): String?

    fun write(key: String, value: String): Boolean

    /** Writes every entry in one commit: afterwards either all of them are stored or none is. */
    fun writeAll(values: Map<String, String>): Boolean

    /** Removes [keys] in one commit. A store that cannot remove anything says false. */
    fun remove(keys: Set<String>): Boolean = false
}

internal class SharedPreferencesTrackerStringStore(
    private val preferences: SharedPreferences,
    private val backups: DurableBackupManager? = null,
) : TrackerStringStore {
    override fun keys(): Set<String> = synchronized(PersonalDataLock) {
        backups?.recoverBeforeAccess()
        preferences.all.keys.toSet()
    }

    override fun read(key: String): String? = synchronized(PersonalDataLock) {
        backups?.recoverBeforeAccess()
        preferences.getString(key, null)
    }

    override fun write(key: String, value: String): Boolean = writeAll(mapOf(key to value))

    override fun writeAll(values: Map<String, String>): Boolean =
        synchronized(PersonalDataLock) {
            backups?.recoverBeforeAccess()
            backups?.beforeChange()
            preferences.edit()
                .apply { values.forEach { (key, value) -> putString(key, value) } }
                .commit()
                .also { backups?.afterChange() }
        }

    /**
     * SharedPreferences forgets the keys in memory before it knows whether the file changed, and
     * removing a key memory no longer holds may report success without writing the file. So when a
     * removal fails, its entries are put back into memory, and a retry finds them and really writes.
     */
    override fun remove(keys: Set<String>): Boolean =
        synchronized(PersonalDataLock) {
            backups?.recoverBeforeAccess()
            backups?.beforeChange()
            val held = keys.mapNotNull { key -> preferences.getString(key, null)?.let { key to it } }.toMap()
            val removed = preferences.edit().apply { keys.forEach(::remove) }.commit()
            if (!removed && held.isNotEmpty()) preferences.edit().apply { held.forEach { (key, value) -> putString(key, value) } }.commit()
            backups?.afterChange()
            removed
        }
}

/** The values of one store as a snapshot holds them, for reading them the way the app reads its own; nothing is written. */
internal class SnapshotStringStore(private val values: Map<String, String>) : TrackerStringStore {
    override fun keys(): Set<String> = values.keys

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String): Boolean = false

    override fun writeAll(values: Map<String, String>): Boolean = false
}

internal fun personalStringStore(context: Context, name: String): TrackerStringStore {
    val backups = DurableBackupManager.get(context)
    backups.recoverBeforeAccess()
    return SharedPreferencesTrackerStringStore(context.getSharedPreferences(name, Context.MODE_PRIVATE), backups)
}
