package com.example.workouttracker.app

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.core.ui.appPlaceViewColors
import com.example.workouttracker.core.ui.momentumScrollFades
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.feature.tracker.TrackerHomeStatus
import com.example.workouttracker.feature.tracker.TrackerView
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.feature.substances.viewIdentity
import com.example.workouttracker.feature.substances.viewLabel
import com.example.workouttracker.core.text.resolve
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.workouttracker.domain.mo.MoEvent
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.feature.mo.MoAtHisBest
import com.example.workouttracker.feature.mo.MoBackfill
import com.example.workouttracker.feature.mo.MoEventTakeover
import com.example.workouttracker.feature.mo.MoLevelTakeover
import com.example.workouttracker.feature.mo.MoLook
import com.example.workouttracker.feature.mo.MoLossTakeover
import com.example.workouttracker.feature.mo.MoHeader
import com.example.workouttracker.feature.mo.acts.MoScene

@Composable
internal fun HomeScreen(
    mood: MoMood,
    onOpenStreak: () -> Unit = {},
    moIsAngry: Boolean,
    moScene: MoScene?,
    moEvent: MoEvent?,
    onMoEventDone: () -> Unit,
    celebrateMo: TrackerRef?,
    /** Which of an activity's acts a save plays today. */
    moSaveTurn: Int = 0,
    onMoCelebrated: () -> Unit,
    featureVisibility: FeatureVisibilitySettings,
    onLogActivity: () -> Unit,
    /** Activity's stored configuration, whose label and look its tile wears. */
    activity: ActivityConfiguration? = null,
    /** Substances' stored settings, whose name and look its tile wears. */
    substances: SubstanceSettings = SubstanceSettings(),
    trackers: List<TrackerView> = emptyList(),
    onOpenTracker: (TrackerRef) -> Unit = {},
    activityStatus: TrackerHomeStatus = TrackerHomeStatus.None,
    substanceStatus: TrackerHomeStatus = TrackerHomeStatus.None,
    onOpenSubstances: () -> Unit,
    /** Each view's picture of today, by its tracker. */
    pictures: Map<TrackerRef, HomeDayPicture> = emptyMap(),
    /** When each tracker's open parts are due today, as minutes since the habit day began; see [homeRowRank]. */
    dueMinutes: (TrackerRef) -> List<Int> = { emptyList() },
    /** Minutes since the habit day began, now. */
    nowMinute: Int = 0,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Shares the streak as a picture, with Mo as [look] shows him. */
    onShareStreak: (MoLook) -> Unit = {},
    /** Hands the statistics export to the share sheet, for an agent to reason about. */
    onTalkToAgent: () -> Unit = {},
    /** The views whose miss broke a lost streak, offered to be filled in. */
    lossBackfills: List<MoBackfill> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val activityCards = if (featureVisibility.isVisible(DashboardFeature.Activity)) listOf(
        Launcher("home_log_activity", onLogActivity, { activity?.label ?: stringResource(AppDestination.Activity.label) }, { activity?.presentation() ?: AppDestination.Activity.viewIdentity() }, activityStatus, 0, ActivityTrackerRef),
    ) else emptyList()
    val trackerCards = trackers.filter { it.visible && it.usable }.map { view ->
        // Mo watches every bank that has an observation. One that came without, as an extension
        // module's may, is none of his tasks, so its standing in the streak speaks for it.
        val sharedStatus = mood.trackerProgress[view.ref]?.let { progress ->
            trackerHomeStatus(progress).let { if (it == TrackerHomeStatus.Done) TrackerHomeStatus.Kept else it }
        } ?: if (view.ref in mood.trackerOrder) TrackerHomeStatus.None else mood.streak.standings[view.ref]?.let { standing ->
            when {
                standing.keptToday -> TrackerHomeStatus.Kept
                standing.openToday -> TrackerHomeStatus.Open(0, 1)
                else -> TrackerHomeStatus.None
            }
        } ?: TrackerHomeStatus.None
        val status = view.homeStatus ?: sharedStatus
        Launcher("home_${view.tagName}", { onOpenTracker(view.ref) }, { view.label }, { view.identity() }, status, view.homeOrder, view.ref)
    }
    val substanceCards = if (featureVisibility.isVisible(DashboardFeature.Substances)) listOf(
        Launcher("home_substances", onOpenSubstances, { substances.viewLabel().resolve() }, { substances.viewIdentity() }, substanceStatus, 1000, SubstancesTrackerRef),
    ) else emptyList()
    val unsorted = (activityCards + trackerCards + substanceCards).sortedBy { it.order }
    // What is next comes first. The order is settled each time Home is shown, opened from a view or
    // brought back from the background, and kept while it stays in front, so no row moves under a
    // finger as a minute passes. The app returning stays on this same Home, so its start sorts too.
    var shown by remember { mutableIntStateOf(0) }
    LifecycleStartEffect(Unit) {
        shown++
        onStopOrDispose {}
    }
    val order = remember(unsorted.map { it.testTag }, shown) {
        unsorted.sortedWith(compareBy { row -> homeRowRank(row.status, row.ref?.let(dueMinutes).orEmpty(), nowMinute).let { (group, minute) -> group.ordinal * MinutesInADay + minute } })
            .map { it.testTag }
    }
    val rows = unsorted.sortedBy { row -> order.indexOf(row.testTag).let { if (it < 0) Int.MAX_VALUE else it } }

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HomePadding, vertical = 12.dp),
        ) {
            // Mo keeps the same share of the screen however many views there are; the rows scroll under him.
            val moHeight = (maxHeight * MoShare - ScoreBarHeight).coerceAtLeast(MinimumMoHeight)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(HomeGap),
            ) {
                MoHeader(
                    mood,
                    moHeight,
                    angry = moIsAngry,
                    scene = moScene,
                    celebrate = celebrateMo,
                    saveTurn = moSaveTurn,
                    onCelebrated = onMoCelebrated,
                    onOpenStreak = onOpenStreak,
                    lift = MoLift,
                )
                // The rows scroll on their own, so Mo and the bar stay where they are, and they fade
                // out at an edge with more beyond it, across the whole screen.
                val rowsScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Mo stands lifted, so his floor reaches down to here; the rows keep clear of it.
                        .padding(top = RowsClearance)
                        .momentumScrollFades(rowsScroll, bleed = HomePadding)
                        .verticalScroll(rowsScroll),
                    verticalArrangement = Arrangement.spacedBy(HomeRowDefaults.Gap),
                ) {
                    rows.forEach { row ->
                        val identity = row.identity()
                        HomeRow(
                            title = row.title(),
                            icon = identity.icon,
                            colors = identity.colors,
                            picture = pictures[row.ref] ?: HomeDayPicture.Empty,
                            status = row.status,
                            onClick = row.onClick,
                            testTag = row.testTag,
                        )
                    }
                }
                HomeBottomBar(onOpenStatistics = onOpenStatistics, onOpenSettings = onOpenSettings)
            }
        }
        // Drawn over all of Home, its padding included, and laid out by nothing, so Home itself never moves for it.
        when (moEvent) {
            null -> Unit
            is MoEvent.LevelReached -> MoLevelTakeover(
                arrival = moEvent.arrival,
                days = mood.streak.days,
                onShare = { onShareStreak(MoAtHisBest) },
                onDone = onMoEventDone,
            )
            // Whatever is chosen on it, the loss has been seen.
            is MoEvent.StreakLost -> MoLossTakeover(
                loss = moEvent.loss,
                backfills = lossBackfills.map { backfill -> MoBackfill(backfill.label, backfill.dateText) { onMoEventDone(); backfill.onOpen() } },
                onRestart = onMoEventDone,
                onStrategy = { onMoEventDone(); onOpenSettings() },
                onTalk = { onMoEventDone(); onTalkToAgent() },
            )
            else -> MoEventTakeover(moEvent, mood, onDone = onMoEventDone)
        }
    }
}

