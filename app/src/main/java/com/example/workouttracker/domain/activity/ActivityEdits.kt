package com.example.workouttracker.domain.activity

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.routine.ExerciseWeight
import com.example.workouttracker.domain.activity.routine.Routine
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.freshId
import java.time.LocalDate
import kotlin.random.Random
import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.domain.checklist.joiningOn
import com.example.workouttracker.domain.checklist.leavingOn

/**
 * Every change a person can make to Activity, as typed operations applied on the habit day they
 * are saved, as a time view's [com.example.workouttracker.domain.time.TimeEdit]s are. None
 * reaches a day before [applyEdit]'s `today`:
 *
 * - The name, the look, the activities, their routines and whether a duration is asked for judge
 *   no day, so they apply everywhere at once. Removing an activity, a routine, a day or an exercise rewrites no record: what was
 *   recorded for it stays stored under its name.
 * - The week, the holiday rule with its count, the bank's size and its steps judge weeks, so
 *   each changes from today on and keeps its earlier value for earlier days
 *   ([com.example.workouttracker.domain.checklist.Dated]). Activity's weeks have always been
 *   judged, so changes to its earning rules are always dated.
 * - Taking part in the streak is dated the same way: joining counts from today and leaving after
 *   yesterday, closing the period the bank has been in since the first record, and each day is
 *   judged by whether it took part, so the days before keep their judgments and the free days
 *   they used.
 *
 * A revision that starts today was never used to judge a day, so a second change on the same day
 * replaces it, and a change taken back leaves no revision.
 */
sealed interface ActivityEdit {
    data class Rename(val label: String) : ActivityEdit

    data class SetIcon(val icon: ViewIcon) : ActivityEdit

    data class SetHue(val hue: ViewHue) : ActivityEdit

    /** Whether logging an activity asks how long it took. Turning it off removes the field and no recorded duration. */
    data class SetLogsDuration(val logsDuration: Boolean) : ActivityEdit

    /**
     * The week's chosen days with what is planned on each, from today on. Fewer days on holiday
     * is for more than one day a week, so one day turns it into still due in the same edit, and a
     * count the days no longer allow is clamped below them, as a checklist item's is; more days
     * leave less room for token steps, so the steps beyond it are dropped from today in the same
     * edit, with earlier revisions untouched.
     */
    data class SetWeek(val plan: WeekPlan) : ActivityEdit

    /** The steps a week earns by, as a whole list, from today on; refused where they outrun the room the week's days leave. */
    data class SetTokenSteps(val steps: ActivityTokenSteps) : ActivityEdit

    /** Still due, not due or fewer days on an away day, from today on; fewer days needs more than one day a week. */
    data class SetWhenAway(val whenAway: WhenAway) : ActivityEdit

    /** What a week away asks while the week lowers, from today on: one day to a day below the days a week. */
    data class SetAwayDays(val days: Int) : ActivityEdit

    /** The passes the bank holds, from today on. */
    data class SetBankSize(val maxPasses: Int) : ActivityEdit

    /** Counts toward the streak from today, with the bank as it is. */
    data object JoinRewards : ActivityEdit

    /** Stops counting after yesterday; each day it took part keeps its judgment, and the bank its size and steps. */
    data object LeaveRewards : ActivityEdit

    /** One activity more, last in the list, under an ID nothing names yet ([freshActivityId]). */
    data class AddActivity(val activity: ListedActivity) : ActivityEdit

    /** A name no other activity has, removed ones included, by [activityNameKey]. */
    data class RenameActivity(val activityId: String, val label: String) : ActivityEdit

    data class SetActivityIcon(val activityId: String, val icon: ViewIcon) : ActivityEdit

    /** One place up among the activities still offered; the first cannot move. */
    data class MoveActivityUp(val activityId: String) : ActivityEdit

    /** Marks it removed: no longer offered, its entries and sessions kept under its name, and the days planned for it plan any activity from today. */
    data class RemoveActivity(val activityId: String) : ActivityEdit

    /**
     * One day more, last in the activity's routine, under IDs nothing names yet
     * ([freshRoutineDayId], [freshExerciseId]). The first day of an activity without a routine,
     * or whose routine was removed, is how it gets one.
     */
    data class AddRoutineDay(val activityId: String, val day: RoutineDay) : ActivityEdit

    data class RenameRoutineDay(val dayId: String, val label: String) : ActivityEdit

