package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
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
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.mitten
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.smoke
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * New Year, part one: a party popper he does not quite trust, a midnight he is fighting to stay up
 * for, a sparkler he relights from his own tip, and a lucky pig that has come to see him.
 */

private const val TURN = 2f * PI.toFloat()

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm =
    MoArm(mix(from.reach, to.reach, amount), mix(from.bend, to.bend, amount))

/** 0 before [from], eased up to 1 at [to], and 1 after it. */
private fun span(x: Float, from: Float, to: Float): Float = ease((x - from) / (to - from))

/** A sine bump from 0 up to 1 and back between [from] and [to], and 0 outside them. */
private fun bump(x: Float, from: Float, to: Float): Float {
    val q = (x - from) / (to - from)
    return if (q <= 0f || q >= 1f) 0f else sin(q * PI.toFloat())
}

private fun polar(angle: Float, radius: Float): Offset = Offset(cos(angle) * radius, sin(angle) * radius)

/** He holds his party popper at arm's length and braces; it pops, he laughs, and the dread creeps back. */
private object NewYearBracingForThePopper : MoAct("newyear_bracing_for_the_popper", MoOccasion.NewYear, setOf(MoBodyKind.Tall)) {
    private val cones = listOf(
        MoPropTeal to MoPropTealDark,
        MoPropPink to MoPropPinkDark,
        MoPropBlue to MoPropBlueDark,
        MoPropPurple to MoPropPurpleDark,
    )

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** The pop is at 0; the first frame sits a third of the way into the brace, confetti still falling. */
    private fun beat(t: Float, v: MoVariant) = cycle(t, v.between(3.8f, 4.8f, salt = 4), offset = 0.6f)

    private fun dread(c: Float) = span(c, 0.4f, 0.9f)

