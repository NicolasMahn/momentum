package de.nicolasmahn.momentum.domain.mo



import de.nicolasmahn.momentum.domain.TrackerRef

/**
 * A moment worth stopping the app for. Events are rare on purpose: most days Mo stays in his corner
 * of Home, so that when he takes over the screen it means something. Like everything about him they
 * are derived from the records and the clock, and they are only ever true on the day they happen.
 */
sealed interface MoEvent {
    /**
     * The streak's run lost [loss]'s days. Shown once, before anything else, with its own
     * acknowledgement, so no other moment can hand it over unseen.
     */
    data class StreakLost(val loss: MoStreakLoss) : MoEvent

    /** The current run reached a level, today or overnight. Shown once per run and level. */
    data class LevelReached(val arrival: MoLevelArrival) : MoEvent

    /** The streak reached a round number today that is not a level. */
    data class StreakMilestone(val days: Int) : MoEvent

    /** Every day of this week, Monday to Sunday, was perfect, and today closed it. */
    data object PerfectWeek : MoEvent

    /** Yesterday went wrong, by a freeze or a restart, and today everything is done. */
    data object Comeback : MoEvent

    /** A task reached everything its week expects, today. */
    data class WeekGoalReached(val ref: TrackerRef) : MoEvent

    /** Yesterday's close earned a token for [ref]. It is shown the next morning, once. */
    data class TokenEarned(val ref: TrackerRef) : MoEvent

    /** Yesterday's close earned a freeze. It is shown the next morning, once. */
    data object FreezeEarned : MoEvent
}

/**
 * Today's events, the biggest first. A milestone, a perfect week and a comeback need today to be
 * complete, because an event is a reward and never a promise; what yesterday earned is his to hand
 * over whatever today looks like.
 */
fun moEvents(mood: MoMood): List<MoEvent> = buildList {
    // The history ends with today.
    val today = mood.streak.history.lastOrNull()?.date
    // A lost streak and a level come first: each is the moment of its day, and neither needs today done.
    today?.let { streakLoss(mood.streak.history, it) }?.let { add(MoEvent.StreakLost(it)) }
    levelArrival(mood.streak.history)?.let { add(MoEvent.LevelReached(it)) }
    // A resting task is settled, so a rest day does not hold a reward back.
    val complete = mood.dueToday > 0 && mood.keptTrackersToday.isNotEmpty() && mood.missingTrackersToday.isEmpty() &&
        mood.keptTrackersToday.size + mood.restingTrackersToday.size == mood.dueToday
    if (complete) {
        // A number that is a level has its own screen.
        if (mood.streak.days.isRoundStreak() && !MoLevel.isThreshold(mood.streak.days)) add(MoEvent.StreakMilestone(mood.streak.days))
        if (mood.streak.week.size == 7 && mood.streak.week.all { it == MoDayMark.Perfect }) add(MoEvent.PerfectWeek)
        // Yesterday by its date, so a Sunday that went wrong is still yesterday on a Monday.
        val yesterday = today?.let { day -> mood.streak.history.lastOrNull { it.date == day.minusDays(1) }?.mark }
            ?: mood.streak.week.getOrNull(mood.streak.week.indexOfLast { it == MoDayMark.Perfect || it == MoDayMark.Open } - 1)
        val yesterdayWentWrong = mood.streak.frozenTrackersYesterday.isNotEmpty() || yesterday == MoDayMark.Missed || yesterday == MoDayMark.Frozen
        if (yesterdayWentWrong) add(MoEvent.Comeback)
    }
    mood.weekProgressByTracker.forEach { (ref, week) ->
        if (ref in mood.weeklyGoalTrackers && week.expected > 0 && week.done == week.expected && ref in mood.keptTrackersToday) {
            add(MoEvent.WeekGoalReached(ref))
        }
    }
    // Something earned is his to hand over whatever today looks like, so it needs no complete day.
    if (mood.streak.freezeEarnedYesterday) add(MoEvent.FreezeEarned)
    mood.streak.earnedTrackersYesterday.forEach { add(MoEvent.TokenEarned(it)) }
}
