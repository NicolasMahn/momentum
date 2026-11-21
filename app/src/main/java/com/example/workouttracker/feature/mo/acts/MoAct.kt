package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.domain.mo.MoSaveAct
import com.example.workouttracker.domain.mo.moHash
import com.example.workouttracker.feature.mo.MoBodies
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig

/**
 * One little thing Mo can be up to: a pose that lives, and whatever belongs to it. An act never draws
 * Mo; it says how he holds himself, and draws only what is around him or in his hands.
 *
 * Everything is a pure function of [t], the seconds since he appeared, so it runs forever and needs
 * no state. At t = 0 the act must already look complete, because that is the frame the widget and
 * the notifications show.
 *
 * The [id] never changes once released: the day's pick is derived from it.
 */
internal abstract class MoAct(
    val id: String,
    val occasion: MoOccasion,
    val bodies: Set<MoBodyKind> = MoBodies.Burning,
    /**
     * How big the feeling is. Mild is a small gesture he could keep up all day, Medium is plainly
     * emotional, Strong takes him over completely: red and spitting fire, or leaping with joy.
     */
    val intensity: MoIntensity = MoIntensity.Medium,
    /** For a [MoOccasion.Doing] act, what he acts out. Every other act leaves it null. */
    val saveAct: MoSaveAct? = null,
) {
    /** How he holds himself at [t]. Start from `moIdle(t)`. The Husk ignores it: he does not move. */
    abstract fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose

    /** The world behind him: floor, furniture, sky. Drawn before Mo, and it does not move with him. */
    open fun DrawScope.world(t: Float, v: MoVariant) {}

    /** What he holds, wears or has on him. Drawn after Mo inside his own transform, from the [rig]. */
    open fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {}

    /** A visitor on the outside of the ice. [ice] is the block's bounds in the act's design space. */
    open fun DrawScope.atIce(t: Float, v: MoVariant, ice: Rect) {}

    /** What passes in front of him: rain, confetti, a visitor. Drawn last, and it does not move with him. */
    open fun DrawScope.foreground(t: Float, v: MoVariant) {}

    /**
     * The world, for an act with something that comes in from the side or leaves there. [picture]
     * is what his picture shows of the act's design space on the body he has. A smaller Mo is drawn
     * smaller in the same box, so more floor shows beside him: the picture ends at -45 and 275
     * only when he blazes, and near -140 and 370 for the Husk. Override this one in place of the
     * plain [world], and bring whatever crosses the floor in from [picture]'s own edge.
     */
    open fun DrawScope.world(t: Float, v: MoVariant, picture: Rect) = world(t, v)

    /** The foreground, for an act that needs to know where the [picture] ends. */
    open fun DrawScope.foreground(t: Float, v: MoVariant, picture: Rect) = foreground(t, v)

    /** True when the act fills the floor beside him, where the things he has neglected would lie. */
    open val ownsTheFloor: Boolean get() = false

    /** False when Blazing's sparkles would fight with what the act puts in the air. */
    open val sparkles: Boolean get() = true

    /**
     * True for the one act of an activity that plays for as long as a timer runs: a quiet loop
     * that can be watched for an hour. The other acts of the activity are for the seconds after a save.
     */
    open val forASession: Boolean get() = false
}

/**
 * The small differences that make the same act a new sight on another day: which colour the balloon
 * is, how many sparks he juggles, whether the visitor comes from the left. Every answer is fixed for
 * a given day and part of the day. Use a different [salt] for each independent choice.
 */
internal class MoVariant(private val seed: Int) {
    fun pick(count: Int, salt: Int = 0): Int = Math.floorMod(moHash(seed.toLong() * 31 + salt), count)

    fun <T> of(options: List<T>, salt: Int = 0): T = options[pick(options.size, salt)]

    fun chance(percent: Int, salt: Int = 0): Boolean = pick(100, salt) < percent

    fun between(min: Float, max: Float, salt: Int = 0): Float = min + (max - min) * pick(1000, salt) / 999f

    companion object {
        val Plain = MoVariant(0)
    }
}

/** An act together with the variant it is played in today. */
internal class MoScene(val act: MoAct, val variant: MoVariant)
