package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropMoon
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropRedDark
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWater
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.core.ui.theme.MoSpark
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodies
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoBrowKind
import com.example.workouttracker.feature.mo.MoBrows
import com.example.workouttracker.feature.mo.MoEye
import com.example.workouttracker.feature.mo.MoEyeShape
import com.example.workouttracker.feature.mo.MoLeg
import com.example.workouttracker.feature.mo.MoLegs
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPalettes
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.grippingMitten
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.drop
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * Sickly, part one: two sneezes, a thermometer, a shelf that is too high, a brave face and a hot
 * water bottle. Each loop is cut into spans of its period, and every channel is zero at both ends
 * of its span, which is what hides the seam.
 */

private const val PI_F = PI.toFloat()

/** How far [p] is through the span [from]..[to], from 0 to 1. The spans are constants with from < to. */
private fun span(p: Float, from: Float, to: Float): Float = ((p - from) / (to - from)).coerceIn(0f, 1f)

/** 0 to 1 and back to 0 across the span: one hop, one squeeze, one flinch. */
private fun bump(p: Float, from: Float, to: Float): Float = sin(span(p, from, to) * PI_F)

private fun mix(a: Float, b: Float, f: Float): Float = a + (b - a) * f

private fun mix(a: MoArm, b: MoArm, f: Float): MoArm =
    MoArm(Offset(mix(a.reach.x, b.reach.x, f), mix(a.reach.y, b.reach.y, f)), mix(a.bend, b.bend, f))

/** A tissue ready in one mitten, a tickle that builds, and a sneeze that blows his tip flat sideways. */
private object SidewaysSneeze : MoAct("sickly_sideways_sneeze", MoOccasion.Sickly) {
    private const val PERIOD = 4.4f
    private const val BLAST = 0.45f

    // The second, smaller sneeze of a double.
    private const val AGAIN = 0.62f

    private fun p(t: Float) = cycle(t, PERIOD, offset = 0.2f)

