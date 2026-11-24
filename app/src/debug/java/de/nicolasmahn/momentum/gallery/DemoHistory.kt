package de.nicolasmahn.momentum.gallery

import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.checklist.ticksAt
import de.nicolasmahn.momentum.domain.checklist.DayRecord
import de.nicolasmahn.momentum.domain.checklist.setCheckpointRecord
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration
import de.nicolasmahn.momentum.domain.questions.QuestionEntry
import de.nicolasmahn.momentum.domain.questions.saveQuestionsDay
import de.nicolasmahn.momentum.domain.time.addTimeMinutes
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.domain.substance.saveSubstanceLog
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ticks
import de.nicolasmahn.momentum.domain.checklist.rulesOn
import de.nicolasmahn.momentum.domain.checklist.setDayRecord
import de.nicolasmahn.momentum.domain.checklist.tickOf
import de.nicolasmahn.momentum.feature.logactivity.RecordActivityResult
import de.nicolasmahn.momentum.domain.activity.activityRules
import de.nicolasmahn.momentum.domain.activity.GymActivityId
import de.nicolasmahn.momentum.domain.activity.routine.RoutineExercise
import de.nicolasmahn.momentum.feature.logactivity.gym.planned
import de.nicolasmahn.momentum.feature.logactivity.gym.withSavedWorkoutSet
import de.nicolasmahn.momentum.feature.logactivity.gym.withWorkoutDuration
import de.nicolasmahn.momentum.feature.logactivity.recordActivity
import de.nicolasmahn.momentum.feature.measurement.replaceMeasurementDay
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.round
import kotlin.random.Random

/**
 * One invented half-year of records for the disposable debug build: the gallery specimens and the
 * "Fill app with demo data" action both read this, so a specimen shows exactly what the real screen
 * will show after a fill.
 *
 * Every record is written through the app's own transition for its feature, so demo data can only
 * be a shape the app itself could have produced. Nothing here is random at runtime: each stream has
 * its own seed and the whole history is anchored on today, so two fills on the same day are equal.
 */
internal const val DemoHistoryDayCount = 183

/** Days are counted backwards from today; the hour only decides the order within a day. */
internal class DemoDayClock(todayKey: String) {
    private val today: LocalDate = LocalDate.parse(todayKey)

    fun dateKey(daysAgo: Int): String = today.minusDays(daysAgo.toLong()).toString()

    fun dayOfWeek(daysAgo: Int): DayOfWeek = today.minusDays(daysAgo.toLong()).dayOfWeek

    fun millis(daysAgo: Int, hour: Int): Long =
        (today.toEpochDay() - daysAgo) * MillisPerDay + hour * MillisPerHour

    private companion object {
        const val MillisPerHour = 60L * 60L * 1000L
        const val MillisPerDay = 24L * MillisPerHour
    }
}

/** An explicitly configured supplements checklist for the disposable gallery. */
internal val DemoSupplementsChecklist = sampleSupplementsChecklist(
    SupplementsLabels(checklist = "Supplements", multivitamin = "Multivitamin", creatine = "Creatine", vitaminD = "Vitamin D"),
    remind = false,
)

/** An explicitly configured two-checkpoint routine, its reminders off. */
internal val DemoCareChecklist = sampleCareChecklist(
    CareLabels(
        checklist = "Care",
        morning = "Morning",
        evening = "Evening",
        toothbrushing = "Brush teeth",
        hairFoam = "Hair foam",
        cleanBetweenTeeth = "Clean between teeth",
    ),
    remindMorning = false,
    remindEvening = false,
)

/** A numerical tracker with an explicit unit, value policy, bank and Mo motif. */
internal val DemoWeightMeasurement = sampleWeightMeasurement(label = "Weight")

/** Questions about yesterday with an explicitly configured food motif. */
internal val DemoEatingQuestions = sampleEatingQuestions(label = "Eating")

/** A timer with an explicitly configured book motif and weekly goal. */
internal val DemoReadingTime = sampleReadingTime(label = "Reading")

