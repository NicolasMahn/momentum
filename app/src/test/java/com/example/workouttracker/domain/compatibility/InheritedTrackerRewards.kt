package com.example.workouttracker.domain.compatibility

import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.include
import com.example.workouttracker.domain.trackerRef
import java.time.LocalDate

/** Stable aliases for historical test reports; production never selects policy by these identities. */
internal val InheritedNeedsByTracker: Map<TrackerRef, MoNeed> = MoNeed.entries.associateBy { it.view.trackerRef }
internal val InheritedNextTokenOrder = listOf(MoNeed.Care, MoNeed.Supplements, MoNeed.Eating, MoNeed.Weight, MoNeed.Activity, MoNeed.Reading)

internal fun inheritedWholeWeek(monday: LocalDate, began: LocalDate?, periods: List<RewardPeriod>?): Boolean =
    began != null && began <= monday && (0L until 7L).all { periods.include(monday.plusDays(it)) }
