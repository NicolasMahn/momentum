package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoFlameOuter
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropLeaf
import de.nicolasmahn.momentum.core.ui.theme.MoPropMoon
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodies
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
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.flameTongue
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.mitten
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.polyline
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/*
 * Proud, part one: a medal, a wreath, a podium, the streak coin, a cup and his own fireworks. The
 * longer acts run on one clock each and blend their phases with [span], so every border is eased.
 */

private const val TAU = 2f * PI.toFloat()

/** 0 before [from], 1 after [to], eased in between. Every call site passes [to] > [from]. */
private fun span(x: Float, from: Float, to: Float): Float = ease((x - from) / (to - from))

/** 0 to 1 and back to 0 across [from]..[to], as a smooth bump. */
private fun bump(x: Float, from: Float, to: Float): Float =
    if (x <= from || x >= to) 0f else sin((x - from) / (to - from) * PI.toFloat())

private fun mix(a: Float, b: Float, w: Float): Float = a + (b - a) * w

private fun mix(a: Offset, b: Offset, w: Float): Offset = a + (b - a) * w

private fun mix(a: MoArm, b: MoArm, w: Float): MoArm = MoArm(mix(a.reach, b.reach, w), mix(a.bend, b.bend, w))

/** The reach that lands the [side] hand exactly on [point] of his design space, on any body. */
private fun reachTo(body: MoBody, side: Float, point: Offset): Offset =
    Offset(side * (point.x - CENTRE) - body.shoulderHalf, point.y - body.shoulderY)

/**
 * The reach that puts the [side] hand at [fromMouth] from the mouth, x counted outward on that side,
 * so a hand meant for the face or the belly lands in the same place on every body.
 */
private fun reachFromMouth(body: MoBody, side: Float, fromMouth: Offset): Offset =
    reachTo(body, side, body.mouth + Offset(side * fromMouth.x, fromMouth.y))

/** The reach that puts the [side] hand on a point of the world while he leans by [tilt] degrees. */
private fun reachToWorld(body: MoBody, side: Float, world: Offset, tilt: Float): Offset {
    val angle = -tilt * PI.toFloat() / 180f
    val x = world.x - CENTRE
    val y = world.y - FEET
    return reachTo(body, side, Offset(CENTRE + x * cos(angle) - y * sin(angle), FEET + x * sin(angle) + y * cos(angle)))
}

