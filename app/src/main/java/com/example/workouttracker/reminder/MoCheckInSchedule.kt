package com.example.workouttracker.reminder

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/** Mo checks for overdue tasks every three hours from noon to nine, never at night. */
private const val FirstNudgeHour = 12
private const val LastNudgeHour = 21
private const val HoursBetweenNudges = 3

/** Every time of day Mo checks for overdue tasks. */
internal fun moNudgeTimes(): List<LocalTime> =
    (FirstNudgeHour..LastNudgeHour step HoursBetweenNudges)
        .map { hour -> LocalTime.of(hour, 0) }
        .sorted()

/** The first nudge strictly after [after]: the next one that day, or the first of the day after. */
internal fun nextMoNudgeAfter(after: LocalDateTime): LocalDateTime {
    val times = moNudgeTimes()
    val day = after.toLocalDate()
    return times.map(day::atTime).firstOrNull { it.isAfter(after) }
        ?: day.plusDays(1).atTime(times.first())
}

/**
 * The nudge to set the alarm for. [firedNudge] is the one being answered right now, so an alarm
 * delivered a moment early is never set for the same nudge again.
 */
internal fun nextMoNudge(
    now: ZonedDateTime,
    firedNudge: LocalDateTime? = null,
): LocalDateTime {
    val localNow = now.toLocalDateTime()
    var nudge = nextMoNudgeAfter(maxOf(localNow, firedNudge ?: localNow))
    // A wall time that a clock change repeats resolves to its first pass, which may be over.
    while (!nudge.atZone(now.zone).isAfter(now)) {
        nudge = nextMoNudgeAfter(nudge)
    }
    return nudge
}
