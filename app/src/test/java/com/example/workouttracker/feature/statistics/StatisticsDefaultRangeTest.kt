package com.example.workouttracker.feature.statistics

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.substance.SubstanceNoneDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Statistics opens on three months only once there is more than two months of anything to show. */
class StatisticsDefaultRangeTest {
    private val today = LocalDate.parse("2026-11-11")

    private fun recordOn(day: LocalDate) = AppState(substanceNoneDays = listOf(SubstanceNoneDay(day.toString(), 1L)))

    @Test fun aShortHistoryOpensOnFourWeeksAndALongerOneOnThreeMonths() {
        assertEquals(StatisticsRange.FourWeeks, defaultStatisticsRange(AppState(), today))
        assertEquals(StatisticsRange.FourWeeks, defaultStatisticsRange(recordOn(today.minusWeeks(8)), today))
        assertEquals(StatisticsRange.ThreeMonths, defaultStatisticsRange(recordOn(today.minusWeeks(8).minusDays(1)), today))
    }
}
