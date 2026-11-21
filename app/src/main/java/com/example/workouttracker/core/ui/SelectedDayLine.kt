package com.example.workouttracker.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.workouttracker.core.format.dateText
import com.example.workouttracker.core.text.resolve

/**
 * Names the day a form is about, only while that is not the day the screen opens on
 * ([openingDateKey]). A reminder or a calendar tap can put the form on another day, and a
 * correction there must not be mistaken for the day's own entry; on the opening day the line
 * would only repeat what the screen already means.
 *
 * It takes no room while it says nothing, unlike a [ReservedMessage]: it is the first thing under
 * the header, where a kept slot is an empty band on the day the screen is opened on nearly every
 * time. So picking another day moves the form down by this line, and picking the opening day
 * brings it back up. Put it in a spaced column, whose spacing then comes and goes with it.
 */
@Composable
internal fun SelectedDayLine(
    selectedDateKey: String,
    openingDateKey: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    if (selectedDateKey == openingDateKey) return
    Text(
        text = dateText(selectedDateKey).resolve(),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
