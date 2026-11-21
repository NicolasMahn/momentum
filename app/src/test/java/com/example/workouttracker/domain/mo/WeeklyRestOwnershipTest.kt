package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.missed

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.freeDays

import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.reward.Earn
import com.example.workouttracker.domain.reward.RewardRules
import com.example.workouttracker.domain.reward.RewardTarget
import com.example.workouttracker.domain.reward.weeklyTrackerReward
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
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
