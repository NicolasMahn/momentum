package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.observation

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.CheckpointPart
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.compatibility.WeightRewards
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reward.Earn
import com.example.workouttracker.domain.reward.RewardRules
import com.example.workouttracker.domain.reward.TrackerRewardCatalogue
import com.example.workouttracker.domain.reward.dailyTrackerReward
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
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
        val daysOff = HolidaySchedule().withSickDays(com.example.workouttracker.domain.holiday.DayRange(today, today))
        val evaluation = MoEvaluation(state, FeatureVisibilitySettings(), today, daysOff)

        assertTrue(evaluation.todayProgress().getValue(configuration.ref.trackerRef).isComplete)
        assertEquals(0, evaluation.mood(emptySet()).dueToday)
    }

    @Test(expected = IllegalArgumentException::class)
    fun twoObservationSourcesCannotOwnTheSameTracker() {
        TrackerRewardCatalogue(emptyList(), listOf(observation(), observation()))
    }
}
