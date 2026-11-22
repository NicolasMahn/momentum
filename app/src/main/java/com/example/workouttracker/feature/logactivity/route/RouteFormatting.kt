package com.example.workouttracker.feature.logactivity.route

import com.example.workouttracker.core.format.formatFixedDecimals
import java.util.Locale

/** Kilometres with two decimals in [locale]'s style, "2.31" or "2,31": a runner reads ten metres. */
internal fun formatKilometers(meters: Double, locale: Locale): String = formatFixedDecimals(meters / 1000, KilometerDecimals, locale)

/** A distance shows ten metres, wherever it stands. */
internal const val KilometerDecimals = 2

/** km/h with one decimal, "18.4" or "18,4". */
internal fun formatSpeed(kilometersPerHour: Double, locale: Locale): String = formatFixedDecimals(kilometersPerHour, 1, locale)

/** A clock: "12:04" below an hour, "1:02:03" from one on. */
internal fun formatClock(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, rest) else "%d:%02d".format(Locale.ROOT, minutes, rest)
}
