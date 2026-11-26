package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropMoon
import de.nicolasmahn.momentum.core.ui.theme.MoPropNavy
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSnow
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoBrowKind
import de.nicolasmahn.momentum.feature.mo.MoBrows
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Frozen: a freeze paid for yesterday, and nothing has thawed him yet. He holds one pose inside the
 * ice for the whole act, so every pose here is constant, and it is the world that moves: visitors,
 * weather, the sky.
 */

private val AllBodies = MoBodyKind.entries.toSet()

// For an act that is about his eyes. The Husk's are shut, and he looks at nobody.
private val EyesOpen = setOf(MoBodyKind.Tall, MoBodyKind.Low, MoBodyKind.Droplet)
private val frozenBody = MoPose(blinks = false)
private val frozenDroplet = frozenBody.copy(leftArm = coldHug, rightArm = coldHug, armsInFront = true)

internal fun frozenRest(body: MoBodyKind): MoPose = if (body == MoBodyKind.Droplet) frozenDroplet else frozenBody

private val TAU = 2f * PI.toFloat()

private fun MoVariant.side(salt: Int = 1) = if (chance(50, salt)) 1f else -1f

/** The side wall the visitor approaches, in the same coordinates as its feet. */
internal fun Rect.wall(side: Float) = if (side < 0f) left else right

private fun lerp(from: Float, to: Float, by: Float) = from + (to - from) * by

/** Frozen cross-eyed at nothing, until a feather rocks down and hangs exactly where he is looking. */
private object FrozenFeatherBetweenHisEyes : MoAct("frozen_feather_between_his_eyes", MoOccasion.Frozen, EyesOpen, MoIntensity.Mild) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = frozenRest(body).copy(
        leftEye = MoEye(gaze = Offset(0.9f, -0.5f), iris = 0.6f),
        rightEye = MoEye(gaze = Offset(-0.9f, -0.5f), iris = 0.6f),
        brows = MoBrows(MoBrowKind.Raised, lift = 4f),
        mouth = MoMouth.Oh(size = 5f),
    )

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val colour = v.of(listOf(MoPropSnow, MoPropSky, MoPropPink, MoPropCream), salt = 1)
        val dir = v.side(salt = 2)
        val swings = 2 + v.pick(2, salt = 3)
        val second = if (v.chance(35, salt = 4)) 0.75f else 1f
        for (index in 0..1) {
            // fall = 0.6 is the frame the widget shows: whole swings from there keep it dead centre
            // and upright then, so it hangs between his eyes and never lies across one.
            val fall = cycle(t, 6f, offset = 0.6f + index * 0.5f)
            val swing = (fall - 0.6f) * TAU * swings
            // The low flame has the narrowest gap between its eyes.
            val drift = fall + 0.4f * fall * (1f - fall)
            val at = Offset(CENTRE + dir * 5f * sin(swing), lerp(40f, FEET - 28f, drift))
            val alpha = min(1f, fall * 8f) * min(1f, (1f - fall) * 6f)
            feather(at, dir * 12f * sin(swing), if (index == 0) 1f else second, colour.copy(alpha = alpha))
        }
    }

    override val sparkles get() = false
}

private fun DrawScope.feather(at: Offset, degrees: Float, size: Float, colour: Color) {
    translate(at.x, at.y) {
        withTransform({ rotate(degrees, Offset.Zero); scale(size, size, Offset.Zero) }) {
            val vane = Path().apply {
                moveTo(4f, -26f)
                quadraticTo(-11f, -18f, -10f, -3f)
                lineTo(-5f, -6f)
                lineTo(-9f, 1f)
                quadraticTo(-8f, 10f, -2f, 16f)
                quadraticTo(7f, 11f, 9f, 0f)
                lineTo(4f, 3f)
                lineTo(10f, -6f)
                quadraticTo(11f, -16f, 4f, -26f)
                close()
            }
            drawPath(vane, colour)
            curve(Offset(-4f, 24f), Offset(-1f, 5f), Offset(3f, -19f), MoPropGrey.copy(alpha = colour.alpha), 2.4f)
        }
    }
}

