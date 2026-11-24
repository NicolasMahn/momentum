package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropBrown
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropMoon
import de.nicolasmahn.momentum.core.ui.theme.MoPropNavy
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
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
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Sleepy on his feet, second helping: the hour before bed, when he is tired, half asleep, or
 * already gone while still standing. None of these is in bed.
 */

/** Eased progress of [p] from [from] to [to]; the two are always distinct constants. */
private fun span(p: Float, from: Float, to: Float): Float = ease((p - from) / (to - from))

private fun mix(a: Float, b: Float, k: Float): Float = a + (b - a) * k

private fun mix(a: Offset, b: Offset, k: Float): Offset = a + (b - a) * k

private fun mix(a: MoArm, b: MoArm, k: Float): MoArm = MoArm(mix(a.reach, b.reach, k), mix(a.bend, b.bend, k))

private fun MoVariant.side(salt: Int): Float = if (chance(50, salt)) 1f else -1f

private val shutEye = MoEye(shape = MoEyeShape.Shut)

/** Walking in his sleep: eyes shut, both arms stretched out ahead, a pillow dangling from one mitten. */
private object Sleepwalking : MoAct("sleepy_sleepwalking", MoOccasion.Sleepy) {
    private const val STEP_SECONDS = 1.4f

    private fun direction(v: MoVariant) = v.side(salt = 1)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        // Shifted so the still frame already has a foot in the air.
        val step = wave(t + 0.35f, STEP_SECONDS)
        val idle = moIdle(t, energy = 0.4f)
        val dir = direction(v)
        // The arm on the side he walks to is held straight out ahead of him, the sleepwalker's arm;
        // the other hangs out a little from his side, where the pillow dangles clear of his legs.
        val ahead = MoArm(Offset(41f, -6f + 3f * step), bend = 0.05f)
        val dragging = MoArm(Offset(14f, 2f * step), bend = 0.15f)
        return idle.copy(
            leftEye = shutEye,
            rightEye = shutEye,
            mouth = MoMouth.Oh(size = 5f + 1.5f * pulse(t, 2.8f)),
            leftArm = if (dir > 0f) dragging else ahead,
            rightArm = if (dir > 0f) ahead else dragging,
            armsInFront = true,
            legs = MoLegs(MoLeg(lift = 9f * max(step, 0f)), MoLeg(lift = 9f * max(-step, 0f))),
            offset = Offset(3f * wave(t, 9f), -2f * abs(step)),
            tilt = dir * 1.5f + 2.5f * step,
            // The flame trails behind the walk.
            sway = idle.sway - dir * 6f,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val dir = direction(v)
        // The pillow hangs from the hand that is not pointing the way, away from his legs.
        val hand = if (dir > 0f) rig.leftHand else rig.rightHand
        val stripe = v.of(listOf(MoPropSky, MoPropPink, MoPropTeal), salt = 3)
        // 50 degrees hangs the pillow's diagonal straight down from the gripped corner; the swing
        // runs a beat behind the step.
        val degrees = 50f + 14f * wave(t + 0.05f, STEP_SECONDS)
        withTransform({
            translate(hand.x, hand.y)
            // Mirrored onto the hand that holds it, so the pillow always hangs clear of his body.
            scale(-dir, 1f, Offset.Zero)
            rotate(degrees, Offset.Zero)
        }) {
            val centre = Offset(23f, 13f)
            // The filling bows the seams outward and leaves the little ears of cloth at each corner empty.
            val pillowcase = Path().apply {
                moveTo(-6f, -6f)
                quadraticTo(-4f, -11f, 3f, -5f)
                quadraticTo(24f, -14f, 48f, -6f)
                quadraticTo(64f, -12f, 56f, 0f)
                quadraticTo(65f, 13f, 56f, 27f)
                quadraticTo(64f, 43f, 48f, 34f)
                quadraticTo(23f, 42f, 1f, 33f)
                quadraticTo(-13f, 42f, -6f, 26f)
                quadraticTo(-15f, 13f, -6f, 1f)
                close()
            }
            withTransform({ translate(0f, 3.5f) }) { drawPath(pillowcase, MoPropSilver) }
            drawPath(pillowcase, MoPropCream)
            // Short puckers where the stuffing pulls away from two corners of the pillowcase.
            val far = if (v.chance(50, salt = 2)) 1f else -1f
            for (end in listOf(-1f, 1f)) {
                val corner = centre + Offset(end * 23f, end * far * 14f)
                curve(corner, corner - Offset(end * 4f, end * far * 5f),
                    corner - Offset(end * 10f, end * far * 6f), stripe, 3f)
            }
        }
        mitten(rig, hand, -dir)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val dir = direction(v)
        // The ground sliding past is the only thing that says he is walking rather than standing,
        // so the dashes are chunky and close enough together to read at the size of a thumbnail.
        for (index in 0..5) {
            // Each dash fades at the edge before its place in the track wraps.
            val along = cycle(t, 2f * STEP_SECONDS, offset = index / 6f)
            val x = if (dir > 0f) 236f - 272f * along else -36f + 272f * along
            val visible = span(along, 0f, 0.12f) * (1f - span(along, 0.88f, 1f))
            limb(Offset(x, 428f), Offset(x + 30f, 428f), MoPropGreyDark.copy(alpha = 0.8f * visible), 5.5f)
        }
    }

