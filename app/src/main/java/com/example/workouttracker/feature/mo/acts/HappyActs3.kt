package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropBrown
import com.example.workouttracker.core.ui.theme.MoPropCharcoal
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropGrass
import com.example.workouttracker.core.ui.theme.MoPropGreenDark
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropInk
import com.example.workouttracker.core.ui.theme.MoPropLeaf
import com.example.workouttracker.core.ui.theme.MoPropMint
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropRedDark
import com.example.workouttracker.core.ui.theme.MoPropSand
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropSteel
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWood
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
import com.example.workouttracker.feature.mo.MoPalettes
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.grippingMitten
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.polyline
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Happy, part three: quiet company that chose him (a bird, a cat, a sunflower), and three ways of
 * not being able to keep it in (pattering feet, a very serious triangle, a kite).
 */

/** 1 between [from] and [to] and 0 outside, eased over [ramp] at both ends, so a phase of a loop never snaps. */
private fun window(x: Float, from: Float, to: Float, ramp: Float): Float =
    ease((x - from) / ramp) * (1f - ease((x - (to - ramp)) / ramp))

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

/** The point [u] of the way along the quadratic stroke that [curve] draws. */
private fun along(from: Offset, control: Offset, to: Offset, u: Float): Offset {
    val w = 1f - u
    return from * (w * w) + control * (2f * u * w) + to * (u * u)
}

/** A petal or a leaf: an oval growing out of [root], pointing up and then turned by [degrees]. */
private fun DrawScope.blade(root: Offset, degrees: Float, length: Float, width: Float, colour: Color) =
    rotate(degrees, root) { oval(root + Offset(0f, -length / 2f), width / 2f, length / 2f, colour) }

private fun DrawScope.shapeOf(colour: Color, points: List<Offset>) =
    shape(colour, *points.map { it.x to it.y }.toTypedArray())

/**
 * Where a point of the still world lies in his own space, which leans and shifts with him. It lets
 * something held, such as a string, reach a thing that must not swing about with his body.
 */
private fun MoPose.fromWorld(point: Offset): Offset {
    val feet = Offset(CENTRE, FEET)
    val moved = point - offset - Offset(shake, 0f) - feet
    val angle = -tilt * PI.toFloat() / 180f
    val turned = Offset(moved.x * cos(angle) - moved.y * sin(angle), moved.x * sin(angle) + moved.y * cos(angle))
    return feet + Offset(turned.x / max(1f - squash / 2f, 0.1f), turned.y / max(1f + squash, 0.1f))
}

