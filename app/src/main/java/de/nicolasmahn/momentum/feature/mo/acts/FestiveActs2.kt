package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoEyeWhite
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoFlameOuter
import de.nicolasmahn.momentum.core.ui.theme.MoInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBrown
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSnow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.ABOVE_THE_PICTURE
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodies
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoBrowKind
import de.nicolasmahn.momentum.feature.mo.MoBrows
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoEyeShape
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.flameTongue
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.threadFromAbove
import de.nicolasmahn.momentum.feature.mo.twitch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Festive, part two: a visitor that has come to his warmth, and a decoration he cannot leave alone.
 * Both loops are one fixed period with the rest written into it, so the still frame is the settled
 * moment and every motion returns to it.
 */

// ------------------------------------------------------------------------------------------------

/** A robin has landed on his outstretched mitten to warm up, and he dares not move, or even blink. */
private object RobinOnTheMitten : MoAct("festive_robin_on_the_mitten", MoOccasion.Festive, intensity = MoIntensity.Mild) {
    private const val HOP_PERIOD = 4.6f
    private const val BREATH = 2.8f

    /** Where a bird stands on the floor beside him, far enough out to clear his base on both bodies. */
    private const val FLOOR_X = 130f

    /** The bird drawn a quarter larger than its unit shape, the most the box allows beside his mitten. */
    private const val SIZE = 1.25f

    private class Plumage(val body: Color, val wing: Color, val breast: Color, val breastLift: Float, val cap: Color?, val cheek: Color?)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    /** A robin wears its red right over its face; the tit keeps a cream cheek under a blue cap. */
    private fun plumage(v: MoVariant) = if (v.chance(70, salt = 2)) {
        Plumage(MoPropBrown, MoPropWoodDark, MoPropRed, breastLift = 6f, cap = null, cheek = null)
    } else {
        Plumage(MoPropGreenDark, MoPropSteel, MoPropYellow, breastLift = 0f, cap = MoPropBlue, cheek = MoPropCream)
    }

    private fun hop(t: Float) = twitch(t, HOP_PERIOD, beat = 0.28f, offset = 0.6f)

