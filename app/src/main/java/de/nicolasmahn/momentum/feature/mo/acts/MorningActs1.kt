package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrass
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropLeaf
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWater
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodies
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoBrowKind
import de.nicolasmahn.momentum.feature.mo.MoBrows
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoEyeShape
import de.nicolasmahn.momentum.feature.mo.MoLeg
import de.nicolasmahn.momentum.feature.mo.MoLegs
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.drop
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Morning, part one: he is up, but not all of him yet. Each act is a little story told on a fixed
 * loop, so the timing is written as parts of that loop: `span` eases from 0 to 1 between two such
 * parts, and `bell` is one soft bump around a moment.
 */

/** Eases from 0 to 1 while [p] runs from [from] to [to], and holds either end outside of that. */
private fun span(p: Float, from: Float, to: Float): Float = ease((p - from) / max(to - from, 0.001f))

/** One smooth bump: 1 at [x] = 0, down to 0 at -1 and 1, and 0 beyond. */
private fun bell(x: Float): Float = if (abs(x) < 1f) cos(x * PI.toFloat() / 2f).let { it * it } else 0f

private fun mix(from: Float, to: Float, k: Float): Float = from + (to - from) * k

private fun mix(from: MoArm, to: MoArm, k: Float): MoArm = MoArm(lerp(from.reach, to.reach, k), mix(from.bend, to.bend, k))

/** He slept on his flame: he blows the flopped tip upright, is pleased, and watches it sag back over. */
private object BedheadTip : MoAct("morning_bedhead_tip", MoOccasion.Morning, intensity = MoIntensity.Mild) {
    private const val PERIOD = 6.4f
    private const val BLOW = 0.34f
    private const val TRY = 0.22f

    private fun side(v: MoVariant) = v.of(listOf(-1f, 1f), salt = 1)

    private fun triesFirst(v: MoVariant) = v.chance(35, salt = 3)

    /** How far over the tip lies: 1 flopped, 0 upright, a little beyond 0 as it springs up. */
    private fun flop(p: Float, v: MoVariant): Float {
        val sagFrom = 0.48f + v.between(0.20f, 0.30f, salt = 4)
        val sprung = 1f - span(p, 0.40f, 0.46f) - 0.12f * bell((p - 0.49f) / 0.03f) + span(p, sagFrom, 1f)
        // The puff that was not enough only makes it twitch.
        return if (triesFirst(v)) sprung - 0.22f * bell((p - TRY - 0.07f) / 0.04f) else sprung
    }

    /** A breath drawn in before [at] and let go right after it, as a squash. */
    private fun breath(p: Float, at: Float): Float =
        -0.03f * span(p, at - 0.08f, at - 0.02f) * (1f - span(p, at - 0.02f, at)) + 0.03f * bell((p - at - 0.01f) / 0.025f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, PERIOD)
        val side = side(v)
        val f = flop(p, v)
        val idle = moIdle(t, energy = 0.5f)
        val blowing = p in 0.30f..0.42f || (triesFirst(v) && p in TRY - 0.03f..TRY + 0.05f)
        val eye = MoEye(lidDrop = mix(0.15f, 0.4f, f.coerceIn(0f, 1f)), gaze = Offset(side * 0.6f * f, -0.9f))
        return idle.copy(
            // Far enough over, and pressed down, that the tip plainly lies on the wrong side.
            sway = idle.sway * (1f - f) + side * 84f * f,
            flare = mix(16f, -24f, f),
            squash = idle.squash + breath(p, BLOW) + if (triesFirst(v)) 0.6f * breath(p, TRY) else 0f,
            tilt = side * -2f * f,
            leftEye = eye,
            rightEye = eye,
            brows = if (f > 0.5f) MoBrows(MoBrowKind.Flat, skew = 5f) else MoBrows(MoBrowKind.Raised, lift = 3f),
            mouth = when {
                blowing -> MoMouth.Whistle
                p > 0.42f && f < 0.5f -> MoMouth.Smile(17f, 14f)
                else -> MoMouth.Flat(13f)
            },
            leftArm = MoArm.Hip,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = cycle(t, PERIOD)
        val colour = v.of(listOf(MoPropWhite, MoPropSky), salt = 2)
        breathPuff(rig, (p - BLOW) / 0.16f, reach = 1f, side(v), colour)
        if (triesFirst(v)) breathPuff(rig, (p - TRY) / 0.10f, reach = 0.5f, side(v), colour)
    }

