package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropSand
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.domain.mo.MoSaveAct
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.FEET
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodies
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoEye
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.grippingMitten
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.wave

private class BrushTeeth(id: String, private val showSmile: Boolean) : MoAct(id, MoOccasion.Doing, saveAct = MoSaveAct.Brushing) {
    private fun lowered(t: Float) = if (showSmile) ease((pulse(t, 4.8f) - 0.62f) / 0.36f) else 0f
    private fun side(v: MoVariant) = if (v.chance(50)) -1f else 1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val frame = MoBodies.of(body)
        val rest = lowered(t)
        val stroke = 7f * wave(t, if (showSmile) 0.54f else 0.42f) * (1f - rest)
        val height = if (showSmile) 23f * rest else 2.5f * wave(t + 0.105f, 0.42f)
        val grip = MoArm(
            Offset(50f - frame.shoulderHalf + stroke + 31f * rest, frame.mouth.y - frame.shoulderY + 13f + height),
            bend = 0.55f, inFront = true,
        )
        val s = side(v)
        return moIdle(t, 0.8f).copy(
            mouth = MoMouth.Beam,
            leftArm = if (s < 0f) grip else MoArm.Down,
            rightArm = if (s > 0f) grip else MoArm.Down,
            tilt = s * (1.4f - 3f * rest),
        ).eyes(MoEye(gaze = Offset(s * 0.24f * (1f - rest), 0.45f * (1f - rest))))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        toothbrush(if (s < 0f) rig.leftHand else rig.rightHand, s, v, salt = 2, angle = -32f * lowered(t))
        grippingMitten(rig, s)
    }
}

private object FollowTheLine : MoAct("doing_reading_follow_the_line", MoOccasion.Doing, saveAct = MoSaveAct.Reading) {
    override val forASession get() = true

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val sweep = cycle(t, 3.6f)
        val gaze = ease(sweep / 0.85f) - ease((sweep - 0.85f) / 0.15f)
        val arm = MoArm(Offset(-40f, 39f), bend = 0.64f, inFront = true)
        return moIdle(t, 0.45f).arms(arm).eyes(MoEye(gaze = Offset(-0.6f + 1.2f * gaze, 0.85f))).copy(
            mouth = MoMouth.Smile(width = 12f, depth = 9f),
            tilt = 1.1f * wave(t, 7.2f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val spine = Offset(CENTRE, rig.leftHand.y + 7f)
        val cover = v.of(BookCovers)
        withTransform({ translate(spine.x, spine.y) }) {
            for (s in listOf(-1f, 1f)) {
                withTransform({ scale(s, 1f, Offset.Zero) }) {
                    shape(cover, 0f to -22f, 55f to -32f, 57f to 13f, 0f to 23f)
                    shape(MoPropSand, 1f to -24f, 52f to -33f, 53f to -28f, 1f to -18f)
                    shape(MoPropCream, 1f to -24f, 48f to -35f, 51f to -33f, 1f to -21f)
                }
            }
            shape(MoPropGold, 28f to -31f, 34f to -32f, 34f to -14f, 31f to -17f, 28f to -14f)
        }
        grippingMitten(rig, -1f)
        grippingMitten(rig, 1f)
    }
}

/** He shows us a picture book on a stand, catching the corner and folding the whole leaf across. */
private object TurnThePage : MoAct("doing_reading_turn_the_page", MoOccasion.Doing, saveAct = MoSaveAct.Reading) {
    override val ownsTheFloor get() = true
    private val spine = Offset(CENTRE + 96f, FEET - 55f)
    private const val STAND_ASIDE = 28f

    private fun phase(t: Float) = cycle(t, 4.6f, offset = 0.22f)
    private fun turn(t: Float) = ease((phase(t) - 0.12f) / 0.46f)
    private fun corner(p: Float) = Offset(43f * (1f - 2f * p), -20f - 22f * 4f * p * (1f - p))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val frame = MoBodies.of(body)
        val p = turn(t)
        val hand = spine + corner(p) + Offset(0f, 5f)
        val back = ease((phase(t) - 0.64f) / 0.32f)
        val next = spine + corner(0f) + Offset(0f, 5f)
        val reach = hand + (next - hand) * back + Offset(0f, 14f * back * (1f - back) * 4f)
        return moIdle(t, 0.7f).copy(
            offset = Offset(-STAND_ASIDE, 0f),
            squash = 0f,
            rightArm = MoArm(reach - Offset(CENTRE - STAND_ASIDE + frame.shoulderHalf, frame.shoulderY), bend = 0.65f, inFront = true),
            leftArm = MoArm.Hip,
            mouth = MoMouth.Smile(width = 15f, depth = 10f),
        ).eyes(MoEye(gaze = Offset(0.95f - 0.4f * p * (1f - back), 0.65f)))
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        block(spine + Offset(0f, 32f), 12f, 43f, MoPropWoodDark)
        block(spine + Offset(0f, 52f), 58f, 6f, MoPropWoodDark)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        // The book is drawn here so that his mitten can lie on the page, but it stands on its
        // stand: when a hop takes him off the floor, his hand comes away and the book stays.
        withTransform({ translate(spine.x + STAND_ASIDE, spine.y + rig.lift) }) {
            for (side in listOf(-1f, 1f)) withTransform({ scale(side, 1f, Offset.Zero) }) {
                shape(v.of(BookCovers), 0f to -16f, 46f to -26f, 49f to 17f, 0f to 26f)
                shape(MoPropCream, 0f to -20f, 43f to -27f, 46f to 12f, 0f to 21f)
            }
            drawCircle(MoPropGold, 9f, Offset(-23f, -8f))
            val p = turn(t)
            val edge = corner(p)
            val arch = 4f * p * (1f - p)
            val side = 1f - 2f * p
            val page = Path().apply {
                moveTo(0f, -20f)
                quadraticTo(edge.x / 2f + 5f * arch, (edge.y - 27f) / 2f - 8f * arch, edge.x, edge.y - 7f)
                lineTo(edge.x + 3f * side, edge.y + 32f)
                quadraticTo(edge.x / 2f, (edge.y + 53f) / 2f - 5f * arch, 0f, 21f)
                close()
            }
            drawPath(page, lerp(MoPropCream, MoPropSand, arch))
            if (side < 0f) {
                oval(Offset(23f * side, -8f - 18f * arch), -9f * side, 9f, MoPropGold)
            }
        }
        grippingMitten(rig, 1f)
    }
}

private val BookCovers = listOf(MoPropBlueDark, MoPropPurpleDark, MoPropTealDark)

internal val doingActs1: List<MoAct> = listOf(
    BrushTeeth("doing_brushing_little_circles", showSmile = false),
    BrushTeeth("doing_brushing_show_the_smile", showSmile = true),
    FollowTheLine,
    TurnThePage,
)
