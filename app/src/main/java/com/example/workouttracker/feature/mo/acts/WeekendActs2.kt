package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropInk
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoPropYellowDark
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.FEET
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoBrowKind
import com.example.workouttracker.feature.mo.MoBrows
import com.example.workouttracker.feature.mo.MoEye
import com.example.workouttracker.feature.mo.MoEyeShape
import com.example.workouttracker.feature.mo.MoLeg
import com.example.workouttracker.feature.mo.MoLegs
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.polyline
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * A weekend with nowhere to be: he copies a garden flamingo, and he watches a snail cross the floor.
 */

/** Eased progress of [p] through the window from [from] to [to]: 0 before it, 1 after it. */
private fun weekendRamp(p: Float, from: Float, to: Float): Float = ease((p - from) / max(to - from, 0.001f))

/** A lawn flamingo stands on one leg, so he tries it too, wobbling and nearly going over. */
private object LikeTheFlamingo : MoAct("weekend_one_leg_like_the_flamingo", MoOccasion.Weekend, intensity = MoIntensity.Mild) {
    private const val PERIOD = 5.2f
    private const val STUMBLE = 0.9f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun birdX(v: MoVariant) = CENTRE + side(v) * 124f

    /** How far into the round we are, in seconds. It starts well clear of the stumble, so t = 0 is balanced. */
    private fun round(t: Float) = cycle(t, PERIOD, offset = 0.77f) * PERIOD

    /** The near-fall: one wobble too far that swells and settles over [STUMBLE] seconds. */
    private fun tipping(t: Float) = twitch(t, PERIOD, beat = STUMBLE, offset = 0.77f)

    /** Straight after catching himself he checks whether anyone saw that. */
    private fun glance(at: Float) = weekendRamp(at, 0.95f, 1.15f) * (1f - weekendRamp(at, 1.5f, 1.7f))

