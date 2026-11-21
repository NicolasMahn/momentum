package com.example.workouttracker.core.time

import java.time.temporal.ChronoUnit

fun currentDateKey(): String = currentHabitDate().toString()

fun nowMillis(): Long = System.currentTimeMillis()

/** Calendar days, so a daylight-saving night never shortens the count. */
fun daysBetween(startDateKey: String, endDateKey: String): Int? {
    val start = parseDateKey(startDateKey) ?: return null
    val end = parseDateKey(endDateKey) ?: return null
    return ChronoUnit.DAYS.between(start, end).toInt()
}
