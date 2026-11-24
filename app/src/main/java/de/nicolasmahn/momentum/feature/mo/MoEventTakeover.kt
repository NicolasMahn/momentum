package de.nicolasmahn.momentum.feature.mo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.ui.LocalTrackerIdentity
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.ViewTheme
import de.nicolasmahn.momentum.core.ui.platformAnimationsEnabled
import de.nicolasmahn.momentum.core.ui.theme.MoCoin
import de.nicolasmahn.momentum.core.ui.theme.MomentumHues
import de.nicolasmahn.momentum.core.ui.viewColors
import de.nicolasmahn.momentum.domain.mo.MoEvent
import de.nicolasmahn.momentum.domain.mo.MoLevel
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.contentKey
import de.nicolasmahn.momentum.domain.mo.moHash
import de.nicolasmahn.momentum.feature.mo.acts.MoActBook
import de.nicolasmahn.momentum.feature.mo.acts.MoScene
import de.nicolasmahn.momentum.feature.mo.acts.MoVariant
import de.nicolasmahn.momentum.feature.tracker.trackerTokenEarnedLine

/**
 * A special moment takes over Home on the stage a level stands on: Mo leaps and blazes in front of
 * the rays, what he earned lands at his side, and the words under him say what happened. It is an
 * overlay and only ever drawn, so nothing under it moves; with animations off it is the final
 * frame. It is rare by design, and it waits for a tap, as the level does.
 */
@Composable
internal fun MoEventTakeover(
    event: MoEvent,
    mood: MoMood,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = platformAnimationsEnabled()
    val progress = remember(event) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(event) { progress.animateTo(1f, tween(LeapMillis, easing = LinearEasing)) }
    val words = event.words(mood)
    // He celebrates as the brightest version of himself, whatever his heat was a moment ago.
    val look = MoLook(
        state = MoState.Blazing,
        scene = MoActBook.firstOrNull { it.id == event.actId() }?.let { MoScene(it, MoVariant(moHash(event.hashCode().toLong()))) },
    )
    MomentStage(warm = true, testTag = "home_mo_event", modifier = modifier) {
        MomentKicker(event.kicker(), MoCoin)
        // Mo takes the height the words and the button leave, so nothing below him is ever squeezed out.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) { drawRays(progress.value) }
            Mo(look = look, modifier = Modifier.fillMaxHeight(StageFigureHeight).leaping { progress.value }, lift = { leapLift(progress.value) })
            event.prize()?.let { prize ->
                Canvas(
                    Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 8.dp).size(PrizeSize).landing { progress.value },
                ) { prize() }
            }
        }
        Text(
            text = words.headline.resolve(),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MoCoin,
            textAlign = TextAlign.Center,
        )
        Text(
            text = words.line.resolve(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        ViewTheme(MomentumHues.Yellow.viewColors()) {
            MomentButtons {
                MomentumButton(text = stringResource(R.string.mo_event_continue), onClick = onDone, modifier = Modifier.fillMaxWidth(), testTag = "mo_event_continue")
            }
        }
    }
}

/** The small line over Mo that names the moment, as "NEW LEVEL" names a level's. */
@Composable
private fun MoEvent.kicker(): String = when (this) {
    // These two have screens of their own; the line says the same thing for anything that reads it.
    is MoEvent.StreakLost -> stringResource(R.string.mo_lost_title)
    is MoEvent.LevelReached -> stringResource(R.string.mo_level_new, arrival.level.ordinal + 1, MoLevel.entries.size)
    is MoEvent.StreakMilestone -> stringResource(R.string.mo_event_kicker_milestone)
    MoEvent.PerfectWeek -> stringResource(R.string.mo_event_kicker_perfect_week)
    MoEvent.Comeback -> stringResource(R.string.mo_event_kicker_comeback)
    is MoEvent.WeekGoalReached -> stringResource(R.string.mo_event_kicker_week_goal)
    is MoEvent.TokenEarned -> stringResource(R.string.mo_event_kicker_token)
    MoEvent.FreezeEarned -> stringResource(R.string.mo_event_kicker_freeze)
}

internal data class EventWords(val headline: UiText, val line: UiText)

internal fun MoEvent.words(mood: MoMood): EventWords = when (this) {
    // These two have screens of their own; the words say the same thing for anything that reads them.
    is MoEvent.StreakLost -> EventWords(pluralText(R.plurals.mo_lost_days, loss.days), uiText(R.string.mo_lost_title))
    is MoEvent.LevelReached -> EventWords(uiText(arrival.level.labelRes), pluralText(R.plurals.mo_level_days, arrival.level.days))
    is MoEvent.StreakMilestone ->
        EventWords(pluralText(R.plurals.mo_event_milestone, days), uiText(R.string.mo_event_milestone_line))
    MoEvent.PerfectWeek -> EventWords(uiText(R.string.mo_event_perfect_week), uiText(R.string.mo_event_perfect_week_line))
    MoEvent.Comeback -> EventWords(uiText(R.string.mo_event_comeback), uiText(R.string.mo_event_comeback_line))
    is MoEvent.WeekGoalReached -> {
        val week = mood.weekProgressByTracker.getValue(ref)
        EventWords(
            uiText(R.string.mo_event_week_goal, week.done, week.expected),
            MoContentCatalogue[mood.contentKey(ref)].weekGoalLine(mood.label(ref)),
        )
    }
    is MoEvent.TokenEarned -> EventWords(
        uiText(R.string.mo_event_token, mood.label(ref)),
        trackerTokenEarnedLine(ref, mood) ?: uiText(R.string.mo_tracker_token_earned, mood.label(ref)),
    )
    MoEvent.FreezeEarned -> EventWords(uiText(R.string.mo_event_freeze), uiText(R.string.mo_event_freeze_line))
}

/** The act he performs on stage. Any of them may be missing from the book; he then simply blazes. */
private fun MoEvent.actId(): String = when (this) {
    is MoEvent.StreakMilestone, MoEvent.PerfectWeek, is MoEvent.LevelReached -> "proud_triumph"
    is MoEvent.StreakLost -> "proud_triumph"
    MoEvent.Comeback -> "happy_tears_of_joy"
    is MoEvent.WeekGoalReached -> "proud_triumph"
    is MoEvent.TokenEarned, MoEvent.FreezeEarned -> "proud_triumph"
}

/** The thing he hands over, drawn above him, for the events that earn one. */
@Composable
private fun MoEvent.prize(): (DrawScope.() -> Unit)? = when (this) {
    is MoEvent.TokenEarned -> {
        val identity = LocalTrackerIdentity.current(ref)
        val stamp = rememberVectorPainter(identity.icon)
        val draw: DrawScope.() -> Unit = { drawPassToken(identity.colors, stamp) }
        draw
    }
    MoEvent.FreezeEarned -> { { drawFreezeCube() } }
    else -> null
}

private val PrizeSize = 72.dp