    private fun DrawScope.breathPuff(rig: MoRig, rise: Float, reach: Float, side: Float, colour: Color) {
        if (rise <= 0f || rise >= 1f) return
        // It leaves the pursed mouth and streams up between his eyes, toward the side the tip lies on,
        // narrow enough to stay in the gap between them.
        val at = rig.mouth + Offset(9f - 7f * ease(rise * 3f) + side * rise * 26f * reach, -20f - rise * 122f * reach)
        val fade = colour.copy(alpha = 0.9f * ease(rise / 0.12f) * (1f - rise * rise))
        val length = 24f + 14f * rise
        // Three streaks of blown air fanning upward. One shape standing over his round mouth is an
        // exclamation mark, and a round one at that height is a nose.
        for (streak in -1..1) {
            val shorter = abs(streak) * 6f
            val foot = at + Offset(streak * 5f, length / 2f - shorter)
            val head = at + Offset(streak * (9f + 4f * rise) + side * 3f, -length / 2f + shorter)
            curve(foot, Offset(foot.x - streak * 2f, (foot.y + head.y) / 2f), head, fade, 5f)
        }
    }

    override val sparkles get() = false
}

/** Only one eye has woken up: he taps the sleeping one awake, and at that moment the other nods off. */
private object OneEyeAtATime : MoAct("morning_one_eye_at_a_time", MoOccasion.Morning, intensity = MoIntensity.Mild) {
    private const val SWAP = 0.60f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val half = v.between(2.6f, 3.4f, salt = 3)
        val h = cycle(t, half)
        val first = v.of(listOf(-1f, 1f), salt = 1)
        val awake = if (cycle(t, half * 2f) < 0.5f) first else -first
        val taps = 1 + v.pick(2, salt = 2)
        // On a hopeful day both eyes stay open for a moment before the first one falls.
        val hope = if (v.chance(30, salt = 4)) 0.12f else 0f
        val droop = span(h, SWAP + hope, SWAP + hope + 0.15f)
        val lookBack = span(h, SWAP + hope + 0.12f, SWAP + hope + 0.18f) * (1f - span(h, 0.95f, 1f))

        val tired = when {
            droop >= 1f -> MoEye(shape = MoEyeShape.Shut)
            else -> MoEye(
                scale = 1.05f,
                open = mix(1f, 0.15f, droop),
                lidDrop = 0.9f * droop,
                // He glances at the mitten while it taps.
                gaze = Offset(-awake * 0.7f * span(h, 0.42f, 0.48f) * (1f - droop), 0.3f * droop),
            )
        }
        val woken = when {
            h < SWAP -> MoEye(shape = MoEyeShape.Shut)
            else -> MoEye(
                // It pops open a little too wide and settles.
                scale = 1.05f + 0.09f * (1f - span(h, SWAP + 0.03f, SWAP + 0.10f)),
                open = mix(0.1f, 1f, span(h, SWAP, SWAP + 0.035f)),
                gaze = Offset(awake * 0.9f * lookBack, 0.1f * lookBack),
            )
        }

        val lifted = span(h, 0.40f, 0.47f) * (1f - span(h, SWAP + 0.03f, SWAP + 0.12f))
        val tap = (0 until taps).maxOf { bell((h - (SWAP - 0.08f * it)) / 0.04f) }
        // The mitten lands on the cheek below and outside the shut eye, never on it.
        val tapping = mix(MoArm.Down, MoArm(Offset(-20f - 9f * tap, -17f), bend = 0.5f), lifted).copy(warm = lifted)

