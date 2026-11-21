package com.example.workouttracker.feature.statistics

import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumButton
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.Icons
import java.time.LocalDate
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.domain.activity.activityRules
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.format.DateStyle
import com.example.workouttracker.core.format.dateText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.MomentumChoiceTile
import com.example.workouttracker.core.ui.MomentumChoiceTileRow
import com.example.workouttracker.core.ui.ViewColors
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.core.ui.platformAnimationsEnabled
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerStatisticsContribution
import com.example.workouttracker.core.ui.LocalTrackerIdentity
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.mo.TrackerObservation
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.mo.PassToken
import com.example.workouttracker.feature.mo.drawFreezeCube
import com.example.workouttracker.feature.mo.drawPassToken
import com.example.workouttracker.core.ui.neutralViewColors
import com.example.workouttracker.feature.mo.drawRestToken
import com.example.workouttracker.feature.mo.drawSpentCoin
import com.example.workouttracker.feature.mo.drawStreakCoin
import com.example.workouttracker.feature.measurement.words
import com.example.workouttracker.feature.measurement.chartSpacing
import com.example.workouttracker.feature.checklist.viewIdentity
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.feature.measurement.MeasurementDateAxis
import com.example.workouttracker.feature.measurement.MeasurementTrendChart

/**
 * One dashboard of small multiples: a range switch, then one block per feature in that feature's
 * own color. Every block reads the same week-aligned window, so the screen answers "how has it
 * gone" for one stretch of time at a glance.
 */