    /** One place up among the days of its routine still offered; the first cannot move. */
    data class MoveRoutineDayUp(val dayId: String) : ActivityEdit

    /** Marks it removed; the last day offered of a routine stays, and [RemoveRoutine] takes the whole. */
    data class RemoveRoutineDay(val dayId: String) : ActivityEdit

    /** Marks every day of the activity's routine removed and keeps them, so an old session still finds its day's name. */
    data class RemoveRoutine(val activityId: String) : ActivityEdit

    /** One exercise more, last in its day, under an ID nothing names yet. */
    data class AddExercise(val dayId: String, val exercise: RoutineExercise) : ActivityEdit

    data class RenameExercise(val exerciseId: String, val label: String) : ActivityEdit

    /** One place up among the exercises of its day still offered; the first cannot move. */
    data class MoveExerciseUp(val exerciseId: String) : ActivityEdit

    /** Marks it removed; a day's last exercise offered stays. */
    data class RemoveExercise(val exerciseId: String) : ActivityEdit

    /** The sets, the rep range, the weight with its start and step or none, and the starting reps of an exercise. */
    data class SetExerciseNumbers(
        val exerciseId: String,
        val sets: Int,
        val reps: IntRange,
        val weight: ExerciseWeight?,
        val startReps: Int,
    ) : ActivityEdit
}

/**
 * [edit] applied to this configuration from the habit day [today]. Throws for an edit it cannot
 * take: a blank or taken name, a number outside its limits, fewer days at one day a week, an ID
 * it has already or does not have, moving what is first, removing the last day of a routine or a
 * day's last exercise, or removing a routine the activity does not offer. [firstRecordedDay]
 * matters to one edit: leaving the streak closes the period a bank without periods has been in
 * since the first entry or session.
 */