    override val ownsTheFloor get() = true
}

/** Not tired, he insists: rubbing one eye, a toy dangling from the other mitten, nearly nodding off. */
private object UpPastBedtime : MoAct("sleepy_up_past_bedtime", MoOccasion.Sleepy, intensity = MoIntensity.Mild) {
    private const val LOOP_SECONDS = 7f

    /** Where the toy's hand hangs on every body, so the toy clears the floor under the low one too. */
    private const val TOY_HAND_Y = 353f

    private const val TOY_SIZE = 1.15f

    /** The angle that puts the toy's weight straight under the paw it hangs by. */
    private const val HANG_DEGREES = 31.6f

    private fun rubbedSide(v: MoVariant) = v.side(salt = 1)

    /** 0 while the hand is on his eye, 1 while it has sunk. */
    private fun sunk(p: Float) = span(p, 0.60f, 0.68f) * (1f - span(p, 0.90f, 1f))

    private fun droop(p: Float) = span(p, 0.62f, 0.80f) * (1f - span(p, 0.85f, 0.87f))

    private fun start(p: Float) = span(p, 0.85f, 0.87f) * (1f - span(p, 0.89f, 0.93f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val shape = MoBodies.of(body)
        val side = rubbedSide(v)
        val p = cycle(t, LOOP_SECONDS)
        val sunk = sunk(p)
        val droop = droop(p)
        val start = start(p)

        val eyeX = if (side < 0f) shape.leftEyeX else shape.rightEyeX
        val circling = Offset(cos(t * 7f), sin(t * 7f)) * (3f * (1f - sunk))
        val onTheEye = Offset(-(shape.shoulderHalf - abs(eyeX - CENTRE)), shape.eyeY - shape.shoulderY) + circling
        val rubbing = mix(MoArm(onTheEye, bend = 0.5f), MoArm(Offset(-30f, 10f), bend = 0.3f), sunk).copy(warm = 1f - sunk)
        val toy = MoArm(Offset(8f, TOY_HAND_Y - shape.shoulderY + 4f * droop - 5f * start), bend = 0.1f)

        val lid = mix(mix(0.55f, 0.9f, droop), 0.2f, start)
        val free = MoEye(lidDrop = lid, gaze = Offset(0f, 0.7f * droop), iris = mix(0.76f, 0.6f, start))
        // The rubbed eye stays shut under the mitten and opens heavily once the hand has left it.
        val rubbed = if (sunk < 0.35f) shutEye else free.copy(lidDrop = mix(0.95f, lid, span(sunk, 0.35f, 0.9f)))

        val idle = moIdle(t, energy = 0.5f)
        return idle.copy(
            leftEye = if (side < 0f) rubbed else free,
            rightEye = if (side < 0f) free else rubbed,
            brows = if (start > 0.5f) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(MoBrowKind.Flat, lift = -1f),
            mouth = when {
                start > 0.5f -> MoMouth.Oh(size = 5f)
                droop > 0.5f -> MoMouth.Flat(width = 8f)
                else -> MoMouth.Pout
            },
            leftArm = if (side < 0f) rubbing else toy,
            rightArm = if (side < 0f) toy else rubbing,
            armsInFront = true,
            tilt = side * (3f + 3f * droop - 2f * start),
            flare = -8f * droop,
            squash = idle.squash - 0.02f * droop,
            offset = Offset(0f, -4f * start),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val outward = -rubbedSide(v)
        val hand = if (outward > 0f) rig.rightHand else rig.leftHand
        val (fur, dark) = v.of(
            listOf(MoPropWood to MoPropWoodDark, MoPropPink to MoPropPinkDark, MoPropTeal to MoPropTealDark, MoPropSky to MoPropBlueDark),
            salt = 2,
        )
        val bunny = v.chance(40, salt = 3)
        val patch = v.chance(50, salt = 4)
        val hang = HANG_DEGREES + 11f * wave(t, 2.3f)
        // The paw slides in his grip while he nods off, and the start snatches it back.
        val slip = 5f * droop(cycle(t, LOOP_SECONDS))
        withTransform({
            translate(hand.x, hand.y + slip)
            scale(outward * TOY_SIZE, TOY_SIZE, Offset.Zero)
            rotate(hang, Offset.Zero)
            // The toy is drawn upright round the middle of its body; this puts its raised paw in his hand.
            translate(16f, 30f)
        }) {
            val head = Offset(0f, -20f)
            limb(Offset(-9f, -8f), Offset(-16f, -30f), dark, 7f)
            limb(Offset(9f, -7f), Offset(16f, 2f), dark, 7f)
            oval(Offset(-7f, 14f), 5f, 6f, dark)
            oval(Offset(7f, 14f), 5f, 6f, dark)
            if (bunny) {
                // Long ears obey gravity, not the toy: undo its tilt so they hang straight down.
                withTransform({ rotate(-hang, head) }) {
                    oval(head + Offset(-9f, 10f), 4f, 13f, fur)
                    oval(head + Offset(9f, 10f), 4f, 13f, fur)
                }
            } else {
                drawCircle(fur, 4.5f, head + Offset(-8f, -9f))
                drawCircle(fur, 4.5f, head + Offset(8f, -9f))
            }
            oval(Offset.Zero, 12f, 14f, fur)
            if (patch) block(Offset(0f, 2f), 10f, 10f, MoPropCream, corner = 3f)
            drawCircle(fur, 11f, head)
            oval(head + Offset(0f, 3.5f), 5.5f, 4.5f, MoPropCream)
            drawCircle(MoPropBrown, 1.8f, head + Offset(0f, 1.5f))
            for (eye in listOf(-5f, 5f)) {
                limb(head + Offset(eye - 1.5f, -3f), head + Offset(eye + 1.5f, -3f), MoPropBrown, 2.5f)
            }
        }
        mitten(rig, hand, outward)
    }

    // The toy dangles just above the floor exactly where a neglected thing would lie beside him.
    override val ownsTheFloor get() = true
}

/** A cat sleeps against his warm base, so he holds very still and clamps down on a yawn. */
private object CatOnTheWarmSpot : MoAct("sleepy_cat_on_the_warm_spot", MoOccasion.Sleepy, intensity = MoIntensity.Mild) {
    private class Coat(val fur: Color, val shade: Color, val patch: Color?)

    private val coats = listOf(
        Coat(MoPropGrey, MoPropGreyDark, null),
        Coat(MoPropOrange, MoPropWood, MoPropCream),
        Coat(MoPropCream, MoPropSand, null),
        Coat(MoPropGreyDark, MoPropCharcoal, MoPropGrey),
    )

    private fun catSide(v: MoVariant) = v.side(salt = 2)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = catSide(v)
        val p = cycle(t, 8f, offset = 0.25f)
        val yawn = span(p, 0.45f, 0.55f) * (1f - span(p, 0.59f, 0.61f))
        val clamp = span(p, 0.59f, 0.61f) * (1f - span(p, 0.68f, 0.72f))
        val glance = span(p, 0.80f, 0.85f) * (1f - span(p, 0.90f, 0.95f))

        val lid = mix(0.45f + 0.15f * pulse(t, 5.2f) + 0.2f * yawn, 0.1f, clamp)
        val eye = MoEye(lidDrop = lid, gaze = mix(Offset(side * 0.8f, 0.9f), Offset.Zero, glance), iris = mix(0.76f, 0.65f, clamp))
        val raised = MoArm(Offset(26f, -30f - 6f * clamp), bend = 0.6f)
        val idle = moIdle(t, energy = 0.3f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (clamp > 0.5f) MoBrows(MoBrowKind.Raised, lift = 2f) else MoBrows(),
            mouth = when {
                clamp > 0.5f -> MoMouth.Flat(width = 12f)
                yawn > 0.05f -> MoMouth.Yawn(open = 0.35f * yawn)
                else -> MoMouth.Smile(width = 10f, depth = 7f)
            },
            leftArm = if (side < 0f) raised else MoArm.Down,
            rightArm = if (side < 0f) MoArm.Down else raised,
            tilt = -side * 2f,
            squash = idle.squash + 0.03f * yawn,
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val side = catSide(v)
        val coat = v.of(coats, salt = 1)
        val breath = 2f * pulse(t, 3.2f)
        val earsToday = v.chance(35, salt = 4)
        // 0.35 s apart, which is 0.048 of the ear's period.
        val ear = twitch(t, 7.3f, beat = 0.2f, offset = 0.7f) + if (earsToday) twitch(t, 7.3f, beat = 0.2f, offset = 0.652f) else 0f
        val flick = if (earsToday) 0f else twitch(t, 5f, beat = 0.5f, offset = 0.4f)

        val cat = Offset(CENTRE + side * 86f, FEET)
        floorShadow(cat.x + side * 4f, 66f, MoPropShadow)
        sleepingCat(cat, side, 1.2f, coat, breath, ear, flick)
        if (v.chance(25, salt = 3)) {
            val kitten = coats[(coats.indexOf(coat) + 1 + v.pick(coats.size - 1, salt = 5)) % coats.size]
            // The kitten lies on the cat's back, sunk in a little, and rides its breathing.
            val back = cat + Offset(-side * 12f, -1.2f * (40f + 2f * breath))
            sleepingCat(back, -side, 0.6f, kitten, breath = 2f * pulse(t + 0.9f, 2.1f), ear = 0f, flick = twitch(t, 6.1f, beat = 0.4f, offset = 0.8f))
        }
    }

    /** A curled-up cat lying on [floor] with its head towards [facing]. Only its back rises as it breathes. */
    private fun DrawScope.sleepingCat(floor: Offset, facing: Float, size: Float, coat: Coat, breath: Float, ear: Float, flick: Float) {
        withTransform({
            translate(floor.x, floor.y)
            scale(facing * size, size, Offset.Zero)
        }) {
            val head = Offset(34f, -19f)
            oval(Offset(0f, -22f - breath), 46f, 22f + breath, coat.fur)
            drawCircle(coat.fur, 19f, Offset(-24f, -27f - breath))
            coat.patch?.let { oval(Offset(8f, -10f), 22f, 8f, it) }
            shape(coat.shade, head.x - 11f to head.y - 11f, head.x - 7f to head.y - 25f, head.x + 1f to head.y - 14f)
            shape(coat.shade, head.x + 3f to head.y - 14f, head.x + 12f to head.y - 24f - 4f * ear, head.x + 14f to head.y - 8f)
            drawCircle(coat.fur, 17f, head)
            for (eye in listOf(-7f, 8f)) {
                curve(head + Offset(eye - 4.5f, -1f), head + Offset(eye, 4.5f), head + Offset(eye + 4.5f, -1f), MoPropInk, 2.8f)
            }
            drawCircle(MoPropPink, 2.8f, head + Offset(0.5f, 7f))
            // The tail wraps round the front of it, and only its tip ever moves.
            curve(Offset(-42f, -14f), Offset(-26f, 3f), Offset(10f, -7f - 8f * flick), coat.shade, 9f)
        }
    }

    override val ownsTheFloor get() = true
}

/** Asleep and smiling, he dreams of being a flame with an important job. */
private object DreamingBig : MoAct("sleepy_dreaming_big", MoOccasion.Sleepy) {
    private fun cloudSide(v: MoVariant) = v.side(salt = 2)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val idle = moIdle(t, energy = 0.4f)
        val smile = pulse(t, 4.8f)
        return idle.copy(
            leftEye = shutEye,
            rightEye = shutEye,
            mouth = MoMouth.Smile(width = 14f + 3f * smile, depth = 11f + 3f * smile),
            // His tip leans away from the cloud and keeps clear of it.
            sway = idle.sway - cloudSide(v) * 8f,
            squash = 0.02f * pulse(t, 3.6f) - 0.01f,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        // As big as fits between the edge of the view and his tip.
        val q = Offset(CENTRE + cloudSide(v) * 94f, 57f + 3f * wave(t, 4f))
        puff(q, 52f, MoPropSilver)
        val (colour, dark) = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropTeal to MoPropTealDark, MoPropPurple to MoPropPurpleDark, MoPropPink to MoPropPinkDark),
            salt = 3,
        )
        val flicker = 1f + 0.15f * wave(t, 0.3f)
        withTransform({ scale(1.35f, 1.35f, q) }) {
            when (v.pick(4, salt = 1)) {
                0 -> lighthouse(q, colour, flicker)
                1 -> balloon(q, colour, dark, flicker)
                2 -> candlestick(q, colour, dark, flicker)
                else -> lantern(q, colour, dark, flicker)
            }
        }
    }

