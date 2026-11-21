package com.example.workouttracker.feature.substances

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.substance.OwnSubstance
import com.example.workouttracker.domain.substance.Substance
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceKind
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.substance.SubstanceSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.example.workouttracker.domain.substance.SubstanceNoneDay

@RunWith(RobolectricTestRunner::class)
class SubstanceModelsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    /** A tile as the user reads it. */
    private data class Shown(val count: Int?, val word: String?, val unit: String, val description: String)

    private fun SubstanceStat.shown() = Shown(
        count = count,
        word = word?.resolve(resources),
        unit = unit.resolve(resources),
        description = description.resolve(resources),
    )

    private fun SubstanceHistoryWindow.statusOf(substance: Substance) = statuses.single { it.substance == substance }

    @Test
    fun historyUsesFourWholeWeeksAndPreservesLegacyDuplicateEntries() {
        val logs = listOf(
            log("c1", SubstanceKind.CANNABIS, "2026-09-16", 300L),
            log("c2", SubstanceKind.CANNABIS, "2026-09-16", 200L),
            log("a1", SubstanceKind.ALCOHOL, "2026-09-16", 100L),
            log("old", SubstanceKind.TOBACCO, "2026-08-23", 400L),
        )

        // 2026-09-16 is a Wednesday, so the last row ends with four days still to come.
        val window = substanceHistoryWindow(logs, "2026-09-16", SubstanceSettings.Legacy)
        val today = window.days.single { it.dateKey == "2026-09-16" }

        assertEquals(28, window.days.size)
        assertEquals("2026-08-24", window.days.first().dateKey)
        assertEquals("2026-09-20", window.days.last().dateKey)
        assertEquals(listOf("c1", "c2", "a1"), today.logs.map(SubstanceLog::id))
        assertEquals(
            setOf(SubstanceKind.CANNABIS.id, SubstanceKind.ALCOHOL.id),
            today.substanceIds,
        )
        assertEquals(listOf(today), window.days.filter { it.logs.isNotEmpty() })
    }

    @Test
    fun aLogDatedAheadStaysOutOfTheCalendarAndOutOfTheStreak() {
        val window = substanceHistoryWindow(
            listOf(
                log("real", SubstanceKind.CANNABIS, "2026-09-10", 100L),
                log("ahead", SubstanceKind.ALCOHOL, "2026-09-18", 200L),
            ),
            "2026-09-16",
            SubstanceSettings.Legacy,
        )

        assertEquals(listOf("2026-09-10"), window.days.filter { it.logs.isNotEmpty() }.map { it.dateKey })
        assertEquals(5L, window.statusOf(SubstanceKind.CANNABIS).daysWithout)
        assertEquals(null, window.statusOf(SubstanceKind.ALCOHOL).daysWithout)
    }

    /** The calendar lists a day's substances once each, in the catalogue's order, tracked or not. */
    @Test
    fun calendarPartitionsUseEachSubstanceOnceInCatalogueOrderTrackedOrNot() {
        val day = SubstanceHistoryDay(
            dateKey = "2026-09-17",
            logs = listOf(
                log("t1", SubstanceKind.TOBACCO, "2026-09-17", 400L),
                log("k1", SubstanceKind.COFFEE, "2026-09-17", 350L),
                log("a1", SubstanceKind.ALCOHOL, "2026-09-17", 300L),
                log("c1", SubstanceKind.CANNABIS, "2026-09-17", 200L),
                log("a2", SubstanceKind.ALCOHOL, "2026-09-17", 100L),
            ),
        )
        val window = substanceHistoryWindow(day.logs, "2026-09-17", SubstanceSettings.Legacy)

        assertEquals(
            listOf(SubstanceKind.ALCOHOL, SubstanceKind.TOBACCO, SubstanceKind.CANNABIS, SubstanceKind.COFFEE),
            window.substancesOn(day),
        )
        assertEquals(listOf(SubstanceKind.ALCOHOL, SubstanceKind.TOBACCO, SubstanceKind.CANNABIS), window.statuses.map { it.substance })
    }

    @Test
    fun statusesUseLatestDateAcrossTheFullHistoryAndKeepNoHistoryHonest() {
        val logs = listOf(
            log("c1", SubstanceKind.CANNABIS, "2026-07-01", 100L),
            log("c2", SubstanceKind.CANNABIS, "2026-09-16", 200L),
            log("a1", SubstanceKind.ALCOHOL, "2026-07-01", 300L),
        )

        val window = substanceHistoryWindow(logs, "2026-09-16", SubstanceSettings.Legacy)

        assertEquals(
            SubstanceStatus(SubstanceKind.CANNABIS, "2026-09-16", 0, true),
            window.statusOf(SubstanceKind.CANNABIS),
        )
        assertEquals(
            SubstanceStatus(SubstanceKind.ALCOHOL, "2026-07-01", 76, false),
            window.statusOf(SubstanceKind.ALCOHOL),
        )
        assertEquals(
            SubstanceStatus(SubstanceKind.TOBACCO, null, null, false),
            window.statusOf(SubstanceKind.TOBACCO),
        )
        assertEquals("Logged today", window.statusOf(SubstanceKind.CANNABIS).statusText().resolve(resources))
        assertEquals("76 days without", window.statusOf(SubstanceKind.ALCOHOL).statusText().resolve(resources))
        assertEquals("No consumption recorded", window.statusOf(SubstanceKind.TOBACCO).statusText().resolve(resources))
    }

    /** Only a tracked substance gets a stat, the person's own after the catalogue; every record still shows. */
    @Test
    fun theStatsFollowTheTrackedList() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val settings = SubstanceSettings(tracked = setOf(own.id, SubstanceKind.COFFEE.id), own = listOf(own), stayingAway = Dated.always(setOf(own.id, SubstanceKind.COFFEE.id)))
        val logs = listOf(
            log("c1", SubstanceKind.CANNABIS, "2026-09-16", 100L),
            SubstanceLog("k1", own.id, "2026-09-10", 200L, 1.5, SubstanceAmountUnit.GRAMS),
        )

        val window = substanceHistoryWindow(logs, "2026-09-16", settings)

        assertEquals(listOf(SubstanceKind.COFFEE, own), window.statuses.map { it.substance })
        assertEquals(
            Shown(null, null, "nothing recorded", "Coffee: No consumption recorded"),
            window.statusOf(SubstanceKind.COFFEE).stat().shown(),
        )
        assertEquals(
            Shown(5, null, "days without", "Kratom: 5 days without"),
            window.statusOf(own).stat().shown(),
        )
        assertEquals(listOf(SubstanceKind.CANNABIS), window.substancesOn(window.days.single { it.dateKey == "2026-09-16" }))
        assertEquals(emptyList<SubstanceStatus>(), substanceHistoryWindow(logs, "2026-09-16", SubstanceSettings()).statuses)
    }

    @Test
    fun statusRecomputesFromEditedAndDeletedHistory() {
        val older = log("c1", SubstanceKind.CANNABIS, "2026-09-10", 100L)
        val latest = log("c2", SubstanceKind.CANNABIS, "2026-09-14", 200L)
        val cannabisOnly = SubstanceSettings(tracked = setOf(SubstanceKind.CANNABIS.id), stayingAway = Dated.always(setOf(SubstanceKind.CANNABIS.id)))

        val original = substanceHistoryWindow(listOf(older, latest), "2026-09-16", cannabisOnly)
        val edited = substanceHistoryWindow(
            listOf(older, latest.copy(dateKey = "2026-09-15")),
            "2026-09-16",
            cannabisOnly,
        )
        val afterDelete = substanceHistoryWindow(listOf(older), "2026-09-16", cannabisOnly)

        assertEquals(1L, original.statuses.single().daysWithout)
        assertEquals("0 days without", edited.statuses.single().statusText().resolve(resources))
        assertEquals(5L, afterDelete.statuses.single().daysWithout)
    }

    @Test
    fun daysWithoutCountsOnlyCompleteInterveningLocalDaysAcrossMonthAndYearBoundaries() {
        val cannabisOnly = SubstanceSettings(tracked = setOf(SubstanceKind.CANNABIS.id), stayingAway = Dated.always(setOf(SubstanceKind.CANNABIS.id)))
        val monthBoundary = substanceHistoryWindow(
            listOf(log("month", SubstanceKind.CANNABIS, "2026-08-31", 100L)),
            "2026-09-01",
            cannabisOnly,
        ).statuses.single()
        val yearBoundary = substanceHistoryWindow(
            listOf(log("year", SubstanceKind.CANNABIS, "2025-12-31", 100L)),
            "2026-01-02",
            cannabisOnly,
        ).statuses.single()

        assertEquals(0L, monthBoundary.daysWithout)
        assertEquals("0 days without", monthBoundary.statusText().resolve(resources))
        assertEquals(1L, yearBoundary.daysWithout)
        assertEquals("1 day without", yearBoundary.statusText().resolve(resources))
    }

    @Test
    fun statTilesShowTheStreakAndKeepTheSentenceTalkBackReads() {
        val logs = listOf(
            log("c1", SubstanceKind.CANNABIS, "2026-09-16", 100L),
            log("a1", SubstanceKind.ALCOHOL, "2026-07-01", 200L),
        )

        val window = substanceHistoryWindow(logs, "2026-09-16", SubstanceSettings.Legacy)

        assertEquals(
            Shown(null, "today", "last logged", "Cannabis: Logged today"),
            window.statusOf(SubstanceKind.CANNABIS).stat().shown(),
        )
        assertEquals(
            Shown(76, null, "days without", "Alcohol: 76 days without"),
            window.statusOf(SubstanceKind.ALCOHOL).stat().shown(),
        )
        assertEquals(
            Shown(null, null, "nothing recorded", "Tobacco: No consumption recorded"),
            window.statusOf(SubstanceKind.TOBACCO).stat().shown(),
        )
    }

    @Test
    fun statTileCountsWholeDaysWithoutARecordAndAgreesInNumber() {
        fun statFor(latestDateKey: String) = substanceHistoryWindow(
            listOf(log("c1", SubstanceKind.CANNABIS, latestDateKey, 100L)),
            "2026-09-16",
            SubstanceSettings.Legacy,
        ).statusOf(SubstanceKind.CANNABIS).stat().shown()

        // Today is not over yet, so a record from yesterday is still zero whole days without.
        assertEquals(
            Shown(0, null, "days without", "Cannabis: 0 days without"),
            statFor("2026-09-15"),
        )
        assertEquals(
            Shown(1, null, "day without", "Cannabis: 1 day without"),
            statFor("2026-09-14"),
        )
        assertEquals(
            Shown(2, null, "days without", "Cannabis: 2 days without"),
            statFor("2026-09-13"),
        )
    }

    @Test
    fun statTileNeverInventsANumberForARecordDatedAfterToday() {
        val stat = substanceHistoryWindow(
            listOf(log("c1", SubstanceKind.CANNABIS, "2026-09-20", 100L)),
            "2026-09-16",
            SubstanceSettings.Legacy,
        ).statusOf(SubstanceKind.CANNABIS).stat().shown()

        assertEquals(
            Shown(null, null, "dated ahead", "Cannabis: Latest record on Sep 20, 2026"),
            stat,
        )
    }

    /** A just-tracked substance says when it was last logged and counts nothing; stayed away from, the same records count days without. */
    @Test
    fun aJustTrackedSubstanceSaysWhenItWasLastLoggedAndNeverCounts() {
        val justTracking = SubstanceSettings(tracked = setOf(SubstanceKind.CANNABIS.id))
        fun statFor(latest: String) = substanceHistoryWindow(listOf(log("c1", SubstanceKind.CANNABIS, latest, 100L)), "2026-09-16", justTracking)
            .statusOf(SubstanceKind.CANNABIS).stat().shown()

        assertEquals(Shown(null, "today", "last logged", "Cannabis: Logged today"), statFor("2026-09-16"))
        assertEquals(Shown(null, "yesterday", "last logged", "Cannabis: Last logged yesterday"), statFor("2026-09-15"))
        assertEquals(Shown(2, null, "days ago", "Cannabis: Last logged 2 days ago"), statFor("2026-09-14"))
        assertEquals(
            Shown(null, null, "nothing recorded", "Cannabis: No consumption recorded"),
            substanceHistoryWindow(emptyList(), "2026-09-16", justTracking).statusOf(SubstanceKind.CANNABIS).stat().shown(),
        )
        assertEquals(
            Shown(1, null, "day without", "Cannabis: 1 day without"),
            substanceHistoryWindow(listOf(log("c1", SubstanceKind.CANNABIS, "2026-09-14", 100L)), "2026-09-16", SubstanceSettings.Legacy).statusOf(SubstanceKind.CANNABIS).stat().shown(),
        )
    }

    /** A day that said "none" shows as logged in the calendar and changes no stat; one dated ahead only holds the grid's shape. */
    @Test
    fun aDayThatSaidNoneShowsInTheCalendarAndChangesNoStat() {
        val window = substanceHistoryWindow(
            listOf(log("c1", SubstanceKind.CANNABIS, "2026-09-14", 100L)),
            "2026-09-16",
            SubstanceSettings.Legacy,
            listOf(SubstanceNoneDay("2026-09-15", 200L), SubstanceNoneDay("2026-09-20", 300L)),
        )

        assertEquals(true, window.days.single { it.dateKey == "2026-09-15" }.none)
        assertEquals(false, window.days.single { it.dateKey == "2026-09-14" }.none)
        assertEquals(false, window.days.single { it.dateKey == "2026-09-20" }.none)
        assertEquals(1L, window.statusOf(SubstanceKind.CANNABIS).daysWithout)
    }

    private fun log(
        id: String,
        kind: SubstanceKind,
        dateKey: String,
        timestamp: Long,
        amount: Double? = null,
        unit: SubstanceAmountUnit? = null,
    ) = SubstanceLog(id, kind.id, dateKey, timestamp, amount, unit)
}
