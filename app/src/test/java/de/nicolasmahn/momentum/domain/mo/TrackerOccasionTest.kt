package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.mood

import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerOccasionTest {
    private val ref = TrackerRef(TrackerTypeId("counter"), "water")
    private val noon = LocalDateTime.parse("2026-10-14T12:00:00")
    private val bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null)
    private val waited = TrackerAsked(waited = mapOf(ref to listOf(90)))
    private val late = waited.copy(overdueBy = mapOf(ref to 90))

    @Test fun anUnknownKindCanBeOverdueWithoutAnInheritedAliasOrMotif() {
        val mood = mood().copy(missingTrackersToday = listOf(ref))

        assertEquals(MoFeeling(MoOccasion.Angry, MoIntensity.Mild), moFeeling(mood, late, noon, bedtime))
        assertEquals(
            MoIntensity.Strong,
            moFeeling(mood.copy(trackerNeglect = mapOf(ref to MoNeglect.Dire)), late, noon, bedtime).intensity,
        )
    }

    @Test fun unknownKindsUseTheirConfiguredContentAndNeglect() {
        val mood = mood().copy(
            missingTrackersToday = listOf(ref),
            trackerContent = mapOf(ref to MoContentKey(MoViewKind.Checklist, MoMotif.Book)),
            trackerNeglect = mapOf(ref to MoNeglect.Dire),
        )
        val feelings = (0L..100L).map { moFeeling(mood, waited, noon.plusDays(it), bedtime) }

        assertTrue(feelings.any { it == MoFeeling(MoOccasion.Dumb, MoIntensity.Strong) })
    }

    @Test fun anUnknownMissingTrackerCanPutTheStreakAtRisk() {
        val mood = mood().copy(missingTrackersToday = listOf(ref))

        assertEquals(MoOccasion.Scared, moOccasion(mood, waited, noon.withHour(22), bedtime))
        assertFalse(moOccasion(mood, TrackerAsked(), noon.withHour(22), bedtime) == MoOccasion.Scared)
        assertFalse(moOccasion(mood.copy(streak = mood.streak.copy(freezes = 1)), waited, noon.withHour(22), bedtime) == MoOccasion.Scared)
    }

    @Test fun anUnknownFrozenTrackerThawsOnAnActualRecord() {
        val mood = mood().let { it.copy(streak = it.streak.copy(frozenTrackersYesterday = listOf(ref))) }

        assertEquals(MoOccasion.Frozen, moOccasion(mood, late, noon, bedtime))
        assertFalse(moOccasion(mood.copy(latestTrackerToday = ref), late, noon, bedtime) == MoOccasion.Frozen)
    }

    @Test fun anUnknownCompletedTrackerBringsJoyAndThrivingRaisesItsIntensity() {
        val mood = mood().copy(keptTrackersToday = listOf(ref), latestTrackerToday = ref, thrivingTrackers = setOf(ref))
        val feelings = (0L..100L).map { moFeeling(mood, TrackerAsked(), noon.plusDays(it), bedtime) }

        assertTrue(feelings.any { it == MoFeeling(MoOccasion.Happy, MoIntensity.Medium) })
        assertTrue(feelings.any { it == MoFeeling(MoOccasion.Proud, MoIntensity.Medium) })
        assertEquals(listOf(ref), mood.keptTrackersToday)
    }

    private fun mood() = MoMood(
        state = MoState.Steady,
        dueToday = 1, daysCold = 0,
        streak = MoStreak(3, 0, 3, List(7) { MoDayMark.Open }, true),
        trackerOrder = listOf(ref), trackerContent = mapOf(ref to MoContentKey(MoViewKind.Checklist)),
    )
}