internal fun demoHistory(
    todayKey: String,
    dayCount: Int = DemoHistoryDayCount,
    supplements: ChecklistConfiguration = DemoSupplementsChecklist,
    care: ChecklistConfiguration = DemoCareChecklist,
    weight: MeasurementConfiguration = DemoWeightMeasurement,
    eating: QuestionsConfiguration = DemoEatingQuestions,
    reading: TimeConfiguration = DemoReadingTime,
): AppState {
    val clock = DemoDayClock(todayKey)
    val days = (dayCount - 1) downTo 0
    return AppState(checklists = listOf(supplements, care), measurements = listOf(weight), questions = listOf(eating), time = listOf(reading), substances = SubstanceSettings.Legacy)
        .withDemoWeights(clock, days)
        .withDemoGymSessions(clock, days)
        .withDemoActivities(clock, days)
        .withDemoReading(clock, days)
        .withDemoCare(clock, days)
        .withDemoSupplements(clock, days)
        .withDemoEating(clock, days)
        .withDemoSubstances(clock, days)
}

// A holiday: nine days with no scale, and the routines around it fall apart too.
private val DemoHolidayGap = 96..104

// Three weeks where weighing in became an afterthought rather than a habit.
private val DemoWeightSporadicStretch = 40..60

private val DemoReadingSlumps = listOf(110..122, 33..39)

// A clean stretch in the middle and a clean recent run, so "days without" has something to report.
private val DemoSubstanceCleanStretches = listOf(61..95, 0..11)

// ---------------------------------------------------------------- weight

private const val MorningWeighInHour = 7

/**
 * The trend the EWMA line has to find under the noise: a first loss, a plateau, a second loss, a
 * small rebound, then a hold. Knots are (progress from the oldest day, kilograms).
 */
private val DemoWeightTrendKg = listOf(
    0.00 to 89.0,
    0.35 to 87.4,
    0.55 to 87.4,
    0.80 to 86.0,
    0.92 to 86.9,
    1.00 to 86.5,
)

private fun AppState.withDemoWeights(clock: DemoDayClock, days: IntProgression): AppState {
    val random = Random(seed = 11)
    val oldest = days.first
    var state = this
    for (daysAgo in days) {
        if (!demoWeighedIn(daysAgo, random)) continue
        state = replaceMeasurementDay(
            state = state,
            measurementId = "demo_weight",
            dateKey = clock.dateKey(daysAgo),
            timestampMillis = clock.millis(daysAgo, MorningWeighInHour),
            value = demoWeightKg(daysAgo, oldest, clock.dayOfWeek(daysAgo), random),
        )
    }
    return state
}

private fun demoWeighedIn(daysAgo: Int, random: Random): Boolean = when {
    daysAgo in DemoHolidayGap -> false
    daysAgo in DemoWeightSporadicStretch -> random.nextDouble() < 0.25
    else -> random.nextDouble() >= 0.12
}

private fun demoWeightKg(
    daysAgo: Int,
    oldestDaysAgo: Int,
    dayOfWeek: DayOfWeek,
    random: Random,
): Double {
    val progress = (oldestDaysAgo - daysAgo).toDouble() / oldestDaysAgo
    // Two uniform draws subtracted peak around zero, so most mornings sit near the trend and only
    // a few reach the full water swing.
    val water = (random.nextDouble() - random.nextDouble()) * 0.8
    val weekendFood = if (dayOfWeek == DayOfWeek.SUNDAY || dayOfWeek == DayOfWeek.MONDAY) 0.4 else 0.0
    val raw = demoWeightTrendKg(progress) + water + weekendFood
    // A bathroom scale reports tenths, and the chart should be judged against that resolution.
    return round(raw * 10.0) / 10.0
}

