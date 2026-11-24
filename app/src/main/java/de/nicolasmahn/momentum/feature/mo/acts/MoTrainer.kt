package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.feature.mo.limb

/** The colours of a pair of running shoes: the upper, the darker leather around it, and the laces. */
internal class MoTrainer(val upper: Color, val trim: Color, val laces: Color = MoPropCream)

/**
 * One running shoe from the side, the same shoe in every act that shows his.
 *
 * [foot] is the middle of the foot inside it, which is `MoRig.leftFoot` or `rightFoot` when he
 * wears it and [TRAINER_FLOOR] above the floor times [size] when it stands by itself. [toe] is the
 * way the toe points, -1 left and 1 right. On his feet it points outward, as his feet do: his leg
 * comes down inside of the middle of his foot, where the shoe has its ankle. A stride that swings
 * the foot sideways moves the foot twice as far as the leg and takes the leg out of the shoe.
 *
 * [toeUp] lifts the toe by that many degrees with the heel on the floor. [behind] darkens the shoe,
 * for the far one of a pair that stands half hidden by the near one.
 */
internal fun DrawScope.trainer(
    foot: Offset,
    toe: Float,
    trainer: MoTrainer,
    size: Float = 1f,
    toeUp: Float = 0f,
    behind: Boolean = false,
) {
    withTransform({
        translate(foot.x, foot.y)
        scale(-toe * size, size, Offset.Zero)
        rotate(toeUp, Offset(24f, TRAINER_FLOOR))
    }) {
        drawPath(Upper, trainer.upper)
        drawPath(Trim, trainer.trim)
        drawPath(Sole, MoPropCream)
        limb(Offset(-16.5f, -8f), Offset(-14f, -3.5f), trainer.laces, 2.8f)
        limb(Offset(-10.5f, -10.5f), Offset(-8f, -6f), trainer.laces, 2.8f)
        limb(Offset(-4.5f, -13f), Offset(-2f, -8.5f), trainer.laces, 2.8f)
        if (behind) drawPath(Whole, MoPropShadow)
    }
}

/** How far the middle of the foot in a [trainer] is above the floor the shoe stands on. */
internal const val TRAINER_FLOOR = 12f

private val Upper by lazy {
    Path().apply {
        moveTo(-31f, 6f)
        quadraticTo(-33f, -6f, -21f, -9f)
        quadraticTo(-12f, -11f, -8f, -15f)
        quadraticTo(-5f, -19f, 0f, -16f)
        quadraticTo(6f, -11f, 13f, -12.5f)
        quadraticTo(21f, -17f, 25f, -13f)
        quadraticTo(29f, -4f, 27f, 6f)
        close()
    }
}

/** Around the heel, along the sole and over the toe. */
private val Trim by lazy {
    Path().apply {
        moveTo(27f, 6f)
        quadraticTo(29f, -4f, 25f, -13f)
        quadraticTo(21f, -17f, 15f, -13.5f)
        quadraticTo(13f, -2f, 5f, 0.5f)
        lineTo(-14f, 1f)
        quadraticTo(-20f, 0f, -21f, -9f)
        quadraticTo(-33f, -6f, -31f, 6f)
        close()
    }
}

/** Thick under the heel and turned up at the toe, which is what makes it a shoe to run in. */
private val Sole by lazy {
    Path().apply {
        moveTo(-33f, 5f)
        quadraticTo(-38f, 7f, -33f, 10.5f)
        quadraticTo(-30f, 12f, -24f, 12f)
        lineTo(22f, 12f)
        quadraticTo(30f, 12f, 30f, 5f)
        quadraticTo(30f, 1f, 27f, 0f)
        quadraticTo(0f, 7f, -33f, 5f)
        close()
    }
}

private val Whole by lazy { Path.combine(PathOperation.Union, Upper, Sole) }