        val openIsRight = if (h < SWAP) awake > 0f else awake < 0f
        return moIdle(t, energy = 0.6f).copy(
            leftEye = if (awake < 0f) tired else woken,
            rightEye = if (awake < 0f) woken else tired,
            brows = when {
                droop >= 1f && h < 0.95f -> MoBrows(MoBrowKind.Flat)
                openIsRight -> MoBrows(MoBrowKind.Raised, lift = 2f, skew = 4f)
                else -> MoBrows(MoBrowKind.Raised, lift = 6f, skew = -4f)
            },
            mouth = when {
                h < SWAP || h >= 0.95f -> MoMouth.Smile(12f, 8f)
                h < SWAP + 0.06f -> MoMouth.Oh(6f)
                droop <= 0f -> MoMouth.Smile(17f, 14f)
                else -> MoMouth.Flat(13f)
            },
            leftArm = if (awake > 0f) tapping else MoArm.Down,
            rightArm = if (awake > 0f) MoArm.Down else tapping,
            armsInFront = true,
            tilt = mix(-awake * 2f, awake * 2f, span(h, SWAP, SWAP + 0.15f + hope)),
            blinks = false,
        )
    }

    override val sparkles get() = false
}

/** His alarm clock rattles on the nightstand: he pats it quiet without looking and sinks back into bliss. */
private object Snooze : MoAct("morning_snooze", MoOccasion.Morning) {
    private const val PERIOD = 6.5f
    private const val PAT = 0.20f

    private fun side(v: MoVariant) = v.of(listOf(1f, -1f), salt = 1)

    private fun standX(v: MoVariant) = CENTRE + side(v) * 130f

    /** When the ringing creeps back in, after a quiet that is longer on some days. */
    private fun ringsAgain(v: MoVariant) = 0.30f + v.between(0.50f, 0.60f, salt = 6)

    private fun patsTwice(v: MoVariant) = v.chance(40, salt = 5)

    private fun ring(p: Float, v: MoVariant): Float = if (p < PAT) 1f else span(p, ringsAgain(v), 1f)

    /** How hard the mitten presses the clock flat. The first of two pats misses the button. */
    private fun press(p: Float, v: MoVariant): Float {
        val real = span(p, PAT - 0.015f, PAT + 0.005f) * (1f - span(p, 0.27f, 0.31f))
        return if (patsTwice(v)) max(real, 0.8f * bell((p - 0.125f) / 0.025f)) else real
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, PERIOD)
        val s = side(v)
        val r = ring(p, v)
        val again = ringsAgain(v)
        val press = press(p, v)

        // 0 hangs at his side, 1 lies on the clock; halfway it hovers above it, at both ends of the loop.
        val down = if (patsTwice(v)) 0.5f + 0.5f * span(p, 0f, 0.125f) - 0.3f * bell((p - 0.165f) / 0.035f) else 0.5f + 0.5f * span(p, 0f, PAT)
        val k = if (p < 0.27f) down else max(1f - span(p, 0.27f, 0.37f), 0.5f * span(p, again, 1f))
        val onClock = Offset(38f, 314f + 9f * press - MoBodies.of(body).shoulderY)
        val hand = lerp(Offset(-4f, 35f), onClock, k) + Offset(14f, -40f) * sin(k * PI.toFloat())
        val patting = MoArm(hand, bend = mix(0.1f, 0.35f, k))