    /** Where the free mitten ends up: on his cheek just under the eye, never over it. */
    private val shielding = MoArm(Offset(-30f, -12f), bend = 0.5f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val c = beat(t, v)
        val dread = dread(c)
        val jolt = bump(c, 0f, 0.08f)
        val laugh = pulse(t, 0.45f)
        val bracing = c >= 0.35f
        // The lean he had at the pop eases away while he laughs and comes back with the dread.
        val away = if (bracing) dread else 1f - span(c, 0f, 0.35f)
        val lean = if (bracing) 2f * span(c, 0.35f, 0.4f) + 6f * dread else 8f * away
        val hold = if (bracing) dread else 1f - span(c, 0.08f, 0.3f)
        val shield = if (bracing) dread else 1f - span(c, 0f, 0.08f)
        val holding = MoArm(Offset(28f, -34f + 3f * hold - 8f * jolt), bend = 0.12f)
        val free = mix(MoArm.Down, shielding, shield)
        val atPopper = Offset(s * 0.9f, -0.3f)
        val gaze = mix(Offset.Zero, atPopper, span(c, 0.35f, 0.4f))
        val near = when {
            c < 0.08f -> MoEye(iris = 0.5f, scale = 1.08f)
            !bracing -> MoEye(shape = MoEyeShape.Happy)
            else -> MoEye(open = 1f - 0.9f * dread, gaze = gaze)
        }
        val far = when {
            c < 0.08f -> MoEye(iris = 0.5f, scale = 1.08f)
            !bracing -> MoEye(shape = MoEyeShape.Happy)
            else -> MoEye(iris = 0.76f - 0.26f * dread, gaze = gaze)
        }
        val mouth = when {
            c < 0.08f -> MoMouth.Oh(11f)
            !bracing -> MoMouth.Grin(open = 0.7f + 0.3f * laugh)
            dread < 0.3f -> MoMouth.Smile()
            dread < 0.7f -> MoMouth.Wavy
            else -> MoMouth.Gritted
        }
        val brows = when {
            c < 0.08f -> MoBrows(MoBrowKind.Raised, lift = 6f)
            !bracing -> MoBrows()
            else -> MoBrows(MoBrowKind.Worried, lift = 2f + 4f * dread)
        }
        val squash = if (bracing) {
            0.02f * laugh * (1f - span(c, 0.35f, 0.4f)) - 0.03f * dread
        } else {
            mix(-0.03f, 0.02f * laugh, span(c, 0.05f, 0.2f)) + 0.09f * jolt
        }
        return moIdle(t).copy(
            leftEye = if (s > 0f) far else near,
            rightEye = if (s > 0f) near else far,
            brows = brows,
            mouth = mouth,
            leftArm = if (s > 0f) free else holding,
            rightArm = if (s > 0f) holding else free,
            armsInFront = true,
            legs = MoLegs(MoLeg(lift = 8f * jolt), MoLeg(lift = 8f * jolt)),
            flare = 10f * jolt,
            squash = squash,
            tilt = -s * lean,
            offset = Offset(-s * 6f * away, -14f * jolt),
            shake = wave(t, 0.07f) * 1.4f * dread,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val c = beat(t, v)
        val (cone, coneDark) = v.of(cones, salt = 2)
        val h = if (s > 0f) rig.rightHand else rig.leftHand
        val axis = Offset(s * 0.3f, -0.95f)
        val across = Offset(0.95f, s * 0.3f)
        val apex = h + Offset(0f, 4f)
        val m = h + axis * 58f
        fun rim(u: Float, side: Float) = apex + (m - apex) * u + across * (19f * u * side)
        shape(cone, apex.pair(), rim(1f, 1f).pair(), rim(1f, -1f).pair())
        shape(coneDark, rim(0.12f, 1f).pair(), rim(0.4f, 1f).pair(), rim(0.4f, -1f).pair(), rim(0.12f, -1f).pair())
        for (u in listOf(0.56f, 0.8f)) {
            shape(MoPropWhite, rim(u, 1f).pair(), rim(u + 0.05f, 1f).pair(), rim(u + 0.05f, -1f).pair(), rim(u, -1f).pair())
        }
        withTransform({ rotate(s * 17.5f, m) }) { oval(m, 19f, 6f, MoPropInk) }
        // The kit drew the mitten under the cone; painting it again puts his grip around the apex.
        mitten(rig, h, s)

        if (c < 0.12f) puff(m + axis * 6f, 10f + 40f * c, MoPropCream.copy(alpha = 1f - c / 0.12f))

        if (c >= 0.8f) return
        val colours = listOf(MoPropTeal, MoPropPink, MoPropYellow, MoPropSky).filter { it != cone }
        val count = 8 + v.pick(4, salt = 3)
        for (i in 0 until count) {
            // Each piece has its own height and its own fall, and all are down before the next pop.
            val life = c / (0.6f + 0.05f * ((i * 7) % 5))
            if (life >= 1f) continue
            val height = 95f + 40f * sin(i * 2.1f)
            // The spread runs mostly inward: the cone already sits near the edge of his box.
            val at = Offset(
                m.x + s * (-68f + 70f * i / (count - 1)) * ease(life * 2f) + sin(life * 6f + i) * 6f,
                m.y - height * sin(min(life * 1.6f, 1f) * PI.toFloat() / 2f) + (height + 40f) * life * life,
            )
            val colour = colours[i % colours.size].copy(alpha = 1f - life * life * life)
            when (i % 3) {
                0 -> withTransform({ rotate(life * 540f + i * 40f, at) }) { block(at, 12f, 8f, colour, corner = 2f) }
                1 -> drawCircle(colour, 5f, at)
                else -> curve(at + Offset(-11f, 3f), at + Offset(0f, -10f), at + Offset(11f, 3f), colour, 4.5f)
            }
        }
    }

    private fun Offset.pair() = x to y

    override val sparkles get() = false
}

/** He is nodding off before midnight; a firework bursts behind him, he jolts awake and grins, and drifts off again. */
private object NewYearStayingUpForMidnight : MoAct("newyear_staying_up_for_midnight", MoOccasion.NewYear) {
    private const val PERIOD = 6.4f
    private val colours = listOf(MoPropGold, MoPropPink, MoPropTeal, MoPropSky)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun lean(v: MoVariant) = if (v.chance(50, salt = 4)) 1f else -1f