    /** The way the flame is blown; the tissue waits in the mitten on the other side. */
    private fun dir(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun again(v: MoVariant) = if (v.chance(30, salt = 3)) 0.5f else 0f

    /** The tip whipped over and boinging back, as a damped wobble that starts at [from]. */
    private fun boing(p: Float, from: Float): Float {
        val u = span(p, from, from + 0.35f)
        return span(p, from, from + 0.015f) * (1f - ease(u)) * cos(u * 5f * 2f * PI_F)
    }

    private fun blast(p: Float, v: MoVariant): Float = bump(p, BLAST, BLAST + 0.07f) + again(v) * bump(p, AGAIN, AGAIN + 0.06f)

    /** The tissue comes up beside the mouth only once it is all over. */
    private fun tooLate(p: Float) = ease(span(p, 0.60f, 0.76f)) * (1f - ease(span(p, 0.86f, 1f)))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = p(t)
        val dir = dir(v)
        val tickle = ease(span(p, 0f, BLAST)) * (1f - span(p, BLAST, BLAST + 0.015f))
        val blast = blast(p, v)
        val whip = boing(p, BLAST) + again(v) * boing(p, AGAIN)
        val slid = span(p, BLAST, BLAST + 0.015f) * (1f - ease(span(p, 0.58f, 0.80f)))
        val shut = blast > 0.05f
        val dazed = p >= BLAST && p < 0.80f

        val eye = when {
            shut -> MoEye(shape = MoEyeShape.Shut)
            dazed -> MoEye(iris = 0.55f, tear = mix(0.12f, 1f, span(p, BLAST, 0.80f)))
            // The spent tear fades back in as the next one wells up.
            p >= 0.80f -> MoEye(lidDrop = 0.2f * ease(span(p, 0.80f, 0.9f)), tear = mix(1f, 0.12f, ease(span(p, 0.80f, 1f))))
            else -> MoEye(lidDrop = 0.2f + 0.55f * tickle, gaze = Offset(0f, -0.3f * tickle), tear = 0.12f)
        }
        val figure = MoBodies.of(body)
        // Beside the mouth corner and under the eye, wherever this body keeps them.
        val atTheMouth = MoArm(Offset(-52f, figure.mouth.y + 24f - figure.shoulderY), bend = 0.5f)
        val waiting = MoArm(Offset(-24f, figure.mouth.y + 34f - figure.shoulderY), bend = 0.5f)
        val tissueArm = mix(waiting, atTheMouth, tooLate(p))
        val flung = span(p, BLAST, BLAST + 0.015f) * (1f - ease(span(p, 0.52f, 0.74f)))
        val freeArm = mix(MoArm.Down, MoArm(Offset(44f, -10f), bend = 0.1f), flung)
        val step = 6f * bump(p, 0.58f, 0.69f)
        val otherStep = 6f * bump(p, 0.69f, 0.80f)

        return moIdle(t, energy = 0.8f).copy(
            palette = MoPalettes.Queasy,
            leftEye = eye,
            rightEye = eye,
            brows = if (dazed && !shut) MoBrows(MoBrowKind.Raised, lift = 4f) else MoBrows(MoBrowKind.Worried, lift = 2f + 6f * tickle),
            mouth = when {
                shut -> MoMouth.Oh(size = 14f)
                dazed -> MoMouth.Wavy
                else -> MoMouth.Oh(size = 4f + 8f * tickle)
            },
            leftArm = if (dir > 0f) tissueArm else freeArm,
            rightArm = if (dir > 0f) freeArm else tissueArm,
            armsInFront = true,
            // He slides away from the blast, so the first step home is taken by the far foot.
            legs = if (dir > 0f) MoLegs(MoLeg(lift = step), MoLeg(lift = otherStep)) else MoLegs(MoLeg(lift = otherStep), MoLeg(lift = step)),
            sway = moIdle(t, 0.8f).sway * (1f - tickle) - dir * 10f * tickle + dir * 85f * whip,
            flare = 14f * tickle - 12f * blast,
            squash = moIdle(t, 0.8f).squash + 0.05f * tickle - 0.10f * blast,
            tilt = -dir * 3f * tickle + dir * 5f * blast,
            offset = Offset(-dir * 12f * slid, 0f),
            blinks = !shut,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = p(t)
        val dir = dir(v)
        val hand = if (dir > 0f) rig.leftHand else rig.rightHand
        val at = hand + Offset(-dir * 4f, -16f)
        val flutter = wave(t, 0.5f) * 3f
        val streak = dir * 14f * blast(p, v)
        shape(
            v.of(listOf(MoPropWhite, MoPropSky, MoPropPink), salt = 2),
            at.x - 17f + streak to at.y - 13f + flutter,
            at.x + 17f + streak to at.y - 13f - flutter,
            at.x + 11f to at.y + 13f,
            at.x - 11f to at.y + 13f,
        )
        grippingMitten(rig, -dir)
        gust(rig.mouth, dir, span(p, BLAST, BLAST + 0.30f), 1f)
        if (again(v) > 0f) gust(rig.mouth, dir, span(p, AGAIN, AGAIN + 0.24f), 0.6f)
    }

    /** What leaves him: a cloud and three sparks fanning out, gone by the time [u] reaches 1. */
    private fun DrawScope.gust(mouth: Offset, dir: Float, u: Float, size: Float) {
        if (u <= 0f || u >= 1f) return
        val from = mouth + Offset(dir * 44f, 6f)
        puff(from + Offset(dir * 60f * u * size, 0f), 21f * size * (1f - u * 0.4f), MoPropWhite.copy(alpha = 0.9f * (1f - u)))
        for (fan in listOf(-0.35f, 0f, 0.35f)) {
            val reach = (20f + 85f * ease(u)) * size
            sparkle(from + Offset(dir * cos(fan) * reach, sin(fan) * reach), 9f * size, 1f, MoSpark.copy(alpha = 1f - u))
        }
    }

    override val sparkles get() = false
}

/** He winds up for a sneeze in gasps, hangs trembling at the top, and it fizzles into a squeak. */
private object SneezeThatNeverComes : MoAct("sickly_sneeze_that_never_comes", MoOccasion.Sickly) {
    private const val PERIOD = 6f
    private const val WOUND = 0.6f
    private const val SQUEAK = 0.70f

