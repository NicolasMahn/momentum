package de.nicolasmahn.momentum.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.ui.MomentumCard
import de.nicolasmahn.momentum.core.ui.ViewColors
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.MomentumCardColors
import de.nicolasmahn.momentum.feature.mo.PassToken
import de.nicolasmahn.momentum.feature.mo.QuietMark
import de.nicolasmahn.momentum.feature.mo.RestToken
import de.nicolasmahn.momentum.feature.tracker.TrackerHomeStatus

/**
 * How a row draws its [HomeDayPicture]. Home uses [Bars]; the gallery shows the others beside it
 * so a look can be compared on a phone before it is chosen.
 */
internal enum class HomeDayPictureStyle {
    /** Segments as rounded bars. */
    Bars,

    /** Segments as one dot per part, a daily plan's checkpoints as groups of dots. */
    Dots,
}

/**
 * One view on Home: its icon, its name, a picture of its day, and whether the day is open or kept.
 * Every row has the same height and the same slots, so the list reads as one column whatever each
 * view has to show.
 */
@Composable
internal fun HomeRow(
    title: String,
    icon: ImageVector,
    colors: ViewColors,
    picture: HomeDayPicture,
    status: TrackerHomeStatus,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    style: HomeDayPictureStyle = HomeDayPictureStyle.Bars,
) {
    // The row is the view, as its Home tile was: its whole face wears the view's tint, and one
    // darker shade is both its outline and its ledge. Kept, it turns over into the view's full
    // color, everything on it in the ink drawn on that color: the day is done, and it shows. A
    // rest day, a day a token covers and a day Mo was asked not to ask turn over too: they are
    // settled for today.
    val face = if (status is TrackerHomeStatus.Open || status == TrackerHomeStatus.None) colors else colors.keptFace()
    MomentumCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(HomeRowDefaults.Height),
        colors = MomentumCardColors(container = face.container, content = face.onContainer, border = face.ledge, ledge = face.ledge),
        testTag = testTag,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = face.accent, modifier = Modifier.size(36.dp))
            // Larger than the labels of the bar under the rows: a view is what Home is for. Every
            // row splits its width the same way, so the pictures line up. A larger font grows the
            // name, not the picture, so the name's share grows with it; a name still too long
            // shrinks before it is cut.
            val titleStyle = MaterialTheme.typography.titleLarge
            Text(
                text = title,
                modifier = Modifier.weight(TitleShare * LocalDensity.current.fontScale),
                color = face.label,
                autoSize = TextAutoSize.StepBased(minFontSize = TitleMinSize, maxFontSize = titleStyle.fontSize, stepSize = 0.5.sp),
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.weight(PictureShare).height(28.dp), contentAlignment = Alignment.CenterStart) {
                HomeDayPictureView(picture, face, style)
            }
            HomeStatusMark(status, ViewIdentity(icon, face))
        }
    }
}

/** The same view, turned over: its accent as the face, and its ink for all that sits on it. */
private fun ViewColors.keptFace(): ViewColors = copy(
    accent = onAccent,
    onAccent = accent,
    container = accent,
    onContainer = onAccent,
    label = onAccent,
)

internal object HomeRowDefaults {
    val Height = 64.dp
    val Gap = 12.dp
}

private const val TitleShare = 3f
private const val PictureShare = 2f
private val TitleMinSize = 12.sp

/**
 * What the row's end says: a check once the day is kept, the token that settled it on a rest day
 * or a day a token covers, and the empty ring it fills until then. [identity] is the row's icon in
 * the colors of its face.
 */
@Composable
private fun HomeStatusMark(status: TrackerHomeStatus, identity: ViewIdentity) {
    val colors = identity.colors
    val description = when (status) {
        TrackerHomeStatus.Done -> stringResource(R.string.app_home_task_done)
        TrackerHomeStatus.Kept -> stringResource(R.string.app_home_task_kept)
        TrackerHomeStatus.Rest -> stringResource(R.string.app_home_task_rest)
        TrackerHomeStatus.Covered -> stringResource(R.string.app_home_task_covered)
        TrackerHomeStatus.Quiet -> stringResource(R.string.mo_quiet_today)
        is TrackerHomeStatus.Open -> when {
            status.parts > 1 -> stringResource(R.string.app_home_task_parts_done, status.done, status.parts)
            status.optional -> stringResource(R.string.app_home_task_optional)
            else -> stringResource(R.string.app_home_task_open)
        }
        TrackerHomeStatus.None -> null
    }
    val mark = Modifier.size(MarkSize).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
    when (status) {
        TrackerHomeStatus.Done, TrackerHomeStatus.Kept -> Box(mark.clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(22.dp))
        }
        // No check: nothing was done, and the token is the one such a day wears in every calendar.
        TrackerHomeStatus.Rest -> RestToken(mark)
        TrackerHomeStatus.Covered -> PassToken(identity, mark)
        // The same "Zz" the view's header offers, where the open ring stood.
        TrackerHomeStatus.Quiet -> QuietMark(colors.label, mark)
        is TrackerHomeStatus.Open, TrackerHomeStatus.None -> {
            val ring = colors.ledge
            Canvas(mark) { drawCircle(ring, radius = size.minDimension / 2 - 1.5.dp.toPx(), style = Stroke(3.dp.toPx())) }
        }
    }
}

