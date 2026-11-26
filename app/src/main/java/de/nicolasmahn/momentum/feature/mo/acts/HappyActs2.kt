package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.util.lerp
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoSpark
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
import de.nicolasmahn.momentum.feature.mo.MoPalettes
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.threadFromAbove
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * More of a good day: things he plays with when everything is done. The toys are his own size and
 * sit in his mittens, so each act is one picture: Mo and the one thing he is happy about.
 */

private const val TURN = 2f * PI.toFloat()

private fun mix(from: MoArm, to: MoArm, amount: Float) =
    MoArm(lerp(from.reach, to.reach, amount), lerp(from.bend, to.bend, amount))

/**
 * Where a point of the still world lies in Mo's own space, so something in his hand can reach a
 * thing that does not lean and hop with him.
 */
private fun MoPose.toHeld(world: Offset): Offset {
    val moved = world - offset - Offset(shake, 0f) - Offset(CENTRE, FEET)
    val back = -tilt * PI.toFloat() / 180f
    val x = moved.x * cos(back) - moved.y * sin(back)
    val y = moved.x * sin(back) + moved.y * cos(back)
    return Offset(CENTRE + x / (1f - squash / 2f).coerceAtLeast(0.5f), FEET + y / (1f + squash).coerceAtLeast(0.5f))
}

/** A finished day: he holds a pinwheel up beside his tip, and his own warm air keeps it turning. */
private object PinwheelWarmth : MoAct("happy_pinwheel_warmth", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private const val BREATH = 4.2f
    private const val LAZY = 1.6f
    private const val WHIRL = 5.5f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 4)) 1f else -1f

    // Squared, so the pride comes late and briefly and most of the breath is the lazy spin.
    private fun boost(t: Float) = pulse(t, BREATH).let { it * it }

    /** The wheel's angle: the exact integral of its speed, so it whirls and eases but never jumps. */
    private fun turned(t: Float, v: MoVariant): Float {
        val breath = cycle(t, BREATH) * TURN
        val whirled = BREATH / (2f * TURN) * (sin(2f * breath) / 8f - sin(breath))
        val steady = cycle(t, TURN / (LAZY + 0.375f * WHIRL)) * TURN
        return (if (v.chance(50, salt = 5)) 1f else -1f) * (steady + WHIRL * whirled)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = boost(t)
        val side = side(v)
        val idle = moIdle(t)
        val holding = MoArm(Offset(26f, -44f), bend = 0.4f)
        val look = MoEye(gaze = Offset(side * 0.8f, -0.7f), scale = 1f + 0.06f * b)
        return idle.copy(
            leftEye = look,
            rightEye = look,
            brows = MoBrows(MoBrowKind.Raised, lift = 2f + 4f * b),
            mouth = if (b > 0.5f) MoMouth.Grin(open = 0.6f) else MoMouth.Smile(),
            leftArm = if (side < 0f) holding else MoArm.Hip,
            rightArm = if (side > 0f) holding else MoArm.Hip,
            flare = 14f * b,
            squash = idle.squash + 0.025f * b,
            // He leans back from the wheel as he swells, which also keeps it inside the picture.
            tilt = -side * (1.5f + 1.5f * b),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = side(v)
        val b = boost(t)
        val hand = if (side < 0f) rig.leftHand else rig.rightHand
        val hub = Offset(hand.x + side * 3f, rig.body.top + 30f)

        // The warm air off his tip, on its way to the wheel.
        for (index in 0..1) {
            val drift = cycle(t, 1.7f, offset = index * 0.5f)
            val from = rig.tip + Offset(side * (20f + 30f * drift), 8f + index * 16f - 26f * drift)
            val air = MoPropWhite.copy(alpha = (0.25f + 0.45f * b) * sin(drift * PI.toFloat()))
            curve(from, from + Offset(side * 10f, -12f), from + Offset(side * 22f, -6f), air, 4f)
        }

        limb(hand + Offset(-side, 16f), hub, MoPropWood, 5f)
        grippingMitten(rig, side)

        val colours = listOf(MoPropRed, MoPropSky, MoPropYellow, MoPropMint, MoPropPink)
        val first = v.pick(colours.size, salt = 1)
        val second = (first + 1 + v.pick(colours.size - 1, salt = 2)) % colours.size
        val blades = if (v.chance(50, salt = 3)) 4 else 6
        val angle = turned(t, v)
        // Each blade leans the way the wheel turns, as a folded paper blade would.
        val lean = (if (v.chance(50, salt = 5)) 1f else -1f) * 0.61f * TURN / blades
        for (blade in 0 until blades) {
            val at = angle + blade * TURN / blades
            val point = hub + Offset(cos(at), sin(at)) * 38f
            val fold = hub + Offset(cos(at + lean), sin(at + lean)) * 27f
            shape(colours[if (blade % 2 == 0) first else second], hub.x to hub.y, point.x to point.y, fold.x to fold.y)
        }
        drawCircle(MoPropGold, 6f, hub)
    }

    override val sparkles get() = false
}

