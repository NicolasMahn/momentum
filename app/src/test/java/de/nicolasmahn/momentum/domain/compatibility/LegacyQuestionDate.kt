package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.core.time.requireValidDateKey

fun eatingDateKeyFor(todayKey: String): String {
    return requireValidDateKey(todayKey).minusDays(1).toString()
}