private fun demoWeightTrendKg(progress: Double): Double {
    val upperIndex = DemoWeightTrendKg.indexOfFirst { it.first >= progress }
        .takeIf { it > 0 } ?: return DemoWeightTrendKg.first().second
    val (startProgress, startKg) = DemoWeightTrendKg[upperIndex - 1]
    val (endProgress, endKg) = DemoWeightTrendKg[upperIndex]
    val span = endProgress - startProgress
    val position = if (span == 0.0) 0.0 else (progress - startProgress) / span
    return startKg + (endKg - startKg) * position
}

// ---------------------------------------------------------------- gym

private const val GymHour = 18
private val DemoGymWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

/** Sessions on the same plan before the working weight goes up one step. */
private const val DemoSessionsPerProgressionStep = 5

private fun AppState.withDemoGymSessions(clock: DemoDayClock, days: IntProgression): AppState {
    val random = Random(seed = 22)
    val gym = requireNotNull(activityRules.activity(GymActivityId))
    val plan = requireNotNull(gym.routine)
    val sessionsPerDay = mutableMapOf<String, Int>()
    var nextDay = 0
    var state = this
    for (daysAgo in days) {
        if (clock.dayOfWeek(daysAgo) !in DemoGymWeekdays) continue
        val missed = if (daysAgo in DemoHolidayGap) true else random.nextDouble() < 0.12
        if (missed) continue

        val day = plan.retainedDays[nextDay]
        nextDay = (nextDay + 1) % plan.retainedDays.size
        val block = (sessionsPerDay[day.id] ?: 0) / DemoSessionsPerProgressionStep
        sessionsPerDay[day.id] = (sessionsPerDay[day.id] ?: 0) + 1

        // Real sessions sometimes end early, so the last exercise is missing from a few of them.
        val exercises = if (random.nextDouble() < 0.18) day.retainedExercises.dropLast(1) else day.retainedExercises
        val dateKey = clock.dateKey(daysAgo)
        val timestampMillis = clock.millis(daysAgo, GymHour)
        exercises.forEach { exercise ->
            repeat(exercise.sets) { setIndex ->
                state = state.withSavedWorkoutSet(
                    activity = gym,
                    day = day,
                    exercise = exercise.planned,
                    setIndex = setIndex,
                    set = LoggedSet(
                        weightKg = exercise.workingWeightKg(block),
                        reps = random.nextInt(exercise.reps.first, exercise.reps.last + 1).toDouble(),
                    ),
                    todayKey = dateKey,
                    timestampMillis = timestampMillis,
                )
            }
        }
        if (random.nextDouble() < 0.7) {
            state = state.withWorkoutDuration(gym, day, dateKey, random.nextInt(48, 78))
        }
    }
    return state
}

private fun RoutineExercise.workingWeightKg(block: Int): Double =
    requireNotNull(weight).let { it.startKg + block * it.stepKg }

// ---------------------------------------------------------------- other activities

private const val ActivityHour = 17

private data class DemoActivityHabit(
    val name: String,
    val dayOfWeek: DayOfWeek?,
    val chance: Double,
    val minMinutes: Int,
    val maxMinutes: Int,
)

private val DemoActivityHabits = listOf(
    DemoActivityHabit("Bouldering", DayOfWeek.SATURDAY, chance = 0.85, minMinutes = 80, maxMinutes = 130),
    DemoActivityHabit("Cycling", DayOfWeek.TUESDAY, chance = 0.4, minMinutes = 35, maxMinutes = 75),
    DemoActivityHabit("Jogging", DayOfWeek.THURSDAY, chance = 0.35, minMinutes = 22, maxMinutes = 45),
    DemoActivityHabit("Yoga", dayOfWeek = null, chance = 0.05, minMinutes = 20, maxMinutes = 40),
    DemoActivityHabit("Swimming", dayOfWeek = null, chance = 0.03, minMinutes = 30, maxMinutes = 55),
)

