package com.example.workouttracker.domain.compatibility

import com.example.workouttracker.core.time.requireValidDateKey

fun eatingDateKeyFor(todayKey: String): String {
    return requireValidDateKey(todayKey).minusDays(1).toString()
}
