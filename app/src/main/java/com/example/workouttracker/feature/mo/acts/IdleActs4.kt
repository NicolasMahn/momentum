package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropBrown
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGlass
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropInk
import com.example.workouttracker.core.ui.theme.MoPropMint
import com.example.workouttracker.core.ui.theme.MoPropNavy
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropRedDark
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropSnow
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
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
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Idle acts, part four: things he has found to play with.
 */

private const val TURN = 2f * PI.toFloat()

private val TallOnly = setOf(MoBodyKind.Tall)

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

/** This point turned by [degrees] about [pivot], clockwise on screen like a canvas rotation. */
private fun Offset.turned(degrees: Float, pivot: Offset = Offset.Zero): Offset {
    val angle = degrees / 360f * TURN
    val from = this - pivot
    return pivot + Offset(from.x * cos(angle) - from.y * sin(angle), from.x * sin(angle) + from.y * cos(angle))
}

/** He shakes a snow globe, then holds perfectly still and watches the snow settle, a flame in love with snow. */
private object SnowGlobe : MoAct("idle_snow_globe", MoOccasion.Idle, TallOnly, MoIntensity.Mild) {
    private const val LOOP = 6f
    private const val FALL = 5.4f
    private const val RADIUS = 30f

    // The little world inside was drawn for a globe of 26, and grows with it.
    private const val SCENE = RADIUS / 26f

    // Starts 0.9 s into the fall: flakes in the air, his mouth still round.
    private fun local(t: Float) = cycle(t, LOOP, offset = 0.15f) * LOOP

