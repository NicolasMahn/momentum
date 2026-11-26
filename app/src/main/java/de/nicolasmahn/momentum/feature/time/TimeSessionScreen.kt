package de.nicolasmahn.momentum.feature.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumDialog
import de.nicolasmahn.momentum.core.ui.MomentumDialogAction
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.ui.ReservedMessage
import de.nicolasmahn.momentum.core.ui.ViewColors
import de.nicolasmahn.momentum.core.ui.ViewTheme
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.time.TimeSession
import de.nicolasmahn.momentum.domain.time.minutesAt
import de.nicolasmahn.momentum.feature.mo.Mo
import de.nicolasmahn.momentum.feature.mo.MoLook
import kotlinx.coroutines.delay

/**
 * How one time view's session, and the question that may come before it, look and speak: the
 * view's words and colors, how Mo keeps company, which follows the view's flavor, and the prefix
 * of its test tags.
 */
internal class TimeSessionAppearance(
    val words: TimeWords,
    val colors: ViewColors,
    val mo: MoLook,
    val testTagPrefix: String,
)

/**
 * The running session, full screen, whichever time view it is of. Done and Back end it and save;
 * leaving the app for anything else ends it too, at that moment. Turning the screen off does not:
 * what is timed may well happen away from the phone, as reading on paper does.
 *
 * [onSave] stores a session's minutes and returns how many, or null when saving failed.
 */
@Composable
internal fun TimeSessionHost(
    sessions: TimeSessions,
    session: TimeSession,
    appearance: TimeSessionAppearance,
    onSave: (TimeSession, Long) -> Int?,
) {
    val context = LocalContext.current
    val save by rememberUpdatedState(onSave)

    // The whole app going to the background, not one activity stopping: a second window of the
    // app or a configuration change stops an activity without anyone leaving.
    DisposableEffect(sessions) {
        val app = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_STOP) return@LifecycleEventObserver
            val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
            if (!sessionContinuesInBackground(interactive)) sessions.end(leftApp = true, save = save)
        }
        app.addObserver(observer)
        // The screen coming back on and being unlocked ends it too, unless it is to look
        // at this screen: a phone unlocked straight into another app never brings Momentum back.
        val handler = Handler(Looper.getMainLooper())
        val unlock = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val unlockedAt = System.currentTimeMillis()
                handler.postDelayed({
                    val inForeground = app.currentState.isAtLeast(Lifecycle.State.STARTED)
                    if (sessionEndsAfterUnlock(inForeground)) sessions.end(leftApp = true, save = save, endedAt = unlockedAt)
                }, UnlockGraceMillis)
            }
        }
        ContextCompat.registerReceiver(
            context,
            unlock,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            app.removeObserver(observer)
            context.unregisterReceiver(unlock)
            handler.removeCallbacksAndMessages(null)
        }
    }
    val done = { sessions.end(leftApp = false, save = save) }
    BackHandler { done() }

    TimeSessionScreen(
        minutes = rememberMinutesTimed(session),
        // Only a session whose save failed is still here after it ended.
        saveFailed = session.endedAtMillis != null,
        appearance = appearance,
        onDone = { done() },
    )
}

/**
 * A calm room to stay in: Mo keeping company using the configured motif, how long
 * this has been going on in small type, and one way out. Nothing on it moves except Mo's quiet
 * idle, and the count only changes once a minute, so there are no seconds to watch.
 */
@Composable
internal fun TimeSessionScreen(
    minutes: Int,
    saveFailed: Boolean,
    appearance: TimeSessionAppearance,
    onDone: () -> Unit,
) {
    val words = appearance.words
    val testTagPrefix = appearance.testTagPrefix
    ViewTheme(appearance.colors) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("${testTagPrefix}_session"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Mo(look = appearance.mo, modifier = Modifier.fillMaxWidth(MoWidthFraction))
            }
            Text(
                text = words.sessionMinutes(minutes).resolve(),
                modifier = Modifier.testTag("${testTagPrefix}_session_minutes"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReservedMessage(
                text = if (saveFailed) words.saveFailed.resolve() else null,
                testTag = "${testTagPrefix}_session_error",
            )
            MomentumButton(
                text = words.sessionDone.resolve(),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                variant = MomentumButtonVariant.Secondary,
                testTag = "${testTagPrefix}_session_done",
            )
        }
    }
}

/**
 * Asked once for the app, the first time a session of any view starts without Do Not Disturb
 * access. Android only grants it in its own settings, so Allow goes there and the session starts
 * on the way back, with the phone quieted if access was given.
 *
 * It asks in the words and the colors of the view whose start is waiting, under that view's tags:
 * [appearance] answers for a view's ID, and with null for one that is gone.
 */
@Composable
internal fun TimeFocusQuestion(sessions: TimeSessions, appearance: @Composable (viewId: String) -> TimeSessionAppearance?) {
    // The view whose session starts on the way back from Android's settings. It is kept as the
    // launcher's own registration is, so a process that died over there still starts it.
    var startOnReturn by rememberSaveable { mutableStateOf<String?>(null) }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        startOnReturn?.let(sessions::start)
        startOnReturn = null
    }
    val viewId = sessions.askingForFocus ?: return
    val asking = appearance(viewId) ?: return
    val words = asking.words
    val testTagPrefix = asking.testTagPrefix
    ViewTheme(asking.colors) {
        MomentumDialog(
            onDismissRequest = sessions::dismissFocusQuestion,
            message = words.focusMessage.resolve(),
            confirm = MomentumDialogAction(
                text = words.focusAllow.resolve(),
                onClick = {
                    sessions.answerFocusQuestion()
                    startOnReturn = viewId
                    if (runCatching { settings.launch(doNotDisturbAccessSettings()) }.isFailure) {
                        startOnReturn = null
                        sessions.start(viewId)
                    }
                },
                testTag = "${testTagPrefix}_focus_allow",
            ),
            dismiss = MomentumDialogAction(
                text = words.focusWithout.resolve(),
                onClick = {
                    sessions.answerFocusQuestion()
                    sessions.start(viewId)
                },
                testTag = "${testTagPrefix}_focus_without",
            ),
            modifier = Modifier.testTag("${testTagPrefix}_focus_dialog"),
        )
    }
}

/**
 * Whole minutes timed, recounted at each minute of the session and whenever the app comes back to
 * the front: a phone asleep in deep sleep lets a timer run late, never the clock.
 */
@Composable
private fun rememberMinutesTimed(session: TimeSession): Int {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val minutes by produceState(session.minutesAt(System.currentTimeMillis()), session, lifecycle) {
        while (true) {
            val now = System.currentTimeMillis()
            value = session.minutesAt(now)
            if (session.endedAtMillis != null) break
            delay(MillisPerMinute - Math.floorMod(now - session.startedAtMillis, MillisPerMinute))
        }
    }
    return minutes
}

/**
 * Mo as he is on an ordinary day, with nothing to act out: how he keeps company while a view of
 * the flavor Plain is timed, which a view a person made starts on.
 */
internal val CalmMo = MoLook(state = MoState.Steady)
private const val MoWidthFraction = 0.6f
private const val MillisPerMinute = 60_000L
