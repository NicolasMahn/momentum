package de.nicolasmahn.momentum.feature.mo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.ui.platformAnimationsEnabled
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.mo.contentKey
import de.nicolasmahn.momentum.domain.mo.figure
import de.nicolasmahn.momentum.feature.mo.acts.MoScene
import de.nicolasmahn.momentum.feature.mo.acts.bodyKind
import de.nicolasmahn.momentum.feature.mo.acts.moDoingScene
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The top of Home: the streak as a score, then Mo as large as the screen allows. He says nothing
 * here; what he has to say arrives as his check-in notification.
 *
 * [celebrate] identifies a newly completed tracker. He hops for it, and while he burns he acts it
 * out for a few seconds (see [MoSaveReaction]). [onCelebrated] is called as he reacts to it, so
 * the same save is not found waiting again when Home is left and returned to. A plain visit never
 * replays an earlier completion: out of the blue it would read as a non sequitur, not as a reaction
 * to anything.
 */
@Composable
internal fun MoHeader(
    mood: MoMood,
    /** The height under the score, all of it his. */
    moHeight: Dp,
    modifier: Modifier = Modifier,
    angry: Boolean = false,
    scene: MoScene? = null,
    celebrate: TrackerRef? = null,
    /** Which of an activity's acts a save plays today (see [MoPicture.saveTurn]). */
    saveTurn: Int = 0,
    onCelebrated: () -> Unit = {},
    onOpenStreak: () -> Unit = {},
    /**
     * How much higher he stands than the room under the score would put him, at the same size: the
     * header is that much shorter, and the top of his canvas, empty unless he blazes, reaches up past it.
     */
    lift: Dp = 0.dp,
    /**
     * His reaction to a save. Handed in only to be watched from outside, by a test, and never
     * kept beyond this header: a new Home starts with a new one.
     */
    reaction: MoSaveReaction = remember { MoSaveReaction() },
) {
    val motion = platformAnimationsEnabled()
    // How he is without the save: what decides whether he acts it out.
    val plain = remember(mood, angry) { MoLook.of(mood, angry = angry) }
    // A save is read after a wait, by which time he and the day may be newer ones.
    val current by rememberUpdatedState(plain)
    val currentMood by rememberUpdatedState(mood)
    val currentTurn by rememberUpdatedState(saveTurn)
    val celebrated by rememberUpdatedState(onCelebrated)

    LaunchedEffect(celebrate) {
        val need = celebrate ?: return@LaunchedEffect
        // Home is still sliding in when he first appears; wait so the moment is actually seen.
        if (motion) delay(ARRIVAL_MILLIS)
        reaction.accept(currentMood.contentKey(need).figure.saveAct, currentTurn, current)
        // Taken the moment he reacts to it. What follows runs on without it, so that a Home left
        // in the middle of the hop does not find the same save waiting when it is returned to.
        celebrated()
    }
    // For as long as he is here, and not for as long as a save waits: the save is off the list
    // the moment he takes it in, and the hop it started still has to come down.
    val animated by rememberUpdatedState(motion)
    LaunchedEffect(reaction) { reaction.drive { animated } }
    LaunchedEffect(reaction.playing) {
        reaction.playing?.let {
            delay(BUSY_MILLIS)
            reaction.expire(it)
        }
    }
    LaunchedEffect(reaction.playing, plain) { reaction.keepWhile(plain) }

    val body = mood.state.bodyKind()
    // The frame in which he stops acting it out already shows his day.
    val moment = reaction.playing?.takeIf { plain.actsOut(it.activity) }
    val doingScene = remember(moment, body) { moment?.let { moDoingScene(it.activity, body, it.turn) } }
    val moDescription = mood.state.description().resolve()

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box(contentAlignment = Alignment.Center) {
                // The fire comes out from behind his outline so his face stays visible at ignition.
                // Drawn past its bounds on purpose: the fire he bursts into is bigger than he is.
                Canvas(Modifier.matchParentSize()) {
                    if (reaction.burst.value < 1f) drawFireBurst(reaction.burst.value)
                }
                Mo(
                    // The activity is the one the save was accepted with, for choosing the act and
                    // for drawing it: a task given another motif meanwhile changes neither.
                    look = plain.copy(doing = moment?.activity, scene = doingScene ?: scene),
                    modifier = Modifier
                        .fillMaxHeight()
                        .graphicsLayer {
                            val swell = sin(reaction.burst.value * PI.toFloat())
                            scaleX = 1f + swell * 0.065f
                            scaleY = 1f + reaction.hop.value * 0.06f + swell * 0.075f
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            // With his day's act around him the whole picture hops as one, as it
                            // always has: its bed, its visitors and its ice were not written to be
                            // left standing while he leaves the floor.
                            if (!reaction.hopsAlone) translationY = -reaction.hop.value * HopHeight.toPx()
                        }
                        .testTag("home_mo")
                        .semantics { contentDescription = moDescription },
                    // An act for a save is written for the hop: only he leaves the floor.
                    lift = { if (reaction.hopsAlone) reaction.hop.value * HopHeight.toPx() else 0f },
                    // What he acts out starts at its first moment, and so does his day when he goes back to it.
                    restart = moment,
                )
            }
            MoStreakBar(mood.streak, onExplain = onOpenStreak)
        },
    ) { (figure, bar), constraints ->
        val score = bar.measure(constraints.copy(minHeight = 0))
        val gap = ScoreGap.roundToPx()
        val below = moHeight.roundToPx()
        // His canvas keeps room above even a blazing flame. Tucked behind the score, that room no
        // longer reads as a gap above him, and he grows into the height it frees.
        val tuck = minOf((below * MoHeadroom / (1f - MoHeadroom)).roundToInt(), score.height + gap)
        val figureHeight = below + tuck
        val drawn = figure.measure(Constraints.fixed(constraints.maxWidth, figureHeight))
        val height = score.height + gap + below - lift.roundToPx()
        layout(constraints.maxWidth, height) {
            drawn.place(0, height - figureHeight)
            // Placed last, so the score stays on top of whatever he draws up there.
            score.place((constraints.maxWidth - score.width) / 2, 0)
        }
    }
}

private val ScoreGap = 4.dp
private val HopHeight = 18.dp

private const val ARRIVAL_MILLIS = 450L
private const val BUSY_MILLIS = 9_000L
