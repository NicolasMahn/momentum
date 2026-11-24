package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.CheckpointPart
import de.nicolasmahn.momentum.domain.DayPart
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.WholeDay
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.dueTime
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.timing
import de.nicolasmahn.momentum.domain.reminder.toLocalTime
import java.time.LocalTime

internal data class MoObligation(val part: DayPart, val since: LocalTime)

/**
 * Configured checkpoints supply deadlines, and the workout reminder the deadline of a task it asks
 * for; every other task is asked at the check-in time. Marks of a miss no longer owe a reminder.
 */
internal fun MoMood.trackerObligations(reminders: ReminderSettings): Map<TrackerRef, List<MoObligation>> {
    val configured = trackerCheckpoints
    val workout = CheckpointTime.Fixed(reminders.workout.fixedTime.toLocalTime()).dueTime(reminders.workout.enabled, reminders.timing)
    return (missingTrackersToday + freeTrackersToday).distinct().associateWith { ref ->
        trackerStillOwed(ref).map { part ->
            val checkpoint = configured[ref].orEmpty().let { available ->
                if (part is CheckpointPart) available.firstOrNull { it.id == part.checkpointId } else available.singleOrNull()
            }
            val since = checkpoint?.dueTime(reminders.timing)
                ?: if (ref in workoutRemindedTrackers) workout else reminders.timing.checkIn
            MoObligation(part, since)
        }
    }
}

fun MoMood.trackerStillOpen(ref: TrackerRef): List<DayPart> = trackerOpenParts[ref] ?: listOf(WholeDay)

fun MoMood.trackerStillOwed(ref: TrackerRef): List<DayPart> = trackerStillOpen(ref) - trackerMissedParts[ref].orEmpty().toSet()

/**
 * The tasks a free day covers today that nothing is asked of, as Activity on a rest day its week
 * names, or on a planned day a person said they rest on. Such a day cannot turn into a miss, so
 * it is settled: the row reads as kept and the day is whole without it. A free day Mo still asks
 * for, a planned workout nothing was said about, is not one of them.
 */
val MoMood.restingTrackersToday: List<TrackerRef>
    get() = freeTrackersToday.filter { it !in missingTrackersToday && trackerStillOwed(it).isEmpty() }

/**
 * When each still-owed part of [ref] is due today, as minutes since the habit day began at 04:00,
 * earliest first: the times Mo counts from ([trackerObligations]), so Home and Mo agree on what is
 * late. A week's amount not reached yet is due at its own checkpoint every day of the week, unless
 * it is quieted for today or, on its Sunday, tokens are set aside for it. A task not owed today is empty.
 */
fun MoMood.trackerDueMinutes(ref: TrackerRef, reminders: ReminderSettings): List<Int> {
    val owed = trackerObligations(reminders)[ref]?.map { it.since }
        ?: if ((trackerWeeklyRemainders[ref] ?: 0) > 0 && ref !in chosenTrackersToday && ref !in quietTrackersToday) listOf(trackerCheckpoints[ref].orEmpty().singleOrNull()?.dueTime(reminders.timing) ?: reminders.timing.checkIn) else emptyList()
    return owed.map(::habitDayMinuteOf).sorted()
}