    /** The burst is at 0; the first frame has him half asleep under its fading embers. */
    private fun beat(t: Float) = cycle(t, PERIOD, offset = 0.62f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val lean = lean(v)
        val c = beat(t)
        val sleep = span(c, 0.35f, 0.9f)
        // He is fully drowsy at the burst, and the jolt shakes it out of him within a tenth of the loop.
        val drowse = if (c < 0.35f) 1f - span(c, 0f, 0.1f) else sleep
        val jolt = bump(c, 0f, 0.06f)
        val watching = span(c, 0.03f, 0.1f) * (1f - span(c, 0.3f, 0.4f))
        val up = Offset(side * 0.6f, -0.9f)
        val low = body == MoBodyKind.Low
        val eye = if (c < 0.06f) {
            MoEye(iris = 0.5f, scale = 1.06f, gaze = up)
        } else {
            MoEye(lidDrop = 0.15f * span(c, 0.3f, 0.4f) + 0.6f * sleep, gaze = mix(up, Offset(0f, 0.4f), sleep))
        }
        val mouth = when {
            c < 0.06f -> MoMouth.Oh(9f)
            c < 0.35f -> if (low) MoMouth.Smile(19f, 17f) else MoMouth.Grin(open = 0.7f)
            sleep < 0.5f -> MoMouth.Smile()
            else -> MoMouth.Oh(5f)
        }
        val idle = moIdle(t, energy = 0.6f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (c < 0.06f) MoBrows(MoBrowKind.Raised, lift = 6f) else MoBrows(),
            mouth = mouth,
            legs = MoLegs(MoLeg(lift = 6f * jolt), MoLeg(lift = 6f * jolt)),
            sway = idle.sway + lean * 14f * drowse,
            flare = -9f * drowse + 4f * pulse(t, 0.8f) * watching,
            squash = -0.02f * drowse + 0.09f * jolt,
            tilt = lean * drowse * (7f + 1.5f * pulse(t, 1.1f)),
            offset = Offset(0f, -(if (low) 6f else 12f) * jolt),
            blinks = c >= 0.06f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = side(v)
        val c = beat(t)
        val colour = v.pick(colours.size, salt = 2)
        val rays = v.of(listOf(8, 10, 12), salt = 3)
        burst(c, Offset(if (side > 0f) 208f else 22f, 72f), 50f, rays, colours[colour])
        if (v.chance(35, salt = 5)) {
            burst(cycle(t, PERIOD, offset = 0.52f), Offset(if (side > 0f) 22f else 208f, 96f), 30f, rays, colours[(colour + 1) % colours.size])
        }
    }

    /** One firework over its cycle [c]: it grows in the first eighth, hangs, then sinks and fades out by 0.8. */
    private fun DrawScope.burst(c: Float, centre: Offset, radius: Float, rays: Int, colour: Color) {
        if (c >= 0.8f) return
        val grow = span(c, 0f, 0.12f)
        val outer = radius * grow
        val inner = outer * 0.55f
        val fade = ((c - 0.3f) / 0.5f).coerceIn(0f, 1f)
        val faded = colour.copy(alpha = 1f - fade * fade)
        val b = centre + Offset(0f, 26f * fade)
        for (i in 0 until rays) {
            val dir = polar(i * TURN / rays - TURN / 4f, 1f)
            limb(b + dir * inner, b + dir * outer, faded, 6f)
            if (c > 0.3f) drawCircle(faded, 3f, b + dir * outer + Offset(0f, 20f * fade))
        }
        for (i in 0 until 4) {
            sparkle(b + polar(TURN / 8f + i * TURN / 4f, outer + 8f), 7f * grow, 1f, MoPropWhite.copy(alpha = 1f - fade * fade))
        }
        if (c < 0.3f) drawCircle(faded, 5f, b)
    }

    override val sparkles get() = false
}

/** His sparkler fizzes out; he pouts, looks up at his own tip, flicks a spark onto the stick, and it is back. */
private object NewYearHisOwnLighter : MoAct("newyear_his_own_lighter", MoOccasion.NewYear, setOf(MoBodyKind.Tall)) {
    private val colours = listOf(MoPropGold, MoPropYellow, MoPropOrange, MoPropSky)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun beat(t: Float, v: MoVariant) = cycle(t, v.between(6.5f, 8f, salt = 5))