/** A finished day: he plays a little squeezebox with his eyes shut, and breathes with its bellows. */
private object Squeezebox : MoAct("happy_squeezebox", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    /** One breath of the bellows: a waltz or a polka. */
    private fun tempo(v: MoVariant) = if (v.chance(50, salt = 3)) 1.8f else 1.1f

    // Half a breath in, so the first frame shows the bellows pulled wide.
    private fun wide(t: Float, v: MoVariant) = pulse(t + tempo(v) / 2f, tempo(v))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = wide(t, v)
        val shape = MoBodies.of(body)
        // Low enough that the boxes stay under his mouth on either body.
        val hold = MoArm(Offset(-40f + 38f * p, shape.mouth.y + 42f - shape.shoulderY), bend = 0.35f)
        val lean = if (v.chance(50, salt = 4)) 1f else -1f
        return moIdle(t).copy(
            leftEye = MoEye(shape = MoEyeShape.Shut),
            rightEye = MoEye(shape = MoEyeShape.Shut),
            brows = MoBrows(MoBrowKind.Raised, lift = 3f + 2f * p),
            mouth = if (p > 0.5f) MoMouth.Oh(size = 6f + 3f * p) else MoMouth.Smile(),
            leftArm = hold,
            rightArm = hold,
            armsInFront = true,
            squash = 0.045f * p - 0.025f,
            flare = 12f * p,
            offset = Offset(0f, -4f * p),
            // He rocks over three breaths, so the lean and the bellows never line up the same way twice in a row.
            tilt = lean * 4f * wave(t, tempo(v) * 3f),
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (colour, ledge) = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropTeal to MoPropTealDark, MoPropPurple to MoPropPurpleDark),
            salt = 1,
        )
        // The boxes sit just inside his mittens, which stay in view on their outer ends.
        val left = rig.leftHand + Offset(15f, 0f)
        val right = rig.rightHand + Offset(-15f, 0f)
        val from = left.x + 11f
        val to = right.x - 11f
        block(Offset((from + to) / 2f, left.y), to - from, 34f, MoPropCharcoal, corner = 3f)
        for (fold in 0..6) {
            val x = lerp(from + 5f, to - 5f, fold / 6f)
            limb(Offset(x, left.y - 19f), Offset(x, left.y + 19f), MoPropCream, 5f)
        }
        chunk(left, 26f, 42f, colour, ledge, corner = 7f)
        chunk(right, 26f, 42f, colour, ledge, corner = 7f)
        block(left + Offset(0f, -2.5f), 8f, 28f, MoPropCream, corner = 3f)
        for (button in -1..1) drawCircle(MoPropCream, 3f, right + Offset(0f, -2.5f + button * 11f))

        // He is the bellows himself: every squeeze lets a little puff out of his tip.
        val rise = cycle(t, tempo(v), offset = -0.3f) / 0.5f
        if (rise < 1f) {
            puff(rig.tip + Offset(10f, -14f - rise * 26f), 5f + 4f * rise, MoPropWhite.copy(alpha = 0.6f * (1f - rise) * ease(rise * 8f)))
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val colour = v.of(listOf(MoPropYellow, MoPropPink, MoPropSky, MoPropMint), salt = 2)
        // The notes leave on the side away from his tip, and do not rock with him.
        for (index in 0..1) {
            val rise = cycle(t, tempo(v) * 1.5f, offset = 0.3f + index * 0.5f)
            val head = Offset(-8f - rise * 20f + sin(rise * 6f + index * 2f) * 6f, 304f - rise * 170f)
            val fade = colour.copy(alpha = ease(rise * 6f) * (1f - rise * rise))
            oval(head, 7f, 5.5f, fade)
            limb(head + Offset(6f, 0f), head + Offset(6f, -22f), fade, 3.5f)
            limb(head + Offset(6f, -22f), head + Offset(15f, -17f), fade, 3.5f)
        }
    }

    override val sparkles get() = false
}