private fun AppState.withDemoActivities(clock: DemoDayClock, days: IntProgression): AppState {
    val random = Random(seed = 33)
    var state = this
    for (daysAgo in days) {
        if (daysAgo in DemoHolidayGap) continue
        DemoActivityHabits.forEachIndexed { habitIndex, habit ->
            if (habit.dayOfWeek != null && clock.dayOfWeek(daysAgo) != habit.dayOfWeek) return@forEachIndexed
            if (random.nextDouble() >= habit.chance) return@forEachIndexed
            val dateKey = clock.dateKey(daysAgo)
            val result = recordActivity(
                state = state,
                rawName = habit.name,
                dateKey = dateKey,
                // Not every logged activity gets timed, and the timeline has to read well either way.
                durationMinutes = if (random.nextDouble() < 0.7) {
                    random.nextInt(habit.minMinutes, habit.maxMinutes + 1)
                } else {
                    null
                },
                notes = null,
                newActivityId = "demo-activity-${habit.name.lowercase()}",
                newEntryId = "demo-activity-$dateKey-$habitIndex",
                timestampMillis = clock.millis(daysAgo, ActivityHour + habitIndex),
            )
            state = when (result) {
                is RecordActivityResult.Success -> result.state
                is RecordActivityResult.Invalid -> error("Demo activity rejected: ${result.problem}")
            }
        }
    }
    return state
}

// ---------------------------------------------------------------- reading

private const val ReadingHour = 22

private fun AppState.withDemoReading(clock: DemoDayClock, days: IntProgression): AppState {
    val random = Random(seed = 44)
    var state = this
    for (daysAgo in days) {
        val chance = when {
            daysAgo in DemoHolidayGap -> 0.35
            DemoReadingSlumps.any { daysAgo in it } -> 0.15
            else -> 0.82
        }
        if (random.nextDouble() >= chance) continue
        state = state.copy(timeLogs = addTimeMinutes(
            logs = state.timeLogs,
            viewId = DemoReadingId,
            dateKey = clock.dateKey(daysAgo),
            minutes = 15,
            timestampMillis = clock.millis(daysAgo, ReadingHour),
        ))
    }
    return state
}

// ---------------------------------------------------------------- care

private fun AppState.withDemoCare(clock: DemoDayClock, days: IntProgression): AppState {
    val random = Random(seed = 55)
    var state = this
    val checklist = requireNotNull(careChecklist)
    for (daysAgo in days) {
        val away = daysAgo in DemoHolidayGap
        val dateKey = clock.dateKey(daysAgo)
        val rules = checklist.rulesOn(LocalDate.parse(dateKey))
        DemoCareSession.entries.forEach { session ->
            val done = rules.ticksAt(session.storageId).filterTo(LinkedHashSet()) { tick ->
                val action = requireNotNull(DemoCareAction.fromStorageId(tick.substringAfter('/')))
                random.nextDouble() < demoCareChance(session, action, away)
            }
            state = setCheckpointRecord(
                state = state,
                checklist = checklist,
                dateKey = dateKey,
                checkpointId = session.storageId,
                selected = DayRecord(done),
                timestampMillis = clock.millis(daysAgo, if (session == DemoCareSession.MORNING) 7 else 22),
            )
        }
    }
    return state
}

private fun demoCareChance(session: DemoCareSession, action: DemoCareAction, away: Boolean): Double {
    val base = when {
        action == DemoCareAction.TOOTHBRUSHING && session == DemoCareSession.MORNING -> 0.97
        action == DemoCareAction.TOOTHBRUSHING -> 0.88
        session == DemoCareSession.MORNING -> 0.6
        else -> 0.12
    }
    return if (away) base * 0.5 else base
}

// ---------------------------------------------------------------- supplements

private const val SupplementHour = 8

private fun AppState.withDemoSupplements(clock: DemoDayClock, days: IntProgression): AppState {
    val random = Random(seed = 66)
    var state = this
    for (daysAgo in days) {
        val chance = if (daysAgo in DemoHolidayGap) 0.35 else 0.85
        if (random.nextDouble() >= chance) continue
        val checklist = requireNotNull(state.supplementsChecklist)
        val due = checklist.rulesOn(LocalDate.parse(clock.dateKey(daysAgo))).ticks
        val taken = if (random.nextDouble() < 0.75) {
            due
        } else {
            setOf(tickOf(SupplementsCheckpointId, StandardSupplementIds.MULTIVITAMIN))
        }
        state = setDayRecord(
            state = state,
            checklist = checklist,
            dateKey = clock.dateKey(daysAgo),
            selected = DayRecord(taken),
            timestampMillis = clock.millis(daysAgo, SupplementHour),
        )
    }
    return state
}

