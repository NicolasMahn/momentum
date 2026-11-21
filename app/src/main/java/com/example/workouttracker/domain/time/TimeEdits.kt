package com.example.workouttracker.domain.time


import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.StarterDayCheckpoint
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.endingBefore
import com.example.workouttracker.domain.checklist.freshId
import com.example.workouttracker.domain.checklist.reachBefore
import com.example.workouttracker.domain.checklist.starterHue
import com.example.workouttracker.domain.checklist.takingPartFrom
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.LocalDate
import kotlin.random.Random
import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.domain.checklist.leavingOn

/**
 * Typed timer changes applied on their saving habit day. Appearance and reminders are current
 * settings; goals and reward revisions retain the rules that judged earlier weeks.
 */
sealed interface TimeEdit {
    data class Rename(val label: String) : TimeEdit

    data class SetIcon(val icon: ViewIcon) : TimeEdit

    data class SetHue(val hue: ViewHue) : TimeEdit

    /** Mo's flavor, which decides what he does during the view's session; null for Plain. */
    data class SetMotif(val motif: MoMotif?) : TimeEdit

    /** What a week asks, in whole minutes, from today on. */
    data class SetGoal(val minutes: Int) : TimeEdit

    /** Counts toward the streak from today, with the bank it had or the one every time view starts with. */
    data object JoinRewards : TimeEdit

    /** Stops counting after yesterday; the weeks it took part in keep their judgments, and the bank its size and steps. */
    data object LeaveRewards : TimeEdit

    /** The passes the bank holds, from today on. */
    data class SetBankSize(val maxPasses: Int) : TimeEdit

    /** The steps a week earns by, as a whole list, from the week today is in. */
    data class SetTokenSteps(val steps: TokenSteps) : TimeEdit

    /** What one token covers of a week that falls short, from the week today is in. */
    data class SetTokenCover(val cover: TokenCover) : TimeEdit

    /** Not due or still due on an away day, from today on; a time view has no count a week to lower. */
    data class SetWhenAway(val whenAway: WhenAway) : TimeEdit

    /** Whether this timer reminds at its configured checkpoint. */
    data class SetRemind(val remind: Boolean) : TimeEdit

    /** When that reminder comes, in the three forms a checkpoint's time has. */
    data class SetReminderTime(val at: CheckpointTime) : TimeEdit
}

/**
 * Applies an edit from [today], rejecting invalid goals, unsupported motifs and unavailable
 * bank operations. [firstRecordedDay] closes a bank's implicit historical participation, and
 * minutes recorded before [today] keep the goal their weeks were shown against, judged or not. A
 * goal that already holds history keeps it, so a settings draft replayed without the records
 * reads the same goal as the one stored.
 */
fun TimeConfiguration.applyEdit(edit: TimeEdit, today: LocalDate, firstRecordedDay: LocalDate? = null): TimeConfiguration = when (edit) {
    is TimeEdit.Rename -> copy(label = edit.label)
    is TimeEdit.SetIcon -> copy(icon = edit.icon)
    is TimeEdit.SetHue -> copy(hue = edit.hue)
    is TimeEdit.SetMotif -> copy(mo = mo.copy(motif = edit.motif))
    is TimeEdit.SetGoal -> copy(goal = goal.changedFrom(today, edit.minutes, dated = judgedBefore(today) || goal.isDated || firstRecordedDay?.isBefore(today) == true))
    TimeEdit.JoinRewards -> copy(rewards = rewards.joining(today))
    TimeEdit.LeaveRewards -> copy(rewards = rewards?.leaving(today, wholeHistoryFrom = firstRecordedDay))
    is TimeEdit.SetBankSize -> ownBank().let { bank ->
        copy(rewards = bank.copy(maxPasses = bank.maxPasses.changedFrom(today, edit.maxPasses, dated = judgedBefore(today))))
    }
    is TimeEdit.SetTokenSteps -> ownBank().let { bank ->
        copy(rewards = bank.copy(tokenSteps = bank.tokenSteps.changedFrom(today, edit.steps, dated = judgedBefore(today))))
    }
    is TimeEdit.SetTokenCover -> ownBank().let { bank ->
        copy(rewards = bank.copy(tokenCover = bank.tokenCover.changedFrom(today, edit.cover, dated = judgedBefore(today))))
    }
    is TimeEdit.SetWhenAway -> copy(whenAway = whenAway.changedFrom(today, edit.whenAway))
    is TimeEdit.SetRemind -> copy(checkpoint = checkpoint.copy(remind = edit.remind))
    is TimeEdit.SetReminderTime -> copy(checkpoint = checkpoint.copy(at = edit.at))
}

private fun TimeConfiguration.ownBank(): TimeRewards = requireNotNull(rewards) { "Time view $id has no bank." }

/** Whether these reward rules have already judged a day before [today]. */
private fun TimeConfiguration.judgedBefore(today: LocalDate): Boolean = rewards?.let { it.periods.reachBefore(today) } == true

/**
 * This bank taking part from [today]: the bank every time view starts with and a first period
 * while there is none yet, and otherwise the bank as it is with its periods taking part.
 */
