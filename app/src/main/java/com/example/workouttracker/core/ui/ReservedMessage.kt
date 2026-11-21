package com.example.workouttracker.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

/**
 * A line that keeps its space whether or not it speaks, so a message that comes and goes never
 * moves what sits under it. Use it wherever a save can fail under a button the thumb just pressed.
 *
 * The slot is one blank line tall, which is the message's whole height while it fits on one. A
 * message that may wrap, at this width and the reader's font scale, is named in [reserving]: every
 * sentence the slot may show, laid out unseen, so the slot is as tall as the tallest of them and
 * holds that height while it says nothing.
 *
 * The tagged node exists only while there is a [text]: an empty slot is silent for TalkBack and
 * absent for tests, so "no message" really means no node. A message is announced when it
 * appears; a line that is part of something else's label, as a settings row's line is, is read
 * with that label instead ([announced] off).
 */
@Composable
internal fun ReservedMessage(
    text: String?,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    color: Color = MaterialTheme.colorScheme.error,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    reserving: List<String> = emptyList(),
    announced: Boolean = true,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // One blank line of the message's own type is the least the slot reserves, so it is
        // exactly as tall as what may appear in it and grows with the reader's font scale; each
        // sentence named is laid out beside it, unseen, for the height it wraps to here.
        (listOf("") + reserving).forEach { reserved ->
            Text(
                text = reserved,
                modifier = Modifier
                    .alpha(0f)
                    .clearAndSetSemantics {},
                style = style,
                minLines = 1,
            )
        }
        if (text != null) {
            Text(
                text = text,
                modifier = Modifier
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                    .then(if (announced) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
                style = style,
                color = color,
            )
        }
    }
}
