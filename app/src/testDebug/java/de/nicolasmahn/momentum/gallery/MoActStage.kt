package de.nicolasmahn.momentum.gallery

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import de.nicolasmahn.momentum.core.ui.theme.MomentumDarkBackground
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.feature.mo.MoLook
import de.nicolasmahn.momentum.feature.mo.boxPoint
import de.nicolasmahn.momentum.feature.mo.drawMo
import kotlin.math.abs
import kotlin.math.max

/**
 * How far an act's first moment may reach past his box, in box units. Less than this is the soft
 * edge of a floor shadow, which nobody sees cut off.
 */
internal const val STILL_PICTURE_SLACK = 4f

/** Mo's box at half size with room around it, so what an act draws beyond the box can be measured. */
internal class MoActStage {
    private val box = Size(CELL_WIDTH * SCALE, CELL_HEIGHT * SCALE)
    private val width = (box.width + 2 * MARGIN).toInt()
    private val height = (box.height + 2 * MARGIN).toInt()
    private val bitmap = ImageBitmap(width, height)
    private val canvas = Canvas(bitmap)
    private val pixels = IntArray(width * height)
    private val changed = BooleanArray(width * height)
    private val ground = stageGround()

    val boxArea = box.width * box.height

    /**
     * The picture at one moment, with him [lift] box units off the floor. The array is reused by
     * the next shot: copy it to keep it.
     */
    fun shoot(look: MoLook, seconds: Float, lift: Float = 0f): IntArray {
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
            drawRect(MomentumDarkBackground)
            translate(MARGIN.toFloat(), MARGIN.toFloat()) {
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, box) { drawMo(look, seconds, MomentumDarkBackground, lift * SCALE) }
            }
        }
        bitmap.asAndroidBitmap().getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    /**
     * How much of the picture changed outright between two frames. His breathing slides every
     * edge a fraction of a pixel a frame, which changes a line of pixels along it without
     * anything having jumped, so a pixel only counts with changed pixels all round it.
     */
    fun changedBetween(a: IntArray, b: IntArray): Int {
        for (index in a.indices) changed[index] = differ(a[index], b[index])
        var solid = 0
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val at = y * width + x
            if (changed[at] && changed[at - 1] && changed[at + 1] && changed[at - width] && changed[at + width]) solid++
        }
        return solid
    }

    /** How far the picture reaches past the box to the left, top, right and bottom, in box units. */
    fun beyondTheBox(frame: IntArray): List<Float> {
        var (left, top, right, bottom) = listOf(0, 0, 0, 0)
        for (y in 0 until height) for (x in 0 until width) {
            if (!differ(frame[y * width + x], ground)) continue
            left = max(left, MARGIN - x)
            top = max(top, MARGIN - y)
            right = max(right, x - (width - MARGIN - 1))
            bottom = max(bottom, y - (height - MARGIN - 1))
        }
        return listOf(left, top, right, bottom).map { it / SCALE }
    }

    /** The last shot with the room around it, and a line where his box ends. */
    fun pictureWithItsBox(): Bitmap {
        val picture = bitmap.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        val edge = Paint().apply {
            style = Paint.Style.STROKE
            color = BOX_EDGE
        }
        android.graphics.Canvas(picture).drawRect(MARGIN - 0.5f, MARGIN - 0.5f, width - MARGIN + 0.5f, height - MARGIN + 0.5f, edge)
        return picture
    }

    /**
     * How many pixels differ between two pictures inside [part], which is given in the design
     * space of a Mo in [state], and how many pixels the part has.
     */
    fun changedWithin(a: IntArray, b: IntArray, state: MoState, part: Rect): Pair<Int, Int> {
        val from = boxPoint(state, part.topLeft) * SCALE
        val to = boxPoint(state, part.bottomRight) * SCALE
        var (changed, all) = 0 to 0
        for (y in (MARGIN + from.y).toInt()..(MARGIN + to.y).toInt()) for (x in (MARGIN + from.x).toInt()..(MARGIN + to.x).toInt()) {
            all++
            if (differ(a[y * width + x], b[y * width + x])) changed++
        }
        return changed to all
    }

    fun differ(a: Int, b: Int): Boolean =
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > TOLERANCE ||
            abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > TOLERANCE ||
            abs((a and 0xFF) - (b and 0xFF)) > TOLERANCE

    private fun stageGround(): Int {
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) { drawRect(MomentumDarkBackground) }
        return bitmap.asAndroidBitmap().getPixel(0, 0)
    }

    private companion object {
        const val SCALE = 0.5f
        const val MARGIN = 70

        // Less than this between two colours is a difference nobody sees.
        const val TOLERANCE = 28
        const val BOX_EDGE = 0xFFFF4081.toInt()
    }
}
