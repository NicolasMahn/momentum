package com.example.workouttracker.feature.statistics

import com.example.workouttracker.domain.compatibility.withWeightWeekRule
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.measurement.recordsOf
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.mo.TrackerMoPolicy

import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.SeededGymDayIds
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.generatedHistory
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.TokenCover
import com.example.workouttracker.domain.time.testMadeTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsRewardSeparationTest {
    @Test
    fun tokensAndFreezesProtectContinuityWithoutCreatingMeasurementFacts() {
        val monday = LocalDate.parse("2026-10-05")
        val measurement = testMadeMeasurement(id = "daily_measurement").copy(
            rewards = MeasurementBank.Standard(ChecklistRewards(maxPasses = 1, everyKeptDays = 1)),
            mo = TrackerMoPolicy(enabled = true),
        )
        val records = (0L..6L).map { offset -> MeasurementLog(measurement.id, monday.plusDays(offset).toString(), offset, 80.0) }
        val state = AppState(measurements = listOf(measurement), measurementLogs = records)
        val today = monday.plusDays(9)
        val visibility = FeatureVisibilitySettings()
        val ref = measurement.ref.trackerRef
        val beforeMissingDays = habitStreak(state, visibility, monday.plusDays(7), rewardStart = monday)
        assertEquals(1, beforeMissingDays.standings.getValue(ref).passes)
        assertEquals(7, beforeMissingDays.days)
        val protectedStreak = habitStreak(state, visibility, today, rewardStart = monday)
        val statistics = statisticsSummary(state, today.toString(), StatisticsRange.FourWeeks, visibility)
        val factual = statistics.consistency.single()

        // The one-token bank covers Monday; the available freeze covers Tuesday. Neither creates records.
        assertEquals(listOf(MoDayMark.Protected, MoDayMark.Frozen), protectedStreak.week.take(2))
        assertEquals(7, protectedStreak.days)
        listOf(monday.plusDays(7), monday.plusDays(8)).forEach { missingDay ->
            assertEquals(setOf(ref), protectedStreak.history.single { it.date == missingDay }.coveredTrackers)
        }
        // Monday and Tuesday stay missed although protected, so this week's bar stays empty;
        // Wednesday, today, is not over yet, and neither is the week, which the number waits for.
        assertEquals(0 to 2, factual.weeks.last().let { it.kept to it.counted })
        assertEquals(0f, factual.weeks.last().fill)
        assertEquals(7 to 7, factual.kept to factual.counted)
        assertEquals(records, state.measurementLogs)
        val withoutProtection = state.copy(measurements = listOf(measurement.copy(rewards = null)))
        val unprotected = statisticsSummary(withoutProtection, today.toString(), StatisticsRange.FourWeeks, visibility)
        assertEquals(statistics.consistency, unprotected.consistency)
        assertEquals(statistics.measurements.map { Triple(it.window, it.latestValue, it.change) },
            unprotected.measurements.map { Triple(it.window, it.latestValue, it.change) })
    }

    /**
     * A week under the token rules: activity on Monday, Tuesday, Thursday and Friday, a weigh-in
     * every day but Wednesday. Wednesday and Saturday become planned rest days, Sunday's missing
     * activity takes the Activity token and Wednesday's missing weigh-in the Weight token.
     */
    @Test
    fun tokensAndRestDaysShowOnTheStreakCalendarButNeverAsActivityOrWeighIns() {
        val monday = LocalDate.parse("2026-09-07")
        val today = LocalDate.parse("2026-09-14")
        val state = AppState(
            sessions = listOf(0L, 1L, 3L, 4L).map { offset ->
                val day = monday.plusDays(offset).toString()
                WorkoutSession(
                    id = day,
                    activityId = "gym",
                    day = "A",
                    dateKey = day,
                    timestampMillis = 0L,
                    exerciseLogs = listOf(ExerciseLog("bench_press", "Bench Press", listOf(LoggedSet(20.0, 8.0)))),
                )
            },
            measurementLogs = listOf(0L, 1L, 3L, 4L, 5L, 6L).map { offset ->
                MeasurementRecord(monday.plusDays(offset).toString(), 0L, 80.0)
            }.asMeasurementLogs("weight"),
        ).withWeight().withActivity()
        val visibility = showingOnly(MoNeed.Activity, MoNeed.Weight)
        val streak = habitStreak(state, visibility, today, rewardStart = monday)
        val summary = historicalSummary(
            state,
            today.toString(),
            StatisticsRange.FourWeeks,
            visibility,
            streakHistory = streak.history,
        )

        // The game's reading of the week, as the streak calendar shows it.
        val streakDays = summary.streak!!.days.associateBy { it.dateKey }
        val wednesday = streakDays.getValue("2026-09-09")
        assertEquals(MoDayMark.Protected, wednesday.mark)
        assertEquals(listOf(MoNeed.Weight.view.trackerRef), wednesday.tokens)
        assertTrue(wednesday.rested)
        assertTrue(streakDays.getValue("2026-09-12").rested)
        assertEquals(listOf(MoNeed.Activity.view.trackerRef), streakDays.getValue("2026-09-13").tokens)

        // What was recorded: a token or a rest day is never an active day or a weigh-in.
        val activity = summary.activity!!
        val activeDays = activity.days.filter { it.kinds.isNotEmpty() }.map { it.dateKey }
        assertEquals(listOf("2026-09-07", "2026-09-08", "2026-09-10", "2026-09-11"), activeDays)
        assertEquals(4, activity.consistency.kept)
        assertEquals(listOf(4), activity.kinds.map { it.days })
        val weight = summary.consistency.single { it.ref == MoNeed.Weight.view.trackerRef }
        assertEquals(6, weight.kept)

        // The facts do not move with the rewards: no streak, or one walked under other rules, leaves
        // every factual block exactly as it was.
        val legacyStreak = habitStreak(state, visibility, today, rewardStart = today.plusDays(1))
        listOf(null, legacyStreak.history).forEach { history ->
            val other = historicalSummary(state, today.toString(), StatisticsRange.FourWeeks, visibility, history)
            assertEquals(summary.activity, other.activity)
            assertEquals(summary.consistency, other.consistency)
            assertEquals(summary.measurements, other.measurements)
        }
    }

    /**
     * A long, messy history judged under every reward input that can change: the rule start, the
     * holidays, sick days and today's chosen passes. However the rewards fall, what was recorded stays put.
     */
    @Test
    fun noRewardInputMovesTheRecordedFactsOfALongHistory() {
        val today = LocalDate.parse("2026-10-14")
        val state = generatedHistory(seed = 21, LocalDate.parse("2026-05-06"), today, adherence = 0.65)
        val visibility = FeatureVisibilitySettings()
        val holidays = HolidaySchedule(listOf(DayRange(LocalDate.parse("2026-09-24"), LocalDate.parse("2026-10-04"))))
        val sick = holidays
            .withSickDays(DayRange(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-08")))
            .withSickDays(DayRange(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-14")))
        val moods = listOf(
            moMood(state, visibility, today),
            moMood(state, visibility, today, holidays, chosenTrackers = MoNeed.entries.mapTo(LinkedHashSet()) { it.view.trackerRef }),
            moMood(state, visibility, today, sick),
            moMood(state, visibility, today, rewardStart = LocalDate.parse("2026-06-01")),
            moMood(state, visibility, today, holidays, rewardStart = today.plusDays(1)),
        )
        // The rewards really differ, so the comparison below is not between equals.
        assertTrue(moods.map { it.streak.history }.distinct().size > 1)
        val plain = historicalSummary(state, today.toString(), StatisticsRange.ThreeMonths, visibility)
        moods.forEach { mood ->
            val other = historicalSummary(state, today.toString(), StatisticsRange.ThreeMonths, visibility, mood.streak.history)
            assertEquals(plain.consistency, other.consistency)
            assertEquals(plain.measurements, other.measurements)
            assertEquals(plain.activity, other.activity)
        }
        // What today kept depends on the rule set, the old rules being looser, but never on tokens or
        // passes; a sick today asks nothing, so it keeps nothing either.
        assertEquals(1, (moods.take(2) + moods[3]).map { it.keptTrackersToday }.distinct().size)
        assertEquals(emptyList<com.example.workouttracker.domain.TrackerRef>(), moods[2].keptTrackersToday)
    }

    /**
     * Weight's settings are reward rules and a name, never records: a smaller bank, fewer days a
     * week, holidays still due and another name leave every logged count, every chart value and
     * the 7-day average exactly as they were, while the streak they feed really moves.
     */
    @Test
    fun noWeightSettingMovesALoggedCountOrAChartValue() {
        val today = LocalDate.parse("2026-10-14")
        val records = generatedHistory(seed = 27, LocalDate.parse("2026-05-06"), today, adherence = 0.7).withWeight()
        val visibility = FeatureVisibilitySettings()
        val holidays = HolidaySchedule(listOf(DayRange(LocalDate.parse("2026-09-24"), LocalDate.parse("2026-10-04"))))
        val changed = records
            .withWeightWeekRule(LocalDate.parse("2026-08-12"), maxPasses = 1, keptDaysInWeek = 2)
            .applyingMeasurementEdits(WeightMeasurementId, listOf(MeasurementEdit.SetWhenAway(WhenAway.Due), MeasurementEdit.Rename("Scale")), LocalDate.parse("2026-08-12"))
            .withWeightWeekRule(LocalDate.parse("2026-10-01"), maxPasses = 9, keptDaysInWeek = 7)
        assertEquals(records.recordsOf("weight"), changed.recordsOf("weight"))

        val before = habitStreak(records, visibility, today, holidays)
        val after = habitStreak(changed, visibility, today, holidays)
        assertTrue(before.history != after.history && before.participantBanks != after.participantBanks)
        StatisticsRange.entries.forEach { range ->
            val plain = historicalSummary(records, today.toString(), range, visibility, before.history)
            val other = historicalSummary(changed, today.toString(), range, visibility, after.history)
            assertEquals(plain.consistency.associate { it.ref to (it.unit to it.weeks) }, other.consistency.associate { it.ref to (it.unit to it.weeks) })
            assertEquals(plain.measurements.map { Triple(it.window, it.latestValue, it.change) }, other.measurements.map { Triple(it.window, it.latestValue, it.change) })
            assertEquals("Scale", other.measurements.single { it.configuration.id == WeightMeasurementId }.configuration.label)
            assertEquals(plain.activity, other.activity)
        }
    }

    /**
     * A timer's weeks that fall short are what its tokens cover, and a wider token covers more of
     * them, yet the share of weeks at goal counts recorded minutes only: a bank, a token cover and
     * the streak they feed leave every week of the row as it was.
     */
    @Test
    fun noTokenOrCoverMovesATimersWeeksAtGoal() {
        val today = LocalDate.parse("2026-09-16")
        val bare = testMadeTime(goal = 60).copy(mo = TrackerMoPolicy(enabled = true))
        val logs = listOf("2026-08-24" to 70, "2026-09-01" to 50, "2026-09-08" to 30, "2026-09-15" to 20)
            .map { (day, minutes) -> TimeLog(bare.id, day, 0L, minutes = minutes) }
        val visibility = FeatureVisibilitySettings()
        fun rowOf(rewards: TimeRewards?): Pair<FeatureConsistency, List<com.example.workouttracker.domain.mo.MoDayRecord>> {
            val state = AppState(time = listOf(bare.copy(rewards = rewards)), timeLogs = logs)
            val streak = habitStreak(state, visibility, today, rewardStart = LocalDate.parse("2026-08-24"))
            return statisticsSummary(state, today.toString(), StatisticsRange.FourWeeks, visibility, streak.history).consistency.single() to streak.history
        }
        val (plain, plainHistory) = rowOf(null)
        val (quarter, quarterHistory) = rowOf(TimeRewards(maxPasses = 3))
        val (wide, wideHistory) = rowOf(TimeRewards(maxPasses = 3, tokenCover = TokenCover.Minutes(60)))

        // The rewards really differ, so the comparison below is not between equals.
        assertTrue(listOf(plainHistory, quarterHistory, wideHistory).distinct().size > 1)
        assertEquals(1, plain.kept)
        assertEquals(3, plain.counted)
        assertEquals(listOf(plain, plain), listOf(quarter, wide))
    }
}
