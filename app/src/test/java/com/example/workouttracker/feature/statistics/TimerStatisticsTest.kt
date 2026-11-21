package com.example.workouttracker.feature.statistics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.testMadeTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A timer is judged as Mo and the rewards judge it: a week's recorded minutes against what the
 * week asks. Today is Wednesday 2026-09-16, so the four weeks are three finished ones from Monday
 * 2026-08-24 and this one.
 */
@RunWith(RobolectricTestRunner::class)
class TimerStatisticsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val timer = testMadeTime(goal = 60)

    private fun UiText.text(): String = resolve(resources)

    /** The case that showed it: two sittings a week reach the hour, which by days would read as 33%. */
    @Test
    fun `seventy minutes on two days of every week reads every week at its whole goal`() {
        val mondays = listOf("2026-08-24", "2026-08-31", "2026-09-07", "2026-09-14").map(LocalDate::parse)
        val row = rowOf(mondays.flatMap { listOf(log(it, 70), log(it.plusDays(1), 70)) })

        assertEquals(3, row.counted)
        assertEquals(3, row.kept)
        assertEquals(listOf(1f, 1f, 1f, 1f), row.weeks.map { it.fill.takeIf { _ -> it.started } })
        assertEquals("100%", row.headline().text())
        assertEquals("Piano", row.caption().text())
        assertEquals(
            "Piano reached 100 percent of its weekly goal on average over 3 weeks in the last 4 weeks, the whole goal in 3. This week so far: 140 of 60 minutes.",
            row.spokenSummary(StatisticsRange.FourWeeks).text(),
        )
    }

    @Test
    fun `a week with a mark and no minutes reached nothing`() {
        val row = rowOf(listOf(
            log("2026-08-24", 60),
            TimeLog(timer.id, "2026-09-01", 0L, minutes = null),
            log("2026-09-08", 30),
            log("2026-09-10", 30),
        ))

        assertEquals(listOf(1f, 0f, 1f, 0f), row.weeks.map { it.fill.takeIf { _ -> it.started } })
        assertEquals(WeekMinutes(0, 60), row.weeks[1].minutes)
        assertEquals("67%", row.headline().text())
    }

    /** The unfinished week fills its bar but waits for Sunday before the share counts it. */
    @Test
    fun `this week fills its bar and stays out of the share until it is over`() {
        val row = rowOf(listOf(log("2026-09-07", 60), log("2026-09-14", 15)))

        assertEquals(listOf(null, null, 1f, 0.25f), row.weeks.map { it.fill.takeIf { _ -> it.started } })
        assertEquals(1, row.counted)
        assertEquals("100%", row.headline().text())
        assertEquals(WeekMinutes(15, 60), row.thisWeek?.minutes)
        assertTrue(row.weeks.last().running)
    }

    /** No week has finished yet: the honest number is how far this one is, and the caption says so. */
    @Test
    fun `a timer taken up this week shows this week's progress`() {
        val row = rowOf(listOf(log("2026-09-15", 45)))

        assertEquals(0, row.counted)
        assertEquals("75%", row.headline().text())
        assertEquals("Piano · this week", row.caption().text())
        assertEquals("Piano: 45 of 60 minutes this week so far, 75 percent.", row.spokenSummary(StatisticsRange.FourWeeks).text())
    }

    @Test
    fun `a sick Sunday leaves its week unjudged and sick days lower what a week asks`() {
        val logs = listOf(log("2026-08-24", 45), log("2026-09-01", 45), log("2026-09-08", 10))
        val sick = HolidaySchedule()
            .withSickDays(DayRange(LocalDate.parse("2026-08-25"), LocalDate.parse("2026-08-26")))
            .withSickDays(DayRange(LocalDate.parse("2026-09-13"), LocalDate.parse("2026-09-13")))

        val healthy = rowOf(logs)
        assertEquals(0, healthy.kept)
        assertEquals(3, healthy.counted)

        val row = rowOf(logs, sick)
        // Two sick days take two sevenths off the hour, rounded up: 43 minutes, which 45 reach.
        assertEquals(WeekMinutes(45, 43), row.weeks[0].minutes)
        assertNull(row.weeks[2].minutes)
        assertFalse(row.weeks[2].started)
        assertEquals(2, row.counted)
        assertEquals(1, row.kept)
        // The number is the average of the two judged bars: the whole goal, and 45 of 60 minutes.
        assertEquals("88%", row.headline().text())
    }

    private fun rowOf(logs: List<TimeLog>, daysOff: HolidaySchedule = HolidaySchedule()): FeatureConsistency =
        statisticsSummary(AppState(time = listOf(timer), timeLogs = logs), Today, StatisticsRange.FourWeeks, daysOff = daysOff)
            .consistency.single()

    private fun log(date: LocalDate, minutes: Int) = log(date.toString(), minutes)

    private fun log(dateKey: String, minutes: Int) = TimeLog(timer.id, dateKey, 0L, minutes = minutes)

    private companion object {
        const val Today = "2026-09-16"
    }
}