        val bliss = span(p, 0.30f, 0.40f) * (1f - span(p, again - 0.14f, again - 0.04f))
        val peeking = p >= again - 0.10f && p < again
        val peek = MoEye(lidDrop = 0.95f - 0.35f * span(p, again - 0.10f, again - 0.05f), gaze = Offset(s * 0.9f, 0.6f))
        val shut = MoEye(shape = MoEyeShape.Shut)
        return moIdle(t, energy = 0.5f).copy(
            leftEye = if (peeking && s < 0f) peek else shut,
            rightEye = if (peeking && s > 0f) peek else shut,
            brows = when {
                peeking && s > 0f -> MoBrows(MoBrowKind.Raised, skew = 5f)
                peeking -> MoBrows(MoBrowKind.Raised, lift = 5f, skew = -5f)
                r > 0f -> MoBrows(MoBrowKind.Flat, lift = -3f)
                else -> MoBrows()
            },
            mouth = when {
                r > 0f -> MoMouth.Pout
                peeking -> MoMouth.Flat(10f)
                p < PAT + 0.04f -> MoMouth.Flat(12f)
                else -> MoMouth.Smile(15f, 12f)
            },
            leftArm = if (s < 0f) patting else MoArm.Down,
            rightArm = if (s > 0f) patting else MoArm.Down,
            // Upright whenever the arm is up, so the mitten meets a clock that does not lean with him.
            tilt = -s * 4f * bliss,
            flare = 4f * r - 6f * bliss,
            squash = wave(t, 3.2f) * 0.014f - 0.02f * press,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val p = cycle(t, PERIOD)
        val x0 = standX(v)
        val r = ring(p, v)
        val (colour, dark) = v.of(
            listOf(MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark, MoPropRed to MoPropRedDark, MoPropYellow to MoPropYellowDark),
            salt = 2,
        )
        floorShadow(x0, 28f, MoPropShadow)
        chunk(Offset(x0, FEET - 25f), width = 46f, height = 44f, MoPropWood, MoPropWoodDark, corner = 8f)
        drawCircle(MoPropWoodDark, 3f, Offset(x0, FEET - 30f))

        val feet = Offset(x0, FEET - 50f)
        withTransform({
            translate(wave(t, 0.06f) * 2.5f * r, 0f)
            rotate(wave(t, 0.09f) * 5f * r, feet)
            // Drawn small and simple, then enlarged so it still reads at the size of a thumbnail.
            scale(1.25f * (1f + 0.08f * press(p, v)), 1.25f * (1f - 0.2f * press(p, v)), feet)
        }) {
            val centre = Offset(x0, FEET - 67f)
            for (leg in listOf(-1f, 1f)) limb(centre + Offset(leg * 9f, 11f), feet + Offset(leg * 13f, -1f), dark, 5f)
            val hammer = Offset(x0 + wave(t, 0.05f) * 6f * r, FEET - 91f)
            limb(Offset(x0, FEET - 80f), hammer, dark, 3f)
            drawCircle(dark, 3.5f, hammer)
            for (bell in listOf(-1f, 1f)) drawCircle(dark, 7f, Offset(x0 + bell * 11f, FEET - 84f))
            drawCircle(dark, 17f, centre + Offset(0f, 4f))
            drawCircle(colour, 17f, centre)
            drawCircle(MoPropCream, 12f, centre)
            val hour = v.between(0f, 2f * PI.toFloat(), salt = 3)
            // Never aligned and never opposite, so the face always shows two hands and never one stroke.
            val minute = hour + v.of(listOf(1f, -1f), salt = 7) * v.between(0.3f, 0.7f, salt = 4) * PI.toFloat()
            limb(centre, centre + Offset(sin(hour), -cos(hour)) * 6f, MoPropInk, 3.5f)
            limb(centre, centre + Offset(sin(minute), -cos(minute)) * 9f, MoPropInk, 3f)
            drawCircle(MoPropInk, 2.2f, centre)
        }

        if (r > 0.3f) {
            val loud = MoPropGrey.copy(alpha = r)
            val bells = Offset(x0, FEET - 95f)
            // Three short dashes fan off the bells, away from him, where his reaching arm never covers
            // them. The flattest one reaches furthest sideways, and even that stays inside the frame.
            for (angle in listOf(1.35f, 0.8f, 0.25f)) {
                val out = Offset(side(v) * cos(angle), -sin(angle))
                val reach = 13f + 2f * pulse(t, 0.24f)
                limb(bells + out * reach, bells + out * (reach + 11f), loud, 4.5f)
            }
        }
    }

