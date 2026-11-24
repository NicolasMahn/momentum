package de.nicolasmahn.momentum.feature.mo

import de.nicolasmahn.momentum.core.ui.DayCover
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoDayRecord
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.MoStreak
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.questions.testEatingQuestions
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.trackerRef
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoDayCoversTest {
    private val tuesday = LocalDate.parse("2026-09-29")

    @Test
    fun eachCalendarGetsItsOwnTokensAndFreezesButNoRestDays() {
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
            // The ledger counts Activity's rest as a free day as well; the rest keeps it plain.
            freeDayKeys = mapOf(reading to setOf("2026-09-28"), activity to setOf("2026-10-01")),
            recordDateForJudgmentDay = recordDateForJudgmentDay,
        )

        assertEquals(mapOf("2026-09-29" to DayCover.Token), covers[weight])
        // Eating reflects on the day before, so the token sits on the day the reflection is about.
        assertEquals(mapOf("2026-09-28" to DayCover.Token), covers[eating])
        assertEquals(mapOf("2026-09-30" to DayCover.Freeze), covers[care])
        // A planned rest day is most days of a plan, so a calendar leaves it plain.
        assertEquals(emptyMap<String, DayCover>(), covers[activity].orEmpty())
        assertNull(covers[configuredRest])
        assertEquals(mapOf("2026-09-28" to DayCover.Free), covers[reading])
    }

    @Test
    fun whatWasSaidForTodayIsCoveredOnTheDayItsRecordWouldHave() {
        val eating = testEatingQuestions().ref.trackerRef
        val covers = moDayCovers(
            streakOf(),
            freeDayKeys = emptyMap(),
            recordDateForJudgmentDay = { ref, day -> if (ref == eating) day.minusDays(1) else day },
            today = tuesday,
            saidToday = mapOf(eating to DayCover.Token),
        )

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
