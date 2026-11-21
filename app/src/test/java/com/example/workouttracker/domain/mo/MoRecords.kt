package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.needs

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.measurement.recordsOf
import com.example.workouttracker.domain.mo.compatibility.withInheritedTrackerConfigurations
import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import com.example.workouttracker.domain.mo.compatibility.InheritedMoodReport
import com.example.workouttracker.domain.mo.compatibility.InheritedStreakReport
import com.example.workouttracker.domain.mo.compatibility.InheritedDayReport
import com.example.workouttracker.domain.mo.compatibility.inheritedReport

import com.example.workouttracker.domain.questions.eatingEntry
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.GymActivityId
import com.example.workouttracker.domain.activity.SeededActivityRules
import com.example.workouttracker.domain.activity.DefaultActivityDaysAWeek
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.SeededGymDayIds
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.care.everyCareAction
import com.example.workouttracker.domain.care.withCare
import com.example.workouttracker.domain.compatibility.WeightRewards
import com.example.workouttracker.domain.compatibility.EatingRewards
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.compatibility.SupplementsChecklistId
import com.example.workouttracker.domain.supplement.supplementDay
import com.example.workouttracker.domain.supplement.withSupplements
import com.example.workouttracker.domain.measurement.MeasurementRecord
import java.time.LocalDate
import com.example.workouttracker.domain.time.DefaultWeeklyGoalMinutes
import com.example.workouttracker.domain.time.TokenCover

internal fun only(vararg needs: MoNeed) = showingOnly(*needs)

/** The smallest record that keeps [need] on [day]. For Eating that is a reflection about the day before. */
internal fun AppState.keeping(need: MoNeed, day: LocalDate, at: Long = 0): AppState {
    val dateKey = day.toString()
    return when (need) {
        MoNeed.Activity -> copy(activityEntries = activityEntries + activity("Running", day, at))
        MoNeed.Reading -> copy(timeLogs = timeLogs + reading(day, at))
        MoNeed.Weight -> copy(measurementLogs = (recordsOf("weight") + MeasurementRecord(dateKey, timestampMillis = at, value = 80.0)).asMeasurementLogs("weight"))
        MoNeed.Supplements -> withSupplements(
            *checklistDays.filter { it.checklistId == SupplementsChecklistId }.toTypedArray(),
            supplementDay(dateKey, timestampMillis = at, takenIds = setOf("any")),
        ).let { it.copy(checklistDays = it.checklistDays.distinct()) }
        MoNeed.Care -> withCare(*everyCareAction(dateKey, timestampMillis = at))
        MoNeed.Eating -> copy(questionEntries = questionEntries + reflectionAbout(day.minusDays(1), at))
    }.withInheritedTrackerConfigurations()
}

/**
 * The mood without the checkpoints it read, which a state that was never loaded through the
 * repository does not hold: what it judges is the same.
 */
internal fun MoMood.withoutCheckpoints(): MoMood = copy(trackerCheckpoints = emptyMap())

/**
 * Records that keep [need] on every `x` of [weeks], written as seven characters with Monday first.
 * The first week starts on [firstMonday], and the last one may stop short.
 */
internal fun AppState.keepingWeeks(need: MoNeed, firstMonday: LocalDate, vararg weeks: String): AppState =
    weeks.joinToString(separator = "").foldIndexed(this) { index, state, mark ->
        if (mark == 'x') state.keeping(need, firstMonday.plusDays(index.toLong())) else state
    }

/** The streak's numbers without its week, for tests that are not about the week. */
internal data class Tally(val days: Int, val freezes: Int, val freezeCapacity: Int)

internal fun MoStreak.tally() = Tally(days, freezes, freezeCapacity)

internal fun activity(name: String, day: LocalDate, at: Long = 0) = ActivityEntry(
    id = "$name-$day",
    activityId = activityIdOf(name),
    name = name,
    dateKey = day.toString(),
    timestampMillis = at,
)

/** The activity a version 29 entry of [name] lands under: the seeded one of that name, else one the configuration never knew. */
internal fun activityIdOf(name: String): String = SeededActivityRules.activityNamed(name)?.id ?: name

internal fun workout(plannedDay: String, day: LocalDate, at: Long = 0, sets: Int = 1) = WorkoutSession(
    id = "workout-$day",
    activityId = GymActivityId,
    day = plannedDay,
    dateKey = day.toString(),
    timestampMillis = at,
    exerciseLogs = listOf(ExerciseLog("exercise", "Exercise", sets = List(sets) { LoggedSet(40.0, 8.0) })),
)

internal fun reading(day: LocalDate, at: Long = 0) = TimeLog(ReadingTimeId, dateKey = day.toString(), timestampMillis = at)

internal fun reflectionAbout(day: LocalDate, at: Long = 0) =
    eatingEntry(id = day.toString(), dateKey = day.toString(), timestampMillis = at, quality = "good")

