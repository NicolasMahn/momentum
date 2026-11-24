package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropMoon
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
import de.nicolasmahn.momentum.core.ui.theme.MoSpark
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBody
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoBrowKind
import de.nicolasmahn.momentum.feature.mo.MoBrows
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoEyeShape
import de.nicolasmahn.momentum.feature.mo.MoLeg
import de.nicolasmahn.momentum.feature.mo.MoLegs
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPalettes
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.polyline
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Happy, part one: a day with everything done, which he spends playing, mostly with toys and once
 * with his own fire.
 */

private const val TURN = 2f * PI.toFloat()

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm =
    MoArm(mix(from.reach, to.reach, amount), mix(from.bend, to.bend, amount))

/** One beat of a longer cycle: 0 before [from], 1 after [to], eased in between. */
private fun span(x: Float, from: Float, to: Float): Float = ease((x - from) / (to - from))

/** A bump from 0 up to 1 and back while [x] crosses [from]..[to], and 0 outside it. */
private fun bump(x: Float, from: Float, to: Float): Float =
    if (x > from && x < to) sin((x - from) / (to - from) * PI.toFloat()) else 0f

/**
 * His outer flame as it stands in [pose], for hiding whatever passes behind him. The kit keeps the
 * lean of the flame to itself, so this repeats it: a point leans and rises with the square of its
 * height above the pivot.
 */
private fun silhouette(body: MoBody, pose: MoPose): Path = body.outer.toPath { point ->
    val height = ((body.pivot - point.y) / (body.pivot - body.top)).coerceIn(0f, 1.2f)
    Offset(point.x + pose.sway * height * height, point.y - pose.flare * height * height)
}

/** A stroke with square ends, for a stripe that has to stop exactly where the next one starts. */
private fun DrawScope.band(colour: Color, width: Float, points: List<Offset>) {
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, colour, style = Stroke(width = width, cap = StrokeCap.Butt))
}

