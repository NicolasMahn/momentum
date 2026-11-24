package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.history

import de.nicolasmahn.momentum.domain.compatibility.MoNeed

import de.nicolasmahn.momentum.domain.mo.compatibility.InheritedMoEvaluation
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.compatibility.InheritedNeedsByTracker
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.compatibility.inheritedWholeWeek
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.reward.Earn
import de.nicolasmahn.momentum.domain.reward.RewardOrder
import de.nicolasmahn.momentum.domain.reward.RewardRules
import de.nicolasmahn.momentum.domain.reward.RewardTarget
import de.nicolasmahn.momentum.domain.reward.RewardWalkResult
import de.nicolasmahn.momentum.domain.reward.TrackerReward
import de.nicolasmahn.momentum.domain.reward.TrackerRewardCatalogue
import de.nicolasmahn.momentum.domain.reward.dailyTrackerReward
import de.nicolasmahn.momentum.domain.reward.rewardWalk
import de.nicolasmahn.momentum.domain.reward.weeklyTrackerReward
import de.nicolasmahn.momentum.domain.settings.showingOnly
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.trackerRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrackerRewardCatalogueTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val today = monday.plusDays(24)
    private val original = MoNeed.Weight.view.trackerRef
    private val renamed = TrackerRef(TrackerTypeId("future_kind"), "another-instance")

    @Test fun dailyHistoryDependsOnItsPolicyAndFactsRatherThanItsInheritedIdentity() {
        fun entry(ref: TrackerRef) = dailyTrackerReward(
            ref, today,
            rulesOn = { RewardRules(3, Earn.EveryKeptDays(3)) },
            began = monday,
            visibility = Dated.always(true),
            openOn = { day -> if (day.dayOfMonth % 4 == 0) 2 else 0 },
            takesPartOn = { true },
            bankShownOn = { true },
            excusedOn = { it == monday.plusDays(2) },
        )

        assertEquals(evaluate(entry(original)).outcome(original), evaluate(entry(renamed)).outcome(renamed))
    }

    @Test fun leadingPartialWeekIsAnExplicitPolicyAvailableToAnyTrackerIdentity() {
        val began = monday.plusDays(3)
        fun entry(ref: TrackerRef) = weeklyTrackerReward(
            ref, today,
            rulesOn = { RewardRules(3, Earn.AmountStepsInWeek(listOf(Earn.AmountStep(60, 1)))) },
            target = RewardTarget.WeekAmount(
                requiredIn = { 60 },
                gapSizeIn = { 15 },
                amountIn = { week -> if (week == monday) 45 else 60 },
                earnsIn = { inheritedWholeWeek(it, began, null) },
            ),
            began = began,
            visibility = Dated.always(true),
            takesPartOn = { true },
            bankShownOn = { true },
        )

        val old = evaluate(entry(original))
        val current = evaluate(entry(renamed))
        assertEquals(old.outcome(original), current.outcome(renamed))
        assertEquals(MoDayMark.Protected, current.history.single { it.date == monday.plusDays(6) }.mark)
    }

    @Test fun catalogueOrderingComesFromPolicyAndKeepsOrdinaryConfigurationOrder() {
        fun entry(ref: TrackerRef, order: RewardOrder = RewardOrder()) = dailyTrackerReward(
            ref, today, { RewardRules(3, Earn.EveryKeptDays(7)) }, monday, Dated.always(true),
            openOn = { 0 }, takesPartOn = { true }, bankShownOn = { true }, order = order,
        )
        val first = TrackerRef(TrackerTypeId("counter"), "first")
        val second = TrackerRef(TrackerTypeId("counter"), "second")
        val catalogue = TrackerRewardCatalogue(listOf(entry(first), entry(renamed, RewardOrder(0, 0, 0)), entry(second)))

        assertEquals(listOf(renamed, first, second), catalogue.participants.map { it.key })
        assertEquals(listOf(renamed, first, second), catalogue.earnOrder)
    }

    @Test fun neutralStandingAndHistoryProjectTheSameInheritedResults() {
        val state = AppState(
            measurements = listOf(testWeightMeasurement()),
            measurementLogs = (0L..24L).filter { it % 4 != 0L }.map {
                MeasurementLog(WeightMeasurementId, monday.plusDays(it).toString(), it, 80.0)
            },
        )
        val result = habitStreak(state, showingOnly(MoNeed.Weight), today)

        val inherited = InheritedMoEvaluation(state, showingOnly(MoNeed.Weight), today).streak
        result.standings.forEach { (ref, standing) ->
            val need = InheritedNeedsByTracker[ref] ?: return@forEach
            assertEquals(inherited.passes[need], standing.passes)
            assertEquals(inherited.passCapacity[need], standing.passCapacity)
            assertEquals(inherited.nextToken[need], standing.nextToken)
        }
        result.history.zip(inherited.history).forEach { (day, old) ->
            assertEquals(old.covered.mapTo(LinkedHashSet()) { it.view.trackerRef }, day.coveredTrackers)
        }
        assertTrue(result.participantBanks.keys.containsAll(result.standings.keys))
    }

    @Test fun anEmptyInstallationExposesNoHistoricalPresetBanksAsCurrentTrackers() {
        val streak = habitStreak(AppState(), FeatureVisibilitySettings(), today)
        assertTrue(streak.standings.isEmpty())
        assertTrue(streak.participantBanks.isEmpty())
    }

    private fun evaluate(entry: TrackerReward): RewardWalkResult<TrackerRef> =
        rewardWalk(listOf(entry.participant), listOf(entry.ref), today, HolidaySchedule())

    private fun RewardWalkResult<TrackerRef>.outcome(ref: TrackerRef): List<Any?> = listOf(
        days, freezes, week, routineDays, freezeEarnedYesterday,
        history.map { listOf(it.date, it.mark, ref in it.covered, ref in it.rested) },
        banks[ref], freeDaysLeft[ref], nextToken[ref],
        ref in protectedYesterday, ref in frozenYesterday, ref in earnedYesterday,
        ref in openToday, ref in keptToday,
    )
}
