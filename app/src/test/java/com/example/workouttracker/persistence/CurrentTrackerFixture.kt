package com.example.workouttracker.persistence

import com.example.workouttracker.persistence.rows.MemoryRowStore

/**
 * The `workout_tracker` preferences with the rows beside them, as one installation has both.
 * [writable] refuses writes to either, and [commits] counts the commits to both.
 */
internal class CurrentTrackerStore(initial: Map<String, String> = emptyMap()) : TrackerStringStore {
    val values = initial.toMutableMap()
    var writable = true
    /** Whether a removal reaches the disk; like SharedPreferences, the keys leave memory either way. */
    var removable = true
    private var preferenceCommits = 0
    val rows = MemoryRowStore(canCommit = { writable })
    val commits: Int get() = preferenceCommits + rows.commits.size

    /** Everything this installation stores, the preferences and every row, for checking that nothing changed. */
    val everything: Map<String, String> get() = values + rows.rows().associate { "row:${it.kind}/${it.id}" to "${it.position}:${it.body}" }
    override fun keys(): Set<String> = values.keys.toSet()
    override fun read(key: String): String? = values[key]
    override fun write(key: String, value: String): Boolean = writeAll(mapOf(key to value))
    override fun writeAll(values: Map<String, String>): Boolean {
        if (!writable) return false
        this.values.putAll(values)
        preferenceCommits++
        return true
    }
    override fun remove(keys: Set<String>): Boolean {
        if (!writable) return false
        values -= keys
        preferenceCommits++
        return removable
    }
}