fun ActivityConfiguration.applyEdit(edit: ActivityEdit, today: LocalDate, firstRecordedDay: LocalDate? = null): ActivityConfiguration = when (edit) {
    is ActivityEdit.Rename -> copy(label = edit.label)
    is ActivityEdit.SetIcon -> copy(icon = edit.icon)
    is ActivityEdit.SetHue -> copy(hue = edit.hue)
    is ActivityEdit.SetLogsDuration -> copy(logsDuration = edit.logsDuration)
    is ActivityEdit.SetWeek -> edit.plan.size.let { days ->
        copy(
            week = week.changedFrom(today, edit.plan),
            whenAway = if (days > 1) whenAway else whenAway.changedFrom(today, whenAway.current.takeUnless { it == WhenAway.LowerTarget } ?: WhenAway.Due),
            awayDays = if (days > 1 && awayDays.current >= days) awayDays.changedFrom(today, days - 1) else awayDays,
            rewards = rewards.copy(tokenSteps = rewards.tokenSteps.changedFrom(today, rewards.tokenSteps.current.fitting(days))),
        )
    }
    is ActivityEdit.SetTokenSteps -> copy(rewards = rewards.copy(tokenSteps = rewards.tokenSteps.changedFrom(today, edit.steps)))
    is ActivityEdit.SetWhenAway -> copy(whenAway = whenAway.changedFrom(today, edit.whenAway))
    is ActivityEdit.SetAwayDays -> {
        require(edit.days < week.current.size) { "A week away asks fewer days than the ${week.current.size} a week." }
        copy(awayDays = awayDays.changedFrom(today, edit.days))
    }
    is ActivityEdit.SetBankSize -> copy(rewards = rewards.copy(maxPasses = rewards.maxPasses.changedFrom(today, edit.maxPasses)))
    ActivityEdit.JoinRewards -> copy(rewards = rewards.copy(periods = rewards.periods.joiningOn(today)))
    ActivityEdit.LeaveRewards -> copy(rewards = rewards.copy(periods = rewards.periods.leavingOn(today, firstRecordedDay)))
    is ActivityEdit.AddActivity -> {
        require(activities.none { it.id == edit.activity.id }) { "Activity ${edit.activity.id} is listed already." }
        copy(activities = activities + edit.activity)
    }
    is ActivityEdit.RenameActivity -> withActivity(edit.activityId) { it.copy(label = edit.label) }
    is ActivityEdit.SetActivityIcon -> withActivity(edit.activityId) { it.copy(icon = edit.icon) }
    is ActivityEdit.MoveActivityUp -> copy(activities = activities.movedUp(edit.activityId, { it.id }, { it.removed }))
    is ActivityEdit.RemoveActivity -> withActivity(edit.activityId) {
        require(!it.removed) { "Activity ${edit.activityId} is removed already." }
        it.copy(removed = true)
    }.let { it.copy(week = it.week.changedFrom(today, it.week.current.withoutActivity(edit.activityId))) }
    is ActivityEdit.AddRoutineDay -> withActivity(edit.activityId) { it.copy(routine = Routine(it.routine?.days.orEmpty() + edit.day)) }
    is ActivityEdit.RenameRoutineDay -> withDay(edit.dayId) { it.copy(label = edit.label) }
    is ActivityEdit.MoveRoutineDayUp -> withRoutineOf(edit.dayId) { it.copy(days = it.days.movedUp(edit.dayId, { day -> day.id }, { day -> day.removed })) }
    is ActivityEdit.RemoveRoutineDay -> withRoutineOf(edit.dayId) { routine ->
        val day = requireNotNull(routine.day(edit.dayId))
        require(!day.removed) { "Routine day ${edit.dayId} is removed already." }
        require(routine.retainedDays.size > 1) { "The last day of a routine stays." }
        routine.copy(days = routine.days.map { if (it.id == edit.dayId) it.copy(removed = true) else it })
    }
    is ActivityEdit.RemoveRoutine -> withActivity(edit.activityId) { activity ->
        val routine = activity.routine?.takeIf { it.isOffered }
        requireNotNull(routine) { "Activity ${edit.activityId} offers no routine." }
        activity.copy(routine = routine.copy(days = routine.days.map { it.copy(removed = true) }))
    }
    is ActivityEdit.AddExercise -> withDay(edit.dayId) { it.copy(exercises = it.exercises + edit.exercise) }
    is ActivityEdit.RenameExercise -> withExercise(edit.exerciseId) { it.copy(label = edit.label) }
    is ActivityEdit.MoveExerciseUp -> withDayOf(edit.exerciseId) { it.copy(exercises = it.exercises.movedUp(edit.exerciseId, { exercise -> exercise.id }, { exercise -> exercise.removed })) }
    is ActivityEdit.RemoveExercise -> withDayOf(edit.exerciseId) { day ->
        require(day.retainedExercises.size > 1) { "A day's last exercise stays." }
        day.copy(exercises = day.exercises.map { if (it.id == edit.exerciseId) it.copy(removed = true) else it })
    }
    is ActivityEdit.SetExerciseNumbers -> withExercise(edit.exerciseId) {
        it.copy(sets = edit.sets, reps = edit.reps, weight = edit.weight, startReps = edit.startReps)
    }
}

private fun ActivityConfiguration.withActivity(activityId: String, change: (ListedActivity) -> ListedActivity): ActivityConfiguration {
    require(activities.any { it.id == activityId }) { "No activity $activityId." }
    return copy(activities = activities.map { if (it.id == activityId) change(it) else it })
}

/** The routine that has the day [dayId], whichever activity's it is, through [change]. */
private fun ActivityConfiguration.withRoutineOf(dayId: String, change: (Routine) -> Routine): ActivityConfiguration {
    val activity = requireNotNull(activityOfDay(dayId)) { "No routine day $dayId." }
    return withActivity(activity.id) { it.copy(routine = change(requireNotNull(it.routine))) }
}

private fun ActivityConfiguration.withDay(dayId: String, change: (RoutineDay) -> RoutineDay): ActivityConfiguration =
    withRoutineOf(dayId) { routine -> routine.copy(days = routine.days.map { if (it.id == dayId) change(it) else it }) }

private fun ActivityConfiguration.withDayOf(exerciseId: String, change: (RoutineDay) -> RoutineDay): ActivityConfiguration =
    withDay(requireNotNull(activityOfExercise(exerciseId)?.routine?.dayOf(exerciseId)) { "No exercise $exerciseId." }.id, change)

private fun ActivityConfiguration.withExercise(exerciseId: String, change: (RoutineExercise) -> RoutineExercise): ActivityConfiguration {
    val exercise = requireNotNull(routineExercise(exerciseId)) { "No exercise $exerciseId." }
    require(!exercise.removed) { "Exercise $exerciseId is removed." }
    return withDayOf(exerciseId) { day -> day.copy(exercises = day.exercises.map { if (it.id == exerciseId) change(it) else it }) }
}

