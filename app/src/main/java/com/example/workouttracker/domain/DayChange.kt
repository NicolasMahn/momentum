package com.example.workouttracker.domain

/**
 * What a view's screen asks to be stored for one day: something to save, or the day's record to
 * remove, a measurement's number, a questions view's answers or a time view's minutes. It holds
 * what the draft opened on, so it is decided against the records as they are stored at that moment
 * and never against the state the screen happened to be shown: whoever stores it asks [refusalIn]
 * and then [appliedTo] inside one update of the store.
 */
internal interface DayChange {
    /** Why this cannot be made to [state], or null when it can: the view must still exist, and the day's record must still be the one the draft opened on. */
    fun refusalIn(state: AppState): DayChangeRefusal?

    /** [state] with this made at [timestampMillis]: that view's day is changed and nothing else. */
    fun appliedTo(state: AppState, timestampMillis: Long): AppState
}

/** Why a day's change is not made. */
internal enum class DayChangeRefusal {
    /** The view was deleted since the screen opened. */
    Deleted,

    /** The day's record is no longer the one the draft opened on: something else stored one meanwhile, such as a notification's reply. */
    ChangedElsewhere,
}

/** What a day's change came to. */
internal sealed interface DayChangeResult {
    /** Stored: the records as they were just before and as they are now. */
    data class Stored(val before: AppState, val after: AppState) : DayChangeResult

    /** Nothing was stored, because the records are no longer what the draft opened on. */
    data class Refused(val why: DayChangeRefusal) : DayChangeResult

    /** Nothing was stored, because the store could not write or the change is one it does not hold. */
    data object Failed : DayChangeResult
}

/**
 * [change] decided against these records and, when it can be made, handed to [save] as the whole
 * state it leaves. For a host that has no store to decide against, a preview, the gallery or a
 * test: there the state on show is all there is. The app stores a change through its repository.
 */
internal fun AppState.changingDay(change: DayChange, timestampMillis: Long, save: (AppState) -> Boolean): DayChangeResult {
    change.refusalIn(this)?.let { return DayChangeResult.Refused(it) }
    val after = runCatching { change.appliedTo(this, timestampMillis) }.getOrElse { return DayChangeResult.Failed }
    return if (save(after)) DayChangeResult.Stored(before = this, after = after) else DayChangeResult.Failed
}