/** A songbird chose his mitten: he dares not move, and cannot help checking that you see it too. */
private object BirdOnMitten : MoAct("happy_bird_on_mitten", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    private fun period(v: MoVariant) = v.between(6f, 9f, salt = 4)

    /** 0 while he stares at the bird, 1 while he beams at the viewer, once in every period. */
    private fun glance(t: Float, v: MoVariant) = window(cycle(t, period(v), offset = 0.1f), from = 0.72f, to = 0.92f, ramp = 0.05f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val glance = glance(t, v)
        val eye = MoEye(gaze = lerp(Offset(side, -0.3f), Offset.Zero, glance), iris = 0.82f, scale = 1.06f)
        val perch = MoArm(Offset(36f, -18f), bend = 0.12f)
        return moIdle(t, energy = 0.5f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = 5f),
            mouth = if (glance > 0.5f) MoMouth.Beam else MoMouth.Oh(size = 5f),
            leftArm = if (side < 0f) perch else MoArm.Down,
            rightArm = if (side > 0f) perch else MoArm.Down,
            // He does not even dare to breathe: only his tip still sways.
            squash = 0f,
            tilt = side * -2f,
            flare = 8f * glance,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = side(v)
        val (feathers, shade) = v.of(
            listOf(MoPropSky to MoPropBlue, MoPropYellow to MoPropYellowDark, MoPropRed to MoPropRedDark, MoPropMint to MoPropGreenDark),
            salt = 1,
        )
        // The bird does its one trick exactly while he looks away, so he misses it every time.
        val unseen = twitch(t, period(v), beat = 0.3f, offset = 0.32f) + twitch(t, period(v), beat = 0.3f, offset = 0.26f)
        val hops = v.chance(50, salt = 3)
        val hop = if (hops) unseen else 0f
        val cock = if (hops) 0f else window(cycle(t, period(v), offset = 0.1f), from = 0.76f, to = 0.9f, ramp = 0.03f)
        val chirp = twitch(t, 1.75f, beat = 0.35f)
        val flick = twitch(t, 3.5f, beat = 0.3f, offset = 0.4f)

        val hand = if (side > 0f) rig.rightHand else rig.leftHand
        val perch = hand + Offset(0f, -11f - 9f * hop)
        // Sized up as a whole, so it still reads at a thumbnail.
        withTransform({ scale(1.25f, 1.25f, perch) }) {
            val chest = perch + Offset(0f, -19f - chirp)
            limb(chest + Offset(side * 12f, 0f), chest + Offset(side * 23f, -7f - 5f * flick), shade, 5f)
            limb(chest + Offset(side * 12f, 3f), chest + Offset(side * 22f, -5f * flick), shade, 5f)
            for (leg in listOf(-4f, 4f)) limb(perch + Offset(leg, 0f), perch + Offset(leg, -8f), MoPropOrange, 2.5f)
            oval(chest, 16f, 13f, feathers)
            oval(chest + Offset(-side * 4f, 5f), 9f, 7f, MoPropCream)
            // The wing lies folded on its back and only opens for a hop.
            oval(chest + Offset(side * 5f, -2f - 9f * hop * abs(wave(t, 0.1f))), 9f, 5.5f, shade)

            val head = chest + Offset(-side * (10f + 4f * cock), -14f + 2f * cock)
            drawCircle(feathers, 9.5f, head)
            drawCircle(MoPropInk, 2.2f, head + Offset(-side * 3f, -2f))
            val beak = head.x - side * 8f
            val point = head.x - side * 17f
            shape(MoPropOrange, beak to head.y - 2f, point to head.y - 3f * chirp, beak to head.y + 1f)
            shape(MoPropOrange, beak to head.y + 1f, point + side * 2f to head.y + 2f + 4f * chirp, beak to head.y + 3f)
            for (ray in listOf(-1f, 0f, 1f)) {
                val from = head + Offset(ray * 7f, -14f)
                limb(from, from + Offset(ray * 4f, -7f), MoPropCream.copy(alpha = chirp), 2.5f)
            }
        }
    }
}