@Composable
internal fun StatisticsScreen(
    state: AppState,
    todayKey: String,
    featureVisibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
    // Home already walks the streak for Mo; walking it again here could only disagree with him.
    streak: MoStreak? = null,
    // A month is what every feature's own calendar already shows; Statistics is for looking further,
    // once there is anything further to look at.
    initialRange: StatisticsRange? = null,
    statisticsContributions: List<TrackerStatisticsContribution>? = null,
    // Home's rules for each view, so a row counts what Mo and the export count.
    observations: List<TrackerObservation>? = null,
    daysOff: HolidaySchedule = HolidaySchedule(),
    /** Writes the whole history as Markdown and hands it to the share sheet; false when that failed. */
    onExport: (() -> Boolean)? = null,
) {
    var exportFailed by remember { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(initialRange ?: defaultStatisticsRange(state, LocalDate.parse(todayKey))) }
    val summary = remember(state, todayKey, range, featureVisibility, streak, statisticsContributions, observations, daysOff) {
        statisticsSummary(state, todayKey, range, featureVisibility, streak?.history, statisticsContributions, observations, daysOff)
    }
    val motionEnabled = platformAnimationsEnabled()
    // Read only while drawing, so the bars and lines grow without ever being measured again.
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(range, motionEnabled) {
        if (!motionEnabled) {
            reveal.snapTo(1f)
            return@LaunchedEffect
        }
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(RevealMillis, easing = FastOutSlowInEasing))
    }

    ViewTheme(statisticsPresentation().colors) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("statistics_screen"),
            verticalArrangement = Arrangement.spacedBy(BlockSpacing),
        ) {
            RangeChoice(range = range, onRangeChange = { range = it })

            summary.streak?.let { streakCalendar ->
                StreakBlock(calendar = streakCalendar, range = range, reveal = { reveal.value })
            }
            summary.activity?.let { activity ->
                ActivityBlock(activity = activity, range = range, reveal = { reveal.value }, configuration = state.activityRules)
            }
            if (summary.consistency.isNotEmpty()) {
                ConsistencyBlock(rows = summary.consistency, range = summary.range, startDateKey = summary.startDateKey, reveal = { reveal.value })
            }
            summary.measurements.forEach { measurement ->
                MeasurementBlock(measurement = measurement, motionEnabled = motionEnabled)
            }
            if (summary.consistency.isEmpty() && summary.measurements.isEmpty() && summary.activity == null && summary.streak == null) {
                Text(
                    text = stringResource(R.string.statistics_all_hidden),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onExport != null) {
                // The whole history, plans and days, for reading it elsewhere, such as with an AI. It closes the
                // screen, so its reserved error line pads the end instead of opening a gap above the range.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MomentumButton(
                        text = stringResource(R.string.statistics_export),
                        onClick = { exportFailed = !onExport() },
                        modifier = Modifier.fillMaxWidth(),
                        variant = MomentumButtonVariant.Secondary,
                        icon = Icons.Rounded.Share,
                        testTag = "statistics_export",
                    )
                    ReservedMessage(
                        text = if (exportFailed) stringResource(R.string.statistics_export_failed) else null,
                        testTag = "statistics_export_failed",
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RangeChoice(
    range: StatisticsRange,
    onRangeChange: (StatisticsRange) -> Unit,
) {
    MomentumChoiceTileRow {
        StatisticsRange.entries.forEach { option ->
            MomentumChoiceTile(
                label = stringResource(option.label),
                selected = option == range,
                onClick = { onRangeChange(option) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                testTag = "statistics_range_${option.name.lowercase()}",
            )
        }
    }
}

@Composable
private fun ConsistencyBlock(
    rows: List<FeatureConsistency>,
    range: StatisticsRange,
    startDateKey: String,
    reveal: () -> Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
        rows.forEach { consistency ->
            ConsistencyRow(consistency = consistency, range = range, reveal = reveal)
        }
        // One time axis for all rows, laid out like a row so it sits exactly under the bars.
        Row(horizontalArrangement = Arrangement.spacedBy(ColumnSpacing)) {
            Spacer(modifier = Modifier.weight(HeadlineWeight))
            Row(
                modifier = Modifier.weight(VisualWeight),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AxisLabel(dateText(startDateKey, DateStyle.DayMonth).resolve())
                AxisLabel(stringResource(R.string.statistics_axis_this_week))
            }
        }
    }
}

@Composable
private fun ConsistencyRow(
    consistency: FeatureConsistency,
    range: StatisticsRange,
    reveal: () -> Float,
) {
    val identity = LocalTrackerIdentity.current(consistency.ref)
    val spokenSummary = consistency.spokenSummary(range).resolve()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Before the summary, which clears whatever semantics follow it on this node.
            .testTag("statistics_consistency_${consistency.ref.type.value}_${consistency.ref.id}")
            .clearAndSetSemantics { contentDescription = spokenSummary },
        horizontalArrangement = Arrangement.spacedBy(ColumnSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Headline(
            icon = identity.icon,
            colors = identity.colors,
            value = consistency.headline().resolve(),
            caption = consistency.caption().resolve(),
            modifier = Modifier.weight(HeadlineWeight),
        )
        WeekBars(
            weeks = consistency.weeks,
            colors = identity.colors,
            reveal = reveal,
            modifier = Modifier
                .weight(VisualWeight)
                .height(WeekBarsHeight),
        )
    }
}

/** A block's one big number, in the feature's color, with the few words that say what it counts. */
@Composable
private fun Headline(
    icon: ImageVector,
    colors: ViewColors,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(HeadlineIconSize),
            tint = colors.accent,
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.label,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MeasurementBlock(measurement: MeasurementSummary, motionEnabled: Boolean) {
    val view = measurement.configuration
    val identity = view.presentation()
    val words = view.words()
    Column(
        modifier = Modifier.fillMaxWidth().testTag("statistics_measurement_${view.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Headline(
            icon = identity.icon,
            colors = identity.colors,
            value = measurement.headline(words).resolve(),
            caption = measurement.caption(words).resolve(),
        )
        MeasurementTrendChart(
            window = measurement.window,
            selectedDateKey = null,
            onDateSelected = null,
            colors = identity.colors,
            motionEnabled = motionEnabled,
            words = words,
            minimumSpacing = view.chartSpacing,
            modifier = Modifier.height(ChartHeight),
        )
        MeasurementDateAxis(days = measurement.window.days)
    }
}

/** The streak's calendar, with a key made of the same objects that also counts them. */
@Composable
private fun StreakBlock(
    calendar: StreakCalendar,
    range: StatisticsRange,
    reveal: () -> Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StreakCalendarGrid(
            calendar = calendar,
            rowHeight = range.calendarRowHeight,
            description = calendar.spokenSummary(range).resolve(),
            reveal = reveal,
        )
        CalendarKey {
            DrawnKeyItem(text = pluralStringResource(R.plurals.statistics_key_kept, calendar.keptDays, calendar.keptDays)) { drawStreakCoin() }
            if (calendar.frozenDays > 0) {
                DrawnKeyItem(text = pluralStringResource(R.plurals.statistics_key_frozen, calendar.frozenDays, calendar.frozenDays)) { drawFreezeCube() }
            }
            // One token per task, in that task's colours: whose token it was is the point. Its
            // number counts the days it covered, and a day two tokens covered counts for both.
            calendar.tokensByTracker.forEach { (ref, count) ->
                KeyItem(text = pluralStringResource(R.plurals.statistics_key_covered, count, count), symbol = { PassToken(LocalTrackerIdentity.current(ref), Modifier.size(KeySymbolSize)) })
            }
            if (calendar.protectedByOthers) {
                DrawnKeyItem(text = stringResource(R.string.statistics_key_protected)) { drawPassToken(neutralViewColors, stamp = null) }
            }
            if (calendar.missedDays > 0) {
                DrawnKeyItem(text = pluralStringResource(R.plurals.statistics_key_missed, calendar.missedDays, calendar.missedDays)) { drawSpentCoin() }
            }
            if (calendar.restDays > 0) {
                DrawnKeyItem(text = pluralStringResource(R.plurals.statistics_key_rest, calendar.restDays, calendar.restDays)) { drawRestToken() }
            }
        }
        // The days themselves, each once, however many tokens covered it: a total under the key,
        // not a kind of day in it. The calendar's spoken summary says it already.
        if (calendar.protectedDays > 0) {
            Text(
                text = pluralStringResource(R.plurals.statistics_streak_protected, calendar.protectedDays, calendar.protectedDays),
                modifier = Modifier.clearAndSetSemantics { },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Activity's rate, then the calendar of what was actually recorded and a key of its kinds. */
@Composable
private fun ActivityBlock(
    activity: ActivitySummary,
    range: StatisticsRange,
    reveal: () -> Float,
    configuration: com.example.workouttracker.domain.activity.ActivityConfiguration,
) {
    val identity = configuration.presentation()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("statistics_activity"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Headline(
            icon = identity.icon,
            colors = identity.colors,
            value = activity.consistency.headline().resolve(),
            caption = activity.consistency.caption().resolve(),
        )
        ActivityCalendarGrid(
            days = activity.days,
            configuration = configuration,
            colors = identity.colors,
            rowHeight = range.calendarRowHeight,
            description = activity.spokenSummary(range).resolve(),
            reveal = reveal,
        )
        if (activity.kinds.isNotEmpty()) {
            CalendarKey {
                activity.kinds.forEach { (kind, days) ->
                    KeyItem(
                        text = stringResource(R.string.statistics_activity_key, kind.label, days),
                        symbol = { ActivityKindSwatch(kind, configuration, identity.colors, Modifier.size(KeySymbolSize)) },
                    )
                }
            }
        }
    }
}

/** A calendar's key. The calendar's description already says all of it, so it stays silent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarKey(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

@Composable
private fun DrawnKeyItem(text: String, draw: DrawScope.() -> Unit) =
    KeyItem(text = text, symbol = { Canvas(Modifier.size(KeySymbolSize)) { draw() } })

@Composable
private fun KeyItem(text: String, symbol: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbol()
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A row is a week, so a longer range gets lower rows to stay a screen rather than a long scroll:
 * square cells for a month, lower ones for a quarter, and a strip for the year.
 */
private val StatisticsRange.calendarRowHeight: Dp
    get() = when (this) {
        StatisticsRange.FourWeeks -> 36.dp
        StatisticsRange.ThreeMonths -> 26.dp
        StatisticsRange.Year -> 14.dp
    }

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// The headline column and the visual beside it share every row's width in the same proportion,
// which is what lines the bars of six features up under one another.
private const val HeadlineWeight = 1f
private const val VisualWeight = 1f
private const val RevealMillis = 600
private val BlockSpacing = 28.dp
private val RowSpacing = 14.dp
private val ColumnSpacing = 12.dp
private val HeadlineIconSize = 28.dp
private val WeekBarsHeight = 44.dp
private val ChartHeight = 160.dp
private val KeySymbolSize = 18.dp
