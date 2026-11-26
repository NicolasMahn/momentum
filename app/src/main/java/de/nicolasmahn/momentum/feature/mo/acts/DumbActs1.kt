package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropLeaf
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWater
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
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
import de.nicolasmahn.momentum.feature.mo.drop
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/*
 * Dumb, part one: books and building blocks, all used with great confidence and no success. Each
 * loop is a row of beats along one cycle, and every beat is eased into the next with the helpers
 * below, so the last beat always lands where the first one starts.
 */

private const val PI_F = PI.toFloat()

/** The blank stare he falls back into between ideas: each eye on its own errand. */
private val DRIFT_LEFT = Offset(-0.7f, -0.3f)
private val DRIFT_RIGHT = Offset(0.8f, 0.4f)

private fun mix(from: Float, to: Float, k: Float): Float = from + (to - from) * k

private fun mix(from: Offset, to: Offset, k: Float): Offset = Offset(mix(from.x, to.x, k), mix(from.y, to.y, k))

private fun mix(from: MoArm, to: MoArm, k: Float): MoArm = MoArm(mix(from.reach, to.reach, k), mix(from.bend, to.bend, k))

/** 0 before [at] and 1 once [over] of the cycle has passed it, eased. */
private fun step(p: Float, at: Float, over: Float = 0.04f): Float = ease((p - at) / over)

/** 1 between [from] and [to] and 0 outside, eased at both ends. */
private fun span(p: Float, from: Float, to: Float, over: Float = 0.04f): Float = step(p, from, over) * (1f - step(p, to, over))

private fun side(right: Boolean): Float = if (right) 1f else -1f

/** A task with the book motif is open: he studies a book whose cover picture is plainly upside down, from every angle. */
private object BookWrongWayUp : MoAct("dumb_book_wrong_way_up", MoOccasion.Dumb, intensity = MoIntensity.Mild) {
    private val covers = listOf(
        MoPropTeal to MoPropTealDark,
        MoPropPurple to MoPropPurpleDark,
        MoPropRed to MoPropRedDark,
        MoPropBlue to MoPropBlueDark,
    )

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val first = side(v.chance(50, salt = 3))
        val lean = v.between(6f, 9f, salt = 4)
        // Cubed, so he rests upright for a while between the two leans.
        val s = wave(t, 6.4f)
        val late = wave(t - 0.35f, 6.4f)
        // The eyes crawl along the cover in opposite directions; the shift starts them apart.
        val crawl = 0.7f * wave(t + 0.6f, 2.3f)
        val idle = moIdle(t, energy = 0.7f)
        // Held wide, so the book is big enough for the picture on it to be read at a glance.
        val hold = MoArm(Offset(-12f, 34f), bend = 0.3f)
        return idle.copy(
            leftEye = MoEye(gaze = Offset(-crawl, 0.9f), iris = 0.66f),
            rightEye = MoEye(gaze = Offset(crawl, 0.9f), iris = 0.66f),
            brows = MoBrows(MoBrowKind.Raised, skew = 7f),
            mouth = MoMouth.TongueOut,
            leftArm = hold,
            rightArm = hold,
            armsInFront = true,
            tilt = first * lean * s * s * s,
            sway = idle.sway - first * 5f * late * late * late,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (cover, coverDark) = v.of(covers, salt = 1)
        val c = (rig.leftHand + rig.rightHand) / 2f + Offset(0f, 12f)
        // A little narrower than his grip, so a mitten shows at each outer edge.
        val panel = (abs(rig.rightHand.x - rig.leftHand.x) - 10f - 6f) / 2f
        for (half in listOf(-1f, 1f)) {
            chunk(c + Offset(half * (panel / 2f + 3f), 0f), panel, 48f, cover, coverDark, corner = 6f, drop = 4f)
        }
        block(c, 8f, 50f, coverDark, corner = 3f)
        block(c + Offset(0f, -24f), panel * 2f - 4f, 4f, MoPropCream, corner = 2f)

        val e = c + Offset(panel / 2f + 3f, 0f)
        val shirt = v.of(listOf(MoPropLeaf, MoPropGold, MoPropPink), salt = 2)
        withTransform({ rotate(180f, e) }) {
            for (s in listOf(-1f, 1f)) {
                limb(e + Offset(0f, 3f), e + Offset(s * 10f, 17f), MoPropInk, 5f)
                limb(e + Offset(s * 5f, -4f), e + Offset(s * 16f, -9f), MoPropCream, 4f)
            }
            block(e, 13f, 16f, shirt, corner = 4f)
            drawCircle(MoPropCream, 9f, e + Offset(0f, -13f))
            for (s in listOf(-1f, 1f)) drawCircle(MoPropInk, 1.7f, e + Offset(s * 3.5f, -13f))
        }
    }
}

