package de.nicolasmahn.momentum.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object MomentumChoiceTileDefaults {
    /** Compact enough for a row of three, tall enough that the ledge never crowds the label. */
    val MinHeight = 56.dp
    val Spacing = 8.dp
}

private val ContentPadding = 10.dp
private val IconSize = 22.dp
private val SwatchHeight = 5.dp
private val SwatchTopInset = 6.dp

/** The least a one-line label shrinks to: below it a label would no longer read at a glance. */
private val OneLineMinSize = 10.sp

/**
 * What a one-line label keeps clear on either side. Less than the tile's padding, so the longest
 * label, "Schwimmen", still reads at [OneLineMinSize] in a quarter of the narrowest phone.
 */
private val OneLineSidePadding = 3.dp

/**
 * One option in a short set of them: a selectable [MomentumCard] with a centred label, optionally
 * over an [icon]. Pass [accent] when the option owns a color, such as a substance kind; otherwise
 * the chosen tile wears the view identity.
 *
 * [swatch] draws that color as a bar across the tile's top, inside the padding every tile already
 * has, so a tile with a swatch measures exactly like one without. An option whose color appears
 * elsewhere — a calendar day, a chart bar — announces which color it will produce before it is
 * picked.
 *
 * A label wider than the tile wraps once. [oneLine] keeps it on one line instead, shrinking it to
 * fit, for a grid whose tiles must all be one size, as the icon picker's are.
 *
 * Put it in a [MomentumChoiceTileRow] with `Modifier.weight(1f).fillMaxHeight()` to share a row.
 */
@Composable
internal fun MomentumChoiceTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color? = null,
    swatch: Color? = null,
    role: Role = Role.RadioButton,
    enabled: Boolean = true,
    testTag: String? = null,
    /** Said instead of [label] when the label is a shortening, such as a weekday's two letters. */
    spoken: String? = null,
    oneLine: Boolean = false,
) {
    MomentumCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = MomentumChoiceTileDefaults.MinHeight),
        selected = selected,
        selectedColors = MomentumCardDefaults.selectedColors(
            accent ?: MaterialTheme.colorScheme.primary,
        ),
        enabled = enabled,
        role = role,
        testTag = testTag,
    ) {
        val swatchOutline = MaterialTheme.colorScheme.outlineVariant
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (swatch != null) drawSwatchBar(swatch, swatchOutline)
                }
                .padding(horizontal = if (oneLine) OneLineSidePadding else ContentPadding, vertical = ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                space = 4.dp,
                alignment = Alignment.CenterVertically,
            ),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                )
            }
            val spokenModifier = if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier
            if (oneLine) {
                OneLineLabel(label, spokenModifier)
            } else {
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(spokenModifier),
                    style = MaterialTheme.typography.labelLarge.copy(hyphens = Hyphens.Auto),
                    textAlign = TextAlign.Center,
                    // A label wider than the tile wraps once, hyphenated, so a long name such as
                    // "Benzodiazepines" is read rather than cut; the row's other tiles grow with it.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A tile's label on one line: too wide for the tile, it shrinks, down to [OneLineMinSize], and is
 * cut only below that. It always stands one full line tall, so a shrunk label leaves its tile, and
 * its row, the height of every other.
 */
@Composable
private fun OneLineLabel(label: String, modifier: Modifier) {
    val style = MaterialTheme.typography.labelLarge
    val color = LocalContentColor.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { style.lineHeight.toDp() }),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            modifier = modifier,
            style = style.copy(textAlign = TextAlign.Center),
            color = { color },
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = OneLineMinSize, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        )
    }
}

/**
 * A row of tiles that all match the tallest of them, so a longer label never leaves its neighbours
 * short. Each tile takes `Modifier.weight(1f).fillMaxHeight()`.
 */
@Composable
internal fun MomentumChoiceTileRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(MomentumChoiceTileDefaults.Spacing),
        content = content,
    )
}

/**
 * Items as rows of [columns] equal tiles. A short last row keeps its tiles the width of the
 * others, so a list of any length reads as one grid and no tile widens for being last.
 */
@Composable
internal fun <T> MomentumTileGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    tile: @Composable RowScope.(T, Modifier) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MomentumChoiceTileDefaults.Spacing)) {
        items.chunked(columns).forEach { row ->
            MomentumChoiceTileRow {
                row.forEach { item -> tile(item, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun DrawScope.drawSwatchBar(color: Color, outline: Color) {
    // Half the tile, centred: a full-width bar would run into the card's rounded corners.
    val barSize = Size(size.width / 2, SwatchHeight.toPx())
    val topLeft = Offset((size.width - barSize.width) / 2, SwatchTopInset.toPx())
    val radius = CornerRadius(barSize.height / 2)
    drawRoundRect(color = color, topLeft = topLeft, size = barSize, cornerRadius = radius)
    // The palest step of a ramp would otherwise dissolve into the tile face.
    drawRoundRect(
        color = outline,
        topLeft = topLeft,
        size = barSize,
        cornerRadius = radius,
        style = Stroke(MomentumCardDefaults.BorderWidth.toPx()),
    )
}
