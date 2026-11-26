package de.nicolasmahn.momentum.feature.mo

import android.content.Context
import de.nicolasmahn.momentum.domain.mo.MoEvent

/**
 * News is what the close of a day brought: a token or a freeze earned overnight. Every other event
 * plays only when it happens while the app is open, but news arrives while nobody watches, so it
 * waits for the first look at Home that day and is then handed over once.
 */
internal val MoEvent.isNews: Boolean
    get() = this is MoEvent.TokenEarned || this == MoEvent.FreezeEarned

/**
 * The day whose news has been handed over. It is runtime state, not user data: losing it only lets
 * Mo hand the same token over once more, so it stays out of the backup.
 */
internal class MoNewsSeen(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("mo_runtime", Context.MODE_PRIVATE)

    fun isSeen(dayKey: String): Boolean = preferences.getString(SEEN_DAY, null) == dayKey

    fun markSeen(dayKey: String) {
        preferences.edit().putString(SEEN_DAY, dayKey).apply()
    }

    private companion object {
        const val SEEN_DAY = "news_seen_day"
    }
}