internal fun TimeRewards?.joining(today: LocalDate): TimeRewards = when {
    this == null -> TimeRewards(maxPasses = DefaultTimeBankSize, periods = listOf(RewardPeriod(today)))
    else -> copy(periods = periods?.takingPartFrom(today))
}

/**
 * This bank out from [today]: its open period ends yesterday, or goes if it only began today, and
 * a bank without periods, which took part since [wholeHistoryFrom] or on no day yet while that is
 * unknown, becomes its one period, ended the same way. The size and the steps stay for when it
 * joins again, even with no period left.
 */
internal fun TimeRewards.leaving(today: LocalDate, wholeHistoryFrom: LocalDate? = null): TimeRewards =
    // An implicit history already judged its first partial week. Materializing it must keep that Sunday eligible.
    copy(periods = periods.leavingOn(today, wholeHistoryFrom?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))))

/**
 * [edits] applied one after another from [today]: what the page shows over the latest stored
 * configuration, and what Save stores on the day it is pressed. An edit the configuration does not
 * take is left out, so what is shown is what is saved.
 */
fun TimeConfiguration.applyingEdits(edits: List<TimeEdit>, today: LocalDate, firstRecordedDay: LocalDate? = null): TimeConfiguration =
    edits.fold(this) { draft, edit -> runCatching { draft.applyEdit(edit, today, firstRecordedDay) }.getOrDefault(draft) }

/** The stored configuration [viewId] with [edits] applied from [today], leaving the streak from its first log; a state without it is left as it is. */
fun AppState.applyingTimeEdits(viewId: String, edits: List<TimeEdit>, today: LocalDate): AppState {
    val current = time.firstOrNull { it.id == viewId } ?: return this
    val updated = current.applyingEdits(edits, today, firstLoggedDay(viewId))
    return if (updated == current) this else copy(time = time.map { if (it.id == viewId) updated else it })
}

/** The earliest recorded date, where a bank without explicit periods began. */
fun AppState.firstLoggedDay(viewId: String): LocalDate? =
    timeLogs.filter { it.viewId == viewId }.mapNotNull { parseDateKey(it.dateKey) }.minOrNull()

/**
 * Every ID a time view is named by in this state: a configuration's, and a stored log's, whether a
 * configuration knows its view or not. A log of a view nothing knows is kept as it is, so a new
 * view under its ID would be handed minutes that were never its own.
 */
val AppState.namedTimeIds: Set<String>
    get() = time.mapTo(HashSet()) { it.id } + timeLogs.map { it.viewId }

/** An ID for a time view a person makes, such as `tim_k2p9`: one nothing names yet, the [hidden] entries of the visibility settings included. */
fun AppState.freshTimeId(hidden: Set<String> = emptySet(), random: Random = Random.Default): String =
    freshId(MadeTimeIdPrefix, namedTimeIds + hidden, random)

/**
 * A new time view, as its setup made it. Throws for an ID that anything names by now: a setup
 * keeps the ID it was started with, so a creation that is retried or restored is refused rather
 * than made twice or given another ID.
 *
 * An entry among the [hidden] views of the visibility settings names an ID too. A delete can leave
 * one behind, and a view made under it would start out hidden.
 */
fun AppState.addTime(view: TimeConfiguration, hidden: Set<String> = emptySet()): AppState {
    require(view.id !in namedTimeIds && view.id !in hidden) { "Time view ${view.id} is named already." }
    return copy(time = time + view)
}

/**
 * A time view as a person starts one: the timer, Mo as he is, an hour a week, outside the streak,
 * still due on holiday, its reminder off at 09:00, in the first starter color no checklist, no
 * measurement, no questions view and no time view wears ([starterHue]). Its ID is one nothing
 * names yet ([freshTimeId]). It is a one-time copy; everything else is set with [TimeEdit]s.
 */
fun AppState.starterTime(label: String, hidden: Set<String> = emptySet(), random: Random = Random.Default): TimeConfiguration {
    val id = freshTimeId(hidden, random)
    return TimeConfiguration(
        id = id,
        label = label,
        icon = ViewIcon.Timer,
        hue = starterHue(worn = checklists.mapTo(HashSet()) { it.hue } + measurements.map { it.hue } + questions.map { it.hue } + time.map { it.hue }),
        goal = Dated.always(DefaultWeeklyGoalMinutes),
        rewards = null,
        whenAway = MadeTimeWhenAway,
        checkpoint = StarterDayCheckpoint,
    )
}

/**
 * Removes a timer with all its logs, including future-dated records. The caller also removes
 * its reminder and visibility entries; any running session can no longer credit this timer.
 */
fun AppState.deleteTime(viewId: String): AppState {
    require(time.any { it.id == viewId }) { "No time view $viewId." }
    return copy(time = time.filterNot { it.id == viewId }, timeLogs = timeLogs.filterNot { it.viewId == viewId })
}

/** How many days hold a log of [viewId], named before it is deleted; a view has one log per date. */
fun AppState.loggedDaysOf(viewId: String): Int = timeLogs.count { it.viewId == viewId }
