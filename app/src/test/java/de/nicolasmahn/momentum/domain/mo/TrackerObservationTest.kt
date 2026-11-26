package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.observation

import de.nicolasmahn.momentum.domain.compatibility.history

import de.nicolasmahn.momentum.domain.compatibility.mood

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.CheckpointPart
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.compatibility.WeightRewards
import de.nicolasmahn.momentum.domain.measurement.MeasurementBank
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reward.Earn
import de.nicolasmahn.momentum.domain.reward.RewardRules
import de.nicolasmahn.momentum.domain.reward.TrackerRewardCatalogue
import de.nicolasmahn.momentum.domain.reward.dailyTrackerReward
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TrackerObservationTest {
    private val first = LocalDate.parse("2026-10-05")
    private val today = first.plusDays(10)
    private val ref = TrackerRef(TrackerTypeId("counter"), "my-counter")
    private val other = TrackerRef(TrackerTypeId("future_kind"), "same-policy")
    private val actions = (0L..8L).filter { it != 3L }.associate { first.plusDays(it) to it }

    private fun observation(ref: TrackerRef = this.ref, enrolled: Boolean = true) = TrackerObservation(
        ref = ref,
        firstDay = first,
        recordedAt = actions,
        target = ObservationTarget.Daily(actions::containsKey),
        visibleOn = { true },
        countsOn = { true },
        participatesInMood = enrolled,
        label = "My counter",
        content = MoContentKey(MoViewKind.Measurement),
        checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(12, 0)), remind = true),
    )

    private fun ledger(observation: TrackerObservation) = MoLedger.fromCatalogue(
        TrackerRewardCatalogue(emptyList(), listOf(observation)), FeatureVisibilitySettings(), today,
        HolidaySchedule(), DefaultRewardStart,
    )

    @Test fun changingTheReferenceDoesNotChangeTheObservationPolicy() {
        val original = ledger(observation())
        val renamed = ledger(observation(other))

        assertEquals(original.history(ref), renamed.history(other))
        assertEquals(original.on(today).keptShare, renamed.on(today).keptShare)
        assertEquals(original.on(first.plusDays(3)).heldShare, renamed.on(first.plusDays(3)).heldShare)
        assertEquals(original.trackerNeglect()[ref], renamed.trackerNeglect()[other])
        assertEquals(original.trackerWeekProgress()[ref], renamed.trackerWeekProgress()[other])
    }

    @Test fun publishingFactsDoesNotEnrollATrackerInMood() {
        val neutral = MoEvaluation(AppState(), FeatureVisibilitySettings(), today).mood(emptySet())
        val published = MoEvaluation(AppState(), FeatureVisibilitySettings(), today, additionalObservations = listOf(observation(enrolled = false))).mood(emptySet())

        assertEquals(neutral.state, published.state)
        assertEquals(neutral.daysCold, published.daysCold)
        assertEquals(neutral.dueToday, published.dueToday)
        assertTrue(published.missingTrackersToday.isEmpty())
        assertEquals("My counter", published.trackerLabels[ref])
    }

    @Test fun anExplicitlyEnrolledNewKindUsesTheSameLedgerRewardsAndDeadline() {
        val observation = observation()
        val reward = dailyTrackerReward(
            ref, today, { RewardRules(3, Earn.EveryKeptDays(7)) }, first, Dated.always(true),
            openOn = { if (it in actions) 0 else 1 }, takesPartOn = { true }, bankShownOn = { true },
        )
        val mood = MoEvaluation(
            AppState(), FeatureVisibilitySettings(), today,
            additionalRewards = listOf(reward), additionalObservations = listOf(observation),
        ).mood(emptySet())

        assertEquals(listOf(ref), mood.missingTrackersToday)
        assertEquals(listOf(ref), mood.missingTrackersToday)
        assertEquals(1, mood.dueToday)
        assertTrue(mood.streak.standings.containsKey(ref))
        assertTrue(mood.streak.history.any { ref in it.coveredTrackers })
        assertEquals(listOf(ref), moOverdueTrackers(mood, ReminderSettings(), LocalTime.of(13, 0)))
        assertFalse(mood.keptTrackersToday.contains(ref))
    }

    @Test fun aNewKindCanOwnItsStoredReservationAndCurrentFactsCanInvalidateIt() {
        fun evaluated(parts: Int): MoEvaluation {
            val observation = observation().copy(
                firstDay = today, recordedAt = emptyMap(),
                target = ObservationTarget.Daily({ false }),
                partsOn = { (0 until parts).map { CheckpointPart("part_$it") } },
                partKeptOn = { _, _ -> false },
            )
            val reward = dailyTrackerReward(
                ref, today, { RewardRules(3, Earn.EveryKeptDays(7)) }, today, Dated.always(true),
                openOn = { parts }, takesPartOn = { true }, bankShownOn = { true },
            )
            return MoEvaluation(AppState(), FeatureVisibilitySettings(), today, additionalRewards = listOf(reward), additionalObservations = listOf(observation))
        }
        val reserved = evaluated(1).moodWithTrackerChoices(setOf(ref))
        assertTrue(reserved.missingTrackersToday.isEmpty())
        assertEquals(setOf(ref), reserved.chosenTrackersToday)
        assertTrue(reserved.keptTrackersToday.isEmpty())
        assertEquals(setOf(ref), reserved.selectedTrackerChoices)
        assertFalse(reserved.trackerProgress.containsKey(ref))

        val noLongerCovered = evaluated(2).moodWithTrackerChoices(setOf(ref))
        assertEquals(listOf(ref), noLongerCovered.missingTrackersToday)
        assertTrue(noLongerCovered.chosenTrackersToday.isEmpty())
    }

    @Test fun anInheritedTrackerWithoutItsFirstRecordHasNoOpenHomeBadge() {
        val configuration = testWeightMeasurement()
        val evaluation = MoEvaluation(AppState(measurements = listOf(configuration)), FeatureVisibilitySettings(), today)
        assertFalse(evaluation.todayProgress().containsKey(configuration.ref.trackerRef))
    }

    @Test fun aReservationKeepsVisiblePartialFactsWhileQuietingOnlyAnUnstartedSocket() {
        val part = CheckpointPart("first")
        val second = CheckpointPart("second")
        val observation = observation().copy(
            firstDay = today, recordedAt = mapOf(today to 1),
            target = ObservationTarget.Daily({ false }),
            partsOn = { listOf(part, second) },
            partKeptOn = { candidate, _ -> candidate == part },
            progressPolicy = TrackerProgressPolicy.ParticipatingParts,
        )
        val reward = dailyTrackerReward(
            ref, today, { RewardRules(3, Earn.EveryKeptDays(7)) }, today, Dated.always(true),
            openOn = { 1 }, takesPartOn = { true }, bankShownOn = { true },
        )
        val evaluation = MoEvaluation(AppState(), FeatureVisibilitySettings(), today, additionalRewards = listOf(reward), additionalObservations = listOf(observation))
        // A direct choice quiets the task; today's one real completed part remains factual.
        val reserved = evaluation.moodWithTrackerChoices(setOf(ref))
        assertTrue(reserved.missingTrackersToday.isEmpty())
        assertEquals(1, reserved.trackerProgress.getValue(ref).done)
        assertEquals(2, reserved.trackerProgress.getValue(ref).parts)
    }

    @Test fun endingARewardPeriodDoesNotHideAnEnrolledTrackersFactualBadge() {
        val configuration = testWeightMeasurement().copy(rewards = MeasurementBank.Week(
            WeightRewards.copy(periods = listOf(RewardPeriod(first, today.minusDays(1)))),
        ))
        val state = AppState(
            measurements = listOf(configuration),
            measurementLogs = listOf(MeasurementLog(WeightMeasurementId, today.toString(), 1, 80.0)),
        )
        val evaluation = MoEvaluation(state, FeatureVisibilitySettings(), today)

        assertTrue(evaluation.todayProgress().getValue(configuration.ref.trackerRef).isComplete)
        assertEquals(80.0, state.measurementLogs.single().value, 0.0)
    }

    @Test fun sickDaysDoNotHideAnInheritedFactualBadgeWhileItStillParticipates() {
        val configuration = testWeightMeasurement()
        val state = AppState(
            measurements = listOf(configuration),
            measurementLogs = listOf(MeasurementLog(WeightMeasurementId, today.toString(), 1, 80.0)),
        )
        val daysOff = HolidaySchedule().withSickDays(de.nicolasmahn.momentum.domain.holiday.DayRange(today, today))
        val evaluation = MoEvaluation(state, FeatureVisibilitySettings(), today, daysOff)

        assertTrue(evaluation.todayProgress().getValue(configuration.ref.trackerRef).isComplete)
        assertEquals(0, evaluation.mood(emptySet()).dueToday)
    }

    @Test(expected = IllegalArgumentException::class)
    fun twoObservationSourcesCannotOwnTheSameTracker() {
        TrackerRewardCatalogue(emptyList(), listOf(observation(), observation()))
    }
}
