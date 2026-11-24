package de.nicolasmahn.momentum.feature.logactivity.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.ui.LocalViewColors
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumRemoveButton
import de.nicolasmahn.momentum.core.ui.ReservedMessage
import de.nicolasmahn.momentum.domain.activity.route.Route
import de.nicolasmahn.momentum.domain.activity.route.RouteRecording
import de.nicolasmahn.momentum.domain.activity.route.kilometersPerHour
import de.nicolasmahn.momentum.domain.activity.route.secondsPerKilometer
import kotlinx.coroutines.delay

/**
 * The recording under way, in place of the form while it lasts: what it is of, its distance and
 * moving time large, its pace and speed beside them, the route so far on the map, and what can be
 * done with it. Pause and Resume share one place, so the buttons never move under the thumb;
 * Finish, the end of it all, is the one wide button under them.
 * Discard is the two-step remove every removal is. [message] says what the person must do for the
 * recording to measure, such as allow location, in the reserved line under the buttons.
 */
@Composable
internal fun RoutePanel(
    recording: RouteRecording,
    activityLabel: String,
    lastFixMillis: Long?,
    message: String?,
    /** Every line [message] may be, so the reserved space fits the longest. */
    reservedMessages: List<String> = emptyList(),
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The clock moves every second while the recording runs; the route itself changes only when a point is kept.
    val now by produceState(System.currentTimeMillis(), recording.paused) {
        while (!recording.paused) {
            value = System.currentTimeMillis()
            delay(1_000L - value % 1_000L)
        }
        value = System.currentTimeMillis()
    }
    val route = recording.route
    // The live clock counts on between points while the runner moves, and learns of a stop only
    // from the next point, seconds later. It holds what it showed rather than run backwards; paused,
    // it shows the measured time that Finish saves.
    val shownMoving = remember(recording.startedAtMillis) { mutableLongStateOf(0L) }
    val measured = route.movingSecondsAt(recording.secondsIn(now), recording = !recording.paused)
    val moving = if (recording.paused) measured else maxOf(shownMoving.longValue, measured)
    SideEffect { shownMoving.longValue = moving }
    val searching = !recording.paused && (lastFixMillis == null || now - lastFixMillis > SearchingAfterMillis)
    val colors = LocalViewColors.current

    Column(modifier = modifier.fillMaxWidth().testTag("activity_route_panel"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = when {
                recording.paused -> stringResource(R.string.activity_route_paused, activityLabel)
                searching -> stringResource(R.string.activity_route_searching, activityLabel)
                else -> stringResource(R.string.activity_route_recording, activityLabel)
            },
            style = MaterialTheme.typography.titleMedium,
            color = colors.label,
            modifier = Modifier.testTag("activity_route_state"),
        )
        RouteNumbers(route = route, movingSeconds = moving, large = true)
        RouteMap(route = route)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MomentumButton(
                text = stringResource(if (recording.paused) R.string.activity_route_resume else R.string.activity_route_pause),
                onClick = if (recording.paused) onResume else onPause,
                variant = MomentumButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
                testTag = "activity_route_pause",
            )
            MomentumRemoveButton(
                description = stringResource(R.string.activity_route_discard),
                onRemove = onDiscard,
                testTag = "activity_route_discard",
                resetKey = recording.startedAtMillis,
            )
        }
        // A row of its own: in German or at a large font, half a row breaks the word.
        MomentumButton(
            text = stringResource(R.string.activity_route_finish),
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            testTag = "activity_route_finish",
        )
        ReservedMessage(text = message, testTag = "activity_route_message", reserving = reservedMessages)
    }
}

/**
 * A route's numbers: distance and moving time, then its pace, which a runner reads, and its speed,
 * which a cyclist reads. Before it went anywhere measurable the pace and speed are a dash, rather
 * than something GPS noise made up. [large] is the live recording, read at a glance mid-run.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RouteNumbers(route: Route, movingSeconds: Long, large: Boolean, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val big = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge
    val pace = route.secondsPerKilometer(movingSeconds)
    val speed = route.kilometersPerHour(movingSeconds)
    Column(modifier = modifier.testTag("activity_route_numbers"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Side by side where they fit; a long run at a large font puts the time under the distance.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                text = stringResource(R.string.activity_route_kilometers, formatKilometers(route.distanceMeters, locale)),
                style = big,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("activity_route_distance"),
            )
            Text(
                text = formatClock(movingSeconds),
                style = big,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("activity_route_time"),
            )
        }
        // Always there, a dash until it can be measured, so nothing under it moves when it appears.
        Text(
            text = stringResource(R.string.activity_route_pace_and_speed, pace?.let(::formatClock) ?: NotYet, speed?.let { formatSpeed(it, locale) } ?: NotYet),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("activity_route_pace"),
        )
    }
}

private const val NotYet = "–"

/** Without a fix for this long the phone is looking for satellites again, under a roof or in a tunnel. */
private const val SearchingAfterMillis = 15_000L