    private fun notches(v: MoVariant) = 2 + v.pick(3, salt = 1)

    // Shifted so that t = 0 is the hold of the second gasp.
    private fun p(t: Float, v: MoVariant) = cycle(t, PERIOD, offset = 0.9f / notches(v))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = p(t, v)
        val notches = notches(v)
        val x = span(p, 0f, WOUND) * notches
        // A quick gasp and then a hold for every notch, and the lot let out again with the squeak.
        val wound = if (p < WOUND) (floor(x) + ease((x - floor(x)) * 2.5f)) / notches else 1f - ease(span(p, SQUEAK, 0.80f))
        val gasp = if (p < WOUND) bump(x - floor(x), 0f, 0.4f) else 0f
        val mitten = 1f - ease(span(p, 0.80f, 0.94f))
        val squeak = bump(p, SQUEAK, 0.76f)
        val looking = p >= 0.76f && p < 0.92f
        val rear = if (v.chance(50, salt = 3)) 1f else -1f
        val hang = ease(span(p, 0.58f, 0.62f)) * (1f - span(p, 0.69f, 0.71f))
        val figure = MoBodies.of(body)
        // Out past his silhouette and up to eye height, where a mitten reads against the background.
        val raised = MoArm(Offset(34f, figure.eyeY - 10f - figure.shoulderY), bend = 0.35f)
        val risen = mix(MoArm.Down, raised, if (p < WOUND) wound else mitten)
        val rightRises = v.chance(50, salt = 2)

        val eye = when {
            squeak > 0.05f -> MoEye(shape = MoEyeShape.Shut)
            looking -> MoEye(iris = 0.7f, gaze = Offset(sin(span(p, 0.76f, 0.92f) * 2f * PI_F) * 0.9f, 0f))
            p >= 0.92f -> MoEye(lidDrop = 0.15f * span(p, 0.92f, 1f))
            else -> MoEye(lidDrop = 0.15f + 0.6f * wound, gaze = Offset(0f, -0.4f * wound))
        }
        return moIdle(t, energy = 0.7f).copy(
            palette = MoPalettes.Queasy,
            leftEye = eye,
            rightEye = eye,
            brows = if (looking) MoBrows(MoBrowKind.Raised, skew = 5f) else MoBrows(MoBrowKind.Worried, lift = 8f * wound),
            mouth = when {
                p < SQUEAK -> MoMouth.Oh(size = 4f + 9f * wound + 2f * gasp)
                p < 0.76f -> MoMouth.Oh(size = 4f)
                // The flat mouth belongs to the looking around; the sag gives it back, so the
                // period ends on the same small Oh it started with and the loop has no seam.
                p < 0.92f -> MoMouth.Flat(width = 12f)
                else -> MoMouth.Oh(size = 4f)
            },
            leftArm = if (rightRises) MoArm.Down else risen,
            rightArm = if (rightRises) risen else MoArm.Down,
            armsInFront = true,
            sway = moIdle(t, 0.7f).sway * (1f - wound) + rear * (36f * wound - 8f * squeak),
            flare = 20f * wound,
            squash = 0.02f + 0.07f * wound - 0.03f * squeak - 0.04f * bump(p, 0.86f, 1f),
            shake = wave(t, 0.05f) * 1.5f * hang,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val out = span(p(t, v), SQUEAK, 0.82f)
        if (out <= 0f || out >= 1f) return
        if (v.chance(8, salt = 4)) {
            sparkle(rig.tip + Offset(0f, -10f - 20f * out), 5f, 1f, MoSpark.copy(alpha = 1f - out))
        } else {
            puff(rig.mouth + Offset(0f, 18f + 14f * out), 5f + 2f * out, MoPropWhite.copy(alpha = 0.8f * (1f - out)))
        }
    }
}

/** A thermometer in the corner of his pout, and he is a flame, so of course it tops out. */
private object ThermometerTopsOut : MoAct("sickly_thermometer_tops_out", MoOccasion.Sickly, intensity = MoIntensity.Mild) {
    private const val BREATH = 2.8f

