package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.TrackerRef

/** Misses describe factual neglect; protection cannot erase them. */
enum class MoNeglect(internal val minimumMisses: Int) {
    Mild(2), Severe(4), Dire(6);

    internal companion object {
        fun of(misses: Int): MoNeglect? = entries.lastOrNull { misses >= it.minimumMisses }
    }
}

internal fun MoLedger.trackerNeglect(): Map<TrackerRef, MoNeglect> = buildMap {
    trackers.forEach { ref ->
        val misses = lastWeek(ref).count { it == MoStatus.Missed }
        val madeUpToday = if (history(ref)[today] == MoStatus.Kept) 1 else 0
        MoNeglect.of(misses - madeUpToday)?.let { put(ref, it) }
    }
}

internal fun MoLedger.thrivingTrackers(): Set<TrackerRef> = trackers.filterTo(LinkedHashSet()) { ref ->
    val week = lastWeek(ref)
    MoStatus.Missed !in week && week.count { it == MoStatus.Kept } >= 3
}

private fun MoLedger.lastWeek(ref: TrackerRef): List<MoStatus> {
    val history = history(ref)
    return finishedDays(today.minusDays(7)).mapNotNull { history[it] }.toList()
}
