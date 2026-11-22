package com.example.workouttracker.feature.logactivity

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonDefaults
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumChoiceTile
import com.example.workouttracker.core.ui.MomentumRemoveButton
import com.example.workouttracker.core.ui.MomentumCard
import com.example.workouttracker.core.ui.MomentumCardDefaults
import com.example.workouttracker.core.ui.MomentumTileGrid
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.core.ui.platformAnimationsEnabled
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.activityNameKey
import com.example.workouttracker.feature.logactivity.gym.formatExerciseSets
import com.example.workouttracker.feature.logactivity.route.RouteMap
import com.example.workouttracker.feature.logactivity.route.RouteNumbers

internal enum class ActivityFormField {
    Name,
    Duration,
    General,
}

internal data class ActivityFormMessage(
    @param:StringRes val text: Int,
    val errorField: ActivityFormField? = null,
)

/**
 * The screen's choices as one row of equal tiles that never scrolls, [TilesInARow] at most, at any
 * width and in any language: a long name wraps or is cut inside its tile. A shorter row keeps its
 * tiles the width of a full one, so Other stays where it is while activities come and go.
 */
@Composable
internal fun ActivityTileRow(
    tiles: List<ActivityMark>,
    configuration: ActivityConfiguration,
    selected: ActivityMark,
    onSelect: (ActivityMark) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("activity_standard_choices"),
        horizontalArrangement = Arrangement.spacedBy(TileSpacing),
    ) {
        tiles.forEach { mark ->
            val look = configuration.lookOf(mark)
            StandardActivityCard(
                title = configuration.labelOf(mark).resolve(),
                icon = look.icon,
                iconContainerColor = look.containerColor,
                iconContentColor = look.contentColor,
                testTag = look.testTag,
                selected = mark == selected,
                onClick = { onSelect(mark) },
                modifier = Modifier.weight(1f),
            )
        }
        repeat(TilesInARow - tiles.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * What Other offers to pick: every activity without a tile, in rows of three, each in its own
 * icon and color. The chosen one is the one whose name the field below holds, so picking one
 * fills the field and typing a name is the same choice made by hand; nothing else is selected.
 */
@Composable
internal fun OtherActivityChoices(
    activities: List<ListedActivity>,
    configuration: ActivityConfiguration,
    chosenName: String,
    onChoose: (ListedActivity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosenKey = activityNameKey(chosenName)
    MomentumTileGrid(items = activities, columns = OtherColumns, modifier = modifier.testTag("activity_other_choices")) { activity, tileModifier ->
        val look = configuration.lookOf(ActivityMark.Listed(activity.id))
        MomentumChoiceTile(
            label = activity.label,
            selected = activityNameKey(activity.label) == chosenKey,
            onClick = { onChoose(activity) },
            modifier = tileModifier,
            icon = look.icon,
            accent = look.containerColor,
            testTag = "activity_other_${activity.id}",
        )
    }
}

/**
 * The name under Other's tiles: what a tile above put there, or a new activity typed by hand. The
 * error speaks in the one reserved line under the Save button; a supporting line here would only
 * wedge blank space above it.
 */
@Composable
internal fun ActivityNameField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag("activity_name"),
        label = { Text(stringResource(R.string.activity_name_label)) },
        placeholder = { Text(stringResource(R.string.activity_name_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        isError = isError,
    )
}

private val TileSpacing = 4.dp
private const val OtherColumns = 3

@Composable
internal fun StandardActivityCard(
    title: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    testTag: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MomentumCard(
        onClick = onClick,
        modifier = modifier.height(112.dp),
        selected = selected,
        // The whole edge takes the activity's own color, so the choice reads as that activity.
        selectedColors = MomentumCardDefaults.selectedColors(accent = iconContainerColor),
        role = Role.RadioButton,
        testTag = testTag,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // One vivid thing at a time: eight filled wells side by side turn the row into noise,
            // so only the chosen activity floods its well. The rest still name their color, in ink.
            IconContainer(
                icon = icon,
                contentDescription = null,
                containerColor = if (selected) {
                    iconContainerColor
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (selected) iconContentColor else iconContainerColor,
                containerSize = 48.dp,
                iconSize = 34.dp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Two lines, hyphenated, so a long name is read rather than cut; the tile's height is
            // fixed, so a wrapped name moves nothing beside or below the row.
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun IconContainer(
    icon: ImageVector,
    contentDescription: String?,
    containerColor: Color,
    contentColor: Color,
    containerSize: Dp = 42.dp,
    iconSize: Dp = 22.dp,
) {
    Surface(
        modifier = Modifier.size(containerSize),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * What the chosen day holds. Every row carries its two acts — edit and remove — at the right,
 * where the thumb already is; nothing has to be opened first. A gym session additionally expands,
 * because its sets are worth reading and there is no other place that shows them.
 */
@Composable
internal fun ActivityTimeline(
    items: List<ActivityTimelineItem>,
    configuration: ActivityConfiguration,
    dayLabel: String,
    expandedItemId: String?,
    onExpandedItemChange: (String?) -> Unit,
    onEdit: (ActivityTimelineItem) -> Unit,
    onRemove: (ActivityTimelineItem) -> Boolean,
) {
    if (items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_timeline"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The list sits under a calendar of 28 days, so it has to say which one it is showing.
        Text(
            text = dayLabel,
            modifier = Modifier.testTag("activity_timeline_day"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { item ->
            ActivityTimelineRow(
                item = item,
                configuration = configuration,
                expanded = item.id == expandedItemId,
                onExpandedChange = { expanded ->
                    onExpandedItemChange(if (expanded) item.id else null)
                },
                onEdit = { onEdit(item) },
                onRemove = { onRemove(item) },
            )
        }
    }
}

/**
 * Every entry is the same bare row on the background, whatever it holds: a ledge would promise a
 * press, and the row itself is not the pressable thing — its buttons are. A gym session adds a
 * chevron in the slot the others leave empty, so pencil and trash still line up down the day.
 */
@Composable
private fun ActivityTimelineRow(
    item: ActivityTimelineItem,
    configuration: ActivityConfiguration,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Boolean,
) {
    var removeError by remember(item.id) { mutableStateOf<Int?>(null) }
    val workout = item.source as? ActivityTimelineSource.Workout
    val route = (item.source as? ActivityTimelineSource.Logged)?.entry?.route
    val title = item.title.resolve()
    val motionEnabled = platformAnimationsEnabled()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_timeline_item_${item.id}"),
    ) {
        TimelineRowHeader(item = item, configuration = configuration) {
            if (workout != null || route != null) {
                QuietIconButton(
                    icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    description = stringResource(
                        when {
                            route != null && expanded -> R.string.activity_entry_hide_route
                            route != null -> R.string.activity_entry_show_route
                            expanded -> R.string.activity_entry_hide_sets
                            else -> R.string.activity_entry_show_sets
                        },
                        title,
                    ),
                    onClick = {
                        removeError = null
                        onExpandedChange(!expanded)
                    },
                    testTag = "activity_entry_expand_${item.id}",
                )
            } else {
                // The empty slot is kept, so the two buttons every row has share one column.
                Spacer(modifier = Modifier.width(MomentumButtonDefaults.QuietMinHeight))
            }
            QuietIconButton(
                icon = Icons.Rounded.Edit,
                description = stringResource(R.string.activity_entry_edit, title),
                onClick = onEdit,
                testTag = "activity_entry_edit_${item.id}",
            )
            MomentumRemoveButton(
                description = stringResource(R.string.activity_entry_remove, title),
                onRemove = {
                    removeError = if (onRemove()) null else R.string.activity_entry_remove_failed
                },
                testTag = "activity_entry_remove_${item.id}",
                resetKey = item.id,
                variant = MomentumButtonVariant.Quiet,
            )
        }
        ReservedMessage(
            text = removeError?.let { stringResource(it) },
            testTag = "activity_entry_message_${item.id}",
        )
        if (workout != null || route != null) {
            AnimatedVisibility(
                visible = expanded,
                enter = if (motionEnabled) expandVertically() + fadeIn() else EnterTransition.None,
                exit = if (motionEnabled) shrinkVertically() + fadeOut() else ExitTransition.None,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Indented under the row's text, not under its icon, so the sets read as
                        // the session's own detail.
                        .padding(start = RowTextInset, bottom = 4.dp)
                        .testTag("activity_timeline_detail_${item.id}"),
                ) {
                    if (workout != null) WorkoutSetReview(workout)
                    if (route != null) {
                        RouteNumbers(route = route, movingSeconds = route.movingSeconds, large = false)
                        RouteMap(route = route, modifier = Modifier.padding(top = 8.dp), testTag = "activity_route_map_${item.id}")
                    }
                }
            }
        }
    }
}

private val RowIconSize = 28.dp
private val RowSpacing = 8.dp
private val RowTextInset = RowIconSize + RowSpacing

@Composable
private fun TimelineRowHeader(
    item: ActivityTimelineItem,
    configuration: ActivityConfiguration,
    actions: @Composable RowScope.() -> Unit,
) {
    val look = configuration.lookOf(item.mark)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = look.icon,
            contentDescription = null,
            modifier = Modifier.size(RowIconSize),
            tint = look.containerColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title.resolve(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            item.detail?.resolve()?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

/** A pencil beside a trash can needs no caption either, and two captions would not fit the row. */
@Composable
private fun QuietIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    testTag: String,
) {
    MomentumButton(
        onClick = onClick,
        modifier = Modifier.width(MomentumButtonDefaults.QuietMinHeight),
        variant = MomentumButtonVariant.Quiet,
        contentPadding = PaddingValues(0.dp),
        testTag = testTag,
        faceModifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

/**
 * What a finished workout logged: one line per exercise, its sets condensed by
 * [formatExerciseSets]. A line per set filled the screen for a four-exercise workout and said the
 * same load four times over.
 */
@Composable
private fun WorkoutSetReview(source: ActivityTimelineSource.Workout) {
    val loggedExercises = source.session.exerciseLogs.filter { it.sets.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        loggedExercises.forEach { exerciseLog ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workout_review_exercise_${exerciseLog.exerciseId}"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = source.exerciseLabels[exerciseLog.exerciseId] ?: exerciseLog.exerciseName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    text = formatExerciseSets(exerciseLog.sets).resolve(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
