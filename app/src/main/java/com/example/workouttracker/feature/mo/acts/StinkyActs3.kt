package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.workouttracker.feature.mo.grippingMitten
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoPropYellowDark
import com.example.workouttracker.core.ui.theme.MoStink
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.ABOVE_THE_PICTURE
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.MoArm
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
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.threadFromAbove
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/* More of the days Care has been let slide: he smells, he knows it, and he tries to cover it up. */

/** One green wisp of smell, [rise] of the way up its [climb]: it fades in off him and out again, so no wrap shows. */
private fun DrawScope.stinkWisp(base: Offset, rise: Float, climb: Float, height: Float = 34f) {
    val foot = base + Offset(sin(rise * 5f) * 5f, -rise * climb)
    val colour = MoStink.copy(alpha = ease(rise / 0.15f) * (1f - rise * rise))
    val half = height / 2f
    curve(foot, foot + Offset(-11f, -half * 0.5f), foot + Offset(0f, -half), colour, 6f)
    curve(foot + Offset(0f, -half), foot + Offset(11f, -half * 1.5f), foot + Offset(0f, -height), colour, 6f)
}

/** A point along a quadratic curve, for a prop that has to travel an arc rather than a straight line. */
private fun bend(from: Offset, control: Offset, to: Offset, at: Float): Offset {
    val back = 1f - at
    return from * (back * back) + control * (2f * back * at) + to * (at * at)
}

/** He spritzes pink perfume around himself with his eyes shut in hope, then peeks and sees it all turn green. */
private object PerfumeInstead : MoAct("stinky_perfume_instead", MoOccasion.Stinky, setOf(MoBodyKind.Tall)) {
    private const val PERIOD = 5.2f

    // The loop starts a third of the way in, so the first frame already has puffs strung up beside him.
    private const val START = 0.35f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    private fun puffs(v: MoVariant) = v.pick(2, salt = 3) + 3

    /** Where puff [index] is in its life: they are spread evenly, so the plume is never empty. */
    private fun life(t: Float, index: Int, total: Int): Float =
        cycle(t, PERIOD, offset = START - index.toFloat() / total)

