package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.util.lerp
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropMint
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.FEET
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoBrowKind
import com.example.workouttracker.feature.mo.MoBrows
import com.example.workouttracker.feature.mo.MoEye
import com.example.workouttracker.feature.mo.MoLeg
import com.example.workouttracker.feature.mo.MoLegs
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.grippingMitten
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.wave
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * A task with the scale motif is open, part two: the scale stands beside him, and he does everything except step on it.
 * Its window stays blank in every act, because a number would be about a logged value.
 */

private val scaleColours = listOf(
    MoPropTeal to MoPropTealDark,
    MoPropBlue to MoPropBlueDark,
    MoPropPink to MoPropPinkDark,
    MoPropPurple to MoPropPurpleDark,
)

// A thumbnail has to say "bathroom scale" at once, so the slab is as wide and as deep as the frame
// allows beside him: about two fifths of his own width, the proportions of the app's own scale.
private const val SCALE_WIDTH = 88f
private const val SCALE_HEIGHT = 20f
private const val SCALE_DROP = 6f

/** The top face of the slab: what a toe pokes and a cloth is rubbed on. */
private const val SCALE_TOP = FEET - SCALE_DROP - SCALE_HEIGHT

/** How far beside him it stands in every act: within reach, and its shadow still inside the frame. */
private const val SCALE_OUT = 106f

/** The side of him the scale stands on: 1 is the viewer's right. */
private fun scaleSide(v: MoVariant, percent: Int = 50): Float = if (v.chance(percent, salt = 1)) 1f else -1f

/** Where the scale stands for [s], the side it is on. */
private fun scaleX(s: Float) = CENTRE + s * SCALE_OUT

/** The bathroom scale seen from the side, centred on [x], with its blank window [window] off centre. */
private fun DrawScope.bathroomScale(x: Float, v: MoVariant, window: Float = 0f) {
    val (colour, ledge) = v.of(scaleColours, salt = 2)
    floorShadow(x, SCALE_WIDTH / 2f + 4f, MoPropShadow)
    // Its ledge stands on the floor, which puts the face's top edge on SCALE_TOP.
    chunk(Offset(x, FEET - SCALE_DROP / 2f - SCALE_HEIGHT / 2f), SCALE_WIDTH, SCALE_HEIGHT, colour, ledge, corner = 8f, drop = SCALE_DROP)
    block(Offset(x + window, SCALE_TOP + 9f), 26f, 7f, MoPropCream, corner = 3f)
}

/** 0 before [from], 1 after [to], eased in between: one beat of a loop. The bounds are never equal. */
private fun span(p: Float, from: Float, to: Float): Float = ease((p - from) / (to - from))

private fun lerp(from: MoArm, to: MoArm, fraction: Float): MoArm =
    MoArm(lerp(from.reach, to.reach, fraction), lerp(from.bend, to.bend, fraction))

/** He buffs the scale until it glints, admires it, looks at you hopefully, and polishes on. */
private object PolishingInstead : MoAct("unweighed_polishing_instead", MoOccasion.Unweighed, intensity = MoIntensity.Mild) {
    private const val LEAN = 9f

    private fun phase(t: Float) = cycle(t, 6f, offset = 0.1f)

    /** 1 while he is bent over the scale, 0 while he stands and admires it. */
    private fun bent(p: Float) = 1f - span(p, 0.52f, 0.60f) + span(p, 0.86f, 0.96f)

    /** The glance up at the viewer, in the middle of the admiring: the beat the whole act is for. */
    private fun glance(p: Float) = span(p, 0.62f, 0.66f) * (1f - span(p, 0.82f, 0.86f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = scaleSide(v)
        val p = phase(t)
        val bent = bent(p)
        val glance = glance(p)
        // The lean carries the hand down onto the slab, so the reach itself stays short. The Low
        // body's shoulders sit lower, and so does its reach.
        val down = if (body == MoBodyKind.Low) 30f else 42f
        val rubbing = MoArm(Offset(20f + 9f * cos(t * 7f), down + 4f * sin(t * 7f)), bend = 0.1f)
        val near = lerp(MoArm.Down, rubbing, bent)
        val eye = MoEye(gaze = lerp(Offset(s * 0.7f, 1f), Offset(0f, -0.3f), glance))
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (glance > 0.5f) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(MoBrowKind.Flat, lift = 2f),
            mouth = when {
                glance > 0.5f -> MoMouth.Smile(width = 13f, depth = 9f)
                bent > 0.5f && v.chance(40, salt = 4) -> MoMouth.TongueOut
                bent > 0.5f && body == MoBodyKind.Tall -> MoMouth.Whistle
                else -> MoMouth.Flat(8f)
            },
            leftArm = if (s > 0f) MoArm.Hip else near,
            rightArm = if (s > 0f) near else MoArm.Hip,
            // The lean turns about the middle of his feet, so one foot reaches down and the other
            // tucks up to keep both on the floor.
            legs = MoLegs(MoLeg(lift = -s * 4.7f * bent), MoLeg(lift = s * 5f * bent)),
            tilt = s * LEAN * bent,
            squash = wave(t, 3.2f) * 0.014f - 0.04f * bent,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = scaleSide(v)
        bathroomScale(scaleX(s), v, window = s * 12f)
        val admired = 1f - bent(phase(t))
        // Glints belong to the slab. The wiping hand can pass in front of them.
        sparkle(Offset(scaleX(s) - s * 30f, SCALE_TOP), 11f, max(pulse(t + 0.75f, 1.5f), admired), MoPropWhite)
        sparkle(Offset(scaleX(s) + s * 30f, SCALE_TOP + 1f), 8f, max(pulse(t, 1.5f), admired), MoPropWhite)
        if (v.chance(50, salt = 5)) {
            // The polish tin waits on his other side: beyond the scale it would leave the frame.
            val tin = Offset(CENTRE - s * 132f, FEET - 9f)
            floorShadow(tin.x, 18f, MoPropShadow)
            chunk(tin, 26f, 14f, MoPropGold, MoPropGoldDark, corner = 5f, drop = 4f)
            block(tin + Offset(0f, -2f), 16f, 4f, MoPropCream, corner = 1.5f)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = scaleSide(v)
        val hand = if (s > 0f) rig.rightHand else rig.leftHand
        val cloth = v.of(listOf(MoPropYellow, MoPropMint, MoPropSky, MoPropCream), salt = 3)
        val bent = bent(phase(t))
        // He pinches the outer corner as he stands. The loose end falls beneath it.
        val corner = hand + Offset(s * 10f * bent, 7f)
        rotate(-s * (LEAN * bent + 76f * (1f - bent)), corner) {
            block(corner + Offset(-s * 10f, 3f), 28f, 10f, cloth, corner = 5f)
            shape(cloth, corner.x - s * 22f to corner.y + 3f, corner.x - s * 27f to corner.y + 10f, corner.x - s * 16f to corner.y + 6f)
        }
        grippingMitten(rig, s)
    }

    override val ownsTheFloor get() = true
}

internal val unweighedActs2: List<MoAct> = listOf(
    PolishingInstead,
)