    override val ownsTheFloor get() = true
}

/** A huge stretching yawn, and the little bird beside him catches it. Then he feels the next one coming. */
private object CatchingYawn : MoAct("morning_catching_yawn", MoOccasion.Morning) {
    private const val PERIOD = 7f

    private fun side(v: MoVariant) = v.of(listOf(1f, -1f), salt = 1)

    private fun yawn(p: Float): Float = 1f - span(p, 0.20f, 0.32f) + span(p, 0.84f, 1f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, PERIOD)
        val s = side(v)
        val y = yawn(p)
        val coming = span(p, 0.70f, 0.84f)
        val idle = moIdle(t, energy = 0.6f)
        val stretch = mix(MoArm(Offset(-2f, 35f), bend = 0.1f), MoArm(Offset(30f + wave(t, 0.5f) * 3f, -62f), bend = 0.3f), y)
        val eye = if (y > 0.5f) MoEye(shape = MoEyeShape.Shut) else MoEye(lidDrop = mix(0.5f, 0.3f, coming), gaze = Offset(s * 0.9f, 0.7f))
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (p in 0.70f..0.84f) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(),
            mouth = when {
                y > 0.08f -> MoMouth.Yawn(open = y)
                p > 0.70f -> MoMouth.Oh(size = mix(4f, 7f, coming))
                else -> MoMouth.Smile(13f, 9f)
            },
            leftArm = stretch,
            rightArm = stretch,
            legs = MoLegs(MoLeg(swing = 4f * y), MoLeg(swing = 4f * y)),
            squash = idle.squash + 0.06f * y,
            flare = 12f * y,
            // Half a step away from the bird, so it has room to stand.
            offset = Offset(-s * 14f, -4f * y),
            tilt = -s * 3f * y,
            blinks = y <= 0.5f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val (colour, dark) = v.of(
            listOf(MoPropSky to MoPropBlue, MoPropPink to MoPropPinkDark, MoPropTeal to MoPropTealDark, MoPropYellow to MoPropYellowDark),
            salt = 2,
        )
        val size = v.between(1.35f, 1.55f, salt = 3)
        // As far out as its round body allows, which keeps its beak clear of his mitten.
        val base = Offset(CENTRE + s * (157f - 17.5f * size), FEET)
        floorShadow(base.x, 17f * size, MoPropShadow)
        bird(base, size, s, caught(t, delay = 0f), 1.5f * pulse(t, 1.7f), colour, dark)
        // On some days a chick has come too, and the yawn goes down the line. There is no floor left
        // beside them inside the frame, so it stands a step nearer instead, in the bird's darker tone
        // so that the two never melt into one shape.
        if (v.chance(30, salt = 4)) {
            val chick = base - Offset(s * 22f, 0f)
            floorShadow(chick.x, 10f * size, MoPropShadow)
            bird(chick, size * 0.52f, s, caught(t, delay = 0.5f), 0f, dark, colour)
        }
    }

    /** The bird's own yawn, which starts once Mo's is over. */
    private fun caught(t: Float, delay: Float): Float {
        val p = cycle(t - delay, PERIOD)
        return span(p, 0.42f, 0.50f) * (1f - span(p, 0.62f, 0.70f))
    }

    /** One round bird standing on [base] and facing Mo, yawning by [b]. */
    private fun DrawScope.bird(base: Offset, size: Float, s: Float, b: Float, bob: Float, colour: Color, dark: Color) {
        withTransform({
            translate(base.x, base.y)
            scale(size, size, Offset.Zero)
        }) {
            for (leg in listOf(-1f, 1f)) limb(Offset(leg * 5f, -12f), Offset(leg * 5f - s * 1.5f, -1.5f), MoPropOrange, 3f)
            val body = Offset(0f, -24f - 3f * b - bob)
            oval(body, 17f, 14f + 2f * b, colour)
            oval(body + Offset(-s * 7f, 3f), 9f, 8f, MoPropCream)
            // The stubby wing stretches up with the yawn, never past the body's outer edge, which is
            // already as far out as the frame allows.
            oval(body + Offset(s * (6f + 3f * b), -1f - 10f * b), 9f - 2f * b, 6f + 3f * b, dark)

            // The head tips back off Mo's side and the beak gapes, which is the whole of its yawn.
            val head = Offset(-s * (3f - 7f * b), -46f - 8f * b - bob)
            val hinge = head + Offset(-s * 9f, 1f)
            val up = (48f * b) * PI.toFloat() / 180f
            val low = (32f * b) * PI.toFloat() / 180f
            val upper = hinge + Offset(-s * cos(up), -sin(up)) * 9f
            val lower = hinge + Offset(-s * cos(low), sin(low)) * 8f
            shape(MoPropOrange, hinge.x to hinge.y - 4f, upper.x to upper.y, hinge.x to hinge.y + 1f)
            shape(MoPropOrange, hinge.x to hinge.y, lower.x to lower.y, hinge.x to hinge.y + 4f)
            drawCircle(colour, 11f, head)
            val eye = head + Offset(-s * 3f, -3f)
            if (b < 0.3f) {
                drawCircle(MoPropInk, 2.2f, eye)
            } else {
                curve(eye + Offset(-3f, -1f), eye + Offset(0f, 2.5f), eye + Offset(3f, -1f), MoPropInk, 2.5f)
            }
        }
    }

    override val ownsTheFloor get() = true
}