    private fun DrawScope.dreamFlame(base: Offset, size: Float) = flameTongue(base, size, MoSpark, MoFlameBelly)

    private fun DrawScope.lighthouse(q: Offset, colour: Color, flicker: Float) {
        // shape() rounds every corner with a stroke 2.5 wide, so the tower is drawn that much slimmer.
        shape(colour, q.x - 9f to q.y + 20f, q.x - 5f to q.y - 3f, q.x + 5f to q.y - 3f, q.x + 9f to q.y + 20f)
        block(q + Offset(0f, 3f), 13.5f, 5f, MoPropWhite, corner = 1f)
        block(q + Offset(0f, 13f), 17f, 5f, MoPropWhite, corner = 1f)
        block(q + Offset(0f, -7f), 22f, 4f, MoPropNavy, corner = 2f)
        limb(q + Offset(-8f, -9f), q + Offset(-8f, -24f), MoPropNavy, 2.5f)
        limb(q + Offset(8f, -9f), q + Offset(8f, -24f), MoPropNavy, 2.5f)
        block(q + Offset(0f, -26f), 22f, 4f, MoPropNavy, corner = 2f)
        dreamFlame(q + Offset(0f, -9f), 5.4f * flicker)
    }

    private fun DrawScope.balloon(q: Offset, colour: Color, dark: Color, flicker: Float) {
        limb(q + Offset(-10f, 2f), q + Offset(-4f, 17f), MoPropGreyDark, 1.8f)
        limb(q + Offset(10f, 2f), q + Offset(4f, 17f), MoPropGreyDark, 1.8f)
        drawCircle(colour, 17f, q + Offset(0f, -9f))
        oval(q + Offset(0f, -9f), 6.5f, 17f, dark)
        block(q + Offset(0f, 8f), 12f, 4f, dark, corner = 2f)
        dreamFlame(q + Offset(0f, 17f), 3.2f * flicker)
        chunk(q + Offset(0f, 21f), 12f, 7f, MoPropWood, MoPropWoodDark, corner = 2f, drop = 2f)
    }

