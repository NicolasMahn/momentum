package de.nicolasmahn.momentum.domain.checklist

import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.core.time.requireValidDateKey
import de.nicolasmahn.momentum.domain.firstJudgmentDay
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule

fun AppState.checklist(id: String): ChecklistConfiguration? = checklists.firstOrNull { it.id == id }

/** Everything recorded for [checklistId] on [dateKey], ticks and misses, due or not. */
fun AppState.recordOn(checklistId: String, dateKey: String): DayRecord =
    checklistDays.firstOrNull { it.checklistId == checklistId && it.dateKey == dateKey }?.record ?: DayRecord()

/** Everything recorded for [checklistId] on any day, as a week item is read against its week. */
fun AppState.recordsOf(checklistId: String): ChecklistRecords = ChecklistRecords { recordOn(checklistId, it.toString()) }

/** The progress of [checklist] on [dateKey], read from every record of its week. */
fun AppState.progressOf(checklist: ChecklistConfiguration, dateKey: String, daysOff: HolidaySchedule): ChecklistProgress =
    checklist.progressOn(requireValidDateKey(dateKey), recordsOf(checklist.id), daysOff)

/**
 * The ticks [rules] lists at its checkpoints, each `checkpointId/itemId`: the daily items due, or
 * everything that can be recorded. A week item is among the recordable ticks every day, and due
 * only through [progressOn].
 */
val ChecklistRules.ticks: Set<String>
    get() = checkpoints.flatMapTo(LinkedHashSet()) { (checkpoint, items) -> items.map { tickOf(checkpoint.id, it.id) } }

/**
 * The ticks among [ticks] of items that log only misses on the rules' date: kept unless marked
 * missed there, and never asked for a tick.
 */
val ChecklistRules.exceptionsOnlyTicks: Set<String>
    get() = checkpoints.flatMapTo(LinkedHashSet()) { (checkpoint, items) -> items.filter { it.exceptionsOnly.on(date) }.map { tickOf(checkpoint.id, it.id) } }

/**
 * Saves what was picked for [dateKey] among what can be recorded there, due or not: each tick and
 * each miss in [selected], the rest cleared. A tick or miss that cannot be recorded that day, such
 * as one for an item that ended, is kept as it was, so editing a day never erases what it recorded.
 */
fun setDayRecord(
    state: AppState,
    checklist: ChecklistConfiguration,
    dateKey: String,
    selected: DayRecord,
    timestampMillis: Long,
): AppState {
    val recordable = checklist.recordableOn(requireValidDateKey(dateKey)).ticks
    require(recordable.containsAll(selected.ticks) && recordable.containsAll(selected.misses)) { "Only items that can be recorded on $dateKey can be picked." }
    return state.withRecord(checklist.id, dateKey, state.recordOn(checklist.id, dateKey).replacing(recordable, selected), timestampMillis)
}

/** The ticks [rules] lists at one checkpoint, each `checkpointId/itemId`. */
fun ChecklistRules.ticksAt(checkpointId: String): Set<String> =
    checkpoints.filter { it.checkpoint.id == checkpointId }
        .flatMapTo(LinkedHashSet()) { (checkpoint, items) -> items.map { tickOf(checkpoint.id, it.id) } }

/**
 * Saves what was picked at one checkpoint among what can be recorded there on [dateKey]: a
 * checklist with several checkpoints saves each on its own. Every
 * other tick or miss, at another checkpoint or one that cannot be recorded that day, stays as it was.
 */
fun setCheckpointRecord(
    state: AppState,
    checklist: ChecklistConfiguration,
    dateKey: String,
    checkpointId: String,
    selected: DayRecord,
    timestampMillis: Long,
): AppState {
    val recordable = checklist.recordableOn(requireValidDateKey(dateKey)).ticksAt(checkpointId)
    require(recordable.containsAll(selected.ticks) && recordable.containsAll(selected.misses)) { "Only items that can be recorded at $checkpointId on $dateKey can be picked." }
    return state.withRecord(checklist.id, dateKey, state.recordOn(checklist.id, dateKey).replacing(recordable, selected), timestampMillis)
}

