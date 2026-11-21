package com.example.workouttracker.feature.statistics

import com.example.workouttracker.domain.questions.eatingEntry
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.settings.hidingTasks
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.SeededGymDayIds
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoDayRecord
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.checklist.DayRecord
import com.example.workouttracker.domain.checklist.setDayRecord
import com.example.workouttracker.domain.checklist.tickOf
import com.example.workouttracker.domain.compatibility.StandardSupplementIds
import com.example.workouttracker.domain.supplement.supplementsState
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.feature.logactivity.ActivityMark
import com.example.workouttracker.feature.measurement.words
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.compatibility.weightMeasurement
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Today is Wednesday 2026-09-16, so the four-week window runs from Monday 2026-08-24 and holds
 * three whole weeks plus Monday to Wednesday: 24 days that have happened.
 */
@RunWith(RobolectricTestRunner::class)
class StatisticsModelsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun UiText.text(): String = resolve(resources)

    @Test
    fun `a range is whole weeks ending with this week and counts only days that have happened`() {
        val state = AppState(measurementLogs = weightEveryDay(from = "2025-01-01", to = Today))

        val fourWeeks = state.consistencyOf(MoNeed.Weight, StatisticsRange.FourWeeks)
        assertEquals(listOf(7, 7, 7, 3), fourWeeks.weeks.map { it.counted })
        assertEquals(1.0, fourWeeks.rate!!, 0.0)
        // This week's bar is measured against the whole week, so three good days of seven read as three sevenths.
        assertEquals(listOf(false, false, false, true), fourWeeks.weeks.map { it.running })
        assertEquals(3f / 7, fourWeeks.weeks.last().fill, 1e-6f)

        // The row's number counts the weeks that are over.
        val threeMonths = state.consistencyOf(MoNeed.Weight, StatisticsRange.ThreeMonths)
        assertEquals(13, threeMonths.weeks.size)
        assertEquals(12 * 7, threeMonths.counted)

        val year = state.consistencyOf(MoNeed.Weight, StatisticsRange.Year)
        assertEquals(52, year.weeks.size)
        assertEquals(51 * 7, year.counted)
        assertEquals("2025-09-22", historicalSummary(state, Today, StatisticsRange.Year).startDateKey)
    }

    @Test
    fun `a feature is only measured from its first record on`() {
        val state = AppState(measurementLogs = weightOn("2026-09-10", "2026-09-12", Today))

        StatisticsRange.entries.forEach { range ->
            val reading = state.consistencyOf(MoNeed.Weight, range)
            assertEquals("$range counted", 4, reading.counted)
            assertEquals("$range logged", 2, reading.kept)
            assertEquals(
                listOf(WeekConsistency(2, 4), WeekConsistency(1, 3, fill = 1f / 7, running = true)),
                reading.weeks.takeLast(2),
            )
            assertTrue(reading.weeks.dropLast(2).all { it.counted == 0 })
        }
    }

    @Test
    fun `a habit older than the range is measured against the whole range`() {
        val state = AppState(measurementLogs = weightOn("2026-01-01"))

        val reading = state.consistencyOf(MoNeed.Weight, StatisticsRange.FourWeeks)

        // Only the weeks that are over count; this week waits for its Sunday.
        assertEquals(21, reading.counted)
        assertEquals(0.0, reading.rate!!, 0.0)
    }

    @Test
    fun `eating is logged on the day its reflection was due, as on Home`() {
        val state = AppState(
            questionEntries = listOf(eatingEntry("r1", dateKey = "2026-09-15", 0L)),
        )

        val eating = state.consistencyOf(MoNeed.Eating, StatisticsRange.FourWeeks)

        val week = eating.weeks.last()
        assertEquals(1 to 1, week.kept to week.counted)
        assertTrue(week.running)
        // The first week is not over, so the number is how far it is.
        assertEquals(0, eating.counted)
        assertEquals(week.fill.toDouble(), eating.rate!!, 1e-6)
        assertEquals("Eating · this week", eating.caption().text())
    }

    @Test
    fun `an emptied supplement record neither counts nor starts the habit`() {
        val creatine = setOf(tickOf("midday", StandardSupplementIds.CREATINE))
        val checklist = testSupplementsChecklist()
        val emptied = setDayRecord(setDayRecord(supplementsState(), checklist, "2026-09-01", DayRecord(creatine), 0L), checklist, "2026-09-01", DayRecord(), 1L)
        val state = setDayRecord(emptied, checklist, "2026-09-14", DayRecord(creatine), 0L)

        val supplements = state.consistencyOf(MoNeed.Supplements, StatisticsRange.FourWeeks)

        // Counted from 2026-09-14 on, not from the emptied 2026-09-01; creatine alone keeps no day.
        assertEquals(2 to 0, supplements.weeks.last().counted to supplements.weeks.last().kept)
        assertTrue(supplements.weeks.dropLast(1).none { it.started })
        assertNull(supplements.rate)
    }

    @Test
    fun `a gym session makes an active day and a record dated ahead is ignored`() {
        val state = AppState(
            sessions = listOf(session("s1", "A", "2026-09-14", "bench_press" to 20.0)),
            timeLogs = listOf(TimeLog(ReadingTimeId, "2026-09-18", 0L)),
        )

        val activity = state.consistencyOf(MoNeed.Activity, StatisticsRange.FourWeeks)
        assertEquals(1 to 3, activity.weeks.last().kept to activity.weeks.last().counted)
        // One active day this week reads as one day a week, not as seven from three days that have happened.
        assertEquals(1.0, activity.daysPerWeek!!, 1e-6)

        assertNull(state.consistencyOf(MoNeed.Reading, StatisticsRange.FourWeeks).rate)
    }

    @Test
    fun `a hidden feature has no block and substances never have one`() {
        val state = AppState(
            timeLogs = listOf(TimeLog(ReadingTimeId, Today, 0L)),
            measurementLogs = listOf(MeasurementRecord(Today, 0L, 80.0)).asMeasurementLogs("weight"),
            sessions = listOf(session("s1", "A", Today, "bench_press" to 20.0)),
            substanceLogs = emptyList<SubstanceLog>(),
        )
        val hidden = FeatureVisibilitySettings(disabledFeatures = setOf(DashboardFeature.Activity)).hidingTasks(MoNeed.Reading, MoNeed.Weight)

        val summary = historicalSummary(state, Today, StatisticsRange.FourWeeks, hidden)

        assertEquals(
            listOf(MoNeed.Supplements, MoNeed.Care, MoNeed.Eating).map { it.view.trackerRef },
            summary.consistency.map { it.ref },
        )
        assertTrue(summary.measurements.isEmpty())
        assertNull(summary.activity)

        val everything = historicalSummary(
            state,
            Today,
            StatisticsRange.FourWeeks,
            FeatureVisibilitySettings(disabledFeatures = emptySet()),
        )
        assertEquals(listOf(MoNeed.Supplements, MoNeed.Care, MoNeed.Weight, MoNeed.Eating, MoNeed.Reading).map { it.view.trackerRef }, everything.consistency.map { it.ref })
    }

    @Test
    fun `with nothing logged every block is empty instead of zero`() {
        val summary = historicalSummary(AppState(), Today, StatisticsRange.ThreeMonths)

        assertTrue(summary.consistency.all { it.rate == null && it.headline().text() == "—" })
        assertNull(summary.measurements.single().change)
        assertNull(summary.measurements.single().latestValue)
        assertEquals("—", summary.measurements.single().headline(summary.measurements.single().configuration.words()).text())
        val activity = summary.activity!!
        assertTrue(activity.days.all { it.kinds.isEmpty() })
        assertTrue(activity.kinds.isEmpty())
        assertEquals("Activity: nothing logged in the last 3 months.", activity.spokenSummary(summary.range).text())
        assertNull(summary.streak)
    }

    @Test
    fun `weight shows the change across the same days as the other blocks`() {
        val state = AppState(
            measurementLogs = listOf(
                MeasurementRecord("2026-08-24", 0L, 80.0),
                MeasurementRecord(Today, 0L, 78.0),
            ).asMeasurementLogs("weight"),
        )

        val weight = historicalSummary(state, Today, StatisticsRange.FourWeeks).measurements.single()

        assertEquals(24, weight.window.days.size)
        assertEquals("2026-08-24", weight.window.days.first().dateKey)
        assertEquals(-2.0, weight.change!!, 1e-9)
        assertEquals("-2 ⁨kg⁩", weight.headline(weight.configuration.words()).text())
        assertEquals("Weight trend · now 78 ⁨kg⁩", weight.caption(weight.configuration.words()).text())
    }

    @Test
    fun `a single weight reading is a value, not a change of zero`() {
        val state = AppState(measurementLogs = listOf(MeasurementRecord("2026-09-01", 0L, 80.0)).asMeasurementLogs("weight"))

        val weight = historicalSummary(state, Today, StatisticsRange.FourWeeks).measurements.single()

        assertEquals(80.0, weight.latestValue!!, 1e-9)
        assertNull(weight.change)
    }

    @Test
    fun `the activity calendar shows gym days with a set and every other logged activity, gym first`() {
        val state = AppState(
            sessions = listOf(
                session("a1", "A", "2026-09-07", "bench_press" to 20.0),
                session("b1", "B", "2026-09-09", "pull_ups" to -15.0),
                // Opened and left without a set: not a workout.
                session("empty", "C", "2026-09-11"),
            ),
            activityEntries = listOf(
                entry("r1", "Jogging", "2026-09-09", timestampMillis = 1L),
                entry("r2", "jogging", "2026-09-12"),
                entry("x1", "Bouldering", "2026-09-14"),
                entry("blank", " ", "2026-09-15"),
            ),
        )

        val activity = historicalSummary(state, Today, StatisticsRange.FourWeeks).activity!!
        val kindsOn = activity.days.associate { it.dateKey to it.kinds.map(ActivityKind::mark) }

        assertEquals(listOf(ActivityMark.Listed("gym")), kindsOn["2026-09-07"])
        assertEquals(listOf(ActivityMark.Listed("gym"), ActivityMark.Listed("jogging")), kindsOn["2026-09-09"])
        assertEquals(emptyList<ActivityMark>(), kindsOn["2026-09-11"])
        assertEquals(listOf(ActivityMark.Listed("jogging")), kindsOn["2026-09-12"])
        assertEquals(emptyList<ActivityMark>(), kindsOn["2026-09-15"])
        // Named by the configuration, whatever spelling an entry kept.
        assertEquals(
            listOf("Gym" to 2, "Jogging" to 2, "Bouldering" to 1),
            activity.kinds.map { it.kind.label to it.days },
        )
        assertEquals(3, activity.consistency.kept)
        assertEquals(1, activity.consistency.weeks.last().kept)
    }

    /** Kinds with the same number of days stand in the configuration's order, Gym first, as the fixed Gym kind did. */
    @Test
    fun `kinds with equal days stand in the configuration's order with Gym first`() {
        val state = AppState(
            sessions = listOf(session("g", "A", "2026-09-07", "bench_press" to 20.0)),
            activityEntries = listOf(entry("j", "Jogging", "2026-09-08"), entry("c", "Cycling", "2026-09-09"), entry("b", "Bouldering", "2026-09-10")),
        )

        val activity = historicalSummary(state, Today, StatisticsRange.FourWeeks).activity!!

        assertEquals(listOf("Gym" to 1, "Bouldering" to 1, "Cycling" to 1, "Jogging" to 1), activity.kinds.map { it.kind.label to it.days })
    }

    @Test
    fun `the calendars are whole weeks and the days after today are only places`() {
        val state = AppState(sessions = listOf(session("a1", "A", Today, "bench_press" to 20.0)))

        val summary = historicalSummary(state, Today, StatisticsRange.FourWeeks, streakHistory = emptyList())

        assertEquals(28, summary.activity!!.days.size)
        assertEquals(listOf(false, false, false, true), summary.activity.days.chunked(7).map { it.last().upcoming })
        assertEquals(4, summary.activity.days.count { it.upcoming })
        assertEquals(28, summary.streak!!.days.size)
        assertEquals(4, summary.streak.days.count { it.mark == MoDayMark.Upcoming })
        assertTrue(summary.streak.days.take(24).all { it.mark == MoDayMark.NothingDue })
    }

    @Test
    fun `the streak calendar shows whose tokens saved a day and nothing for a freeze`() {
        val history = listOf(
            MoDayRecord(LocalDate.parse("2026-09-10"), MoDayMark.Perfect, restedTrackers = setOf(ActivityTrackerRef)),
            MoDayRecord(LocalDate.parse("2026-09-11"), MoDayMark.Frozen, coveredTrackers = setOf(MoNeed.Care.view.trackerRef)),
            MoDayRecord(LocalDate.parse("2026-09-12"), MoDayMark.Protected, coveredTrackers = setOf(MoNeed.Weight.view.trackerRef, MoNeed.Reading.view.trackerRef)),
            MoDayRecord(LocalDate.parse("2026-09-13"), MoDayMark.Protected, coveredTrackers = setOf(MoNeed.Weight.view.trackerRef)),
            MoDayRecord(LocalDate.parse("2026-09-14"), MoDayMark.Missed),
            MoDayRecord(LocalDate.parse("2026-09-15"), MoDayMark.Perfect),
            MoDayRecord(LocalDate.parse(Today), MoDayMark.Open),
        )

        val streak = historicalSummary(AppState(), Today, StatisticsRange.FourWeeks, streakHistory = history).streak!!
        val byDate = streak.days.associateBy { it.dateKey }

        assertEquals(MoDayMark.NothingDue, byDate.getValue("2026-09-09").mark)
        assertTrue(byDate.getValue("2026-09-10").rested)
        assertEquals(emptyList<com.example.workouttracker.domain.TrackerRef>(), byDate.getValue("2026-09-11").tokens)
        assertEquals(listOf(MoNeed.Weight, MoNeed.Reading).map { it.view.trackerRef }, byDate.getValue("2026-09-12").tokens)
        assertEquals(MoDayMark.Open, byDate.getValue(Today).mark)
        assertEquals(2, streak.keptDays)
        assertEquals(1, streak.frozenDays)
        // Two days were protected, three task-days covered: a day two tokens saved is one day.
        assertEquals(2, streak.protectedDays)
        assertEquals(mapOf(MoNeed.Reading.view.trackerRef to 1, MoNeed.Weight.view.trackerRef to 2), streak.tokensByTracker)
        assertEquals(1, streak.restarts)
        assertEquals(
            "Streak in the last 4 weeks: 2 days kept, 1 day saved by a freeze, 2 protected days, 1 day missed, " +
                "1 restart, 1 planned rest day.",
            streak.spokenSummary(StatisticsRange.FourWeeks).text(),
        )
    }

    @Test
    fun `only the miss that ends a running streak is a restart`() {
        val history = listOf(
            MoDayRecord(LocalDate.parse("2026-09-07"), MoDayMark.Missed),
            MoDayRecord(LocalDate.parse("2026-09-08"), MoDayMark.Perfect),
            MoDayRecord(LocalDate.parse("2026-09-09"), MoDayMark.NothingDue),
            MoDayRecord(LocalDate.parse("2026-09-10"), MoDayMark.Missed),
            MoDayRecord(LocalDate.parse("2026-09-11"), MoDayMark.Missed),
        )

        val streak = historicalSummary(AppState(), Today, StatisticsRange.FourWeeks, streakHistory = history).streak!!

        assertEquals(listOf("2026-09-10"), streak.days.filter { it.restart }.map { it.dateKey })
        assertEquals(3, streak.missedDays)
    }

    @Test
    fun `a row says in words what its bars show`() {
        val state = AppState(
            measurementLogs = weightEveryDay(from = "2026-08-24", to = "2026-09-13"),
            sessions = listOf(
                session("s1", "A", "2026-08-24", "bench_press" to 20.0),
                session("s2", "B", "2026-09-02", "dips" to 0.0),
            ),
        )

        val reading = state.consistencyOf(MoNeed.Weight, StatisticsRange.FourWeeks)
        assertEquals("100%", reading.headline().text())
        assertEquals("Weight", reading.caption().text())
        // A feature that runs on a stored configuration is called by its stored name.
        assertEquals("Gewicht", reading.copy(label = "Gewicht").caption().text())
        assertEquals(
            "Weight kept on 21 of 21 days in the last 4 weeks, 100 percent. This week so far: 0 percent of its goal.",
            reading.spokenSummary(StatisticsRange.FourWeeks).text(),
        )

        val activity = state.consistencyOf(MoNeed.Activity, StatisticsRange.FourWeeks)
        assertEquals("0.7", activity.headline().text())
        assertEquals("Activity · days a week", activity.caption().text())
        assertEquals(
            "Activity logged on 2 of 21 days in the last 4 weeks, about 0.7 days a week. Gym on 2 days.",
            historicalSummary(state, Today, StatisticsRange.FourWeeks).activity!!
                .spokenSummary(StatisticsRange.FourWeeks).text(),
        )
    }

    /**
     * This week's bar is how much of the whole week's goal is done, so a Wednesday with three good
     * days is not drawn as a perfect week; a day off ahead takes its day out of the goal.
     */
    @Test
    fun `this week's bar measures the days so far against the days the week asks`() {
        val state = AppState(
            measurements = listOf(testWeightMeasurement()),
            measurementLogs = weightEveryDay(from = "2026-08-24", to = Today),
        )
        fun thisWeek(daysOff: HolidaySchedule) =
            statisticsSummary(state, Today, StatisticsRange.FourWeeks, daysOff = daysOff).consistency.single().weeks.last()

        assertEquals(3f / 7, thisWeek(HolidaySchedule()).fill, 1e-6f)
        val sickWeekend = HolidaySchedule().withSickDays(DayRange(LocalDate.parse("2026-09-19"), LocalDate.parse("2026-09-20")))
        assertEquals(3f / 5, thisWeek(sickWeekend).fill, 1e-6f)
    }

    /** The week that is not over never moves the number; only its bar shows how it goes. */
    @Test
    fun `this week stays out of the number until it is over`() {
        val throughSunday = AppState(measurementLogs = weightOn("2026-09-07", "2026-09-09", "2026-09-11", "2026-09-13"))
        val withThisWeek = throughSunday.copy(measurementLogs = throughSunday.measurementLogs + weightOn("2026-09-14", "2026-09-15", Today))

        val before = throughSunday.consistencyOf(MoNeed.Weight, StatisticsRange.FourWeeks)
        val after = withThisWeek.consistencyOf(MoNeed.Weight, StatisticsRange.FourWeeks)

        assertEquals(4.0 / 7, before.rate!!, 1e-9)
        assertEquals(before.rate, after.rate)
        assertEquals(before.headline().text(), after.headline().text())
        assertEquals(3f / 7, after.weeks.last().fill, 1e-6f)
    }

    /** Activity's rate rides with its calendar; every other feature has a row of its own. */
    private fun AppState.consistencyOf(
        feature: MoNeed,
        range: StatisticsRange,
    ): FeatureConsistency {
        val summary = historicalSummary(this, Today, range)
        return if (feature == MoNeed.Activity) {
            summary.activity!!.consistency
        } else {
            summary.consistency.single { it.ref == feature.view.trackerRef }
        }
    }

    private fun weightEveryDay(from: String, to: String) =
        weightOn(*generateSequence(LocalDate.parse(from)) { it.plusDays(1) }
            .takeWhile { it <= LocalDate.parse(to) }
            .map { it.toString() }
            .toList().toTypedArray())

    private fun weightOn(vararg days: String) = days.map { MeasurementRecord(it, 0L, 80.0) }.asMeasurementLogs("weight")

    private fun session(
        id: String,
        day: String,
        dateKey: String,
        vararg topSets: Pair<String, Double>,
    ) = WorkoutSession(
        id = id,
        activityId = "gym",
        day = day,
        dateKey = dateKey,
        timestampMillis = 0L,
        exerciseLogs = topSets.map { (exerciseId, weightKg) ->
            ExerciseLog(exerciseId, exerciseId, listOf(LoggedSet(weightKg, 8.0)))
        },
    )

    /** An entry of the seeded activity of that name, kept under the spelling it was logged with. */
    private fun entry(id: String, name: String, dateKey: String, timestampMillis: Long = 0L) =
        ActivityEntry(id, activityId = name.trim().lowercase(), name = name, dateKey = dateKey, timestampMillis = timestampMillis)

    private companion object {
        const val Today = "2026-09-16"
    }
}
