package de.nicolasmahn.momentum.domain.checklist

import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.reminder.afterWake
import java.time.Duration
import java.time.LocalTime

/**
 * When a checkpoint checks, in the three forms a stored configuration may use: a fixed time of day,
 * minutes after waking, or minutes before sleep. [toString] is the stored form, and
 * [parseCheckpointTime] accepts exactly what it writes.
 */
sealed interface CheckpointTime {
    /** `"12:30"`. */
    data class Fixed(val time: LocalTime) : CheckpointTime {
        init {
            require(time.second == 0 && time.nano == 0) { "A checkpoint time has minute precision, was $time." }
        }

        override fun toString(): String = time.toString()
    }

    /** `"wake+5"`: minutes after the wake time. */
    data class AfterWake(val minutes: Int) : CheckpointTime {
        init {
            requireOffset(minutes)
        }

        override fun toString(): String = "wake+$minutes"
    }

    /** `"sleep-15"`: minutes before sleep. */
    data class BeforeSleep(val minutes: Int) : CheckpointTime {
        init {
            requireOffset(minutes)
        }

        override fun toString(): String = "sleep-$minutes"
    }
}

/**
 * The time of day this means on a day whose [night] ends at its wake time: after waking stays on
 * the wake day, before sleep counts back from the
 * night's bedtime and is never later than 23:45.
 */
fun CheckpointTime.on(night: Bedtime): LocalTime = when (this) {
    is CheckpointTime.Fixed -> time
    is CheckpointTime.AfterWake -> afterWake(night.wake, minutes.toLong())
    is CheckpointTime.BeforeSleep -> night.beforeSleep(Duration.ofMinutes(minutes.toLong()))
}

/**
 * The stored form back as a time, or null for anything [CheckpointTime.toString] would not write:
 * `7:30`, `24:00`, `wake+05` and `wake-5` are not times.
 */
fun parseCheckpointTime(text: String): CheckpointTime? {
    val parsed = runCatching {
        when {
            text.startsWith(WakePrefix) -> CheckpointTime.AfterWake(text.removePrefix(WakePrefix).toInt())
            text.startsWith(SleepPrefix) -> CheckpointTime.BeforeSleep(text.removePrefix(SleepPrefix).toInt())
            else -> CheckpointTime.Fixed(LocalTime.parse(text))
        }
    }.getOrNull()
    return parsed?.takeIf { it.toString() == text }
}

/** Less than a day, so an offset can never name the same moment as a different one. */
private fun requireOffset(minutes: Int) =
    require(minutes in 0 until MinutesPerDay) { "A checkpoint offset is 0 to ${MinutesPerDay - 1} minutes, was $minutes." }

private const val WakePrefix = "wake+"
private const val SleepPrefix = "sleep-"
private const val MinutesPerDay = 24 * 60
