package com.example.workouttracker.feature.logactivity

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.core.ui.theme.MomentumHue
import com.example.workouttracker.core.ui.theme.MomentumHues
import com.example.workouttracker.core.ui.theme.MomentumRampStep
import com.example.workouttracker.core.ui.theme.MomentumRamps
import com.example.workouttracker.core.ui.viewColors
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.OtherActivityIcon
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.feature.checklist.vector

/**
 * What a tile, a timeline row, a calendar dot or a Statistics glyph stands for: an activity the
 * configuration lists, a day of an activity's routine, or Other, the tile for a name a person
 * types. Its words and its look come from the configuration ([labelOf], [lookOf]), so a rename or
 * a new icon shows everywhere, old days included, and a record of something the configuration
 * never knew falls back on the name it keeps.
 */
sealed interface ActivityMark {
    data class Listed(val activityId: String) : ActivityMark

    data class RoutineDay(val activityId: String, val dayId: String) : ActivityMark

    data object Other : ActivityMark
}

/** How a mark is drawn: its icon, the color it names an activity by, and the tag its tile carries. */
internal data class ActivityLook(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val testTag: String = "",
)

/** The identity Activity wore before it was stored, which a state without its configuration still wears. */
@Composable
internal fun logActivityPresentation(): ViewIdentity = ViewIdentity(
    icon = Icons.Rounded.Hiking,
    colors = MomentumHues.Green.viewColors(),
)

/**
 * The words [mark] is called by: an activity's label, or the [recordedName] an entry keeps for
 * an activity the configuration never knew; a routine day's after its activity's; and Other's word.
 */
internal fun ActivityConfiguration.labelOf(mark: ActivityMark, recordedName: String = ""): UiText = when (mark) {
    is ActivityMark.Listed -> UiText.Verbatim(activityLabel(mark.activityId, recordedName))
    is ActivityMark.RoutineDay -> uiText(
        R.string.activity_gym_session_title,
        activityLabel(mark.activityId, mark.activityId),
        activity(mark.activityId)?.routine?.dayLabel(mark.dayId) ?: mark.dayId,
    )
    ActivityMark.Other -> uiText(R.string.activity_choice_other)
}

/** How many tiles the screen's one row holds, Other among them. */
internal const val TilesInARow = 4

/**
 * The tiles the screen offers, in one row: the first activities of the list that are not removed,
 * up to [TilesInARow] in all with Other, which offers the rest. A person picks which activities
 * are tiles by their order in the list; Gym is first at the start.
 */
internal fun ActivityConfiguration.tiles(): List<ActivityMark> =
    retainedActivities.take(TilesInARow - 1).map { ActivityMark.Listed(it.id) } + ActivityMark.Other

/** The activities Other offers to pick: every one still offered that has no tile of its own, in the list's order. */
internal fun ActivityConfiguration.activitiesUnderOther(): List<ListedActivity> = retainedActivities.drop(TilesInARow - 1)

/**
 * The look of [mark]. An activity has no hue of its own: the four sport icons keep the colors
 * their tiles always had, the dumbbell the gym's green, and any other icon takes a color of
 * Activity's palette chosen by the activity's ID, so activities stay apart and keep their color
 * for life. A routine day wears its activity's color; the dumbbell's days keep the three gym
 * shades by their place, fanned out around the gym's own step, so three cells stay far enough
 * apart to tell apart while all still read as gym. Other, with no name yet, owns Pink.
 */
internal fun ActivityConfiguration.lookOf(mark: ActivityMark): ActivityLook = when (mark) {
    is ActivityMark.Listed -> listedLook(mark.activityId).copy(testTag = "activity_choice_${mark.activityId}")
    is ActivityMark.RoutineDay -> {
        val activity = activity(mark.activityId)
        if (activity?.icon == ViewIcon.Dumbbell) {
            (activity.routine?.placeOf(mark.dayId)?.let { GymDaySteps[it % GymDaySteps.size] } ?: GymStep).look(Icons.Rounded.FitnessCenter)
        } else {
            listedLook(mark.activityId)
        }
    }
    ActivityMark.Other -> CustomTileHue.look(OtherActivityIcon.vector, testTag = "activity_choice_custom")
}

private fun ActivityConfiguration.listedLook(activityId: String): ActivityLook {
    val icon = activity(activityId)?.icon ?: OtherActivityIcon
    return when (icon) {
        ViewIcon.Dumbbell -> GymStep.look(icon.vector)
        ViewIcon.Bouldering -> MomentumHues.Indigo.look(icon.vector)
        ViewIcon.Bicycle -> MomentumHues.Yellow.look(icon.vector)
        ViewIcon.Running -> MomentumHues.Purple.look(icon.vector)
        else -> CustomActivityHues[Math.floorMod(activityId.hashCode(), CustomActivityHues.size)].look(icon.vector)
    }
}

private fun MomentumHue.look(icon: ImageVector, testTag: String = "") = ActivityLook(icon, containerColor = face, contentColor = ink, testTag = testTag)

private fun MomentumRampStep.look(icon: ImageVector, testTag: String = "") = ActivityLook(icon, containerColor = fill, contentColor = ink, testTag = testTag)

private val GymGreenFamily = MomentumRamps.GreenWide

/** The gym keeps the step closest to the view's own green. */
private val GymStep = GymGreenFamily[2]

/** The three shades the gym's days cycle through, around the gym's own step. */
private val GymDaySteps = listOf(GymGreenFamily[0], GymGreenFamily[1], GymGreenFamily[3])

private val CustomTileHue = MomentumHues.Pink

/** No other activity may be green or teal, or a calendar full of gyms would stop reading as gyms. */
private val CustomActivityHues = listOf(
    MomentumHues.Pink,
    MomentumHues.Orange,
    MomentumHues.Sky,
)