    /** 0 while the snow falls, up to 1 and back over the shake. */
    private fun shake(local: Float) = if (local > FALL) sin((local - FALL) / (LOOP - FALL) * PI.toFloat()) else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val local = local(t)
        val shake = shake(local)
        val jig = wave(local - FALL, 0.15f) * 7f * shake
        // The whole shake wears the shaking face, so the loop hands straight back to the round Oh.
        val shaking = local > FALL
        val watching = MoEye(gaze = Offset(0f, 1f), scale = 1.04f)
        val happy = MoEye(shape = MoEyeShape.Happy)
        // The Oh shrinks until it is as small as the smile it turns into.
        val melt = ease((local - 1.8f) / 1.2f)
        return moIdle(t, energy = 0.4f).copy(
            leftEye = if (shaking) happy else watching,
            rightEye = if (shaking) happy else watching,
            brows = if (!shaking && local < 1.8f) MoBrows(MoBrowKind.Raised, lift = 4f) else MoBrows(),
            mouth = when {
                shaking -> MoMouth.Grin(open = 0.4f, tongue = false)
                local < 1.8f -> MoMouth.Oh(6f - 2.5f * ease(local / 1.8f))
                else -> MoMouth.Smile(width = 8f + 7f * melt, depth = 6f + 6f * melt)
            },
            leftArm = MoArm(Offset(-58f - jig, 56f - abs(jig) * 0.6f), bend = 0.3f),
            rightArm = MoArm(Offset(-58f + jig, 56f - abs(jig) * 0.6f), bend = 0.3f),
            armsInFront = true,
            squash = wave(t, 3.2f) * 0.008f + 0.02f * shake * wave(local - FALL, 0.15f),
            blinks = !shaking,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val local = local(t)
        val base = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropTeal to MoPropTealDark, MoPropPurple to MoPropPurpleDark, MoPropWood to MoPropWoodDark),
            salt = 2,
        )
        val g = (rig.leftHand + rig.rightHand) / 2f + Offset(0f, -20f)
        val glass = Path().apply { addOval(Rect(g, RADIUS)) }

        // A night inside, because white snow would vanish against his belly.
        drawCircle(MoPropNavy, RADIUS, g)
        clipPath(glass) {
            withTransform({ scale(SCENE, SCENE, g) }) {
                oval(g + Offset(0f, 19f), 27f, 9f, MoPropSnow)
                inside(g, v.pick(4, salt = 1))
                flakes(g, local, count = 10 + v.pick(4, salt = 3))
            }
        }
        drawCircle(MoPropGlass.copy(alpha = 0.18f), RADIUS, g)
        curve(g + Offset(-22f, -7f), g + Offset(-20f, -20f), g + Offset(-8f, -23f), MoPropWhite, 3.5f)
        // A pedestal as wide as his mittens are apart, so the silhouette reads as a globe on a stand.
        chunk(g + Offset(0f, 30f), 70f, 18f, base.first, base.second, corner = 8f)
        mitten(rig, rig.leftHand, -1f)
        mitten(rig, rig.rightHand, 1f)
    }

    private fun DrawScope.inside(g: Offset, what: Int) {
        val floor = g.y + 13f
        when (what) {
            0 -> {
                block(Offset(g.x, floor - 2f), 4f, 7f, MoPropWood, corner = 1f)
                shape(MoPropGreen, g.x - 9f to floor - 5f, g.x + 9f to floor - 5f, g.x to floor - 16f)
                shape(MoPropGreen, g.x - 6f to floor - 13f, g.x + 6f to floor - 13f, g.x to floor - 23f)
            }
            1 -> {
                block(Offset(g.x, floor - 5f), 18f, 12f, MoPropRed, corner = 2f)
                shape(MoPropBrown, g.x - 11f to floor - 12f, g.x + 11f to floor - 12f, g.x to floor - 21f)
                block(Offset(g.x + 3f, floor - 5f), 5f, 5f, MoPropYellow, corner = 1f)
            }
            2 -> {
                drawCircle(MoPropWhite, 7f, Offset(g.x, floor - 5f))
                drawCircle(MoPropWhite, 5f, Offset(g.x, floor - 15f))
                limb(Offset(g.x - 4f, floor - 10.5f), Offset(g.x + 4f, floor - 10.5f), MoPropRed, 3f)
                drawCircle(MoPropInk, 1.1f, Offset(g.x - 2f, floor - 16.5f))
                drawCircle(MoPropInk, 1.1f, Offset(g.x + 2f, floor - 16.5f))
                limb(Offset(g.x, floor - 14f), Offset(g.x + 4f, floor - 13.5f), MoPropOrange, 2f)
            }
            else -> {
                limb(Offset(g.x, floor), Offset(g.x, floor - 8f), MoPropWood, 3f)
                sparkle(Offset(g.x, floor - 14f), 10f, 1f, MoPropGold)
            }
        }
    }

    private fun DrawScope.flakes(g: Offset, local: Float, count: Int) {
        for (index in 0 until count) {
            val start = g + Offset(17f * sin(index * 2.4f + 0.5f), -15f + 7f * cos(index * 1.7f))
            val ground = g + Offset(18f * sin(index * 1.3f + 2f), 14f + 2f * cos(index * 2.9f))
            val at = if (local <= FALL) {
                // Each flake takes its own 3 to 4.6 s, so the last one lands just before the next shake.
                val fall = ease(local / (3f + 1.6f * (index * 0.618f).mod(1f)))
                mix(start, ground, fall) + Offset(3.5f * sin(local * 2.2f + index) * 4f * fall * (1f - fall), 0f)
            } else {
                val up = (local - FALL) / (LOOP - FALL)
                val swirl = up * TURN * 1.5f + index
                mix(ground, start, ease(up)) + Offset(cos(swirl), sin(swirl)) * (9f * sin(up * PI.toFloat()))
            }
            drawCircle(MoPropSnow, 2.6f + index % 2 * 0.8f, at)
        }
    }
}

/** He inspects you through a big magnifying glass: one eye enormous behind the lens, looking slowly around. */
private object MagnifyingGlass : MoAct("idle_magnifying_glass", MoOccasion.Idle, TallOnly, MoIntensity.Mild) {
    private const val LENS = 36f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun scan(v: MoVariant) = v.between(4f, 6f, salt = 3)

