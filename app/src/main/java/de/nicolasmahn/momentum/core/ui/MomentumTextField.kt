package de.nicolasmahn.momentum.core.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * A single-line field whose error speaks in the label's place. A supporting line would either grow
 * the field when it appears or, reserved in advance, leave a permanent gap under a valid one; the
 * label already has a line, so the error borrows it and nothing around the field ever moves. An
 * error is read whole: it keeps the label's minimized size even while it rests inside an empty,
 * unfocused field, where a plain label is set large.
 *
 * The error is also the field's accessibility error and a polite live region, so TalkBack says it
 * when it appears. [testTag] lands on the text field itself, so tests type into and read from the
 * same node. A field that only shows what cannot be changed here is not [enabled]; nothing can
 * scroll its line then, so it wraps and shows all of its text. A number with a [suffix] is set
 * against it with [textAlign] at the end, so "200 %" reads as one value.
 */
@Composable
internal fun MomentumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    enabled: Boolean = true,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    suffix: String? = null,
    textAlign: TextAlign = TextAlign.Start,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = LocalTextStyle.current.copy(textAlign = textAlign),
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics {
                if (error != null) {
                    error(error)
                    liveRegion = LiveRegionMode.Polite
                }
            },
        enabled = enabled,
        label = {
            Text(
                text = error ?: label,
                style = if (error != null) MaterialTheme.typography.bodySmall else LocalTextStyle.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        placeholder = placeholder?.let { text -> { Text(text) } },
        leadingIcon = leadingIcon,
        suffix = suffix?.let { text -> { Text(text) } },
        isError = error != null,
        singleLine = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}