    /** And then he is quietly pleased with himself for a second. */
    private fun pleased(at: Float) = weekendRamp(at, 0.95f, 1.25f) * (1f - weekendRamp(at, 1.9f, 2.4f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val at = round(t)
        val k = tipping(t)
        val away = glance(at)
        val proud = pleased(at)
        // Folded on his flanks at rest, and opening into a flap as he goes over: the two beat against
        // each other, and they snap open faster than he tips, so a mitten is outside his silhouette
        // for the whole wobble. The reach stops where one would leave the frame on the far side.
        val spread = sqrt(max(k, 0f))
        fun wing(beat: Float) = MoArm(
            Offset(-10f + 30f * spread, 24f - 38f * spread + beat * 12f * k),
            bend = 0.9f - 0.8f * spread,
        )
        val beat = wave(t, 0.16f)
        val watch = Offset(s * 0.9f * (1f - away), -0.1f * (1f - away))
        val eye = if (k > 0.3f) MoEye(gaze = watch, iris = 0.5f, scale = 1.05f) else MoEye(gaze = watch)
        // The standing foot comes in under his middle, like the bird's, and the other is up out of sight
        // until the stumble drops it far enough to show.
        val stands = MoLeg(swing = -20f)
        val tucked = MoLeg(lift = 26f - 12f * k, swing = -6f)
        return moIdle(t, energy = 0.9f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (k > 0.3f) MoBrows(MoBrowKind.Raised, lift = 5f) else MoBrows(),
            mouth = if (k > 0.3f) MoMouth.Oh(size = 8f) else MoMouth.Smile(14f + 3f * proud, 10f + 5f * proud),
            leftArm = wing(beat),
            rightArm = wing(-beat),
            armsInFront = true,
            legs = if (v.chance(50, salt = 2)) MoLegs(tucked, stands) else MoLegs(stands, tucked),
            // Two slow wobbles of balance, and the stumble that throws him away from the bird.
            tilt = wave(t, 2.3f) * 2f + wave(t, 0.9f) * 0.8f - s * 7f * k,
            flare = 8f * k,
            offset = Offset(-s * 14f, -3f * k),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val fx = birdX(v)
        val (plumage, dark) = if (v.chance(20, salt = 3)) {
            v.of(listOf(MoPropTeal to MoPropTealDark, MoPropYellow to MoPropYellowDark), salt = 4)
        } else {
            MoPropPink to MoPropPinkDark
        }

        floorShadow(fx, 18f, MoPropShadow)
        polyline(dark, 6.5f, listOf(Offset(fx, 338f), Offset(fx + s * 7f, 382f), Offset(fx - s * 2f, FEET)))
        polyline(dark, 6f, listOf(Offset(fx + s * 6f, 336f), Offset(fx + s * 16f, 352f), Offset(fx + s * 6f, 362f)))

        // The neck is one S from two curves, drawn under the body so the join is hidden. It leans out
        // over its own back before coming back, which keeps its whole length clear of Mo.
        val head = Offset(fx - s * 12f, 212f)
        curve(Offset(fx - s * 10f, 312f), Offset(fx + s * 12f, 282f), Offset(fx + s * 4f, 252f), plumage, 12f)
        curve(Offset(fx + s * 4f, 252f), Offset(fx - s * 10f, 228f), head, plumage, 12f)
        // The body carries its own tail, so the point at its back cannot hide inside an oval.
        shape(
            plumage,
            (fx - s * 27f) to 318f,
            (fx - s * 20f) to 336f,
            (fx + s * 8f) to 341f,
            (fx + s * 32f) to 310f,
            (fx + s * 14f) to 305f,
            (fx - s * 8f) to 303f,
        )
        oval(Offset(fx + s * 10f, 322f), 15f, 10f, dark)

        // One gloss glint slides along his back and nothing else about the bird ever moves.
        val g = cycle(t, 4f)
        val from = Offset(fx - s * 16f + s * 24f * g, 306f)
        limb(from, from + Offset(s * 8f, 0f), MoPropWhite.copy(alpha = 0.7f * sin(PI.toFloat() * g)), 4.5f)

        drawCircle(plumage, 12f, head)
        val tip = Offset(head.x - s * 11f, head.y + 14f)
        shape(MoPropCream, (head.x - s * 9f) to (head.y - 4f), tip.x to tip.y, (head.x + s * 2f) to (head.y + 7f))
        drawCircle(MoPropInk, 3.5f, tip + Offset(s * 2f, -1f))
        drawCircle(MoPropInk, 2.8f, head + Offset(-s * 4f, -3f))
    }

    override val ownsTheFloor get() = true
}

/** Nowhere to be: chin in both mittens, he watches a snail take the whole floor to cross it. */
private object ASnailGoesBy : MoAct("weekend_a_snail_goes_by", MoOccasion.Weekend, intensity = MoIntensity.Mild) {
    private const val CROSSING = 18f
    private const val SIZE = 1.3f

    // They stop on opposite sides of him when the little one follows.
    private const val PAUSE_X = 102f
    private const val TRAIL = 204f

    private fun direction(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** Where it is in its crossing. It starts a third of the way in, so the pause comes round early. */
    private fun round(t: Float) = cycle(t, CROSSING, offset = 0.40f)

    /** Distance from his centre, easing to a stop beside his foot before it crawls on. */
    private fun position(c: Float, extent: Float): Float = when {
        c < 0.45f -> PAUSE_X - (extent + PAUSE_X) * (1f - c / 0.45f).let { it * it }
        c < 0.55f -> PAUSE_X
        else -> PAUSE_X + (extent - PAUSE_X) * ((c - 0.55f) / 0.45f).let { it * it }
    }

    /** The pause in the middle, where it lifts its stalks at him. */
    private fun resting(c: Float) = weekendRamp(c, 0.46f, 0.49f) * (1f - weekendRamp(c, 0.51f, 0.54f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val d = direction(v)
        val c = round(t)
        val gazeX = d * (position(c, 220f) / 150f).coerceIn(-1f, 1f) *
            (1f - weekendRamp(c, 0.90f, 1f)) - d * weekendRamp(c, 0.90f, 1f)
        val still = resting(c)
        val eye = if (still > 0.5f) {
            MoEye(shape = MoEyeShape.Happy)
        } else {
            MoEye(gaze = Offset(gazeX, 0.9f), lidDrop = 0.28f)
        }
        // Both mittens propping his chin: below the mouth and to either side of it, on either body.
        val prop = MoArm(Offset(-58f, 14f), bend = 0.5f)
        return moIdle(t, energy = 0.6f).copy(
            leftEye = eye,
            rightEye = eye,
            mouth = MoMouth.Smile(13f + 5f * still, 9f + 7f * still),
            leftArm = prop,
            rightArm = prop,
            armsInFront = true,
            legs = MoLegs(sitting = true),
            tilt = gazeX * 2.5f,
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant, picture: Rect) {
        val d = direction(v)
        val c = round(t)
        val extent = picture.width / 2f + 62f
        val along = position(c, extent)
        val still = resting(c)
        val (shell, dark) = v.of(
            listOf(
                MoPropPink to MoPropPinkDark,
                MoPropTeal to MoPropTealDark,
                MoPropYellow to MoPropYellowDark,
                MoPropPurple to MoPropPurpleDark,
            ),
            salt = 2,
        )
        val nested = v.chance(50, salt = 3)
        clipRect(picture.left, picture.top, picture.right, picture.bottom) {
            if (v.chance(20, salt = 4)) {
                // Behind on the same road, and round the end of it on its own when the leader starts over.
                val behind = (along + extent - TRAIL).mod(2f * extent) - extent
                snail(CENTRE + d * behind, d, 0.8f, wave(t + 0.7f, 1.6f), 0f, t + 0.7f, shell, dark, nested)
            }
            // It stops stretching while it rests, and its body keeps whatever length it had.
            snail(CENTRE + d * along, d, SIZE, wave(t, 1.6f) * (1f - still), still, t, shell, dark, nested)
        }
    }

    private fun DrawScope.snail(
        x: Float,
        d: Float,
        scale: Float,
        stretch: Float,
        lean: Float,
        t: Float,
        shell: Color,
        dark: Color,
        nested: Boolean,
    ) {
        withTransform({ scale(scale, scale, Offset(x, FEET)) }) {
            floorShadow(x, 26f, MoPropShadow)
            val head = Offset(x + d * (30f + 5f * stretch), 411f)
            oval(Offset(x + d * 6f, 418f), 26f + 5f * stretch, 7f, MoPropCream)
            drawCircle(MoPropCream, 8f, head)
            for (index in 0..1) {
                val tip = head + Offset(
                    d * (index * 7f - 3f - 4f * lean) + wave(t + index * 0.4f, 1.1f) * 3f,
                    -15f - 5f * lean,
                )
                limb(head, tip, MoPropCream, 3f)
                drawCircle(MoPropInk, 3f, tip)
            }
            val centre = Offset(x - d * 4f, 399f)
            drawCircle(dark, 17f, centre + Offset(0f, 3f))
            drawCircle(shell, 17f, centre)
            if (nested) {
                drawCircle(dark, 10f, centre + Offset(d * 2f, 1f))
                drawCircle(shell, 4f, centre + Offset(d * 3f, 2f))
            } else {
                curve(centre + Offset(-d * 13f, -5f), centre + Offset(-d * 3f, 1f), centre + Offset(-d * 10f, 10f), dark, 4f)
                curve(centre + Offset(d * 1f, -14f), centre + Offset(d * 9f, -2f), centre + Offset(d * 3f, 11f), dark, 4f)
            }
        }
    }

    // The snail has the whole floor for its road, so nothing else may lie on it.
    override val ownsTheFloor get() = true
}

internal val weekendActs2: List<MoAct> = listOf(
    LikeTheFlamingo,
    ASnailGoesBy,
)