/**
 * This list with the entry [id] names one place up among the entries not [removed]: it moves to
 * just before the nearest earlier one still offered, over any removed ones between. Throws for an
 * entry that is not there or has none before it.
 */
private fun <T> List<T>.movedUp(id: String, idOf: (T) -> String, removed: (T) -> Boolean): List<T> {
    val index = indexOfFirst { idOf(it) == id }
    require(index >= 0) { "No entry $id." }
    val earlier = (index - 1 downTo 0).firstOrNull { !removed(this[it]) }
    requireNotNull(earlier) { "Entry $id is first already." }
    return toMutableList().also { it.add(earlier, it.removeAt(index)) }
}

/**
 * [edits] applied one after another from [today]: what the page shows over the latest stored
 * configuration, and what Save stores on the day it is pressed. An edit the configuration does not
 * take is left out, so what is shown is what is saved.
 */
fun ActivityConfiguration.applyingEdits(edits: List<ActivityEdit>, today: LocalDate, firstRecordedDay: LocalDate? = null): ActivityConfiguration =
    edits.fold(this) { draft, edit -> runCatching { draft.applyEdit(edit, today, firstRecordedDay) }.getOrDefault(draft) }

/** The stored configuration with [edits] applied from [today], leaving the streak from the first record; a state without one is left as it is. */
fun AppState.applyingActivityEdits(edits: List<ActivityEdit>, today: LocalDate): AppState {
    val current = activity ?: return this
    val updated = current.applyingEdits(edits, today, firstActiveDay)
    return if (updated == current) this else copy(activity = updated)
}

/** The first day an entry or a session is of, or null while there is none: where a bank without periods began, as the walk reads Activity's. */
val AppState.firstActiveDay: LocalDate?
    get() = (activityEntries.map { it.dateKey } + sessions.map { it.dateKey }).mapNotNull(::parseDateKey).minOrNull()

/**
 * Every ID Activity's parts are named by in this state, which a new one must differ from: the
 * configuration's, removed ones included, and the records'. An entry of an activity nothing knows
 * is kept as it is, so a new activity under its ID would be handed days that were never its own;
 * a session's day and a log's exercise no routine names are kept the same way.
 */
data class NamedActivityIds(
    val activities: Set<String> = emptySet(),
    val days: Set<String> = emptySet(),
    val exercises: Set<String> = emptySet(),
)

val AppState.namedActivityIds: NamedActivityIds
    get() = NamedActivityIds(
        activities = activityRules.activities.mapTo(HashSet()) { it.id } + activityEntries.map { it.activityId } + sessions.map { it.activityId },
        days = activityRules.activities.flatMapTo(HashSet()) { activity -> activity.routine?.days.orEmpty().map { it.id } } + sessions.map { it.day },
        exercises = activityRules.activities.flatMapTo(HashSet()) { activity -> activity.routine?.days.orEmpty().flatMap { day -> day.exercises.map { it.id } } } +
            sessions.flatMap { session -> session.exerciseLogs.map { it.exerciseId } },
    )

/** An ID for an activity a person adds, such as `act_k2p9`: one nothing in [taken] names. */
fun freshActivityId(taken: Collection<String>, random: Random = Random.Default): String = freshId(ActivityIdPrefix, taken, random)

/** An ID for a routine day a person adds, such as `day_k2p9`: one nothing in [taken] names. */
fun freshRoutineDayId(taken: Collection<String>, random: Random = Random.Default): String = freshId(RoutineDayIdPrefix, taken, random)

/** An ID for an exercise a person adds, such as `ex_k2p9`: one nothing in [taken] names. */
fun freshExerciseId(taken: Collection<String>, random: Random = Random.Default): String = freshId(ExerciseIdPrefix, taken, random)

/** An ID for an activity the screen adds under a new name: one nothing this state names, nor [alsoTaken], the ones it drew since. */
fun AppState.freshActivityId(alsoTaken: Collection<String> = emptySet(), random: Random = Random.Default): String =
    freshId(ActivityIdPrefix, namedActivityIds.activities + alsoTaken, random)

private const val ActivityIdPrefix = "act"
private const val RoutineDayIdPrefix = "day"
private const val ExerciseIdPrefix = "ex"

/** The icon an activity saved under a new name wears: Other's bolt, which reads as effort of some other kind. */
val OtherActivityIcon = ViewIcon.Bolt
