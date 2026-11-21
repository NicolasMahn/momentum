package com.example.workouttracker.feature.mo

import android.content.Context
import com.example.workouttracker.domain.mo.MoEvent

/**
 * The key a moment of its own is acknowledged by, or null for an event handed over with the
 * others. A lost streak and a level each wait for their own screen to be seen, so no other event
 * can hand them over unseen, and seeing them hands nothing else over.
 */
internal val MoEvent.momentKey: String?
    get() = when (this) {
        is MoEvent.StreakLost -> "lost/${loss.seenKey}"
        is MoEvent.LevelReached -> "level/${arrival.seenKey}"
        else -> null
    }

/** Which of today's events have had their moment: the ordinary ones this session, the moments for good. */
internal data class MoEventLedger(
    val celebrated: Set<MoEvent> = emptySet(),
    val momentsSeen: Set<String> = emptySet(),
) {
    /** The event to show now, the first still waiting in [events]' order. */
    fun next(events: List<MoEvent>): MoEvent? = events.firstOrNull { event ->
        event.momentKey?.let { it !in momentsSeen } ?: (event !in celebrated)
    }

    /**
     * [event] was shown. A moment is acknowledged alone; an ordinary event hands over every ordinary
     * event waiting with it, as before, but never a moment.
     */
    fun handedOver(event: MoEvent, events: List<MoEvent>): MoEventLedger = when (val key = event.momentKey) {
        null -> copy(celebrated = celebrated + events.filter { it.momentKey == null })
        else -> copy(momentsSeen = momentsSeen + key)
    }
}

/**
 * The moments already shown. Runtime state, not user data: losing it only shows a loss or a
 * level once more, so it stays out of the backup.
 */
internal class MoMomentsSeen(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("mo_runtime", Context.MODE_PRIVATE)

    fun all(): Set<String> = preferences.getStringSet(SEEN_MOMENTS, emptySet()).orEmpty().toSet()

    fun markSeen(key: String) {
        preferences.edit().putStringSet(SEEN_MOMENTS, all() + key).apply()
    }

    private companion object {
        const val SEEN_MOMENTS = "moments_seen"
    }
}