/** A day worth a sparkler: he draws one big figure in the air, and it stays behind as light. */
private object SparklerDrawing : MoAct("happy_sparkler_drawing", MoOccasion.Happy) {
    /** How much of the figure stays lit behind the sparkler, in radians of its turn. */
    private const val TRAIL = 5.2f
    private const val BANDS = 16
    private const val STEPS = 4

    private fun side(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    private fun turn(t: Float, v: MoVariant) = cycle(t, v.between(2.8f, 3.8f, salt = 4)) * TURN

    /** The figure as one smooth closed stroke, from -1 to 1 on both axes: a heart, a star or a flower. */
    private fun figure(u: Float, v: MoVariant): Offset = when (v.pick(3, salt = 1)) {
        0 -> Offset(sin(u).pow(3), -(13f * cos(u) - 5f * cos(2f * u) - 2f * cos(3f * u) - cos(4f * u)) / 16f - 0.15f)
        // Two turns one way and three the other meet in a point five times: a star in one stroke.
        1 -> Offset(1.7f * sin(3f * u) - 3f * sin(2f * u), -3f * cos(2f * u) - 1.7f * cos(3f * u)) / 4.7f
        else -> (0.68f + 0.32f * cos(5f * u)).let { Offset(it * sin(u), -it * cos(u)) }
    }

    /** Where the light is in the still air beside his tip, clear of his face on either body. */
    private fun light(u: Float, v: MoVariant): Offset {
        val at = figure(u, v)
        return Offset(CENTRE + side(v) * (100f + 38f * at.x), 128f + 58f * at.y)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val at = figure(turn(t, v), v)
        val side = side(v)
        val wand = MoArm(Offset(30f + 14f * at.x, -58f + 16f * at.y), bend = 0.3f)
        val free = MoArm(Offset(50f, -2f - 6f * at.x), bend = 0.1f)
        val look = MoEye(gaze = Offset(side * (0.6f + 0.3f * at.x), -0.7f + 0.3f * at.y))
        return moIdle(t, energy = 1.2f).copy(
            leftEye = look,
            rightEye = look,
            mouth = MoMouth.Grin(open = 0.75f),
            leftArm = if (side < 0f) wand else free,
            rightArm = if (side > 0f) wand else free,
            tilt = side * 3f * at.x,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val pose = pose(t, v, rig.body.kind)
        val u = turn(t, v)
        val side = side(v)
        val colour = v.of(listOf(MoPropGold, MoPropPink, MoPropSky, MoPropMint), salt = 2)
        // The figure hangs still in the air while he leans into it.
        fun lit(back: Float) = pose.toHeld(light(u - back, v))

        // Bands with flat ends, so the fading stroke never overlaps itself into beads.
        val step = TRAIL / (BANDS * STEPS)
        for (band in BANDS - 1 downTo 0) {
            val fresh = 1f - band / BANDS.toFloat()
            val stroke = Path().apply {
                val start = lit(band * STEPS * step)
                moveTo(start.x, start.y)
                for (index in 1..STEPS) lit((band * STEPS + index) * step).let { lineTo(it.x, it.y) }
            }
            drawPath(
                stroke,
                colour.copy(alpha = 0.2f + 0.8f * fresh),
                style = Stroke(width = 3.5f + 5f * fresh, cap = StrokeCap.Butt, join = StrokeJoin.Round),
            )
        }

        val end = lit(0f)
        limb(if (side < 0f) rig.leftHand else rig.rightHand, end, MoPropGrey, 3.5f)
        grippingMitten(rig, side)
        sparkle(end, 15f + 3f * wave(t, 0.09f), 1f, MoSpark)
        for (index in 0..2) {
            val angle = t * 9f + index * TURN / 3f
            val out = 9f + 7f * cycle(t, 0.31f, offset = index / 3f)
            sparkle(end + Offset(cos(angle), sin(angle)) * out, 4f, 1f, MoPropWhite)
        }
    }

    override val sparkles get() = false
}

/** A finished day: he swings on a garden swing, kicking at each end and swooshing through the middle. */
private object GardenSwing : MoAct("happy_garden_swing", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private const val PERIOD = 2.6f
    private const val BEAM = 23f
    private const val SEAT_LIFT = 28f
    private const val ROPE_HALF = 104f
    private const val SEAT_Y = 392f

    /** How far the ropes swing out, in degrees. */
    private fun reach(v: MoVariant) = v.between(3.4f, 4.6f, salt = 3)

    // A quarter in, so the first frame is the high point of one end.
    private fun swing(t: Float, v: MoVariant) = (if (v.chance(50, salt = 4)) 1f else -1f) * wave(t + PERIOD / 4f, PERIOD)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = swing(t, v)
        val speed = swing(t + PERIOD / 4f, v)
        val angle = reach(v) * s * PI.toFloat() / 180f
        // A real pendulum about the beam, so the ropes stay tied to it; only the rise is a little generous.
        val rope = SEAT_Y - SEAT_LIFT - BEAM
        val idle = moIdle(t, energy = 0.6f)
        val ends = MoEye(gaze = Offset(s * 0.6f, -0.3f))
        return idle.copy(
            leftEye = if (abs(s) < 0.45f) MoEye(shape = MoEyeShape.Happy) else ends,
            rightEye = if (abs(s) < 0.45f) MoEye(shape = MoEyeShape.Happy) else ends,
            mouth = MoMouth.Grin(open = 0.4f + 0.4f * abs(s)),
            leftArm = MoArm(Offset(ROPE_HALF - MoBodies.of(body).shoulderHalf, -44f), bend = 0.45f),
            rightArm = MoArm(Offset(ROPE_HALF - MoBodies.of(body).shoulderHalf, -44f), bend = 0.45f),
            armsInFront = true,
            legs = MoLegs(MoLeg(lift = 5f * s * s), MoLeg(lift = 5f * swing(t - 0.12f, v).let { it * it })),
            // The tip streams behind the motion.
            sway = idle.sway - 16f * speed,
            squash = 0.02f * s * s,
            tilt = -reach(v) * s * 1.3f,
            offset = Offset(rope * sin(angle), -SEAT_LIFT - rope * (1f - cos(angle)) - 8f * s * s),
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        // A whole swing set, so the ropes hang from something however much room there is above him.
        limb(Offset(-34f, BEAM), Offset(-40f, FEET - 4f), MoPropWoodDark, 8f)
        limb(Offset(264f, BEAM), Offset(270f, FEET - 4f), MoPropWoodDark, 8f)
        chunk(Offset(CENTRE, BEAM), 310f, 15f, MoPropWood, MoPropWoodDark, corner = 7f)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val pose = pose(t, v, rig.body.kind)
        val (seat, ledge) = v.of(
            listOf(MoPropWood to MoPropWoodDark, MoPropRed to MoPropRedDark, MoPropTeal to MoPropTealDark),
            salt = 1,
        )
        val rope = v.of(listOf(MoPropSand, MoPropCream), salt = 2)
        for (side in listOf(-1f, 1f)) {
            val knot = pose.toHeld(Offset(CENTRE + side * ROPE_HALF, BEAM))
            val hand = if (side < 0f) rig.leftHand else rig.rightHand
            limb(knot, hand, rope, 5f)
            limb(hand, Offset(CENTRE + side * ROPE_HALF, SEAT_Y), rope, 5f)
            drawCircle(rope, 5f, knot)
            grippingMitten(rig, side)
        }
        chunk(Offset(CENTRE, SEAT_Y), 228f, 13f, seat, ledge, corner = 6f)
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** A day to dance: under a turning mirror ball he strikes the pose, and swaps sides on every beat. */
private object MirrorBall : MoAct("happy_mirror_ball", MoOccasion.Happy) {
    private const val BALL_TURN = 3.6f
    private const val RADIUS = 30f

    private fun beat(v: MoVariant) = v.between(0.8f, 1.05f, salt = 3)

    private fun ballSide(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** Which way he points, from -1 to 1: it snaps over at the start of a beat and then holds. */
    private fun pointing(t: Float, v: MoVariant): Float {
        val beat = beat(v)
        // Half a beat in, so the first frame is a held pose and not the swap.
        val at = cycle(t, 2f * beat, offset = 0.25f) * 2f
        val first = ballSide(v) * if (v.chance(50, salt = 4)) 1f else -1f
        val now = if (at < 1f) first else -first
        return now * (2f * ease(at.mod(1f) * beat / 0.18f) - 1f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val beat = beat(v)
        val right = (pointing(t, v) + 1f) / 2f
        val point = MoArm(Offset(44f, -58f), bend = 0.15f)
        val idle = moIdle(t, energy = 1.4f)
        val joy = MoEye(shape = MoEyeShape.Happy)
        return idle.copy(
            leftEye = joy,
            rightEye = joy,
            mouth = MoMouth.Grin(open = 0.8f),
            leftArm = mix(MoArm.Hip, point, 1f - right),
            rightArm = mix(MoArm.Hip, point, right),
            // The foot under the hip arm comes off the floor a little.
            legs = MoLegs(MoLeg(lift = 3f * right, swing = 5f * right), MoLeg(lift = 3f * (1f - right), swing = 5f * (1f - right))),
            // The tip snaps over a moment after the body.
            sway = idle.sway + 12f * pointing(t - 0.1f, v),
            tilt = -5f * pointing(t, v),
            offset = Offset(0f, -9f * twitch(t + beat / 2f, beat, beat = 0.3f)),
            // He lands as the hop ends.
            squash = idle.squash - 0.03f * twitch(t + beat / 2f - 0.3f, beat, beat = 0.14f),
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val colours = listOf(MoPropPink, MoPropSky, MoPropMint, MoPropYellow, MoPropPurple)
        val firstColour = v.pick(colours.size, salt = 2)
        val throb = 1f + 0.12f * twitch(t + beat(v) / 2f, beat(v), beat = 0.25f)
        // Flat spots of light that drift the way the ball turns, fading in and out so none pops.
        for ((index, spot) in Spots.withIndex()) {
            val drift = cycle(t, BALL_TURN * 2f, offset = index / Spots.size.toFloat())
            val at = spot.from + Offset(46f * drift, -10f * sin(drift * PI.toFloat()))
            val colour = colours[(firstColour + index % 3) % colours.size].copy(alpha = 0.7f * sin(drift * PI.toFloat()))
            if (spot.onFloor) oval(at, 22f * throb, 7f * throb, colour) else drawCircle(colour, 14f * throb, at)
        }

        val ball = Offset(CENTRE + ballSide(v) * 118f, 56f)
        threadFromAbove(ball, MoPropGrey, 3f)
        block(ball + Offset(0f, -RADIUS), 12f, 8f, MoPropGrey, corner = 3f)
        drawCircle(MoPropGreyDark, RADIUS, ball)
        // Mirrors on a turning ball: they cross its face, narrow toward the edge, and come round again.
        val shift = cycle(t, BALL_TURN) * COLUMNS
        for (row in 0..3) {
            val y = (row - 1.5f) * RADIUS * 0.42f
            val half = sqrt((RADIUS * RADIUS - y * y).coerceAtLeast(0f))
            for (column in 0 until COLUMNS) {
                val angle = (-0.5f + (column + shift.mod(1f)) / COLUMNS) * PI.toFloat()
                val mirror = (column - shift.toInt()).mod(COLUMNS)
                val glint = (mirror == 1 && row == 0) || (mirror == 4 && row == 2)
                block(ball + Offset(half * 0.94f * sin(angle), y), RADIUS * 0.31f * cos(angle), RADIUS * 0.29f, if (glint) MoPropWhite else MoPropSilver, corner = 1.5f)
            }
        }
    }

    private class Spot(val from: Offset, val onFloor: Boolean)

    private const val COLUMNS = 6

    private val Spots = listOf(
        Spot(Offset(-22f, 421f), onFloor = true),
        Spot(Offset(-30f, 300f), onFloor = false),
        Spot(Offset(196f, 232f), onFloor = false),
        Spot(Offset(84f, 422f), onFloor = true),
        Spot(Offset(-14f, 170f), onFloor = false),
        Spot(Offset(208f, 336f), onFloor = false),
        Spot(Offset(198f, 421f), onFloor = true),
    )

    override val sparkles get() = false
}

/** Your win: a pom-pom on each mitten, one up and one down, swap, both up, and a shake with a hop. */
private object PomPoms : MoAct("happy_pom_poms", MoOccasion.Happy) {
    private const val BEAT = 0.8f
    private const val LAG = 0.018f
    private val High = MoArm(Offset(26f, -70f), bend = 0.3f)
    private val Low = MoArm(Offset(26f, 26f), bend = 0.6f)

    // A little into beat three, so the first frame is the cheer itself: both up, looking at you.
    private fun routine(t: Float) = cycle(t, 4f * BEAT, offset = 0.59f) * 4f

    /** How far up an arm is, from where the routine wants it on each of the four beats. */
    private fun raised(t: Float, marks: List<Float>): Float {
        val at = routine(t)
        val beat = at.toInt().coerceIn(0, 3)
        return lerp(marks[(beat + 3) % 4], marks[beat], ease((at - beat) * BEAT / 0.28f))
    }

    /** The shake and the hop both live in beat four, and swell and fade inside it. */
    private fun finale(t: Float) = pulse((routine(t) - 3f).coerceIn(0f, 1f), 1f)

    private fun arm(t: Float, v: MoVariant, side: Float): MoArm {
        val leads = (side < 0f) == v.chance(50, salt = 2)
        val up = raised(t, if (leads) listOf(1f, 0f, 1f, 1f) else listOf(0f, 1f, 1f, 1f))
        val shake = 5f * finale(t) * wave(t + if (side < 0f) 0f else 0.05f, 0.12f)
        return mix(Low, High, up).let { it.copy(reach = it.reach + Offset(shake, 0f)) }
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val hop = finale(t)
        val idle = moIdle(t, energy = 1.5f)
        val kick = MoLeg(lift = 12f * hop, swing = 10f * hop)
        val kicksLeft = v.chance(50, salt = 3)
        val you = MoEye(iris = 0.8f, scale = 1.04f)
        val lean = raised(t, listOf(0f, 1f, 1f, 1f)) - raised(t, listOf(1f, 0f, 1f, 1f))
        return idle.copy(
            palette = MoPalettes.Golden.takeIf { v.chance(30, salt = 4) },
            leftEye = you,
            rightEye = you,
            brows = MoBrows(MoBrowKind.Raised, lift = 3f),
            mouth = MoMouth.Beam,
            leftArm = arm(t, v, -1f),
            rightArm = arm(t, v, 1f),
            legs = MoLegs(if (kicksLeft) kick else MoLeg(), if (kicksLeft) MoLeg() else kick),
            squash = idle.squash + 0.03f * hop,
            // Toward the high arm, which also carries the low pom-pom away from the edge of the picture.
            tilt = 2.5f * lean * if (v.chance(50, salt = 2)) 1f else -1f,
            offset = Offset(0f, -14f * hop),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (one, other) = v.of(listOf(MoPropRed to MoPropWhite, MoPropBlue to MoPropYellow, MoPropPink to MoPropMint), salt = 1)
        for (side in listOf(-1f, 1f)) {
            // Each pom-pom trails its mitten by a moment, which is what makes it look soft.
            val reach = arm(t - LAG, v, side).reach
            val hand = Offset(CENTRE + side * (rig.body.shoulderHalf + reach.x), rig.body.shoulderY + reach.y)
            pomPom(hand + Offset(0f, -10f), one, other, t + side)
        }
    }

    /** A shaggy ball that never quite holds still, even while his arms rest. */
    private fun DrawScope.pomPom(centre: Offset, one: Color, other: Color, t: Float) {
        // Strands so close that they close into a ball with a ragged edge. The second colour comes
        // unevenly, because a regular alternation reads as a peppermint and not as a pom-pom.
        fun ring(strands: Int, reach: Float, ragged: Float, width: Float, turned: Float, accent: Int) {
            for (strand in 0 until strands) {
                val angle = turned + strand * TURN / strands + 0.16f * wave(t + strand * 0.37f, 0.23f)
                val length = reach + ragged * ((strand * 5) % 3) / 2f
                val colour = if ((strand * 7 + strand / 3) % 3 == accent) other else one
                limb(centre, centre + Offset(cos(angle), sin(angle)) * length, colour, width)
            }
        }
        ring(strands = 18, reach = 22f, ragged = 6.5f, width = 7f, turned = 0f, accent = 0)
        ring(strands = 11, reach = 15f, ragged = 3f, width = 8f, turned = 0.3f, accent = 1)
        drawCircle(one, 8f, centre)
    }
}

internal val happyActs2: List<MoAct> = listOf(
    PinwheelWarmth,
    Squeezebox,
    SparklerDrawing,
    GardenSwing,
    MirrorBall,
    PomPoms,
)
