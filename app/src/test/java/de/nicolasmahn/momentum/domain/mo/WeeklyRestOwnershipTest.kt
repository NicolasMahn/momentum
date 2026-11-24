package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.missed

import de.nicolasmahn.momentum.domain.compatibility.history

import de.nicolasmahn.momentum.domain.compatibility.mood

import de.nicolasmahn.momentum.domain.compatibility.freeDays

import de.nicolasmahn.momentum.domain.ActivityTrackerRef
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.reward.Earn
import de.nicolasmahn.momentum.domain.reward.RewardRules
import de.nicolasmahn.momentum.domain.reward.RewardTarget
import de.nicolasmahn.momentum.domain.reward.weeklyTrackerReward
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WeeklyRestOwnershipTest {
    @Test fun twoWeeklyParticipantsKeepTheirOwnRestDaysAndTokenCalendars() {
        val monday = LocalDate.parse("2026-10-05")
        val tuesday = monday.plusDays(1)
        val today = monday.plusDays(2)
        val rested = TrackerRef(TrackerTypeId("counter"), "with-rest")
        val missed = TrackerRef(TrackerTypeId("counter"), "without-rest")
        fun participant(ref: TrackerRef, freeDays: Int) = weeklyTrackerReward(
            ref, today, { RewardRules(3, Earn.KeptDaysInWeek(2)) },
            RewardTarget.WeekTimes({ it == monday }, { freeDays }), monday, Dated.always(true),
            takesPartOn = { true }, bankShownOn = { true },
        )
        val mood = MoEvaluation(
            AppState(), FeatureVisibilitySettings(), today,
            additionalRewards = listOf(participant(rested, 1), participant(missed, 0)),
        ).moodWithTrackerChoices(emptySet())
        val row = mood.streak.history.single { it.date == tuesday }

        assertEquals(setOf(rested), row.restedTrackers)
        assertEquals(setOf(missed), row.coveredTrackers)
        assertFalse(ActivityTrackerRef in row.restedTrackers)
        assertEquals(MoWeekDay.Rested, mood.streak.standings.getValue(rested).nextToken!!.week[1])
        assertEquals(MoWeekDay.Covered, mood.streak.standings.getValue(missed).nextToken!!.week[1])
        assertEquals(mapOf(rested to 0, missed to 0), mood.streak.freeDaysLeftByTracker)
    }
}
