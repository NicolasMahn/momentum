package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.freeDays

import com.example.workouttracker.domain.compatibility.moFreeDays

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.SeededGym
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.logactivity.gym.latestWorkoutSummaries
import com.example.workouttracker.feature.logactivity.gym.planned
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * JVM timings of the evaluations Home, reminders and the widget run, over histories of one and
 * three years. Run with `MO_BENCHMARK=1`; it is skipped otherwise, because timings are no test.
 * Every case is warmed up first and the runs are interleaved, so no case pays for a cold JIT.
 * These are desktop JVM numbers only; they say nothing measured about Android performance.
 */
class MoEvaluationBenchmark {
    @Test fun measure() {
        assumeTrue(System.getenv("MO_BENCHMARK") == "1")
        val today = LocalDate.parse("2026-10-21")
        val holidays = HolidaySchedule(listOf(DayRange(today.minusDays(40), today.minusDays(30))))
        val all = FeatureVisibilitySettings()
        val two = only(MoNeed.Weight, MoNeed.Care)
        val exercises = SeededGym.day("A")!!.exercises.map { it.planned }
        val cases = listOf(1, 3).flatMap { years ->
            val state = generatedHistory(years, today.minusYears(years.toLong()), today, adherence = 0.85)
            val evaluation = MoEvaluation(state, all, today, holidays)
            listOf<Pair<String, () -> Any?>>(
                "moMood, all visible, $years y (${state.recordCount()} records)" to { moMood(state, all, today, holidays) },
                "moMood, two visible, $years y" to { moMood(state, two, today, holidays) },
                "moFreeDays, all visible, $years y" to { moFreeDays(state, all, today, holidays) },
                "Home: mood + free days, $years y" to { MoEvaluation(state, all, today, holidays).run { freeDays(); mood(emptySet()) } },
                "Home: choosing a pass, $years y" to { evaluation.mood(setOf(MoNeed.Weight)) },
                "Gym editor summaries, $years y (${state.sessions.size} sessions)" to {
                    latestWorkoutSummaries(state, exercises)
                },
            )
        }
        repeat(WARMUP) { cases.forEach { (_, block) -> block() } }
        val nanos = cases.associate { (label, _) -> label to LongArray(RUNS) }
        repeat(RUNS) { run ->
            cases.forEach { (label, block) ->
                val start = System.nanoTime()
                block()
                nanos.getValue(label)[run] = System.nanoTime() - start
            }
        }
        nanos.forEach { (label, runs) ->
            runs.sort()
            println("BENCH %-52s median %8.3f ms  p90 %8.3f ms".format(label, runs[RUNS / 2] / 1e6, runs[RUNS * 9 / 10] / 1e6))
        }
    }

    private fun AppState.recordCount() = measurementLogs.size + sessions.size + activityEntries.size + checklistDays.size +
        timeLogs.size + questionEntries.size

    private companion object {
        const val WARMUP = 20
        const val RUNS = 41
    }
}
