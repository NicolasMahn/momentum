package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.domain.mo.MoSaveAct
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoEyeShape
import de.nicolasmahn.momentum.feature.mo.MoLeg
import de.nicolasmahn.momentum.feature.mo.MoLegs
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.abs

private class RunningStride(id: String, private val relay: Boolean) : MoAct(id, MoOccasion.Doing, saveAct = MoSaveAct.Running) {
    private fun pass(t: Float) = if (relay) ease(pulse(t + 2.1f, 4.2f)) else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val step = wave(t + 0.13f, if (relay) 0.91f else 0.68f)
        // He jogs on the spot. His knees come up and his feet stay where they are, so each leg
        // stays over the ankle of its shoe.
        fun leg(phase: Float) = MoLeg(lift = 18f * phase.coerceAtLeast(0f), swing = STANCE)
        fun elbow(phase: Float) = MoArm(Offset(17f - 15f * phase, -7f - 21f * phase), bend = 0.85f, inFront = true)
        val offer = pass(t)
        val right = elbow(step)
        val batonArm = right.copy(reach = right.reach + (Offset(40f, -53f) - right.reach) * offer)
        return moIdle(t, 1.15f).copy(
            legs = MoLegs(leg(step), leg(-step)),
            leftArm = elbow(-step), rightArm = batonArm,
            offset = Offset(-20f * offer, -1f - 3f * (1f - abs(step))),
            tilt = -2f + 1.2f * step + 4f * offer,
            mouth = MoMouth.Grin(open = 0.48f, tongue = false),
        ).eyes(MoEye(gaze = Offset(-0.6f + 1.5f * offer, -0.45f * offer)))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val trainers = v.of(Trainers)
        trainer(rig.leftFoot, toe = -1f, trainers)
        trainer(rig.rightFoot, toe = 1f, trainers)
        if (relay) {
            val grip = rig.rightHand
            val handoff = pass(t)
            val axis = Offset(-0.2f + 0.65f * handoff, -1f)
            val tip = grip + axis * 46f
            limb(grip - axis * 17f, tip, MoPropPurpleDark, 13f)
            limb(grip - axis * 16f, tip, MoPropPurple, 10f)
            limb(tip - axis * 3f, tip + axis * 1f, MoPropCream, 10f)
            grippingMitten(rig, 1f)
        }
    }
}

private val Trainers = listOf(
    MoTrainer(MoPropBlue, MoPropBlueDark),
    MoTrainer(MoPropTeal, MoPropTealDark),
    MoTrainer(MoPropPurple, MoPropPurpleDark),
)

/** How far each foot stands out from under him while he jogs. */
private const val STANCE = 6f

private object LittleVictory : MoAct("doing_generic_little_victory", MoOccasion.Doing, saveAct = MoSaveAct.Generic) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val pump = ease((pulse(t + 0.35f, 2.5f) - 0.30f) / 0.55f)
        val fist = MoArm(Offset(28f, -34f - 26f * pump), bend = 0.78f)
        val right = v.chance(50)
        return moIdle(t).copy(
            leftArm = if (right) MoArm.Hip else fist,
            rightArm = if (right) fist else MoArm.Hip,
            armsInFront = true,
            mouth = MoMouth.Grin(open = 0.43f + 0.2f * pump),
            tilt = (if (right) -1f else 1f) * (1.5f + 1.5f * pump),
            offset = Offset(0f, -5f * pump),
        ).eyes(MoEye(shape = MoEyeShape.Happy))
    }
}

internal val doingActs3: List<MoAct> = listOf(
    RunningStride("doing_running_morning_jog", relay = false),
    RunningStride("doing_running_relay_leg", relay = true),
    LittleVictory,
)