    /** One squeeze of the bulb for every puff, so nothing in the air is without a cause. */
    private fun squeeze(t: Float, v: MoVariant): Float {
        val total = puffs(v)
        return (0 until total).maxOf { twitch(t, PERIOD, beat = 0.45f, offset = START - it.toFloat() / total) }
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val h = side(v)
        val p = cycle(t, PERIOD, offset = START)
        val peek = ease((p - 0.55f) / 0.07f) * (1f - ease((p - 0.9f) / 0.06f))
        val sag = ease((p - 0.64f) / 0.07f) * (1f - ease((p - 0.9f) / 0.06f))
        val shut = MoEye(shape = MoEyeShape.Shut)
        val peeking = if (peek < 0.05f) shut else MoEye(
            open = peek,
            gaze = Offset(0.9f * h, -0.8f),
            iris = 0.76f - 0.16f * sag,
            lid = -0.3f * sag,
        )
        val leftPeeks = v.chance(50, salt = 4)
        // Far enough out that the mitten holding the bottle stands clear of his own silhouette.
        val holding = MoArm(Offset(26f, -30f - 4f * squeeze(t, v)), bend = 0.7f)
        val idle = moIdle(t)
        return idle.copy(
            leftEye = if (leftPeeks) peeking else shut,
            rightEye = if (leftPeeks) shut else peeking,
            brows = MoBrows(if (sag > 0.5f) MoBrowKind.Worried else MoBrowKind.Raised, lift = 3f * (1f - sag)),
            mouth = if (sag > 0.5f) MoMouth.Flat(9f) else MoMouth.Smile(width = 12f, depth = 8f * (1f - 2f * sag)),
            leftArm = if (h < 0f) holding else MoArm.Hip,
            rightArm = if (h < 0f) MoArm.Hip else holding,
            tilt = -h * 2f * (1f - sag),
            squash = idle.squash + 0.015f - 0.03f * sag,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val h = side(v)
        val (glass, glassLedge) = v.of(
            listOf(MoPropPurple to MoPropPurpleDark, MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark),
            salt = 1,
        )
        val bulb = if (v.chance(50, salt = 5)) MoPropPink else MoPropRed
        val hand = if (h < 0f) rig.leftHand else rig.rightHand
        val bottle = hand + Offset(-h * 7f, -3f)
        val cap = bottle + Offset(0f, -26f)
        val nozzle = cap + Offset(h * 16f, -13f)

        stinkWisp(Offset(CENTRE - h * 106f, rig.body.shoulderY - 50f), cycle(t, PERIOD / 2f, offset = 0.2f), climb = 110f)
        // This one rises out of the shoulder the plume passes, and meets it where the pink turns.
        stinkWisp(Offset(CENTRE + h * 92f, rig.body.shoulderY - 80f), cycle(t, PERIOD / 2f, offset = 0.7f), climb = 120f)

        // A short cone of a spray, kept in the clear air beside his flame where it has the contrast to
        // read: small at the nozzle, a swelling cloud by the time it is up level with his shoulders.
        val end = Offset(CENTRE + h * 100f, rig.body.top + 58f)
        val bow = Offset(nozzle.x + h * 10f, (nozzle.y + end.y) / 2f)
        val total = puffs(v)
        for (index in 0 until total) {
            val along = life(t, index, total)
            val at = bend(nozzle, bow, end, along) + Offset(sin(along * 7f + index) * 4f, 0f)
            val size = 6f + 18f * along
            // Pink going in, green coming out: the chain reads by colour alone, bottom to top. The
            // green eats the pink from the inside at full strength instead of blending with it,
            // because every mixture of the two is a brown smudge and every half-alpha a grey one.
            // The pink then leaves under a green that already covers it, so the puff dies one colour.
            val born = ease(along / 0.04f)
            val green = ease((along - 0.46f) / 0.14f)
            val fade = ease((1f - along) / 0.12f)
            puff(at, size, MoPropPink.copy(alpha = born * (1f - ease((along - 0.65f) / 0.2f))))
            puff(at, size * green, MoStink.copy(alpha = born * fade))
        }

        val press = squeeze(t, v)
        val bulbAt = hand + Offset(h * 19f, -21f)
        limb(cap, bulbAt, MoPropGoldDark, 5f)
        oval(bulbAt, 11f * (1f - 0.28f * press), 10f, bulb)
        limb(cap + Offset(0f, 2f), nozzle, MoPropGold, 7f)
        block(bottle + Offset(0f, -24f), 15f, 18f, MoPropGold, corner = 4f)
        chunk(bottle, width = 40f, height = 36f, colour = glass, ledge = glassLedge, corner = 14f)
        curve(bottle + Offset(-12f, 3f), bottle + Offset(-13f, -10f), bottle + Offset(-4f, -14f), MoPropWhite.copy(alpha = 0.6f), 4f)
        grippingMitten(rig, h)
    }

    override val sparkles get() = false
}

/** Little scented trees dangle beside him; he looks up at one hopefully, then at his own shoulder, and droops. */
private object ScentedTrees : MoAct("stinky_scented_trees", MoOccasion.Stinky, intensity = MoIntensity.Mild) {
    private val colours = listOf(
        MoPropTeal to MoPropTealDark,
        MoPropPink to MoPropPinkDark,
        MoPropYellow to MoPropYellowDark,
        MoPropBlue to MoPropBlueDark,
    )

