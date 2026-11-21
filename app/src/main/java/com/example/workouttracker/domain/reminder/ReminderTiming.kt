package com.example.workouttracker.domain.reminder

import java.time.LocalTime

internal fun ReminderTime.toLocalTime(): LocalTime = LocalTime.of(hour, minute)

/** [minutes] after [wake]. An offset that would reach midnight stays on the wake day. */
fun afterWake(wake: LocalTime, minutes: Long): LocalTime =
    if (wake.toSecondOfDay() + minutes * 60 >= SecondsPerDay) LocalTime.MAX else wake.plusMinutes(minutes)

private const val SecondsPerDay = 24L * 60L * 60L