    // Every 7.5 s he leans in for a closer look. The offset puts the first one at 2.4 s.
    private fun lean(t: Float) = twitch(t, period = 7.5f, beat = 2f, offset = 0.68f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val scan = scan(v)
        val swing = wave(t, scan)
        val lean = lean(t)
        // Cubed, the look lingers on you in the middle before it wanders off again.
        val look = Offset(0.85f * swing * swing * swing, 0.3f * wave(t, 3.1f))
        val big = MoEye(scale = v.between(1.19f, 1.26f, salt = 4) + 0.05f * lean, gaze = look)
        val spotted = cycle(t, scan, offset = 0.3f / scan) * scan < 0.6f
        // Low and outside his body, so the whole handle shows between the hand and the lens.
        val glass = MoArm(Offset(8f + 4f * swing, 44f), bend = 0.7f)
        return moIdle(t, energy = 0.8f).copy(
            leftEye = if (s < 0f) big else MoEye(gaze = look),
            rightEye = if (s > 0f) big else MoEye(gaze = look),
            // The cocked brow is the one over the glass; skew can only raise the right one.
            brows = if (s > 0f) MoBrows(MoBrowKind.Raised, lift = 3f, skew = 10f) else MoBrows(MoBrowKind.Raised, lift = 13f, skew = -10f),
            mouth = if (spotted) MoMouth.Oh(5f) else MoMouth.Smile(12f, 8f),
            leftArm = if (s < 0f) glass else MoArm.Hip,
            rightArm = if (s > 0f) glass else MoArm.Hip,
            armsInFront = true,
            tilt = 3f * s * lean,
            offset = Offset(0f, -3f * lean),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        // Cool colours only: a gold or a red rim is lost against his own face, and the glass is the act.
        val frame = v.of(listOf(MoPropNavy, MoPropTeal, MoPropPurple, MoPropBlue), salt = 2)
        val eye = if (s > 0f) rig.rightEye else rig.leftEye
        val hand = if (s > 0f) rig.rightHand else rig.leftHand
        val c = eye + Offset(s * 1.5f + wave(t, scan(v)), -1.5f)
        val reach = hand - c
        val length = reach.getDistance()
        val along = if (length > 1f) reach / length else Offset(0f, 1f)

        limb(c + along * (LENS + 4f), hand + along * 12f, MoPropWood, 11f)
        limb(c + along * (LENS + 3f), c + along * (LENS + 11f), frame, 14f)
        mitten(rig, hand, s)
        // Barely tinted: the eye behind it has to stay his own crisp brown.
        drawCircle(MoPropGlass.copy(alpha = 0.14f), LENS, c)
        drawCircle(frame, LENS, c, style = Stroke(8f))
        // The glint rides on the rim: the eye behind the lens is white where a glint would be.
        curve(c + Offset(-34.8f, -9.3f), c + Offset(-28.8f, -24.3f), c + Offset(-15.2f, -32.7f), MoPropWhite.copy(alpha = 0.85f), 3f)
    }
}

/** A magician's chain of knotted hankies comes out of his fist, tug after tug, and nobody is more surprised than he is. */
private object EndlessHankies : MoAct("idle_endless_hankies", MoOccasion.Idle, TallOnly, MoIntensity.Medium) {
    private const val TUG = 0.95f
    private const val PULL = 0.5f
    private const val SHRUG = 1.2f
    private const val SPACING = 50f

    /** How many hankies one tug draws out. The colour set has as many, so a tug ends on the picture it began with. */
    private const val PER_TUG = 3

    /** Where along the chain he takes hold: the knot just outside his fist. */
    private const val GRIP = SPACING * 1.15f

    /** The line the chain runs along: out of the fist, past his side, and up into the air beside him. */
    private class Chain(fist: Offset, p: Float, t: Float) {
        private val points: List<Offset>
        private val lengths: FloatArray

        init {
            val control = Offset(CENTRE + p * 95f, fist.y + 2f)
            // It climbs steeply once it is past his side, so the far hankies hang in clear air beside him.
            val end = Offset(CENTRE + p * 138f, fist.y - 150f + 4f * wave(t, 2.3f))
            points = (0..STEPS).map { step ->
                val u = step / STEPS.toFloat()
                fist * ((1f - u) * (1f - u)) + control * (2f * u * (1f - u)) + end * (u * u)
            }
            lengths = FloatArray(points.size)
            for (index in 1 until points.size) lengths[index] = lengths[index - 1] + (points[index] - points[index - 1]).getDistance()
        }

        val total: Float get() = lengths.last()

        fun at(length: Float): Offset {
            val a = length.coerceIn(0f, total)
            val index = (1 until points.size).firstOrNull { lengths[it] >= a } ?: (points.size - 1)
            val span = lengths[index] - lengths[index - 1]
            return mix(points[index - 1], points[index], if (span > 0.001f) (a - lengths[index - 1]) / span else 0f)
        }

        private companion object {
            const val STEPS = 40
        }
    }

