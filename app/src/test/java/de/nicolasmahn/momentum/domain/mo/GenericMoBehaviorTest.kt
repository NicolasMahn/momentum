package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.CheckpointPart
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reward.Earn
import de.nicolasmahn.momentum.domain.reward.RewardRules
import de.nicolasmahn.momentum.domain.reward.TrackerRewardCatalogue
import de.nicolasmahn.momentum.domain.reward.dailyTrackerReward
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared Mo behavior is specified with explicit observations, without original feature identities. */
class GenericMoBehaviorTest {
    private val monday = LocalDate.parse("2026-10-19")
    private val today = monday.plusDays(6)
    private val ref = TrackerRef(TrackerTypeId("custom-kind"), "any-id")
    private val other = TrackerRef(TrackerTypeId("another-kind"), "another-id")
    private val visibility = FeatureVisibilitySettings()

    private fun observation(
        actions: Map<LocalDate, Long>,
        freeDays: Int = 0,
        first: LocalDate? = actions.keys.minOrNull(),
    ) = TrackerObservation(
        ref, first, actions,
        ObservationTarget.Daily(actions::containsKey, freeDaysOn = { _, _ -> freeDays }),
        { true }, { true }, participatesInMood = true,
        expectedInWeek = 7 - freeDays, content = MoContentKey(MoViewKind.Checklist),
        checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(12, 0)), remind = true),
    )

    private fun ledger(observation: TrackerObservation, day: LocalDate = today, daysOff: HolidaySchedule = HolidaySchedule()) =
        MoLedger.fromCatalogue(TrackerRewardCatalogue(emptyList(), listOf(observation)), visibility, day, daysOff, DefaultRewardStart)

    private fun mood(vararg observations: TrackerObservation, day: LocalDate = today): MoMood =
        MoEvaluation(AppState(), visibility, day, additionalObservations = observations.toList()).moodWithTrackerChoices(emptySet())

    @Test fun noFactsOrExplicitNonEnrollmentLeaveMoNeutralAndWithoutCommitments() {
        val empty = observation(emptyMap())
        val notEnrolled = observation(mapOf(monday to 1L)).copy(participatesInMood = false)
        listOf(empty, notEnrolled).forEach { configured ->
            val observed = mood(configured)
            assertEquals(MoState.Steady, observed.state)
            assertEquals(0, observed.dueToday)
            assertTrue(observed.missingTrackersToday.isEmpty())
            assertTrue(observed.trackerNeglect.isEmpty())
            assertTrue(observed.thrivingTrackers.isEmpty())
        }
    }

    @Test fun freeDaysAreExplicitAllocatedToEarliestGapsAndResetEachMonday() {
        val actions = mapOf(monday to 1L, monday.plusDays(4) to 2L)
        val configured = observation(actions, freeDays = 1)
        val history = ledger(configured, monday.plusDays(8)).history(ref)
        assertEquals(MoStatus.Free, history[monday.plusDays(1)])
        assertEquals(MoStatus.Missed, history[monday.plusDays(2)])
        assertEquals(MoStatus.Free, history[monday.plusDays(7)])
        assertEquals(MoStatus.Missed, history[monday.plusDays(8)])
        assertEquals(actions, configured.recordedAt)
        assertEquals(2, history.values.count { it == MoStatus.Kept })
    }

    @Test fun backfillingARecordMovesTheFreeDayWithoutInventingAnotherCompletion() {
        val actions = mapOf(monday to 1L, monday.plusDays(3) to 2L)
        val before = observation(actions, freeDays = 1)
        val after = observation(actions + (monday.plusDays(1) to 3L), freeDays = 1)
        assertEquals(setOf(monday.plusDays(1)), ledger(before).trackerFreeDays().getValue(ref))
        assertEquals(setOf(monday.plusDays(2)), ledger(after).trackerFreeDays().getValue(ref))
        assertEquals(2, ledger(before).history(ref).values.count { it == MoStatus.Kept })
        assertEquals(3, ledger(after).history(ref).values.count { it == MoStatus.Kept })
    }

    @Test fun historyStartsWithItsOwnFactsAndSkipsHiddenSickAndExplicitlyExcusedDays() {
        val first = monday.plusDays(1)
        val configured = observation(mapOf(first to 1L), first = first).copy(
            visibleOn = { it != monday.plusDays(2) },
            countsOn = { it != monday.plusDays(2) },
            target = ObservationTarget.Daily({ it == first }, excusedOn = { it == monday.plusDays(4) }),
        )
        val sick = HolidaySchedule().withSickDays(DayRange(monday.plusDays(3), monday.plusDays(3)))
        val history = ledger(configured, daysOff = sick).history(ref)
        assertEquals(setOf(first, monday.plusDays(5), today), history.keys)
        assertEquals(MoStatus.Kept, history[first])
        assertEquals(MoStatus.Missed, history[today])
    }

    @Test fun latestActionUsesFactualTimestampAndIgnoresHiddenAndOtherDayRecords() {
        val first = observation(mapOf(monday to 1L, today to 10L))
        val second = observation(mapOf(monday to 1L, today to 20L)).copy(ref = other)
        assertEquals(other, mood(first, second).latestTrackerToday)
        assertEquals(ref, mood(first, second.copy(visibleOn = { false }, countsOn = { false })).latestTrackerToday)
        assertEquals(ref, mood(first, observation(mapOf(monday to 100L)).copy(ref = other)).latestTrackerToday)
    }

    @Test fun aPartialFactualActionWarmsColdMoWithoutCompletingTheTask() {
        val morning = CheckpointPart("morning")
        val evening = CheckpointPart("evening")
        val lost = observation(mapOf(monday.minusWeeks(2) to 1L)).copy(
            partialActionRelief = true,
            partsOn = { listOf(morning, evening) },
            partKeptOn = { _, _ -> false },
        )
        val acted = lost.copy(recordedAt = lost.recordedAt + (today to 2L), partKeptOn = { part, date -> date == today && part == morning })
        assertTrue(mood(acted).state > mood(lost).state)
        assertEquals(listOf(ref), mood(acted).missingTrackersToday)
        assertTrue(mood(acted).keptTrackersToday.isEmpty())
        assertEquals(listOf(evening), mood(acted).trackerOpenParts.getValue(ref))
    }

    @Test fun protectionDoesNotEraseNeglectOrCreateThriving() {
        val configured = observation(mapOf(monday.minusDays(1) to 1L))
        val reward = dailyTrackerReward(
            ref, today, { RewardRules(20, Earn.EveryKeptDays(7)) }, monday.minusDays(1), Dated.always(true),
            openOn = { if (it in configured.recordedAt) 0 else 1 }, takesPartOn = { true }, bankShownOn = { true },
        )
        val protected = MoEvaluation(AppState(), visibility, today, additionalRewards = listOf(reward), additionalObservations = listOf(configured)).moodWithTrackerChoices(emptySet())
        assertTrue(protected.streak.history.any { ref in it.coveredTrackers })
        assertEquals(mood(configured).trackerNeglect, protected.trackerNeglect)
        assertEquals(MoNeglect.Dire, protected.trackerNeglect[ref])
        assertFalse(ref in protected.thrivingTrackers)
        assertTrue(protected.keptTrackersToday.isEmpty())
    }

    @Test fun thrivingRequiresActualKeptDaysAndNoMissesWhileTodaysRecordRelievesNeglect() {
        val actions = (0L..5L).associate { monday.plusDays(it) to it }
        val thriving = observation(actions)
        assertTrue(ref in mood(thriving).thrivingTrackers)
        assertTrue(mood(thriving).trackerNeglect.isEmpty())
        val neglected = observation(mapOf(monday.minusDays(1) to 1L))
        val recovered = observation(neglected.recordedAt + (today to 2L))
        assertEquals(MoNeglect.Dire, mood(neglected).trackerNeglect[ref])
        assertEquals(MoNeglect.Severe, mood(recovered).trackerNeglect[ref])
    }

    @Test fun everyKindUsesItsConfiguredDeadlineAndSharedMinuteThresholdsAcrossMidnight() {
        val configured = observation(mapOf(monday to 1L)).copy(checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(23, 50)), remind = true))
        val open = mood(configured)
        val reminders = ReminderSettings()
        assertTrue(moAskedTrackers(open, reminders, LocalTime.of(0, 4, 59)).waited.isEmpty())
        assertEquals(listOf(15), moAskedTrackers(open, reminders, LocalTime.of(0, 5)).waited[ref])
        assertFalse(moIsAngry(open, reminders, LocalTime.of(0, 49, 59)))
        assertTrue(moIsAngry(open, reminders, LocalTime.of(0, 50)))
        assertEquals(listOf(ref), moOverdueTrackers(open, reminders, LocalTime.of(0, 50)))
        assertFalse(moIsAngry(open, reminders, LocalTime.of(4, 0)))
    }

    @Test fun eachOwedCheckpointHasItsOwnDeadlineAndADisabledReminderUsesTheSharedCheckIn() {
        val morning = CheckpointPart("morning")
        val evening = CheckpointPart("evening")
        val open = mood(observation(mapOf(monday to 1L))).copy(
            trackerOpenParts = mapOf(ref to listOf(morning, evening)),
            trackerCheckpoints = mapOf(ref to listOf(
                Checkpoint("morning", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = true),
                Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(18, 0)), remind = false),
            )),
        )
        assertEquals(listOf(LocalTime.of(9, 0), LocalTime.of(20, 0)), open.trackerObligations(ReminderSettings()).getValue(ref).map { it.since })
        val morningSettled = open.copy(trackerMissedParts = mapOf(ref to listOf(morning)))
        assertEquals(listOf(evening), morningSettled.trackerStillOwed(ref))
        assertFalse(moIsAngry(morningSettled, ReminderSettings(), LocalTime.of(10, 0)))
    }
}
