package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
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
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.abs

/*
 * More of a hungry Mo: a visitor with crumbs he will not touch, a stage faint, foods circling his
 * tip where a dizzy cartoon sees stars, and a belt of plates that are all empty.
 */

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

/** A small visitor eats crumbs beside him; he longs, catches himself, whistles at nothing, and looks again. */
private object WatchingTheBird : MoAct("hungry_watching_the_bird", MoOccasion.Hungry, intensity = MoIntensity.Mild) {
    private const val LOOP = 6.4f
    private const val VISITOR = 1.7f

    // He stands a little to the far side, which leaves the visitor room to be big enough to read.
    private const val ROOM = 14f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val g = cycle(t, LOOP)
        val lean = 1f - ease((g - 0.4f) / 0.08f) + ease((g - 0.82f) / 0.16f)
        // His eyes give him away: they creep back to the crumbs before the rest of him dares to.
        val looking = 1f - ease((g - 0.4f) / 0.05f) + ease((g - 0.72f) / 0.1f)
        val away = 1f - lean
        val caught = g in 0.4f..0.47f
        val eye = MoEye(
            gaze = mix(Offset(-s * 0.8f, -0.5f), Offset(s * 0.9f, 0.85f), looking),
            scale = 1f + 0.06f * lean,
            iris = if (caught) 0.5f else 0.76f,
        )
        val idle = moIdle(t, energy = 0.9f)
        // The near mitten is held back against his side, clear of the visitor and out of temptation.
        val nearArm = MoArm(Offset(9f, -12f), bend = 0.55f)
        // Eased onto his hip with the rest of him, so the elbow never snaps out between two frames.
        val farArm = MoArm(mix(MoArm.Down.reach, MoArm.Hip.reach, away), mix(MoArm.Down.bend, MoArm.Hip.bend, away))
        val tap = MoLeg(lift = 4f * twitch(t, 0.5f, beat = 0.2f) * away)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (lean > 0.5f) MoBrows(MoBrowKind.Worried, lift = 3f) else MoBrows(MoBrowKind.Raised, skew = 4f),
            mouth = when {
                caught -> MoMouth.Oh(5f)
                lean > 0.5f -> MoMouth.TongueOut
                else -> MoMouth.Whistle
            },
            leftArm = if (s < 0f) nearArm else farArm,
            rightArm = if (s < 0f) farArm else nearArm,
            legs = if (s < 0f) MoLegs(right = tap) else MoLegs(left = tap),
            tilt = s * 7f * lean,
            offset = Offset(s * (6f * lean - ROOM - if (body == MoBodyKind.Low) 22f else 0f), 0f),
            sway = idle.sway + s * 6f * lean,
        )
    }

    // Drawn in front, so neither the visitor nor a crumb hides behind his base.
    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = side(v)
        val bx = CENTRE + s * 118f
        floorShadow(bx, 34f, MoPropShadow)
        val crumbs = 3 + v.pick(3, salt = 3)
        // Every fourth beat the visitor lifts its head and looks about instead of eating.
        val eating = if (cycle(t, 3.2f) < 0.75f) 1f else 0f
        // Drawn small and scaled up from the floor, so it reads at the size of a thumbnail.
        withTransform({ scale(VISITOR, VISITOR, Offset(bx, FEET)) }) {
            for (index in 0 until crumbs) {
                // Each crumb has its own size and sits a little nearer or further on the ground, so a
                // few of them scatter instead of lining up into a row of even dots.
                val jitter = index * 5 % 3 - 1
                val out = 27f + index * 13f / (crumbs - 1)
                drawCircle(MoPropSand, 3.4f - (jitter + 1) * 0.45f, Offset(bx - s * out, FEET - 3f + jitter * 2.5f))
            }
            if (v.chance(20, salt = 4)) mouse(t, bx, s, eating) else bird(t, v, bx, s, eating)
        }
    }

    private fun DrawScope.bird(t: Float, v: MoVariant, bx: Float, s: Float, eating: Float) {
        val (colour, dark) = v.of(listOf(MoPropSky to MoPropBlue, MoPropPink to MoPropPinkDark, MoPropYellow to MoPropYellowDark), salt = 1)
        val peck = twitch(t, 0.8f, beat = 0.3f) * eating
        limb(Offset(bx - 4f, FEET - 11f), Offset(bx - 4f, FEET - 1.5f), MoPropOrange, 3f)
        limb(Offset(bx + 4f, FEET - 11f), Offset(bx + 4f, FEET - 1.5f), MoPropOrange, 3f)
        // It tips forward from the hips, which brings the beak down between the crumbs, never onto one.
        withTransform({ rotate(-s * 40f * peck, Offset(bx, FEET - 12f)) }) {
            shape(dark, bx + s * 9f to FEET - 29f, bx + s * 20f to FEET - 38f, bx + s * 20f to FEET - 26f)
            drawCircle(colour, 14f, Offset(bx, FEET - 23f))
            oval(Offset(bx - s * 4f, FEET - 16f), 8f, 6f, MoPropCream)
            oval(Offset(bx + s * 5f, FEET - 22f), 8f, 5.5f, dark)
            shape(MoPropOrange, bx - s * 12f to FEET - 30f, bx - s * 22f to FEET - 23f, bx - s * 12f to FEET - 23f)
            drawCircle(MoPropInk, 2.6f, Offset(bx - s * 6f, FEET - 29f))
        }
    }

    private fun DrawScope.mouse(t: Float, bx: Float, s: Float, eating: Float) {
        val nibble = wave(t, 0.2f) * 1.2f * eating
        val head = Offset(bx - s * 14f, FEET - 17f + nibble)
        curve(Offset(bx + s * 14f, FEET - 8f), Offset(bx + s * 27f, FEET - 4f), Offset(bx + s * 20f, FEET - 24f), MoPropPink, 3f)
        oval(Offset(bx, FEET - 12f), 17f, 11f, MoPropGrey)
        for (ear in listOf(Offset(s * 4f, -9f), Offset(-s * 5f, -9f))) {
            drawCircle(MoPropGrey, 6.5f, head + ear)
            drawCircle(MoPropPink, 3.5f, head + ear)
        }
        drawCircle(MoPropGrey, 9f, head)
        drawCircle(MoPropInk, 2.2f, head + Offset(-s * 3f, -2f))
        drawCircle(MoPropPink, 2f, head + Offset(-s * 9f, 1f))
        // The one crumb it holds up to its mouth, in two tiny paws.
        drawCircle(MoPropSand, 3.5f, head + Offset(-s * 7f, 7f - nibble))
        drawCircle(MoPropGrey, 2.4f, head + Offset(-s * 3f, 9f - nibble))
    }

    override val ownsTheFloor get() = true
}

