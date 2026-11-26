package de.nicolasmahn.momentum.feature.time

import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import de.nicolasmahn.momentum.domain.time.TimeSession

/**
 * A session owner that keeps everything in memory, on a phone that gave Do Not Disturb access or
 * did not. It starts with [stored], a session an earlier process left behind, or with none.
 */
internal fun testTimeSessions(hasDoNotDisturbAccess: Boolean = false, now: () -> Long = System::currentTimeMillis, stored: TimeSession? = null) = TimeSessions(
    store = object : TimeSessionStore {
        override var session: TimeSession? = stored
            private set

        override fun writeSession(session: TimeSession?): Boolean {
            this.session = session
            return true
        }

        override var focusChange: FocusChange? = null
        override var focusAsked = false
        override var note: TimeSessionNote? = null
    },
    doNotDisturb = object : DoNotDisturb {
        override val hasAccess = hasDoNotDisturbAccess
        override var filter = INTERRUPTION_FILTER_ALL
    },
    now = now,
)