// ---------------------------------------------------------------- eating

private const val EatingHour = 21

private val DemoEatingNotes = listOf(
    "Cooked twice, ate out once.",
    "Too much bread again.",
    "Good day, lots of vegetables.",
    "Late dinner after the gym.",
    "Snacked through the afternoon.",
)

private fun AppState.withDemoEating(clock: DemoDayClock, days: IntProgression): AppState {
    val random = Random(seed = 77)
    val eating = questions.single()
    var entries = questionEntries
    for (daysAgo in days) {
        val chance = if (daysAgo in DemoHolidayGap) 0.4 else 0.8
        if (random.nextDouble() >= chance) continue
        val dateKey = clock.dateKey(daysAgo)
        val entry = QuestionEntry(
            id = "demo-eating-$dateKey",
            viewId = DemoEatingId,
            dateKey = dateKey,
            timestampMillis = clock.millis(daysAgo, EatingHour),
            answers = mapOf(
                EatingQualityQuestionId to demoEatingQuality(random),
                EatingAmountQuestionId to demoEatingAmount(random),
            ),
            note = if (random.nextDouble() < 0.25) DemoEatingNotes.random(random) else null,
        )
        entries = saveQuestionsDay(entries, eating.id, eating.scales, dateKey, entry.timestampMillis, entry.answers, entry.note, newEntryId = entry.id)
    }
    return copy(questionEntries = entries)
}

private fun demoEatingQuality(random: Random): String = when (random.nextInt(10)) {
    0 -> "poor"
    1, 2 -> "fair"
    3, 4, 5, 6 -> "good"
    7, 8 -> "very_good"
    else -> "excellent"
}

private fun demoEatingAmount(random: Random): String = when (random.nextInt(10)) {
    0 -> "much_less"
    1, 2 -> "a_little_less"
    3, 4, 5, 6 -> "about_the_same"
    7, 8 -> "a_little_more"
    else -> "much_more"
}

// ---------------------------------------------------------------- substances

private const val SubstanceHour = 20

private fun AppState.withDemoSubstances(clock: DemoDayClock, days: IntProgression): AppState {
    val random = Random(seed = 88)
    var state = this
    for (daysAgo in days) {
        if (DemoSubstanceCleanStretches.any { daysAgo in it }) continue
        val weekend = clock.dayOfWeek(daysAgo) in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        SubstanceKind.Legacy.forEachIndexed { kindIndex, kind ->
            val chance = when (kind) {
                SubstanceKind.ALCOHOL -> if (weekend) 0.35 else 0.06
                SubstanceKind.CANNABIS -> 0.08
                else -> 0.05
            }
            if (random.nextDouble() >= chance) return@forEachIndexed
            val dateKey = clock.dateKey(daysAgo)
            state = saveSubstanceLog(
                state = state,
                log = SubstanceLog(
                    id = "demo-substance-$dateKey-${kind.id}",
                    substanceId = kind.id,
                    dateKey = dateKey,
                    timestampMillis = clock.millis(daysAgo, SubstanceHour + kindIndex),
                    amount = demoSubstanceAmount(kind, random),
                    amountUnit = kind.unit,
                ),
            )
        }
    }
    return state
}

private fun demoSubstanceAmount(kind: SubstanceKind, random: Random): Double = when (kind) {
    SubstanceKind.ALCOHOL -> random.nextInt(1, 5).toDouble()
    SubstanceKind.CANNABIS -> round(random.nextDouble(0.2, 0.9) * 10.0) / 10.0
    else -> random.nextInt(1, 4).toDouble()
}
