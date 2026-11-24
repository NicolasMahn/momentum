package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.fading
import de.nicolasmahn.momentum.feature.mo.limb

/**
 * The grip is the pivot. With the right mitten on it, the head points left towards Mo. [salt] is
 * the variant's choice of colour: an act gives the brush a salt none of its other choices use.
 */
internal fun DrawScope.toothbrush(grip: Offset, side: Float, v: MoVariant, salt: Int, angle: Float = 0f, size: Float = 1f, alpha: Float = 1f) {
    val (face, ledge) = v.of(BrushColours, salt)
    // Tufts in two colours that are neither the handle's nor the white of his teeth, so all four show.
    val tufts = listOf(MoPropBlue, MoPropMint, MoPropTeal).filter { it != face }
    withTransform({
        translate(grip.x, grip.y)
        scale(side * size, size, Offset.Zero)
        rotate(angle, Offset.Zero)
    }) {
        // Neck on grip, head on neck: at its own alpha each joint would show as it fades.
        fading(alpha, Rect(-64f, -22f, 24f, 10f)) {
            limb(Offset(-8f, 0f), Offset(-47f, -3f), ledge, 7f)
            limb(Offset(-8f, -2f), Offset(-47f, -5f), face, 5f)
            chunk(Offset(1f, 0f), 40f, 11f, face, ledge, corner = 5.5f, drop = 2.5f)
            for (tuft in 0..3) block(Offset(-57f + 6f * tuft, -11f), 4f, 13f, tufts[tuft % 2], corner = 1.7f)
            chunk(Offset(-48f, -4f), 25f, 7f, face, ledge, corner = 3.5f, drop = 2f)
        }
    }
}

private val BrushColours = listOf(MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark, MoPropPink to MoPropPinkDark)
