package com.example.workouttracker.feature.mo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.LocalTrackerIdentity
import com.example.workouttracker.core.ui.LocalViewColors
import com.example.workouttracker.core.ui.MomentumCard
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.ActivityTrackerRef
import androidx.annotation.StringRes
import com.example.workouttracker.domain.mo.canRest
import com.example.workouttracker.domain.mo.choosesByCheckpoint
import com.example.workouttracker.domain.mo.tokensCover
import com.example.workouttracker.domain.mo.tokensHeld

/**
 * How many rest days this week plans for Activity: the days its target leaves free. A holiday or
 * sick week lowers the target, so it plans more.
 */
internal fun MoMood.plannedRestDays(): Int =
    (DAYS_PER_WEEK - (trackerWeekProgress[ActivityTrackerRef]?.expected ?: DAYS_PER_WEEK)).coerceAtLeast(streak.freeDaysLeftByTracker[ActivityTrackerRef] ?: 0)

/** Reservations are presentation of the neutral reward policy; they never create records. */
internal fun MoMood.canSetAside(ref: TrackerRef): Boolean = ref in chosenTrackersToday || tokensCover(ref)

/**
 * Whether [ref]'s tokens stand on its checkpoints and not in its view's header: a checklist with
 * a bank whose tokens are set aside a checkpoint at a time. Each checkpoint then shows the token
 * and the number left, so it is plain which one a token goes to.
 */
internal fun MoMood.tokensOnCheckpoints(ref: TrackerRef): Boolean = streak.standings[ref] != null && choosesByCheckpoint(ref)

/** Whether [ref] can be told to rest today, or told so already and free to take it back. */
internal fun MoMood.canChooseRest(ref: TrackerRef): Boolean = ref in restChosenTrackers || canRest(ref)

/** The tokens held for [ref]: all it leaves open once they cover it, else one for each checkpoint set aside. */
internal fun MoMood.tokensSetAside(ref: TrackerRef): Int =
    if (ref in chosenTrackersToday) trackerTokenRequirements[ref] ?: trackerOpenParts[ref].orEmpty().size
    else checkpointsSetAside[ref].orEmpty().size

internal fun MoMood.tokensLeft(ref: TrackerRef): Int = (tokensHeld(ref) - tokensSetAside(ref)).coerceAtLeast(0)

/**
 * What a task's view header says about the game: the tokens it holds and, for Activity, the rest
 * days left this week. A day count would only repeat today's checkmark. [tokens] is null while the
 * task has no bank.
 *
 * While a token can still cover the task, [onReserve] makes the tokens a pressable card, the same
 * switch the streak view offers: pressing it reserves a token, and [reserved] shows it. A task
 * judged by its week is reserved for [thisWeek], any other for today.
 *
 * While a rest day covers a day the plan still asks for, [onRest] makes the rest days the same
 * kind of switch: pressing it says the day is a rest day, and [resting] shows it.
 *
 * Before a week's amount reaches its Sunday, a token cannot cover it yet, since the week can still
 * be made up. [onQuiet] then puts Mo's "Zz" before the tokens, a switch that asks him not to ask
 * today, and [quiet] shows it. The tokens stay as they are.
 */
@Composable
internal fun MoTaskStanding(
    ref: TrackerRef,
    label: UiText,
    tokens: Int?,
    restDays: Int?,
    modifier: Modifier = Modifier,
    reserved: Boolean = false,
    onReserve: ((Boolean) -> Unit)? = null,
    thisWeek: Boolean = false,
    resting: Boolean = false,
    onRest: ((Boolean) -> Unit)? = null,
    quiet: Boolean = false,
    onQuiet: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (restDays != null) {
            val spoken = pluralStringResource(R.plurals.mo_rest_days_left, restDays, restDays)
            val content: @Composable () -> Unit = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RestToken(Modifier.size(24.dp), empty = restDays == 0)
                    Count(restDays)
                }
            }
            if (onRest != null) {
                val action = stringResource(R.string.mo_rest_today_action)
                val state = stringResource(if (resting) R.string.mo_resting_today else R.string.mo_can_rest_today)
                MomentumCard(
                    onClick = { onRest(!resting) },
                    modifier = Modifier.semantics {
                        contentDescription = "$spoken. $action"
                        stateDescription = state
                    },
                    selected = resting,
                    role = Role.Switch,
                    testTag = "top_rest_days",
                ) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { content() }
                }
            } else {
                Box(Modifier.testTag("top_rest_days").semantics(mergeDescendants = true) { contentDescription = spoken }) {
                    content()
                }
            }
        }
        if (onQuiet != null) {
            val action = stringResource(R.string.mo_quiet_today_action)
            val state = stringResource(if (quiet) R.string.mo_quiet_today else R.string.mo_asks_today)
            MomentumCard(
                onClick = { onQuiet(!quiet) },
                modifier = Modifier.semantics {
                    contentDescription = action
                    stateDescription = state
                },
                selected = quiet,
                role = Role.Switch,
                testTag = "top_quiet",
            ) {
                QuietMark(LocalViewColors.current.label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp).size(24.dp))
            }
        }
        if (tokens != null) {
            val spoken = pluralText(R.plurals.mo_task_tokens, tokens, tokens, label).resolve()
            val identity = LocalTrackerIdentity.current(ref)
            val content: @Composable () -> Unit = { PassTokenCount(identity, tokens, faded = tokens == 0) }
            if (onReserve != null) {
                val action = stringResource(if (thisWeek) R.string.mo_reserve_tokens_for_week else R.string.mo_reserve_token_for, label.resolve())
                val state = stringResource(reservationState(reserved, thisWeek))
                MomentumCard(
                    onClick = { onReserve(!reserved) },
                    modifier = Modifier.semantics {
                        contentDescription = "$spoken. $action"
                        stateDescription = state
                    },
                    selected = reserved,
                    role = Role.Switch,
                    testTag = "top_tokens",
                ) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { content() }
                }
            } else {
                Box(Modifier.testTag("top_tokens").semantics(mergeDescendants = true) { contentDescription = spoken }) {
                    content()
                }
            }
        }
    }
}

/**
 * A bank's token with the number of tokens left beside it, as a view's header and a checkpoint
 * show it. It says nothing itself: whatever holds it speaks for both.
 */
@Composable
internal fun PassTokenCount(identity: ViewIdentity, count: Int, faded: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PassToken(identity, Modifier.size(26.dp), empty = faded)
        Count(count)
    }
}

@Composable
private fun Count(value: Int) {
    Text(
        text = "$value",
        modifier = Modifier.clearAndSetSemantics {},
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = LocalViewColors.current.label,
    )
}

/** What a token switch says of itself: reserved or not, for today or, for a task judged by its week, for the week. */
@StringRes
internal fun reservationState(reserved: Boolean, thisWeek: Boolean): Int = when {
    thisWeek -> if (reserved) R.string.mo_reserved_this_week else R.string.mo_can_reserve_this_week
    else -> if (reserved) R.string.mo_reserved_today else R.string.mo_can_reserve_today
}

private const val DAYS_PER_WEEK = 7