    private class Beat(val tug: Int, val pulled: Float, val local: Float, val shrug: Float, val surprise: Float)

    private fun tugs(v: MoVariant) = 2 + v.pick(3, salt = 4)

    private fun puller(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    private fun beat(t: Float, v: MoVariant): Beat {
        val tugs = tugs(v)
        val loop = tugs * TUG + SHRUG
        // Starts mid first tug, with the chain already on its way.
        val at = cycle(t, loop, offset = 0.2f / loop) * loop
        if (at < tugs * TUG) {
            val tug = (at / TUG).toInt().coerceAtMost(tugs - 1)
            val local = at - tug * TUG
            val pulled = ease(local / PULL)
            return Beat(tug, pulled, local, shrug = 0f, surprise = (tug + pulled) / tugs)
        }
        val local = at - tugs * TUG
        return Beat(tugs, 0f, local, shrug = ease(local / 0.3f) * ease((SHRUG - local) / 0.3f), surprise = 1f - ease(local / 0.3f))
    }

    private fun fist(body: MoBodyKind, p: Float) = MoBodies.of(body).let { Offset(CENTRE - p * (it.shoulderHalf - 40f), it.shoulderY + 36f) }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = puller(v)
        val tugs = tugs(v)
        val beat = beat(t, v)
        val chain = Chain(fist(body, p), p, t)
        val shoulder = MoBodies.of(body).let { Offset(CENTRE + p * it.shoulderHalf, it.shoulderY) }

        // He grips the knot nearest the fist, sweeps it out and up, lets go and reaches back for the next.
        val back = if (beat.shrug == 0f && beat.tug < tugs) ((beat.local - PULL) / (TUG - PULL)).coerceIn(0f, 1f) else 0f
        val along = GRIP + SPACING * PER_TUG * (beat.pulled - ease(back))
        val grip = chain.at(along) + Offset(0f, -12f * sin(back * PI.toFloat()))
        val hand = mix(grip, shoulder + Offset(p * 40f, -12f), beat.shrug)
        val pulling = MoArm(Offset((hand.x - shoulder.x) * p, hand.y - shoulder.y), bend = mix(0.3f, 0.6f, beat.shrug))
        val holding = MoArm(Offset(-40f, 36f), bend = 0.4f)

        val q = beat.surprise
        val heave = if (beat.shrug == 0f && beat.local < PULL) sin(beat.local / PULL * PI.toFloat()) else 0f
        val eye = MoEye(iris = 0.76f - 0.26f * q, scale = 1f + 0.05f * q, gaze = Offset(-0.6f * p * q, 0.9f * q))
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (beat.shrug > 0.3f) MoBrows(MoBrowKind.Raised, lift = 5f, skew = 6f) else MoBrows(MoBrowKind.Raised, lift = 7f * q),
            mouth = when {
                // The Oh lingers into the shrug, and the smile then holds to the end, so the loop
                // hands straight from the smile to the grin without an Oh in between.
                beat.tug == tugs -> if (beat.local < 0.15f) MoMouth.Oh(6f) else MoMouth.Smile(10f, 5f)
                beat.tug == tugs - 1 -> MoMouth.Oh(6f)
                else -> MoMouth.Grin(open = 0.5f, tongue = false)
            },
            leftArm = if (p < 0f) pulling else holding,
            rightArm = if (p > 0f) pulling else holding,
            armsInFront = true,
            squash = wave(t, 3.2f) * 0.014f - 0.02f * heave + 0.03f * beat.shrug,
            tilt = 2f * p * heave,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = puller(v)
        val beat = beat(t, v)
        val flags = v.chance(35, salt = 3)
        // Every set is cool: a red or orange hanky would be lost against his own flame.
        val colours = v.of(
            listOf(
                listOf(MoPropTeal, MoPropPurple, MoPropSky),
                listOf(MoPropPink, MoPropGreen, MoPropBlue),
                listOf(MoPropPurple, MoPropMint, MoPropBlue),
            ),
            salt = 1,
        )
        val fist = if (p > 0f) rig.leftHand else rig.rightHand
        val chain = Chain(fist, p, t)
        // Only the last hanky fades, in the air beside him, so the chain never ends anywhere.
        fun fade(along: Float) = 1f - ease((along - (chain.total - 42f)) / 42f)

        if (flags) {
            for (step in 0 until 30) {
                val from = chain.total * step / 30f
                limb(chain.at(from), chain.at(from + chain.total / 30f), MoPropWhite.copy(alpha = fade(from)), 3f)
            }
        }
        val slots = (chain.total / SPACING).toInt() + 1
        // The slots still inside the fist are counted too, so the same hankies are on the chain
        // whether the tug has moved them on by PER_TUG or the shrug has not moved them at all.
        for (slot in -PER_TUG..slots) {
            val front = (slot + PER_TUG * beat.pulled) * SPACING
            val rear = maxOf(front - SPACING * 0.68f, 0f)
            if (front <= 0f || rear >= chain.total) continue
            val from = chain.at(rear)
            val to = chain.at(front)
            val span = (to - from).getDistance()
            if (span < 2f) continue
            val tangent = (to - from) / span
            val across = Offset(-tangent.y, tangent.x).let { if (it.y < 0f) it * -1f else it }
            // One tug moves every hanky on by exactly as many slots as there are colours, so the chain repeats.
            val away = fade(front)
            val colour = colours[Math.floorMod(slot, PER_TUG)].copy(alpha = away)
            val middle = (from + to) / 2f
            // The last one only narrows part of the way as it fades: taken all the way down it would
            // read as a dark shard rather than a hanky going off into the distance.
            // One still coming out of the fist is as narrow as it is short, so it grows out of the
            // mitten as a whole hanky instead of appearing beside it as a bar of full width.
            val taper = (0.35f + 0.65f * away) * (span / (SPACING * 0.68f)).coerceAtMost(1f)
            if (flags) {
                val tip = middle + across * (30f * taper)
                shape(colour, from.x to from.y, to.x to to.y, tip.x to tip.y)
            } else {
                val up = middle - across * (17f * taper)
                val down = middle + across * (17f * taper)
                shape(colour, from.x to from.y, up.x to up.y, to.x to to.y, down.x to down.y)
            }
        }
        if (!flags) {
            for (slot in -PER_TUG..slots) {
                val along = (slot + PER_TUG * beat.pulled) * SPACING + SPACING * 0.15f
                if (along > 0f && along < chain.total) drawCircle(MoPropWhite.copy(alpha = fade(along)), 6f * fade(along), chain.at(along))
            }
        }
        mitten(rig, rig.leftHand, -1f)
        mitten(rig, rig.rightHand, 1f)
    }
}

/** He and the sock puppet on his mitten are deep in conversation, taking turns forever, and neither wins. */
private object SockPuppetChat : MoAct("idle_sock_puppet_chat", MoOccasion.Idle, TallOnly, MoIntensity.Mild) {
    private const val LOOP = 6.4f
    private const val FLAP = 0.3f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    // Starts with the puppet's jaw wide open, half a flap into its turn.
    private fun local(t: Float) = cycle(t, LOOP, offset = 0.15f / LOOP) * LOOP

