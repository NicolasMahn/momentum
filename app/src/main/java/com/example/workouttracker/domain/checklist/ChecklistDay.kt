package com.example.workouttracker.domain.checklist

import com.example.workouttracker.core.time.requireValidDateKey
import com.example.workouttracker.domain.holiday.HolidaySchedule
import java.time.LocalDate

/**
 * What was recorded on one day of one checklist. A record belongs to the day it is for, not the
 * day it was written, so a late tick keeps an earlier day. A day with nothing recorded has no record.
 */
data class ChecklistDay(
    val checklistId: String,
    val dateKey: String,
    val timestampMillis: Long,
    /** Each `checkpointId/itemId` ticked; ticks for IDs no configuration knows are kept as they are. */
    val ticks: Set<String>,
    /**
     * Each `checkpointId/itemId` marked missed, in the same form: the one thing recorded for an
     * item that logs only misses. A miss is a fact, settled and never owed, and it is kept like a
     * tick wherever nothing reads it.
     */
    val misses: Set<String> = emptySet(),
) {
    init {
        requireValidDateKey(dateKey)
        require(timestampMillis >= 0) { "Timestamp must not be negative." }
        require(ticks.isNotEmpty() || misses.isNotEmpty()) { "A checklist day with nothing recorded is not stored." }
        require(ticks.none { it in misses }) { "An item is ticked or missed at a checkpoint, never both." }
    }

    val record: DayRecord get() = DayRecord(ticks, misses)
}

/**
 * The record of one day of one checklist, as the progress and the screen read it: each
 * `checkpointId/itemId` ticked and each marked missed, never both for one. Empty for a day with
 * nothing recorded.
 */
data class DayRecord(val ticks: Set<String> = emptySet(), val misses: Set<String> = emptySet()) {
    init {
        require(ticks.none { it in misses }) { "An item is ticked or missed at a checkpoint, never both." }
    }

    val isEmpty: Boolean get() = ticks.isEmpty() && misses.isEmpty()
}

/** The records of one checklist on any day: what a week item is read against, week by week. */
fun interface ChecklistRecords {
    fun on(date: LocalDate): DayRecord
}

/**
 * Whether [checklist] has nothing unmet on [dateKey], by the rules of that day: every due item
 * ticked or assumed, none owed or missed. [days] holds the checklist's records, the whole week's
 * where it has a week item.
 */
fun ChecklistConfiguration.isCompleteFor(days: List<ChecklistDay>, dateKey: String, daysOff: HolidaySchedule): Boolean =
    progressOn(LocalDate.parse(dateKey), recordsIn(days), daysOff).isComplete

/**
 * Whether the day reads as done on Home. A day asking something of an item that logs only misses
 * is done as the walk judges it, when something is due and nothing is unmet, so a miss is never
 * outweighed by a tick and a day kept by assumption alone reads as kept. Any other day keeps the
 * rule it has always had there: any tick, due or not. What was recorded at all is [isLoggedFor].
 */
fun ChecklistConfiguration.isDoneFor(days: List<ChecklistDay>, dateKey: String, daysOff: HolidaySchedule): Boolean {
    val progress = progressOn(LocalDate.parse(dateKey), recordsIn(days), daysOff)
    return if (progress.exceptionsOnly.isEmpty()) isTickedFor(days, dateKey) else progress.checkpoints.isNotEmpty() && progress.isComplete
}

/** Whether the day is kept by assumption alone; see [ChecklistProgress.isAssumed]. */
fun ChecklistConfiguration.isAssumedFor(days: List<ChecklistDay>, dateKey: String, daysOff: HolidaySchedule): Boolean =
    progressOn(LocalDate.parse(dateKey), recordsIn(days), daysOff).isAssumed

/** Whether any record exists on [dateKey], a tick due or not, or a miss: what was logged, which Statistics counts. */
fun ChecklistConfiguration.isLoggedFor(days: List<ChecklistDay>, dateKey: String): Boolean =
    days.any { it.checklistId == id && it.dateKey == dateKey }

/** Whether something was ticked on [dateKey], due or not: a recorded action, which a miss is not. */
fun ChecklistConfiguration.isTickedFor(days: List<ChecklistDay>, dateKey: String): Boolean =
    days.any { it.checklistId == id && it.dateKey == dateKey && it.ticks.isNotEmpty() }

/** This checklist's records among [days], day by day. */
fun ChecklistConfiguration.recordsIn(days: List<ChecklistDay>): ChecklistRecords = ChecklistRecords { date ->
    val dateKey = date.toString()
    days.filter { it.checklistId == id && it.dateKey == dateKey }.merged()
}

/**
 * The records of one day as one. A day is stored once, so this is that record or none; a tick
 * wins over a miss should two stored copies ever disagree, rather than refusing the day.
 */
internal fun Iterable<ChecklistDay>.merged(): DayRecord {
    val ticks = flatMapTo(HashSet()) { it.ticks }
    return DayRecord(ticks, flatMapTo(HashSet()) { it.misses } - ticks)
}
