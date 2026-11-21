package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropCharcoal
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropGreenDark
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropInk
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropRedDark
import com.example.workouttracker.core.ui.theme.MoPropSand
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSnow
import com.example.workouttracker.core.ui.theme.MoPropSteel
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWater
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoPropYellowDark
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
import com.example.workouttracker.feature.mo.drop
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.polyline
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Festive, part one: the season happens around him, and every one of these is about the one thing a
 * living flame gets wrong about it. The timings are written as parts of a fixed loop, so the whole
 * act is one cycle and the rest beat is plainly visible in it.
 */

private fun mix(from: Float, to: Float, k: Float): Float = from + (to - from) * k

/** One smooth bump of [height], from nothing at [from] up and back down over [span]. */
private fun hump(x: Float, from: Float, span: Float, height: Float): Float {
    val k = (x - from) / max(span, 0.001f)
    return if (k > 0f && k < 1f) height * sin(k * PI.toFloat()) else 0f
}

/** A quadratic run of something: a cord, a rope, a string of lights, and where its parts sit on it. */
private class Strand(val from: Offset, val control: Offset, val to: Offset) {
    fun at(u: Float): Offset = lerp(lerp(from, control, u), lerp(control, to, u), u)
}

private fun DrawScope.strand(s: Strand, colour: Color, width: Float) = curve(s.from, s.control, s.to, colour, width)

// ------------------------------------------------------------------------------------------------

/** A snowman has chosen to stand beside him: Mo leans away, tucked in small, wincing at every drip. */
private object SorryIAmWarm : MoAct("festive_sorry_i_am_warm", MoOccasion.Festive, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun period(v: MoVariant) = v.between(2.6f, 4.0f, salt = 5)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        // The wince and the drip run off one period, so he flinches exactly as the drop lets go.
        val wince = twitch(t, period(v), beat = 0.6f, offset = 0.75f)
        val idle = moIdle(t, energy = 0.7f)
        val eye = MoEye(gaze = Offset(s * 0.9f, 0.6f), iris = 0.76f - 0.18f * wince)
        // Far enough across that the mittens land on the bright belly, where they can be seen at all.
        val tucked = MoArm(Offset(-58f, 20f), bend = 0.45f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 2f + 3f * wince),
            mouth = MoMouth.Smile(width = 11f, depth = 8f - 6f * wince),
            leftArm = tucked,
            rightArm = tucked,
            armsInFront = true,
            offset = Offset(-s * 14f, 0f),
            tilt = -s * (5f + 3f * wince),
            squash = idle.squash - 0.015f * wince,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val x = CENTRE + s * 118f
        // Everything that has melted is on the near side, the one turned toward Mo.
        val n = -s
        val c = cycle(t, period(v), offset = 0.35f)
        val ripple = if (c > 0.85f) sin((c - 0.85f) / 0.15f * PI.toFloat()) else 0f
        val scarf = v.of(listOf(MoPropRed, MoPropTeal, MoPropPurple, MoPropPink), salt = 2)
        val head = Offset(x + n * 3f, 340f)

        floorShadow(x, 38f, MoPropShadow)
        oval(Offset(x + n * 26f, FEET - 1f), 22f + 3f * ripple, 5.5f, MoPropWater)

        drawCircle(MoPropSilver, 32f, Offset(x, 397f))
        drawCircle(MoPropSnow, 32f, Offset(x, 393f))
        oval(Offset(x + n * 26f, 412f), 18f, 12f, MoPropSnow)
        drawCircle(MoPropSnow, 21f, head)
        curve(Offset(x + n * 23f, 374f), Offset(x + n * 30f, 390f), Offset(x + n * 23f, 406f), MoPropWhite, 4f)

        limb(Offset(x + s * 26f, 382f), Offset(x + s * 36f, 358f), MoPropWoodDark, 4.5f)
        if (v.chance(50, salt = 4)) {
            limb(Offset(x + n * 28f, 384f), Offset(x + n * 40f, 400f), MoPropWoodDark, 4.5f)
        } else {
            limb(Offset(x + n * 28f, 380f), Offset(x + n * 42f, 370f), MoPropWoodDark, 4.5f)
        }

        block(Offset(x, 362f), 46f, 10f, scarf, corner = 5f)
        block(Offset(x + s * 18f, 376f), 10f, 20f, scarf, corner = 5f)
        repeat(2 + v.pick(2, salt = 3)) { index -> drawCircle(MoPropCharcoal, 3.5f, Offset(x, 380f + index * 14f)) }

        drawCircle(MoPropInk, 3.5f, head + Offset(-8f, -5f))
        drawCircle(MoPropInk, 3.5f, head + Offset(8f, -5f))
        curve(head + Offset(-8f, 5f), head + Offset(0f, 11f), head + Offset(8f, 5f), MoPropInk, 3.5f)
        shape(
            MoPropOrange,
            (head.x + s * 3f) to (head.y - 4f),
            (head.x + s * 20f) to (head.y + 1f),
            (head.x + s * 3f) to (head.y + 5f),
        )

        // The drop swells on his shoulder, lets go, and lands in the puddle it has been making all day.
        val hang = Offset(x + n * 22f, 372f)
        when {
            c < 0.6f -> drop(hang, 3f + 3.5f * c / 0.6f, MoPropWater)
            c < 0.85f -> drop(Offset(hang.x, hang.y + ease((c - 0.6f) / 0.25f) * 36f), 6.5f, MoPropWater)
        }
    }

    override val ownsTheFloor get() = true
}

