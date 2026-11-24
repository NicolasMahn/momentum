package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoFlameMiddle
import de.nicolasmahn.momentum.core.ui.theme.MoFlameOuter
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropLeaf
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropMoon
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
import de.nicolasmahn.momentum.core.ui.theme.MoSlate
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.flameTongue
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * The Husk, part two. He does not move and does not look at anyone, so only the world around him
 * can, and the point of every act here is that something small still finds him worth being near.
 */

/** The Husk's spark pulses on this period in MoFigure; whatever answers it here keeps the same beat. */
private const val SPARK_PERIOD = 3.1f

/** How bright his spark is at [t], from 0 to 1: the curve the Husk draws it with. */
private fun sparkGlow(t: Float) = 0.5f + 0.5f * wave(t, SPARK_PERIOD)

private fun MoVariant.outSide(salt: Int) = if (chance(50, salt)) 1f else -1f

private fun polar(angle: Float, radius: Float) = Offset(cos(angle) * radius, sin(angle) * radius)

private fun DrawScope.triangle(a: Offset, b: Offset, c: Offset, colour: Color) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }
    drawPath(path, colour)
}

/** A Husk: fireflies drift above him, and their lights come on each time his spark dims, as if answering it. */
private object OutFirefliesAnswerTheSpark : MoAct("out_fireflies_answer_the_spark", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    // Where each firefly keeps its figure of eight, relative to the group's centre, for up to six of them.
    private val seats = listOf(Offset(-48f, -34f), Offset(40f, 6f), Offset(-8f, 44f), Offset(56f, -50f), Offset(-60f, 26f), Offset(12f, -70f))

    private fun count(v: MoVariant) = 3 + v.pick(4, salt = 1)

    private fun light(v: MoVariant) = v.of(listOf(MoPropYellow, MoPropLeaf, MoPropMint), salt = 2)

    private fun perched(v: MoVariant) = v.chance(35, salt = 4)

    /** Each light answers a little later than the one before, so the group blinks on in a ripple, not as one lamp. */
    private fun lit(t: Float, index: Int) = ease(1f - sparkGlow(t - index * 0.12f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val (groupX, spread) = when (v.pick(3, salt = 3)) {
            0 -> 28f to 0.55f
            1 -> 202f to 0.55f
            else -> CENTRE to 1f
        }
        val flying = count(v) - if (perched(v)) 1 else 0
        for (index in 0 until flying) {
            val seat = Offset(groupX + seats[index].x * spread, 130f + seats[index].y)
            // Each drifts round its own slow figure of eight on its own period, so no two move in step.
            val angle = cycle(t, 8.5f + index * 1.3f, offset = index * 0.37f) * 2f * PI.toFloat()
            val width = 26f * spread
            val at = seat + Offset(sin(angle) * width, sin(2f * angle) * 20f)
            val heading = Offset(cos(angle) * width, cos(2f * angle) * 40f)
            firefly(at, heading, flap = abs(wave(t, 0.09f)), lit = lit(t, index), light = light(v))
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        if (!perched(v)) return
        // One has settled on his shoulder, wings folded, and only its light still answers him.
        val side = v.outSide(salt = 5)
        firefly(Offset(CENTRE + side * 58f, 257f), Offset(-side, 0f), flap = 0f, lit = lit(t, count(v) - 1), light = light(v), folded = true)
    }

    private fun DrawScope.firefly(at: Offset, heading: Offset, flap: Float, lit: Float, light: Color, folded: Boolean = false) {
        val facing = if (heading.x < 0f) -1f else 1f
        // It noses along its path, but only a little: a full turn on a small body looks like a tumble.
        val tilt = Math.toDegrees(atan2(heading.y, abs(heading.x)).toDouble()).toFloat() * 0.5f
        withTransform({ rotate(facing * tilt, at); scale(facing, 1f, at) }) {
            val wing = MoPropSky.copy(alpha = 0.85f)
            if (folded) {
                oval(at + Offset(-1f, -4f), 8f, 3f, wing)
            } else {
                oval(at + Offset(-4f, -6.5f - flap * 4f), 8.5f, 4.5f, wing)
                oval(at + Offset(4f, -6.5f - flap * 4f), 8.5f, 4.5f, wing)
            }
            // Charcoal rather than ink: ink is the night itself, and a body that vanishes leaves only wings.
            oval(at, 9f, 6f, MoPropCharcoal)
            if (lit > 0.02f) drawCircle(light.copy(alpha = lit), 5.5f, at + Offset(-7.5f, 0.5f))
        }
    }
}

/** A Husk: a shooting star crosses the sky over him while his eyes stay shut, so the wish is left to whoever is looking. */
private object OutShootingStarUnseen : MoAct("out_shooting_star_he_does_not_see", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    private const val PERIOD = 6.2f

    /** The part of each period the streak is in the sky. */
    private const val CROSSING = 0.36f

    private val sky = listOf(Offset(0.09f, 0.22f), Offset(0.32f, 0.09f), Offset(0.62f, 0.19f), Offset(0.92f, 0.12f), Offset(0.20f, 0.49f), Offset(0.83f, 0.43f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.world(t: Float, v: MoVariant, picture: Rect) {
        val count = 4 + v.pick(3, salt = 3)
        for (index in 0 until count) {
            val star = Offset(picture.left + picture.width * sky[index].x, picture.top + picture.height * sky[index].y)
            sparkle(star, 12f, 0.78f + 0.22f * wave(t + index * 0.7f, 2.3f + index * 0.35f), MoPropMoon.copy(alpha = 0.85f))
        }

        // Offset by half a crossing, so the first frame already has the streak mid-sky.
        val progress = cycle(t, PERIOD, offset = CROSSING / 2f) / CROSSING
        if (progress >= 1f) return
        val dir = v.outSide(salt = 1)
        val top = picture.top + picture.height * 0.19f + v.between(0f, 30f, salt = 5)
        // Give the head room to flare and let the tail grow behind it as it crosses.
        val reach = picture.width / 2f - 16f
        val from = Offset(CENTRE - dir * reach, top)
        val to = Offset(CENTRE + dir * reach, top + v.between(45f, 120f, salt = 4))
        val head = from + (to - from) * progress
        val along = (to - from) / (to - from).getDistance().coerceAtLeast(1f)
        val across = Offset(-along.y, along.x)
        val colour = v.of(listOf(MoPropMoon, MoPropGold, MoPropSky), salt = 2)
        val shine = ease(progress / 0.12f) * ease((1f - progress) / 0.3f)
        val tail = head - along * (84f * ease(progress / 0.25f))
        triangle(head + across * 6f, head - across * 6f, tail, colour.copy(alpha = 0.5f * shine))
        sparkle(head, 14f, 1f + 0.12f * wave(t, 0.21f), colour.copy(alpha = shine))
    }
}

/** A Husk: a tumbleweed rolls past behind him in lazy hops, and a little one hurries after it and never quite catches up. */
private object OutTumbleweedAndLittleOne : MoAct("out_tumbleweed_and_its_little_one", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    // Every hop period below divides the loop, so nothing is mid-hop when the run starts over.
    private const val LOOP = 12f

    /**
     * Above the floor line: they roll past further back than anything standing beside him, and high
     * enough that nothing of them shows in the gap between his feet as they pass behind him.
     */
    private const val GROUND = 400f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.world(t: Float, v: MoVariant, picture: Rect) =
        clipRect(picture.left, picture.top, picture.right, picture.bottom) {
            val dir = v.outSide(salt = 1)
            val (body, twig) = v.of(listOf(MoPropSand to MoPropWood, MoPropWood to MoPropWoodDark, MoPropGrey to MoPropGreyDark), salt = 2)
            val little = 1 + v.pick(2, salt = 3)
            val lag = v.between(30f, 36f, salt = 4)
            // The run starts with the big one arriving beside him and the little one in plain sight behind it.
            val margin = 36f
            val run = picture.width + 2f * margin
            val lead = cycle(t, LOOP, offset = 0.26f) * run
            fun position(distance: Float) = picture.left - margin + distance.mod(run)
            tumbleweed(position(lead), dir, radius = 27f, hop = hop(t, 1.5f, 18f), body, twig)
            for (index in 0 until little) {
                // It gains a little on the big one and loses it again, over the whole run.
                val behind = lead - lag * (1f + index * 0.75f) - 10f * wave(t, LOOP / 2f)
                tumbleweed(position(behind), dir, radius = 13f - index * 2.5f, hop = hop(t + index * 0.2f, 0.5f, 9f), body, twig)
            }
        }

    /** A parabolic hop of [height], from 0 to 1 and back over [period]. */
    private fun hop(t: Float, period: Float, height: Float): Float {
        val p = cycle(t, period)
        return 4f * p * (1f - p) * height
    }

    private fun DrawScope.tumbleweed(travelled: Float, dir: Float, radius: Float, hop: Float, body: Color, twig: Color) {
        val x = if (dir > 0f) travelled else 2f * CENTRE - travelled
        val centre = Offset(x, GROUND - radius - hop)
        oval(Offset(x, GROUND + 2f), radius * 0.9f * (1f - 0.45f * (hop / (radius * 0.7f)).coerceIn(0f, 1f)), 3.5f, MoPropShadow.copy(alpha = 0.85f))
        val turn = dir * travelled / radius * 180f / PI.toFloat()
        withTransform({ rotate(turn, centre) }) {
            for (index in 0..2) {
                withTransform({ rotate(index * 60f, centre) }) { oval(centre, radius, radius * 0.5f, body.copy(alpha = 0.7f)) }
            }
            // A few twigs curl round the rim, which is what makes the turning visible.
            for (index in 0..3) {
                val a = index * 1.57f + 0.4f
                curve(centre + polar(a, radius * 0.95f), centre + polar(a + 0.55f, radius * 1.15f), centre + polar(a + 1.1f, radius * 0.95f), twig, radius * 0.13f)
            }
        }
    }

    override val ownsTheFloor get() = true
}

/** A Husk: a little bird beside him sings up at his face, cocks its head one way and the other for an answer, gets none, and tries again. */
private object OutBirdWaitingForAnAnswer : MoAct("out_bird_that_waits_for_an_answer", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    /** One song and the wait after it. The loop is two of them, so it can hop closer for the second and back for the first. */
    private const val TRY = 5.5f
    private const val SONG = 1.5f

    private fun side(v: MoVariant) = v.outSide(salt = 1)

    private fun hops(v: MoVariant) = v.chance(40, salt = 5)

    private fun notes(v: MoVariant) = 2 + v.pick(2, salt = 3)

    private fun ramp(w: Float, from: Float, to: Float) = ease((w - from) / (to - from))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = side(v)
        val facing = -side
        // Half a second into a song at t = 0, so the first frame has notes in the air.
        val u = cycle(t, 2f * TRY, offset = 0.5f / (2f * TRY)) * 2f * TRY
        val second = u >= TRY
        val w = if (second) u - TRY else u
        val singing = w < SONG
        val voice = if (singing) ease(w / 0.15f) * ease((SONG - w) / 0.15f) * abs(wave(w, 0.27f)) else 0f
        val cock = ramp(w, 2f, 2.4f) - 2f * ramp(w, 3.1f, 3.6f) + ramp(w, 4.3f, 4.7f)
        val hop = if (hops(v)) ramp(w, 4.85f, 5.25f) else 0f
        val closer = when {
            !hops(v) -> 0f
            second -> 1f - hop
            else -> hop
        }

        val (feathers, shade, breast) = v.of(
            listOf(
                Triple(MoPropSky, MoPropBlue, null),
                Triple(MoPropYellow, MoPropYellowDark, null),
                Triple(MoPropPink, MoPropPinkDark, null),
                Triple(MoPropGrey, MoPropGreyDark, MoPropRed),
            ),
            salt = 2,
        )
        // Far enough out that its beak clears his arm even after the hop closer.
        val x = CENTRE + side * (129f - 10f * closer)
        val lift = 4f * hop * (1f - hop) * 14f
        val foot = Offset(x, FEET - lift)
        floorShadow(x, 20f, MoPropShadow.copy(alpha = 1f - 0.4f * (lift / 14f)))
        val body = foot + Offset(0f, -21f - 3f * voice)
        for (leg in listOf(-6f, 6f)) limb(body + Offset(leg, 8f), Offset(x + leg, foot.y), MoPropOrange, 2.5f)
        val flick = twitch(t, 3.5f, beat = 0.3f, offset = 0.4f)
        triangle(body + Offset(-facing * 13f, -4f), body + Offset(-facing * 13f, 4f), body + Offset(-facing * 26f, -8f - 4f * flick), shade)
        oval(body, 17f, 14f, feathers)
        breast?.let { oval(body + Offset(facing * 6f, 2f), 10f, 9f, it) }
        oval(body + Offset(-facing * 3f, 0f), 9f, 6f, shade)

        val neck = body + Offset(facing * 6f, -8f)
        withTransform({ rotate(cock * 28f, neck) }) {
            val head = body + Offset(facing * 11f, -17f)
            drawCircle(feathers, 11f, head)
            drawCircle(MoPropInk, 2.3f, head + Offset(facing * 4.5f, -3f))
            val gape = voice
            val hinge = head + Offset(facing * 9f, -2f)
            triangle(head + Offset(facing * 7f, -6f), hinge, head + Offset(facing * 18f, -6f - gape * 4.5f), MoPropOrange)
            triangle(hinge, head + Offset(facing * 7f, 2f), head + Offset(facing * 18f - facing * gape * 2f, -6f + gape * 4.5f), MoPropOrange)
        }

        val colour = v.of(listOf(MoPropTeal, MoPropPink, MoPropYellow, MoPropSky, MoPropMint), salt = 4)
        val beak = body + Offset(facing * 29f, -23f)
        for (index in 0 until notes(v)) {
            val age = (w - 0.2f - index * 0.42f) / 1.9f
            if (age < 0f || age >= 1f) continue
            // They rise away from him, not toward his face: the song is for him, the notes are not.
            val head = beak + Offset(side * (6f + 30f * age + 5f * sin(age * 7f + index)), -(6f + 118f * age))
            val fade = colour.copy(alpha = 1f - age * age)
            oval(head, 7f, 5.5f, fade)
            limb(head + Offset(6f, 0f), head + Offset(6f, -22f), fade, 3.5f)
            limb(head + Offset(6f, -22f), head + Offset(15f, -17f), fade, 3.5f)
        }
    }

    override val ownsTheFloor get() = true
}

/** A Husk: a toy pinwheel stuck in the ash beside him turns in a draught that gusts and dies down, and flecks of ash slide along with it. */
private object OutPinwheelInTheAsh : MoAct("out_pinwheel_in_the_ash", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    private fun side(v: MoVariant) = v.outSide(salt = 1)

    /** The wheel's angle in radians: always the same way round, never at a steady speed, and without a seam. */
    private fun turned(t: Float) = t * 1.2f + sin(t * 0.45f) * 2.2f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = side(v)
        val foot = Offset(CENTRE + side * 127f, FEET)
        val lean = Math.toRadians(v.between(4f, 10f, salt = 3).toDouble()).toFloat()
        // The stick leans in toward him, which keeps the wheel inside the picture on either side.
        val hub = foot + Offset(-side * sin(lean) * 175f, -cos(lean) * 175f)
        floorShadow(foot.x, 16f, MoPropShadow)
        limb(foot, hub, MoPropWood, 6f)

        val (first, second) = v.of(listOf(MoPropPink to MoPropTeal, MoPropYellow to MoPropSky, MoPropRed to MoPropCream, MoPropPurple to MoPropMint), salt = 2)
        val blades = if (v.chance(50, salt = 4)) 4 else 6
        val slot = 2f * PI.toFloat() / blades
        for (index in 0 until blades) {
            val start = turned(t) + index * slot
            val outer = hub + polar(start, 30f)
            val trailing = hub + polar(start + slot * 0.78f, 23f)
            shape(if (index % 2 == 0) first else second, hub.x to hub.y, outer.x to outer.y, trailing.x to trailing.y)
        }
        drawCircle(MoPropGoldDark, 6.5f, hub + Offset(0f, 2.5f))
        drawCircle(MoPropGold, 6.5f, hub)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val side = side(v)
        val flecks = 2 + v.pick(2, salt = 5)
        for (index in 0 until flecks) {
            // Ash rides the same draught as the wheel, from his pile out past the stick, and fades at each end of its slide.
            val run = (turned(t) * 20f + index * 63f / flecks).mod(63f) / 63f
            val x = CENTRE + side * (90f + run * 63f)
            oval(Offset(x, 423f), 8f, 3.5f, MoSlate.copy(alpha = 0.9f * sin(run * PI.toFloat())))
        }
    }

    override val ownsTheFloor get() = true
}

/** A Husk: a thought puff floats beside his collapsed tip with a tiny flame in it, his own colours, and swells whenever his spark does. */
private object OutDreamingOfFire : MoAct("out_dreaming_of_fire", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = v.outSide(salt = 1)
        val bob = wave(t, v.between(3.6f, 5.4f, salt = 5)) * 5f
        val glow = sparkGlow(t)
        val cloud = if (v.chance(50, salt = 3)) MoPropSilver else MoPropCream
        val centre = Offset(CENTRE + side * 92f, 112f + bob)

        // The trail climbs from beside his crown to the puff, and rides less of the bob the closer it is to him.
        val trail = 2 + v.pick(2, salt = 2)
        val crown = Offset(CENTRE + side * 34f, 198f)
        for (index in 0 until trail) {
            val f = (index + 1f) / (trail + 1f)
            drawCircle(cloud, 4.5f + 6f * f, crown + (centre - crown) * f)
        }

        val swell = 1f + 0.06f * glow
        withTransform({ scale(swell, swell, centre) }) {
            puff(centre, 38f, cloud)
            val size = v.between(10f, 12.5f, salt = 4) * (1f + 0.12f * glow + 0.06f * wave(t, 0.31f) + 0.04f * wave(t, 0.53f))
            val base = centre + Offset(1.5f * wave(t, 0.43f), 15f)
            // Washed into the puff: a memory of fire, never a flame that could be mistaken for one near him.
            flameTongue(base, size, MoFlameOuter.copy(alpha = 0.62f))
            flameTongue(base, size * 0.7f, MoFlameMiddle.copy(alpha = 0.62f))
            flameTongue(base, size * 0.42f, MoFlameBelly.copy(alpha = 0.7f))
        }
    }
}

internal val outActs2: List<MoAct> = listOf(
    OutFirefliesAnswerTheSpark,
    OutShootingStarUnseen,
    OutTumbleweedAndLittleOne,
    OutBirdWaitingForAnAnswer,
    OutPinwheelInTheAsh,
    OutDreamingOfFire,
)