/** The day's picture alone, as a row draws it; the gallery hosts it with each [style]. */
@Composable
internal fun HomeDayPictureView(
    picture: HomeDayPicture,
    colors: ViewColors,
    style: HomeDayPictureStyle,
) {
    // On the view's tint, a faint ledge shade is what is not filled yet: faint enough that it
    // never reads as a filled part in a darker color.
    val empty = colors.ledge.copy(alpha = EmptyAlpha)
    when (picture) {
        HomeDayPicture.Empty -> Unit
        is HomeDayPicture.Segments -> when (style) {
            HomeDayPictureStyle.Bars -> SegmentBars(picture, colors.accent, empty)
            HomeDayPictureStyle.Dots -> SegmentDots(picture, colors.accent, empty)
        }
        is HomeDayPicture.Week -> WeekStrip(picture.days, colors.accent, empty)
        is HomeDayPicture.Trend -> TrendLine(picture.values, colors.accent)
    }
}

/**
 * The week as the calendar shows it, one cell a day from Monday, across the whole slot as the
 * bars are: a recorded day full, today outlined until it is, and the days to come only faintly there.
 */
@Composable
private fun WeekStrip(days: List<WeekDay>, fill: Color, empty: Color) {
    Canvas(Modifier.fillMaxWidth().height(26.dp)) {
        val gap = 3.dp.toPx()
        // Square cells, as the calendar's are, as large as the slot allows.
        val width = minOf((size.width - gap * 6) / 7, size.height)
        val height = width
        val radius = CornerRadius(4.dp.toPx())
        val stroke = 1.5.dp.toPx()
        days.forEachIndexed { index, day ->
            val topLeft = Offset(index * (width + gap), (size.height - height) / 2)
            val box = Size(width, height)
            when (day) {
                WeekDay.Recorded -> drawRoundRect(fill, topLeft, box, radius)
                WeekDay.Empty -> drawRoundRect(empty, topLeft, box, radius)
                WeekDay.Today -> drawRoundRect(fill, topLeft + Offset(stroke / 2, stroke / 2), Size(width - stroke, height - stroke), radius, style = Stroke(stroke))
                WeekDay.Ahead -> drawRoundRect(empty, topLeft, box, radius, alpha = AheadAlpha)
            }
        }
    }
}

private val MarkSize = 32.dp
private const val EmptyAlpha = 0.35f
private const val AheadAlpha = 0.4f

@Composable
private fun SegmentBars(picture: HomeDayPicture.Segments, fill: Color, empty: Color) {
    val gap = if (picture.grouped) 8.dp else 4.dp
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val gapPx = gap.toPx()
        val width = (size.width - gapPx * (picture.fills.size - 1)) / picture.fills.size
        val radius = CornerRadius(size.height / 2)
        picture.fills.forEachIndexed { index, share ->
            val left = index * (width + gapPx)
            drawRoundRect(empty, Offset(left, 0f), Size(width, size.height), radius)
            if (share > 0f) drawRoundRect(fill, Offset(left, 0f), Size(width * share, size.height), radius)
        }
    }
}

/** One dot per part; past [MaxDots] a dot could no longer be told apart, so it falls back to bars. */
@Composable
private fun SegmentDots(picture: HomeDayPicture.Segments, fill: Color, empty: Color) {
    if (picture.fills.size > MaxDots) return SegmentBars(picture, fill, empty)
    Row(horizontalArrangement = Arrangement.spacedBy(if (picture.grouped) 10.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
        picture.fills.forEach { share ->
            Canvas(Modifier.size(DotSize)) {
                drawCircle(empty)
                // A partly done checkpoint fills its dot from the bottom, as a glass fills.
                if (share > 0f) clipRect(top = size.height * (1f - share)) { drawCircle(fill) }
            }
        }
    }
}

private const val MaxDots = 10
private val DotSize: Dp = 10.dp

/** The latest values as one line, scaled to their own range, in the view's color and no other. */
@Composable
private fun TrendLine(values: List<Double>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val low = values.min()
        val span = (values.max() - low).takeIf { it > 0.0 } ?: 1.0
        val stroke = 2.dp.toPx()
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1)
            val y = stroke + (size.height - 2 * stroke) * (1f - ((value - low) / span).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