    private fun DrawScope.candlestick(q: Offset, colour: Color, dark: Color, flicker: Float) {
        drawCircle(dark, 6f, q + Offset(12f, 12f), style = Stroke(width = 3f))
        chunk(q + Offset(0f, 18f), 26f, 7f, colour, dark, corner = 3f, drop = 2f)
        block(q + Offset(0f, 3f), 11f, 23f, MoPropGold, corner = 2f)
        block(q + Offset(0f, 12f), 15f, 6f, colour, corner = 2f)
        dreamFlame(q + Offset(0f, -8f), 7f * flicker)
    }

    private fun DrawScope.lantern(q: Offset, colour: Color, dark: Color, flicker: Float) {
        drawCircle(MoPropGreyDark, 5f, q + Offset(0f, -21f), style = Stroke(width = 2.5f))
        block(q + Offset(0f, 2f), 20f, 28f, MoPropNavy, corner = 3f)
        dreamFlame(q + Offset(0f, 13f), 6f * flicker)
        block(q + Offset(0f, -13f), 26f, 6f, colour, corner = 3f)
        block(q + Offset(0f, 17f), 26f, 6f, dark, corner = 3f)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = cloudSide(v)
        val from = Offset(CENTRE + side * 88f, rig.body.top + 110f)
        val to = Offset(CENTRE + side * 94f, 125f)
        listOf(0.15f to 5f, 0.5f to 8f, 0.85f to 11f).forEachIndexed { index, (along, radius) ->
            // Each bubble swells a beat after the one below it, so the dream seems to travel up.
            drawCircle(MoPropSilver, radius * (0.8f + 0.3f * pulse(t - index * 0.8f, 2.4f)), mix(from, to, along))
        }
    }

