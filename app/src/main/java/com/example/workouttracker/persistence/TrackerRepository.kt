package com.example.workouttracker.persistence

import com.example.workouttracker.domain.AppState

sealed interface TrackerUpdateResult {
    data class Updated(val state: AppState) : TrackerUpdateResult

    data object UnreadableState : TrackerUpdateResult

    data object SaveFailed : TrackerUpdateResult
}

interface TrackerRepository {
    fun load(): AppState

    /**
     * The records, or null where stored records cannot be read, so a caller can tell a view that
     * is gone from records it cannot see. [load] answers an empty state for both.
     */
    fun loadIfReadable(): AppState? = load()

    fun save(state: AppState): Boolean

    /**
     * Saves only what changed from [base], the state [state] was made from, so a change stored
     * since [base] was read is kept. A repository that cannot tell writes [state] whole.
     */
    fun save(state: AppState, base: AppState): Boolean = save(state)

    fun update(transform: (AppState) -> AppState): TrackerUpdateResult
}