/** He plays starving like a stage actor, mitten to his temple, and peeks to check that someone is watching. */
private object GreatSwoon : MoAct("hungry_great_swoon", MoOccasion.Hungry) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = if (v.chance(50, salt = 1)) 1f else -1f
        val b = MoBodies.of(body)
        val g = cycle(t, 5f)
        val further = ease((g - 0.7f) / 0.08f) * (1f - ease((g - 0.9f) / 0.1f))
        val peeking = if (v.chance(35, salt = 3)) g in 0.5f..0.57f || g in 0.62f..0.69f else g in 0.55f..0.68f
        val peekLeft = v.chance(50, salt = 2)
        val shut = MoEye(shape = MoEyeShape.Shut)
        val peek = MoEye(lidDrop = 0.35f, iris = 0.6f)
        val sigh = pulse(t, 2.5f)
        // His elbow shows outside the silhouette while the mitten rests beside his eye.
        val temple = MoArm(Offset(-16f, b.eyeY - 19f - b.shoulderY), bend = 0.9f)
        val belly = MoArm(Offset(-46f, 22f), bend = 0.4f)
        val idle = moIdle(t, energy = 0.7f)
        return idle.copy(
            leftEye = if (peeking && peekLeft) peek else shut,
            rightEye = if (peeking && !peekLeft) peek else shut,
            brows = MoBrows(MoBrowKind.Worried, lift = 4f + 2f * further),
            mouth = if (peeking) MoMouth.Flat(8f) else MoMouth.Oh(6f + 1.5f * sigh),
            leftArm = if (s > 0f) temple else belly,
            rightArm = if (s > 0f) belly else temple,
            armsInFront = true,
            legs = if (s > 0f) MoLegs(right = MoLeg(swing = 5f)) else MoLegs(left = MoLeg(swing = 5f)),
            tilt = s * (v.between(9f, 13f, salt = 4) + 3f * further),
            sway = idle.sway + s * (18f + 5f * further),
            flare = -8f,
            offset = Offset(s * 5f, -5f - 3f * further),
            squash = -0.015f + 0.02f * sigh,
            blinks = false,
        )
    }
}

/** A belt of plates glides past his feet, fork at the ready, and every single plate is empty. */
private object BeltOfEmptyPlates : MoAct("hungry_belt_of_empty_plates", MoOccasion.Hungry, intensity = MoIntensity.Mild) {
    // The plates travel from inside one end housing to inside the other.
    private const val FROM = -76f
    private const val TRAVEL = 382f

