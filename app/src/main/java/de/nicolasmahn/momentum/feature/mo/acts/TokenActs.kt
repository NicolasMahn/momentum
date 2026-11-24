package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
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
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoEyeShape
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.drawWorldRestToken
import de.nicolasmahn.momentum.feature.mo.drawWorldToken
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.mitten
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.sparkle

import kotlin.math.sin

/*
 * Mo with his tokens on an ordinary day: the passes and rest days he banks are things he owns and
 * is fond of. The tokens are the ones the streak dialog shows, drawn in his world's own colours.
 */

/** The colours a token can come in here: one face and its ledge per habit-like hue. */
private val TokenHues = listOf(
    MoPropGreen to MoPropGreenDark,
    MoPropYellow to MoPropYellowDark,
    MoPropTeal to MoPropTealDark,
    MoPropPurple to MoPropPurpleDark,
    MoPropBlue to MoPropBlueDark,
    MoPropPink to MoPropPinkDark,
)

/** Proud of his bank: he lifts the top token to admire it, then lays it back on the tower. */
private object TokenTower : MoAct("proud_token_tower", MoOccasion.Proud, intensity = MoIntensity.Mild) {
    private const val LOOP = 3.6f

    override val ownsTheFloor get() = true

    // He tips the top token up to admire its face, then lays it flat again.
    private fun lift(t: Float): Float {
        val phase = cycle(t, LOOP, offset = 0.35f)
        return ease(phase / 0.35f) * (1f - ease((phase - 0.48f) / 0.28f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = MoBodies.of(body)
        val up = lift(t)
        val hand = Offset(TOWER_X - 17f, towerTop(v) - 40f * up)
        val reach = MoArm(hand - Offset(CENTRE + b.shoulderHalf, b.shoulderY), bend = 0.2f)
        val look = Offset(0.8f, 0.35f - 0.3f * up)
        return moIdle(t).copy(
            leftEye = MoEye(gaze = look),
            rightEye = MoEye(gaze = look),
            mouth = MoMouth.Smile(width = 14f, depth = 10f + 4f * up),
            leftArm = MoArm.Hip,
            rightArm = reach,
            squash = 0f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        floorShadow(TOWER_X, 32f, MoPropShadow)
        repeat(chips(v)) { index ->
            val at = Offset(TOWER_X, FEET - 10f - index * CHIP_STEP)
            chip(at, index, v, lift = 0f)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        chip(rig.rightHand + Offset(17f, 0f), chips(v), v, lift(t))
        mitten(rig, rig.rightHand, 1f)
    }

    private fun DrawScope.chip(at: Offset, index: Int, v: MoVariant, lift: Float) {
        val (face, ledge) = TokenHues[(index + v.pick(TokenHues.size, salt = 2)) % TokenHues.size]
        scale(1f, 0.3f + 0.7f * lift, at) {
            drawWorldToken(at, CHIP_WIDTH / 2f, face, ledge, MoPropWhite.copy(alpha = 0.45f))
        }
    }

    private fun chips(v: MoVariant) = 3 + v.pick(4, salt = 1)
    private fun towerTop(v: MoVariant) = FEET - 10f - chips(v) * CHIP_STEP

    private const val TOWER_X = 242f
    private const val CHIP_STEP = 13f
    private const val CHIP_WIDTH = 60f
}

/** An ordinary moment: he holds one of his tokens out beside him, breathes on it and watches it gleam. */
private object PolishingAToken : MoAct("idle_polishing_a_token", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private const val LOOP = 3.4f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = cycle(t, LOOP, offset = 0.15f)
        val huff = if (c < 0.3f) sin(c / 0.3f * 3.14f) else 0f
        val look = Offset(-0.9f, 0.1f)
        return moIdle(t, energy = 0.8f).copy(
            leftEye = MoEye(gaze = look, lidDrop = 0.2f * (1f - huff)),
            rightEye = MoEye(gaze = look, lidDrop = 0.2f * (1f - huff)),
            mouth = if (huff > 0.2f) MoMouth.Oh(size = 6f + 4f * huff) else MoMouth.Smile(width = 12f, depth = 8f),
            leftArm = MoArm(Offset(19f, -20f), bend = 0.3f),
            rightArm = MoArm.Hip,
            tilt = -4f - 3f * huff,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (face, ledge) = v.of(TokenHues, salt = 1)
        val token = rig.leftHand + Offset(-4f, -19f)
        drawWorldToken(token, 27f, face, ledge, MoPropWhite.copy(alpha = 0.45f))
        val c = cycle(t, LOOP, offset = 0.15f)
        // The breath fogs the token, and as it clears the token catches the light.
        if (c < 0.45f) {
            val fog = ease(c / 0.15f) * (1f - ease((c - 0.15f) / 0.3f))
            oval(token + Offset(0f, 2f), 20f, 14f, MoPropWhite.copy(alpha = 0.45f * fog))
        } else {
            val gleam = sin((c - 0.45f) / 0.55f * 3.14f)
            sparkle(token + Offset(-12f, -16f), 10f, gleam, MoPropWhite)
            sparkle(token + Offset(14f, 10f), 6f, gleam * 0.8f, MoPropWhite)
        }
        grippingMitten(rig, -1f)
    }
}

/** A day off: he leans on a rest token as big as a pillow and dozes in the sun. */
private object LeaningOnTheRestToken : MoAct("weekend_leaning_on_the_rest_token", MoOccasion.Weekend, intensity = MoIntensity.Mild) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = MoBodies.of(body)
        val breath = pulse(t, v.between(3.6f, 4.8f))
        return moIdle(t, energy = 0.5f).copy(
            leftEye = MoEye(shape = MoEyeShape.Shut),
            rightEye = MoEye(shape = MoEyeShape.Shut),
            mouth = MoMouth.Smile(width = 12f, depth = 7f + 2f * breath),
            leftArm = MoArm(Offset(2f, 30f), bend = 0.2f),
            rightArm = MoArm(Offset(TOKEN.x - (b.shoulderHalf + 115f) + 20f, TOKEN.y - 34f - b.shoulderY), bend = 0.35f),
            tilt = 7f + 1.5f * breath,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        floorShadow(TOKEN.x, 42f, MoPropShadow)
        drawWorldRestToken(TOKEN, 44f)
    }

    override val sparkles get() = false
    override val ownsTheFloor get() = true

    // Behind his right side, so he visibly leans against it.
    private val TOKEN = Offset(228f, FEET - 50f)
}

internal val tokenActs: List<MoAct> = listOf(TokenTower, PolishingAToken, LeaningOnTheRestToken)
