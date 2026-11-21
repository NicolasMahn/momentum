package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropNight
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropSand
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodies
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoLeg
import com.example.workouttracker.feature.mo.MoLegs
import com.example.workouttracker.feature.mo.MoPalettes
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.pulse
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * Mo's bed, seen from its foot end, so he stays upright and readable while plainly being in bed:
 * the headboard and pillow stand behind him and the blanket lies across the front. Every act that
 * puts him to bed uses this one bed, so bedtime always looks like the same place.
 *
 * An act draws [bedBehind] in its world, starts its pose from [moInBed], and draws [bedBlanket] in
 * its foreground, before anything that should lie on top of the blanket.
 */

/** The top edge of the blanket when it lies flat. His mouth stays above it on every burning body. */
internal const val BED_LINE = 352f
internal const val BED_BLANKET_WIDTH = 300f

private fun blanketTop(t: Float, pulledUp: Float) = BED_LINE - pulledUp - 3f * pulse(t, BED_BREATH_SECONDS)

/** How far his feet are drawn up in bed: enough to stay under the blanket however he lies. */
private const val TUCKED = 22f

/** How long one sleeping breath takes, shared so the blanket and his chest rise together. */
internal const val BED_BREATH_SECONDS = 4.2f

/**
 * Mo tucked in: slow, low on energy, mittens resting on the blanket's edge. The low body is lifted so
 * his face sits where the tall one's does, which the blanket hides.
 */
internal fun moInBed(
    t: Float,
    body: MoBodyKind,
    tilt: Float = 0f,
    shift: Offset = Offset.Zero,
    pulledUp: Float = 0f,
): MoPose {
    val shape = MoBodies.of(body)
    val lift = MoBodies.Tall.mouth.y - shape.mouth.y
    val breath = pulse(t, BED_BREATH_SECONDS)
    // His chest rolls but both palms stay on the hem. Lower the high hand and raise the low one.
    val reachX = -30f
    val roll = (shape.shoulderHalf + reachX) * sin(tilt * PI.toFloat() / 180f)
    val reachY = blanketTop(t, pulledUp) - 12.5f - (shape.shoulderY + lift + shift.y)
    fun mitten(side: Float) = MoArm(Offset(reachX, reachY - side * roll), bend = 0.3f)
    return moIdle(t, energy = 0.4f).copy(
        // Lifting the low body would show the cold band at his base above the blanket as a smudge.
        palette = MoPalettes.Warm.takeIf { body == MoBodyKind.Low },
        leftArm = mitten(-1f),
        rightArm = mitten(1f),
        armsInFront = true,
        // Drawn up under the blanket. At full length a lean or a roll pushes a foot out below its hem.
        legs = MoLegs(MoLeg(lift = TUCKED), MoLeg(lift = TUCKED)),
        squash = 0.02f * breath - 0.01f,
        tilt = tilt,
        offset = Offset(0f, lift) + shift,
    )
}

private fun Color.atNight(night: Float) = if (night == 0f) this else lerp(this, MoPropNight, night)

/** The frame, sheet-covered mattress and pillow, for the act's world. */
internal fun DrawScope.bedBehind(night: Float = 0f) {
    chunk(Offset(CENTRE, 307f), 304f, 228f, MoPropWood.atNight(night), MoPropWoodDark.atNight(night), corner = 30f, drop = 8f)
    // Two bedposts, so the headboard reads as furniture and not as a wall.
    chunk(Offset(CENTRE - 141f, 186f), 26f, 26f, MoPropWood.atNight(night), MoPropWoodDark.atNight(night), corner = 13f)
    chunk(Offset(CENTRE + 141f, 186f), 26f, 26f, MoPropWood.atNight(night), MoPropWoodDark.atNight(night), corner = 13f)
    chunk(Offset(CENTRE, 371f), 296f, 96f, MoPropCream.atNight(night), MoPropSand.atNight(night), corner = 18f, drop = 7f)
    chunk(Offset(CENTRE, 312f), 268f, 112f, MoPropCream.atNight(night), MoPropSand.atNight(night), corner = 44f, drop = 8f)
}

/**
 * The blanket, for the act's foreground. It rises and falls with his breathing. [pulledUp] raises
 * its edge in design units. [bunch] pulls the trailing corner inward as he rolls, while the
 * opposite edge stays on the bed.
 */
internal fun DrawScope.bedBlanket(t: Float, v: MoVariant, pulledUp: Float = 0f, bunch: Float = 0f, night: Float = 0f) {
    val (colour, ledge) = blanketColours(v)
    val top = blanketTop(t, pulledUp)
    val bottom = 428f
    val width = BED_BLANKET_WIDTH - abs(bunch)
    val x = CENTRE + bunch / 2f
    block(Offset(x, (top + bottom) / 2f), width, bottom - top, colour.atNight(night), corner = 18f)
    // The folded-over sheet along its top edge.
    chunk(Offset(x, top + 12f), width, 24f, MoPropCream.atNight(night), ledge.atNight(night), corner = 12f, drop = 6f)
}

private fun blanketColours(v: MoVariant) = v.of(
    listOf(
        MoPropBlue to MoPropBlueDark,
        MoPropPurple to MoPropPurpleDark,
        MoPropTeal to MoPropTealDark,
        MoPropPink to MoPropPinkDark,
    ),
    salt = BLANKET_SALT,
)

/** One corner turned back in his hand. The diagonal exposes the sheet on the empty side. */
internal fun DrawScope.openBedBlanket(v: MoVariant, emptySide: Float, grip: Offset) {
    val (colour, ledge) = blanketColours(v)
    withTransform({
        translate(CENTRE, 0f)
        scale(emptySide, 1f, Offset.Zero)
    }) {
        val peak = (grip.x - CENTRE) * emptySide
        val y = grip.y
        fun Path.turnedEdge() {
            quadraticTo(-60f, 338f, -12f, 341f)
            quadraticTo(76f, 347f, 146f, 392f)
        }
        val cloth = Path().apply {
            moveTo(-150f, 345f)
            quadraticTo(-135f, 338f, peak, y)
            turnedEdge()
            quadraticTo(150f, 393f, 150f, 399f)
            lineTo(150f, 411f)
            quadraticTo(150f, 428f, 133f, 428f)
            lineTo(-133f, 428f)
            quadraticTo(-150f, 428f, -150f, 411f)
            close()
        }
        drawPath(cloth, colour)
        val fold = Path().apply {
            moveTo(peak, y)
            turnedEdge()
            quadraticTo(34f, 383f, -28f, 363f)
            close()
        }
        drawPath(fold, ledge)
        val cuff = Path().apply {
            moveTo(-150f, 345f)
            quadraticTo(-135f, 338f, peak, y)
            lineTo(-28f, 363f)
            quadraticTo(-72f, 335f, -150f, 366f)
            close()
        }
        drawPath(cuff, MoPropCream)
    }
}

// A salt no act is likely to use for its own choices, so the blanket's colour stays independent.
private const val BLANKET_SALT = 9001
