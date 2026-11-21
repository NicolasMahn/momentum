package com.example.workouttracker.feature.mo

import com.example.workouttracker.domain.compatibility.contentKey

import com.example.workouttracker.domain.activity.DefaultActivityDaysAWeek
import com.example.workouttracker.domain.care.testCareChecklist
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.mo.MoContentKey
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoNeglect
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.mo.MoWeekProgress
import com.example.workouttracker.domain.mo.contentKey
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import com.example.workouttracker.domain.trackerRef

/** Supplements' and Care's repository seed configurations, with the motifs they were built with. */
internal val storedChecklists: Map<MoNeed, ChecklistConfiguration> =
    mapOf(MoNeed.Supplements to testSupplementsChecklist(), MoNeed.Care to testCareChecklist())

/** Fixed tracker-keyed scenarios exercise each presentation branch independently of the ledger. */
internal fun contentComparisonMoods(state: MoState, need: MoNeed, neglect: MoNeglect?): List<MoMood> {
    val ref = need.view.trackerRef
    val week = MoNeed.entries.associate { inherited ->
        inherited.view.trackerRef to MoWeekProgress(
            done = 3,
            expected = inherited.expectedDaysPerWeek ?: DefaultActivityDaysAWeek,
        )
    }
    val content = MoNeed.entries.associate { inherited ->
        inherited.view.trackerRef to inherited.contentKey(storedChecklists[inherited])
    }
    val base = MoMood(
        state = state,
        dueToday = 1,
        daysCold = 4,
        streak = MoStreak(
            days = 14,
            freezes = 1,
            freezeCapacity = 3,
            week = listOf(MoDayMark.Open) + List(6) { MoDayMark.Upcoming },
            weekCanEarnFreeze = true,
        ),
        trackerLabels = storedChecklists.mapKeys { (inherited, _) -> inherited.view.trackerRef }
            .mapValues { (_, checklist) -> checklist.label },
        trackerContent = content,
        missingTrackersToday = listOf(ref),
        trackerWeekProgress = week,
        trackerNeglect = neglect?.let { mapOf(ref to it) }.orEmpty(),
        trackerOrder = MoNeed.entries.map { it.view.trackerRef },
        weeklyGoalTrackers = MoNeed.entries.filter { it.hasWeekGoal }
            .mapTo(LinkedHashSet()) { it.view.trackerRef },
        trackerCheckpoints = storedChecklists.mapKeys { (inherited, _) -> inherited.view.trackerRef }
            .mapValues { (_, checklist) -> checklist.checkpoints },
    )
    val done = base.copy(
        missingTrackersToday = emptyList(),
        keptTrackersToday = listOf(ref),
        latestTrackerToday = ref,
    )
    val frozen = base.streak.copy(frozenTrackersYesterday = listOf(ref))
    return listOf(
        base,
        base.copy(missingTrackersToday = emptyList(), dueToday = 0),
        base.copy(missingTrackersToday = emptyList(), freeTrackersToday = listOf(ref)),
        done,
        done.copy(trackerWeeklyNudges = mapOf(MoNeed.Reading.view.trackerRef to 25)),
        base.copy(streak = frozen),
        base.copy(missingTrackersToday = emptyList(), streak = frozen),
        done.copy(thrivingTrackers = setOf(ref)),
    )
}