/**
 * The mood as text without the configurations it was read against, the checklists, the
 * checkpoints, the number of logged days Weight's week takes, the kept days a token of Eating
 * takes, the goal and the gap of Reading's week and the active days a token of Activity takes.
 * Those are inputs, not outcomes, and the recorded outcome digests predate moods carrying them.
 */
internal fun MoMood.outcome(): String = inheritedReport().outcome()

internal fun InheritedMoodReport.outcome(): String = outcomeText(copy(
    checklists = emptyMap(), checkpoints = emptyMap(),
    weightDaysForATokenYesterday = WeightRewards.keptDaysInWeek.current,
    eatingDaysForATokenYesterday = EatingRewards.everyKeptDays.current,
    readingGapMinutes = TokenCover.QuarterOfGoal.minutesOf(DefaultWeeklyGoalMinutes),
    readingGoalForATokenYesterday = DefaultWeeklyGoalMinutes,
    activityDaysForATokenYesterday = DefaultActivityDaysAWeek,
))

/**
 * An outcome as text, without the fields that only configured checklists fill while none takes
 * part in rewards, that Substances fills only while its logging counts toward the streak, that
 * a logged view a person made fills only while it counts toward it, that only Weight's 7-day
 * average sets, or that only a miss fills. The recorded digests predate those fields; an empty
 * one adds nothing. A made time view's stand last in their outcomes, a made questions view's
 * before them and a made measurement's before those, so they are taken out first, in that order.
 */
internal fun outcomeText(outcome: Any?): String = historicalOutcome(outcome).toString()
    .replace("InheritedMoodReport(", "MoMood(")
    .replace("InheritedStreakReport(", "MoStreak(")
    .replace("InheritedDayReport(", "MoDayRecord(")
    .replace(", labels={}, content={})", ")")
    .replace("CheckpointPart(checkpointId=morning)", "MORNING")
    .replace("CheckpointPart(checkpointId=evening)", "EVENING")
    // Preserve the frozen report's model names while comparing every generic chart value.
    .replace("MeasurementHistoryWindow(", "WeightHistoryWindow(")
    .replace("MeasurementHistoryDay(", "WeightHistoryDay(")
    .replace("trendValue=", "trendKg=")
    .replace(Regex("""MeasurementRecord\(([^()]*)\)""")) {
        "WeightLog(${it.groupValues[1].replace("value=", "weightKg=")})"
    }
    .replace(Regex("""MeasurementAverage\(([^()]*)\)""")) {
        "WeightAverage(${it.groupValues[1].replace("average=", "averageKg=")})"
    }
    .replace(Regex("""MeasurementTrendPoint\(([^()]*)\)""")) {
        "WeightTrendPoint(${it.groupValues[1].replace("value=", "weightKg=")})"
    }
    .replace(", checkpoints={}", "")
    .replace(", weightDaysForATokenYesterday=6, eatingDaysForATokenYesterday=7, readingGapMinutes=15, readingGoalForATokenYesterday=60, activityDaysForATokenYesterday=5)", ")")
    .replace(", averaged=false)", ")")
    // Statistics names a kind by its configuration now; the report keeps the fixed keys it was frozen with.
    .replace(Regex("""ActivityKind\(activityId=(gym|bouldering|cycling|jogging), label=[^)]*\)""")) { "ActivityKind(key=${it.groupValues[1].uppercase()}, customName=null)" }
    .replace(Regex("""ActivityKind\(activityId=[^,]*, label=([^)]*)\)"""), "ActivityKind(key=CUSTOM, customName=$1)")
    .replace(", missedParts={}", "")
    .replace(", time={})", ")")
    .replace(", coveredTime=[])", ")")
    .replace(", questions={})", ")")
    .replace(", coveredQuestions=[])", ")")
    .replace(", measurements={})", ")")
    .replace(", coveredMeasurements=[])", ")")
    .replace(", checklists={})", ")")
    .replace(", checklistPasses={}, checklistPassCapacity={}, checklistNextToken={}, checklistsOpenToday=[], checklistsTakingPart=[], substances=null)", ")")
    .replace(", substances=null)", ")")
    .replace(", coveredChecklists=[])", ")")

/** Historical strings are built from explicit reports; canonical classes carry no old fields. */
private fun historicalOutcome(outcome: Any?): Any? = when (outcome) {
    is MoMood -> outcome.inheritedReport()
    is MoStreak -> outcome.inheritedReport()
    is MoDayRecord -> outcome.inheritedReport()
    is List<*> -> outcome.map(::historicalOutcome)
    is Set<*> -> outcome.mapTo(linkedSetOf(), ::historicalOutcome)
    is Map<*, *> -> outcome.mapValues { historicalOutcome(it.value) }
    else -> outcome
}
