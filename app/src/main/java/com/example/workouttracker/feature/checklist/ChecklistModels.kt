package com.example.workouttracker.feature.checklist

import com.example.workouttracker.core.time.calendarWeeksWindow
import com.example.workouttracker.core.time.requireValidDateKey
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistProgress
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.DayRecord
import com.example.workouttracker.domain.checklist.WeekItemProgress
import com.example.workouttracker.domain.checklist.exceptionsOnlyTicks
import com.example.workouttracker.domain.checklist.merged
import com.example.workouttracker.domain.checklist.tickOf
import com.example.workouttracker.domain.checklist.ChecklistRules
import com.example.workouttracker.domain.checklist.CheckpointRules
import com.example.workouttracker.domain.checklist.dueOn
import com.example.workouttracker.domain.checklist.firstDay
import com.example.workouttracker.domain.checklist.progressOf
import com.example.workouttracker.domain.checklist.recordDayFor
import com.example.workouttracker.domain.checklist.recordOn
import com.example.workouttracker.domain.checklist.recordableOn
import com.example.workouttracker.domain.checklist.ticks
import com.example.workouttracker.domain.holiday.HolidaySchedule

/**
 * How one item stands in a day's draft: a tick asked for and not given, a tick recorded, a check
 * assumed (the item logs only misses and nothing is recorded), or a miss marked. The box is
 * checked while ticked or assumed.
 */
internal enum class ItemMark {
    Open,
    Ticked,
    Assumed,
    Missed,
    ;

    val isChecked: Boolean get() = this == Ticked || this == Assumed
}

/**
 * How [tick] stands in this record; [logsOnlyMisses] says whether its item is kept unless missed
 * today. A miss is said as the fact it is, even where it judges nothing, as for an item changed
 * the same day to one that ticks; ticking that item replaces it.
 */
internal fun DayRecord.markOf(tick: String, logsOnlyMisses: Boolean): ItemMark = when {
    tick in ticks -> ItemMark.Ticked
    tick in misses -> ItemMark.Missed
    logsOnlyMisses -> ItemMark.Assumed
    else -> ItemMark.Open
}

/**
 * [tick] toggled in a draft. An item that logs only misses swings between done and missed, done
 * being its stored tick while it had one ([storedTick]) and assumed otherwise, so taking a miss
 * back brings a recorded tick back; any other item ticks, and a tick replaces a miss left on it.
 */
internal fun DayRecord.toggling(tick: String, logsOnlyMisses: Boolean, storedTick: Boolean): DayRecord = when {
    !logsOnlyMisses -> if (tick in ticks) copy(ticks = ticks - tick) else DayRecord(ticks + tick, misses - tick)
    tick in misses -> DayRecord(if (storedTick) ticks + tick else ticks, misses - tick)
    else -> DayRecord(ticks - tick, misses + tick)
}

/**
 * The form's draft for one day: what is due, what can be recorded (due or not, which is what the
 * form lists), what is saved among it, and what is picked now.
 */
internal data class ChecklistSelection(
    val progress: ChecklistProgress,
    val recordable: ChecklistRules,
    /** What is stored among what can be recorded, which a save replaces. */
    val persisted: DayRecord,
    val selected: DayRecord,
    val hasPersistedRecord: Boolean,
) {
    val dueTicks: Set<String> get() = progress.dueTicks
    val hasChanges: Boolean get() = selected != persisted

    /** Whether saving the draft records a tick that is not stored yet. */
    val addsATick: Boolean get() = !persisted.ticks.containsAll(selected.ticks)

    fun markOf(tick: String): ItemMark = selected.markOf(tick, tick in progress.exceptionsOnly)

    fun toggle(tick: String): ChecklistSelection {
        require(tick in recordable.ticks) { "Only items that can be recorded on ${progress.date} can be picked." }
        return copy(selected = selected.toggling(tick, tick in progress.exceptionsOnly, tick in persisted.ticks))
    }
}

/**
 * The day the screen opens on: the one the checklist asks about on [todayKey], yesterday for a
 * checklist about yesterday, whether or not anything is due there, as its reminder and the streak
 * ask about that day either way. Only a day before the checklist existed, as on the first day of
 * one about yesterday, was never asked for anything, so the screen opens on today instead.
 */
