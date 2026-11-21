package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.TrackerRef
import java.time.LocalDate

internal fun MoLedger.trackerFreeDays(): Map<TrackerRef, Set<LocalDate>> = trackers.associateWith { ref ->
    history(ref).filter { (day, status) -> day < today && status == MoStatus.Free }.keys
}

/** Recorded progress against the task's explicit weekly target, never protection. */
data class MoWeekProgress(val done: Int, val expected: Int)

internal fun MoLedger.trackerWeekProgress(): Map<TrackerRef, MoWeekProgress> {
    val monday = today.monday()
    return trackers.associateWith { ref ->
        MoWeekProgress(history(ref).count { (day, status) -> day >= monday && status == MoStatus.Kept }, observations.getValue(ref).expectedInWeek)
    }
}
