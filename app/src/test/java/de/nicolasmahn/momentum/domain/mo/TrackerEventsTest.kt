package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.mood

import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.WholeDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerEventsTest {
    private val counter = TrackerRef(TrackerTypeId("counter"), "water")

    @Test fun anUnlistedKindCanEarnATokenEventWhileTodayIsStillMissing() {
        val mood = mood().copy(streak = streak().copy(earnedTrackersYesterday = listOf(counter)))
        assertEquals(listOf(MoEvent.TokenEarned(counter)), moEvents(mood))
    }

    @Test fun anUnlistedMissingOrFreeTrackerPreventsAPrematureMilestone() {
        val waiting = mood().copy(streak = streak(days = 50))
        assertTrue(moEvents(waiting).isEmpty())
        assertTrue(moEvents(waiting.copy(missingTrackersToday = emptyList(), freeTrackersToday = listOf(counter))).isEmpty())
        val complete = waiting.copy(missingTrackersToday = emptyList(), keptTrackersToday = listOf(counter))
        assertEquals(listOf(MoEvent.StreakMilestone(50)), moEvents(complete))
    }

    @Test fun aRestDayDoesNotHoldAMilestoneBack() {
        val rest = TrackerRef(TrackerTypeId("counter"), "steps")
        val kept = mood().copy(dueToday = 2, missingTrackersToday = emptyList(), keptTrackersToday = listOf(counter), freeTrackersToday = listOf(rest), streak = streak(days = 50))
        // A free day Mo still asks for is open, so the day is not whole yet.
        assertTrue(moEvents(kept.copy(trackerOpenParts = mapOf(rest to listOf(WholeDay)))).isEmpty())
        val resting = kept.copy(trackerOpenParts = mapOf(rest to listOf(WholeDay)), trackerMissedParts = mapOf(rest to listOf(WholeDay)))
        assertEquals(listOf(rest), resting.restingTrackersToday)
        assertEquals(listOf(MoEvent.StreakMilestone(50)), moEvents(resting))
        // Resting alone is no reason for a reward.
        assertTrue(moEvents(resting.copy(dueToday = 1, keptTrackersToday = emptyList())).isEmpty())
    }

    @Test fun aReservationCannotMakeAnUnrecordedDayComplete() {
        val reserved = mood().copy(
            missingTrackersToday = emptyList(), selectedTrackerChoices = setOf(counter),
            streak = streak(days = 30),
        )
        assertTrue(moEvents(reserved).isEmpty())
    }

    @Test fun aWeeklyGoalEventRequiresExplicitPolicyAndARecordToday() {
        val recorded = mood().copy(
            missingTrackersToday = emptyList(), keptTrackersToday = listOf(counter),
            trackerWeekProgress = mapOf(counter to MoWeekProgress(5, 5)),
        )
        assertTrue(moEvents(recorded).isEmpty())
        val eligible = recorded.copy(weeklyGoalTrackers = setOf(counter))
        assertEquals(listOf(MoEvent.WeekGoalReached(counter)), moEvents(eligible))
        assertFalse(MoEvent.WeekGoalReached(counter) in moEvents(eligible.copy(keptTrackersToday = emptyList())))
        assertFalse(MoEvent.WeekGoalReached(counter) in moEvents(eligible.copy(trackerWeekProgress = mapOf(counter to MoWeekProgress(0, 0)))))
    }

    @Test fun aFreezeForAnUnlistedTrackerCanLeadToAComeback() {
        val complete = mood().copy(
            missingTrackersToday = emptyList(), keptTrackersToday = listOf(counter),
            streak = streak(days = 1).copy(
                week = listOf(MoDayMark.Perfect) + List(6) { MoDayMark.Upcoming },
                frozenTrackersYesterday = listOf(counter),
            ),
        )
        assertEquals(listOf(MoEvent.Comeback), moEvents(complete))
    }

    private fun mood() = MoMood(
        state = MoState.Steady,
        dueToday = 1, daysCold = 0, streak = streak(),
        missingTrackersToday = listOf(counter),
    )

    private fun streak(days: Int = 0) = MoStreak(
        days = days, freezes = 0, freezeCapacity = 3,
        week = listOf(MoDayMark.Open) + List(6) { MoDayMark.Upcoming },
        weekCanEarnFreeze = false,
    )
}
