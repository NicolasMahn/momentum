package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.questions.eatingEntry
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.GymActivityId
import com.example.workouttracker.domain.activity.SeededActivityRules
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.SeededGymDayIds
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.compatibility.LegacyCareAction
import com.example.workouttracker.domain.care.CareDone
import com.example.workouttracker.domain.care.withCare
import com.example.workouttracker.domain.compatibility.LegacyCareSession
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.compatibility.StandardSupplementIds
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.supplement.AllSupplementIds
import com.example.workouttracker.domain.supplement.YearRoundSupplementIds
import com.example.workouttracker.domain.supplement.supplementDay
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import com.example.workouttracker.domain.measurement.MeasurementRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.random.Random
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading

/**
 * A messy, reproducible history for comparing whole evaluations: partial care sessions, partial
 * supplement days, late eating reflections, empty gym sessions, unplanned activities and reading
 * without a duration. Each task is taken up on a different weekday, so first weeks start mid-week.
 *
 * Timestamps are UTC, so a test that reads eating cutoffs must run with a UTC default zone.
 */
internal fun generatedHistory(seed: Int, first: LocalDate, last: LocalDate, adherence: Double): AppState {
    val random = Random(seed)
    fun chance(p: Double = adherence) = random.nextDouble() < p
    fun at(day: LocalDate, hour: Int) = day.atTime(LocalTime.of(hour, random.nextInt(60))).toInstant(ZoneOffset.UTC).toEpochMilli()

    val weight = mutableListOf<MeasurementRecord>()
    val care = mutableListOf<CareDone>()
    val supplements = mutableListOf<ChecklistDay>()
    val eating = mutableListOf<QuestionEntry>()
    val activities = mutableListOf<ActivityEntry>()
    val sessions = mutableListOf<WorkoutSession>()
    val reading = mutableListOf<TimeLog>()
    val activityNames = listOf("Running", "Bouldering", "Yoga", "Cycling", "jogging")

    generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.forEach { day ->
        val key = day.toString()
        val sinceFirst = day.toEpochDay() - first.toEpochDay()
        if (chance()) weight += MeasurementRecord(key, at(day, 7), 70.0 + random.nextDouble(5.0))
        if (sinceFirst >= 3) LegacyCareSession.entries.forEach { session ->
            LegacyCareAction.entries.forEach { action ->
                if (chance(if (action == LegacyCareAction.CLEAN_BETWEEN_TEETH) adherence / 2 else adherence)) {
                    care += CareDone(key, session, action, at(day, if (session == LegacyCareSession.MORNING) 8 else 22))
                }
            }
        }
        if (sinceFirst >= 10 && chance()) {
            val taken = when (random.nextInt(4)) {
                0 -> YearRoundSupplementIds
                1 -> setOf(StandardSupplementIds.CREATINE)
                2 -> emptySet()
                else -> AllSupplementIds
            }
            // An emptied record is never stored, so a day with nothing taken has none. Its time is
            // still drawn, so every later record keeps the values the history always had.
            val timestamp = at(day, 9)
            if (taken.isNotEmpty()) supplements += supplementDay(key, timestamp, taken)
        }
        if (sinceFirst >= 1 && chance()) {
            // About the day before; most are written the next evening, some only after the 04:00 cutoff.
            val about = day.minusDays(1)
            val written = if (chance(0.85)) at(day, 20) else at(day.plusDays(1), 5)
            eating += eatingEntry("eat-$about", about.toString(), written, quality = "good")
        }
        if (sinceFirst >= 17 && chance()) {
            if (chance(0.5)) {
                val sets = random.nextInt(4)
                sessions += WorkoutSession(
                    id = "gym-$key", activityId = GymActivityId, day = SeededGymDayIds[random.nextInt(3)], dateKey = key, timestampMillis = at(day, 18),
                    exerciseLogs = listOf(ExerciseLog("bench", "Bench", List(sets) { LoggedSet(40.0, 8.0) })),
                )
            } else {
                val name = activityNames[random.nextInt(activityNames.size)]
                activities += ActivityEntry("act-$key", activityIdOf(name), name, key, at(day, 17))
            }
        }
        if (sinceFirst >= 5 && chance(adherence * 0.6)) {
            reading += TimeLog(ReadingTimeId, key, at(day, 21), minutes = if (chance(0.15)) null else 5 + random.nextInt(50))
        }
    }
    // The order persistence keeps: newest first.
    return AppState(
        measurementLogs = weight.sortedByDescending { it.dateKey }.asMeasurementLogs("weight"),
        sessions = sessions.sortedByDescending { it.timestampMillis },
        activityEntries = activities.sortedByDescending { it.timestampMillis },
        checklists = listOf(testSupplementsChecklist()),
        checklistDays = supplements.reversed(),
        timeLogs = reading.reversed(),
        questionEntries = eating.reversed(),
    ).withCare(*care.toTypedArray()).withWeight().withEating().withReading().withActivity()
}
