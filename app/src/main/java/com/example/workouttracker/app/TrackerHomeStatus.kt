package com.example.workouttracker.app

import com.example.workouttracker.domain.DayProgress
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.mo.restingTrackersToday
import com.example.workouttracker.feature.tracker.TrackerHomeStatus

internal fun trackerHomeStatus(progress: DayProgress?): TrackerHomeStatus = when {
    progress == null -> TrackerHomeStatus.None
    progress.isComplete -> if (progress.assumed) TrackerHomeStatus.Kept else TrackerHomeStatus.Done
    else -> TrackerHomeStatus.Open(progress.done, progress.parts)
}

/**
 * Each task's standing today as its Home row shows it. A task a free day covers rests when
 * nothing is asked of it, as Activity on a rest day, and is open and optional while Mo still asks,
 * as a planned workout. A task with a token set aside for today is covered. [started] are the tasks with a first record: before it nothing is asked of
 * one yet.
 */
internal fun MoMood.homeStatuses(started: Set<TrackerRef>): Map<TrackerRef, TrackerHomeStatus> = trackerProgress.mapValues { (ref, progress) ->
    val status = trackerHomeStatus(progress)
    when {
        status !is TrackerHomeStatus.Open || status.done > 0 || ref !in freeTrackersToday || ref in missingTrackersToday -> status
        ref in restingTrackersToday -> TrackerHomeStatus.Rest
        else -> status.copy(optional = true)
    }
} + trackerWeeklyRemainders.filterKeys { it !in trackerProgress && it in started }.mapValues { (_, remainder) ->
    // A week's amount judges no single day, so its row stands for the week: kept once the week's
    // goal is reached, open until then.
    if (remainder == 0) TrackerHomeStatus.Kept else TrackerHomeStatus.Open(0, 1)
    // A token set aside settles the task whatever of it is recorded, so it stands last.
} + chosenTrackersToday.associateWith { TrackerHomeStatus.Covered }
