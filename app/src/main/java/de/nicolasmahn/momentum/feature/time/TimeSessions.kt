package de.nicolasmahn.momentum.feature.time

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.nicolasmahn.momentum.domain.time.TimeSession

/**
 * What the last session of a view left to explain, for the quiet line on that view's screen: the
 * day it belonged to, the minutes it saved and whether it ended because the app was left.
 */
internal data class TimeSessionNote(
    val viewId: String,
    val dateKey: String,
    val minutes: Int,
    val leftApp: Boolean,
) {
    /** False when there is nothing to explain: a session ended on purpose that saved its minutes shows in the week bar itself. */
    val explainsSomething: Boolean get() = leftApp || minutes == 0
}

/**
 * The app going to the background while the screen is off is the phone put down beside what is
 * being done; with the screen on, it is switching to something else: Home, recents, another app,
 * a call.
 */
internal fun sessionContinuesInBackground(screenInteractive: Boolean): Boolean = !screenInteractive

/**
 * Unlocking with the session's screen on top brings the app straight back. Unlocking into
 * anything else leaves it in the background, and then the phone is being used: the session ended
 * at the unlock.
 */
internal fun sessionEndsAfterUnlock(appInForeground: Boolean): Boolean = !appInForeground

/** How long an unlock may take to bring the app back before it counts as using the phone. */
internal const val UnlockGraceMillis = 4_000L

/** Where a session survives its process. See [PreferencesTimeSessionStore]. */
internal interface TimeSessionStore {
    val session: TimeSession?

    /** Replaces the stored session, or clears it with null. Returns whether the write committed. */
    fun writeSession(session: TimeSession?): Boolean

    var focusChange: FocusChange?
    var focusAsked: Boolean
    var note: TimeSessionNote?
}

/**
 * The one owner of the timed session, whichever time view it is of: starting it, quieting the
 * phone, and ending it however it ends. One session runs at a time in the whole app, so a start
 * asked for while one runs is not taken. Everything it knows is written to [store] before it acts,
 * so a process that dies in between leaves enough behind to resume the session or put Do Not
 * Disturb back.
 */
internal class TimeSessions(
    private val store: TimeSessionStore,
    private val doNotDisturb: DoNotDisturb,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var session: TimeSession? by mutableStateOf(store.session)
        private set

    var note: TimeSessionNote? by mutableStateOf(store.note)
        private set

    /**
     * The view whose start waits for the one-time question about Do Not Disturb access, while that
     * question is on screen; null otherwise. The question is asked once for the app, whichever
     * view asks first.
     */
    var askingForFocus: String? by mutableStateOf(null)
        private set

    init {
        // A change without a session was left behind by a process that died while ending one.
        if (store.session == null) restoreFocus()
    }

    /** Starts a session of [viewId] at once, unless this is the first start without Do Not Disturb access. */
    fun requestStart(viewId: String) {
        if (session != null) return
        if (!doNotDisturb.hasAccess && !store.focusAsked) askingForFocus = viewId else start(viewId)
    }

    /** Answers the question for good; [start] follows once the answer has been acted on. */
    fun answerFocusQuestion() {
        store.focusAsked = true
        askingForFocus = null
    }

    fun dismissFocusQuestion() {
        askingForFocus = null
    }

    fun start(viewId: String) {
        if (session != null) return
        val started = TimeSession.startingAt(viewId, now())
        // The session still starts if this write fails; only a process death before the end loses it.
        store.writeSession(started)
        dismissNote()
        quietThePhone()
        session = started
    }

    /**
     * Ends the session at [endedAt] and hands it to [save], which stores its minutes and returns
     * how many, or null when saving failed. Anything that fails keeps the session, stopped at
     * [endedAt], so trying again can neither lose the minutes nor add any. Returns whether it was
     * saved.
     *
     * The stopped session is stored before its minutes are saved, so a process that dies between
     * the two comes back to the same stopped session, and [save] recognises it as already recorded
     * (see finishTimeSession). Its minutes are never saved before that write has committed.
     */
    fun end(
        leftApp: Boolean,
        save: (TimeSession, Long) -> Int?,
        endedAt: Long = now(),
    ): Boolean {
        val current = session ?: return true
        val endMillis = current.endedAtMillis ?: endedAt
        val stopped = current.copy(endedAtMillis = endMillis)
        // The session is over either way, so the phone gets its sound back before anything can fail.
        restoreFocus()
        session = stopped
        if (!store.writeSession(stopped)) return false
        val saved = save(stopped, endMillis) ?: return false
        // A failed clear leaves the stopped session behind, which saving again adds nothing for.
        store.writeSession(null)
        session = null
        val ended = TimeSessionNote(stopped.viewId, stopped.dateKey, saved, leftApp).takeIf { it.explainsSomething }
        store.note = ended
        note = ended
        return true
    }

    fun dismissNote() {
        if (note == null && store.note == null) return
        store.note = null
        note = null
    }

    private fun quietThePhone() {
        if (store.focusChange != null) return
        val change = runCatching { focusChangeFor(doNotDisturb.filter, doNotDisturb.hasAccess) }.getOrNull() ?: return
        // Written first: if the process dies right after, the next start knows what to put back.
        store.focusChange = change
        if (runCatching { doNotDisturb.filter = change.sessionFilter }.isFailure) store.focusChange = null
    }

    companion object {
        @Volatile
        private var instance: TimeSessions? = null

        /**
         * One per process: two windows of the app, or a window and a lifecycle callback, must
         * agree on whether a session runs.
         */
        fun of(context: Context): TimeSessions = instance ?: synchronized(this) {
            instance ?: TimeSessions(
                PreferencesTimeSessionStore(context),
                AndroidDoNotDisturb(context),
            ).also { instance = it }
        }
    }

    private fun restoreFocus() {
        val change = store.focusChange ?: return
        runCatching {
            filterToRestore(change, doNotDisturb.filter)?.let { doNotDisturb.filter = it }
        }
        store.focusChange = null
    }
}