// ------------------------------------------------------------------------------------------------

/** The lights are wound round him and plugged into nothing: he squeezes the loose end and they run. */
private object PluggedIntoHimself : MoAct("festive_plugged_into_himself", MoOccasion.Festive) {
    private const val PERIOD = 2.6f
    private const val BULBS = 10

    /** Ten bulbs and three bulbs' worth of dark rest, which is what the head of the run counts out. */
    private const val RUN = 13f

    private fun hand(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun tail(v: MoVariant) = if (v.chance(30, salt = 3)) 1f else 3f

    private fun deadBulb(v: MoVariant) = v.chance(20, salt = 5)

    private fun colours(v: MoVariant): List<Pair<Color, Color>> = when (v.pick(3, salt = 2)) {
        0 -> listOf(MoPropGold to MoPropGoldDark)
        1 -> listOf(MoPropRed to MoPropRedDark, MoPropGreen to MoPropGreenDark)
        else -> listOf(
            MoPropRed to MoPropRedDark,
            MoPropYellow to MoPropYellowDark,
            MoPropBlue to MoPropBlueDark,
            MoPropPink to MoPropPinkDark,
        )
    }

    /** The lead from his mitten and the two turns round his base, which is also where the bulbs sit. */
    private fun strands(mouth: Offset, grip: Offset, h: Float): List<Strand> {
        val span = FEET - mouth.y
        val lead = Strand(
            grip + Offset(0f, 8f),
            Offset(mouth.x + h * 104f, mouth.y + span * 0.10f),
            Offset(mouth.x + h * 78f, mouth.y + span * 0.21f),
        )
        val turnOne = Strand(lead.to, Offset(mouth.x, mouth.y + span * 0.50f), Offset(mouth.x - h * 86f, mouth.y + span * 0.32f))
        val turnTwo = Strand(
            turnOne.to,
            Offset(mouth.x, mouth.y + span * 0.66f),
            Offset(mouth.x + h * 92f, mouth.y + span * 0.39f),
        )
        return listOf(lead, turnOne, turnTwo)
    }

    private fun bulbAt(strands: List<Strand>, index: Int): Offset = when {
        index <= 0 -> strands[0].at(0.6f)
        index <= 4 -> strands[1].at((index - 0.5f) / 4f)
        else -> strands[2].at((index - 4.5f) / 4.5f)
    }

    /** Where his mitten holds the loose end, which the pose needs before there is a rig to ask. */
    private fun grip(body: MoBodyKind, h: Float, squeeze: Float): Offset {
        val shape = MoBodies.of(body)
        return Offset(CENTRE + h * (shape.shoulderHalf + 40f), shape.shoulderY - 30f - 6f * squeeze)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val idle = moIdle(t, energy = 1.1f)
        val h = hand(v)
        val squeeze = twitch(t, PERIOD, beat = 0.3f, offset = 0.5f)
        val head = cycle(t, PERIOD, offset = 0.5f) * RUN
        val lead = head.toInt()
        val shape = MoBodies.of(body)
        val eye = if (lead < BULBS) {
            val at = bulbAt(strands(shape.mouth, grip(body, h, squeeze), h), lead)
            MoEye(gaze = Offset(((at.x - CENTRE) / 100f).coerceIn(-1f, 1f), 0.8f))
        } else {
            MoEye(shape = MoEyeShape.Happy)
        }
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            // The one bulb that never lights holds his eye a moment longer than the others.
            brows = if (deadBulb(v) && lead == 7) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(),
            mouth = if (body == MoBodyKind.Low) MoMouth.Smile(width = 10f, depth = 4f) else MoMouth.Grin(open = 0.7f),
            leftArm = if (h < 0f) MoArm(Offset(40f, -30f - 6f * squeeze), bend = 0.5f) else MoArm.Hip,
            rightArm = if (h > 0f) MoArm(Offset(40f, -30f - 6f * squeeze), bend = 0.5f) else MoArm.Hip,
            flare = 6f * squeeze,
            squash = idle.squash + 0.02f * squeeze,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val colours = colours(v)
        val round = v.chance(50, salt = 4)
        val h = hand(v)
        val head = cycle(t, PERIOD, offset = 0.5f) * RUN
        val grip = if (h < 0f) rig.leftHand else rig.rightHand
        val strands = strands(rig.mouth, grip, h)
        strands.forEach { strand(it, MoPropGreenDark, 4f) }
        val plug = grip + Offset(0f, -12f)
        for (dx in listOf(-4f, 4f)) {
            limb(plug + Offset(dx, -5f), plug + Offset(dx, -13f), MoPropSilver, 3.5f)
        }
        chunk(plug, 16f, 13f, MoPropGrey, MoPropGreyDark, corner = 3f, drop = 2f)
        val litCount = if (rig.body.kind == MoBodyKind.Low) 1f else tail(v)
        for (index in 0 until BULBS) {
            val (bright, dark) = colours[index % colours.size]
            val lit = head - index >= 0f && head - index < litCount && !(deadBulb(v) && index == 7)
            bulb(bulbAt(strands, index), lit, bright, dark, round)
        }
    }

    private fun DrawScope.bulb(at: Offset, lit: Boolean, bright: Color, dark: Color, round: Boolean) {
        block(at, 6f, 4f, MoPropGrey, corner = 1.5f)
        val centre = at + Offset(0f, 7f)
        // A lit bulb swells a little, which is what makes the run read at the size of a thumbnail.
        val swell = if (lit) 1.3f else 1f
        val rx = (if (round) 7f else 6.5f) * swell
        val ry = (if (round) 7f else 8.5f) * swell
        oval(centre, rx, ry, if (lit) bright else dark)
        if (lit) drawCircle(MoPropWhite, 2.4f, centre + Offset(-rx * 0.42f, -ry * 0.36f))
    }

    override val sparkles get() = false
}

// ------------------------------------------------------------------------------------------------

/** A present is sitting right there: he lifts the lid a crack, goes starry, and whistles it off. */
private object OneLittlePeek : MoAct("festive_one_little_peek", MoOccasion.Festive) {
    private const val PERIOD = 5.2f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun boxX(v: MoVariant) = CENTRE + side(v) * 120f

