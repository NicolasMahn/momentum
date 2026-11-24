package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrass
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropLeaf
import de.nicolasmahn.momentum.core.ui.theme.MoPropNavy
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.domain.mo.MoSaveAct
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodies
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.pulse

private object SpoonfulOfPeas : MoAct("doing_eating_spoonful_of_peas", MoOccasion.Doing, saveAct = MoSaveAct.Eating) {
    private fun lift(t: Float) = ease(pulse(t + 0.5f, 3.2f) * 1.2f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val lift = lift(t)
        val frame = MoBodies.of(body)
        return moIdle(t, 0.65f).copy(
            leftArm = MoArm(Offset(-33f, 43f), bend = 0.65f),
            rightArm = MoArm(Offset(-54f + 6f * lift, 46f + (frame.mouth.y - frame.shoulderY - 35f) * lift), bend = 0.65f),
            armsInFront = true,
            mouth = MoMouth.Chew(open = 0.14f + 0.72f * lift),
        ).eyes(MoEye(gaze = Offset(-0.25f * (1f - lift), 0.78f)))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val bowl = rig.leftHand + Offset(27f, 2f)
        oval(bowl, 40f, 18f, MoPropTealDark)
        oval(bowl + Offset(0f, -4f), 40f, 16f, v.of(BowlColours))
        oval(bowl + Offset(0f, -12f), 39f, 8f, MoPropCream)
        for ((x, y) in Peas) drawCircle(MoPropLeaf, 5f, bowl + Offset(x, y - 14f))
        val spoon = rig.rightHand + Offset(-44f, -9f)
        limb(rig.rightHand + Offset(10f, 2f), spoon, MoPropSteel, 6f)
        oval(spoon, 12f, 5.5f, MoPropSilver)
        // He carries one pea up, then brings an empty spoon back to the bowl.
        val lap = cycle(t + 0.5f, 3.2f)
        val pea = 4.8f * (1f - ease((lap - 0.30f) / 0.07f) + ease((lap - 0.91f) / 0.07f))
        if (pea > 0f) drawCircle(MoPropLeaf, pea, spoon + Offset(0f, -4f))
        grippingMitten(rig, -1f)
        grippingMitten(rig, 1f)
    }
}

private val BowlColours = listOf(MoPropBlue, MoPropTeal)
private val Peas = listOf(-22f to 1f, -11f to -2f, 0f to 1f, 11f to -2f, 22f to 2f)

private object BiteOfApple : MoAct("doing_eating_bite_of_apple", MoOccasion.Doing, saveAct = MoSaveAct.Eating) {
    private fun bite(t: Float) = ease((pulse(t, 3.4f) - 0.28f) / 0.66f)
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val up = bite(t)
        val frame = MoBodies.of(body)
        return moIdle(t, 0.7f).copy(
            leftArm = MoArm(Offset(-36f - 21f * up, 45f + up * (frame.mouth.y - frame.shoulderY - 26f)), bend = 0.7f, inFront = true),
            mouth = MoMouth.Chew(open = 0.28f + 0.5f * pulse(t, 0.8f)),
        ).eyes(MoEye(gaze = Offset(-0.6f, 0.55f)))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val apple = rig.leftHand + Offset(17f, -14f)
        val colour = if (v.chance(50)) MoPropRed else MoPropGreen
        limb(apple + Offset(0f, -10f), apple + Offset(3f, -23f), MoPropWoodDark, 4f)
        oval(apple + Offset(10f, -20f), 8f, 4f, MoPropGrass)
        val bite = ease((t - 1.3f) / 0.22f)
        val notch = Path().apply { addOval(Rect(apple + Offset(18f, -9f), 12f * bite)) }
        clipPath(notch, ClipOp.Difference) {
            oval(apple + Offset(-7f, 0f), 13f, 16f, colour)
            oval(apple + Offset(7f, 0f), 13f, 16f, colour)
            oval(apple + Offset(-9f, -7f), 4f, 2.5f, MoPropCream)
        }
        grippingMitten(rig, -1f)
    }
}

private object DailyTablet : MoAct("doing_pills_daily_tablet", MoOccasion.Doing, saveAct = MoSaveAct.Pills) {
    private fun lap(t: Float) = cycle(t, 12f) * 12f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val sec = lap(t)
        val up = ease(sec / 1.4f) * (1f - ease((sec - 1.8f) / 1.2f))
        val pleased = ease((sec - 2f) / 0.8f) * (1f - ease((sec - 9.5f) / 1.2f))
        val cheer = pleased * pulse(sec - 3f, 3.4f)
        val frame = MoBodies.of(body)
        val idle = moIdle(t, 0.6f)
        return idle.copy(
            leftArm = MoArm(Offset(-26f, 42f - 6f * cheer), bend = 0.55f),
            rightArm = MoArm(Offset(-29f - 42f * up + 48f * pleased, 32f + up * (frame.mouth.y - frame.shoulderY - 23f) - 40f * pleased - 34f * cheer), bend = 0.72f),
            mouth = if (pleased > 0.12f) MoMouth.Grin(open = 0.35f + 0.33f * cheer, tongue = false) else MoMouth.Oh(size = 5f + 3f * up),
            tilt = 3f * cheer,
            offset = Offset(0f, -7f * cheer),
            armsInFront = true,
        ).eyes(MoEye(gaze = Offset(0.65f * (1f - pleased), 0.6f - 0.9f * pleased)))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val bottle = rig.leftHand + Offset(14f, -12f)
        chunk(bottle, 31f, 42f, v.of(BowlColours), MoPropTealDark, corner = 8f, drop = 3f)
        block(bottle + Offset(0f, -2f), 31f, 13f, MoPropCream, corner = 1f)
        block(bottle + Offset(0f, -24f), 22f, 9f, MoPropTealDark, corner = 2f)
        oval(bottle + Offset(0f, -28f), 11f, 3f, MoPropNavy)
        val tablet = rig.rightHand + Offset(-15f, -9f)
        val showing = 1f - ease((lap(t) - 1.35f) / 0.35f) + ease((lap(t) - 11f) / 0.8f)
        oval(tablet, 7f * showing, 4.5f * showing, MoPropCream)
        grippingMitten(rig, -1f)
        grippingMitten(rig, 1f)
    }
}

private object StandOnTheScale : MoAct("doing_weighing_stand_on_the_scale", MoOccasion.Doing, saveAct = MoSaveAct.Weighing) {
    override val ownsTheFloor get() = true

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind) = moIdle(t, 0.55f).copy(
        offset = Offset(0f, -14f),
        mouth = MoMouth.Smile(width = 12f, depth = 10f),
    ).eyes(MoEye(gaze = Offset(0f, 0.8f * pulse(t + 0.8f, 4.6f))))

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val at = Offset(CENTRE, FEET - 11f)
        floorShadow(at.x, 80f, MoPropShadow)
        chunk(at, 156f, 27f, v.of(BowlColours), MoPropTealDark, corner = 11f, drop = 4f)
        block(at + Offset(0f, 3f), 31f, 10f, MoPropNavy, corner = 4f)
        oval(at + Offset(0f, 3f), 11f, 3.5f, MoPropSilver)
        limb(at + Offset(0f, 5f), at + Offset(-2f, 1f), MoPropNavy, 2f)
    }
}

internal val doingActs2: List<MoAct> = listOf(SpoonfulOfPeas, BiteOfApple, DailyTablet, StandOnTheScale)