/** Morning toe-touches on arms far too short: he strains, gives up, and lifts the foot to the mitten instead. */
private object ToesComeToHim : MoAct("morning_toes_come_to_him", MoOccasion.Morning) {
    private const val HALF = 3.2f

    private fun first(v: MoVariant) = v.of(listOf(1f, -1f), salt = 1)

    /** The side he is working on now. */
    private fun side(t: Float, v: MoVariant) = if (cycle(t, HALF * 2f) < 0.5f) first(v) else -first(v)

    private fun strain(h: Float) = 1f - span(h, 0.42f, 0.52f)

    /** The next side's strain already builds as this half ends, so every half starts at full strain. */
    private fun nextStrain(h: Float) = span(h, 0.85f, 1f)

    /** How far the foot has come up to the mitten; with two taps it dips away once and comes back. */
    private fun foot(h: Float, v: MoVariant): Float {
        val up = span(h, 0.45f, 0.60f) * (1f - span(h, 0.72f, 0.84f))
        return if (v.pick(2, salt = 2) == 1) up * (1f - 0.5f * bell((h - 0.66f) / 0.035f)) else up
    }

    /** Out and up until the toes are under the mitten: the swing brings the foot clear of his body. */
    private fun leg(g: Float) = MoLeg(lift = 28f * g, swing = 85f * g)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val h = cycle(t, HALF)
        val d = side(t, v)
        val e = strain(h)
        val next = nextStrain(h)
        val g = foot(h, v)
        val effort = max(e, next)
        // The side he strains toward: this one, and the other as the half ends.
        val lean = d * e - d * next