/** A cat has curled up against him for the warmth, and he keeps very still so as not to wake it. */
private object CatByTheFire : MoAct("happy_cat_by_the_fire", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    /** Once in a while he looks up from the cat at the viewer, holds it a moment, and looks back down. */
    private fun lookUp(t: Float) = window(cycle(t, 9f, offset = 0.55f), from = 0f, to = 0.22f, ramp = 0.06f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val up = lookUp(t)
        val eye = MoEye(gaze = lerp(Offset(side * 0.9f, 0.9f), Offset.Zero, up), iris = 0.78f, lidDrop = mix(0.3f, 0.1f, up))
        // The arm above the cat is held clear of it.
        val careful = MoArm(Offset(16f, 20f), bend = 0.45f)
        return moIdle(t, energy = 0.5f).copy(
            leftEye = eye,
            rightEye = eye,
            mouth = MoMouth.Smile(width = mix(14f, 19f, up), depth = mix(12f, 16f, up)),
            leftArm = if (side < 0f) careful else MoArm.Down,
            rightArm = if (side > 0f) careful else MoArm.Down,
            tilt = side * -2.5f,
            flare = -6f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = side(v)
        val (fur, marking) = v.of(
            listOf(MoPropOrange to MoPropWood, MoPropGrey to MoPropGreyDark, MoPropGreyDark to MoPropCharcoal, MoPropCream to MoPropSand),
            salt = 1,
        )
        val earTwitches = v.chance(50, salt = 3)
        val flick = twitch(t, 4.5f, beat = 0.5f, offset = 0.3f)
        val x = CENTRE + side * 110f
        // Breathing lifts its back only: the belly stays on the floor.
        val ry = 22f + 2.5f * pulse(t, 3.6f)
        val middle = Offset(x, FEET - ry)
        floorShadow(x - side * 6f, 56f, MoPropShadow)
        oval(middle, 44f, ry, fur)
        if (v.chance(50, salt = 4)) {
            for (dx in listOf(-4f, 11f, 26f)) {
                val top = middle.y - ry * sqrt(max(1f - (dx / 44f) * (dx / 44f), 0f))
                limb(Offset(x + side * dx, top + 5f), Offset(x + side * (dx + 3f), top + 17f), marking, 5f)
            }
        } else {
            oval(middle + Offset(side * 12f, -ry * 0.35f), 18f, 8f, marking)
        }

        // The tail comes round the front from the far end; only its tip is ever awake.
        val tail = Offset(x - side * 2f, FEET - 6f)
        curve(Offset(x + side * 42f, FEET - 16f), Offset(x + side * 40f, FEET - 1f), tail, marking, 9f)
        limb(tail, tail + Offset(-side * 13f, -1f - if (earTwitches) 0f else 7f * flick), marking, 9f)

        val head = Offset(x - side * 40f, FEET - 21f)
        for (ear in listOf(-1f, 1f)) {
            val lift = if (earTwitches && ear == side) 6f * flick else 0f
            shape(fur, head.x + ear * 3f to head.y - 12f, head.x + ear * 13f to head.y - 25f - lift, head.x + ear * 16f to head.y - 6f)
        }
        drawCircle(fur, 17f, head)
        for (eye in listOf(-8f, 8f)) {
            curve(head + Offset(eye - 4f, 0f), head + Offset(eye, 5f), head + Offset(eye + 4f, 0f), MoPropInk, 3f)
        }
        drawCircle(MoPropPink, 2.4f, head + Offset(0f, 6f))
    }

    override val ownsTheFloor get() = true
}

/** A potted sunflower has decided he is the sun: it bows to him as he sways, and he beams back. */
private object SunflowerSun : MoAct("happy_sunflower_sun", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** How far he leans toward the flower, from -1 to 1. The quarter period starts him leaning in. */
    private fun lean(t: Float) = wave(t + 1.1f, 4.4f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val lean = lean(t)
        val close = lean > 0.3f
        val eye = if (close) MoEye(shape = MoEyeShape.Happy) else MoEye(gaze = Offset(side * 0.85f, -0.6f))
        val idle = moIdle(t)
        val relaxed = MoArm(Offset(14f, 20f), bend = 0.3f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            mouth = if (close) MoMouth.Beam else MoMouth.Smile(),
            leftArm = if (side < 0f) relaxed else MoArm.Hip,
            rightArm = if (side > 0f) relaxed else MoArm.Hip,
            // His tip barely follows the lean: at full sway it would hide the flower's face.
            sway = idle.sway + side * 2f * lean,
            tilt = side * 3f * lean,
            offset = Offset(side * 4f * lean, 0f),
            flare = 6f * max(0f, lean),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = side(v)
        val (pot, potLedge) = v.of(listOf(MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark, MoPropRed to MoPropRedDark), salt = 2)
        val (petal, petalShade) = v.of(listOf(MoPropYellow to MoPropYellowDark, MoPropGold to MoPropGoldDark), salt = 3)
        // The flower answers him a moment late, and its petals a moment later still.
        val bow = lean(t - 0.3f)
        val spread = max(0f, lean(t - 0.55f))
        val x = CENTRE + side * 128f
        floorShadow(x, 36f, MoPropShadow)

        val root = Offset(x, 380f)
        val bend = Offset(x + side * 10f, 250f)
        val head = Offset(x - side * (13f + 5f * bow), 124f + 5f * bow)
        if (v.chance(35, salt = 4)) {
            val bud = Offset(x + side * 19f, 328f)
            curve(root, Offset(x + side * 22f, 356f), bud, MoPropLeaf, 5f)
            oval(bud + Offset(0f, -12f), 4.5f, 6f, petal)
            oval(bud + Offset(0f, -5f), 7f, 9f, MoPropGrass)
        }
        curve(root, bend, head, MoPropLeaf, 7f)
        // The lower leaf waves hello as he comes close.
        val hello = twitch(t, 4.4f, beat = 0.9f, offset = 0.1f) * wave(t, 0.45f)
        blade(along(root, bend, head, 0.28f), side * (55f + 18f * hello), 32f, 14f, MoPropGrass)
        blade(along(root, bend, head, 0.5f), -side * 50f, 28f, 13f, MoPropGrass)
        chunk(Offset(x, 402f), 50f, 40f, pot, potLedge, corner = 8f)
        chunk(Offset(x, 381f), 58f, 12f, pot, potLedge, corner = 5f, drop = 4f)

        // Narrowed, with its heart a little toward him, the head reads as turned his way.
        withTransform({ scale(0.9f, 1f, head) }) {
            for (index in 0 until 12) blade(head, index * 30f + 15f, 38f + 6f * spread, 14f, petalShade)
            for (index in 0 until 12) blade(head, index * 30f, 42f + 7f * spread, 15f, petal)
        }
        oval(head + Offset(-side * 3f, 0f), 16f, 17f, MoPropBrown)
        oval(head + Offset(-side * 3f, 0f), 9.5f, 10.5f, MoPropWood)
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** He cannot stand still: his feet patter like a dog's at the door, with one trembling beat of looking at you. */
private object TippyTaps : MoAct("happy_tippy_taps", MoOccasion.Happy) {
    /** One left-and-right pair of taps, in seconds. */
    private const val STEP = 0.26f

    /** 1 during the beat he holds still, 0 while he taps. */
    private fun hold(t: Float, v: MoVariant) = window(cycle(t, v.between(3f, 4.4f, salt = 1), offset = 0.2f), from = 0.8f, to = 1f, ramp = 0.04f)

    private fun lead(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    // A quarter of a step in, so the leading foot is in the air at t = 0.
    private fun step(t: Float) = wave(t + STEP / 4f, STEP)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val hold = hold(t, v)
        val tapping = 1f - hold
        val step = step(t) * lead(v)
        val still = hold > 0.5f
        val eye = when {
            still -> MoEye(iris = 0.82f, scale = 1.08f)
            v.chance(50, salt = 2) -> MoEye(shape = MoEyeShape.Star)
            else -> MoEye(shape = MoEyeShape.Happy)
        }
        // Mittens tucked up under his chin, either side of the mouth.
        val shape = MoBodies.of(body)
        val chin = shape.mouth.y + 8f - shape.shoulderY
        val tucked = MoArm(Offset(-34f, chin - 2f * abs(step) * tapping - 3f * hold), bend = 0.6f)
        return moIdle(t, energy = 1.6f).copy(
            // The low body keeps the cold at his feet, however happy he is.
            palette = MoPalettes.Golden.takeIf { body == MoBodyKind.Tall && v.chance(25, salt = 4) },
            leftEye = eye,
            rightEye = eye,
            brows = if (still) MoBrows(MoBrowKind.Raised, lift = 5f) else MoBrows(),
            mouth = if (still) MoMouth.Smile(width = 20f, depth = 17f) else MoMouth.Beam,
            leftArm = tucked,
            rightArm = tucked,
            armsInFront = true,
            legs = MoLegs(MoLeg(lift = 12f * max(0f, -step) * tapping), MoLeg(lift = 12f * max(0f, step) * tapping)),
            offset = Offset(0f, -2.5f * abs(step) * tapping),
            squash = 0.012f * wave(t, STEP / 2f) * tapping + 0.03f * hold,
            flare = 12f * hold,
            shake = wave(t, 0.05f) * 1.4f * hold,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val tapping = 1f - hold(t, v)
        val lead = lead(v)
        // Each foot comes down half a step after the other, and kicks up a little dust as it lands.
        val beat = cycle(t + STEP / 4f, STEP)
        for (side in listOf(-1f, 1f)) {
            val foot = if (side < 0f) rig.leftFoot else rig.rightFoot
            val since = if (side == lead) (beat + 0.5f).mod(1f) else beat
            val at = Offset(foot.x + side * (18f + 16f * since), FEET - 4f - 7f * since)
            puff(at, 11f * (1f - since), MoPropSand.copy(alpha = 0.6f * (1f - since) * tapping))
        }
    }
}

/** He plays the triangle with enormous dignity: one tiny ting, and one eye opens to check that you heard. */
private object SeriousTriangle : MoAct("happy_serious_triangle", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private const val BAR = 4f

    /** How far into the second ting's place in the bar, for the variant that plays two. */
    private const val SECOND = 0.0625f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun twice(v: MoVariant) = v.chance(50, salt = 3)

    // The flick is centred on the start of the bar, so t = 0 is the striker on the metal.
    private fun hit(t: Float, v: MoVariant) =
        twitch(t, BAR, beat = 0.16f, offset = 0.02f) + if (twice(v)) twitch(t, BAR, beat = 0.16f, offset = 0.02f - SECOND + 1f) else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val bar = cycle(t, BAR)
        val hit = hit(t, v)
        val peek = window(bar, from = 0.3f, to = 0.72f, ramp = 0.04f)
        val pleased = ease((bar - 0.3f) / 0.35f) * (1f - ease((bar - 0.8f) / 0.15f))
        val peeksRight = v.chance(50, salt = 4)
        val shut = MoEye(shape = MoEyeShape.Shut)
        val peeking = if (peek > 0.05f) MoEye(open = peek, lidDrop = 0.35f) else shut
        // Both mittens meet low in front of him, clear of his mouth: the triangle hangs at his side.
        val holding = MoArm(Offset(-16f, -2f), bend = 0.5f)
        val striking = MoArm(Offset(-84f - 8f * hit, 34f), bend = 0.3f)
        return moIdle(t, energy = 0.5f).copy(
            leftEye = if (peeksRight) shut else peeking,
            rightEye = if (peeksRight) peeking else shut,
            // Only the brow over the peeking eye goes up with it.
            brows = MoBrows(MoBrowKind.Raised, lift = 6f + if (peeksRight) 0f else 5f * peek, skew = if (peeksRight) 5f * peek else -5f * peek),
            // A smile with no depth is his composed straight line, so it can grow without a jump.
            mouth = MoMouth.Smile(width = mix(10f, 20f, pleased), depth = mix(5f, 16f, pleased)),
            leftArm = if (side < 0f) holding else striking,
            rightArm = if (side > 0f) holding else striking,
            armsInFront = true,
            flare = 4f * hit,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = side(v)
        val bar = cycle(t, BAR)
        val hand = if (side > 0f) rig.rightHand else rig.leftHand
        val apex = hand + Offset(0f, 22f)
        val inner = apex + Offset(-side * 23f, 40f)
        val outer = apex + Offset(side * 23f, 40f)
        // It swings a little after each ting and comes to rest before the next.
        rotate(3f * wave(t, 0.5f) * (1f - bar) * (1f - bar), hand) {
            limb(hand, apex, MoPropGrey, 2.5f)
            for ((colour, drop) in listOf(MoPropSteel to 2.5f, MoPropSilver to 0f)) {
                val down = Offset(0f, drop)
                limb(apex + down, inner + down, colour, 7f)
                limb(apex + down, outer + down, colour, 7f)
                // A triangle is open at one corner.
                limb(inner + down, outer + down - Offset(side * 12f, 0f), colour, 7f)
            }
        }

        // The striker is fixed in his mitten: drawn up before the ting, and it lands as the hand flicks in.
        val poised = ease((bar - 0.72f) / 0.2f) * (1f - ease((bar - 0.97f) / 0.03f))
        val mitten = if (side > 0f) rig.leftHand else rig.rightHand
        val stick = Offset(side * 58f, 8f - 12f * poised)
        limb(mitten - stick * 0.25f, mitten + stick, MoPropSteel, 4.5f)
        grippingMitten(rig, side)
        grippingMitten(rig, -side)

        val colour = v.of(listOf(MoPropSky, MoPropMint, MoPropGold), salt = 2)
        val struck = apex + Offset(-side * 15f, 22f)
        val middle = apex + Offset(0f, 26f)
        val tings = if (twice(v)) listOf(0f, SECOND) else listOf(0f)
        for (start in tings) {
            val ring = (bar - start) / 0.35f
            if (ring < 0f || ring >= 1f) continue
            sparkle(struck, 9f * (1f - ring), 1f, colour)
            // Sound leaves on the open side, away from his face.
            val out = if (side > 0f) -0.12f else PI.toFloat() + 0.12f
            for (index in 0..2) {
                val radius = 30f + 11f * index + 26f * ring
                fun at(angle: Float, r: Float) = middle + Offset(cos(angle), sin(angle)) * r
                // A quadratic stands in for the arc: its control sits where the end tangents cross.
                curve(at(out - 0.55f, radius), at(out, radius / cos(0.55f)), at(out + 0.55f, radius), colour.copy(alpha = (1f - ring) * (1f - 0.25f * index)), 3.5f)
            }
        }
    }

    override val sparkles get() = false
}

/** A kite tugs high in the corner, and every gust pulls him and his whole flame a little its way. */
private object FlyingAKite : MoAct("happy_flying_a_kite", MoOccasion.Happy) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    /** 0 in the calm, 1 at the height of a gust, sharpened so the gust is short. It starts mid-gust. */
    private fun gust(t: Float): Float = pulse(t + 1.5f, 3f).let { it * it }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val gust = gust(t)
        val pull = gust * v.between(0.7f, 1.2f, salt = 5)
        val eye = if (v.chance(50, salt = 6)) MoEye(shape = MoEyeShape.Star) else MoEye(gaze = Offset(side * 0.8f, -0.9f), iris = 0.8f)
        val idle = moIdle(t)
        val string = MoArm(Offset(21f + 6f * pull, -58f - 6f * pull), bend = 0.25f)
        val balance = MoArm(Offset(46f, 4f + 8f * pull), bend = 0.2f)
        // The foot away from the kite comes off the floor as he is pulled over.
        val lifted = MoLeg(lift = 4f * pull)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            mouth = MoMouth.Grin(open = 0.6f + 0.3f * gust),
            leftArm = if (side < 0f) string else balance,
            rightArm = if (side > 0f) string else balance,
            legs = MoLegs(left = if (side > 0f) lifted else MoLeg(), right = if (side < 0f) lifted else MoLeg()),
            // The wind that carries the kite streams his flame the same way.
            sway = idle.sway + side * (6f + 10f * pull),
            tilt = side * (1.5f + 3f * pull),
            offset = Offset(side * 5f * pull, 0f),
            flare = 4f * pull,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = side(v)
        val gust = gust(t)
        val colours = listOf(MoPropRed, MoPropYellow, MoPropSky, MoPropPurple, MoPropMint)
        val first = v.pick(colours.size, salt = 1)
        val near = colours[first]
        val far = colours[(first + 1 + v.pick(colours.size - 1, salt = 2)) % colours.size]

        // The kite keeps its corner of the sky while he leans, so it is placed in the world and
        // brought into his space: only the string, which starts in his mitten, joins the two.
        val pose = pose(t, v, rig.body.kind)
        val sky = Offset(CENTRE + side * 124f + 6f * wave(t, 1.5f), 56f + 8f * wave(t, 2f) - 6f * gust)
        val lean = side * 15f * PI.toFloat() / 180f
        fun kite(x: Float, y: Float) = pose.fromWorld(sky + Offset(x * cos(lean) - y * sin(lean), x * sin(lean) + y * cos(lean)))
        val top = kite(0f, -24f)
        val bottom = kite(0f, 38f)
        val inward = kite(-side * 23f, 0f)
        val outward = kite(side * 23f, 0f)

        val hand = if (side > 0f) rig.rightHand else rig.leftHand
        val slack = 1f - gust
        curve(hand, (hand + bottom) / 2f + Offset(-side * 26f * slack, 18f * slack), bottom, MoPropCream, 2.5f)
        grippingMitten(rig, side)

        val bows = 2 + v.pick(3, salt = 4)
        val tail = (0..bows * 2).map { index ->
            // The wriggle grows from nothing where the tail is tied on.
            val wriggle = sin(index * 1.2f - cycle(t, 1.25f) * 2f * PI.toFloat()) * 6f * minOf(index, 3) / 3f
            pose.fromWorld(sky + Offset(-side * 9.8f + side * 2f * index + wriggle, 36.7f + 9.5f * index))
        }
        polyline(MoPropCream, 2.5f, tail)
        for (index in 1..bows) {
            val knot = tail[index * 2]
            val colour = if (index % 2 == 0) near else far
            for (wing in listOf(-1f, 1f)) shapeOf(colour, listOf(knot, knot + Offset(wing * 8f, -5f), knot + Offset(wing * 8f, 5f)))
        }

        shapeOf(near, listOf(top, inward, bottom))
        shapeOf(far, listOf(top, outward, bottom))
        limb(top, bottom, MoPropWood, 2.5f)
        limb(inward, outward, MoPropWood, 2.5f)
    }

    override val sparkles get() = false
}

internal val happyActs3: List<MoAct> = listOf(
    BirdOnMitten,
    CatByTheFire,
    SunflowerSun,
    TippyTaps,
    SeriousTriangle,
    FlyingAKite,
)