/**
 * Statistics and Settings are places of the app, not views of a day, so they sit apart from the
 * rows, side by side, as buttons in the places' own neutral colors. Half the width is narrow for
 * "Einstellungen" at a large font or on a small phone: the icons give their room to the labels
 * first, and only then do the labels shrink. The two always stand side by side, so the rows above
 * keep their room.
 */
@Composable
internal fun HomeBottomBar(
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val labels = listOf(AppDestination.Statistics, AppDestination.Settings).map { stringResource(it.label) }
    val labelStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    ViewTheme(appPlaceViewColors()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labelRoom = (maxWidth - PlaceGap) / 2 - PlacePadding * 2 - PlaceIconSize - PlaceIconGap
            val widestLabel = labels.maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width }
            val icons = widestLabel <= with(LocalDensity.current) { labelRoom.roundToPx() }
            Row(
                modifier = Modifier.fillMaxWidth().height(BottomBarHeight),
                horizontalArrangement = Arrangement.spacedBy(PlaceGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaceButton(AppDestination.Statistics, labels[0], labelStyle, icons, onOpenStatistics, "home_statistics", Modifier.weight(1f))
                PlaceButton(AppDestination.Settings, labels[1], labelStyle, icons, onOpenSettings, "home_settings", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlaceButton(
    place: AppDestination,
    label: String,
    labelStyle: TextStyle,
    icon: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier,
) {
    MomentumButton(
        onClick = onClick,
        modifier = modifier,
        variant = MomentumButtonVariant.Secondary,
        testTag = testTag,
        contentPadding = PaddingValues(horizontal = PlacePadding),
    ) {
        if (icon) {
            Icon(place.viewIdentity().icon, contentDescription = null, modifier = Modifier.size(PlaceIconSize))
            Spacer(Modifier.width(PlaceIconGap))
        }
        Text(
            text = label,
            style = labelStyle,
            autoSize = TextAutoSize.StepBased(minFontSize = PlaceLabelMinSize, maxFontSize = labelStyle.fontSize, stepSize = 0.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val PlaceGap = 12.dp
private val PlacePadding = 12.dp
private val PlaceIconSize = 22.dp
private val PlaceIconGap = 8.dp
private val PlaceLabelMinSize = 12.sp

private const val MinutesInADay = 24 * 60

private val HomeGap = 12.dp
private val ScoreBarHeight = 56.dp
private val BottomBarHeight = 56.dp
private val MinimumMoHeight = 150.dp
private const val MoShare = 0.5f

/** Mo stands this much higher than his room would put him, closer under the score. */
private val MoLift = 32.dp

/** The room between Mo's floor and the first row. */
private val RowsClearance = 12.dp
private val HomePadding = 16.dp

private class Launcher(
    val testTag: String,
    val onClick: () -> Unit,
    val title: @Composable () -> String,
    val identity: @Composable () -> ViewIdentity,
    val status: TrackerHomeStatus = TrackerHomeStatus.None,
    val order: Int = 1000,
    val ref: TrackerRef? = null,
)