    // Seven seconds in is the middle of the third breath, the big one, so that is what t = 0 shows.
    private fun clock(t: Float) = t + 7f

    private fun breath(t: Float) = pulse(clock(t), BREATH)

    /** Every third breath runs the column to the end. Breath is 0 where this switches, so it never shows. */
    private fun big(t: Float) = if (cycle(clock(t), BREATH * 3f) > 2f / 3f) 1f else 0f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val breath = breath(t)
        val up = big(t) * ease((breath - 0.7f) / 0.2f)
        // Both eyes cross down onto the tube, the far one further, until the glance up at the viewer.
        val far = MoEye(gaze = Offset(mix(s * 0.9f, 0f, up), mix(0.9f, -0.3f, up)), lidDrop = 0.25f * (1f - up))
        val near = MoEye(gaze = Offset(mix(s * 0.4f, 0f, up), mix(0.9f, -0.3f, up)), lidDrop = 0.25f * (1f - up))
        val figure = MoBodies.of(body)
        val steady = MoArm(Offset(-50f, figure.mouth.y + 27f - figure.shoulderY), bend = 0.4f)
        val steadies = v.chance(40, salt = 4)

        return moIdle(t, energy = 0.7f).copy(
            leftEye = if (s > 0f) far else near,
            rightEye = if (s > 0f) near else far,
            brows = if (up > 0.5f) MoBrows(MoBrowKind.Raised, lift = 5f) else MoBrows(MoBrowKind.Worried),
            mouth = MoMouth.Pout,
            leftArm = if (steadies && s < 0f) steady else MoArm.Down,
            rightArm = if (steadies && s > 0f) steady else MoArm.Down,
            armsInFront = steadies,
            flare = 8f * breath,
            squash = 0.02f * breath,
            tilt = s * 2f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val from = rig.mouth + Offset(s * 10f, 5f)
        val to = rig.mouth + Offset(s * 60f, 37f)
        val along = to - from
        val colour = v.of(listOf(MoPropPink, MoPropOrange, MoPropRed), salt = 2)
        val level = 0.30f + breath(t) * (0.30f + 0.40f * big(t))
        val bulge = ease((level - 0.9f) / 0.1f)
        val glass = v.of(listOf(MoPropSilver, MoPropMoon), salt = 3)

        limb(from, to, glass, 14f)
        // The tube is 59.4 long, so this is its unit normal without a division.
        val across = Offset(-along.y, along.x) / 59.4f
        for (mark in listOf(0.35f, 0.55f, 0.75f)) {
            val at = from + along * mark
            limb(at - across * 1f, at + across * 6f, MoPropGreyDark, 2.5f)
        }
        // The end of the tube is glass until the column reaches it; then it fills and swells.
        drawCircle(glass, 8.5f, to)
        limb(from, from + along * level, colour, 6f)
        if (bulge > 0f) drawCircle(colour, 6f + 5f * bulge, to)
        if (v.chance(40, salt = 4)) grippingMitten(rig, s)
    }
}

/** His pill bottle stands on a shelf he cannot reach: three hops, each weaker, and a pleading look. */
private object TopShelf : MoAct("sickly_top_shelf", MoOccasion.Sickly) {
    private const val PERIOD = 5.6f

    // Even his best hop leaves daylight between his mitten and the shelf brackets.
    private const val SHELF_Y = 179f