    /** How open a talking mouth is, [since] seconds into a 2.4 s turn: eight flaps, one of them swallowed for breath. */
    private fun talk(since: Float): Float {
        if (since < 0f || since > 2.4f) return 0f
        val flap = (since / FLAP).toInt()
        return if (flap == 4) 0f else pulse(since, FLAP) * (0.65f + 0.35f * sin(flap * 2.1f))
    }

    private fun shakes(v: MoVariant, local: Float) = if (v.chance(40, salt = 4)) twitch(local - 5f, LOOP, beat = 0.6f) else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val local = local(t)
        val listening = local < 2.4f
        val talking = local in 3.2f..5.6f && shakes(v, local) == 0f
        val answer = talk(local - 3.2f)
        // One slow blink in the pause, as he gathers what to say.
        val lids = 1f - twitch(local - 2.6f, LOOP, beat = 0.3f)
        val eye = MoEye(gaze = Offset(0.9f * s, -0.1f), open = lids)
        val puppet = MoArm(Offset(24f, -48f), bend = 0.6f)
        // The free hand rises into its gesture and settles back, rather than snapping off his hip.
        val raise = ease((local - 3.2f) / 0.35f) * ease((5.6f - local) / 0.35f)
        val free = MoArm(
            mix(MoArm.Hip.reach, Offset(30f, -6f + 8f * wave(local - 3.2f, 0.8f)), raise),
            bend = mix(MoArm.Hip.bend, 0.4f, raise),
        )
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                listening -> MoBrows(MoBrowKind.Raised, lift = 4f)
                shakes(v, local) > 0f -> MoBrows(MoBrowKind.Flat)
                else -> MoBrows()
            },
            mouth = when {
                talking -> MoMouth.Chew(open = answer)
                shakes(v, local) > 0f -> MoMouth.Pout
                else -> MoMouth.Smile(12f, 8f)
            },
            leftArm = if (s < 0f) puppet else free,
            rightArm = if (s > 0f) puppet else free,
            tilt = 2f * s,
            offset = Offset(0f, if (listening) 1.5f * pulse(local, 1.2f) else 0f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val local = local(t)
        val (sock, stripe) = v.of(
            listOf(MoPropGreen to MoPropYellow, MoPropPurple to MoPropPink, MoPropBlue to MoPropWhite, MoPropRed to MoPropCream),
            salt = 1,
        )
        val h = if (s > 0f) rig.rightHand else rig.leftHand
        val jaw = 40f * talk(local)
        // It nods along while he talks, four times, and on some days shakes its head at the end.
        val nod = if (local in 3.2f..5.6f) -11f * pulse(local - 3.2f, 0.6f) * (1f - ease((local - 4.7f) / 0.3f)) else 0f
        val no = shakes(v, local) * wave(local - 5f, 0.2f) * 4f

        // Drawn facing left, for the puppet on his right mitten; mirrored about the hand for the other.
        withTransform({ scale(s, 1f, h) }) {
            block(h + Offset(0f, 5f), 34f, 52f, sock, corner = 14f)
            block(h + Offset(0f, 27f), 38f, 15f, sock, corner = 6f)
            block(h + Offset(0f, 23f), 38f, 4f, stripe, corner = 1f)
            block(h + Offset(0f, 31f), 38f, 4f, stripe, corner = 1f)

            val hinge = h + Offset(12f + no, -26f)
            withTransform({ rotate(nod, hinge) }) {
                val upper = hinge + Offset(-46f, 0f).turned(jaw * 0.35f)
                val lower = hinge + Offset(-44f, 0f).turned(-jaw * 0.65f)
                if (jaw > 0.5f) shape(MoPropInk, hinge.x to hinge.y, upper.x to upper.y, lower.x to lower.y)
                withTransform({ rotate(-jaw * 0.65f, hinge) }) {
                    block(hinge + Offset(-18f, 8f), 52f, 15f, sock, corner = 7f)
                }
                withTransform({ rotate(jaw * 0.35f, hinge) }) {
                    if (v.chance(50, salt = 3)) {
                        for (strand in -1..1) limb(hinge + Offset(1f, -14f), hinge + Offset(3f + strand * 8f, -29f + abs(strand) * 3f), MoPropYellow, 4f)
                    }
                    block(hinge + Offset(-20f, -9f), 56f, 18f, sock, corner = 9f)
                    for (x in listOf(-10f, -25f)) {
                        drawCircle(MoPropWhite, 7f, hinge + Offset(x, -19f))
                        drawCircle(MoPropInk, 3f, hinge + Offset(x - 2.2f, -18.5f))
                    }
                }
            }
        }
    }
}

