package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.feature.tracker.TrackerHomeStatus

/** Where a row stands on Home: groups top to bottom, and within one by the minute [homeRowRank] gives. */
internal enum class HomeRowGroup {
    /** Open and due within the hour, or already past its time: the one that has waited longest first. */
    Due,

    /** Open, with no time of its own. */
    Untimed,

    /** Open, due later today: the earliest first. */
    Later,

    /** Nothing asked of it today. */
    NothingAsked,

    /** Kept today, resting, or covered by a token. */
    Kept,
}

/** How soon an open task counts as next: due within this many minutes, or already past due. */
internal const val HomeDueSoonMinutes = 60

/**
 * The group of a row whose task stands at [status], with its open parts due at [dueMinutes]
 * (minutes since the habit day began), at [nowMinute], and the minute it sorts by within the
 * group. Due tasks stand in the order of the day, by their earliest open part: the one that has
 * waited longest is first, the one Mo is cross about, and a task that just came due joins below it.
 *
 * A task a free day would cover stands by its time like any other: Mo asks for it at that time and
 * its reminder comes then, so Home must not say nothing is asked. Saying it rests is what settles it.
 */
internal fun homeRowRank(status: TrackerHomeStatus, dueMinutes: List<Int>, nowMinute: Int): Pair<HomeRowGroup, Int> = when (status) {
    TrackerHomeStatus.Done, TrackerHomeStatus.Kept, TrackerHomeStatus.Rest, TrackerHomeStatus.Covered -> HomeRowGroup.Kept to 0
    TrackerHomeStatus.None -> HomeRowGroup.NothingAsked to 0
    is TrackerHomeStatus.Open -> {
        val soon = dueMinutes.filter { it <= nowMinute + HomeDueSoonMinutes }
        when {
            dueMinutes.isEmpty() -> HomeRowGroup.Untimed to 0
            soon.isNotEmpty() -> HomeRowGroup.Due to soon.min()
            else -> HomeRowGroup.Later to dueMinutes.min()
        }
    }
}