    /** How far the lid is up: eased open, held, and clapped shut, so it is 0 at both ends of the loop. */
    private fun peek(t: Float, v: MoVariant): Float {
        val c = cycle(t, PERIOD, offset = 0.3f)
        val open = ease((c - 0.15f) / 0.1f) * (1f - ease((c - 0.5f) / 0.05f))
        // A greedy day: the lid dips halfway and comes back up in the middle of the peek.
        return if (v.chance(30, salt = 5)) open * (1f - 0.5f * hump(c, 0.30f, 0.08f, 1f)) else open
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val c = cycle(t, PERIOD, offset = 0.3f)
        val peek = peek(t, v)
        val idle = moIdle(t)
        val reach = MoArm(Offset(28f, 22f - 9f * peek), bend = 0.35f)
        val pose = idle.copy(
            leftArm = if (s < 0f) reach else MoArm.Down,
            rightArm = if (s > 0f) reach else MoArm.Down,
        )
        return when {
            // Innocence, the rest beat: he looks the other way and whistles, leaning off the box,
            // with the mitten still resting on the lid. The free hand eases to his hip and back.
            c >= 0.56f && c < 0.9f -> {
                val look = MoEye(gaze = Offset(-s * 0.8f, -0.5f), lidDrop = 0.35f)
                val hip = ease((c - 0.56f) / 0.08f) * (1f - ease((c - 0.84f) / 0.06f))
                val free = MoArm(Offset(mix(4f, -6f, hip), mix(36f, 30f, hip)), bend = mix(0.1f, 0.75f, hip))
                pose.copy(
                    leftEye = look,
                    rightEye = look,
                    mouth = MoMouth.Whistle,
                    leftArm = if (s < 0f) pose.leftArm else free,
                    rightArm = if (s > 0f) pose.rightArm else free,
                    sway = idle.sway - s * 4f,
                )
            }
            c >= 0.9f -> {
                // The eyes creep back, which is already the temptation of the next round.
                val back = ease((c - 0.9f) / 0.1f)
                val look = MoEye(gaze = Offset(mix(-s * 0.8f, s * 0.9f, back), mix(-0.5f, 0.5f, back)), lidDrop = 0.35f * (1f - back))
                pose.copy(leftEye = look, rightEye = look, mouth = MoMouth.Whistle)
            }
            c >= 0.5f -> pose.copy(
                leftEye = MoEye(iris = 0.5f),
                rightEye = MoEye(iris = 0.5f),
                mouth = MoMouth.Flat(12f),
                squash = idle.squash - 0.03f * sin((c - 0.5f) / 0.06f * PI.toFloat()),
            )
            c >= 0.15f -> pose.copy(
                leftEye = MoEye(shape = MoEyeShape.Star),
                rightEye = MoEye(shape = MoEyeShape.Star),
                brows = MoBrows(MoBrowKind.Raised, lift = 4f),
                mouth = MoMouth.Grin(open = 0.5f, tongue = false),
            )
            else -> {
                val look = MoEye(gaze = Offset(s * 0.9f, 0.5f))
                pose.copy(
                    leftEye = look,
                    rightEye = look,
                    brows = MoBrows(MoBrowKind.Raised, skew = 5f),
                    mouth = MoMouth.Oh(5f),
                )
            }
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) = floorShadow(boxX(v), 34f, MoPropShadow)

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val x = boxX(v)
        val peek = peek(t, v)
        val (paper, paperDark, ribbon) = v.of(
            listOf(
                Triple(MoPropRed, MoPropRedDark, MoPropGold),
                Triple(MoPropTeal, MoPropTealDark, MoPropPink),
                Triple(MoPropPurple, MoPropPurpleDark, MoPropYellow),
            ),
            salt = 2,
        )
        // Where the lid rests when the box is shut, which is also where the mitten finds it.
        val top = rig.body.shoulderY + 45f
        val height = FEET - top - 5f

        chunk(Offset(x, (top + FEET) / 2f), 58f, height, paper, paperDark)
        block(Offset(x, (top + FEET) / 2f), 10f, height, ribbon, corner = 2f)
        when (v.pick(3, salt = 3)) {
            1 -> repeat(6) { index ->
                drawCircle(ribbon, 3f, Offset(x - 20f + (index % 3) * 20f, top + 12f + (index / 3) * 18f))
            }
            2 -> repeat(3) { index -> block(Offset(x - 18f + index * 18f, (top + FEET) / 2f), 5f, height, ribbon, corner = 2f) }
        }
        if (peek > 0.01f) block(Offset(x - s * 3f, top - 4.5f * peek), 52f, 9f * peek, MoPropInk, corner = 3f)

        // The lid hinges on its outer end, so the crack opens on the side his mitten is on.
        withTransform({ rotate(s * 16f * peek, Offset(x + s * 31f, top - 7f)) }) {
            chunk(Offset(x, top - 7f), 62f, 14f, paper, paperDark, corner = 6f)
            block(Offset(x, top - 7f), 10f, 14f, ribbon, corner = 2f)
            val bow = Offset(x + s * 13f, top - 16f)
            oval(bow + Offset(-8f, -2f), 10f, 7f, ribbon)
            oval(bow + Offset(8f, -2f), 10f, 7f, ribbon)
            drawCircle(ribbon, 5f, bow)
        }

        val glint = v.of(listOf(MoPropGold, MoPropYellow, MoPropWhite), salt = 4)
        repeat(3) { index ->
            val rise = cycle(t, 1.1f, offset = index * 0.33f + 0.2f)
            // They leave the crack and drift up and outward, away from his face.
            val at = Offset(x + s * (-8f + index * 8f + rise * 16f), top - 8f - rise * 70f)
            sparkle(at, 11f, 1f, glint.copy(alpha = peek * (1f - rise)))
        }
    }