/**
 * The running session, what Do Not Disturb was before it, and whether access was asked for. It is
 * runtime state, not user data, so it stays out of the backup: losing it can at worst end one
 * session early or leave Do Not Disturb on for him to turn off. The minutes a session
 * saves are user data and go through the tracker repository like every other record.
 */
internal class PreferencesTimeSessionStore(context: Context) : TimeSessionStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override val session: TimeSession?
        get() {
            val started = preferences.getLong(STARTED, NONE)
            val dateKey = preferences.getString(DATE, null)
            if (started == NONE || dateKey == null) return null
            val ended = preferences.getLong(ENDED, NONE).takeIf { it != NONE }
            val viewId = preferences.getString(VIEW, null)?.takeIf(String::isNotBlank) ?: return null
            return TimeSession(viewId, started, dateKey, ended)
        }

    override fun writeSession(session: TimeSession?): Boolean = runCatching {
        preferences.edit()
            .putString(VIEW, session?.viewId)
            .putLong(STARTED, session?.startedAtMillis ?: NONE)
            .putString(DATE, session?.dateKey)
            .putLong(ENDED, session?.endedAtMillis ?: NONE)
            .commit()
    }.getOrDefault(false)

    override var focusChange: FocusChange?
        get() {
            val previous = preferences.getInt(PREVIOUS_FILTER, NO_FILTER)
            val during = preferences.getInt(SESSION_FILTER, NO_FILTER)
            return if (previous == NO_FILTER || during == NO_FILTER) null else FocusChange(previous, during)
        }
        set(value) {
            preferences.edit()
                .putInt(PREVIOUS_FILTER, value?.previousFilter ?: NO_FILTER)
                .putInt(SESSION_FILTER, value?.sessionFilter ?: NO_FILTER)
                .commit()
        }

    override var focusAsked: Boolean
        get() = preferences.getBoolean(FOCUS_ASKED, false)
        set(value) {
            preferences.edit().putBoolean(FOCUS_ASKED, value).apply()
        }

    override var note: TimeSessionNote?
        get() {
            val dateKey = preferences.getString(NOTE_DATE, null) ?: return null
            val viewId = preferences.getString(NOTE_VIEW, null)?.takeIf(String::isNotBlank) ?: return null
            return TimeSessionNote(
                viewId = viewId,
                dateKey = dateKey,
                minutes = preferences.getInt(NOTE_MINUTES, 0),
                leftApp = preferences.getBoolean(NOTE_LEFT_APP, false),
            )
        }
        set(value) {
            preferences.edit()
                .putString(NOTE_VIEW, value?.viewId)
                .putString(NOTE_DATE, value?.dateKey)
                .putInt(NOTE_MINUTES, value?.minutes ?: 0)
                .putBoolean(NOTE_LEFT_APP, value?.leftApp ?: false)
                .apply()
        }

    internal companion object {
        const val PreferencesName = "reading_session_runtime"
        const val VIEW = "view_id"
        const val STARTED = "started_at"
        const val DATE = "date_key"
        const val ENDED = "ended_at"
        const val PREVIOUS_FILTER = "previous_filter"
        const val SESSION_FILTER = "session_filter"
        const val FOCUS_ASKED = "focus_asked"
        const val NOTE_VIEW = "note_view_id"
        const val NOTE_DATE = "note_date_key"
        const val NOTE_MINUTES = "note_minutes"
        const val NOTE_LEFT_APP = "note_left_app"
        const val NONE = -1L
        const val NO_FILTER = -1
    }
}