/** He balances on a plank on a roller, tongue out, and at the end of a slide it nearly goes wrong. He never falls. */
private object RolaBola : MoAct("idle_rola_bola", MoOccasion.Idle, MoBodies.Burning, MoIntensity.Medium) {
    private const val PERIOD = 3f
    private const val ROLLER = 13f

    // The plank's underside rests on the roller: its top, plus the plank and its ledge under his feet.
    private const val RAISED = 2f * ROLLER + 2f + 14f

    private fun slide(v: MoVariant) = v.between(10f, 14f, salt = 4)

    private fun scary(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val w = wave(t, PERIOD)
        val leaving = w * cos(t / PERIOD * TURN) < 0f
        val thisSide = w * scary(v) > 0f
        // Only one end of the slide is the scary one; at the other he turns round calmly and rests.
        val panic = ease((abs(w) - 0.78f) / 0.17f) * if (thisSide) 1f else 0.3f
        val relief = if (leaving && thisSide) sin(((0.95f - abs(w)) / 0.8f).coerceIn(0f, 1f) * PI.toFloat()) else 0f
        val spin = cycle(t, 0.45f) * TURN

        fun arm(side: Float): MoArm {
            val steady = Offset(32f - 6f * abs(w), -4f + 8f * w * side)
            val turn = spin + if (side > 0f) PI.toFloat() else 0f
            // Reaching no further than this keeps the outer mitten inside the view at the far end of
            // the slide, where the tilt and the squash both push it outward too.
            val windmill = Offset(9f + 15f * cos(turn), -16f - 30f * sin(turn))
            return MoArm(mix(steady, windmill, panic), bend = 0.15f, inFront = true)
        }
        val eye = MoEye(iris = mix(0.76f, 0.5f, panic), gaze = Offset(0.3f * w, 0.8f) * (1f - panic))
        return moIdle(t, energy = 1.1f).let { idle ->
            idle.copy(
                leftEye = eye,
                rightEye = eye,
                brows = if (panic > 0.5f) MoBrows(MoBrowKind.Raised, lift = 6f) else MoBrows(),
                mouth = when {
                    panic > 0.5f -> MoMouth.Oh(7f)
                    relief > 0.15f -> if (body == MoBodyKind.Tall) MoMouth.Smile(14f, 11f) else MoMouth.Smile(10f, 6f)
                    else -> MoMouth.TongueOut
                },
                leftArm = arm(-1f),
                rightArm = arm(1f),
                // His flame trails behind the slide, and the plank tips a moment after he gets there.
                sway = idle.sway - 10f * w,
                tilt = (5f + 3f * panic) * wave(t - 0.2f, PERIOD),
                offset = Offset(slide(v) * w, -RAISED),
                squash = idle.squash - 0.015f * abs(w) - 0.03f * relief,
            )
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val (face, ledge) = v.of(listOf(MoPropBlue to MoPropBlueDark, MoPropPurple to MoPropPurpleDark, MoPropYellow to MoPropYellowDark), salt = 2)
        // The roller travels half as far as the plank on top of it, and turns by exactly that distance.
        val travel = slide(v) / 2f * wave(t, PERIOD)
        val centre = Offset(CENTRE + travel, FEET - ROLLER - 2f)
        val spoke = Offset(cos(travel / ROLLER), sin(travel / ROLLER)) * (ROLLER - 3f)
        floorShadow(centre.x, 62f, MoPropShadow)
        drawCircle(ledge, ROLLER, centre + Offset(0f, 2f))
        drawCircle(face, ROLLER, centre)
        limb(centre - spoke, centre + spoke, MoPropWhite, 4f)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (face, ledge) = v.of(listOf(MoPropWood to MoPropWoodDark, MoPropTeal to MoPropTealDark, MoPropRed to MoPropRedDark), salt = 1)
        val plank = Offset(CENTRE, FEET + 7f)
        for (side in listOf(-1f, 1f)) block(plank + Offset(side * 80f, -8f), 9f, 9f, ledge, corner = 3f)
        chunk(plank, 170f, 10f, face, ledge, corner = 5f, drop = 4f)
    }

    override val ownsTheFloor get() = true
}

/** He holds a balloon out at arm's length and leans his whole flame away from it, to keep it safe. */
private object CarefulWithTheBalloon : MoAct("idle_careful_with_the_balloon", MoOccasion.Idle, TallOnly, MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    // A breeze every 5 s pushes the balloon toward him. The offset brings the first at 1.5 s.
    private fun breeze(t: Float) = twitch(t, period = 5f, beat = 1.6f, offset = 0.7f)

    private fun tilt(t: Float, s: Float) = -s * (5f + 3f * breeze(t))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val breeze = breeze(t)
        // A fond look up at it for two seconds, then back to you.
        val glance = cycle(t, 3.5f, offset = 0.1f) * 3.5f
        val up = maxOf(ease(glance / 0.3f) * ease((2f - glance) / 0.3f), ease(breeze * 3f))
        val eye = MoEye(iris = mix(0.76f, 0.6f, breeze), gaze = Offset(0.75f * s, -0.9f) * up)
        val string = MoArm(Offset(40f, -40f), bend = 0.2f)
        return moIdle(t).let { idle ->
            idle.copy(
                leftEye = eye,
                rightEye = eye,
                brows = if (breeze > 0.2f) MoBrows(MoBrowKind.Worried, lift = 3f) else MoBrows(),
                mouth = if (breeze > 0.2f) MoMouth.Oh(5f) else MoMouth.Smile(14f, 11f),
                leftArm = if (s < 0f) string else MoArm.Hip,
                rightArm = if (s > 0f) string else MoArm.Hip,
                sway = idle.sway * 0.4f - s * (10f + 8f * breeze),
                tilt = tilt(t, s),
                // Up on his toes, giving way.
                offset = Offset(0f, -5f * breeze),
                squash = idle.squash + 0.03f * breeze,
            )
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val breeze = breeze(t)
        val colours = listOf(MoPropRed, MoPropSky, MoPropPink, MoPropYellow, MoPropPurple)
        val first = v.pick(colours.size, salt = 1)
        val kind = v.pick(3, salt = 2)
        val hand = if (s > 0f) rig.rightHand else rig.leftHand
        // He leans, the balloon does not: its place is turned back so that it rises straight above his hand.
        val lean = tilt(t, s)
        fun above(x: Float, y: Float) = hand + Offset(x, y).turned(-lean)

        // Drawn from the last to the first, so the one he cares about most is in front.
        for (index in (0 until 1 + v.pick(3, salt = 4)).reversed()) {
            val drift = Offset(-s * (4f + 14f * breeze) + 3f * wave(t + index * 0.6f, 2.3f), 4f * wave(t + index * 0.9f, 3.1f))
            val place = when (index) {
                0 -> Offset(0f, -120f)
                // Kept close in: an outer balloon this high clears the edge of the view by very little.
                1 -> Offset(s * 10f, -92f)
                else -> Offset(-s * 22f, -84f)
            }
            val size = if (index == 0) 1f else 0.85f
            val centre = above(place.x + drift.x, place.y + drift.y)
            val knot = above(place.x + drift.x, place.y + drift.y + 31f * size)
            val slack = above((place.x + drift.x) / 2f + 10f * wave(t + index * 0.6f, 2.3f), place.y / 2f)
            curve(hand, slack, knot, MoPropWhite, 2.5f)
            withTransform({ rotate(-lean, centre) }) {
                balloon(centre, size, kind, colours[(first + index * 2) % colours.size])
            }
        }
    }

    private fun DrawScope.balloon(c: Offset, size: Float, kind: Int, colour: Color) {
        val below = 30f * size
        shape(colour, c.x - 4f to c.y + below + 4f, c.x + 4f to c.y + below + 4f, c.x to c.y + below - 4f)
        when (kind) {
            0 -> oval(c, 24f * size, 29f * size, colour)
            1 -> {
                drawCircle(colour, 14f * size, c + Offset(-11f, -9f) * size)
                drawCircle(colour, 14f * size, c + Offset(11f, -9f) * size)
                shape(colour, c.x - 22f * size to c.y - 1f * size, c.x + 22f * size to c.y - 1f * size, c.x to c.y + below - 3f)
            }
            else -> {
                // A foil star, plump and soft at its points, so that it is a balloon before it is
                // a star. A thin star on a string read as a wand, and the shine lay beside it.
                val star = Path().apply {
                    for (point in 0 until 10) {
                        val reach = (if (point % 2 == 0) 25f else 15f) * size
                        val angle = -PI.toFloat() / 2f + point * PI.toFloat() / 5f
                        val at = c + Offset(cos(angle), sin(angle)) * reach
                        if (point == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
                    }
                    close()
                }
                drawPath(star, colour)
                drawPath(star, colour, style = Stroke(width = 8f * size, join = StrokeJoin.Round))
                limb(c, c + Offset(0f, below - 2f), colour, 5f)
            }
        }
        // On the body of whichever shape it is: a star's is its middle.
        val shine = if (kind == 2) Offset(-6f, -5f) else Offset(-9f, -13f)
        val gleam = if (kind == 2) 0.75f else 1f
        oval(c + shine * size, 4f * size * gleam, 7f * size * gleam, MoPropWhite.copy(alpha = 0.6f))
    }
}

internal val idleActs4: List<MoAct> = listOf(
    SnowGlobe,
    MagnifyingGlass,
    EndlessHankies,
    SockPuppetChat,
    RolaBola,
    CarefulWithTheBalloon,
)