    override val sparkles get() = false
}

/** His flame tip falls asleep before he does, and he blows it back upright. */
private object TipNodsOff : MoAct("sleepy_tip_nods_off", MoOccasion.Sleepy, intensity = MoIntensity.Mild) {
    private fun droopSide(v: MoVariant) = v.side(salt = 1)

    private fun twoPuffs(v: MoVariant) = v.chance(40, salt = 3)

    // The 0.3 offset means the still frame already has the tip curled well over.
    private fun progress(t: Float, v: MoVariant) = cycle(t, v.between(6f, 7.5f, salt = 4), offset = 0.3f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = droopSide(v)
        val depth = v.between(80f, 98f, salt = 2)
        val p = progress(t, v)

        // On a two-puff day the first, weaker puff lifts the tip halfway and it sinks right back.
        val halfLift = if (twoPuffs(v)) 0.45f * span(p, 0.64f, 0.67f) * (1f - span(p, 0.68f, 0.74f)) else 0f
        // The sinking is over well before he notices, so most of the loop rests with the tip curled.
        val droop = (ease(p / 0.36f) - halfLift) * (1f - span(p, 0.78f, 0.84f))
        val springing = (p - 0.78f).coerceIn(0f, 0.14f)
        val wobble = -18f * sin(springing * 70f) * (1f - ease(springing / 0.14f))
        val bump = sin(PI.toFloat() * (springing / 0.14f))

        val looking = span(p, 0.52f, 0.56f) * (1f - span(p, 0.84f, 0.90f))
        val eye = MoEye(
            lidDrop = mix(0.35f + 0.4f * droop, 0.2f, looking),
            gaze = mix(Offset(0f, 0.4f), Offset(side * 0.35f, -1f), looking),
        )
        val blowing = p in 0.70f..0.78f || (twoPuffs(v) && p in 0.58f..0.64f)
        val idle = moIdle(t, energy = 0.5f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (looking > 0.5f) MoBrows(MoBrowKind.Raised, skew = 8f) else MoBrows(),
            mouth = when {
                blowing -> MoMouth.Whistle
                p in 0.84f..0.93f -> MoMouth.Smile(width = 10f, depth = 6f)
                else -> MoMouth.Flat(width = 10f)
            },
            leftArm = mix(MoArm.Down, MoArm.Hip, looking),
            sway = idle.sway + side * (depth * droop + wobble),
            flare = -36f * droop + 8f * bump,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = progress(t, v)
        if (twoPuffs(v)) breath(rig, (p - 0.58f) / 0.08f, size = 11f, side = droopSide(v))
        breath(rig, (p - 0.70f) / 0.10f, size = 18f, side = droopSide(v))
    }

    /** One puff of breath on its way from his pursed mouth up to the tip, [k] of the way there. */
    private fun DrawScope.breath(rig: MoRig, k: Float, size: Float, side: Float) {
        if (k !in 0f..1f) return
        // It leaves on the side the Whistle mouth points to and passes beside his eye, not over it.
        val t = ease(k)
        val start = rig.mouth + Offset(10f, 4f)
        val end = rig.tip + Offset(-side * 6f, 34f)
        val beside = rig.rightEye + Offset(92f, 65f)
        val above = rig.rightEye + Offset(96f, -78f)
        val u = 1f - t
        val at = start * (u * u * u) + beside * (3f * u * u * t) +
            above * (3f * u * t * t) + end * (t * t * t)
        puff(at, size * ease(k / 0.2f) * (1f - 0.3f * k), MoPropSilver.copy(alpha = 1f - k * k * k * k))
    }
}

/** Asleep on his feet while a shooting star crosses behind him; he looks up a moment too late. */
private object SleptThroughIt : MoAct("sleepy_slept_through_it", MoOccasion.Sleepy) {
    private const val CROSSING = 0.30f