/** A finished day: he roasts a corn cob over his own tip, never past golden, and eats it off the stick. */
private object RoastedCorn : MoAct("happy_roasted_corn", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private val overTheFlame = MoArm(Offset(24f, -60f), bend = 0.3f)
    private val atTheMouth = MoArm(Offset(-32f, 2f), bend = 0.5f)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    // A third of the way in at t = 0, so the first frame is a half-golden cob over his tip.
    private fun round(t: Float, v: MoVariant) = cycle(t, v.between(5f, 7f, salt = 3), offset = 0.3f)

    /** 0 while the stick is up over his flame, 1 while its end is at his mouth. */
    private fun lowered(c: Float) = span(c, 0.60f, 0.72f) * (1f - span(c, 0.92f, 1f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = round(t, v)
        val side = side(v)
        val down = lowered(c)
        val stick = mix(overTheFlame, atTheMouth, down).copy(inFront = true)
        val chewing = c in 0.74f..0.90f
        val eye = if (c in 0.74f..0.93f) MoEye(shape = MoEyeShape.Happy) else MoEye(gaze = Offset(side * 0.55f, mix(-0.9f, 0.8f, down)))
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (down < 0.5f) MoBrows(MoBrowKind.Raised, lift = 2f) else MoBrows(),
            mouth = when {
                c < 0.60f || c >= 0.95f -> MoMouth.TongueOut
                c < 0.74f -> MoMouth.Grin(open = down)
                chewing -> MoMouth.Chew(open = 0.5f + 0.5f * wave(t, 0.28f))
                else -> MoMouth.Smile()
            },
            leftArm = if (side < 0f) stick else MoArm.Hip,
            rightArm = if (side > 0f) stick else MoArm.Hip,
            // He turns himself up a little while it toasts.
            flare = (5f + 3f * pulse(t, 1.1f)) * (1f - down),
            squash = idle.squash + if (chewing) 0.015f * pulse(t, 0.28f) else 0f,
            tilt = side * 2f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = round(t, v)
        val side = side(v)
        val down = lowered(c)
        val hand = if (side > 0f) rig.rightHand else rig.leftHand
        val husk = v.chance(50, salt = 2)

        // Beside the tip and above it, so the tip always shows. His tip leans right of where the rig puts it.
        val overTip = rig.tip + Offset(-3f + side * 35f, -24f)
        val byMouth = rig.mouth + Offset(side * 26f, 4f)
        // It comes down in an arc round the outside of his eye, not across it.
        val outside = Offset(CENTRE + side * 160f, rig.mouth.y + 20f)
        val bitten = span(c, 0.72f, 0.80f)
        // The bare end leaves his mouth before the stick swings up, or the loop would jump at its seam.
        val inMouth = bitten * (1f - span(c, 0.88f, 0.93f))
        val at = mix(mix(mix(overTip, outside, down), mix(outside, byMouth, down), down), rig.mouth + Offset(0f, 4f), inMouth)

        val along = at - hand
        val length = along.getDistance()
        if (length > 1f) {
            // The stick shortens as it swings down because it then points at the viewer.
            val butt = along / length * min(14f, length * 0.4f)
            limb(hand - butt, at, MoPropWood, 5f)
            grippingMitten(rig, side)
        }

        // Gone while he chews; the fresh one pops onto the stick as it swings back up.
        val size = (1f - bitten) + span(c, 0.95f, 0.99f)
        val gold = if (c < 0.9f) ease(c / 0.6f) else 0f
        if (size > 0.02f) {
            // The cob lies along the stick, which runs into its near end.
            val angle = atan2(along.y, along.x) * 180f / PI.toFloat()
            withTransform({ rotate(angle, at) }) { cob(at, size, gold, husk) }
        }
        for (index in 0..1) {
            val rise = cycle(t, 2.2f, offset = index * 0.5f)
            // It fades in as well as out, so a curl never pops into being.
            val steam = 0.5f * min(1f, rise * 5f) * (1f - rise) * (1f - down) * min(1f, gold * 2f)
            puff(at + Offset((index - 0.5f) * 12f + sin(rise * 5f) * 3f, -24f - rise * 18f), 5f, MoPropWhite.copy(alpha = steam))
        }
    }

    /** Drawn lying along +x from [at]; [gold] toasts it and [size] shrinks it while he eats. */
    private fun DrawScope.cob(at: Offset, size: Float, gold: Float, husk: Boolean) {
        val length = 48f * size
        val girth = 22f * size
        val centre = at + Offset(length * 0.3f, 0f)
        if (husk) {
            for (flap in listOf(-1f, 1f)) {
                withTransform({ rotate(flap * 24f, at) }) { oval(at + Offset(4f * size, 0f), 14f * size, 6f * size, MoPropGreen) }
            }
        }
        chunk(centre, length, girth, MoPropYellow, MoPropYellowDark, corner = girth * 0.48f, drop = 3f)
        chunk(centre, length, girth, MoPropGold.copy(alpha = gold * 0.85f), MoPropGoldDark.copy(alpha = gold * 0.85f), corner = girth * 0.48f, drop = 3f)
        // Rows of kernels, and the toasted spots come up between them as it goes golden.
        for (row in -1..1) {
            for (k in 0..4) {
                val kernel = centre + Offset((k - 2f) * length * 0.17f + row * 2f, row * girth * 0.26f)
                drawCircle(MoPropYellowDark.copy(alpha = 0.6f), 1.8f * size, kernel)
                if ((k + row) % 3 == 0) drawCircle(MoPropWood.copy(alpha = gold * 0.8f), 2.4f * size, kernel + Offset(2f * size, 0f))
            }
        }
    }
}

/** A round streak: he crouches, shoots a spark off his tip, and throws his arms up when it bursts into a ring. */
private object OwnFireworks : MoAct("happy_own_fireworks", MoOccasion.Happy, intensity = MoIntensity.Strong) {
    private const val LAUNCH_SECONDS = 2.4f
    private val armsUp = MoArm(Offset(32f, -64f), bend = 0.3f)
    private val fistsTucked = MoArm(Offset(-4f, 26f), bend = 0.6f)
    private val colours = listOf(MoPropPink, MoPropSky, MoPropMint, MoPropGold, MoPropPurple)

    // Half way in at t = 0: the ring is already open in a corner and his arms are up.
    private fun launch(t: Float) = cycle(t, LAUNCH_SECONDS, offset = 0.5f)

    /** Which upper corner this launch bursts in; they take turns. */
    private fun corner(t: Float, v: MoVariant): Float {
        val evenLaunch = cycle(t, LAUNCH_SECONDS * 2f, offset = 0.25f) < 0.5f
        return if (evenLaunch == v.chance(50, salt = 3)) 1f else -1f
    }

    // Far enough into the corner that a full ring stays inside the frame and clear of his tip.
    private fun burst(corner: Float) = Offset(CENTRE + corner * 94f, 72f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = launch(t)
        val corner = corner(t, v)
        val crouch = span(c, 0.85f, 1f)
        val spring = span(c, 0f, 0.05f)
        val cheer = span(c, 0.24f, 0.34f) * (1f - span(c, 0.76f, 0.86f))
        val hop = bump(c, 0.3f, 0.55f)
        val eye = when {
            c >= 0.85f -> MoEye(shape = MoEyeShape.Shut)
            c < 0.3f -> MoEye(gaze = Offset(corner * 0.8f * (c / 0.3f), -0.9f), iris = 0.6f)
            else -> MoEye(shape = MoEyeShape.Star)
        }
        // His fists stay tucked through the wrap of the cycle and let go as he springs.
        val tucked = if (c < 0.5f) 1f - span(c, 0f, 0.1f) else crouch
        val arm = mix(mix(MoArm.Down, fistsTucked, tucked), armsUp, cheer)
        val idle = moIdle(t, energy = 1.6f)
        return idle.copy(
            palette = MoPalettes.Golden,
            leftEye = eye,
            rightEye = eye,
            mouth = when {
                c >= 0.85f -> MoMouth.Smile(width = 12f)
                c < 0.3f -> MoMouth.Oh(size = 8f)
                else -> MoMouth.Beam
            },
            leftArm = arm,
            rightArm = arm,
            legs = MoLegs(MoLeg(lift = 5f * hop), MoLeg(lift = 5f * hop)),
            // He gathers himself, springs, and the flame that shot up settles while the ring fades.
            squash = idle.squash + when {
                c >= 0.85f -> -0.07f * crouch
                c < 0.05f -> mix(-0.07f, 0.06f, spring)
                else -> 0.06f * (1f - span(c, 0.05f, 0.2f))
            },
            flare = when {
                c >= 0.85f -> -8f * crouch
                c < 0.05f -> mix(-8f, 26f, spring)
                else -> mix(26f, 6f, span(c, 0.05f, 0.3f)) * (1f - span(c, 0.3f, 0.85f))
            },
            offset = Offset(0f, -8f * hop),
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = launch(t)
        if (c < 0.03f || c >= 0.3f) return
        val from = rig.tip + Offset(-4f, -10f)
        val to = burst(corner(t, v))
        // Straight up off the tip first, then over into the corner.
        fun at(progress: Float): Offset {
            val p = progress.coerceIn(0f, 1f)
            return Offset(mix(from.x, to.x, p * p), mix(from.y, to.y, 1f - (1f - p) * (1f - p)))
        }
        val rise = (c - 0.03f) / 0.27f
        limb(at(rise - 0.2f), at(rise), MoSpark.copy(alpha = 0.6f), 6f)
        sparkle(at(rise), 13f, 1f, MoSpark)
    }

    // The ring belongs to the sky, not to him, so it does not hop when he does.
    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val c = launch(t)
        if (c < 0.3f) return
        val q = (c - 0.3f) / 0.7f
        val centre = burst(corner(t, v))
        val first = v.pick(colours.size, salt = 1)
        val count = 6 + 2 * v.pick(2, salt = 2)
        // Fast out of the bang and then hanging, sinking a little as it fades.
        val radius = 10f + 44f * (1f - (1f - q) * (1f - q) * (1f - q))
        val fade = 1f - q * q
        sparkle(centre, 24f, max(0f, 1f - q * 5f), MoPropMoon)
        for (index in 0 until count) {
            val angle = index * TURN / count + 0.3f
            val out = Offset(cos(angle), sin(angle))
            val colour = colours[(first + index % 2 * 2) % colours.size].copy(alpha = fade)
            val at = centre + out * radius + Offset(0f, 12f * q * q)
            limb(at - out * (radius * 0.4f * (1f - q)), at, colour.copy(alpha = fade * 0.7f), 4.5f)
            // Chunky to the end: they fade out more than they shrink.
            sparkle(at, 20f, 1f - 0.6f * q, colour)
        }
    }

    override val sparkles get() = false
}

/** A good week: a bright hoop swings round his base while his hips circle the other way, arms up out of its path. */
private object HulaHoop : MoAct("happy_hula_hoop", MoOccasion.Happy) {
    private const val TURN_SECONDS = 1.1f

    // How flat the hoop's circle looks from where we stand.
    private const val FLATTEN = 0.17f
    private const val STRIPES = 6
    private const val HOOP_WIDTH = 11f

    private val hoops = listOf(
        MoPropPink to MoPropPinkDark,
        MoPropBlue to MoPropBlueDark,
        MoPropGreen to MoPropGreenDark,
        MoPropPurple to MoPropPurpleDark,
    )

    /** Where round him the hoop touches: 0 is at his right side, a quarter turn is in front of him. */
    private fun touch(t: Float, v: MoVariant) = cycle(t, TURN_SECONDS) * TURN * if (v.chance(50, salt = 3)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val a = touch(t, v)
        // His hips lead the hoop by a beat, and his tip swings the other way.
        val hips = cos(a + 0.6f)
        val showingOff = twitch(t, period = 4.4f, beat = 1.1f) > 0.5f
        val eye = if (showingOff) MoEye(gaze = Offset(0f, 0.3f)) else MoEye(shape = MoEyeShape.Happy)
        val idle = moIdle(t, energy = 1.3f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            mouth = if (showingOff) MoMouth.Grin(open = 0.7f) else MoMouth.TongueOut,
            leftArm = MoArm(Offset(34f + 4f * sin(a), -46f + 4f * cos(a)), bend = 0.6f),
            rightArm = MoArm(Offset(34f - 4f * sin(a), -46f - 4f * cos(a)), bend = 0.6f),
            // The feet swing against the hips, which keeps them planted while he circles.
            legs = MoLegs(MoLeg(swing = 7f * hips), MoLeg(swing = -7f * hips)),
            offset = Offset(7f * hips, 0f),
            sway = idle.sway - 10f * hips,
            squash = idle.squash + 0.01f * cos(2f * a),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val a = touch(t, v)
        val (bright, dark) = v.of(hoops, salt = 1)
        val striped = v.chance(40, salt = 2)

        // The hoop is wider than he is and hangs from the side it touches, so its centre circles him
        // on the far side of that touch, and it dips away from it.
        val waist = if (rig.body.kind == MoBodyKind.Low) 70f else 86f
        val radius = waist + 24f
        val centre = Offset(CENTRE - 20f * cos(a), rig.mouth.y + 30f + 3f * wave(t, 2.2f) - 20f * FLATTEN * sin(a))
        fun at(angle: Float) = centre + Offset(radius * cos(angle), radius * FLATTEN * sin(angle) - 8f * cos(angle - a))
        fun arc(from: Float, to: Float, steps: Int) = List(steps + 1) { at(mix(from, to, it / steps.toFloat())) }

        fun half(from: Float, colour: Color, stripe: Color) {
            polyline(colour, HOOP_WIDTH, arc(from, from + TURN / 2f, 24))
            if (!striped) return
            // The stripes travel round with the hoop. Half a turn is a whole number of stripe pairs,
            // so the wrap of the angle does not show.
            val pair = TURN / STRIPES
            val roll = (a * 0.5f).mod(pair)
            for (index in -1..STRIPES) {
                val start = max(roll + index * pair, from)
                val end = min(roll + index * pair + pair / 2f, from + TURN / 2f)
                if (end - start > 0.02f) band(stripe, HOOP_WIDTH, arc(start, end, 4))
            }
        }

        // The far half only shows where it sticks out beyond him; the near half lies across his belly.
        clipPath(rig.body.outer.toPath(), clipOp = ClipOp.Difference) { half(TURN / 2f, dark, MoPropGrey) }
        half(0f, bright, MoPropWhite)
    }
}

/** A good week: he skips rope, hopping exactly as it passes under him, and every fourth turn he tucks both feet for a high one. */
private object SkippingRope : MoAct("happy_skipping_rope", MoOccasion.Happy) {
    private fun tempo(v: MoVariant) = v.between(0.7f, 0.95f, salt = 3)

    // Four turns make a round. Just past the top at t = 0: the rope arches high over his tip.
    private fun round(t: Float, v: MoVariant) = cycle(t, tempo(v) * 4f, offset = 0.01f)

    /** 0 with the rope straight up, 0.5 with it under his feet. It comes down in front of him. */
    private fun turn(t: Float, v: MoVariant) = (round(t, v) * 4f).mod(1f)

    private fun highHop(t: Float, v: MoVariant) = v.chance(75, salt = 4) && round(t, v) >= 0.75f

    /** How far off the floor he is: up exactly while the rope is in the lower half of its turn. */
    private fun lift(t: Float, v: MoVariant) = max(0f, -cos(turn(t, v) * TURN)) * if (highHop(t, v)) 27f else 16f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val r = turn(t, v)
        val a = r * TURN
        val high = highHop(t, v)
        val up = max(0f, -cos(a))
        val gather = bump(r, 0.13f, 0.25f)
        val landing = bump(r, 0.75f, 0.87f)
        val wrist = MoArm(Offset(40f + 3f * sin(a), 8f - 4f * cos(a)), bend = 0.5f)
        val eye = if (high && up > 0.15f) MoEye(shape = MoEyeShape.Happy) else MoEye(gaze = Offset(0f, -0.8f * cos(a)))
        val tuck = MoLeg(lift = up * if (high) 14f else 8f)
        val idle = moIdle(t, energy = 1.3f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            mouth = MoMouth.Grin(open = 0.7f),
            leftArm = wrist,
            rightArm = wrist,
            legs = MoLegs(tuck, tuck),
            offset = Offset(0f, -lift(t, v)),
            squash = idle.squash + 0.04f * up - 0.02f * gather - 0.035f * landing,
            // His flame lags behind him: pressed down on the way up, streaming on the way down.
            flare = -12f * sin(a) * up + 6f * landing,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val r = turn(t, v)
        val a = r * TURN
        val colour = v.of(listOf(MoPropSky, MoPropPink, MoPropMint), salt = 1)
        val (handle, handleLedge) = v.of(listOf(MoPropRed to MoPropRedDark, MoPropYellow to MoPropYellowDark), salt = 2)

        val left = rig.leftHand + Offset(-14f, 0f)
        val right = rig.rightHand + Offset(14f, 0f)
        val hands = (left.y + right.y) / 2f
        // Its middle clears his tip at the top and brushes the floor at the bottom. Everything here
        // hops with him, so the floor is further down the higher he is.
        val above = hands - (rig.tip.y - 38f)
        val below = FEET - 1f + lift(t, v) - hands
        val middle = hands - cos(a) * (above + below) / 2f - cos(a) * cos(a) * (above - below) / 2f
        // A cubic hung from both hands passes through its middle when the controls sit a third further out.
        val control = (4f * middle - hands) / 3f
        val rope = Path().apply {
            moveTo(left.x, left.y)
            cubicTo(left.x, control, right.x, control, right.x, right.y)
        }
        val stroke = Stroke(width = 6.5f, cap = StrokeCap.Round)
        if (r < 0.5f) {
            drawPath(rope, colour, style = stroke)
        } else {
            // On its way up it is behind him: hidden by his body and legs, in sight again over his tip.
            clipPath(silhouette(rig.body, pose(t, v, rig.body.kind)), clipOp = ClipOp.Difference) {
                val legs = Path().apply {
                    for ((foot, legX) in listOf(rig.leftFoot to rig.body.leftLegX, rig.rightFoot to rig.body.rightLegX)) {
                        addRect(Rect(legX, rig.body.legTop - (413f - foot.y), legX + 30f, foot.y))
                        addOval(Rect(foot.x - 19f, foot.y - 12f, foot.x + 19f, foot.y + 12f))
                    }
                }
                clipPath(legs, clipOp = ClipOp.Difference) {
                    drawPath(rope, colour, style = stroke)
                }
            }
        }
        for ((hand, side) in listOf(rig.leftHand to -1f, rig.rightHand to 1f)) {
            val grip = hand + Offset(-side * 13f, 0f)
            val end = hand + Offset(side * 14f, 0f)
            limb(grip + Offset(0f, 3f), end + Offset(0f, 3f), handleLedge, 10f)
            limb(grip, end, handle, 10f)
            grippingMitten(rig, side)
        }
    }

    override val ownsTheFloor get() = true
}

/** A finished day: a beach ball bounces on the very top of his flame, and he shuffles to stay under it. */
private object BeachBallOnTip : MoAct("happy_beach_ball_on_tip", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private const val RADIUS = 26f

    // How far his tip gives under the ball: the duck in the pose, as the rig's tip feels it.
    private const val DUCK = 8.6f
    private val wedges = listOf(MoPropRed, MoPropYellow, MoPropBlue, MoPropGreen, MoPropPink)

    // The tall body leaves little room under the top of the frame, so there the bounce is low and quick.
    private fun period(body: MoBodyKind) = if (body == MoBodyKind.Low) 1.3f else 1f

    /** 0 as the ball touches his tip and 0.5 at the top of its arc, which is where t = 0 is. */
    private fun bounce(t: Float, body: MoBodyKind) = cycle(t, period(body), offset = 0.5f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = bounce(t, body)
        val touching = ease(1f - min(b, 1f - b) / 0.09f)
        val flick = bump(b, 0.05f, 0.25f)
        val shuffle = wave(t, period(body) * 2f)
        val step = wave(t, period(body))
        val eye = MoEye(gaze = Offset(0.2f * shuffle, -1f), iris = 0.7f)
        val idle = moIdle(t, energy = 1.2f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = 3f),
            mouth = if (touching > 0.4f) MoMouth.TongueOut else MoMouth.Grin(open = 0.6f),
            leftArm = MoArm(Offset(36f, -14f + 5f * step), bend = 0.2f),
            rightArm = MoArm(Offset(36f, -14f - 5f * step), bend = 0.2f),
            legs = MoLegs(MoLeg(lift = 5f * max(0f, step)), MoLeg(lift = 5f * max(0f, -step))),
            offset = Offset(10f * shuffle, 0f),
            sway = idle.sway + 5f * shuffle,
            // The tip gives as the ball lands and flicks it back up. He stands a little low, for balance.
            flare = -10f * touching + 8f * flick,
            squash = idle.squash - 0.03f - 0.03f * touching,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val body = rig.body.kind
        val b = bounce(t, body)
        val arc = 4f * b * (1f - b)
        // As high as the variant likes, or as high as the frame allows over the tall body.
        val height = min(v.between(50f, 70f, salt = 2), rig.body.top - 2f * RADIUS - 4f)
        // The top of his flame sits beside the rig's tip: left of it when tall, right of it when low.
        val crownX = rig.tip.x + if (body == MoBodyKind.Low) 15f else -5f
        val landing = rig.body.top + DUCK
        // It wanders off while it is in the air and comes back to the tip to land.
        val drift = 14f * wave(t + 0.3f, period(body) * 2f) * arc
        val centre = Offset(crownX + drift, landing - RADIUS - 1f - (height + DUCK) * arc)

        val direction = if (v.chance(50, salt = 3)) 1f else -1f
        val spin = cycle(t, 4.5f) * TURN * direction
        val first = v.pick(wedges.size, salt = 1)
        val pole = centre + Offset(cos(spin), sin(spin)) * (RADIUS * 0.4f)
        drawCircle(MoPropWhite, RADIUS, centre)
        clipPath(Path().apply { addOval(Rect(centre, RADIUS)) }) {
            for (index in 0..2) {
                val from = spin + index * TURN / 3f + TURN / 12f
                val to = from + TURN / 6f
                val wedge = Path().apply {
                    moveTo(pole.x, pole.y)
                    lineTo(pole.x + cos(from) * RADIUS * 3f, pole.y + sin(from) * RADIUS * 3f)
                    lineTo(pole.x + cos(to) * RADIUS * 3f, pole.y + sin(to) * RADIUS * 3f)
                    close()
                }
                drawPath(wedge, wedges[(first + index) % wedges.size])
            }
            // Its ledge: a darker sliver along the bottom, wherever the ball has spun to.
            drawCircle(MoPropShadow, RADIUS, centre + Offset(0f, RADIUS * 1.75f))
        }
        drawCircle(MoPropWhite, RADIUS * 0.2f, pole)
    }
}

/** A finished day: a few balloons too many lift him just off the floor, and he looks down at the gap, delighted. */
private object BalloonLift : MoAct("happy_balloon_lift", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private val holding = MoArm(Offset(30f, -62f), bend = 0.25f)
    private val balloons = listOf(
        MoPropRed to MoPropRedDark,
        MoPropBlue to MoPropBlueDark,
        MoPropYellow to MoPropYellowDark,
        MoPropPurple to MoPropPurpleDark,
        MoPropGreen to MoPropGreenDark,
    )

    private fun side(v: MoVariant) = if (v.chance(50, salt = 5)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val glance = cycle(t, 6.8f)
        // He looks down at the gap for the first half, then up at the viewer, and back down.
        val down = 1f - span(glance, 0.50f, 0.58f) + span(glance, 0.95f, 1f)
        val eye = MoEye(gaze = Offset(0f, 0.95f * down))
        val balance = MoArm(Offset(46f, 6f + 6f * wave(t, 1.6f)), bend = 0.2f)
        val paddle = wave(t, 0.7f)
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (down > 0.5f) MoBrows(MoBrowKind.Raised, lift = 4f) else MoBrows(),
            mouth = if (down > 0.5f) MoMouth.Oh(size = 8f) else MoMouth.Grin(open = 0.8f),
            leftArm = if (side < 0f) holding else balance,
            rightArm = if (side > 0f) holding else balance,
            legs = MoLegs(MoLeg(lift = 5f + 5f * paddle), MoLeg(lift = 5f - 5f * paddle)),
            offset = Offset(0f, -v.between(16f, 28f, salt = 6) - 9f * pulse(t, 3.4f)),
            // He hangs from the hand that holds them, stretched a little by the pull.
            tilt = -3f * side + 1.5f * wave(t, 3.4f),
            squash = idle.squash + 0.02f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = side(v)
        val hand = if (side > 0f) rig.rightHand else rig.leftHand
        val count = 1 + v.pick(3, salt = 1)
        // A bunch over his hand, beside his tip and never over it, every second one a little higher.
        // A bigger bunch sits further in, so its outer balloon stays in the frame.
        val bunch = List(count) { index ->
            val spread = index - (count - 1) / 2f
            val nod = Offset(4f * wave(t + index, 2.1f), 3f * wave(t + index * 0.7f, 1.7f))
            hand + Offset(side * (spread * 24f - 6f * count), -134f - 18f * (index % 2)) + nod
        }
        bunch.forEach { at ->
            val knot = at + Offset(0f, 38f)
            curve(knot, Offset((knot.x + hand.x) / 2f + side * 6f, (knot.y + hand.y) / 2f + 8f), hand, MoPropGrey, 2.5f)
        }
        grippingMitten(rig, side)
        bunch.forEachIndexed { index, at ->
            val (colour, ledge) = v.of(balloons, salt = 2 + index)
            shape(ledge, at.x to at.y + 30f, at.x - 5.5f to at.y + 39f, at.x + 5.5f to at.y + 39f)
            oval(at + Offset(0f, 2.5f), 26f, 31f, ledge)
            oval(at - Offset(0f, 2.5f), 26f, 31f, colour)
            curve(at + Offset(-17f, -7f), at + Offset(-15f, -21f), at + Offset(-5f, -27f), MoPropWhite.copy(alpha = 0.7f), 4.5f)
        }
    }

    override val sparkles get() = false
}

internal val happyActs1: List<MoAct> = listOf(
    RoastedCorn,
    OwnFireworks,
    HulaHoop,
    SkippingRope,
    BeachBallOnTip,
    BalloonLift,
)
