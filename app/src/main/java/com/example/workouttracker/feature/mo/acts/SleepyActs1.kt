package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropCharcoal
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropMint
import com.example.workouttracker.core.ui.theme.MoPropMoon
import com.example.workouttracker.core.ui.theme.MoPropNight
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.FEET
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
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.mitten
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.threadFromAbove
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * The wind-down hour, on his feet: he is tired and fighting it, or has stopped fighting. None of
 * these is in bed, so none is Strong.
 */

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm =
    MoArm(Offset(mix(from.reach.x, to.reach.x, amount), mix(from.reach.y, to.reach.y, amount)), mix(from.bend, to.bend, amount))

/** 0 before [from], 1 after [to], eased in between. */
private fun ramp(p: Float, from: Float, to: Float): Float = if (to <= from) 0f else ease((p - from) / (to - from))

/** Trying to stay up: he sinks sideways, lids closing, jerks awake, and checks whether anyone saw. */
private object NoddingOff : MoAct("sleepy_nodding_off", MoOccasion.Sleepy) {
    private const val SINK_END = 0.70f
    private const val JERK_END = 0.76f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    // 0.35 into the loop he is already half way down, which is the frame a still picture needs.
    private fun progress(t: Float, v: MoVariant) = cycle(t, v.between(5.5f, 7f, salt = 2), offset = 0.35f)

    private fun nod(p: Float, v: MoVariant): Float {
        if (p >= JERK_END) return 0f
        if (p >= SINK_END) return 1f - ramp(p, SINK_END, SINK_END + 0.025f)
        // False dips: he catches himself once or twice before the one that gets him.
        var dips = 0f
        for (index in 0 until v.pick(3, salt = 3)) {
            val into = (p - (0.12f + index * 0.16f)) / 0.09f
            if (into in 0f..1f) dips += 0.15f * sin(into * PI.toFloat())
        }
        return ease(p / SINK_END) + dips
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = progress(t, v)
        val side = side(v)
        val nod = nod(p, v)
        val jerk = if (p in SINK_END..JERK_END) sin((p - SINK_END) / (JERK_END - SINK_END) * PI.toFloat()) else 0f
        val alert = ramp(p, SINK_END, SINK_END + 0.02f) * (1f - ramp(p, 0.90f, 1f))
        val glance = 0.9f * (-ramp(p, 0.77f, 0.79f) + 2f * ramp(p, 0.85f, 0.87f) - ramp(p, 0.93f, 0.95f))
        val eye = MoEye(
            lidDrop = (0.25f + 0.6f * nod) * (1f - alert),
            gaze = Offset(glance, 0.6f * nod),
            iris = mix(0.76f, 0.5f, alert),
            scale = 1f + 0.05f * alert,
        )
        val idle = moIdle(t, energy = 0.5f)
        val propped = v.chance(35, salt = 4)
        val prop = if (propped) ramp(nod, 0.05f, 0.25f) else 0f
        // Out on the edge of his face: beside the eye, below it, and far enough that the mitten
        // breaks his outline instead of sitting orange on orange inside it.
        val onCheek = mix(MoArm.Down, MoArm(Offset(14f, -28f), bend = 0.6f), prop).copy(warm = prop)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (alert > 0.3f) MoBrows(MoBrowKind.Raised, lift = 6f * alert) else MoBrows(),
            mouth = when {
                p in SINK_END..0.78f -> MoMouth.Oh(7f)
                alert > 0.05f -> MoMouth.Flat(12f)
                nod > 0.8f -> MoMouth.Oh(5f)
                else -> MoMouth.Flat(10f)
            },
            leftArm = if (side < 0f) onCheek else MoArm.Down,
            rightArm = if (side > 0f) onCheek else MoArm.Down,
            // For the whole loop on a propped day: switching layers as the mitten rises would pop.
            armsInFront = propped,
            tilt = side * (2f + 15f * nod),
            sway = idle.sway + side * 24f * nod,
            flare = -14f * nod + 12f * jerk,
            squash = idle.squash - 0.05f * nod + 0.04f * jerk,
            // The squash sinks his head. The offset keeps the low foot planted as he tilts.
            offset = Offset(0f, -9f * nod - 8f * jerk),
            blinks = alert < 0.1f,
        )
    }
}