    private fun p(t: Float) = cycle(t, PERIOD, offset = 0.07f)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** How much of the reach is left in him: all of it for the hops, a third while he gets his breath back. */
    private fun reach(p: Float) = mix(1f, 0.35f, ease(span(p, 0.40f, 0.50f)) * (1f - ease(span(p, 0.85f, 1f))))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = p(t)
        val s = side(v)
        val r = reach(p)
        val third = if (v.pick(2, salt = 3) == 1) 1f else 0f
        val first = bump(p, 0f, 0.14f)
        val second = bump(p, 0.16f, 0.28f)
        val last = bump(p, 0.30f, 0.40f) * third
        val hop = first * 30f + second * 18f + last * 7f
        val crouch = bump(p, 0.14f, 0.16f) + bump(p, 0.28f, 0.30f) * third + bump(p, 0.88f, 1f) * 1.25f
        val spent = ease(span(p, 0.40f, 0.50f)) * (1f - ease(span(p, 0.80f, 0.88f)))
        val pleading = p >= 0.58f && p < 0.78f

        val eye = if (pleading) {
            MoEye(gaze = Offset(0f, -0.2f), lid = -0.3f, scale = 1.05f)
        } else {
            MoEye(gaze = Offset(s * 0.8f, -0.9f), lid = -0.2f * spent)
        }
        val reaching = MoArm(Offset(mix(10f, 26f, r), mix(36f, -63f, r)), bend = 0.15f)
        // The free arm throws itself up with each hop, the way anyone reaching for a top shelf does.
        val free = MoArm(Offset(44f, -2f - 18f * hop / 30f), bend = 0.1f + 0.2f * hop / 30f)
        val balancing = mix(MoArm.Down, free, (r - 0.35f) / 0.65f)

        return moIdle(t, energy = 0.8f).copy(
            palette = MoPalettes.Queasy,
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = if (pleading) 5f else 3f),
            mouth = if (spent > 0.5f) MoMouth.Oh(size = 6f + 3f * pulse(t, 0.5f)) else MoMouth.Flat(width = 10f),
            leftArm = if (s < 0f) reaching else balancing,
            rightArm = if (s < 0f) balancing else reaching,
            legs = MoLegs(MoLeg(lift = hop * 0.4f), MoLeg(lift = hop * 0.4f)),
            // The tip bends toward the bottle like a third hand, and further at the top of a hop.
            sway = moIdle(t, 0.8f).sway * (1f - r) + s * (18f * r + 8f * (first + second * 0.6f)),
            flare = mix(-8f, 10f, (r - 0.35f) / 0.65f),
            squash = 0.06f * (first + second * 0.7f + last * 0.4f) - 0.04f * crouch - 0.03f * spent,
            tilt = s * 1.5f * r,
            offset = Offset(0f, -hop),
            shake = wave(t, 1.1f) * 1.5f * spent,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val x = CENTRE + s * 128f
        val (colour, dark) = v.of(
            listOf(MoPropTeal to MoPropTealDark, MoPropSky to MoPropBlueDark, MoPropPink to MoPropPinkDark),
            salt = 2,
        )
        for (bracket in listOf(-18f, 18f)) {
            shape(MoPropWoodDark, x + bracket - 6f to SHELF_Y + 6f, x + bracket + 6f to SHELF_Y + 6f, x + bracket to SHELF_Y + 20f)
        }
        chunk(Offset(x, SHELF_Y), 58f, 12f, MoPropWood, MoPropWoodDark)
        // It rocks in the warm draught that rises from him, most while he is right under it.
        rotate(wave(t, 0.4f) * 2f * reach(p(t)), Offset(x, SHELF_Y - 8f)) {
            chunk(Offset(x, SHELF_Y - 28f), 30f, 38f, colour, dark)
            block(Offset(x, SHELF_Y - 26f), 30f, 10f, MoPropCream, corner = 0f)
            block(Offset(x, SHELF_Y - 53f), 22f, 10f, MoPropWhite)
        }
    }