    override val ownsTheFloor get() = true
}

// ------------------------------------------------------------------------------------------------

/** Straight down the hill at us: rope in both mittens, flame flat, and a bump he never sees coming. */
private object SledHeadOn : MoAct(
    "festive_sled_head_on",
    MoOccasion.Festive,
    bodies = setOf(MoBodyKind.Tall),
    intensity = MoIntensity.Strong,
) {
    private const val STEER = 2.4f
    private const val BUMPS = 4f

    // How far the sled's deck is above the snow.
    private const val DECK = 28f

    private fun lean(t: Float, v: MoVariant): Float =
        (if (v.chance(50, salt = 1)) 1f else -1f) * cos(t / STEER * 2f * PI.toFloat())

    private fun air(t: Float, v: MoVariant): Float {
        val b = cycle(t, BUMPS, offset = 0.5f)
        return hump(b, 0f, 0.22f, 1f) + if (v.chance(35, salt = 3)) hump(b, 0.56f, 0.18f, 0.5f) else 0f
    }

    private fun land(t: Float, v: MoVariant): Float {
        val b = cycle(t, BUMPS, offset = 0.5f)
        return hump(b, 0.22f, 0.12f, 1f) + if (v.chance(35, salt = 3)) hump(b, 0.74f, 0.10f, 0.5f) else 0f
    }

    private fun sledY(lean: Float, air: Float) = -13f * abs(lean) - 30f * air

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = cycle(t, BUMPS, offset = 0.5f)
        val lean = lean(t, v)
        val air = air(t, v)
        val land = land(t, v)
        val idle = moIdle(t, energy = 1.8f)
        // Low and forward, but past his own silhouette, or the rope would come out of nowhere.
        val grip = MoArm(Offset(-4f, 46f), bend = 0.5f)
        val flying = air > 0.05f
        val cheering = b > 0.34f && b < 0.465f
        val eye = when {
            flying -> MoEye(iris = 0.5f, scale = 1.05f)
            cheering -> MoEye(shape = MoEyeShape.Happy)
            else -> MoEye(gaze = Offset(0.4f * lean, 0f))
        }
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (flying) MoBrows(MoBrowKind.Raised, lift = 5f) else MoBrows(),
            mouth = when {
                flying -> MoMouth.Oh(8f)
                cheering -> MoMouth.Grin(open = 1f)
                else -> MoMouth.Grin(open = 0.9f)
            },
            leftArm = grip,
            rightArm = grip,
            armsInFront = true,
            // He sits on the sled's deck, not on the snow, and his feet hang down its front.
            legs = MoLegs(MoLeg(lift = -DECK, swing = 8f), MoLeg(lift = -DECK, swing = 8f), sitting = true),
            // The flame is pressed flat by the speed and only lets go while he is off the ground.
            sway = idle.sway - 6f * lean,
            flare = -22f + 3f * wave(t, 0.12f) + 20f * air,
            squash = -0.05f + 0.05f * air - 0.07f * land,
            tilt = 6f * lean,
            offset = Offset(8f * lean, sledY(lean, air) - DECK),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant, picture: Rect) {
        val lean = lean(t, v)
        val air = air(t, v)
        val (sled, sledDark) = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropTeal to MoPropTealDark, MoPropWood to MoPropWoodDark),
            salt = 2,
        )
        block(Offset(picture.center.x, (414f + picture.bottom) / 2f), picture.width, picture.bottom - 414f, MoPropSnow, corner = 0f)
        oval(Offset(CENTRE, 426f), 118f * (1f - 0.35f * air), 5f, MoPropShadow)
        for (s in listOf(-1f, 1f)) {
            val k = cycle(t, 0.5f)
            val lead = CENTRE + s * (122f + k * 32f)
            limb(Offset(lead - s * 20f, 427f), Offset(lead, 427f), MoPropSteel.copy(alpha = 1f - k), 4f)
        }

        // The sled hops and leans on exactly the numbers his pose does, so he never slides off it.
        withTransform({
            translate(8f * lean, sledY(lean, air))
            rotate(6f * lean, Offset(CENTRE, FEET))
        }) {
            chunk(Offset(CENTRE, 409f), 232f, 26f, sled, sledDark, corner = 10f)
            for (s in listOf(-1f, 1f)) {
                val rx = CENTRE + s * 114f
                curve(Offset(rx, 424f), Offset(rx, 390f), Offset(rx + s * 16f, 384f), MoPropSteel, 12f)
                drawCircle(MoPropSteel, 7f, Offset(rx + s * 16f, 384f))
            }
        }

        val flakes = 9 + v.pick(3, salt = 4)
        repeat(flakes) { index ->
            val out = cycle(t, 1.6f, offset = index.toFloat() / flakes)
            val angle = (-20f + index * 220f / max(flakes - 1, 1)) * PI.toFloat() / 180f
            val at = Offset(CENTRE, 250f) +
                Offset(cos(angle) * (60f + out * out * 70f), -sin(angle) * (60f + out * out * 150f))
            sparkle(at, 5f + 16f * out, 1f, MoPropWhite.copy(alpha = 1f - out * out * out))
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val rope = v.of(listOf(MoPropCream, MoPropYellow, MoPropSand), salt = 5)
        for ((hand, s) in listOf(rig.leftHand to -1f, rig.rightHand to 1f)) {
            // It ends on the rail rather than past it, so the little squash of a landing cannot
            // pull the rope off the sled.
            val tip = Offset(CENTRE + s * 124f, 388f)
            curve(hand, (hand + tip) / 2f + Offset(0f, 14f), tip, rope, 4.5f)
        }
        curve(rig.leftHand, (rig.leftHand + rig.rightHand) / 2f + Offset(0f, 14f), rig.rightHand, rope, 4.5f)
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

// ------------------------------------------------------------------------------------------------

/** Tongue out for one fat snowflake: his eyes cross as it comes, and his own warmth takes it away. */
private object TheFlakeThatGotAway : MoAct("festive_the_flake_that_got_away", MoOccasion.Festive, intensity = MoIntensity.Mild) {
    private fun dir(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun period(v: MoVariant) = v.between(3.8f, 5.0f, salt = 5)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val dir = dir(v)
        val c = cycle(t, period(v), offset = 0.4f)
        val f = (c / 0.72f).coerceIn(0f, 1f)
        val idle = moIdle(t, energy = 0.8f)
        return when {
            // The fall: he tracks it in from the side until both eyes are crossed on his own face.
            c < 0.72f -> idle.copy(
                leftEye = MoEye(gaze = Offset(mix(dir * 0.7f, 0.9f, ease(f)), mix(-1f, 0.5f, f))),
                rightEye = MoEye(gaze = Offset(mix(dir * 0.7f, -0.9f, ease(f)), mix(-1f, 0.5f, f))),
                brows = MoBrows(MoBrowKind.Raised, lift = 3f),
                mouth = MoMouth.TongueOut,
                tilt = dir * 3f * (1f - f),
            )
            c < 0.86f -> idle.copy(
                leftEye = MoEye(iris = 0.5f, gaze = Offset(0.3f, 0.4f)),
                rightEye = MoEye(iris = 0.5f, gaze = Offset(-0.3f, 0.4f)),
                brows = MoBrows(MoBrowKind.Raised, lift = 6f),
                mouth = MoMouth.Oh(5f),
            )
            c < 0.95f -> idle.copy(
                leftEye = MoEye(gaze = Offset(0f, 0.3f), lidDrop = 0.15f),
                rightEye = MoEye(gaze = Offset(0f, 0.3f), lidDrop = 0.15f),
                brows = MoBrows(MoBrowKind.Flat),
                mouth = if (v.chance(50, salt = 4)) MoMouth.Pout else MoMouth.Flat(10f),
            )
            else -> idle.copy(
                leftEye = MoEye(gaze = Offset(dir * 0.7f, -1f)),
                rightEye = MoEye(gaze = Offset(dir * 0.7f, -1f)),
                brows = MoBrows(MoBrowKind.Raised, lift = 3f),
                mouth = MoMouth.TongueOut,
                // He leans in for the next one as he looks up, so the fall starts without a jolt.
                tilt = dir * 3f * ease((c - 0.95f) / 0.05f),
            )
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant, picture: Rect) {
        val count = 4 + v.pick(2, salt = 3)
        repeat(count) { index ->
            val fall = cycle(t, 6f, offset = index.toFloat() / count)
            val x = picture.left + (index + 0.5f) * picture.width / count + sin(fall * 5f + index) * 10f
            val visible = ease(fall / 0.08f) * ease((1f - fall) / 0.08f)
            sparkle(Offset(x, mix(picture.top, FEET, fall)), 5f, 1f, MoPropSnow.copy(alpha = 0.9f * visible))
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant, picture: Rect) {
        val dir = dir(v)
        val c = cycle(t, period(v), offset = 0.4f)
        // The bridge between his eyes is clear on either face when he straightens to catch the flake.
        val landing = Offset(CENTRE + 2f, 304f)
        if (c < 0.72f) {
            val f = c / 0.72f
            // It comes in beside his tip, never across it, and ends on his midline, in the gap
            // between his eyes, where a fat flake can be fat without hiding one.
            val at = Offset(
                landing.x + dir * 66f * (1f - ease(f)) + sin(f * 7f) * 10f * (1f - f),
                mix(picture.top - 20f, landing.y, f),
            )
            flake(at, v.between(15f, 18f, salt = 2) - 4f * f, spin = f * 1.5f, size = ease(f / 0.07f))
            return
        }
        if (c < 0.84f) {
            val k = (c - 0.72f) / 0.12f
            val base = landing
            puff(base + Offset(0f, -18f * k), 7f + 7f * k, MoPropWhite.copy(alpha = 0.8f * (1f - k)))
            puff(base + Offset(-5f, -18f * (k - 0.3f)), 5f + 5f * k, MoPropWhite.copy(alpha = 0.5f * (1f - k)))
        }
    }

    override val sparkles get() = false

    /** Six arms through one centre: a snowflake, plainly, and not one of his own sparks. */
    private fun DrawScope.flake(at: Offset, r: Float, spin: Float, size: Float) {
        val colour = MoPropWhite.copy(alpha = size)
        for (arm in 0..5) {
            val angle = spin + arm * PI.toFloat() / 3f
            val along = Offset(cos(angle), sin(angle))
            limb(at, at + along * (r * size), colour, 3.5f)
            // Two barbs on every arm: six bare strokes through a point are an asterisk.
            val fork = at + along * (r * size * 0.5f)
            for (side in listOf(-1f, 1f)) {
                val barb = angle + side * PI.toFloat() / 4f
                limb(fork, fork + Offset(cos(barb), sin(barb)) * (r * size * 0.36f), colour, 2.5f)
            }
        }
        drawCircle(MoPropWhite, 3.5f * size, at)
    }
}

// ------------------------------------------------------------------------------------------------

/** A gingerbread man his own shape stands beside him, and copies his step half a beat late. */
private object GingerbreadTwin : MoAct("festive_gingerbread_twin", MoOccasion.Festive) {
    /** One beat of the step they share: the two sides, the hop, the lean, and which side he beams on. */
    private class Step(val left: Float, val right: Float, val hop: Float, val lean: Float, val beam: Float)

    private fun stepOf(t: Float, style: Int, s: Float): Step {
        val raw = wave(t + 0.55f, 2.2f)
        val toward = if (s > 0f) max(-raw, 0f) else max(raw, 0f)
        return when (style) {
            1 -> Step(abs(raw), abs(raw), 8f * abs(raw), 0f, toward)
            2 -> Step(0f, 0f, 0f, 5f * raw, toward)
            else -> Step(max(raw, 0f), max(-raw, 0f), 4f * abs(raw), 2f * raw, toward)
        }
    }

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun style(v: MoVariant) = v.pick(3, salt = 4)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val step = stepOf(t, style(v), s)
        val idle = moIdle(t, energy = 1.2f)
        // He beams sideways at his twin on the beat that faces him, which is every second one.
        val eye = if (step.beam > 0.5f) MoEye(shape = MoEyeShape.Happy) else MoEye(gaze = Offset(s * 0.85f, 0.3f))
        fun arm(up: Float) = if (style(v) == 2) MoArm.Down else MoArm(Offset(4f + 24f * up, 36f - 88f * up), bend = 0.1f + 0.2f * up)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            mouth = MoMouth.Grin(open = 0.6f),
            leftArm = arm(step.left),
            rightArm = arm(step.right),
            legs = MoLegs(MoLeg(lift = 3f * step.left, swing = 10f * step.left), MoLeg(lift = 3f * step.right, swing = 10f * step.right)),
            offset = Offset(-s * 14f, -step.hop),
            tilt = step.lean,
            squash = idle.squash + 0.015f * max(step.left, step.right),
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = side(v)
        val x = CENTRE + s * 127f
        // The cookie runs the same beat a fraction late, so he plainly echoes without breaking step.
        val step = stepOf(t - 0.18f, style(v), s)
        val button = v.of(listOf(MoPropRed, MoPropGreen, MoPropPink), salt = 2)
        fun hand(side: Float, up: Float) = Offset(x + side * (20f + 2f * up), 398f - 58f * up)

        floorShadow(x, 32f, MoPropShadow)
        withTransform({
            translate(0f, -step.hop * 0.75f)
            scale(0.88f, 0.88f, Offset(x, FEET))
            rotate(step.lean, Offset(x, 414f))
        }) {
            // Every part is drawn twice: once low in the dark dough as its ledge, then in the light.
            for (pass in 0..1) {
                val colour = if (pass == 0) MoPropWoodDark else MoPropWood
                val dy = if (pass == 0) 3f else 0f
                for (side in listOf(-1f, 1f)) {
                    val lift = if (side < 0f) step.left else step.right
                    val foot = Offset(x + side * (13f + 7f * lift), 414f + dy)
                    limb(Offset(x + side * 11f, 398f + dy), foot, colour, 16f)
                    drawCircle(colour, 10f, foot)
                }
                block(Offset(x, 385f + dy), 44f, 52f, colour, corner = 16f)
                drawCircle(colour, 22f, Offset(x, 340f + dy))
                for (side in listOf(-1f, 1f)) {
                    val up = if (side < 0f) step.left else step.right
                    limb(Offset(x + side * 19f, 372f + dy), hand(side, up) + Offset(0f, dy), colour, 15f)
                    drawCircle(colour, 10f, hand(side, up) + Offset(0f, dy))
                }
            }

            for (side in listOf(-1f, 1f)) {
                val up = if (side < 0f) step.left else step.right
                icing(lerp(Offset(x + side * 19f, 372f), hand(side, up), 0.74f))
                icing(Offset(x + side * (12f + 6f * up), 407f))
            }
            repeat(2 + v.pick(2, salt = 3)) { index -> drawCircle(button, 4f, Offset(x, 372f + index * 13f)) }
            drawCircle(MoPropInk, 3f, Offset(x - 7f, 336f))
            drawCircle(MoPropInk, 3f, Offset(x + 7f, 336f))
            if (v.chance(50, salt = 5)) {
                curve(Offset(x - 9f, 348f), Offset(x, 356f), Offset(x + 9f, 348f), MoPropCream, 3.5f)
            } else {
                oval(Offset(x, 350f), 6f, 5f, MoPropCream)
            }
        }
    }

    /** The wavy line of icing across a wrist or an ankle. */
    private fun DrawScope.icing(at: Offset) =
        polyline(MoPropCream, 3f, listOf(at + Offset(-6f, -2f), at + Offset(0f, 2f), at + Offset(6f, -2f)))

    override val ownsTheFloor get() = true
}

internal val festiveActs1: List<MoAct> = listOf(
    SorryIAmWarm,
    PluggedIntoHimself,
    OneLittlePeek,
    SledHeadOn,
    TheFlakeThatGotAway,
    GingerbreadTwin,
)