/** This record with [selected] in place of whatever it held for [keys]; everything else stays. */
private fun DayRecord.replacing(keys: Set<String>, selected: DayRecord): DayRecord =
    DayRecord(ticks - keys + selected.ticks, misses - keys + selected.misses)

/**
 * Ticks everything asked at one checkpoint on [dateKey], keeping what was already recorded. A week
 * item is due there only on a day skipping it would cost, so a free day's is left as it is, and an
 * item that logs only misses asks for no tick, so it stays as it is: assumed, or missed.
 */
fun tickAllDueAt(
    state: AppState,
    checklist: ChecklistConfiguration,
    dateKey: String,
    checkpointId: String,
    timestampMillis: Long,
    daysOff: HolidaySchedule,
): AppState {
    val due = state.progressOf(checklist, dateKey, daysOff).dueTicksAt(checkpointId)
    return state.withRecord(checklist.id, dateKey, state.recordOn(checklist.id, dateKey).ticking(due), timestampMillis)
}

/** Ticks everything asked on [dateKey], keeping what was already recorded; see [tickAllDueAt]. */
fun tickAllDue(
    state: AppState,
    checklist: ChecklistConfiguration,
    dateKey: String,
    timestampMillis: Long,
    daysOff: HolidaySchedule,
): AppState {
    val due = state.progressOf(checklist, dateKey, daysOff).dueTicks
    return state.withRecord(checklist.id, dateKey, state.recordOn(checklist.id, dateKey).ticking(due), timestampMillis)
}

/** This record with [ticks] added; a tick replaces a miss left on the same item. */
private fun DayRecord.ticking(ticks: Set<String>): DayRecord = DayRecord(this.ticks + ticks, misses - ticks)

fun removeChecklistDay(state: AppState, checklistId: String, dateKey: String): AppState {
    requireValidDateKey(dateKey)
    val kept = state.checklistDays.filterNot { it.checklistId == checklistId && it.dateKey == dateKey }
    return if (kept.size == state.checklistDays.size) state else state.copy(checklistDays = kept)
}

/** An unchanged record keeps the day and its time; an empty one removes the day. */
private fun AppState.withRecord(checklistId: String, dateKey: String, record: DayRecord, timestampMillis: Long): AppState {
    require(timestampMillis >= 0) { "Timestamp must not be negative." }
    val current = checklistDays.firstOrNull { it.checklistId == checklistId && it.dateKey == dateKey }
    if (current?.record == record || (current == null && record.isEmpty)) return this
    val others = checklistDays.filterNot { it === current }
    val updated = if (record.isEmpty) others else others + ChecklistDay(checklistId, dateKey, timestampMillis, record.ticks, record.misses)
    return copy(checklistDays = updated.sortedWith(ChecklistDayOrder))
}

/** Newest first, the order the records are kept in. */
val ChecklistDayOrder: Comparator<ChecklistDay> = compareByDescending<ChecklistDay> { it.dateKey }
    .thenByDescending { it.timestampMillis }
    .thenBy { it.checklistId }

/**
 * Turns a checkpoint's reminder on or off. Whether it reminds never judges a day, so the change is
 * not dated: it applies from now on, like a rename.
 */
fun setCheckpointRemind(state: AppState, checklistId: String, checkpointId: String, remind: Boolean): AppState {
    val checklist = requireNotNull(state.checklist(checklistId)) { "No checklist $checklistId." }
    require(checklist.checkpoints.any { it.id == checkpointId }) { "Checklist $checklistId has no checkpoint $checkpointId." }
    val updated = checklist.copy(checkpoints = checklist.checkpoints.map { if (it.id == checkpointId) it.copy(remind = remind) else it })
    return if (updated == checklist) state else state.copy(checklists = state.checklists.map { if (it.id == checklistId) updated else it })
}

/** The rules a checklist starts with when it joins rewards. */
val DefaultChecklistRewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7)