    private val skySpots = listOf(Offset(-18f, 70f), Offset(252f, 46f), Offset(246f, 190f), Offset(-30f, 200f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, 8f, offset = 0.12f)
        val first = v.side(salt = 5)
        val awake = span(p, 0.42f, 0.47f) * (1f - span(p, 0.74f, 0.82f))
        val breath = pulse(t, 3.6f)
        val idle = moIdle(t, energy = 0.4f)
        val asleep = idle.copy(
            leftEye = shutEye,
            rightEye = shutEye,
            mouth = MoMouth.Smile(width = 12f, depth = 9f),
            tilt = mix(4f + 1.5f * breath, 1f, awake),
            squash = 0.02f * breath - 0.01f,
            blinks = false,
        )
        if (awake < 0.02f) return asleep

        // A slit that opens under a lid that stays heavy: he never really wakes up.
        val eye = MoEye(
            open = mix(0.1f, 1f, awake),
            lidDrop = 0.5f,
            gaze = Offset(first * mix(-0.8f, 0.8f, span(p, 0.60f, 0.65f)), -0.6f),
        )
        val looking = awake > 0.6f
        return asleep.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (looking) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(),
            mouth = if (looking) MoMouth.Oh(size = 4f) else asleep.mouth,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val count = 2 + v.pick(2, salt = 4)
        val firstSpot = v.pick(skySpots.size, salt = 6)
        for (index in 0 until count) {
            val at = skySpots[(firstSpot + index) % skySpots.size]
            sparkle(at, 11f, 0.8f + 0.2f * pulse(t - index, 2.6f), MoPropMoon)
        }

        val p = cycle(t, 8f, offset = 0.12f)
        if (p >= CROSSING) return
        val s = p / CROSSING
        val dir = v.side(salt = 1)
        val y0 = if (v.chance(50, salt = 2)) 40f else 90f
        val colour = v.of(listOf(MoPropMoon, MoPropGold, MoPropSky), salt = 3)
        // It fades in and out at the edges of the view, so it never pops.
        val alpha = ease(s / 0.15f) * ease((1f - s) / 0.15f)
        fun at(s: Float) = Offset(if (dir > 0f) -38f + 306f * s else 268f - 306f * s, y0 + 60f * s - 30f * sin(PI.toFloat() * s))

        val head = at(s)
        // The tail grows out of the edge of the view instead of starting beyond it.
        val end = at(max(s - 0.25f, 0f))
        val along = end - head
        val length = along.getDistance()
        if (length > 1f) {
            val across = Offset(-along.y, along.x) / length * 8f
            val tail = Path().apply {
                moveTo(head.x + across.x, head.y + across.y)
                lineTo(end.x, end.y)
                lineTo(head.x - across.x, head.y - across.y)
                close()
            }
            drawPath(tail, colour.copy(alpha = 0.6f * alpha))
        }
        sparkle(mix(head, end, 0.55f), 8f, 1f, colour.copy(alpha = alpha))
        sparkle(mix(head, end, 0.9f), 5f, 1f, colour.copy(alpha = alpha))
        sparkle(head, 20f, 1f, colour.copy(alpha = alpha))
        drawCircle(colour.copy(alpha = alpha), 5f, head)
    }

    override val sparkles get() = false
}

internal val sleepyActs2: List<MoAct> = listOf(
    Sleepwalking,
    UpPastBedtime,
    CatOnTheWarmSpot,
    DreamingBig,
    TipNodsOff,
    SleptThroughIt,
)