        // The hand hangs where the toes will come, far enough out past his side that the tap happens
        // against the background: on his silhouette the mitten is the same orange as the foot meeting
        // it, and the two read as one blob. The lean does the straining; the arm barely gains on it.
        val low = 362f - MoBodies.of(body).shoulderY
        val balance = MoArm(Offset(44f, -4f + 4f * wave(t, 0.9f)), bend = 0.1f)
        val reaching = MoArm(Offset(31f + 3f * e, low + 6f * e + wave(t, 0.11f) * 1.5f * e), bend = 0.05f)
        val nextReaching = MoArm(Offset(34f, low + 6f + wave(t, 0.11f) * 1.5f * next), bend = 0.05f)
        val working = mix(reaching, balance, span(h, 0.78f, 0.90f))
        val other = mix(balance, nextReaching, next)
        val leg = leg(g)

        val down = MoEye(lid = -0.2f * effort, gaze = Offset(lean * 0.7f + d * 0.7f * span(h, 0.45f, 0.55f) * (1f - span(h, 0.78f, 0.86f)), 0.9f * max(effort, g)))
        val eye = if (g > 0.8f) MoEye(shape = MoEyeShape.Happy) else down
        return moIdle(t, energy = 0.9f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (effort > 0.5f) MoBrows(MoBrowKind.Worried, lift = 2f) else MoBrows(),
            mouth = when {
                effort > 0.5f -> MoMouth.Gritted
                h < 0.54f -> MoMouth.Oh(6f)
                g > 0.5f -> MoMouth.Grin(open = 0.7f)
                else -> MoMouth.Smile()
            },
            leftArm = if (d < 0f) working else other,
            rightArm = if (d > 0f) working else other,
            legs = if (d < 0f) MoLegs(left = leg) else MoLegs(right = leg),
            offset = Offset(-6f * lean, -6f * effort),
            tilt = lean * 15f + d * 3f * g,
            squash = wave(t, 3.2f) * 0.014f - 0.07f * effort,
            shake = wave(t, 0.07f) * 1.2f * effort,
            blinks = effort < 0.5f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        if (v.chance(40, salt = 5)) drawCircle(MoPropYellow, 24f, Offset(if (v.chance(50, salt = 4)) 236f else -6f, 44f))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val h = cycle(t, HALF)
        val d = side(t, v)
        val next = nextStrain(h)
        val effort = max(strain(h), next)
        val outside = if (next > 0f) -d else d
        for (index in 0..v.pick(2, salt = 3)) {
            val fly = cycle(t, 0.9f, offset = 0.2f + index * 0.5f)
            drop(rig.tip + Offset(outside * (46f + fly * 34f), 70f - fly * 40f), 7f * ease(fly / 0.12f), MoPropWater.copy(alpha = (1f - fly * fly) * effort))
        }
        // The kit moves a swung leg only half as far as its foot, so this joins the two again.
        val leg = leg(foot(h, v))
        val hip = Offset((if (d < 0f) rig.body.leftLegX else rig.body.rightLegX) + 15f + d * leg.swing / 2f, 403f - leg.lift)
        limb(hip, if (d < 0f) rig.leftFoot else rig.rightFoot, rig.palette.feet, 22f)

        // The tap itself: a spark right where the toes meet the mitten, which is also what keeps the
        // two orange shapes apart. With two taps the foot dips away in the middle, and the spark
        // goes out and comes back with it.
        val touch = span(foot(h, v), 0.88f, 1f)
        if (touch > 0f) {
            val hand = if (d < 0f) rig.leftHand else rig.rightHand
            val toes = if (d < 0f) rig.leftFoot else rig.rightFoot
            sparkle((hand + toes) / 2f + Offset(d * 4f, -3f), (13f + 3f * pulse(t, 0.3f)) * touch, 1f, MoPropYellow)
        }
    }

    override val sparkles get() = false
}

/** The potted flower beside him has turned to face him, not the window: he is the warm bright thing here. */
private object SomebodysSunrise : MoAct("morning_somebodys_sunrise", MoOccasion.Morning, intensity = MoIntensity.Mild) {
    private const val PERIOD = 5.2f

    private fun side(v: MoVariant) = v.of(listOf(1f, -1f), salt = 1)