    /** How hard the sparkler burns: full, sputtering out, dead, then a flash as it catches. */
    private fun fizz(c: Float) = when {
        c < 0.5f -> 1f
        c < 0.88f -> 1f - span(c, 0.5f, 0.58f)
        else -> 1.2f - 0.2f * span(c, 0.88f, 0.97f)
    }

    private fun flick(c: Float) = ((c - 0.78f) / 0.1f).coerceIn(0f, 1f)

    /** Where the spark he flicked is on its arc from his tip to the stick's end [e]. */
    private fun spark(tip: Offset, e: Offset, q: Float) = mix(tip, e, q) + Offset(0f, -46f * sin(q * PI.toFloat()))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val c = beat(t, v)
        val lift = span(c, 0.72f, 0.8f) * (1f - span(c, 0.92f, 1f))
        val out = span(c, 0.5f, 0.58f) * (1f - span(c, 0.88f, 0.97f))
        val flick = bump(c, 0.78f, 0.88f)
        val hop = if (c in 0.88f..0.97f) abs(sin((c - 0.88f) / 0.09f * TURN)) else 0f
        val holding = MoArm(Offset(18f, -16f - 6f * lift), bend = 0.2f)
        val idleFree = if (v.chance(50, salt = 4)) MoArm.Hip else MoArm(Offset(30f, -40f + 6f * wave(t, 0.9f)), bend = 0.5f)
        val free = mix(idleFree, MoArm.Down, out)
        val atStick = Offset(s * 0.9f, -0.5f)
        val atTip = Offset(0.2f, -1f)
        val eye = when {
            c < 0.58f -> MoEye(gaze = atStick)
            c < 0.72f -> MoEye(lid = -0.3f, gaze = Offset(s * 0.9f, -0.4f))
            c < 0.78f -> MoEye(gaze = atTip)
            c < 0.88f -> MoEye(gaze = mix(atTip, Offset(s * 0.9f, -0.4f), flick(c)))
            c < 0.97f -> MoEye(shape = MoEyeShape.Star)
            else -> MoEye(gaze = atStick)
        }
        val brows = when {
            c < 0.5f -> MoBrows()
            c < 0.58f -> MoBrows(MoBrowKind.Raised, lift = 3f)
            c < 0.72f -> MoBrows(MoBrowKind.Worried, lift = 3f)
            c < 0.78f -> MoBrows(MoBrowKind.Raised, lift = 7f, skew = 4f)
            else -> MoBrows()
        }
        val mouth = when {
            c < 0.5f -> MoMouth.Grin(open = 0.7f)
            c < 0.58f -> MoMouth.Oh(6f)
            c < 0.72f -> MoMouth.Pout
            c < 0.78f -> MoMouth.Smile()
            c < 0.88f -> MoMouth.TongueOut
            c < 0.97f -> MoMouth.Beam
            else -> MoMouth.Grin(open = 0.7f)
        }
        val idle = moIdle(t, energy = 1.1f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = brows,
            mouth = mouth,
            leftArm = if (s > 0f) free else holding,
            rightArm = if (s > 0f) holding else free,
            sway = idle.sway + s * 18f * flick,
            flare = 14f * flick,
            squash = -0.025f * out,
            tilt = s * 2f * wave(t, 1.7f) * (1f - out) + s * 3f * flick,
            offset = Offset(0f, -5f * hop),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val c = beat(t, v)
        val colour = v.of(colours, salt = 2)
        val h = if (s > 0f) rig.rightHand else rig.leftHand
        val axis = Offset(s * 0.04f, -0.999f)
        val e = h + axis * 72f
        limb(h + Offset(0f, 8f), e, MoPropGreyDark, 4f)
        limb(h + axis * 40f, e, MoPropCharcoal, 7f)
        mitten(rig, h, s)

        val f = fizz(c)
        if (f > 0.001f) {
            drawCircle(MoPropWhite, 5f * f, e)
            val count = 4 + v.pick(2, salt = 3)
            for (i in 0 until count) {
                val orbit = (12f + 7f * pulse(t + i * 0.17f, 0.23f)) * f
                sparkle(e + polar(i * TURN / count + t * 1.3f, orbit), (9f + 4f * pulse(t + i * 0.31f, 0.19f)) * f, 1f, colour)
            }
            // The sparks fly along fixed spokes, so the star is as wide when it catches as when it burns.
            val reach = min(f, 1f)
            for (i in 0 until 5) {
                val progress = cycle(t, 0.5f, offset = i / 5f)
                val angle = i * TURN / 5f - TURN / 4f + 0.3f
                val from = (8f + 14f * progress) * reach
                limb(e + polar(angle, from), e + polar(angle, from + 10f * reach), colour.copy(alpha = 1f - progress), 3.5f)
            }
        } else {
            val smokeStrength = span(c, 0.58f, 0.61f) * (1f - span(c, 0.85f, 0.88f))
            smoke(e + Offset(0f, -4f), cycle(t, 1.6f), strength = smokeStrength)
        }

        if (c in 0.78f..0.88f) {
            val q = flick(c)
            val at = spark(rig.tip, e, q)
            limb(spark(rig.tip, e, (q - 0.12f).coerceAtLeast(0f)), at, MoPropGold.copy(alpha = 0.6f), 4f)
            drawCircle(MoPropGold, 5f, at)
        }
    }

