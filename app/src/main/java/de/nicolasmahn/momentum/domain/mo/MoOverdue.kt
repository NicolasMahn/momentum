package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.core.time.HabitDayStart
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Current tracker references whose open parts have waited long enough to act out, and those
 * overdue: asked for an hour ago and still open, whether a free day would cover them or not. A
 * free day protects the streak; it does not make a task he asked for any less late.
 */
data class TrackerAsked(
    val waited: Map<TrackerRef, List<Int>> = emptyMap(),
    val overdueBy: Map<TrackerRef, Int> = emptyMap(),
)

fun moAskedTrackers(mood: MoMood, reminders: ReminderSettings, now: LocalTime): TrackerAsked {
    val waited = mood.waitedSinceAsked(reminders, now)
    return TrackerAsked(
        waited = waited.mapValues { (_, parts) -> parts.filter { it >= ACT_OUT_AFTER_MINUTES } }.filterValues { it.isNotEmpty() },
        overdueBy = waited.mapNotNull { (ref, parts) -> parts.maxOrNull()?.takeIf { it >= OVERDUE_AFTER_MINUTES }?.let { ref to it } }.toMap(),
    )
}

fun moIsAngry(mood: MoMood, reminders: ReminderSettings, now: LocalTime): Boolean =
    moAskedTrackers(mood, reminders, now).overdueBy.isNotEmpty()

/** What his nudges fume about: the same overdue tasks his look is angry about. */
fun moOverdueTrackers(mood: MoMood, reminders: ReminderSettings, now: LocalTime): List<TrackerRef> =
    moAskedTrackers(mood, reminders, now).overdueBy.keys.toList()

private fun MoMood.waitedSinceAsked(reminders: ReminderSettings, now: LocalTime): Map<TrackerRef, List<Int>> {
    val minute = habitDayMinute(now.truncatedTo(ChronoUnit.MINUTES))
    return trackerObligations(reminders).mapValues { (_, parts) -> parts.map { minute - habitDayMinute(it.since) }.filter { it >= 0 } }
}

private const val ACT_OUT_AFTER_MINUTES = 15

/** One hour, so tasks done together in one go are not fumed about one by one. */
private const val OVERDUE_AFTER_MINUTES = 60

private fun habitDayMinute(time: LocalTime): Int = habitDayMinuteOf(time)

/** Minutes since the habit day began at 04:00, so a time after midnight sorts after the evening. */
fun habitDayMinuteOf(time: LocalTime): Int =
    Math.floorMod(time.toSecondOfDay() / 60 - HabitDayStart.toSecondOfDay() / 60, 24 * 60)