internal fun ChecklistConfiguration.openingDayKey(todayKey: String): String {
    val asked = recordDayFor(requireValidDateKey(todayKey))
    return if (firstDay?.let { asked < it } == true) todayKey else asked.toString()
}

internal fun checklistSelection(state: AppState, checklist: ChecklistConfiguration, dateKey: String, daysOff: HolidaySchedule): ChecklistSelection {
    val date = requireValidDateKey(dateKey)
    val recordable = checklist.recordableOn(date)
    val recorded = state.recordOn(checklist.id, dateKey)
    val persisted = recorded.among(recordable.ticks)
    return ChecklistSelection(
        progress = state.progressOf(checklist, dateKey, daysOff),
        recordable = recordable,
        persisted = persisted,
        // Nothing starts ticked or missed: a mark is only ever something the person pressed themselves.
        selected = persisted,
        hasPersistedRecord = !recorded.isEmpty,
    )
}

/** This record's part for [keys]. */
private fun DayRecord.among(keys: Set<String>): DayRecord = DayRecord(ticks intersect keys, misses intersect keys)

/**
 * One checkpoint's draft for one day, for a checklist that saves each checkpoint on its own: the
 * items that can be recorded there, due or not, what is recorded among them, and what is picked
 * now. Only those items can be toggled, and a save replaces only their marks, so a tick for an
 * item that ended survives every save. Where the day's week items stand is not part of it: that
 * changes with a save at another checkpoint or on another day, and must not reset this one's draft.
 */
internal data class CheckpointSelection(
    val checkpoint: Checkpoint,
    val items: List<ChecklistItem>,
    /** The items among [items] that log only misses today and are due here, each `checkpointId/itemId`. */
    val exceptionsOnly: Set<String>,
    val persisted: DayRecord,
    val selected: DayRecord,
) {
    val recordableTicks: Set<String> get() = items.mapTo(LinkedHashSet()) { tickOf(checkpoint.id, it.id) }
    val hasChanges: Boolean get() = selected != persisted

    /** Whether saving the draft records a tick that is not stored yet. */
    val addsATick: Boolean get() = !persisted.ticks.containsAll(selected.ticks)

    fun markOf(tick: String): ItemMark = selected.markOf(tick, tick in exceptionsOnly)

    fun toggle(tick: String): CheckpointSelection {
        require(tick in recordableTicks) { "Only items that can be recorded at ${checkpoint.id} can be picked." }
        return copy(selected = selected.toggling(tick, tick in exceptionsOnly, tick in persisted.ticks))
    }
}

/**
 * The checkpoints that have something to record on [dateKey], each with its own draft. [daysOff]
 * says which items are due there, and so which of them log only misses that day.
 */
internal fun checkpointSelections(state: AppState, checklist: ChecklistConfiguration, dateKey: String, daysOff: HolidaySchedule): List<CheckpointSelection> {
    val date = requireValidDateKey(dateKey)
    val recorded = state.recordOn(checklist.id, dateKey)
    val exceptionsOnly = checklist.dueOn(date, daysOff).exceptionsOnlyTicks
    return checklist.recordableOn(date).checkpoints.map { (checkpoint, items) ->
        val keys = items.mapTo(LinkedHashSet()) { tickOf(checkpoint.id, it.id) }
        val here = recorded.among(keys)
        CheckpointSelection(checkpoint, items, exceptionsOnly = exceptionsOnly intersect keys, persisted = here, selected = here)
    }
}

/** How a week item stands on the day, for the row that shows [item]; null for a daily item. */
internal fun List<WeekItemProgress>.of(item: ChecklistItem): WeekItemProgress? = firstOrNull { it.item.id == item.id }

internal enum class ChecklistDayState {
    EMPTY,
    PARTIAL,
    COMPLETE,
}

/** How an item stands in a calendar cell's bar: ticked, asked for and not ticked, or assumed done. */
internal enum class CalendarMark {
    Ticked,
    Open,
    Assumed,
}

/**
 * One calendar cell, judged by its own day's rules with the days off read, as the progress reads
 * them. The cell shows facts: what was recorded against what the day asked for. A day kept by
 * assumption alone has nothing recorded, so it reads empty, and only its bar's quiet part says what was
 * assumed. [listed] is what the day's description names: by default what is due ([listedOn] adds
 * every stored mark).
 */