/** A task with the book motif is open: he searches everywhere for his book, which he is sitting on. */
private object SittingOnTheBook : MoAct("dumb_sitting_on_the_book", MoOccasion.Dumb, intensity = MoIntensity.Mild) {
    private val covers = listOf(
        MoPropTeal to MoPropTealDark,
        MoPropPurple to MoPropPurpleDark,
        MoPropGreen to MoPropGreenDark,
        MoPropRed to MoPropRedDark,
    )

    /** How high the book holds him: his base ends 31 above his feet, and the book is 54 thick. */
    private const val SEAT = 23f

    private fun p(t: Float) = cycle(t, 8f, offset = 0.1f)

    /** One eye's way through the search; [delay] lets the right eye trail the left. */
    private fun gaze(t: Float, first: Float, checking: Float, delay: Float, drift: Offset): Offset {
        val p = p(t)
        val far = Offset(-first, 0.2f + 0.12f * wave(t, 1.7f))
        var g = far
        g = mix(g, Offset(first, far.y), step(p, 0.28f + delay, over = 0.05f))
        g = mix(g, Offset(checking * 0.9f, -0.5f), step(p, 0.56f + delay / 2f))
        g = mix(g, Offset(0f, 0.1f), step(p, 0.74f))
        g = mix(g, drift, step(p, 0.88f))
        return mix(g, far, step(p, 0.95f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val first = side(v.chance(50, salt = 4))
        val checking = side(v.chance(50, salt = 5))
        val p = p(t)
        val check = span(p, 0.56f, 0.74f)
        val shrug = span(p, 0.74f, 0.88f)
        val blank = span(p, 0.88f, 0.96f)
        val shrugged = MoArm(Offset(40f, -22f), bend = 0.6f)
        val lifted = mix(mix(MoArm.Down, MoArm(Offset(36f, -50f), bend = 0.5f), check), shrugged, shrug)
        val other = mix(MoArm.Down, shrugged, shrug)
        // He leans into every look, and sits up straight for the shrug and the blank stare.
        var lean = -3f * first
        lean = mix(lean, 3f * first, step(p, 0.28f, over = 0.05f))
        lean = mix(lean, 2f * checking, step(p, 0.56f))
        lean = mix(lean, 0f, step(p, 0.74f))
        lean = mix(lean, -3f * first, step(p, 0.95f))
        val idle = moIdle(t, energy = 0.8f)
        return idle.copy(
            leftEye = MoEye(gaze = gaze(t, first, checking, 0f, DRIFT_LEFT), iris = mix(0.76f, 0.62f, blank)),
            rightEye = MoEye(gaze = gaze(t, first, checking, 0.04f, DRIFT_RIGHT), iris = mix(0.76f, 0.62f, blank)),
            // The blank stare is over before the cycle ends, so nothing of him changes at the seam.
            brows = when {
                p < 0.56f || p >= 0.95f -> MoBrows(MoBrowKind.Flat)
                p < 0.74f -> MoBrows(MoBrowKind.Raised, skew = 6f)
                p < 0.88f -> MoBrows(MoBrowKind.Raised, lift = 7f)
                else -> MoBrows(MoBrowKind.Raised, skew = 7f)
            },
            mouth = when {
                p < 0.56f || p >= 0.95f -> MoMouth.Flat(10f)
                p < 0.74f -> MoMouth.Oh(5f)
                p < 0.88f -> MoMouth.Flat(10f)
                else -> MoMouth.TongueOut
            },
            leftArm = if (checking < 0f) lifted else other,
            rightArm = if (checking > 0f) lifted else other,
            // His legs hang over the front of the book and swing, which is what says he sits on it.
            legs = MoLegs(MoLeg(lift = 5f + 5f * wave(t, 1.3f)), MoLeg(lift = 5f - 5f * wave(t, 1.3f))),
            tilt = lean,
            squash = idle.squash + 0.02f * shrug,
            // Just high enough for his base to rest on the book's top cover.
            offset = Offset(0f, -SEAT),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val (cover, coverDark) = v.of(covers, salt = 1)
        val ribbon = v.of(listOf(MoPropRed, MoPropGold, MoPropPink), salt = 2)
        val r = side(v.chance(50, salt = 3))
        floorShadow(CENTRE, 140f, MoPropShadow)
        // Thick rather than long, or it would read as a shelf: the thickness is what says book.
        block(Offset(CENTRE, FEET - 27f), 252f, 54f, coverDark, corner = 7f)
        // The pages stop short of the spine, which is at the end away from the ribbon.
        block(Offset(CENTRE + r * 6f, FEET - 26f), 236f, 34f, MoPropCream, corner = 3f)
        block(Offset(CENTRE, FEET - 49f), 252f, 12f, cover, corner = 5f)
        val top = Offset(CENTRE + r * 100f, FEET - 44f)
        limb(top, top + Offset(0f, 24f), ribbon, 8f)
        limb(top + Offset(0f, 24f), top + Offset(-4f, 32f), ribbon, 5f)
        limb(top + Offset(0f, 24f), top + Offset(4f, 32f), ribbon, 5f)
    }

    override val ownsTheFloor get() = true
}

/** He taps a block onto the one hole of a shape sorter it will never fit, with undimmed hope. */
private object SquareBlockRoundHole : MoAct("dumb_square_block_round_hole", MoOccasion.Dumb) {
    private val boxes = listOf(MoPropBlue to MoPropBlueDark, MoPropTeal to MoPropTealDark, MoPropPurple to MoPropPurpleDark)
    private val blocks = listOf(
        MoPropRed to MoPropRedDark,
        MoPropYellow to MoPropYellowDark,
        MoPropGreen to MoPropGreenDark,
        MoPropPink to MoPropPinkDark,
    )

    private fun boxSide(v: MoVariant) = side(v.chance(50, salt = 1))

    private fun taps(v: MoVariant) = 2 + v.pick(2, salt = 5)

    // Started at the top of the first tap, so the still frame shows the round hole and not the block over it.
    private fun p(t: Float, v: MoVariant) = cycle(t, 4.2f, offset = 0.25f / taps(v))

    /** How far the block is above the hole: bouncing taps in the first half, a long look in the second. */
    private fun lift(t: Float, v: MoVariant): Float {
        val p = p(t, v)
        return if (p < 0.5f) 18f * abs(sin(p / 0.5f * taps(v) * PI_F)) else 30f * span(p, 0.5f, 0.9f, over = 0.1f)
    }

    /** How hard the block is resting on the box top, which is simply how low it is. */
    private fun contact(t: Float, v: MoVariant): Float = (1f - lift(t, v) / 4f).coerceIn(0f, 1f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = boxSide(v)
        val p = p(t, v)
        val lift = lift(t, v)
        // A hand at y 316 puts the block's underside on the box top at lift 0, on either body, and
        // leaves the mitten showing above it.
        val tapping = MoArm(Offset(29f, 316f - MoBodies.of(body).shoulderY - lift), bend = 0.35f)
        val onBlock = Offset(side * 0.9f, 0.75f - lift / 18f * 0.25f)
        fun gaze(drift: Offset): Offset {
            var g = onBlock
            g = mix(g, Offset(side * 0.9f, 0.1f), step(p, 0.5f, over = 0.08f))
            g = mix(g, Offset(side * 0.9f, 0.85f), step(p, 0.68f))
            g = mix(g, drift, step(p, 0.8f))
            return mix(g, onBlock, step(p, 0.94f, over = 0.05f))
        }
        val blank = span(p, 0.8f, 0.95f)
        val idle = moIdle(t, energy = 0.8f)
        return idle.copy(
            leftEye = MoEye(gaze = gaze(DRIFT_LEFT), iris = mix(0.66f, 0.6f, blank)),
            rightEye = MoEye(gaze = gaze(DRIFT_RIGHT), iris = mix(0.66f, 0.6f, blank)),
            brows = MoBrows(MoBrowKind.Raised, skew = 6f),
            mouth = MoMouth.TongueOut,
            leftArm = if (side < 0f) tapping else MoArm.Hip,
            rightArm = if (side > 0f) tapping else MoArm.Hip,
            squash = idle.squash - 0.012f * contact(t, v),
            tilt = side * 2f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = boxSide(v)
        val (box, boxDark) = v.of(boxes, salt = 2)
        val b = Offset(CENTRE + side * 121f, FEET - 31.5f)
        floorShadow(b.x, 39f, MoPropShadow)
        chunk(b, 74f, 58f, box, boxDark, corner = 10f)
        // Narrower than the block, so it plainly cannot go in.
        oval(Offset(b.x, FEET - 59f), 19f, 6f, MoPropInk)
        // The front holes are no help either: neither is the shape he holds. They sit on a slant,
        // because two holes side by side over the rim would make the box a face.
        drawCircle(MoPropInk, 9f, b + Offset(-15f, -3f))
        val o = b + Offset(14f, 13f)
        if (v.pick(3, salt = 4) == 1) {
            block(o, 18f, 18f, MoPropInk, corner = 3f)
        } else {
            shape(MoPropInk, o.x - 9f to o.y + 7f, o.x + 9f to o.y + 7f, o.x to o.y - 8f)
        }
        val contact = contact(t, v)
        for (end in listOf(-1f, 1f)) {
            val from = Offset(b.x + end * 30f, FEET - 68f)
            limb(from, from + Offset(end * 6f, -6f), MoPropWhite.copy(alpha = contact), 4f)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (colour, dark) = v.of(blocks, salt = 3)
        val hand = if (boxSide(v) < 0f) rig.leftHand else rig.rightHand
        // It hangs under the mitten, which stays visible over it. The flat undersides are 49 under
        // the hand, which is what the hand line in the pose counts on; the star rests on its two lower points.
        val c = hand + Offset(0f, 30f)
        when (v.pick(3, salt = 4)) {
            0 -> chunk(c, 42f, 38f, colour, dark, corner = 5f, drop = 4f)
            1 -> for ((tone, down) in listOf(dark to 4f, colour to 0f)) {
                shape(tone, c.x - 21f to c.y + 19f + down, c.x + 21f to c.y + 19f + down, c.x to c.y - 20f + down)
            }
            else -> for ((tone, down) in listOf(MoPropGoldDark to 4f, MoPropGold to 0f)) {
                withTransform({ translate(c.x, c.y + down) }) {
                    shape(tone, 0f to -22f, 7f to -10f, 21f to -8f, 12f to 3f, 15f to 17f,
                        0f to 11f, -15f to 17f, -12f to 3f, -21f to -8f, -7f to -10f)
                }
            }
        }
    }

    override val ownsTheFloor get() = true
}

/** He is very proud of a tower built smallest block first, and keeps having to catch it. */
private object BiggestBlockOnTop : MoAct("dumb_biggest_block_on_top", MoOccasion.Dumb) {
    private val colours = listOf(
        MoPropRed to MoPropRedDark,
        MoPropYellow to MoPropYellowDark,
        MoPropBlue to MoPropBlueDark,
        MoPropGreen to MoPropGreenDark,
    )

    private fun towerSide(v: MoVariant) = side(v.chance(50, salt = 1))

    private fun sizes(v: MoVariant) = if (v.chance(35, salt = 2)) listOf(24f, 34f, 44f, 54f) else listOf(30f, 42f, 54f)

    private fun p(t: Float) = cycle(t, 5f, offset = 0.12f)

    // As far out as the picture allows, so the whole tower stands clear of him.
    private const val TOWER = 132f

    /** How far the top block has drifted toward him. It is 0 at both ends of the cycle. */
    private fun lean(t: Float, v: MoVariant): Float {
        val p = p(t)
        val grow = ease(p / 0.55f)
        val wobbling = v.between(11f, 15f, salt = 4) * grow * (1f + 0.25f * wave(t, 0.7f))
        return wobbling * (1f - step(p, 0.62f, over = 0.18f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = towerSide(v)
        val p = p(t)
        val sizes = sizes(v)
        val topBlockY = FEET - 2f - sizes.sum() + sizes.last() / 2f
        // The mitten lands flat on the top block and rides it back as the tower straightens.
        val build = MoBodies.of(body)
        val steady = MoArm(Offset(TOWER - build.shoulderHalf - lean(t, v), topBlockY - build.shoulderY), bend = 0.3f)
        val reach = step(p, 0.55f, over = 0.07f) * (1f - step(p, 0.85f, over = 0.15f))
        val towerArm = mix(MoArm.Down, steady, reach)
        val alarm = span(p, 0.4f, 0.78f)
        val alarmed = alarm > 0.5f
        val idle = moIdle(t)
        return idle.copy(
            leftEye = if (alarmed) MoEye(iris = 0.5f, gaze = Offset(side * 0.9f, -0.2f)) else MoEye(shape = MoEyeShape.Happy),
            rightEye = if (alarmed) MoEye(iris = 0.5f, gaze = Offset(side * 0.9f, -0.2f)) else MoEye(shape = MoEyeShape.Happy),
            brows = if (alarmed) MoBrows(MoBrowKind.Raised, lift = 6f) else MoBrows(),
            mouth = if (alarmed) MoMouth.Oh(8f) else MoMouth.Beam,
            leftArm = if (side < 0f) towerArm else MoArm.Hip,
            rightArm = if (side > 0f) towerArm else MoArm.Hip,
            // Leaning back to admire it, then toward it to save it.
            tilt = side * mix(-2f, 3f, alarm),
            // A proud little bounce while all is well.
            squash = idle.squash + 0.018f * pulse(t, 0.62f) * (1f - alarm),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = towerSide(v)
        val sizes = sizes(v)
        val turn = v.pick(4, salt = 3)
        val lean = lean(t, v)
        val x = CENTRE + side * TOWER
        floorShadow(x, 26f, MoPropShadow)
        var floor = FEET - 2f
        sizes.forEachIndexed { index, size ->
            val height = index / (sizes.size - 1f)
            val (colour, dark) = colours[(index + turn) % colours.size]
            val centre = Offset(x - side * lean * height * height, floor - size / 2f)
            withTransform({ rotate(-side * lean * 0.4f * height, centre) }) {
                chunk(centre, size, size, colour, dark, corner = 6f, drop = 4f)
            }
            floor -= size
        }
    }

    override val ownsTheFloor get() = true
}

/** He has noticed the tip of his own flame and tries to catch it, one mitten after the other. */
private object ChasingHisTip : MoAct("dumb_chasing_his_tip", MoOccasion.Dumb) {
    private const val SWIPE = 1.1f
    private const val FREEZE = 1.4f
    // Far enough that the tip plainly ducks away from the mitten, even at the size of a thumbnail.
    private const val LEAN = 48f

    private class Moment(val swiping: Boolean, val a: Float, val u: Float, val lean: Float, val frozen: Float)

    private fun moment(t: Float, v: MoVariant): Moment {
        val first = side(v.chance(50, salt = 1))
        val n = 2 + v.pick(3, salt = 2)
        val period = n * SWIPE + FREEZE
        // Into the second swipe, so the still frame catches the tip still leaning into the mitten.
        val s = cycle(t, period, offset = (SWIPE + 0.33f) / period) * period
        val k = (s / SWIPE).toInt().coerceIn(0, n)
        fun sideOf(index: Int) = if (index % 2 == 0) first else -first
        if (k < n) {
            val u = (s - k * SWIPE) / SWIPE
            val a = sideOf(k)
            // The tip leans into the raised mitten and only ducks across once it has nearly arrived.
            val from = if (k == 0) 0f else a * LEAN
            return Moment(true, a, u, mix(from, -a * LEAN, ease((u - 0.22f) / 0.34f)), 0f)
        }
        val still = s - n * SWIPE
        val last = -sideOf(n - 1) * LEAN
        val wiggle = if (still in 0.9f..1.25f) 5f * sin((still - 0.9f) / 0.35f * 2f * PI_F) else 0f
        return Moment(false, 0f, 0f, mix(last, 0f, ease(still / 0.3f)) + wiggle, still)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val m = moment(t, v)
        val hops = v.chance(50, salt = 3)
        val up = sin(m.u * PI_F)
        // As high as the arm goes, and just far enough out to clear his own body, which would hide it.
        val swipe = mix(MoArm.Down, MoArm.Up, ease(up))
        // In the freeze both mittens hover, ready, and sink again just before the next chase.
        val hover = span(m.frozen, 0f, FREEZE - 0.22f, over = 0.22f)
        val ready = mix(MoArm.Down, MoArm(Offset(20f, -30f), bend = 0.5f), hover)
        val crouch = if (m.u < 0.16f) sin(m.u / 0.16f * PI_F) else 0f
        val lookX = m.lean / LEAN * 0.6f
        val idle = moIdle(t, energy = 0.6f)
        return idle.copy(
            leftEye = MoEye(gaze = Offset(lookX + 0.2f, -1f), iris = 0.6f),
            rightEye = MoEye(gaze = Offset(lookX - 0.2f, -1f), iris = 0.6f),
            brows = MoBrows(MoBrowKind.Raised, lift = 4f),
            mouth = if (m.swiping) MoMouth.TongueOut else MoMouth.Flat(8f),
            leftArm = if (!m.swiping) ready else if (m.a < 0f) swipe else MoArm.Down,
            rightArm = if (!m.swiping) ready else if (m.a > 0f) swipe else MoArm.Down,
            armsInFront = true,
            sway = m.lean + wave(t, 0.83f) * 1.2f,
            // The flame ducks as well as leans, so a still frame shows it dodging and not him waving.
            flare = -39f * up,
            tilt = m.a * 6f * up,
            offset = if (hops && m.swiping) Offset(0f, -8f * up) else Offset.Zero,
            squash = idle.squash + if (hops && m.swiping) 0.03f * up - 0.03f * crouch else 0f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val m = moment(t, v)
        if (!m.swiping) return
        // The swish the tip leaves behind as it ducks away from the mitten.
        val swish = MoPropWhite.copy(alpha = 0.8f * span(m.u, 0.2f, 0.62f, over = 0.1f))
        val tip = rig.tip
        curve(tip + Offset(m.a * 20f, -12f), tip + Offset(m.a * 36f, 6f), tip + Offset(m.a * 24f, 28f), swish, 5.5f)
        curve(tip + Offset(m.a * 36f, -2f), tip + Offset(m.a * 46f, 10f), tip + Offset(m.a * 38f, 24f), swish, 4.5f)
    }
}

/** Too warm, he fans himself with a book, and is startled every time his flame shoots up. */
private object FanningTheFlame : MoAct("dumb_fanning_the_flame", MoOccasion.Dumb, intensity = MoIntensity.Strong) {
    private val covers = listOf(
        MoPropTeal to MoPropTealDark,
        MoPropRed to MoPropRedDark,
        MoPropPurple to MoPropPurpleDark,
        MoPropBlue to MoPropBlueDark,
    )

    private fun fanSide(v: MoVariant) = side(v.chance(50, salt = 2))

    private fun flaps(v: MoVariant) = 2 + v.pick(3, salt = 3)

    // The start is shifted by a part of one waft, so the still frame has the book swung and the flame up.
    private fun p(t: Float, v: MoVariant) = cycle(t, 3.6f, offset = 0.2f / flaps(v))

    /** The book's swing, from -1 to 1, positive toward him. It fans in the first half of the cycle and hangs still in the second. */
    private fun fan(t: Float, v: MoVariant): Float {
        val p = p(t, v)
        return if (p < 0.5f) sin(p / 0.5f * flaps(v) * 2f * PI_F) else 0f
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = fanSide(v)
        val p = p(t, v)
        val f = fan(t, v)
        val maxFlare = v.between(40f, 58f, salt = 4)
        // Each waft lifts the flame a moment late, and the last one lifts it highest and lets it sink slowly.
        val wafted = if (p < 0.535f) 0.7f * max(0f, sin((p - 0.035f) / 0.5f * flaps(v) * 2f * PI_F)) else 0f
        val last = step(p, 0.47f, over = 0.06f) * (1f - step(p, 0.58f, over = 0.22f))
        val startle = span(p, 0.5f, 0.8f)
        val blank = span(p, 0.8f, 0.96f)
        // Eyes shut in relief while he fans, which is why he never sees who does it.
        val cooling = startle < 0.5f && blank < 0.5f
        fun eye(drift: Offset): MoEye {
            var gaze = Offset(side * 0.5f, 0.2f)
            gaze = mix(gaze, Offset(0.3f, -1f), startle)
            gaze = mix(gaze, drift, blank)
            return MoEye(
                shape = if (cooling) MoEyeShape.Shut else MoEyeShape.Round,
                gaze = gaze,
                iris = mix(mix(0.76f, 0.5f, startle), 0.62f, blank),
            )
        }
        // Far enough out that the mitten shows under the book, instead of the book hanging in the air.
        val fanning = MoArm(Offset(30f + 8f * f, -16f), bend = 0.6f)
        val idle = moIdle(t, energy = 1.2f)
        return idle.copy(
            leftEye = eye(DRIFT_LEFT),
            rightEye = eye(DRIFT_RIGHT),
            brows = if (startle > 0.5f) MoBrows(MoBrowKind.Raised, lift = 7f) else MoBrows(),
            mouth = when {
                startle > 0.5f -> MoMouth.Oh(8f)
                blank > 0.5f -> MoMouth.TongueOut
                else -> MoMouth.Smile()
            },
            leftArm = if (side < 0f) fanning else MoArm.Down,
            rightArm = if (side > 0f) fanning else MoArm.Down,
            flare = maxFlare * max(wafted, last),
            squash = idle.squash + 0.03f * last,
            tilt = -side * 2f * (1f - step(p, 0.5f)) * step(p, 0f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = fanSide(v)
        val (cover, coverDark) = v.of(covers, salt = 1)
        val f = fan(t, v)
        val hand = if (side < 0f) rig.leftHand else rig.rightHand
        // A wide swing, mostly toward him: held nearly still beside his head it would be a telephone.
        withTransform({ rotate(-side * (7f + 17f * f), hand) }) {
            // The pages show along the outer and the lower edge, which is what makes it a book.
            block(hand + Offset(side * 4f, -32f), 50f, 62f, MoPropCream, corner = 3f)
            chunk(hand + Offset(side * 1f, -38f), 46f, 62f, cover, coverDark, corner = 5f, drop = 4f)
            block(hand + Offset(-side * 18f, -40f), 8f, 62f, coverDark, corner = 3f)
            block(hand + Offset(side * 5f, -50f), 22f, 12f, MoPropGold, corner = 3f)
        }
        grippingMitten(rig, side)
        // The air he sends his own way, which is the whole problem: two gusts across his face, one
        // over the eyes and one under them.
        val air = MoPropWhite.copy(alpha = max(0f, f) * 0.8f)
        val over = hand + Offset(-side * 34f, -70f)
        curve(over, over + Offset(-side * 22f, -9f), over + Offset(-side * 46f, 2f), air, 5.5f)
        val under = hand + Offset(-side * 30f, -8f)
        curve(under, under + Offset(-side * 14f, 7f), under + Offset(-side * 30f, 2f), air, 5f)

        // He is warm, as flames are: one bead of sweat on the far temple while he fans.
        val p = p(t, v)
        val warm = step(p, 0f) * (1f - step(p, 0.5f))
        val bead = cycle(t, 1.8f)
        val temple = (if (side < 0f) rig.rightEye else rig.leftEye) + Offset(-side * 30f, -40f + bead * 16f)
        drop(temple, 7f, MoPropWater.copy(alpha = warm * sin(bead * PI_F)))
    }

    override val sparkles get() = false
}

internal val dumbActs1: List<MoAct> = listOf(
    BookWrongWayUp,
    SittingOnTheBook,
    SquareBlockRoundHole,
    BiggestBlockOnTop,
    ChasingHisTip,
    FanningTheFlame,
)