/**
 * Lets a checklist take part in rewards from [today]. Earlier days stay outside the walk, so joining
 * can never break a streak; a checklist that left before gets a new period.
 */
fun joinRewards(state: AppState, checklistId: String, today: java.time.LocalDate): AppState =
    state.updateChecklist(checklistId) { it.joiningRewards(today) }

/**
 * Ends a checklist's part in rewards after yesterday, so the days it was judged keep their
 * judgments. A period that only started today leaves nothing behind.
 */
fun leaveRewards(state: AppState, checklistId: String, today: java.time.LocalDate): AppState =
    state.updateChecklist(checklistId) { it.leavingRewards(today, state.checklistRecordedDates(checklistId)) }

internal fun ChecklistConfiguration.joiningRewards(today: java.time.LocalDate): ChecklistConfiguration = copy(rewards = rewards.joining(today))

/**
 * A checklist without periods took part over its whole history, from
 * the first due day of its records or explicitly assumed tasks ([firstJudgmentDay]), so
 * leaving materializes that same start instead of reinterpreting its recorded-day ownership.
 */
internal fun ChecklistConfiguration.leavingRewards(today: java.time.LocalDate, recordedDates: Set<java.time.LocalDate>): ChecklistConfiguration =
    copy(rewards = rewards?.leaving(today, wholeHistoryFrom = firstJudgmentDay(recordedDates)))

/**
 * This bank taking part from [today], for every view that has one: the rules a view starts with
 * and a first period while there is no bank yet, the bank as it is while it takes part already,
 * and otherwise one period more, which reopens a period closed yesterday ([joinedFrom]).
 */
internal fun ChecklistRewards?.joining(today: java.time.LocalDate): ChecklistRewards = when {
    this == null -> DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(today)))
    else -> copy(periods = periods?.takingPartFrom(today))
}

/**
 * This bank out from [today]: its open period ends yesterday, or goes if it only began today, and
 * every closed period stays, so the days it took part keep their judgments. The rules stay for
 * when it joins again, even with no period left. A bank without periods took part since
 * [wholeHistoryFrom], or on no day yet while that is unknown.
 */
internal fun ChecklistRewards.leaving(today: java.time.LocalDate, wholeHistoryFrom: java.time.LocalDate? = null): ChecklistRewards =
    copy(periods = periods.leavingOn(today, wholeHistoryFrom))

/**
 * Whether a day before [today] was judged by this bank's rules: one it took part on. A bank
 * without periods took part over its whole history.
 */
internal fun ChecklistRewards.judgedBefore(today: java.time.LocalDate): Boolean = periods.reachBefore(today)

/**
 * This bank with its size and the kept days a pass takes changed from [today] on, as dated
 * revisions only while [dated]: rules no earlier day was judged by change plainly.
 */
internal fun ChecklistRewards.withRules(today: java.time.LocalDate, maxPasses: Int, everyKeptDays: Int, dated: Boolean): ChecklistRewards = copy(
    maxPasses = this.maxPasses.changedFrom(today, maxPasses, dated),
    everyKeptDays = this.everyKeptDays.changedFrom(today, everyKeptDays, dated),
)

/** The first day recorded for [checklistId], or null while nothing is. */
fun AppState.firstRecordedDay(checklistId: String): java.time.LocalDate? = checklistRecordedDates(checklistId).minOrNull()

/** Raw record dates stay separate from the judgment dates a configuration assigns to them. */
fun AppState.checklistRecordedDates(checklistId: String): Set<java.time.LocalDate> =
    checklistDays.asSequence().filter { it.checklistId == checklistId }.mapNotNull { parseDateKey(it.dateKey) }.toSet()

private fun AppState.updateChecklist(id: String, change: (ChecklistConfiguration) -> ChecklistConfiguration): AppState {
    val checklist = requireNotNull(checklist(id)) { "No checklist $id." }
    val updated = change(checklist)
    return if (updated == checklist) this else copy(checklists = checklists.map { if (it.id == id) updated else it })
}