/** Asleep in a saucer candle holder, flame turned low, squirming now and then to get comfortable. */
private object CandleHolderBed : MoAct("sleepy_candle_holder_bed", MoOccasion.Sleepy, intensity = MoIntensity.Mild) {
    private const val BREATH = 3.6f

    /** The cup's top edge: still under his mouth on the low body, deep enough that he plainly sits in it. */
    private const val RIM = 358f

    private fun colours(v: MoVariant) = v.of(
        listOf(MoPropGold to MoPropGoldDark, MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark),
        salt = 1,
    )

    private fun squirmPeriod(v: MoVariant) = BREATH * (2 + v.pick(3, salt = 3))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val breath = pulse(t, BREATH)
        val period = squirmPeriod(v)
        // Half a period in, so the still picture is the settled one.
        val squirm = twitch(t, period, beat = 1.3f, offset = 0.5f)
        val content = twitch(t, period, beat = 1.6f, offset = 0.5f - 1.3f / period)
        val shoulder = MoBodies.of(body).shoulderY
        // Out past his own sides, where a mitten on the rim is not orange on orange. The rim is at
        // the same height for both bodies, so only the reach differs.
        val onRim = MoArm(Offset(24f, RIM - 8f - shoulder), bend = 0.45f)
        return moIdle(t, energy = 0.35f).copy(
            leftEye = MoEye(shape = MoEyeShape.Shut),
            rightEye = MoEye(shape = MoEyeShape.Shut),
            mouth = MoMouth.Smile(width = 12f + 3f * content, depth = 9f + 3f * content),
            leftArm = onRim,
            rightArm = onRim,
            armsInFront = true,
            flare = v.between(-34f, -24f, salt = 4) + 8f * breath,
            squash = 0.02f * breath - 0.01f,
            tilt = 2f + 3f * squirm * sin(t * 9f + 1f),
            offset = Offset(4f * squirm * sin(t * 9f), 0f),
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        floorShadow(CENTRE, 156f, MoPropShadow)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val (colour, ledge) = colours(v)
        // The ring is drawn first and half of it disappears behind the plate, so it reads as attached.
        val handle = Offset(CENTRE + (if (v.chance(50, salt = 2)) 1f else -1f) * 132f, 399f)
        drawCircle(ledge, radius = 19f, center = handle + Offset(0f, 5f), style = Stroke(9f))
        drawCircle(colour, radius = 19f, center = handle, style = Stroke(9f))
        chunk(Offset(CENTRE, 418f), width = 300f, height = 15f, colour, ledge, corner = 7f, drop = 6f)
        val cupHeight = 418f - RIM
        chunk(Offset(CENTRE, RIM + cupHeight / 2f), width = 244f, height = cupHeight, colour, ledge, corner = 20f, drop = 6f)
        // A lighter lip along the rim, so the cup reads as hollow and him as sitting down inside it.
        block(Offset(CENTRE, RIM + 5f), 228f, 7f, MoPropCream.copy(alpha = 0.45f), corner = 3.5f)
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** Sheep hop a tiny fence at his feet, and his lids sink a little further with every landing. */
private object CountingSheep : MoAct("sleepy_counting_sheep", MoOccasion.Sleepy, intensity = MoIntensity.Mild) {
    private const val CROSSING = 6f
    // A full-size sheep is about 31 wide either side of its middle, and the visible box ends at -45 and 275.
    private const val FROM = -40f
    private const val TRAVEL = 310f
    private const val HOP_FROM = 60f
    private const val HOP_WIDTH = 110f
    private const val HOP_HEIGHT = 26f
    private const val GROUND = 404f

    // As big as they can be while the top of a hop still passes under the low body's mouth.
    private const val SHEEP_SCALE = 1.4f

    private fun dir(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f
    private fun count(v: MoVariant) = 2 + v.pick(2, salt = 2)

    // Sheep 0 starts half way across, which is the top of its hop over the fence.
    private fun along(t: Float, v: MoVariant, index: Int) = cycle(t, CROSSING, offset = 0.5f + index.toFloat() / count(v))

    private fun x(along: Float, dir: Float) = if (dir > 0f) FROM + TRAVEL * along else FROM + TRAVEL * (1f - along)

    private fun hop(x: Float): Float {
        val over = (x - HOP_FROM) / HOP_WIDTH
        return if (over in 0f..1f) sin(over * PI.toFloat()) else 0f
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val dir = dir(v)
        val n = count(v)
        val watched = (0 until n).map { x(along(t, v, it), dir) }.minBy { abs(it - CENTRE) }
        // The share of the crossing at which a sheep touches down, the same in either direction.
        val landing = (HOP_FROM + HOP_WIDTH - FROM) / TRAVEL
        val since = (n * (t / CROSSING + 0.5f - landing)).mod(1f)
        val dip = if (since < 0.08f) ease(since / 0.08f) else 1f - ease((since - 0.08f) / 0.6f)
        val eye = MoEye(
            gaze = Offset(((watched - CENTRE) / 150f).coerceIn(-1f, 1f), 0.95f - 0.35f * hop(watched)),
            lidDrop = 0.45f + 0.3f * dip,
        )
        return moIdle(t, energy = 0.5f).copy(
            leftEye = eye,
            rightEye = eye,
            mouth = MoMouth.Smile(width = 11f, depth = 7f),
            tilt = 1.5f - 2.5f * dip,
            flare = -5f * dip,
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val dir = dir(v)
        val n = count(v)
        val (wood, woodLedge) = v.of(listOf(MoPropWood to MoPropWoodDark, MoPropCream to MoPropGrey), salt = 3)
        val dark = if (v.chance(40, salt = 4)) n - 1 else -1
        // Never sheep 0: that is the one over the fence at t = 0, and the still frame wants it whole.
        val lamb = if (v.chance(35, salt = 5)) n - 1 else -1

        floorShadow(CENTRE, 42f, MoPropShadow)
        for (post in listOf(-24f, 24f)) {
            chunk(Offset(CENTRE + post, 410f), 9f, 26f, wood, woodLedge, corner = 4.5f, drop = 4f)
        }
        for (rail in listOf(403f, 415f)) {
            limb(Offset(CENTRE - 33f, rail + 2f), Offset(CENTRE + 33f, rail + 2f), woodLedge, 5f)
            limb(Offset(CENTRE - 33f, rail), Offset(CENTRE + 33f, rail), wood, 5f)
        }

        for (index in 0 until n) {
            val along = along(t, v, index)
            // They grow out of nothing at the edges, slowly enough to stay inside the box, so the wrap can never show.
            val size = SHEEP_SCALE * (if (index == lamb) 0.6f else 1f) * ramp(along, 0f, 0.10f) * (1f - ramp(along, 0.90f, 1f))
            if (size <= 0.01f) continue
            val x = x(along, dir)
            val wool = if (index == dark) MoPropGreyDark else MoPropCream
            val skin = if (index == dark) MoPropCream else MoPropCharcoal
            sheep(t + index * 0.11f, x, hop(x), dir, size, wool, skin)
        }
    }

    private fun DrawScope.sheep(t: Float, x: Float, hop: Float, dir: Float, size: Float, wool: Color, skin: Color) {
        floorShadow(x, 17f * size * (1f - 0.4f * hop), MoPropShadow)
        val air = if (hop > 0f) 1f else 0f
        // How far through the jump he is, counted from take-off, whichever way the line runs.
        val over = ((x - HOP_FROM) / HOP_WIDTH).coerceIn(0f, 1f)
        val through = if (dir > 0f) over else 1f - over
        withTransform({
            translate(0f, -HOP_HEIGHT * hop)
            scale(size, size, Offset(x, FEET - 3f))
            // Nose up on the way over, nose down on the way back to the grass.
            rotate(-dir * 14f * sin(through * 2f * PI.toFloat()), Offset(x, GROUND))
        }) {
            val body = Offset(x, GROUND - 2f * abs(wave(t, 0.3f)) * (1f - air))
            for ((legX, lead) in listOf(-9f to -1f, -4f to 1f, 4f to -1f, 9f to 1f)) {
                // Trotting on the ground; in the air the front pair reaches forward and the back pair trails.
                val trot = lead * 4f * wave(t, 0.3f) * (1f - air)
                val leap = (if (legX * dir > 0f) 1f else -1f) * dir * 7f * hop
                limb(body + Offset(legX, 8f), Offset(x + legX + trot + leap, FEET - 3f - 4f * hop), skin, 3.5f)
            }
            puff(body, 15f, wool)
            drawCircle(wool, 5f, body + Offset(-dir * 15f, -2f))
            oval(body + Offset(dir * 15f, -5f), 7f, 6f, skin)
            oval(body + Offset(dir * 10f, -10f), 3.5f, 2.2f, skin)
        }
    }

    override val ownsTheFloor get() = true
}

/** The moon is a ceiling lamp on a pull chain: he tugs it dark, dozes, and his sagging arm tugs it on again. */
private object LightsOut : MoAct("sleepy_lights_out", MoOccasion.Sleepy) {
    private const val OFF_AT = 0.04f
    private const val ON_AT = 0.62f
    private const val LIFT_AT = 0.80f
    private const val ARM_UP = -62f
    private const val PULL = 26f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f
    private fun progress(t: Float, v: MoVariant) = cycle(t, v.between(5.5f, 7f, salt = 5))

    /** How far the chain is pulled, 0 to 1. The lamp clicks whenever it reaches 1. */
    private fun pull(p: Float): Float = when {
        p < 2f * OFF_AT -> sin(p / (2f * OFF_AT) * PI.toFloat())
        p < 0.12f -> 0f
        // The sleeping arm sags slowly at first and drops the last bit, as a dozing arm does.
        p < ON_AT -> ((p - 0.12f) / (ON_AT - 0.12f)).let { it * it * it }
        p < LIFT_AT -> 1f
        else -> 1f - ramp(p, LIFT_AT, 1f)
    }

    private fun lit(p: Float) = p < OFF_AT || p >= ON_AT

    private fun moon(p: Float, side: Float) = Offset(CENTRE + side * 125f, 58f + 6f * pull(p))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = progress(t, v)
        val side = side(v)
        val asleep = ramp(p, 0.10f, 0.18f) * (1f - ramp(p, ON_AT, ON_AT + 0.02f))
        val atTheMoon = Offset(side * 0.8f, -0.9f)
        val shut = MoEye(shape = MoEyeShape.Shut)
        val heavy = MoEye(lidDrop = 0.5f, gaze = atTheMoon)
        val puzzled = MoEye(lidDrop = 0.3f, gaze = atTheMoon, iris = 0.62f)
        val dozing = p in 0.10f..ON_AT
        val oneEyed = p > ON_AT && p < LIFT_AT
        val lampEye = when {
            dozing -> shut
            oneEyed -> puzzled
            else -> heavy
        }
        val otherEye = if (dozing || oneEyed) shut else heavy
        val pulling = MoArm(Offset(30f, ARM_UP + PULL * pull(p)), bend = 0.22f)
        val idle = moIdle(t, energy = 0.5f)
        // No tilt, squash, offset or shake at all: the chain in his hand has to meet a moon that hangs in the world.
        return idle.copy(
            leftEye = if (side < 0f) lampEye else otherEye,
            rightEye = if (side > 0f) lampEye else otherEye,
            brows = if (oneEyed) MoBrows(MoBrowKind.Raised, lift = 2f, skew = if (side > 0f) 6f else 0f) else MoBrows(),
            mouth = when {
                dozing -> MoMouth.Smile(12f, 9f)
                oneEyed -> MoMouth.Oh(5f)
                else -> MoMouth.Flat(10f)
            },
            leftArm = if (side < 0f) pulling else MoArm.Down,
            rightArm = if (side > 0f) pulling else MoArm.Down,
            sway = idle.sway - side * 10f * asleep,
            flare = -8f * asleep,
            squash = 0f,
            blinks = !dozing && !oneEyed,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val p = progress(t, v)
        val side = side(v)
        val moon = moon(p, side)
        threadFromAbove(moon, MoPropGreyDark, 3f)
        if (lit(p)) {
            drawCircle(MoPropMoon, 26f, moon)
            drawCircle(MoPropGrey.copy(alpha = 0.35f), 6f, moon + Offset(-8f, -6f))
            drawCircle(MoPropGrey.copy(alpha = 0.35f), 4f, moon + Offset(9f, 8f))
        } else {
            drawCircle(MoPropNight, 26f, moon)
            drawCircle(MoPropMoon.copy(alpha = 0.6f), 24.5f, moon, style = Stroke(3f))
            // With the lamp off, the real night comes out on the far side of the sky.
            val stars = listOf(Offset(-120f, 40f), Offset(-95f, 112f), Offset(-138f, 176f), Offset(58f, 24f))
            stars.take(3 + v.pick(2, salt = 4)).forEachIndexed { index, star ->
                val out = ramp(p, OFF_AT + index * 0.03f, OFF_AT + 0.08f + index * 0.03f)
                sparkle(Offset(CENTRE + side * star.x, star.y), 14f, out * (0.85f + 0.15f * wave(t + index, 1.7f)), MoPropMoon)
            }
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = progress(t, v)
        val side = side(v)
        val hand = if (side > 0f) rig.rightHand else rig.leftHand
        val top = moon(p, side) + Offset(0f, 26f)
        val knob = hand + Offset(0f, 24f)
        val beads = 13
        for (index in 0..beads) {
            val at = index.toFloat() / beads
            drawCircle(MoPropSilver, 3f, Offset(mix(top.x, knob.x, at), mix(top.y, knob.y, at)))
        }
        val (colour, ledge) = v.of(
            listOf(MoPropPink to MoPropPinkDark, MoPropTeal to MoPropTealDark, MoPropGold to MoPropGoldDark),
            salt = 3,
        )
        when (v.pick(3, salt = 2)) {
            0 -> {
                drawCircle(ledge, 8.5f, knob + Offset(0f, 3f))
                drawCircle(colour, 8.5f, knob)
            }
            1 -> {
                sparkle(knob + Offset(0f, 3f), 14f, 1f, ledge)
                sparkle(knob, 14f, 1f, colour)
            }
            else -> {
                for (strand in -1..1) limb(knob + Offset(0f, 2f), knob + Offset(strand * 5f, 16f), ledge, 3.5f)
                block(knob, 11f, 9f, colour, corner = 4f)
            }
        }
        mitten(rig, hand, side)
    }

    override val sparkles get() = false
}

/** A huge yawn, arms up and eyes squeezed shut. The sleeper in the sky catches it, and that sets him off again. */
private object SleepyCatchingYawn : MoAct("sleepy_catching_yawn", MoOccasion.Sleepy) {
    private const val PERIOD = 7f
    private const val TEAR_FROM = 0.91f
    private const val TEAR_SPAN = 0.30f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, PERIOD)
        val side = side(v)
        val yawn = (1f - ramp(p, 0f, 0.14f)) + ramp(p, 0.80f, 1f)
        val looking = ramp(p, 0.30f, 0.34f) * (1f - ramp(p, 0.64f, 0.70f))
        val squeezed = yawn > 0.55f
        val tearOnLeft = v.chance(50, salt = 4)
        // The tear wells up as his eyes squeeze shut and rolls across the loop boundary, so nothing pops at t = 0.
        val tear = ((p - TEAR_FROM).mod(1f) / TEAR_SPAN).takeIf { it < 1f }
        fun eye(left: Boolean) = MoEye(
            shape = if (squeezed) MoEyeShape.Shut else MoEyeShape.Round,
            lidDrop = 0.55f - 0.1f * looking,
            gaze = Offset(side * 0.8f * looking, mix(0.3f, -0.9f, looking)),
            tear = if (left == tearOnLeft) tear else null,
        )
        // High and wide of his own silhouette: an arm reaching straight up would be orange on orange.
        val stretch = mix(MoArm.Down, MoArm(Offset(40f, -76f), bend = 0.25f), yawn)
        // Some days only the arm away from the sleeper goes up, and the other mitten goes to his hip.
        val oneArmed = v.chance(40, salt = 3)
        val lazy = if (oneArmed) mix(MoArm.Down, MoArm.Hip, yawn) else stretch
        return moIdle(t, energy = 0.6f).copy(
            leftEye = eye(left = true),
            rightEye = eye(left = false),
            brows = if (looking > 0.5f) MoBrows(MoBrowKind.Raised, lift = 2f) else MoBrows(),
            mouth = when {
                yawn > 0.05f -> MoMouth.Yawn(open = yawn)
                p in 0.16f..0.28f -> MoMouth.Chew(open = 0.3f * abs(wave(t, 0.35f)))
                else -> MoMouth.Flat(10f)
            },
            leftArm = if (side < 0f) lazy else stretch,
            rightArm = if (side > 0f) lazy else stretch,
            flare = 16f * yawn,
            squash = 0.05f * yawn - 0.01f,
            tilt = -side * 2f * yawn,
            blinks = !squeezed,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val p = cycle(t, PERIOD)
        val caught = ramp(p, 0.30f, 0.40f) * (1f - ramp(p, 0.52f, 0.62f))
        val centre = Offset(CENTRE + side(v) * 114f, 58f)
        val swell = 1f + 0.06f * caught
        val face = when (v.pick(3, salt = 2)) {
            0 -> {
                drawCircle(MoPropMoon, 34f * swell, centre)
                MoPropGreyDark
            }
            1 -> {
                withTransform({
                    translate(centre.x, centre.y)
                    scale(swell, swell, Offset.Zero)
                }) {
                    shape(
                        MoPropGold, 0f to -35f, 11f to -14f, 34f to -9f, 18f to 8f,
                        21f to 31f, 0f to 21f, -21f to 31f, -18f to 8f, -34f to -9f, -11f to -14f,
                    )
                }
                MoPropWoodDark
            }
            else -> {
                puff(centre + Offset(0f, 2f), 33f * swell, MoPropSilver)
                MoPropGreyDark
            }
        }
        for (eye in listOf(-11f, 11f)) {
            curve(centre + Offset(eye - 6f, -6f), centre + Offset(eye, 2f), centre + Offset(eye + 6f, -6f), face, 3.5f)
        }
        oval(centre + Offset(0f, 10f), 4f + 3f * caught, 2f + 8f * caught, face)
    }

    override val sparkles get() = false
}

/** His blanket is too short: up to his chin and his feet are out, down over his feet and his belly is bare. */
private object BlanketTooShort : MoAct("sleepy_blanket_too_short", MoOccasion.Sleepy, intensity = MoIntensity.Mild) {
    private const val WIDTH = 200f
    private const val HEM = 7f

    private fun period(v: MoVariant) = v.between(5.5f, 7f, salt = 4)

    // 0.2 into the loop the blanket is up and his feet are out, which is the picture that explains it.
    private fun up(t: Float, v: MoVariant): Float {
        val p = cycle(t, period(v), offset = 0.2f)
        return ramp(p, 0f, 0.15f) * (1f - ramp(p, 0.45f, 0.60f))
    }

    /**
     * The blanket's top edge when pulled up, and its height: from under his mouth down to the top of
     * his feet, which still leaves them out and gives the short low body enough cloth to read as one.
     */
    private fun topUp(body: MoBodyKind) = MoBodies.of(body).mouth.y + if (body == MoBodyKind.Tall) 16f else 18f
    private fun height(body: MoBodyKind) = (if (body == MoBodyKind.Tall) 404f else 403f) - topUp(body)
    private fun top(body: MoBodyKind, up: Float) = mix(424f - height(body), topUp(body), up)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val up = up(t, v)
        val leftFoot = v.chance(50, salt = 3)
        // A little heavier with every tug, over three tugs, and then he pulls himself together.
        val heavier = 0.15f * pulse(t, 3f * period(v))
        val peeking = MoEye(lidDrop = mix(0.6f, 0.35f, up) + heavier, gaze = Offset(0f, mix(0.8f, 1f, up)))
        val drooping = MoEye(lidDrop = mix(0.6f, 0.78f, up) + heavier, gaze = Offset(0f, mix(0.8f, 1f, up)))
        val wiggle = ramp(up, 0.5f, 1f)
        fun leg(big: Boolean, phase: Float) =
            MoLeg(swing = (if (big) 8f else 4f) * wiggle * wave(t + phase, 0.4f))
        val onTheEdge = MoArm(Offset(-22f, top(body, up) - MoBodies.of(body).shoulderY), bend = 0.5f)
        return moIdle(t, energy = 0.5f).copy(
            leftEye = if (leftFoot) peeking else drooping,
            rightEye = if (leftFoot) drooping else peeking,
            brows = if (up > 0.5f) MoBrows(MoBrowKind.Raised, skew = if (leftFoot) 0f else 6f) else MoBrows(MoBrowKind.Worried),
            mouth = if (up > 0.5f) MoMouth.Oh(5f) else MoMouth.Pout,
            leftArm = onTheEdge,
            rightArm = onTheEdge,
            armsInFront = true,
            legs = MoLegs(leg(leftFoot, 0f), leg(!leftFoot, 0.13f)),
            tilt = 1.5f * wiggle * wave(t, 0.8f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (colour, ledge) = v.of(
            listOf(MoPropSky to MoPropBlueDark, MoPropPink to MoPropPinkDark, MoPropMint to MoPropTealDark, MoPropPurple to MoPropPurpleDark),
            salt = 1,
        )
        val height = height(rig.body.kind)
        val top = top(rig.body.kind, up(t, v))
        val middle = Offset(CENTRE, top + height / 2f)
        // Barely rounded: a deep corner turns the short low-body blanket into a bolster.
        block(middle + Offset(0f, 6f), WIDTH, height, ledge, corner = 10f)
        block(middle, WIDTH, height, colour, corner = 10f)
        val inside = Offset(CENTRE, top + HEM + (height - HEM) / 2f)
        when (v.pick(3, salt = 2)) {
            0 -> for (column in -1..1) for (row in listOf(-1f, 1f)) {
                drawCircle(MoPropCream, 5f, inside + Offset(column * 52f + row * 9f, row * (height - HEM) * 0.22f))
            }
            1 -> for (column in -1..1) block(inside + Offset(column * 50f, 1f), 10f, height - HEM - 10f, ledge.copy(alpha = 0.55f), corner = 5f)
            else -> {
                val patch = inside + Offset(44f, 1f)
                block(patch, 24f, 22f, MoPropCream, corner = 5f)
                for ((dx, dy) in listOf(-12f to 0f, 12f to 0f, 0f to -11f, 0f to 11f)) {
                    val across = if (dy == 0f) Offset(4f, 0f) else Offset(0f, 4f)
                    limb(patch + Offset(dx, dy) - across, patch + Offset(dx, dy) + across, ledge, 3f)
                }
            }
        }
        // The hem is a band of its own along the top, cut short so the blanket's round corners stay round.
        block(Offset(CENTRE, top + HEM / 2f + 1f), WIDTH - 20f, HEM, ledge, corner = 3.5f)
        mitten(rig, rig.leftHand, -1f)
        mitten(rig, rig.rightHand, 1f)
    }

    // The blanket hangs to the floor on both sides of him, over where his neglected things would lie.
    override val ownsTheFloor get() = true
}

internal val sleepyActs1: List<MoAct> = listOf(
    NoddingOff,
    CandleHolderBed,
    CountingSheep,
    LightsOut,
    SleepyCatchingYawn,
    BlanketTooShort,
)