/** He bites his gold medal like a champion in a press photo, and the hard metal makes him ring. */
private object BitingTheMedal : MoAct("proud_biting_the_medal", MoOccasion.Proud) {
    private const val LOOP = 5.2f
    private const val RADIUS = 29f
    private val admireGrip = Offset(-2f, -30f)
    private val biteGrip = Offset(-24f, -12f)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) -1f else 1f

    private fun atMouth(c: Float) = span(c, 0.35f, 0.45f) * (1f - span(c, 0.6f, 0.66f))

    /** Dazed after the bite, the medal hangs from his mitten below the eye, clear of the spirals. */
    private fun dazed(c: Float) = span(c, 0.6f, 0.66f) * (1f - span(c, 0.78f, 0.92f))

    private fun ringing(c: Float) = span(c, 0.6f, 0.63f) * (1f - span(c, 0.72f, 0.78f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = cycle(t, LOOP)
        val side = side(v)
        val shape = MoBodies.of(body)
        val bite = atMouth(c)
        val daze = dazed(c)
        val ring = ringing(c)
        // Far enough out to be clear of his flame, near enough that the medal stays inside the frame.
        val admire = MoArm(Offset(26f, -16f), bend = 0.4f)
        // Swing below his eye on the way in, then bring the rim up to his teeth.
        val underEye = 22f * 4f * bite * (1f - bite) * (1f - daze)
        val lifting = MoArm(
            admire.reach + Offset(0f, underEye) +
                (reachFromMouth(shape, side, Offset(56f, 36f)) - admire.reach) * bite +
                (reachFromMouth(shape, side, Offset(74f, 56f)) - admire.reach) * daze,
            bend = 0.4f + 0.1f * bite,
        )
        val bites = if (v.chance(40, salt = 4)) 2f else 1f
        val chomp = if (c in 0.45f..0.6f) pulse((c - 0.45f) / 0.15f, 1f / bites) else 0f
        val shakeOff = span(c, 0.76f, 0.8f) * (1f - span(c, 0.84f, 0.92f))

        val atMedal = MoEye(gaze = Offset(side * 0.8f, 0.4f))
        val squeezed = MoEye(shape = MoEyeShape.Happy)
        val (medalEye, otherEye) = when {
            c < 0.35f || c >= 0.92f -> MoEye(shape = MoEyeShape.Star) to MoEye(shape = MoEyeShape.Star)
            c < 0.45f -> atMedal to atMedal
            c < 0.6f -> squeezed to atMedal
            c < 0.76f -> MoEye(shape = MoEyeShape.Spiral) to MoEye(shape = MoEyeShape.Spiral)
            else -> MoEye(gaze = Offset(side * 0.6f, 0.3f)).let { it to it }
        }
        return moIdle(t, energy = 1.2f).copy(
            leftEye = if (side < 0f) medalEye else otherEye,
            rightEye = if (side < 0f) otherEye else medalEye,
            mouth = when {
                c < 0.35f || c >= 0.92f -> MoMouth.Grin(open = 0.7f)
                c < 0.45f -> MoMouth.Grin(open = 0.7f + 0.3f * span(c, 0.35f, 0.43f), tongue = false)
                c < 0.6f -> MoMouth.Gritted
                c < 0.76f -> MoMouth.Wavy
                c < 0.84f -> MoMouth.Smile()
                else -> MoMouth.Grin(open = 0.7f * span(c, 0.84f, 0.92f))
            },
            leftArm = if (side < 0f) lifting else MoArm.Hip,
            rightArm = if (side < 0f) MoArm.Hip else lifting,
            armsInFront = true,
            tilt = side * 2f * (1f - bite - daze).coerceIn(0f, 1f) + wave(t, 0.18f) * 3f * shakeOff,
            squash = wave(t, 3.2f) * 0.014f - 0.02f * chomp,
            offset = Offset(0f, 2f * chomp),
            shake = wave(t, 0.05f) * 2.2f * ring,
            flare = 6f * pulse(t, 0.1f) * ring,
            blinks = ring < 0.05f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = cycle(t, LOOP)
        val side = side(v)
        val bite = atMouth(c)
        val ring = ringing(c)
        val hand = if (side < 0f) rig.leftHand else rig.rightHand
        val grip = mix(admireGrip, biteGrip, bite)
        val medal = hand + Offset(side * grip.x, grip.y)

        val (ribbon, stripe) = v.of(
            listOf(
                MoPropRedDark to MoPropRed,
                MoPropBlueDark to MoPropBlue,
                MoPropPurpleDark to MoPropPurple,
                MoPropTealDark to MoPropTeal,
            ),
            salt = 1,
        )
        for (shoulder in listOf(-1f, 1f)) {
            val from = Offset(CENTRE + shoulder * (rig.body.shoulderHalf - 4f), rig.body.shoulderY - 8f)
            // The wider a strand spans, the deeper it sags, which is what keeps it under his mouth.
            val sag = (10f + 0.7f * abs(medal.x - from.x)).coerceAtMost(80f + 2f * (rig.mouth.y - from.y))
            val control = Offset((from.x + medal.x) / 2f, max(from.y, medal.y) + sag)
            curve(from, control, medal, ribbon, 14f)
            curve(from, control, medal, stripe, 5f)
        }

        chunk(medal, RADIUS * 2f, RADIUS * 2f, MoPropGold, MoPropGoldDark, corner = RADIUS, drop = 7f)
        val face = medal - Offset(0f, 3f)
        drawCircle(MoPropYellow, 20f, face)
        when (v.pick(3, salt = 3)) {
            0 -> sparkle(face, 17f, 1f, MoPropGoldDark)
            1 -> flameTongue(face + Offset(0f, 13f), 10f, MoPropOrange)
            else -> drawCircle(MoPropGoldDark, 13f, face, style = Stroke(width = 6f))
        }
        mitten(rig, hand, side)

        val admiring = (1f - bite - dazed(c)).coerceIn(0f, 1f)
        // The glint pops off the rim on the side facing him, not out of the emblem, so the medal
        // keeps reading as metal and the star stays inside the frame on either side.
        sparkle(medal + Offset(side * -26f, -24f), 22f * pulse(t + 0.65f, 1.3f) * admiring, 1f, MoPropWhite)

        if (ring > 0f) {
            val ink = MoPropGreyDark.copy(alpha = ring)
            for (out in listOf(-1f, 1f)) {
                for ((step, size) in listOf(9f, 14f).withIndex()) {
                    val at = Offset(CENTRE + out * (128f + step * 11f), rig.leftEye.y + 8f)
                    curve(at + Offset(0f, -size), at + Offset(out * size * 0.8f, 0f), at + Offset(0f, size), ink, 4f)
                }
            }
        }
    }
}

/** He keeps a laurel wreath spinning round his middle like a hula hoop, and rescues it when it sinks. */
private object LaurelHulaHoop : MoAct("proud_laurel_hula_hoop", MoOccasion.Proud) {
    private const val SWING = 0.9f
    /** Wide enough to pass either side of him, narrow enough that a sunk leaf stays in the frame. */
    private const val RX = 120f
    private const val RY = 30f

    /** The wreath slips down every few swings; a whole number of swings, so the hips never jump. */
    private fun sink(t: Float, v: MoVariant): Float {
        val period = SWING * (5 + v.pick(5, salt = 5))
        val at = cycle(t, period, offset = 0.5f) * period
        return bump(at, 0f, 1.4f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = sink(t, v)
        val hips = wave(t, SWING)
        val shape = MoBodies.of(body)
        // The mittens stay at one height on both bodies, above the wreath.
        val armY = 318f - shape.shoulderY
        val alarmed = s > 0.3f
        val down = MoEye(gaze = Offset(0f, 0.9f), iris = 0.5f)
        val joy = MoEye(shape = MoEyeShape.Happy)
        return moIdle(t, energy = 1.3f).copy(
            leftEye = if (alarmed) down else joy,
            rightEye = if (alarmed) down else joy,
            brows = if (alarmed) MoBrows(MoBrowKind.Raised, lift = 4f) else MoBrows(),
            mouth = if (alarmed) MoMouth.Oh(8f) else MoMouth.Grin(open = 0.6f),
            // Out for balance, but no further: the arms swing with his hips, and a mitten out at the
            // kit's full reach would leave the frame on the big wiggle that saves the wreath.
            leftArm = MoArm(Offset(40f, armY + 5f * hips), bend = 0.1f),
            rightArm = MoArm(Offset(40f, armY - 5f * hips), bend = 0.1f),
            legs = MoLegs(MoLeg(lift = 4f * max(hips, 0f)), MoLeg(lift = 4f * max(-hips, 0f))),
            offset = Offset(9f * hips * (1f + 0.5f * s), 0f),
            tilt = -3f * hips,
            squash = wave(t, 3.2f) * 0.014f + 0.015f * pulse(t, SWING / 2f),
        )
    }

    private fun DrawScope.wreath(t: Float, v: MoVariant, front: Boolean) {
        val s = sink(t, v)
        // It trails his hips by a beat and rocks as it goes, the low side running round with the swing.
        val centre = Offset(CENTRE + 14f * wave(t - 0.2f, SWING) * (1f + 0.25f * s), 360f + 20f * s)
        val rock = TAU * cycle(t, SWING)
        fun onRing(a: Float) = centre + Offset(cos(a) * RX, sin(a) * RY + 6f * cos(a - rock))

        val gold = v.chance(50, salt = 1)
        val start = if (front) 0f else PI.toFloat()
        polyline(if (gold) MoPropGoldDark else MoPropGreenDark, 6f, (0..24).map { onRing(start + PI.toFloat() * it / 24f) })

        val dir = if (v.chance(50, salt = 2)) 1f else -1f
        val count = 16 + 2 * v.pick(3, salt = 3)
        for (index in 0 until count) {
            val a = TAU * (index.toFloat() / count + dir * cycle(t, 2f * SWING))
            if ((sin(a) >= 0f) != front) continue
            val at = onRing(a)
            val tx = -sin(a) * RX * dir
            val ty = cos(a) * RY * dir
            val length = max(hypot(tx, ty), 1f)
            val along = Offset(tx / length, ty / length)
            val across = Offset(-along.y, along.x)
            // The near side of the ring is a little bigger than the far one.
            val near = 0.85f + 0.15f * sin(a)
            val colour = when {
                gold -> if (index % 2 == 0) MoPropGold else MoPropYellow
                else -> if (index % 2 == 0) MoPropLeaf else MoPropGreen
            }
            limb(at, at + (along * 13f + across * 8f) * near, colour, 10f * near)
            limb(at, at + (along * 13f - across * 8f) * near, colour, 10f * near)
            if (index == 0) {
                // The bow says which way it turns.
                val bow = v.of(listOf(MoPropRed, MoPropPink, MoPropBlue), salt = 4)
                oval(at + Offset(-12f, -2f) * near, 11f * near, 7.5f * near, bow)
                oval(at + Offset(12f, -2f) * near, 11f * near, 7.5f * near, bow)
                limb(at, at + Offset(-7f, 14f) * near, bow, 5.5f * near)
                limb(at, at + Offset(7f, 14f) * near, bow, 5.5f * near)
                drawCircle(bow, 6f * near, at)
            }
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) = wreath(t, v, front = false)

    override fun DrawScope.foreground(t: Float, v: MoVariant) = wreath(t, v, front = true)

    override val ownsTheFloor get() = true
}

/** At attention on the top step of a podium, dignified, except that the grin keeps getting out. */
private object TryingNotToGrin : MoAct("proud_trying_not_to_grin", MoOccasion.Proud, intensity = MoIntensity.Mild) {
    private const val LEAK = 1.3f

    /**
     * How tall the top step is, and so how high he stands. A Blazing Mo is drawn at full size, and
     * this much plus the flare of the leak is all the room his tip has above him.
     */
    private const val STEP = 50f

    /** One block of the podium, with a lit top face so it reads as a box and not as a flat tile. */
    private fun DrawScope.step(centreX: Float, width: Float, height: Float, colour: Color, ledge: Color) {
        chunk(Offset(centreX, FEET - height / 2f), width, height, colour, ledge, corner = 10f)
        // Flush with the top of the face, and never more than a third of a step, or a low step
        // becomes two stripes instead of a box with a lit top.
        val lit = (height * 0.32f).coerceAtMost(13f)
        block(Offset(centreX, FEET - height - 2.5f + lit / 2f), width - 10f, lit, MoPropWhite.copy(alpha = 0.18f), corner = 6f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val period = v.between(4.5f, 6.5f, salt = 4)
        // The clock starts half way into a leak, so the still frame is the proud one.
        val at = cycle(t, period, offset = LEAK / 2f / period) * period
        val k = bump(at, 0f, LEAK)
        // The brows run a little behind the mouth: he flattens his smile first and his brows last.
        val browK = bump(cycle(t - 0.25f, period, offset = LEAK / 2f / period) * period, 0f, LEAK)
        val building = span(at, period - 1.2f, period)
        val glance = if (v.chance(50, salt = 2)) 1f else -1f
        val gaze = when {
            at < 0.1f || at >= 1.05f -> Offset(0f, -0.5f)
            at < 0.4f -> Offset(glance * 0.7f, 0f)
            else -> Offset(0f, 0.1f)
        }
        val hands = MoArm(Offset(-2f, 38f - 8f * k), bend = 0f)
        return moIdle(t, energy = 0.8f).copy(
            leftEye = MoEye(gaze = gaze),
            rightEye = MoEye(gaze = gaze),
            brows = if (browK > 0.2f) MoBrows(MoBrowKind.Raised, lift = 6f * browK) else MoBrows(MoBrowKind.Flat),
            mouth = when {
                at < 0.95f -> if (v.chance(40, salt = 3)) MoMouth.Beam else MoMouth.Grin(open = 0.9f)
                at < 1.9f -> MoMouth.Flat(9f)
                building > 0.5f -> MoMouth.Smile(12f, 3f + 5f * building)
                else -> MoMouth.Flat(12f)
            },
            leftArm = hands,
            rightArm = hands,
            squash = wave(t, 3.2f) * 0.01f + 0.02f * (1f - k),
            flare = 13f * k,
            offset = Offset(0f, -STEP - 3f * k),
            // Holding it in makes him tremble, more and more until it gets out.
            shake = wave(t, 0.09f) * (0.4f + 1.1f * building) * (1f - k),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val (colour, ledge) = v.of(
            listOf(
                MoPropBlue to MoPropBlueDark,
                MoPropPurple to MoPropPurpleDark,
                MoPropTeal to MoPropTealDark,
                MoPropSilver to MoPropSteel,
            ),
            salt = 1,
        )
        floorShadow(CENTRE, 156f, MoPropShadow)
        // The lower steps run under the top one rather than meeting it, so their rounded corners do
        // not leave a notch and the three read as one stepped block instead of three tiles.
        step(CENTRE - 100f, 86f, 30f, colour, ledge)
        step(CENTRE + 100f, 86f, 18f, colour, ledge)
        step(CENTRE, 150f, STEP, colour, ledge)
    }

    override val ownsTheFloor get() = true
}

/** He polishes the streak coin with his tongue out, until a glint pops off it and dazzles him. */
private object PolishingTheStreakCoin : MoAct("proud_polishing_the_streak_coin", MoOccasion.Proud, intensity = MoIntensity.Mild) {
    private const val LOOP = 6f
    private const val RADIUS = 24f

    private fun clock(t: Float) = cycle(t, LOOP, offset = 0.12f)

    private fun holdSide(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun rubbing(c: Float) = span(c, 0.06f, 0.11f) * (1f - span(c, 0.55f, 0.58f))

    private fun glint(c: Float) = span(c, 0.55f, 0.59f) * (1f - span(c, 0.66f, 0.72f))

    /** The huff runs over the seam of the clock, from 0.9 round to 0.06. */
    private fun huff(c: Float): Float = ((c - 0.9f).mod(1f) / 0.16f).let { if (it < 1f) it else -1f }

    /**
     * The coin sits in front of his belly, where both mittens reach it with a stubby arm and the two
     * of them read as one gesture. The 46 clears his tongue, which hangs 20 below the mouth.
     */
    private fun coin(mouth: Offset) = mouth + Offset(0f, 46f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = clock(t)
        val hold = holdSide(v)
        val shape = MoBodies.of(body)
        val g = glint(c)
        val h = huff(c)
        val speed = v.between(11f, 16f, salt = 3)
        val k = coin(shape.mouth)
        // High on the coin's face, so the mitten reads as a hand working on it and not as a hole in it.
        val onCoin = MoArm(
            reachTo(shape, -hold, k + Offset(-hold * 13f, -7f)) + Offset(8f * cos(t * speed), 6f * sin(t * speed)),
            bend = 0.5f,
        )
        val hover = MoArm(reachTo(shape, -hold, k + Offset(-hold * 48f, -22f)), bend = 0.4f)
        val away = mix(MoArm.Out, hover, span(c, 0.69f, 0.76f))
        val polishing = mix(if (c < 0.3f) hover else away, onCoin, rubbing(c))
        // The holding mitten cups the lower edge, so the two arms mirror each other round the coin.
        val holding = MoArm(reachTo(shape, hold, k + Offset(hold * 20f, 14f)), bend = 0.6f)

        val onTheCoin = Offset(hold * 0.2f, 0.9f)
        val eye = when {
            g > 0.3f -> MoEye(shape = MoEyeShape.Star)
            else -> MoEye(gaze = onTheCoin)
        }
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                g > 0.3f -> MoBrows()
                c in 0.72f..0.9f -> MoBrows(MoBrowKind.Raised, skew = 6f)
                else -> MoBrows()
            },
            mouth = when {
                g > 0.3f -> MoMouth.Grin(open = 0.9f)
                h >= 0f -> MoMouth.Oh(8f)
                c in 0.11f..0.56f -> MoMouth.TongueOut
                else -> MoMouth.Flat(8f)
            },
            leftArm = if (hold < 0f) holding else polishing,
            rightArm = if (hold < 0f) polishing else holding,
            armsInFront = true,
            tilt = hold * 2f * rubbing(c),
            flare = 16f * g,
            offset = Offset(0f, -4f * g),
            squash = wave(t, 3.2f) * 0.014f - 0.03f * bump(h, 0f, 1f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = clock(t)
        val hold = holdSide(v)
        val g = glint(c)
        val h = huff(c)
        val k = coin(rig.mouth)

        // He turns it edge-on and back to check it, so it narrows instead of spinning.
        val turn = bump(c, 0.74f, 0.88f)
        val lean = if (v.chance(50, salt = 4)) 1f else -1f
        withTransform({
            rotate(lean * 14f * turn, k)
            scale(1f - 0.55f * turn, 1f, k)
        }) {
            // The rim runs all the way round, because gold on his belly is gold on yellow.
            drawCircle(MoPropGoldDark, RADIUS + 3.5f, k + Offset(0f, 2.5f))
            drawCircle(MoPropGold, RADIUS, k)
            flameTongue(k + Offset(0f, 16f), 12f, MoFlameOuter, MoPropYellow)
        }

        // The kit drew both mittens under the coin, and two mittens on it is the whole gesture: one
        // cups its lower edge, the other rides on top, so the polishing one is drawn last.
        mitten(rig, if (hold < 0f) rig.leftHand else rig.rightHand, hold)
        mitten(rig, if (hold < 0f) rig.rightHand else rig.leftHand, -hold)

        if (h >= 0f) {
            val from = rig.mouth + Offset(hold * 8f, 12f)
            puff(mix(from, k, ease(h)), 6f + 5f * h, MoPropWhite.copy(alpha = 0.7f * (1f - h)))
        }
        val glintColour = v.of(listOf(MoPropWhite, MoPropMoon, MoPropSky), salt = 2)
        sparkle(k + Offset(hold * 18f, -18f), 46f * g, 1f, glintColour)
    }
}

/** Plainly pleased, he leans on his gold cup, until it slides away and he has to fetch it back. */
private object LeaningOnTheCup : MoAct("proud_leaning_on_the_cup", MoOccasion.Proud) {
    private const val LOOP = 7f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    /** How far through everything after the lean he is, 0 while he still leans. */
    private fun after(t: Float, v: MoVariant): Float {
        val lean = v.between(0.35f, 0.5f, salt = 4)
        return ((cycle(t, LOOP) - lean) / (1f - lean)).coerceIn(0f, 1f)
    }

    private fun slide(u: Float) = 6f * span(u, 0f, 0.22f) * (1f - span(u, 0.62f, 0.72f))

    /** Leaning on it, tipping over it, a small overshoot the other way on the catch, and back. */
    private fun lean(u: Float) =
        13f + 6f * span(u, 0.02f, 0.3f) - 26f * span(u, 0.38f, 0.5f) + 4f * span(u, 0.5f, 0.6f) + 16f * span(u, 0.88f, 1f)

    private fun bowl(v: MoVariant): Pair<Float, Float> = when (v.pick(3, salt = 2)) {
        0 -> 56f to 72f
        1 -> 66f to 58f
        else -> 60f to 66f
    }

    /**
     * He stands well off centre, because leaning swings his flame a long way over the cup: the shift
     * is what leaves the cup and the arm on it outside his silhouette.
     */
    private const val ASIDE = 29f

    private fun cupX(u: Float, side: Float) = CENTRE + side * (108f + slide(u))

    /** The rim is what he leans on: high enough that his arm is out of his silhouette and readable. */
    private fun rim(v: MoVariant) = FEET - 44f - bowl(v).second

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val u = after(t, v)
        val side = side(v)
        val shape = MoBodies.of(body)
        val tilt = lean(u)
        val tipping = span(u, 0.05f, 0.3f) * (1f - span(u, 0.4f, 0.52f))

        val pats = if (u in 0.72f..0.88f) pulse(u - 0.72f, 0.08f) else 0f
        // The cup is drawn where he is not, so the reach is taken back through his own shift and lean.
        val onCup = Offset(cupX(u, side) + side * ASIDE + side * 10f, rim(v) - 8f - 8f * pats)
        val onTheCup = MoArm(reachToWorld(shape, side, onCup, side * tilt), bend = -0.3f)
        val slipped = MoArm(Offset(46f, 4f + 12f * wave(t, 0.3f)), bend = 0.1f)
        val leaning = mix(slipped, onTheCup, (1f - span(u, 0.02f, 0.1f)) + span(u, 0.52f, 0.62f))

        val flail = span(u, 0.03f, 0.1f) * (1f - span(u, 0.4f, 0.5f))
        // The windmilling hand circles high, where his flame is narrow enough for it to be seen going
        // round and where it stays inside the frame: he already stands [ASIDE] towards that edge.
        val windmill = MoArm(Offset(20f + 16f * cos(t * 18f), -32f + 26f * sin(t * 18f)), bend = 0.2f)
        val other = mix(MoArm.Hip, windmill, flail).copy(inFront = true)

        val eye = when {
            u <= 0f || u >= 0.88f -> MoEye(gaze = Offset(-side * 0.2f, 0.1f))
            u < 0.4f -> MoEye(iris = 0.5f, scale = 1.05f, gaze = Offset(side * 0.5f, 0.4f))
            u < 0.62f -> MoEye(gaze = Offset(0f, 0.1f))
            else -> MoEye(gaze = Offset(side * 0.8f, 0.5f))
        }
        val planted = MoLeg(lift = 0.55f * tilt.coerceAtLeast(0f))
        val farLeg = MoLeg(lift = 14f * tipping, swing = -8f + 16f * tipping)
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                u <= 0f || u >= 0.62f -> MoBrows()
                u < 0.4f -> MoBrows(MoBrowKind.Raised, lift = 5f)
                else -> MoBrows(MoBrowKind.Raised, lift = 2f)
            },
            mouth = when {
                u <= 0f || u >= 0.88f -> MoMouth.Smile(19f, 16f)
                u < 0.4f -> MoMouth.Oh(10f)
                u < 0.62f -> MoMouth.Smile(12f, 8f)
                else -> MoMouth.Smile(15f, 11f)
            },
            leftArm = if (side < 0f) leaning else other,
            rightArm = if (side < 0f) other else leaning,
            legs = if (side < 0f) MoLegs(planted, farLeg) else MoLegs(farLeg, planted),
            tilt = side * tilt,
            offset = Offset(-side * ASIDE, 0f),
            blinks = u <= 0f || u >= 0.4f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val x = cupX(after(t, v), side(v))
        val (width, height) = bowl(v)
        val rim = rim(v)
        floorShadow(x, 42f, MoPropShadow)
        if (v.pick(3, salt = 2) != 2) {
            // Set inside the bowl's edge: they still read as handles, and the outer one stays in frame.
            for (out in listOf(-1f, 1f)) {
                drawCircle(MoPropGold, 10f, Offset(x + out * (width / 2f - 4f), rim + 28f), style = Stroke(width = 5f))
            }
        }
        block(Offset(x, FEET - 32f), 18f, 34f, MoPropGoldDark, corner = 4f)
        chunk(Offset(x, FEET - 10f), 62f, 20f, MoPropGold, MoPropGoldDark, corner = 6f)
        chunk(Offset(x, rim + height / 2f + 3f), width, height, MoPropGold, MoPropGoldDark, corner = 26f)
        // A straight top and a bright lip make it a cup and not a ball.
        block(Offset(x, rim + 14f), width, 28f, MoPropGold, corner = 4f)
        block(Offset(x, rim + 4f), width + 8f, 10f, MoPropYellow, corner = 5f)
        curve(Offset(x - width / 2f + 12f, rim + 48f), Offset(x - width / 2f + 9f, rim + 28f), Offset(x - width / 2f + 13f, rim + 22f), MoPropMoon, 6f)

        val bow = v.of(listOf(MoPropRed, MoPropBlue, MoPropPink), salt = 3)
        val knot = Offset(x, FEET - 32f)
        oval(knot + Offset(-11f, 0f), 10f, 6.5f, bow)
        oval(knot + Offset(11f, 0f), 10f, 6.5f, bow)
        drawCircle(bow, 5.5f, knot)
    }

    override val ownsTheFloor get() = true
}

/** Glowing gold, he pops sparks off his own tip, watches them burst in the corners, and claps. */
private object HisOwnFireworks : MoAct("proud_his_own_fireworks", MoOccasion.Proud, intensity = MoIntensity.Strong) {
    private const val LAUNCH = 1.6f
    private const val CLIMB = 0.3f
    private val corners = listOf(Offset(210f, 92f), Offset(18f, 100f))

    /** Where a corner is between one of its launches and the next: it climbs until [CLIMB], then bursts. */
    private fun timeline(t: Float, v: MoVariant, corner: Int): Float {
        val leftFirst = v.chance(50, salt = 5)
        val climbingAtRest = (corner == 1) == leftFirst
        return cycle(t, 2f * LAUNCH, offset = if (climbingAtRest) 0.15f else 0.65f)
    }

    private fun latestSide(t: Float, v: MoVariant) = if (timeline(t, v, 0) < 0.5f) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        // A launch leaves his tip at u = 0.2, which is where a timeline starts.
        val u = cycle(t, LAUNCH, offset = 0.5f)
        val shape = MoBodies.of(body)
        val crouch = span(u, 0.05f, 0.12f) * (1f - span(u, 0.19f, 0.22f))
        val pop = span(u, 0.19f, 0.23f) * (1f - span(u, 0.26f, 0.42f))
        val clap = if (u < 0.5f) 1f - span(u, 0f, 0.06f) else span(u, 0.72f, 0.78f)
        val beat = pulse(t, 0.22f)
        // The mittens meet in front of his belly, under the beam of his smile.
        fun clapping(side: Float) = MoArm(reachFromMouth(shape, side, Offset(12f + 20f * (1f - beat), 37f)), bend = 0.4f)
        val up = MoEye(scale = 1.05f, gaze = Offset(latestSide(t, v) * 0.8f * span(u, 0.2f, 0.45f), -0.9f))
        val eye = if (u in 0.06f..0.2f) MoEye(shape = MoEyeShape.Happy) else up
        return moIdle(t, energy = 1.6f).copy(
            palette = MoPalettes.Golden,
            leftEye = eye,
            rightEye = eye,
            brows = if (u in 0.2f..0.75f) MoBrows(MoBrowKind.Raised, lift = 4f) else MoBrows(),
            mouth = when {
                u in 0.06f..0.2f -> MoMouth.Flat(10f)
                u in 0.2f..0.75f -> MoMouth.Oh(10f)
                else -> MoMouth.Beam
            },
            leftArm = mix(MoArm.Down, clapping(-1f), clap),
            rightArm = mix(MoArm.Down, clapping(1f), clap),
            armsInFront = clap > 0.05f,
            squash = wave(t, 3.2f) * 0.014f - 0.06f * crouch + 0.05f * pop,
            flare = 22f * span(u, 0.19f, 0.22f) * (1f - span(u, 0.24f, 0.5f)),
            offset = Offset(0f, -6f * pop - 3f * beat * clap),
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val pairs = listOf(MoPropPink to MoPropYellow, MoPropSky to MoPropWhite, MoPropGreen to MoPropGold, MoPropPurple to MoPropPink)
        val first = v.pick(pairs.size, salt = 1)
        val dots = if (v.chance(50, salt = 4)) 8 else 10
        val style = v.pick(3, salt = 3)
        val double = v.chance(30, salt = 6)
        for ((corner, centre) in corners.withIndex()) {
            val q = timeline(t, v, corner)
            if (q < CLIMB) continue
            val p = (q - CLIMB) / (1f - CLIMB)
            // The other corner always gets another pair.
            val (outer, inner) = pairs[(first + corner * (1 + v.pick(pairs.size - 1, salt = 2))) % pairs.size]
            burst(centre, p, 1f, outer, dots, style)
            burst(centre, (p - 0.1f) / 0.9f, 0.55f, inner, dots / 2, style)
            if (double) burst(centre + Offset(0f, 64f), (p - 0.22f) / 0.78f, 0.45f, inner, dots / 2, 0)
        }
    }

    private fun DrawScope.burst(centre: Offset, p: Float, size: Float, colour: Color, points: Int, style: Int) {
        if (p <= 0f || p >= 1f) return
        // Fast out and then hanging in the air, as a firework does.
        val out = 1f - (1f - p) * (1f - p) * (1f - p)
        // The widest ring still clears the frame with its dots on, which is what caps the 44.
        val radius = (12f + 44f * out) * size
        val fade = colour.copy(alpha = 1f - p * p * p * p)
        val middle = centre + Offset(0f, 12f * p)
        // The core of the flash, which is what makes a ring of dots read as a burst and not confetti.
        if (p < 0.35f) drawCircle(fade, 13f * size * (1f - p / 0.35f), middle)
        for (index in 0 until points) {
            val a = TAU * index / points + size
            val ray = Offset(cos(a), sin(a))
            val at = middle + ray * radius
            when (style) {
                0 -> drawCircle(fade, 8f * (1f - 0.55f * p), at)
                1 -> sparkle(at, 14f * (1f - 0.45f * p), 1f, fade)
                else -> limb(middle + ray * radius * 0.5f, at, fade, 7f * (1f - 0.45f * p))
            }
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val pose = pose(t, v, rig.body.kind)
        val u = cycle(t, LAUNCH, offset = 0.5f)
        sparkle(rig.tip + Offset(0f, -4f), 14f * bump(u, 0.19f, 0.32f), 1f, MoPropWhite)

        for ((corner, centre) in corners.withIndex()) {
            val q = timeline(t, v, corner)
            if (q >= CLIMB) continue
            // The burst hangs in the world and he does not, so its corner is taken back into his space.
            val still = centre - pose.offset
            val target = Offset(
                CENTRE + (still.x - CENTRE) / (1f - pose.squash / 2f),
                FEET + (still.y - FEET) / (1f + pose.squash),
            )
            val from = rig.tip + Offset(0f, -8f)
            fun onPath(e: Float) = mix(from, target, ease(e)) + Offset(0f, -26f * sin(e * PI.toFloat()))
            val e = q / CLIMB
            val size = 1f - span(e, 0.85f, 1f)
            for (step in 2 downTo 1) {
                drawCircle(MoPropYellow.copy(alpha = 0.7f - 0.25f * step), (6.5f - step) * size, onPath((e - 0.07f * step).coerceAtLeast(0f)))
            }
            sparkle(onPath(e), 15f * size, 1f, MoPropYellow)
        }
    }

    override val sparkles get() = false
}

internal val proudActs1: List<MoAct> = listOf(
    BitingTheMedal,
    LaurelHulaHoop,
    TryingNotToGrin,
    PolishingTheStreakCoin,
    LeaningOnTheCup,
    HisOwnFireworks,
)