    private fun dir(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun plates(v: MoVariant) = 3 + v.pick(2, salt = 2)

    private fun spacingSeconds(v: MoVariant) = v.between(2.2f, 3f, salt = 6)

    private fun forkLeft(v: MoVariant) = v.chance(50, salt = 7)

    private fun droop(t: Float, v: MoVariant): Float {
        val g = cycle(t, spacingSeconds(v), offset = 0.5f)
        return ease((g - 0.6f) / 0.25f) * (1f - ease((g - 0.9f) / 0.1f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val dir = dir(v)
        // Half way in at t = 0, which is a plate right in front of him.
        val g = cycle(t, spacingSeconds(v), offset = 0.5f)
        // He follows a plate across and eases back to the next, so his gaze never jumps.
        // The return straddles the wrap, so his eyes are dead centre when the plate is.
        val track = if (g in 0.07f..0.93f) mix(-1f, 1f, (g - 0.07f) / 0.86f) else mix(1f, -1f, ease(cycle(g, 1f, offset = 0.07f) / 0.14f))
        val near = 1f - abs(track)
        val eye = MoEye(gaze = Offset(dir * 0.9f * track, 0.85f), scale = 1f + 0.05f * near)
        val fork = MoArm(Offset(30f, -40f + 8f * droop(t, v)), bend = 0.6f)
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = 2f + 5f * near),
            mouth = when {
                abs(g - 0.5f) > 0.17f -> MoMouth.Flat(10f)
                v.chance(30, salt = 8) -> MoMouth.TongueOut
                else -> MoMouth.Oh(5f)
            },
            leftArm = if (forkLeft(v)) fork else MoArm.Down,
            rightArm = if (forkLeft(v)) MoArm.Down else fork,
            tilt = dir * 2f * track,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) = floorShadow(CENTRE, 150f, MoPropShadow)

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = if (forkLeft(v)) -1f else 1f
        val hand = if (forkLeft(v)) rig.leftHand else rig.rightHand
        // The fork sinks outward as his hope does.
        withTransform({ rotate(side * 18f * droop(t, v), hand) }) {
            limb(hand, hand + Offset(0f, -44f), MoPropSilver, 6f)
            block(hand + Offset(0f, -48f), 18f, 10f, MoPropSilver, corner = 3f)
            for (prong in -1..1) limb(hand + Offset(prong * 6.5f, -50f), hand + Offset(prong * 6.5f, -64f), MoPropSilver, 4f)
        }
        grippingMitten(rig, side)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val dir = dir(v)
        val n = plates(v)
        val lap = n * spacingSeconds(v)
        val speed = TRAVEL / lap
        chunk(Offset(CENTRE, FEET - 10f), 300f, 20f, MoPropCharcoal, MoPropInk, corner = 4f, drop = 6f)
        for (index in 0 until 10) {
            val x = -35f + (index * 30f + dir * t * speed).mod(300f)
            block(Offset(x, FEET - 13f), 6f, 4f, MoPropGreyDark, corner = 2f)
        }
        val rims = listOf(MoPropTeal, MoPropPink, MoPropSky, MoPropYellow)
        val firstRim = v.pick(rims.size, salt = 3)
        val leftover = v.pick(n, salt = 4)
        // Nothing is drawn outside his box, so a plate is cut off where it goes under a housing.
        clipRect(left = -30f, top = 0f, right = 260f, bottom = FEET + 6f) {
            for (index in 0 until n) {
                val along = cycle(t, lap, offset = index / n.toFloat() + 0.5f)
                val at = Offset(if (dir > 0f) FROM + along * TRAVEL else FROM + TRAVEL - along * TRAVEL, FEET - 25f)
                plate(at, rims[(firstRim + index) % rims.size])
                if (index == leftover) {
                    drawCircle(MoPropSand, 3f, at + Offset(5f, -2f))
                }
            }
        }
        for (end in listOf(-1f, 1f)) {
            val at = Offset(CENTRE + end * 145f, FEET - 25f)
            chunk(at, 30f, 44f, MoPropSteel, MoPropGreyDark, corner = 7f)
            block(at + Offset(-end * 11f, 0f), 5f, 22f, MoPropInk, corner = 2.5f)
        }
    }

    private fun DrawScope.plate(at: Offset, rim: Color) {
        oval(at + Offset(0f, 3f), 31f, 7f, MoPropGrey)
        oval(at, 31f, 6.5f, MoPropWhite)
        oval(at, 22f, 4f, rim)
        oval(at, 17f, 2.8f, MoPropWhite)
    }

    override val ownsTheFloor get() = true
}

internal val hungryActs3: List<MoAct> = listOf(
    WatchingTheBird,
    GreatSwoon,
    BeltOfEmptyPlates,
)
