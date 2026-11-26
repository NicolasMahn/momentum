package de.nicolasmahn.momentum.feature.mo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import de.nicolasmahn.momentum.domain.mo.MoSaveAct
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** One save being acted out. A new one for every save he acts out, so the same task saved twice starts twice. */
internal class MoSavePlayed(val activity: MoSaveAct, val turn: Int)

/**
 * What Mo on Home does about a save: whether he acts it out, and the hop or the burst of fire that
 * greets it.
 *
 * It is kept apart from the header that draws it so that it can be followed step by step. A save
 * comes in at any moment, he changes while it plays, another save arrives in the middle of the hop,
 * animations are switched off; at each of these there has to be one thing that happens next, and
 * his motion has to end at rest.
 *
 * It lives as long as the header it belongs to, which waits for the save to arrive and counts its
 * nine seconds. Handed on to a second header while a save is being acted out, it would have that
 * one act the save out again from the start.
 */
@Stable
internal class MoSaveReaction {
    /** The save he is acting out, or null. */
    var playing: MoSavePlayed? by mutableStateOf(null)
        private set

    /** 0 on the floor, 1 at the top of his hop. As he lands, the spring dips a little below 0. */
    val hop = Animatable(0f)

    /** 0 as he bursts into fire, 1 once the fire is gone. */
    val burst = Animatable(1f)

    /**
     * Whether a hop takes only him off the floor, which an act for a save is written for, or his
     * whole picture as one, which his day's act needs. It only ever changes while he is on the
     * floor: changing it in the air would move the floor under him by the height he is at.
     */
    var hopsAlone: Boolean by mutableStateOf(false)
        private set

    /**
     * A save he will act out once he stands. It reached him while his whole picture was in the
     * air, and what a save shows stands on the floor: shown at once, it would come down with him.
     * Until then a later save may take its place, and it is dropped if he can no longer act it
     * out or if Home is left.
     */
    private var coming: MoSavePlayed? = null

    /**
     * Goes up with every save he takes in, which [drive] watches. It is a count and not a list:
     * taken in one at a time, as the header does it, each save gets its motion.
     */
    var reactions: Int by mutableIntStateOf(0)
        private set

    /** A save he acts out, or is about to, has him hop alone. */
    private val wantsAlone: Boolean get() = (coming ?: playing) != null
    private var bursting = false
    private var moved = 0

    /**
     * A save has arrived. Whether he acts it out is decided here, once, by how he [look]s now. A
     * save he does not act out is not kept for later, and leaves alone whatever is playing, be that
     * his day or an earlier save. He is moved by it all the same, see [drive].
     */
    fun accept(activity: MoSaveAct, turn: Int, look: MoLook) {
        if (look.actsOut(activity)) {
            val save = MoSavePlayed(activity, turn)
            // Lifted alone or standing, the floor is where it belongs, and standing is also where
            // his lift may change. So what he plays and how he is lifted change in one step.
            if (hopsAlone || hop.value == 0f) {
                hopsAlone = true
                coming = null
                playing = save
            } else {
                coming = save
            }
        }
        bursting = activity == MoSaveAct.Running
        reactions++
    }

    /**
     * He may lose his temper or go cold while he is at it. He stops acting the save out then, for
     * good. A hop or a fire it started still runs to its end.
     */
    fun keepWhile(look: MoLook) {
        if (playing?.let { look.actsOut(it.activity) } == false) playing = null
        if (coming?.let { look.actsOut(it.activity) } == false) coming = null
    }

    /** [moment] has had its time. A later save that took its place stays. */
    fun expire(moment: MoSavePlayed) {
        if (playing === moment) playing = null
    }

    /**
     * Moves him when he takes a save in, for as long as the header is there: a hop, or for a
     * workout a burst of fire. If [animated] says animations are off when it comes to the save, he
     * is put at rest instead, and so he is when the phone switches them off while he moves.
     *
     * A save that arrives in the middle of the last one's motion takes over from wherever that
     * left off, and whatever takes over ends at rest, so that nothing is ever left half done: he is
     * on the floor again and the fire is out.
     */
    suspend fun drive(animated: () -> Boolean) {
        // What every motion here runs by. At nothing, a motion is over in its first frame: the
        // hop that is still to come after a touchdown would show him at the top for one frame
        // and on the floor in the next.
        val pace = currentCoroutineContext()[MotionDurationScale]
        snapshotFlow { reactions to (animated() && (pace?.scaleFactor ?: 1f) > 0f) }.collectLatest { (count, moving) ->
            val fresh = count != moved
            moved = count
            when {
                !moving -> rest()
                fresh -> react()
                // Nothing new: only what an earlier stay left unfinished is brought to rest.
                else -> settle()
            }
        }
    }

    private suspend fun rest() {
        hop.snapTo(0f)
        burst.snapTo(1f)
        stand()
    }

    private suspend fun settle() = coroutineScope {
        launch { burnOut() }
        hop.animateTo(0f, Landing)
        stand()
    }

    /** Only ever on the floor: there the way he is lifted and what he acts out may change. */
    private fun stand() {
        hopsAlone = wantsAlone
        coming?.let { playing = it }
        coming = null
    }

    private suspend fun react() = coroutineScope {
        // A fire still out from the last save burns on through everything that follows here,
        // his touching down included, until it is gone or a workout lights a new one.
        val embers = launch { burnOut() }
        // This save lifts him the other way. If he is still in the air from the last one he
        // comes down first, lifted the way he was when he left the floor.
        if (hopsAlone != wantsAlone) {
            touchDown()
            stand()
        }
        if (bursting) {
            // Only one motion can have the fire, and the burn-out above may not have started
            // yet. Starting after the new fire is lit, it would take the fire from it, and that
            // ends this whole reaction with nothing burnt. So it is stopped, and waited for.
            embers.cancelAndJoin()
            launch { hop.animateTo(0f, Landing) }
            burst.snapTo(0f)
            burst.animateTo(1f, tween(durationMillis = BURST_MILLIS, easing = LinearEasing))
        } else {
            rise()
            hop.animateTo(0f, Landing)
        }
    }

    /**
     * Up to the top of a hop, in the share of a rise's time that the way left takes. A save that
     * catches him at the top has no way left to give him, so he comes straight down from there
     * and does not wait out a rise he is not making.
     */
    private suspend fun rise() {
        val left = 1f - hop.value
        if (left > 0f) hop.animateTo(1f, tween(durationMillis = (left * RISE_MILLIS).roundToInt()))
    }

    /**
     * Down to the floor, in the share of a touchdown's time that the way left takes. A save that
     * meets him a hair above the floor, at the end of the last touchdown, has him there at once.
     */
    private suspend fun touchDown() {
        val left = abs(hop.value)
        if (left > 0f) hop.animateTo(0f, tween(durationMillis = (left * TOUCH_DOWN_MILLIS).roundToInt()))
    }

    /** A fire cut short by the next save burns on to its end, in the time it had left. */
    private suspend fun burnOut() {
        burst.animateTo(1f, tween(durationMillis = ((1f - burst.value) * BURST_MILLIS).roundToInt(), easing = LinearEasing))
    }

    companion object {
        /** How long a workout's fire burns. It burns evenly, so a fire handed on keeps its pace. */
        const val BURST_MILLIS = 1_500
    }
}

private val Landing = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
private const val RISE_MILLIS = 170
private const val TOUCH_DOWN_MILLIS = 90