    // Blazing's sparkles would sit on the shelf and the bottle.
    override val sparkles get() = false
}

/** Wilted and green, he notices you and puts on a brave face for one proud beat before it slides off again. */
private object SicklyBraveFace : MoAct("sickly_brave_face", MoOccasion.Sickly) {
    private const val PERIOD = 7f

    /** How much of the brave face is up: 0 wilted, 1 for the proud beat, then a slow slide back. */
    private fun bravery(t: Float, v: MoVariant): Float {
        val p = cycle(t, PERIOD, offset = 0.4f)
        val hold = v.between(0.18f, 0.30f, salt = 1)
        return ease(span(p, 0.15f, 0.30f)) * (1f - ease(span(p, 0.30f + hold, 0.90f)))
    }

    private fun gritted(v: MoVariant) = v.chance(60, salt = 2)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, PERIOD, offset = 0.4f)
        val b = bravery(t, v)
        val drift = if (v.chance(50, salt = 3)) 1f else -1f
        // He notices the viewer a beat before he straightens, and the glance melts into the brave face.
        val noticing = ease(span(p, 0.10f, 0.13f)) * (1f - ease(span(p, 0.20f, 0.30f)))
        val wilt = (1f - b) * (1f - noticing)
        val tremble = ease((b - 0.9f) / 0.1f)
        val eye = MoEye(
            lidDrop = 0.45f * wilt + 0.2f * noticing,
            gaze = Offset(drift * 0.6f, 0.6f) * wilt + Offset(0f, -0.2f) * noticing,
        )
        val hip = mix(MoArm.Down, MoArm.Hip, b)
        val limpWave = mix(MoArm.Down, MoArm(Offset(40f, -30f + wave(t, 0.5f) * 6f), bend = 0.5f), b)

        return moIdle(t, energy = 0.6f).copy(
            palette = MoPalettes.Queasy,
            leftEye = eye,
            rightEye = eye,
            // The brows never join in, which is half of what gives him away.
            brows = MoBrows(MoBrowKind.Worried, lift = 6f * b + 4f * noticing),
            mouth = when {
                b < 0.3f -> MoMouth.Wavy
                gritted(v) && b >= 0.7f -> MoMouth.Gritted
                else -> {
                    val smile = if (gritted(v)) 0f else ease(span(b, 0.3f, 0.7f))
                    MoMouth.Smile(width = 10f + 14f * smile + wave(t, 0.13f) * 1.5f * tremble, depth = 6f + 11f * smile)
                }
            },
            leftArm = hip,
            rightArm = if (v.chance(50, salt = 4)) limpWave else hip,
            // Wilted, the tip curls over; brave, it stands straight and only the idle sway is left.
            // Wilted keeps a share of it, and his breath: a flame that holds perfectly still for
            // two seconds looks like a frozen frame, not like Mo.
            sway = moIdle(t, 0.6f).sway * (0.4f + 0.6f * b) + 22f * (1f - b),
            flare = -10f + 20f * b,
            squash = moIdle(t, 0.6f).squash - 0.03f + 0.06f * b,
            shake = wave(t, 0.07f) * 1.2f * tremble,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        // A closed smile alone looks cheerful, so that one always sweats.
        if (gritted(v) && !v.chance(65, salt = 5)) return
        // The one drop of sweat that gives him away, only while the face is up.
        val fall = cycle(t, 1.6f, offset = 0.25f)
        drop(rig.rightEye + Offset(33f, -40f + 30f * fall), 8f * ease(fall / 0.15f), MoPropWater.copy(alpha = bravery(t, v) * (1f - fall * fall)))
    }
}

internal val sicklyActs1: List<MoAct> = listOf(
    SidewaysSneeze,
    SneezeThatNeverComes,
    ThermometerTopsOut,
    TopShelf,
    SicklyBraveFace,
)
