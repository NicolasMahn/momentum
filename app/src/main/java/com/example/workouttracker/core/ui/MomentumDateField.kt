package com.example.workouttracker.core.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.example.workouttracker.R

/**
 * The one typed date field: a [MomentumTextField] that owns the date label and the invalid-date
 * wording, so every screen rejects a date in the same words.
 *
 * Validity is the caller's decision ([isError]); what a date key is lives in `core/time`.
 */
@Composable
internal fun MomentumDateField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    label: String = stringResource(R.string.core_date_field_label),
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    MomentumTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        error = stringResource(R.string.core_date_field_invalid).takeIf { isError },
        modifier = modifier,
        testTag = testTag,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = keyboardActions,
    )
}
