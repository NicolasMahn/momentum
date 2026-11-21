package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropCharcoal
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropGrass
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropGreenDark
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropLeaf
import com.example.workouttracker.core.ui.theme.MoPropMoon
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
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropSteel
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoPropYellowDark
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.ABOVE_THE_PICTURE
import com.example.workouttracker.feature.mo.BELLY_FLAME
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
import com.example.workouttracker.feature.mo.OUTER_FLAME
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.body
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.grippingMitten
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.quadTo
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.threadFromAbove
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/*
 * Proud, second helping: the small and middling kinds of pride. Every act runs on one loop cut
 * into phases, and everything that changes between phases rides on a ramp, so nothing jumps.
 */

/** 0 before [from], 1 after [to], eased in between: the edge of a phase. */
private fun ramp(c: Float, from: Float, to: Float): Float = ease((c - from) / max(to - from, 0.001f))

/** 1 inside [from]..[to] and 0 outside, with eased edges [edge] wide. */
private fun during(c: Float, from: Float, to: Float, edge: Float = 0.04f): Float =
    ramp(c, from, from + edge) * (1f - ramp(c, to - edge, to))

private fun mix(a: Float, b: Float, k: Float): Float = a + (b - a) * k

private fun mix(a: MoArm, b: MoArm, k: Float): MoArm = MoArm(lerp(a.reach, b.reach, k), mix(a.bend, b.bend, k))

private fun quad(from: Offset, control: Offset, to: Offset, u: Float): Offset =
    lerp(lerp(from, control, u), lerp(control, to, u), u)

/** Hands on the hips. [MoArm.Hip] tucks the mitten inside his silhouette, where no pose can be read. */
private val AKIMBO = MoArm(Offset(2f, 32f), bend = 0.9f)

/** A spotlight finds him: he tests it with a step to the side, it follows, and he basks in it. */
private object IsThatLightForMe : MoAct("proud_is_that_light_for_me", MoOccasion.Proud, intensity = MoIntensity.Mild) {
    private const val LOOP = 9f
    private const val STEP = 34f

    private fun c(t: Float) = cycle(t, LOOP, offset = 0.6f)

    /** How far out of his spot he is, from 0 to 1: a step out and a step back. */
    private fun stepOut(c: Float) = ramp(c, 0.12f, 0.25f) * (1f - ramp(c, 0.25f, 0.4f))

