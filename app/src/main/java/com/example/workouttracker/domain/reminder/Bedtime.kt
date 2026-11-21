package com.example.workouttracker.domain.reminder

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Where a moment falls in the night before a wake time. */
enum class BedtimePhase { Awake, WindingDown, InBed }

/**
 * The night that ends at [wake]: asleep by the [fixedBedtime] a person set, or else eight hours
 * before waking, and before that an hour to get ready for bed. Every time before sleep and Mo's
 * sleepiness read it, so "bedtime" is decided in one place.
 */
data class Bedtime(val wake: LocalTime, val fixedBedtime: LocalTime?) {
    val asleepBy: LocalTime = fixedBedtime ?: wake.minus(Sleep)
    val windDown: LocalTime = asleepBy.minus(GettingReady)

    /** Eight hours unless a fixed bedtime makes the night shorter or longer. */
    private val asleepFor: Duration =
        Duration.ofSeconds(Math.floorMod(Duration.between(asleepBy, wake).seconds, SECONDS_PER_DAY))

    /**
     * [before] sleep, on the evening of the day that woke at [wake]: a time that would fall before
     * waking, or after [LatestEveningReminder], becomes [LatestEveningReminder].
     *
     * A fixed bedtime after midnight, such as 00:30, moves when Mo goes to bed but not this rule:
     * the reminders and due times read here belong to the evening they ask about, so 15 minutes
     * before 00:30 is 23:45, while an hour before it is 23:30.
     */
    fun beforeSleep(before: Duration): LocalTime = asleepBy.minus(before)
        .takeIf { it > wake }
        ?.coerceAtMost(LatestEveningReminder)
        ?: LatestEveningReminder

    fun phaseAt(time: LocalTime): BedtimePhase {
        // In seconds: minutes would round a negative duration toward zero and flip a phase early.
        val sinceWindDown = Math.floorMod(Duration.between(windDown, time).seconds, SECONDS_PER_DAY)
        return when {
            sinceWindDown < GettingReady.seconds -> BedtimePhase.WindingDown
            sinceWindDown < (GettingReady + asleepFor).seconds -> BedtimePhase.InBed
            else -> BedtimePhase.Awake
        }
    }

    companion object {
        val Sleep: Duration = Duration.ofHours(8)
        val GettingReady: Duration = Duration.ofHours(1)
        val LatestEveningReminder: LocalTime = LocalTime.of(23, 45)
    }
}

/**
 * Whether an alarm at this time of day ends a night. The phone reports every alarm-clock entry, and
 * a nap or an appointment must not put anyone to bed at noon, so only an alarm within a few hours of
 * the [usualWake] time counts.
 */
fun LocalTime.isWakeTimeNear(usualWake: LocalTime): Boolean {
    val apart = Math.floorMod(Duration.between(usualWake, this).seconds, SECONDS_PER_DAY)
    return minOf(apart, SECONDS_PER_DAY - apart) <= WakeWindow.seconds
}

/**
 * The wake time the coming night ends at. The phone's next alarm decides it when it rings within a
 * day, so an alarm set for next Monday does not decide tonight. An alarm that rang within the last
 * few hours still counts, because the phone forgets it the moment it rings and he must not go back
 * to bed until the fallback time. Otherwise it is the fallback.
 */
fun nextWakeTime(
    now: ZonedDateTime,
    nextSystemAlarmMillis: Long?,
    fallbackAlarmTime: ReminderTime,
    lastRungAlarmMillis: Long? = null,
): LocalTime {
    val usualWake = fallbackAlarmTime.toLocalTime()
    fun Long.asWakeAlarm(): ZonedDateTime? =
        Instant.ofEpochMilli(this).atZone(now.zone).takeIf { it.toLocalTime().isWakeTimeNear(usualWake) }

    val rung = lastRungAlarmMillis?.asWakeAlarm()
        ?.takeIf { !it.isAfter(now) && Duration.between(it, now) <= WakeWindow }
    val upcoming = nextSystemAlarmMillis?.asWakeAlarm()
        ?.takeIf { it.isAfter(now) && Duration.between(now, it) <= Duration.ofDays(1) }
    return (rung ?: upcoming)?.toLocalTime()?.withSecond(0)?.withNano(0) ?: usualWake
}

private val WakeWindow: Duration = Duration.ofHours(4)
private const val SECONDS_PER_DAY = 24L * 60L * 60L