    /** The side he looks up at, which is also where a third tree hangs. */
    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val g = side(v)
        val p = cycle(t, 7f, offset = 0.05f)
        val away = ease((p - 0.45f) / 0.1f) * (1f - ease((p - 0.92f) / 0.08f))
        val droop = ease((p - 0.55f) / 0.07f) * (1f - ease((p - 0.88f) / 0.06f))
        val gaze = Offset(0.7f * g, -0.9f) * (1f - away) + Offset(-0.9f * g, 0.5f) * away
        val idle = moIdle(t, energy = 0.7f)
        return idle.copy(
            leftEye = MoEye(gaze = gaze, lidDrop = 0.35f * droop, lid = -0.2f * droop),
            rightEye = MoEye(gaze = gaze, lidDrop = 0.35f * droop, lid = -0.2f * droop),
            brows = MoBrows(if (droop > 0.5f) MoBrowKind.Worried else MoBrowKind.Raised, lift = 3f * (1f - droop)),
            mouth = if (droop > 0.5f) MoMouth.Flat(10f) else MoMouth.Smile(width = 12f, depth = 8f * (1f - 2f * droop)),
            // The gaze alone barely reads at thumbnail size, so he leans the little he is allowed to.
            tilt = g * (2.5f - 4f * away),
            squash = idle.squash - 0.015f * droop,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val g = side(v)
        // Never fewer than two, so there is always one on each side of him.
        for (index in 0..1 + v.pick(2, salt = 1)) {
            // The first hangs far out on his side, the second far out on the other, the third short and close to his tip.
            val near = index == 2
            val anchor = Offset(CENTRE + (if (index == 1) -g else g) * (if (near) 58f else 108f), ABOVE_THE_PICTURE)
            // The near one ends above where the far ones begin, so two swinging trees never tangle.
            val topY = if (near) v.between(16f, 30f, salt = 6 + index) else v.between(66f, 90f, salt = 6 + index)
            val top = Offset(anchor.x, topY)
            val swing = 6f * (topY + 32f) / (topY + 32f - anchor.y) * wave(t + v.between(0f, 3f, salt = 9 + index), 3.1f + 0.6f * index)
            // A card turning on its thread. Overdriving the cosine keeps it flat and readable most of
            // the time and flicks it quickly through the edge, where its darker back comes round.
            val turn = (1.6f * cos(t * 0.62f + index * 1.9f)).coerceIn(-1f, 1f)
            val (front, back) = v.of(colours, salt = 3 + index)
            withTransform({ rotate(swing, anchor) }) {
                threadFromAbove(top, MoPropGrey, 2f)
                withTransform({ scale(0.22f + 0.78f * abs(turn), 1f, anchor) }) {
                    tree(top, if (near) 0.8f else 1f, if (turn < 0f) back else front)
                }
            }
        }
    }

    private fun DrawScope.tree(top: Offset, size: Float, colour: Color) {
        block(top + Offset(0f, 60f * size), 12f * size, 14f * size, MoPropWood, corner = 3f)
        listOf(16f, 23f, 30f).forEachIndexed { tier, half ->
            val y = top.y + tier * 16f * size
            shape(colour, top.x to y, top.x + half * size to y + 24f * size, top.x - half * size to y + 24f * size)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val g = side(v)
        // Half of his 7 s cycle, so a wisp is always just leaving the shoulder at the moment he looks at it.
        stinkWisp(
            Offset(CENTRE - g * 102f, rig.body.shoulderY - 44f),
            cycle(t, 3.5f, offset = 0.22f),
            climb = 170f,
            height = 36f,
        )
        listOf(
            Offset(CENTRE + g * 102f, rig.body.shoulderY - 44f),
            Offset(CENTRE + g * 118f, rig.body.shoulderY - 74f),
        ).forEachIndexed { index, base ->
            stinkWisp(base, cycle(t, 3.5f, offset = 0.55f + index * 0.25f), climb = 170f, height = 36f)
        }
    }

    override val sparkles get() = false
}

internal val stinkyActs3: List<MoAct> = listOf(
    PerfumeInstead,
    ScentedTrees,
)
