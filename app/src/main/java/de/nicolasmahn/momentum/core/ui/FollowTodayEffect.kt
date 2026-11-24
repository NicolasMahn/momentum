package de.nicolasmahn.momentum.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Lets a screen that is still resting on "today" move to the new day when the day changes, without
 * ever keying its state on today.
 *
 * A draft belongs to the selected date. Keying it on [todayKey] (`remember(todayKey)`) replaces it
 * on the first recomposition after the habit day changes, which silently drops unsaved work. Screens instead
 * remember their selection unkeyed and ask here to be moved: [canFollow] receives the day that was
 * "today" until now and answers whether the screen is still untouched on it; only then does
 * [onFollow] get the new day.
 *
 * The day followed is saved state, as a screen's selection is: a screen brought back after its
 * process died overnight still rests on the earlier day, and must be asked to move as well.
 */
@Composable
internal fun FollowTodayEffect(
    todayKey: String,
    canFollow: (previousTodayKey: String) -> Boolean,
    onFollow: (todayKey: String) -> Unit,
) {
    var followedKey by rememberSaveable { mutableStateOf(todayKey) }
    val currentCanFollow by rememberUpdatedState(canFollow)
    val currentOnFollow by rememberUpdatedState(onFollow)
    LaunchedEffect(todayKey) {
        if (todayKey == followedKey) return@LaunchedEffect
        val previousTodayKey = followedKey
        followedKey = todayKey
        if (currentCanFollow(previousTodayKey)) currentOnFollow(todayKey)
    }
}
