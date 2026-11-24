package de.nicolasmahn.momentum.feature.logactivity

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val BoulderingIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Bouldering",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(9.4f, 3.2f)
            curveTo(10.7f, 3.2f, 11.7f, 4.2f, 11.7f, 5.5f)
            curveTo(11.7f, 6.8f, 10.7f, 7.8f, 9.4f, 7.8f)
            curveTo(8.1f, 7.8f, 7.1f, 6.8f, 7.1f, 5.5f)
            curveTo(7.1f, 4.2f, 8.1f, 3.2f, 9.4f, 3.2f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The wall and holds make the reaching pose read as climbing rather than walking.
            moveTo(20.3f, 1.8f)
            lineTo(20.3f, 22.2f)

            moveTo(9.3f, 8.5f)
            lineTo(10.4f, 13.3f)
            moveTo(9.7f, 9.5f)
            lineTo(5.9f, 11.7f)
            lineTo(4.2f, 9.2f)
            moveTo(9.9f, 9.4f)
            lineTo(13.6f, 7.7f)
            lineTo(16.2f, 5.6f)
            moveTo(10.4f, 13.3f)
            lineTo(7.1f, 17.1f)
            lineTo(6.2f, 21.2f)
            moveTo(10.4f, 13.3f)
            lineTo(13.8f, 16.3f)
            lineTo(14.7f, 20.5f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.2f, 4.5f)
            curveTo(16.9f, 4.5f, 17.4f, 5f, 17.4f, 5.7f)
            curveTo(17.4f, 6.4f, 16.9f, 6.9f, 16.2f, 6.9f)
            curveTo(15.5f, 6.9f, 15f, 6.4f, 15f, 5.7f)
            curveTo(15f, 5f, 15.5f, 4.5f, 16.2f, 4.5f)
            close()
            moveTo(17.7f, 10.1f)
            curveTo(18.4f, 10.1f, 18.9f, 10.6f, 18.9f, 11.3f)
            curveTo(18.9f, 12f, 18.4f, 12.5f, 17.7f, 12.5f)
            curveTo(17f, 12.5f, 16.5f, 12f, 16.5f, 11.3f)
            curveTo(16.5f, 10.6f, 17f, 10.1f, 17.7f, 10.1f)
            close()
            moveTo(15.5f, 16.1f)
            curveTo(16.2f, 16.1f, 16.7f, 16.6f, 16.7f, 17.3f)
            curveTo(16.7f, 18f, 16.2f, 18.5f, 15.5f, 18.5f)
            curveTo(14.8f, 18.5f, 14.3f, 18f, 14.3f, 17.3f)
            curveTo(14.3f, 16.6f, 14.8f, 16.1f, 15.5f, 16.1f)
            close()
        }
    }.build()
}