/** A polar bear cub licks his block like a lolly until its tongue sticks; he can only stare sideways. */
private object FrozenCubLicksTheIce : MoAct("frozen_cub_licks_the_ice", MoOccasion.Frozen, AllBodies, MoIntensity.Mild) {
    private const val LICK = 0.8f
    private const val STUCK = 1.3f
    private const val SHAKE = 0.6f
    private const val REST = 0.7f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val look = MoEye(gaze = Offset(v.side(), 0.6f), iris = 0.5f)
        return frozenRest(body).copy(
            leftEye = look,
            rightEye = look,
            brows = MoBrows(MoBrowKind.Raised, lift = 2f, skew = 6f),
            mouth = MoMouth.Wavy,
        )
    }

    override fun DrawScope.atIce(t: Float, v: MoVariant, ice: Rect) {
        val s = v.side()
        val wall = ice.wall(s)
        val licks = 2 + v.pick(3, salt = 2)
        val stretch = v.between(10f, 16f, salt = 3)
        val (fur, shade) = v.of(listOf(MoPropSnow to MoPropSilver, MoPropCream to MoPropSand), salt = 4)
        val scarf = if (v.chance(30, salt = 5)) v.of(listOf(MoPropTeal, MoPropPink, MoPropYellow), salt = 6) else null

        // A lick slides the tongue out as the head lifts and back in as it drops, so the tongue is
        // in at the loop's start and end; the last lick sticks at the top of its stroke.
        val licking = (licks - 0.5f) * LICK
        val period = licking + STUCK + SHAKE + REST
        // Half a lick in, so the first frame has the tongue out flat on the ice.
        val p = cycle(t, period, offset = LICK / 2f / period) * period
        var bob = 0f
        var pull = Offset.Zero
        var tongue = 0f
        var tongueWidth = 9f
        var startled = false
        var tilt = 0f
        var earTilt = 0f
        when {
            p < licking -> {
                val stroke = pulse(p.mod(LICK), LICK)
                bob = -10f * stroke
                tongue = stroke
            }
            p < licking + STUCK -> {
                val local = p - licking
                // It tugs mostly upward: the strip beside the widest block has no room to back away.
                val tug = Offset(s * 0.3f, -0.53f) * stretch
                if (local < 1f) {
                    bob = -10f
                    pull = tug * ease(local / 0.9f)
                    tongue = 1f
                    tongueWidth = 9f - 3f * ease(local / 0.9f)
                    startled = true
                } else if (local < 1.15f) {
                    // The pop: the tongue snaps home and the head comes back with it.
                    val snap = ease((local - 1f) / 0.15f)
                    bob = -10f * (1f - snap)
                    pull = tug * (1f - snap)
                    tongue = 1f - snap
                    tongueWidth = 6f + 3f * snap
                    startled = true
                }
            }
            p < licking + STUCK + SHAKE -> {
                val local = p - licking - STUCK
                fun swing(at: Float, span: Float) = 8f * sin(at / SHAKE * 3f * TAU) * sin(PI.toFloat() * (at / span).coerceIn(0f, 1f))
                tilt = swing(local, SHAKE)
                // The ears lag a twelfth of a second and still settle by the end of the shake.
                earTilt = swing(local - 0.08f, SHAKE - 0.08f)
            }
        }

        val head = Offset(wall + s * 15f, 378f + bob) + pull
        val neck = head + Offset(0f, 12f)
        val mouth = head + Offset(-s * 10f, 6f)
        floorShadow(wall + s * 16f, 20f, MoPropShadow)
        oval(Offset(wall + s * 7f, FEET - 4f), 8f, 6f, shade)
        oval(Offset(wall + s * 7f, FEET - 7f), 8f, 6f, fur)
        // He stretches up from his haunches as he pulls, with his feet still on the floor.
        val rump = Offset(wall + s * 21f, FEET - 18f)
        limb(rump + Offset(0f, 3f), neck, shade, 27f)
        limb(rump, neck, fur, 27f)
        oval(rump, 15f, 18f, fur)
        val paw = head + Offset(-s * 11f, 28f)
        limb(paw + Offset(s * 8f, -7f), paw, shade, 8f)
        scarf?.let { block(head + Offset(0f, 16f), 24f, 7f, it, corner = 3.5f) }
        if (tongue > 0.08f) {
            // The tip stays planted on the face even while the cub pulls its head away.
            val tip = Offset(lerp(mouth.x, wall - s * 7f, tongue), lerp(mouth.y, 379f, tongue))
            limb(mouth, tip, MoPropPink, tongueWidth)
        }
        withTransform({ rotate(earTilt, neck) }) {
            for (dx in floatArrayOf(-11f, 11f)) {
                drawCircle(fur, 6f, head + Offset(dx, -13f))
                drawCircle(MoPropPink, 3f, head + Offset(dx, -13f))
            }
        }
        withTransform({ rotate(tilt, neck) }) {
            drawCircle(shade, 17f, head + Offset(0f, 4f))
            drawCircle(fur, 17f, head)
            oval(head + Offset(-s * 14f, 3f), 4.5f, 3.5f, MoPropInk)
            val eye = head + Offset(-s * 5f, -5f)
            if (startled) drawCircle(MoPropInk, 3f, eye)
            else curve(eye + Offset(-5f, 1f), eye + Offset(0f, -5f), eye + Offset(5f, 1f), MoPropInk, 3f)
        }
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** A seal pup has fallen asleep against his block, so holding still suddenly looks like a kindness. */
private object FrozenDoNotWakeThePup : MoAct("frozen_do_not_wake_the_pup", MoOccasion.Frozen, AllBodies, MoIntensity.Mild) {
    private const val BREATH = 3.6f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val look = MoEye(gaze = Offset(v.side() * 0.8f, 0.9f), iris = 0.7f, lidDrop = 0.15f)
        return frozenRest(body).copy(
            leftEye = look,
            rightEye = look,
            brows = MoBrows(MoBrowKind.Worried, lift = 3f),
            mouth = MoMouth.Smile(width = 9f, depth = 5f),
        )
    }

    override fun DrawScope.atIce(t: Float, v: MoVariant, ice: Rect) {
        val s = v.side()
        val wall = ice.wall(s)
        val (fur, shade) = v.of(listOf(MoPropSnow to MoPropSilver, MoPropGrey to MoPropGreyDark, MoPropCream to MoPropSand), salt = 2)
        val bear = v.chance(30, salt = 4)
        // Whole breaths between wriggles, so the breath and the slide close together.
        val period = (2 + v.pick(3, salt = 3)) * BREATH
        val p = cycle(t, period, offset = 0.5f) * period
        val wriggle = if (p < period - 0.5f) 0f else sin(PI.toFloat() * (p - period + 0.5f) / 0.5f)
        val slump = if (p < period - 0.5f) p / (period - 0.5f) else 1f - ease((p - period + 0.5f) / 0.5f)
        val breath = pulse(t, BREATH)
        val rise = cycle(t, BREATH)

        val body = Offset(wall + s * (8f + slump * 2f), FEET - 13f - breath)
        val head = Offset(wall + s * (4f + slump * 2f), FEET - 25f + slump * 3f)
        val nose = head + Offset(-s * 10f, 2f)
        floorShadow(wall + s * 14f, 22f, MoPropShadow)
        if (!bear) {
            val hinge = Offset(wall + s * 24f, FEET - 10f)
            for (fan in floatArrayOf(-22f, 22f)) {
                withTransform({ rotate(s * (fan - 25f * wriggle), hinge) }) { oval(hinge + Offset(s * 6f, 0f), 7f, 3.5f, fur) }
            }
        }
        oval(body + Offset(0f, 4f), 25f, 13f + 2f * breath, shade)
        oval(body, 25f, 13f + 2f * breath, fur)
        if (bear) {
            for (dx in floatArrayOf(-8f, 8f)) {
                // The ear on the block side twitches; the wriggle sets it going.
                val flick = if (dx * s > 0f) 0f else wave(t, 0.1f) * 2f * wriggle
                drawCircle(fur, 6f, head + Offset(dx, -10f + flick))
                drawCircle(MoPropPink, 3f, head + Offset(dx, -10f + flick))
            }
        }
        drawCircle(shade, 13f, head + Offset(0f, 4f))
        drawCircle(fur, 13f, head)
        val eye = head + Offset(-s * 3f, -3f)
        curve(eye + Offset(-4f, 0f), eye + Offset(0f, 4f), eye + Offset(4f, 0f), MoPropInk, 2.5f)
        drawCircle(MoPropInk, 2.5f, nose)
        if (!bear) {
            withTransform({ rotate(wave(t, 0.1f) * 3f * wriggle, nose) }) {
                for (dy in floatArrayOf(-3f, 1f, 5f)) limb(nose, nose + Offset(-s * 8f, dy), MoPropGreyDark, 1.8f)
            }
        }
        puff(nose + Offset(-s * 3f, -7f - rise * 28f), 4f + rise * 4f, MoPropWhite.copy(alpha = 0.7f * (1f - rise)))
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** A penguin sweeps the ice ahead of him and the floor slides past: a curling stone, mid-whee. */
private object FrozenCurlingStone : MoAct("frozen_curling_stone", MoOccasion.Frozen, AllBodies) {
    private const val BURST = 3.2f
    private const val SWEEPING = 2.2f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = v.side()
        return if (body == MoBodyKind.Droplet) {
            frozenRest(body).copy(
                leftEye = MoEye(gaze = Offset(s * 0.8f, 0.3f), iris = 0.5f),
                rightEye = MoEye(gaze = Offset(s * 0.8f, 0.3f), iris = 0.5f),
                brows = MoBrows(MoBrowKind.Worried, lift = 3f),
                mouth = MoMouth.Oh(size = 7f),
            )
        } else {
            frozenRest(body).copy(
                leftEye = MoEye(gaze = Offset(s * 0.8f, 0.1f), scale = 1.05f),
                rightEye = MoEye(gaze = Offset(s * 0.8f, 0.1f), scale = 1.05f),
                brows = MoBrows(MoBrowKind.Raised, lift = 4f),
                mouth = MoMouth.Grin(open = 0.7f),
            )
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = v.side()
        val streaks = 5 + v.pick(3, salt = 4)
        // Far snow humps at a quarter of the floor's speed, so the ride has depth.
        for (index in 0..1) {
            val position = (index * 0.5f - s * cycle(t, BURST * 2f)).mod(1f)
            puff(Offset(-30f + position * 290f, FEET - 5f), 11f, MoPropSnow.copy(alpha = 0.3f * sin(PI.toFloat() * position)))
        }
        for (index in 0 until streaks) {
            val position = (index / streaks.toFloat() - s * cycle(t, BURST / 2f)).mod(1f)
            val x = -28f + position * 286f
            val y = FEET - 3f + (index % 3) * 3.5f
            limb(Offset(x - 13f, y), Offset(x + 13f, y), MoPropSilver.copy(alpha = 0.55f * sin(PI.toFloat() * position)), 4f)
        }
    }

    override fun DrawScope.atIce(t: Float, v: MoVariant, ice: Rect) {
        val s = v.side()
        val wall = ice.wall(s)
        val (broom, broomLedge) = v.of(listOf(MoPropRed to MoPropRedDark, MoPropYellow to MoPropYellowDark, MoPropTeal to MoPropTealDark), salt = 2)
        val stroke = v.between(0.32f, 0.48f, salt = 3)
        val belly = if (v.chance(50, salt = 5)) MoPropWhite else MoPropCream

        val burst = cycle(t, BURST, offset = 0.6f / BURST) * BURST
        val working = if (burst < SWEEPING) ease(burst / 0.3f) * ease((SWEEPING - burst) / 0.3f) else 0f
        val sweep = cos(burst / stroke * TAU) * working
        // Between bursts it glances back at its stone.
        val glance = 1.5f * (1f - working)

        val x = wall + s * 23f
        val head = Offset(wall + s * (sweep * 9f - 4f), FEET - 5f)
        val flipper = Offset(x - s * 13f, FEET - 26f)
        floorShadow(x, 14f, MoPropShadow)
        chunk(head, 18f, 8f, broom, broomLedge, corner = 4f, drop = 3f)
        limb(flipper, head + Offset(0f, -4f), MoPropWood, 4f)
        for (index in 0..1) {
            val fly = cycle(t, 0.4f, offset = index * 0.5f)
            drawCircle(MoPropSnow.copy(alpha = (1f - fly) * working), 2.5f, head + Offset(-s * (4f + fly * 14f), -4f - fly * 16f))
        }
        withTransform({ rotate(-s * 8f, Offset(x, FEET)) }) {
            for ((index, dx) in floatArrayOf(-5f, 5f).withIndex()) {
                val lift = 5f * max(0f, wave(t + index * 0.16f, 0.32f))
                oval(Offset(x + dx, FEET - 2f - lift), 7f, 4f, MoPropOrange)
            }
            block(Offset(x, FEET - 24f), 24f, 40f, MoPropNavy, corner = 12f)
            oval(Offset(x - s * 2f, FEET - 20f), 8f, 14f, belly)
            val eye = Offset(x - s * 6f, FEET - 38f)
            drawCircle(MoPropWhite, 2.5f, eye)
            drawCircle(MoPropInk, 1.5f, eye + Offset(-s * glance, 0f))
            triangle(MoPropOrange, Offset(x - s * 12f, FEET - 37f), Offset(x - s * 20f, FEET - 34f), Offset(x - s * 12f, FEET - 31f))
        }
        limb(Offset(x - s * 8f, FEET - 30f), flipper, MoPropNavy, 6f)
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

private fun DrawScope.triangle(colour: Color, a: Offset, b: Offset, c: Offset) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }
    drawPath(path, colour)
}

/** An owl chick stares him down and loses every round: he cannot blink, and his blank face never moves. */
private object FrozenStaringContest : MoAct("frozen_staring_contest", MoOccasion.Frozen, EyesOpen, MoIntensity.Mild) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val look = MoEye(gaze = Offset(v.side(), 0.5f))
        return frozenRest(body).copy(
            leftEye = look,
            rightEye = look,
            brows = MoBrows(MoBrowKind.Flat),
            mouth = MoMouth.Flat(width = 12f),
        )
    }

    override fun DrawScope.atIce(t: Float, v: MoVariant, ice: Rect) {
        val s = v.side()
        val wall = ice.wall(s)
        val hold = v.between(3f, 5f, salt = 2)
        val forward = v.chance(50, salt = 3)
        val (fur, shade) = v.of(listOf(MoPropSnow to MoPropSilver, MoPropCream to MoPropSand, MoPropGrey to MoPropGreyDark), salt = 4)
        val penguin = v.chance(30, salt = 5)

        val period = hold + 3f
        val p = cycle(t, period) * period
        // The strip beside the widest block is narrow: these leans keep the eyes out of the ice
        // one way and inside the view the other.
        val leanIn = -s * 10f
        val leanOut = if (forward) -s * 16f else s * 10f
        var lean = leanIn
        var squash = 0f
        var open = 1f
        var beaten = false
        var jitter = 0f
        when {
            p < hold -> jitter = wave(t, 0.09f) * 1.5f * ease((p - hold + 1.4f) / 1.4f)
            p < hold + 0.3f -> open = 1f - 0.9f * ease((p - hold) / 0.3f)
            p < hold + 1.5f -> {
                val give = ease((p - hold - 0.3f) / 0.4f)
                lean = lerp(leanIn, leanOut, give)
                squash = 0.08f * give
                beaten = true
            }
            p < hold + 2.1f -> {
                val local = p - hold - 1.5f
                open = 0.1f + 0.9f * ease(local / 0.15f)
                lean = leanOut + 6f * wave(local, 0.15f) * sin(PI.toFloat() * local / 0.6f)
                squash = 0.08f
            }
            else -> {
                val back = ease((p - hold - 2.1f) / 0.9f)
                lean = lerp(leanOut, leanIn, back)
                squash = 0.08f * (1f - back)
            }
        }
        val sink = if (forward) 4f * squash / 0.08f else 0f

        val x = wall + s * 13f
        val foot = Offset(x, FEET)
        floorShadow(x, 18f, MoPropShadow)
        withTransform({ rotate(lean, foot); scale(1f + squash * 0.5f, 1f - squash, foot) }) {
            for (dx in floatArrayOf(-6f, 6f)) oval(Offset(x + dx, FEET - 2f), 5f, 3f, MoPropOrange)
            oval(Offset(x, FEET - 19f), 15f, 23f, if (penguin) MoPropGreyDark else shade)
            oval(Offset(x, FEET - 23f), 15f, 23f, if (penguin) MoPropGrey else fur)
            if (penguin) {
                oval(Offset(x, FEET - 15f), 9f, 12f, MoPropWhite)
            } else {
                for ((dx, dy) in listOf(-6f to -14f, 5f to -11f, 0f to -19f)) oval(Offset(x + dx, FEET + dy), 3f, 1.5f, MoPropGrey)
            }
            val radius = if (penguin) 6f else 8f
            for (dx in floatArrayOf(-7f, 7f)) {
                val eye = Offset(x + dx, FEET - 32f + sink)
                if (beaten) {
                    curve(eye + Offset(-5f, 0f), eye + Offset(0f, 5f), eye + Offset(5f, 0f), MoPropInk, 3f)
                } else {
                    withTransform({ scale(1f, open, eye) }) {
                        drawCircle(MoPropYellow, radius, eye)
                        drawCircle(MoPropInk, 3.5f, eye + Offset(-s * 2.5f + jitter, -1.5f))
                    }
                }
            }
            triangle(MoPropOrange, Offset(x - 3f, FEET - 26f + sink), Offset(x + 3f, FEET - 26f + sink), Offset(x, FEET - 21f + sink))
        }
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** Northern lights ripple behind him; if you have to be stuck somewhere, at least the view is good. */
private object FrozenNorthernLights : MoAct("frozen_northern_lights", MoOccasion.Frozen, AllBodies, MoIntensity.Mild) {
    private val places = listOf(Offset(-28f, 34f), Offset(40f, 12f), Offset(196f, 22f), Offset(258f, 58f), Offset(150f, 6f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val look = MoEye(gaze = Offset(v.side(salt = 5) * 0.4f, -1f), iris = 0.82f, scale = 1.05f)
        return frozenRest(body).copy(
            leftEye = look,
            rightEye = look,
            brows = MoBrows(MoBrowKind.Raised, lift = 5f),
            mouth = MoMouth.Oh(size = 5f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val (first, second) = v.of(listOf(MoPropTeal to MoPropGreen, MoPropPurple to MoPropPink, MoPropMint to MoPropSky), salt = 1)
        val drift = v.side(salt = 3)
        ribbon(t, base = 16f, thickness = 26f, k = 0f, drift = drift, colour = first)
        ribbon(t, base = 46f, thickness = 22f, k = 2.1f, drift = -drift, colour = second)
        if (v.chance(50, salt = 2)) ribbon(t, base = 74f, thickness = 16f, k = 4f, drift = drift, colour = first.copy(alpha = 0.6f))
        val skipped = if (v.chance(50, salt = 6)) -1 else v.pick(places.size, salt = 4)
        places.forEachIndexed { index, place ->
            if (index == skipped) return@forEachIndexed
            val period = floatArrayOf(3f, 4f, 6f)[index % 3]
            sparkle(place, 6f, 0.6f + 0.4f * pulse(t + index, period), MoPropMoon)
        }
    }

    /** One flat band that waves, swells and narrows, and tapers to a point at both ends of the sky. */
    private fun DrawScope.ribbon(t: Float, base: Float, thickness: Float, k: Float, drift: Float, colour: Color) {
        fun edge(x: Float, phase: Float) =
            base + 9f * sin(x / 40f + drift * TAU * t / 6f + k + phase) + 4f * sin(x / 17f - drift * TAU * t / 12f + phase)
        fun taper(x: Float) = ease(min(x + 45f, 275f - x) / 70f)
        val xs = List(21) { -45f + it * 16f }
        val path = Path().apply {
            moveTo(xs[0], edge(xs[0], 0f))
            xs.drop(1).forEach { x -> lineTo(x, edge(x, 0f)) }
            xs.reversed().forEach { x -> lineTo(x, edge(x, 0.9f * taper(x)) + thickness * taper(x)) }
            close()
        }
        drawPath(path, colour.copy(alpha = colour.alpha * 0.85f))
    }

    override val sparkles get() = false
}

internal val frozenActs1: List<MoAct> = listOf(
    FrozenFeatherBetweenHisEyes,
    FrozenCubLicksTheIce,
    FrozenDoNotWakeThePup,
    FrozenCurlingStone,
    FrozenStaringContest,
    FrozenNorthernLights,
)