internal data class ChecklistHistoryDay(
    val rules: ChecklistRules,
    val record: DayRecord,
    val listed: List<CheckpointRules> = rules.checkpoints,
) {
    val dateKey: String get() = rules.date.toString()

    /** Each `checkpointId/itemId` due that day of an item that logs only misses: kept unless a miss is recorded, and never asked for a tick. */
    val exceptionsOnly: Set<String> get() = rules.exceptionsOnlyTicks

    /** The ticks the day asked for. */
    private val asked: Set<String> get() = rules.ticks - exceptionsOnly

    /**
     * The days a week item was ticked, at whichever of its checkpoints: each counts in the bar on its own,
     * beside the cell's color, which the daily items alone decide. A week item's free days and gaps
     * leave the cell as its daily items are; the streak calendar is where a gap shows.
     */
    val weekTicksDone: Set<String>
        get() = rules.weekItems.flatMap { week -> week.checkpoints.map { tickOf(it.id, week.item.id) } }.filterTo(LinkedHashSet()) { it in record.ticks }

    /**
     * Every tick and miss recorded for an item the checklist knows, whether or not it judges the day
     * or can still be recorded there, as one left on an item changed the same day to a week target
     * or removed: a fact, shown as one, as the day's description names it.
     */
    private val marksRecorded: Set<String>
        get() = (record.ticks + record.misses) intersect listed.flatMapTo(HashSet()) { (checkpoint, items) -> items.map { tickOf(checkpoint.id, it.id) } }

    /**
     * The items in the cell's bar, in order: each tick asked for, each item that logs only misses, each
     * week tick done and each other mark recorded. A miss reads as not ticked, and an item neither
     * ticked nor missed as assumed, never as ticked.
     */
    val marks: Map<String, CalendarMark>
        get() = (asked + exceptionsOnly + weekTicksDone + marksRecorded).associateWith { tick ->
            when {
                tick in record.ticks -> CalendarMark.Ticked
                tick in exceptionsOnly && tick !in record.misses -> CalendarMark.Assumed
                else -> CalendarMark.Open
            }
        }

    /** Whether the cell shows its bar: something was recorded for an item it knows, or an item is assumed done. */
    val showsMarks: Boolean get() = state != ChecklistDayState.EMPTY || weekTicksDone.isNotEmpty() || marksRecorded.isNotEmpty() || exceptionsOnly.isNotEmpty()

    val state: ChecklistDayState
        get() {
            val due = rules.ticks
            val done = record.ticks intersect due
            val missed = record.misses intersect due
            return when {
                done.isEmpty() && missed.isEmpty() -> ChecklistDayState.EMPTY
                missed.isEmpty() && done.containsAll(asked) -> ChecklistDayState.COMPLETE
                else -> ChecklistDayState.PARTIAL
            }
        }
}

internal fun checklistHistory(state: AppState, checklist: ChecklistConfiguration, todayKey: String, daysOff: HolidaySchedule): List<ChecklistHistoryDay> {
    val weeks = calendarWeeksWindow(todayKey)
    val recordByDate = weeks.groupVisible(state.checklistDays.filter { it.checklistId == checklist.id }) { it.dateKey }
        .mapValues { (_, days) -> days.merged() }
    return weeks.dateKeys.map { dateKey ->
        val date = requireValidDateKey(dateKey)
        val rules = checklist.dueOn(date, daysOff)
        val record = recordByDate[dateKey] ?: DayRecord()
        ChecklistHistoryDay(rules, record, checklist.listedOn(rules, record))
    }
}

/**
 * What a calendar day names, checkpoint by checkpoint in the checklist's order: every item due, and
 * every item with a tick or a miss stored, due or not, and whether or not it can still be recorded
 * that day, as one removed after its mark was saved. A stored mark is a fact; only one for an item
 * no configuration knows is left unsaid, as it has nothing to be named by.
 */
internal fun ChecklistConfiguration.listedOn(due: ChecklistRules, record: DayRecord): List<CheckpointRules> {
    val known = items.distinctBy { it.id }
    return checkpoints.mapNotNull { checkpoint ->
        val dueHere = due.checkpoints.firstOrNull { it.checkpoint.id == checkpoint.id }?.items.orEmpty().mapTo(HashSet()) { it.id }
        known.filter { item -> item.id in dueHere || tickOf(checkpoint.id, item.id).let { it in record.ticks || it in record.misses } }
            .takeIf { it.isNotEmpty() }
            ?.let { CheckpointRules(checkpoint, it) }
    }
}
