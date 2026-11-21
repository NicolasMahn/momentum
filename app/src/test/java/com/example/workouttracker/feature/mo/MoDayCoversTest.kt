package com.example.workouttracker.feature.mo

import com.example.workouttracker.core.ui.DayCover
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoDayRecord
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.questions.testEatingQuestions
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.trackerRef
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MoDayCoversTest {
    private val tuesday = LocalDate.parse("2026-09-29")

    @Test
    fun eachCalendarGetsItsOwnTokensFreezesAndRestDays() {
        val weight = MoNeed.Weight.view.trackerRef
        val eating = testEatingQuestions().ref.trackerRef
        val care = MoNeed.Care.view.trackerRef
        val activity = MoNeed.Activity.view.trackerRef
        val configuredRest = TrackerRef(TrackerTypeId("measurement"), "hydration")
        val reading = MoNeed.Reading.view.trackerRef
        val streak = streakOf(
            MoDayRecord(tuesday, MoDayMark.Protected, coveredTrackers = setOf(weight, eating)),
            MoDayRecord(tuesday.plusDays(1), MoDayMark.Frozen, coveredTrackers = setOf(care)),
            MoDayRecord(tuesday.plusDays(2), MoDayMark.Perfect, restedTrackers = setOf(activity, configuredRest)),
        )
        val recordDateForJudgmentDay: (TrackerRef, LocalDate) -> LocalDate? = { ref, day ->
            if (ref == eating) day.minusDays(1) else day
        }

        val covers = moDayCovers(
            streak,
            freeDayKeys = mapOf(reading to setOf("2026-09-28")),
            recordDateForJudgmentDay = recordDateForJudgmentDay,
        )

        assertEquals(mapOf("2026-09-29" to DayCover.Token), covers[weight])
        // Eating reflects on the day before, so the token sits on the day the reflection is about.
        assertEquals(mapOf("2026-09-28" to DayCover.Token), covers[eating])
        assertEquals(mapOf("2026-09-30" to DayCover.Freeze), covers[care])
        assertEquals(mapOf("2026-10-01" to DayCover.Rest), covers[activity])
        assertEquals(mapOf("2026-10-01" to DayCover.Rest), covers[configuredRest])
        assertEquals(mapOf("2026-09-28" to DayCover.Free), covers[reading])
    }

    @Test
    fun whatWasSaidForTodayIsCoveredOnTheDayItsRecordWouldHave() {
        val eating = testEatingQuestions().ref.trackerRef
        val activity = MoNeed.Activity.view.trackerRef
        val covers = moDayCovers(
            streakOf(),
            freeDayKeys = emptyMap(),
            recordDateForJudgmentDay = { ref, day -> if (ref == eating) day.minusDays(1) else day },
            today = tuesday,
            saidToday = mapOf(activity to DayCover.Rest, eating to DayCover.Token),
        )

        assertEquals(mapOf("2026-09-29" to DayCover.Rest), covers[activity])
        assertEquals(mapOf("2026-09-28" to DayCover.Token), covers[eating])
    }

    @Test
    fun genericCounterKeepsItsIdentityAndUsesTheSuppliedDateProjection() {
        val eating = testEatingQuestions().ref.trackerRef
        val counter = TrackerRef(TrackerTypeId("counter"), "water")
        val projectedCalls = mutableListOf<Pair<TrackerRef, LocalDate>>()
        val recordDateForJudgmentDay: (TrackerRef, LocalDate) -> LocalDate? = { ref, day ->
            projectedCalls += ref to day
            if (ref == eating) day.minusDays(1) else day
        }

        val freeDays = moFreeDayKeys(
            mapOf(eating to setOf(tuesday), counter to setOf(tuesday)),
            recordDateForJudgmentDay = recordDateForJudgmentDay,
        )

        assertEquals(setOf(eating, counter), freeDays.keys)
        assertEquals(setOf("2026-09-28"), freeDays[eating])
        assertEquals(setOf("2026-09-29"), freeDays[counter])
        assertEquals(setOf(eating, counter), projectedCalls.map { it.first }.toSet())
        assertEquals(
            setOf("2026-09-29"),
            moFreeDayKeys(mapOf(counter to setOf(tuesday)))[counter],
        )

        projectedCalls.clear()
        val covers = moDayCovers(
            streak = streakOf(
                MoDayRecord(
                    tuesday,
                    MoDayMark.Protected,
                    coveredTrackers = setOf(eating, counter),
                ),
            ),
            freeDayKeys = emptyMap(),
            recordDateForJudgmentDay = recordDateForJudgmentDay,
        )

        assertEquals(setOf(eating, counter), covers.keys)
        assertEquals(mapOf("2026-09-28" to DayCover.Token), covers[eating])
        assertEquals(mapOf("2026-09-29" to DayCover.Token), covers[counter])
        assertEquals(setOf(eating, counter), projectedCalls.map { it.first }.toSet())
    }

    private fun streakOf(vararg history: MoDayRecord) = MoStreak(
        days = 0,
        freezes = 0,
        freezeCapacity = 2,
        week = List(7) { MoDayMark.Upcoming },
        weekCanEarnFreeze = false,
        history = history.toList(),
    )
}
