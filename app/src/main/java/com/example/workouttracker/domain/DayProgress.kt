package com.example.workouttracker.domain

/**
 * How far a tracker has come today, across its configured parts. A tracker completed with one
 * daily record has one part. Only a day with
 * every part done is complete, and anything in between is visibly in between. A complete day may be
 * [assumed]: kept by items that log only misses, with nothing recorded, which Home says was kept
 * rather than done.
 */
data class DayProgress(val done: Int, val parts: Int, val assumed: Boolean = false) {
    init {
        require(parts >= 1 && done in 0..parts) { "done must lie within 0..parts, was $done of $parts" }
        require(!assumed || done == parts) { "Only a complete day is kept by assumption." }
    }

    val isComplete: Boolean get() = done == parts
    val isStarted: Boolean get() = done > 0
}