    private fun first(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    private fun lampSide(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = c(t)
        val out = stepOut(c)
        // He only shuffles while he is actually moving, which is where the step's curve is steep.
        val moving = during(c, 0.12f, 0.4f) * (1f - during(c, 0.235f, 0.265f, edge = 0.012f))
        val lamp = Offset(lampSide(v) * 0.6f, -0.9f)
        val pool = Offset(first(v) * 0.3f * out, 0.8f)
        val gaze = when {
            c < 0.4f -> lerp(lamp, pool, ramp(c, 0.12f, 0.16f))
            c < 0.5f -> lerp(pool, lamp, ramp(c, 0.4f, 0.43f))
            else -> lerp(Offset.Zero, lamp, ramp(c, 0.97f, 1f))
        }
        val basking = c in 0.5f..0.95f
        val eye = if (basking) MoEye(shape = MoEyeShape.Happy) else MoEye(gaze = gaze)
        val bask = during(c, 0.5f, 0.99f, edge = 0.05f)
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (basking) MoBrows() else MoBrows(MoBrowKind.Raised, lift = 3f),
            mouth = when {
                c < 0.12f -> MoMouth.Oh(6f)
                c < 0.4f -> MoMouth.Flat(8f)
                c < 0.5f -> MoMouth.Smile(14f, 10f)
                c < 0.95f -> MoMouth.Smile(19f, 16f)
                else -> MoMouth.Oh(6f)
            },
            leftArm = mix(MoArm.Down, MoArm(Offset(40f, -20f), bend = 0.3f), bask),
            rightArm = mix(MoArm.Down, MoArm(Offset(40f, -20f), bend = 0.3f), bask),
            legs = MoLegs(MoLeg(lift = 5f * moving * pulse(t, 0.4f)), MoLeg(lift = 5f * moving * pulse(t + 0.2f, 0.4f))),
            tilt = first(v) * 2f * moving + 1.5f * bask * wave(t, 2.4f),
            squash = idle.squash + 0.015f * bask,
            offset = Offset(first(v) * STEP * out, 0f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val tint = v.of(listOf(MoPropMoon, MoPropYellow, MoPropSky), salt = 2)
        // The light is the same step played late, so it always trails him and always comes home.
        val lag = v.between(0.4f, 0.7f, salt = 4)
        val x = CENTRE + first(v) * STEP * stepOut(c(t - lag))
        // The lamp hangs just above a Blazing Mo's picture and inside every other state's, so the
        // beam always has something to come from.
        val lamp = Offset(if (lampSide(v) > 0f) 250f else -20f, -50f)
        val beam = Path().apply {
            moveTo(lamp.x, lamp.y)
            lineTo(x + 114f, FEET - 5f)
            lineTo(x - 114f, FEET - 5f)
            close()
        }
        drawPath(beam, tint.copy(alpha = 0.2f))
        oval(Offset(x, FEET - 5f), 120f, 11f, tint.copy(alpha = 0.45f))
        threadFromAbove(lamp, MoPropGreyDark, 5f)
        withTransform({ rotate(atan2(lamp.x - x, FEET - 5f - lamp.y) * 180f / PI.toFloat(), lamp) }) {
            block(lamp, 36f, 28f, MoPropCharcoal, corner = 9f)
            oval(lamp + Offset(0f, 13f), 15f, 5f, tint)
        }
    }

    override val ownsTheFloor get() = true
}

/** He blows a limp little flag into flying, stands proud while it streams, and watches it droop again. */
private object TheLimpFlag : MoAct("proud_the_limp_flag", MoOccasion.Proud) {
    private const val POLE = 220f
    private const val COLUMNS = 5
    private const val LENGTH = 46f
    private const val HEIGHT = 38f

    private fun c(t: Float) = cycle(t, 6.5f, offset = 0.42f)

    private fun flyEnd(v: MoVariant) = 0.32f + v.between(0.35f, 0.5f, salt = 5)

    private fun poleTop(v: MoVariant) = v.between(250f, 275f, salt = 4)

    /** How far the flag is out, from hanging (0) to streaming (1). The breath needs a moment to arrive. */
    private fun fly(c: Float, v: MoVariant): Float {
        val end = flyEnd(v)
        return ramp(c, 0.15f, 0.32f) * (1f - ramp(c, end, end + (1f - end) * 0.8f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = c(t)
        val end = flyEnd(v)
        val inhale = during(c, 0f, 0.16f, edge = 0.06f)
        val blow = during(c, 0.12f, 0.34f)
        val proud = during(c, 0.3f, end + 0.08f, edge = 0.07f)
        val toUs = during(c, 0.43f, end + 0.02f)
        val eye = MoEye(gaze = lerp(Offset(0.8f, -0.2f), Offset.Zero, toUs), lidDrop = 0.2f * blow)
        val droop = ramp(c, end, 1f)
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                c < 0.12f -> MoBrows(MoBrowKind.Raised, lift = 3f)
                c > end + 0.05f -> MoBrows(MoBrowKind.Raised, lift = 3f * droop)
                else -> MoBrows()
            },
            mouth = when {
                c < 0.12f -> MoMouth.Oh(6f + 2f * inhale)
                c < 0.32f -> MoMouth.Whistle
                c < end -> MoMouth.Beam
                droop < 0.7f -> MoMouth.Smile(17f - 8f * ease(droop / 0.7f), 15f - 9f * ease(droop / 0.7f))
                else -> MoMouth.Oh(6f)
            },
            leftArm = mix(MoArm.Down, AKIMBO, proud),
            squash = idle.squash + 0.04f * inhale - 0.05f * blow + 0.02f * proud,
            tilt = 3f * blow,
            flare = -4f * blow,
            offset = Offset(-22f, 0f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val top = poleTop(v)
        floorShadow(POLE, 24f, MoPropShadow)
        limb(Offset(POLE, FEET - 8f), Offset(POLE, top), MoPropWood, 6f)
        oval(Offset(POLE, FEET - 5f), 22f, 9f, MoPropGrass)

        val fly = fly(c(t), v)
        val angle = mix(80f, 0f, fly) * PI.toFloat() / 180f
        // The middle line of the cloth at a column; the hoist is pinned, so the ripple grows along it.
        fun spine(column: Float): Offset {
            val d = column / COLUMNS * LENGTH
            val ripple = sin(column * 1.2f - t * 9f) * 4f * fly * (column / COLUMNS)
            return Offset(POLE + 2f + d * cos(angle), top + 6f + HEIGHT / 2f + d * sin(angle) + ripple)
        }
        val form = v.pick(3, salt = 2)
        fun half(column: Float) = if (form == 1) HEIGHT / 2f * (1f - 0.9f * column / COLUMNS) else HEIGHT / 2f
        val columns = (0..COLUMNS).map { it.toFloat() }
        val upper = columns.map { spine(it) - Offset(0f, half(it)) }
        val lower = columns.reversed().map { spine(it) + Offset(0f, half(it)) }
        val notch = if (form == 2) listOf(spine(COLUMNS - 1.4f)) else emptyList()
        val colour = v.of(listOf(MoPropRed, MoPropBlue, MoPropPurple, MoPropTeal, MoPropPink), salt = 1)
        shape(colour, *(upper + notch + lower).map { it.x to it.y }.toTypedArray())

        // The emblem only shows once the cloth is open enough to carry it.
        val open = ramp(fly, 0.45f, 0.9f)
        val at = spine(if (form == 1) 1.5f else 2.1f)
        if (open > 0f) when (v.pick(3, salt = 3)) {
            0 -> oval(at, 7f * open, 7f, MoPropWhite)
            1 -> sparkle(at, 10f, open, MoPropWhite)
        }
        drawCircle(MoPropGold, 5f, Offset(POLE, top))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = c(t)
        val blowing = during(c, 0.12f, 0.32f, edge = 0.03f)
        if (blowing <= 0f) return
        // held draws in his own space, and he stands 22 to the left of the pole's.
        val cloth = Offset(POLE + 22f + 4f, poleTop(v) + 6f + HEIGHT / 2f)
        for (index in 0..2) {
            val p = cycle(t, 0.5f, offset = index / 3f)
            // Out of the pursed mouth and under his right eye, never over it.
            val at = quad(rig.mouth + Offset(14f, 5f), rig.mouth + Offset(70f, 18f), cloth, p)
            puff(at, 7f + 7f * p, MoPropWhite.copy(alpha = 0.95f * ease(p / 0.15f) * (1f - p) * blowing))
        }
    }

    override val ownsTheFloor get() = true
}

/** Between stage curtains he takes bow after bow, one mitten on his belly, while roses land at his feet. */
private object CurtainCall : MoAct("proud_curtain_call", MoOccasion.Proud) {
    private class Rose(val colour: Color, val heart: Color)

    private val roses = listOf(
        Rose(MoPropRed, MoPropRedDark),
        Rose(MoPropPink, MoPropPinkDark),
        Rose(MoPropYellow, MoPropYellowDark),
        Rose(MoPropWhite, MoPropSand),
    )

    private fun bow(t: Float): Float {
        val c = cycle(t, 3.6f, offset = 0.7f)
        return ramp(c, 0f, 0.25f) * (1f - ramp(c, 0.45f, 0.6f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val d = bow(t)
        val shape = MoBodies.of(body)
        val belly = MoArm(Offset(-70f, shape.mouth.y + 30f - shape.shoulderY), bend = 0.4f)
        val open = mix(MoArm(Offset(50f, -30f), bend = 0.15f), MoArm(Offset(20f, 30f), bend = 0.1f), d)
        val rightOpens = v.chance(50, salt = 6)
        val eye = if (d > 0.3f) MoEye(shape = MoEyeShape.Happy) else MoEye(gaze = Offset(0.5f * wave(t, 3.6f), 0f))
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            mouth = if (d > 0.3f) MoMouth.Smile(18f, 15f) else MoMouth.Grin(0.7f),
            leftArm = if (rightOpens) belly else open,
            rightArm = if (rightOpens) open else belly,
            armsInFront = true,
            // He cannot lean toward us, so the bow is a deep squash with a nodding tip.
            squash = idle.squash - 0.13f * d,
            flare = -10f * d,
            offset = Offset(0f, 2f * d),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val (colour, dark) = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropPurple to MoPropPurpleDark, MoPropBlue to MoPropBlueDark),
            salt = 1,
        )
        val sway = 2f * wave(t, 4f)
        for (side in listOf(-1f, 1f)) {
            // Written for the left curtain and mirrored about his centre for the right one.
            fun at(x: Float, y: Float) = Offset(if (side < 0f) x else 2f * CENTRE - x, y)
            val cinch = at(-24f, 300f)
            // A curtain hangs from above the picture, however much of it his state shows.
            val drape = Path().apply {
                moveTo(at(-42f, 0f).x, ABOVE_THE_PICTURE)
                lineTo(at(0f, 0f).x, ABOVE_THE_PICTURE)
                lineTo(at(0f, 0f).x, 0f)
                quadTo(this, at(0f, 0f), at(-2f, 210f), cinch)
                quadTo(this, cinch, at(-22f, 370f), at(-10f + sway, FEET))
                lineTo(at(-42f, FEET).x, FEET)
                close()
            }
            drawPath(drape, colour)
            for (fold in listOf(10f, 20f)) {
                limb(at(-fold, ABOVE_THE_PICTURE), at(-fold, 0f), dark, 5f)
                curve(at(-fold, 0f), at(-2f - fold, 210f), at(-24f - fold * 0.7f, 300f), dark, 5f)
                curve(at(-24f - fold * 0.7f, 300f), at(-22f - fold, 370f), at(-10f - fold + sway, FEET - 3f), dark, 5f)
            }
            chunk(at(-29f, 300f), 26f, 12f, MoPropGold, MoPropGoldDark, corner = 6f)
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val count = 3 + v.pick(3, salt = 7)
        val bouquet = v.chance(35, salt = 8)
        for (index in 0 until count) {
            val q = cycle(t, 5.4f, offset = index.toFloat() / count)
            val wrapped = bouquet && index == 0
            val spot = Offset(40f + 160f * index / (count - 1), FEET - if (wrapped) 14f else 6f)
            // Each comes from the nearer wing and stays low, so none crosses his face or his flung mitten.
            val side = if (spot.x < CENTRE) -1f else 1f
            val flight = (q / 0.25f).coerceIn(0f, 1f)
            val hop = 5f * sin(((q - 0.25f) / 0.07f).coerceIn(0f, 1f) * PI.toFloat())
            val at = lerp(Offset(CENTRE + side * 128f, 372f), spot, flight) - Offset(0f, 160f * flight * (1f - flight) + hop)
            // It tumbles in and comes to rest bloom first, pointing the way it flew.
            val rest = (if (side < 0f) -0.35f else PI.toFloat() + 0.35f) + (index % 2 - 0.5f) * 0.3f
            val angle = rest + side * 2.6f * (1f - ease(flight))
            // It appears just inside the curtain, because nothing may be drawn beyond his box.
            val alpha = ramp(q, 0f, 0.03f) * (1f - ramp(q, 0.85f, 1f))
            if (flight >= 1f) oval(Offset(spot.x, FEET + 2f), 20f, 4f, MoPropShadow.copy(alpha = MoPropShadow.alpha * alpha))
            val rose = v.of(roses, salt = 10 + index)
            rose(at, angle, rose, alpha, wrapped)
        }
    }

    private fun DrawScope.rose(at: Offset, angle: Float, rose: Rose, alpha: Float, bouquet: Boolean) {
        val along = Offset(cos(angle), sin(angle))
        val across = Offset(-along.y, along.x)
        val head = at + along * 14f
        val tail = at - along * 14f
        limb(tail, head, MoPropGreen.copy(alpha = alpha), 5f)
        oval(at - along * 4f + across * 5f, 8f, 4f, MoPropLeaf.copy(alpha = alpha))
        if (bouquet) {
            val wrap = listOf(tail, at + along * 6f + across * 12f, at + along * 6f - across * 12f)
            shape(MoPropCream.copy(alpha = alpha), *wrap.map { it.x to it.y }.toTypedArray())
        }
        val blooms = if (bouquet) listOf(head - along * 3f + across * 11f, head - along * 3f - across * 11f, head + along * 4f) else listOf(head)
        blooms.forEach {
            drawCircle(rose.colour.copy(alpha = alpha), 11f, it)
            drawCircle(rose.heart.copy(alpha = alpha), 4.5f, it)
        }
    }

    override val ownsTheFloor get() = true
}

/** He flexes and strains, and every pump sends his flame tip higher until a sparkle pops off the top. */
private object FlexingUpTheFlame : MoAct("proud_flexing_up_the_flame", MoOccasion.Proud) {
    private fun c(t: Float) = cycle(t, 5f, offset = 0.35f)

    private fun top(v: MoVariant) = v.between(42f, 50f, salt = 4)

    /** How far through the sparkle's moment at the top he is, from 0 to 1. */
    private fun atTheTop(c: Float) = ((c - 0.5f) / 0.18f).coerceIn(0f, 1f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = c(t)
        val pumps = 2 + v.pick(3, salt = 3)
        val m = (c / 0.5f).coerceAtMost(1f) * pumps
        val within = m - floor(m)
        // Each pump lifts the tip one step, and it sags a little before the next one.
        val sag = sin(((within - 0.625f) / 0.375f).coerceIn(0f, 1f) * PI.toFloat())
        val climbing = top(v) / pumps * (floor(m) + ease(within * 1.6f)) - 3f * sag
        val flare = if (c < 0.5f) climbing else top(v) * (1f - ramp(c, 0.68f, 0.85f))

        val effort = max(1f - ramp(c, 0.47f, 0.52f), ramp(c, 0.95f, 1f))
        val strain = (c / 0.5f) * (1f - ramp(c, 0.47f, 0.52f))
        val flexed = max(1f - ramp(c, 0.68f, 0.85f), ramp(c, 0.95f, 1f))
        val shakeOut = during(c, 0.84f, 0.96f, edge = 0.03f)
        val pump = if (c < 0.5f) pulse(within, 1f) else 0f
        fun arm(side: Float) = mix(
            MoArm(Offset(4f + side * wave(t, 0.12f) * 6f * shakeOut, 36f - 4f * shakeOut), bend = 0.1f),
            MoArm(Offset(34f + wave(t, 0.09f) * 2f * strain, -52f - 6f * pump), bend = 0.9f),
            flexed,
        )
        val which = v.pick(3, salt = 1)

        val eye = when {
            c < 0.5f -> MoEye(gaze = Offset(0.15f, -0.9f))
            c < 0.68f -> MoEye(gaze = Offset(0.2f, -1f), scale = 1.06f)
            c < 0.85f -> MoEye(shape = MoEyeShape.Shut)
            else -> MoEye(gaze = lerp(Offset.Zero, Offset(0.15f, -0.9f), ramp(c, 0.95f, 1f)))
        }
        val idle = moIdle(t, energy = 1.2f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                c < 0.5f || c > 0.97f -> MoBrows(MoBrowKind.Flat, lift = 2f)
                c < 0.68f -> MoBrows(MoBrowKind.Raised, lift = 5f)
                else -> MoBrows()
            },
            mouth = when {
                c < 0.5f || c > 0.97f -> MoMouth.Gritted
                c < 0.68f -> MoMouth.Grin(0.9f)
                c < 0.85f -> MoMouth.Oh(8f)
                else -> MoMouth.Smile()
            },
            leftArm = if (which == 2) MoArm.Hip else arm(-1f),
            rightArm = if (which == 1) MoArm.Hip else arm(1f),
            flare = flare,
            squash = idle.squash - 0.02f * effort - 0.04f * during(c, 0.68f, 0.87f, edge = 0.06f),
            shake = wave(t, 0.07f) * 1.2f * strain,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = c(t)
        val k = atTheTop(c)
        if (k > 0f && k < 1f) {
            val colour = v.of(listOf(MoPropGold, MoPropWhite, MoPropYellow, MoPropSky), salt = 2)
            val pop = ramp(k, 0f, 0.2f) * (1f - ramp(k, 0.75f, 1f))
            sparkle(rig.tip + Offset(0f, -10f - 8f * ease(k)), 24f, pop, colour)
            for (side in listOf(-1f, 1f)) {
                sparkle(rig.tip + Offset(side * (20f + 30f * k), -8f - 10f * k), 9f, ramp(k, 0f, 0.2f) * (1f - k), colour)
            }
        }
        val p = (c - 0.68f) / 0.17f
        if (p > 0f && p < 1f) {
            puff(rig.mouth + Offset(32f + 26f * p, 6f - 8f * p), 8f + 6f * p, MoPropWhite.copy(alpha = 0.75f * ramp(p, 0f, 0.15f) * (1f - p)))
        }
    }

    override val sparkles get() = false
}

/** He shows off a paper chain with no link missing: a tug to prove it holds, then a fond look down at it. */
private object TheUnbrokenChain : MoAct("proud_the_unbroken_chain", MoOccasion.Proud, intensity = MoIntensity.Mild) {
    private fun tug(t: Float, v: MoVariant): Float {
        // The offsets put the peak of the first tug on t = 0 and the second one 1.2 s later.
        val first = twitch(t, 4.2f, beat = 1.2f, offset = 0.6f / 4.2f)
        val second = if (v.chance(35, salt = 3)) 0.6f * twitch(t, 4.2f, beat = 0.8f, offset = (0.6f - 1.4f) / 4.2f) else 0f
        return max(first, second)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val g = tug(t, v)
        val showing = ramp(g, 0.1f, 0.4f)
        val eye = MoEye(gaze = Offset(0f, 0.9f * (1f - showing)))
        // Held out past his sides: a mitten inside his silhouette cannot be seen holding anything, and
        // the chain would read as a necklace draped on him.
        val arm = MoArm(Offset(6f + 8f * g, 22f + 12f * g), bend = 0.3f)
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (g >= 0.2f) MoBrows(MoBrowKind.Raised, lift = 3f * g) else MoBrows(),
            mouth = if (g >= 0.2f) MoMouth.Grin(0.7f) else MoMouth.Smile(15f, 12f),
            leftArm = arm,
            rightArm = arm,
            armsInFront = true,
            tilt = 1.5f * wave(t, 3.2f),
            squash = idle.squash + 0.02f * g,
            flare = 6f * g,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val g = tug(t, v)
        val (one, other) = v.of(
            listOf(MoPropPink to MoPropSky, MoPropYellow to MoPropTeal, MoPropPurple to MoPropGreen, MoPropOrange to MoPropBlue),
            salt = 1,
        )
        val links = 5 + 2 * v.pick(3, salt = 2)
        val middle = links / 2
        val golden = v.chance(40, salt = 4)
        // The ends hang from below his mittens, so the chain is plainly held and not growing out of him.
        val left = rig.leftHand + Offset(0f, 7f)
        val right = rig.rightHand + Offset(0f, 7f)
        val control = (left + right) / 2f + Offset(5f * wave(t, 3.2f), 2f * mix(22f, 6f, g))
        val spacing = (right - left).getDistance() / (links - 1)
        val stroke = Stroke((spacing * 0.3f).coerceIn(4.5f, 6.5f))

        // Each flat link passes behind its neighbours at the bottom and in front at the top.
        for (pass in 0..2) {
            val standing = pass == 1
            for (index in 0 until links) {
                if ((index % 2 == 1) != standing) continue
                val u = index.toFloat() / (links - 1)
                val at = quad(left, control, right, u)
                val tangent = lerp(control - left, right - control, u)
                val degrees = atan2(tangent.y, tangent.x) * 180f / PI.toFloat()
                val colour = if (golden && index == middle) MoPropGold else if (index % 2 == 0) one else other
                // Even five links must span the full distance between his hands when he pulls.
                val rx = spacing * if (standing) 0.48f else 0.76f
                val ry = (spacing * if (standing) 0.6f else 0.42f).coerceAtMost(if (standing) 20f else 13f)
                rotate(degrees, at) {
                    val corner = at - Offset(rx, ry)
                    val size = Size(rx * 2f, ry * 2f)
                    if (standing) drawOval(colour, corner, size, style = stroke)
                    else drawArc(colour, if (pass == 0) 0f else 180f, 181f, false, corner, size, style = stroke)
                }
            }
        }
        grippingMitten(rig, -1f)
        grippingMitten(rig, 1f)
        if (g > 0f) sparkle(quad(left, control, right, 0.5f) + Offset(0f, -4f), 16f, g, MoPropWhite)
    }

    override val sparkles get() = false
}

/** He holds the hero pose of the little gold statue of himself beside him, and keeps peeking to check it. */
private object PosingLikeHisStatue : MoAct("proud_posing_like_his_statue", MoOccasion.Proud, intensity = MoIntensity.Mild) {
    /** How big the cast is next to him. A third of his height still leaves it plainly a statuette. */
    private const val CAST = 0.3f

    /** What one arm does in the day's hero pose. The statue holds the same one. */
    private enum class Hero { Up, Flex, Hip }

    /** -1 when the statue stands on the viewer's left, 1 on the right. */
    private fun statueSide(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    /** The hero pose of the day: 0 one arm up and one on the hip, 1 a double flex, 2 both up. */
    private fun heroPose(v: MoVariant) = v.pick(3, salt = 1)

    private fun hero(v: MoVariant, armSide: Float): Hero = when (heroPose(v)) {
        0 -> if (armSide == statueSide(v)) Hero.Up else Hero.Hip
        1 -> Hero.Flex
        else -> Hero.Up
    }

    // Straighter than MoArm.Up, so the raised mitten clears the statue's head instead of landing on it,
    // and a shade tighter than MoArm.Flex: standing aside from the statue, a full flex would push his
    // far elbow past the edge of his box.
    private fun moArm(hero: Hero): MoArm = when (hero) {
        Hero.Up -> MoArm(Offset(24f, -72f))
        Hero.Flex -> MoArm(Offset(26f, -56f), bend = 0.55f)
        Hero.Hip -> AKIMBO
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = statueSide(v)
        val period = v.between(3.8f, 5.5f, salt = 5)
        val peek = twitch(t, period, beat = 1.1f, offset = 0.6f)
        // Half a second after the peek he corrects an arm quickly, and it sinks back slowly: never quite.
        val since = cycle(t, period, offset = 0.6f - 0.5f / period) * period
        val nudge = if (since < 0.3f) ease(since / 0.3f) else 1f - ease((since - 0.3f) / 1.5f)
        fun arm(armSide: Float): MoArm {
            val base = moArm(hero(v, armSide))
            val k = if (armSide == side) nudge else 0f
            return MoArm(base.reach + Offset(4f * k, -8f * k), base.bend + 0.15f * k)
        }
        val eye = MoEye(gaze = lerp(Offset(-side * 0.3f, -0.45f), Offset(side, 0.2f), ramp(peek, 0.1f, 0.5f)))
        val idle = moIdle(t, energy = 0.7f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (peek > 0.2f) MoBrows(MoBrowKind.Raised, skew = 5f) else MoBrows(),
            mouth = if (peek > 0.2f) MoMouth.Flat(8f) else MoMouth.Smile(18f, 14f),
            leftArm = arm(-1f),
            rightArm = arm(1f),
            squash = idle.squash + 0.02f,
            offset = Offset(-side * 24f, 0f),
            shake = wave(t, 0.08f) * 0.7f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = statueSide(v)
        val x = CENTRE + side * 116f
        val (stone, dark) = v.of(listOf(MoPropGrey to MoPropGreyDark, MoPropSilver to MoPropSteel, MoPropSand to MoPropWood), salt = 3)
        floorShadow(x, 32f, MoPropShadow)
        // Foot, shaft and cap: three stones, or the pedestal reads as a box he has been stood in.
        chunk(Offset(x, FEET - 8f), 62f, 14f, stone, dark, corner = 4f)
        chunk(Offset(x, FEET - 24f), 38f, 26f, stone, dark, corner = 4f)
        chunk(Offset(x, FEET - 44f), 56f, 14f, stone, dark, corner = 4f)

        val feet = Offset(x, FEET - 52f)
        // Cast from his own outline, or a little gold flame on a plinth just reads as a candle.
        withTransform({
            translate(feet.x - CENTRE * CAST, feet.y - FEET * CAST)
            scale(CAST, CAST, Offset.Zero)
        }) {
            for (armSide in listOf(-1f, 1f)) {
                val shoulder = Offset(CENTRE + armSide * 88f, 332f)
                // Longer and thicker than his own arm: at a third his size a tucked-in arm says nothing.
                val joints = when (hero(v, armSide)) {
                    Hero.Up -> listOf(shoulder + Offset(armSide * 30f, -78f))
                    Hero.Flex -> listOf(shoulder + Offset(armSide * 30f, 4f), shoulder + Offset(armSide * 18f, -54f))
                    Hero.Hip -> listOf(shoulder + Offset(armSide * 22f, 18f), shoulder + Offset(armSide * 2f, 38f))
                }
                var from = shoulder
                for (joint in joints) {
                    limb(from, joint, MoPropGold, 24f)
                    from = joint
                }
                drawCircle(MoPropGold, 14f, from)
            }
            for (footX in listOf(85f, 147f)) {
                limb(Offset(footX, 378f), Offset(footX, 413f), MoPropGold, 24f)
                oval(Offset(footX, 413f), 19f, 12f, MoPropGold)
            }
            body(OUTER_FLAME.toPath(), MoPropGold, MoPropGoldDark, lift = 11f)
            drawPath(BELLY_FLAME.toPath(), MoPropMoon)
            for (eyeSide in listOf(-1f, 1f)) drawCircle(MoPropGoldDark, 11f, Offset(CENTRE + eyeSide * 42f, 284f))
            curve(Offset(96f, 318f), Offset(113f, 334f), Offset(130f, 318f), MoPropGoldDark, 7f)
        }
        sparkle(Offset(x + 10f * wave(t, 5f), feet.y - 72f + 8f * wave(t, 2.5f)), 7f, pulse(t, 2.5f), MoPropWhite)

        if (v.chance(35, salt = 4)) {
            // On the side facing him, clear of the pedestal's foot.
            val from = Offset(x - side * 34f, FEET - 1f)
            val to = Offset(x - side * 6f, FEET - 5f)
            limb(from, to, MoPropGreenDark, 3f)
            for (leaf in 0..2) oval(lerp(from, to, 0.2f + leaf * 0.33f) + Offset(0f, if (leaf % 2 == 0) -3f else 3f), 7f, 3.5f, MoPropLeaf)
        }
    }

    override val ownsTheFloor get() = true
}

internal val proudActs2: List<MoAct> = listOf(
    IsThatLightForMe,
    TheLimpFlag,
    CurtainCall,
    FlexingUpTheFlame,
    TheUnbrokenChain,
    PosingLikeHisStatue,
)