    /** 0 away and 1 closest, starting a quarter of the sway in so that t = 0 already leans toward it. */
    private fun near(t: Float) = pulse(t + PERIOD / 4f, PERIOD)

    /** The flower does what he does, a beat late. */
    private fun lean(t: Float) = near(t - 0.6f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val near = near(t)
        // About half a second around the closest point, as pulse spends it above this.
        val eye = if (near > 0.977f) MoEye(shape = MoEyeShape.Shut) else MoEye(lidDrop = 0.2f, gaze = Offset(s * 0.9f, 0.15f))
        val far = MoArm(Offset(4f + 4f * near, 36f - 6f * near), bend = 0.1f)
        return moIdle(t, energy = 0.7f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = 2f + 2f * near),
            mouth = MoMouth.Smile(width = 13f + 4f * near, depth = 9f + 5f * near),
            leftArm = if (s > 0f) far else MoArm.Down,
            rightArm = if (s > 0f) MoArm.Down else far,
            tilt = s * (0.5f + 4f * near),
            // He stands half a step aside, so the flower has room between them.
            offset = Offset(s * (-16f + 3f * near), 0f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val lean = lean(t)
        val petal = v.of(listOf(MoPropPink, MoPropYellow, MoPropPurple, MoPropWhite), salt = 2)
        val (pot, potDark) = v.of(listOf(MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark, MoPropRed to MoPropRedDark), salt = 3)
        val px = CENTRE + s * 131f
        floorShadow(px, 28f, MoPropShadow)
        // Drawn small around its own foot, then enlarged so the flower reaches the height of his face.
        withTransform({
            translate(px, FEET)
            scale(1.45f, 1.45f, Offset.Zero)
        }) {
            // Always turned his way, and further over the closer he is: the stem swings, he does not.
            val head = Offset(-s * (4f + 9f * lean), -98f + 4f * lean)
            curve(Offset(0f, -34f), Offset(s * 9f, -70f), head, MoPropGrass, 5f)
            oval(Offset(s * 8f, -52f - 3f * lean), 8f, 4.5f, MoPropLeaf)
            if (v.chance(35, salt = 5)) {
                val bud = Offset(-s * 9f, -66f - 3f * lean)
                curve(Offset(s * 4f, -50f), Offset(-s * 6f, -52f), bud, MoPropGrass, 4f)
                drawCircle(petal, 5f, bud)
            } else {
                oval(Offset(-s * 5f, -64f - 3f * lean), 10f, 5f, MoPropLeaf)
            }
            chunk(Offset(0f, -17f), width = 32f, height = 30f, pot, potDark, corner = 8f)
            block(Offset(0f, -33f), 36f, 8f, pot, corner = 4f)

            val petals = 5 + v.pick(2, salt = 4)
            for (index in 0 until petals) {
                val angle = index * 2f * PI.toFloat() / petals - PI.toFloat() / 2f
                oval(head + Offset(cos(angle), sin(angle)) * (8f + 2.5f * lean), 6f, 6f, petal)
            }
            // The centre sits toward him, which turns the flower's face his way instead of ours.
            drawCircle(if (petal == MoPropYellow) MoPropOrange else MoPropGold, 6.5f, head + Offset(-s * 3f, 0.5f))

            if (v.chance(50, salt = 6)) {
                // It pops as the petals open widest, which is 0.6 s after he was closest.
                val sinceOpen = cycle(t - 0.6f - PERIOD / 4f + 0.25f, PERIOD) * PERIOD
                if (sinceOpen < 0.5f) sparkle(head + Offset(s * 8f, -22f), 8f * sin(sinceOpen / 0.5f * PI.toFloat()), 1f, petal)
            }
        }
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

internal val morningActs1: List<MoAct> = listOf(
    BedheadTip,
    OneEyeAtATime,
    Snooze,
    CatchingYawn,
    ToesComeToHim,
    SomebodysSunrise,
)