    /** The head cocks for the 0.6 s after a hop: quick in, slow back. */
    private fun cock(t: Float): Float {
        val at = cycle(t, HOP_PERIOD, offset = 0.6f) * HOP_PERIOD
        return ease((at - 0.28f) / 0.12f) * (1f - ease((at - 0.62f) / 0.26f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val idle = moIdle(t, energy = 0.35f)
        // The hop startles him a little, and his pupils take the rest of the beat to recover.
        val startle = twitch(t, HOP_PERIOD, beat = 0.6f, offset = 0.6f)
        val smile = MoMouth.Smile(width = 9f, depth = 6f)
        val over = MoEye(gaze = Offset(s * 1f, -0.15f), iris = 0.76f - 0.2f * startle)
        val out = MoArm(Offset(36f, -10f), bend = 0.1f)
        return idle.copy(
            leftEye = over,
            rightEye = over,
            brows = MoBrows(MoBrowKind.Raised, lift = 2f),
            mouth = smile,
            leftArm = if (s < 0f) out else MoArm.Down,
            rightArm = if (s > 0f) out else MoArm.Down,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant, picture: Rect) {
        val count = 3 + v.pick(2, salt = 3)
        for (index in 0 until count) {
            val fall = cycle(t, 6.5f, offset = index / count.toFloat())
            val x = picture.left + 24f + index * (picture.width - 48f) / (count - 1) + sin(fall * 5f + index * 2f) * 10f
            val visible = ease(fall / 0.08f) * ease((1f - fall) / 0.08f)
            sparkle(Offset(x, picture.top + (FEET - picture.top) * fall), 6.5f, 1f, MoPropSnow.copy(alpha = 0.9f * visible))
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val plumage = plumage(v)
        val cockSide = if (v.chance(50, salt = 4)) 1f else -1f
        val floor = Offset(CENTRE + s * FLOOR_X, FEET - 2f)
        val hand = if (s < 0f) rig.leftHand else rig.rightHand
        bird(hand + Offset(0f, -12f), SIZE, s, t, hop(t), cockSide * cock(t), up = 0f, plumage)
        if (v.chance(15, salt = 5)) {
            // A smaller one on the floor, on its own beat, looking up at the one that got the mitten.
            val late = t + HOP_PERIOD / 2f
            floorShadow(floor.x, 14f, MoPropShadow)
            bird(floor, 0.9f, s, late, hop(late), -cockSide * cock(late), up = 1f, plumage)
        }
    }

    /** One round bird on [perch], facing Mo, who is on the -[s] side; [up] turns its face toward his. */
    private fun DrawScope.bird(perch: Offset, size: Float, s: Float, t: Float, hop: Float, cock: Float, up: Float, p: Plumage) =
        withTransform({ scale(size, size, perch) }) { unitBird(perch, s, t, hop, cock, up, p) }

    private fun DrawScope.unitBird(perch: Offset, s: Float, t: Float, hop: Float, cock: Float, up: Float, p: Plumage) {
        val fluff = 1f + 0.08f * pulse(t, BREATH)
        val feet = perch + Offset(0f, -5f * hop)
        val b = feet + Offset(0f, -18f)
        for (dx in listOf(-4.5f, 4.5f)) limb(b + Offset(dx, 6f), feet + Offset(dx, 0f), MoPropWoodDark, 2.5f)
        shape(MoPropWoodDark, (b.x + s * 11f) to (b.y - 3f), (b.x + s * 19f) to (b.y - 10f), (b.x + s * 18f) to (b.y + 1f))
        oval(b, 15f * fluff, 13f * fluff, p.body)
        oval(b + Offset(s * 5f, -1f - 3f * hop), 8f, 6f, p.wing)
        drawCircle(p.breast, 9.5f * fluff, b + Offset(-s * 5f, 4f - p.breastLift))
        oval(b + Offset(-s * 1f, 8f), 7f, 4f, MoPropCream)
        // The face turns as one piece about the body, which is all a round bird has for a neck.
        withTransform({ rotate(s * 35f * up + 15f * cock, b) }) {
            val eye = b + Offset(-s * 6f, -7f)
            p.cap?.let { oval(b + Offset(-s * 2f, -10f), 9f, 5f, it) }
            p.cheek?.let { drawCircle(it, 4.5f, eye) }
            drawCircle(MoPropInk, 2.4f, eye)
            drawCircle(MoPropWhite, 0.9f, eye + Offset(-0.8f, -0.8f))
            shape(MoPropYellow, (b.x - s * 12f) to (b.y - 5.5f), (b.x - s * 17.5f) to (b.y - 3.5f), (b.x - s * 12f) to (b.y - 1.5f))
        }
    }

    override val sparkles get() = false
    override val ownsTheFloor get() = true
}

// ------------------------------------------------------------------------------------------------

/** A bauble hangs beside him with a tiny Mo in it: he pats it, watches it swing down, and they grin. */
private object TinyMoInTheBauble : MoAct("festive_tiny_mo_in_the_bauble", MoOccasion.Festive) {
    private const val PERIOD = 3.6f

    /** From here to the end of the cycle the bauble hangs still, and the two of them share the moment. */
    private const val REST = 0.8f

    /** How far he steps away from the thread, so the ball hangs beside his edge and only tucks behind it on the inward swing. */
    private const val STEP = 20f

    private fun side(v: MoVariant) = if (v.chance(70, salt = 3)) -1f else 1f

    /** Smaller on the right, where his tip leans. */
    private fun radius(v: MoVariant) = if (side(v) < 0f) 26f else 22f

    private fun restY(v: MoVariant) = v.between(245f, 260f, salt = 4)

    private fun anchor(v: MoVariant) = Offset(CENTRE + side(v) * 107f, ABOVE_THE_PICTURE)

    /** How hard he pats it, in degrees for a thread as long as the visible drop. */
    private fun amplitude(v: MoVariant) = v.between(4.5f, 6.5f, salt = 5)

    private fun at(t: Float) = cycle(t, PERIOD, offset = 0.9f)

    private fun pat(t: Float) = twitch(t, PERIOD, beat = 0.3f, offset = 0.9f)

    /** Three swings that die away, exactly zero at both ends so the next pat meets a bauble at rest. */
    private fun swing(c: Float): Float = sin(c * 6f * PI.toFloat()) * (1f - c) * (1f - c)

    /** The reflection's two little hops of joy while the bauble hangs still. */
    private fun skip(t: Float) = twitch(t, PERIOD, beat = 0.22f, offset = 0.07f) + twitch(t, PERIOD, beat = 0.22f, offset = 0.99f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val c = at(t)
        val pat = pat(t)
        // The mitten waits 8 units under the bauble's lower side, right under the thread on either body, and
        // meets the ball only on the pat. His step away puts the thread STEP + 15 out from the shoulder.
        val shoulderY = MoBodies.of(body).shoulderY
        val raised = MoArm(Offset(STEP + 15f + 3f * pat, (restY(v) + radius(v) + 20f - shoulderY) - 16f * pat), bend = 0.35f)
        val resting = c >= REST
        val eye = if (resting) MoEye(shape = MoEyeShape.Happy) else MoEye(gaze = Offset(s * (0.85f + 0.15f * swing(c)), -0.3f))
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            mouth = if (resting) MoMouth.Grin(open = 0.5f, tongue = false) else MoMouth.Smile(),
            leftArm = if (s < 0f) raised else MoArm.Down,
            rightArm = if (s > 0f) raised else MoArm.Down,
            offset = Offset(-s * STEP, 0f),
            tilt = s * 1.5f * pat,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val r = radius(v)
        val length = restY(v) - ABOVE_THE_PICTURE
        val c = at(t)
        val swing = swing(c)
        val theta = s * amplitude(v) * swing * restY(v) / length
        val hang = Offset(sin(theta * PI.toFloat() / 180f), cos(theta * PI.toFloat() / 180f))
        val anchor = anchor(v)
        val centre = anchor + hang * length
        val colour = v.of(listOf(MoPropRed, MoPropGreen, MoPropBlue, MoPropPurple, MoPropGold), salt = 1)
        val rest = if (c > REST) skip(t) else 0f

        threadFromAbove(centre - hang * (r + 7f), MoPropGrey, 2.5f, anchor.x)
        withTransform({ rotate(-theta, centre) }) {
            block(Offset(centre.x, centre.y - r - 3f), 12f, 8f, MoPropGoldDark, corner = 3f)
            drawCircle(colour, r, centre)
            clipPath(Path().apply { addOval(Rect(centre, r)) }) {
                when (v.pick(3, salt = 2)) {
                    1 -> block(centre + Offset(0f, -r * 0.6f), 2f * r, r * 0.28f, MoPropCream, corner = 0f)
                    2 -> for (index in 0 until 5) {
                        val angle = index * 2f * PI.toFloat() / 5f
                        drawCircle(MoPropCream, r * 0.12f, centre + Offset(sin(angle), -cos(angle)) * (r * 0.7f))
                    }
                }
                // The tiny Mo sits low in the ball and slides against the swing, the way a reflection does.
                val size = r * 0.42f
                val base = centre + Offset(-r * 0.15f * swing, r * 0.55f - r * 0.12f * rest)
                flameTongue(base, size, MoFlameOuter, MoFlameBelly)
                for (dx in listOf(-0.3f, 0.3f)) {
                    val eye = base + Offset(dx * size, -size * 0.7f)
                    drawCircle(MoEyeWhite, size * 0.28f, eye)
                    drawCircle(MoInk, size * 0.17f, eye + Offset(0f, size * 0.03f))
                }
            }
            // It shines by one flat gloss mark and nothing else.
            curve(centre + Offset(-r * 0.62f, -r * 0.3f), centre + Offset(-r * 0.5f, -r * 0.62f), centre + Offset(-r * 0.2f, -r * 0.72f), MoPropWhite, 4f)
        }
    }

    override val sparkles get() = false
}

internal val festiveActs2: List<MoAct> = listOf(
    RobinOnTheMitten,
    TinyMoInTheBauble,
)