    override val sparkles get() = false
}

/** A lucky pig has trotted up with a four-leaf clover and nudges it at him; he smiles down and pats its head. */
private object NewYearTheLuckyPig : MoAct("newyear_the_lucky_pig", MoOccasion.NewYear, intensity = MoIntensity.Mild) {
    private const val PIG_X = 114f
    private const val HEAD_X = 92f

    /** Where his mitten hovers at rest: just above the pig's head, whichever body's shoulder it hangs from. */
    private const val MITTEN_REST_Y = 341f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** Nudge, pat, rest; the first frame is his mitten coming down on the pig's head. */
    private fun beat(t: Float, v: MoVariant) = cycle(t, v.between(2.6f, 3.4f, salt = 4), offset = 0.35f)

    private fun pat(c: Float) = bump(c, 0.25f, 0.55f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val d = side(v)
        val c = beat(t, v)
        val pat = pat(c)
        val near = MoArm(Offset(22f, MITTEN_REST_Y - MoBodies.of(body).shoulderY + 21f * pat), bend = 0.3f)
        val eye = if (pat > 0.5f) MoEye(shape = MoEyeShape.Happy) else MoEye(gaze = Offset(d * 0.8f, 0.9f))
        return moIdle(t, energy = 0.9f).copy(
            leftEye = eye,
            rightEye = eye,
            mouth = if (pat > 0.5f) MoMouth.Grin(open = 0.4f, tongue = false) else MoMouth.Smile(width = 19f, depth = 17f),
            leftArm = if (d > 0f) MoArm.Down else near,
            rightArm = if (d > 0f) near else MoArm.Down,
            armsInFront = true,
            squash = -0.015f * pat,
            offset = Offset(-d * 20f, 0f),
            tilt = d * (3f + 1.5f * pat),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        floorShadow(CENTRE + side(v) * PIG_X, 38f, MoPropShadow)
        val d = side(v)
        val c = beat(t, v)
        val nudge = bump(c, 0f, 0.25f)
        val pat = pat(c)
        val coat = v.pick(3, salt = 2)
        val light = listOf(MoPropPink, MoPropCream, MoPropGold)[coat]
        val dark = listOf(MoPropPinkDark, MoPropSand, MoPropGoldDark)[coat]
        val p = Offset(CENTRE + d * PIG_X, FEET - 30f - 3f * nudge)
        val head = Offset(CENTRE + d * (HEAD_X - 3f * nudge), FEET - 39f - 5f * nudge + 3f * pat)

        block(Offset(p.x - d * 12f, FEET - 7f), 10f, 14f, dark, corner = 5f)
        block(Offset(p.x + d * 20f, FEET - 7f), 10f, 14f, dark, corner = 5f)
        curve(p + Offset(d * 30f, -6f), p + Offset(d * 40f, -18f + wave(t, 0.47f) * 6f), p + Offset(d * 37f, -1f), dark, 4f)
        oval(p + Offset(0f, 4f), 33f, 21f, dark)
        oval(p, 33f, 21f, light)
        if (coat == 1) oval(p + Offset(d * 10f, -8f), 10f, 7f, MoPropPink)
        block(Offset(p.x - d * 18f, FEET - 7.5f), 11f, 15f, light)
        block(Offset(p.x + d * 14f, FEET - 7.5f), 11f, 15f, light)
        if (v.chance(30, salt = 3)) {
            val bug = p + Offset(d * 4f, -20f)
            drawCircle(MoPropRed, 6f, bug)
            drawCircle(MoPropInk, 3f, bug + Offset(-d * 5f, -1f))
            drawCircle(MoPropInk, 1.3f, bug + Offset(d * 1f, -2.5f))
            drawCircle(MoPropInk, 1.3f, bug + Offset(d * 3f, 2f))
        }

        drawCircle(light, 17f, head)
        // The ears flop outward under his hand.
        val flop = 7f * pat
        shape(dark, (head.x - d * 8f) to head.y - 10f, (head.x + d * 2f) to head.y - 12f, (head.x - d * 5f - d * 3f * pat) to head.y - 26f + flop)
        shape(dark, (head.x + d * 5f) to head.y - 10f, (head.x + d * 14f) to head.y - 6f, (head.x + d * 13f + d * 3f * pat) to head.y - 22f + flop)
        val snout = head + Offset(-d * 18f, 4f)
        oval(snout, 9f, 7f, dark)
        drawCircle(MoPropInk, 1.8f, snout + Offset(-d * 3.5f, 0f))
        drawCircle(MoPropInk, 1.8f, snout + Offset(d * 3.5f, 0f))
        if (pat > 0.5f) {
            curve(head + Offset(-d * 10f, -3f), head + Offset(-d * 6f, -8f), head + Offset(-d * 2f, -3f), MoPropInk, 3f)
        } else {
            drawCircle(MoPropInk, 3f, head + Offset(-d * 6f, -4f))
            drawCircle(MoPropWhite, 1f, head + Offset(-d * 7f, -5f))
        }

        val top = snout + Offset(-d * 13f + wave(t, 1.3f) * 2f, -19f)
        limb(snout, top, MoPropGreenDark, 3.5f)
        for (i in 0 until 4) drawCircle(MoPropGreen, 6.5f, top + polar(TURN / 8f + i * TURN / 4f, 7.5f))
        drawCircle(MoPropGreenDark, 3f, top)
    }

    override val ownsTheFloor get() = true
}

internal val newYearActs1: List<MoAct> = listOf(
    NewYearBracingForThePopper,
    NewYearStayingUpForMidnight,
    NewYearHisOwnLighter,
    NewYearTheLuckyPig,
)
