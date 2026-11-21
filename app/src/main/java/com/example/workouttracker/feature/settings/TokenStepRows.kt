package com.example.workouttracker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonDefaults
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumRemoveButton
import com.example.workouttracker.core.ui.ReservedMessage

/**
 * One step a week earns tokens by, as [TokenStepRows] lays it out: [lead], when the step is
 * reached, set in the width it is handed; [middle], what that comes to; the [tokens] reaching it
 * adds, which [onTokens] changes and TalkBack hears as [tokensLabel]; and, for a further step,
 * its two-step remove ([onRemove]), whose armed state [removeKey] resets when another step moves
 * into its row. The first step has no remove.
 */
internal class TokenStepRow(
    val lead: @Composable (Modifier) -> Unit,
    val middle: String,
    val tokens: Int,
    val tokensLabel: String,
    val onTokens: (Int) -> Boolean,
    val onRemove: (() -> Boolean)? = null,
    val removeKey: Any? = null,
)

/**
 * The steps a week earns tokens by, a time view's in percent of its goal and Activity's in days,
 * under "Tokens a week" and one line saying that a week earns the tokens of every step it
 * reaches. One row each ([TokenStepRow]), read from the first step up, each the same way left to
 * right: when, what that comes to, a stepper whose value reads "+1" and keeps within [tokens],
 * and the remove, whose place the first row keeps empty so the columns line up. Under the rows one line, [total], adds them
 * up, keeping the room of [widestTotal]; then "Add a step", disabled while no step can be added
 * ([canAdd]), so nothing under the rows ever moves but by a step added or removed.
 *
 * What a step comes to stands beside it while every row has room for it there on one line; on a
 * narrow phone it stands under the step, in its column, and at a large font the controls take a
 * line of their own as well ([StepRowLayout]). That is decided once, from the width the rows get,
 * the lead column's [leadWidth], the widest the middle can be ([widestMiddle]) and the controls,
 * so the columns stay aligned across rows and nothing typed or chosen ever moves a row.
 *
 * Tags follow from [testTag]: a row is `<testTag>_step_<number>`, counted from the first, what
 * it comes to `…_<middleTag>`, its stepper `…_tokens` and its remove `…_remove`; the line over
 * the rows is `<testTag>_steps_note`, the one under them `<testTag>_steps_total` and the button
 * `<testTag>_add_step`.
 */
@Composable
internal fun TokenStepRows(
    rows: List<TokenStepRow>,
    tokens: IntRange,
    leadWidth: Dp,
    widestMiddle: String,
    total: String,
    widestTotal: String,
    canAdd: Boolean,
    onAdd: () -> Unit,
    testTag: String,
    middleTag: String,
) {
    EditorGroup(stringResource(R.string.settings_steps)) {
        BoxWithConstraints {
            val layout = stepRowLayout(rowWidth = if (constraints.hasBoundedWidth) maxWidth else Dp.Infinity, leadWidth, widestMiddle, controlsWidth = stepControlsWidth(tokens.last))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EditorNote(stringResource(R.string.settings_steps_note), testTag = "${testTag}_steps_note")
                rows.forEachIndexed { index, row ->
                    StepRow(row, number = index + 1, tokens = tokens, leadWidth = leadWidth, layout = layout, testTag = "${testTag}_step_${index + 1}", middleTag = middleTag)
                }
                ReservedMessage(
                    text = total,
                    testTag = "${testTag}_steps_total",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    reserving = listOf(widestTotal),
                )
                MomentumButton(
                    text = stringResource(R.string.settings_add_step),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    variant = MomentumButtonVariant.Secondary,
                    icon = Icons.Rounded.Add,
                    enabled = canAdd,
                    testTag = "${testTag}_add_step",
                )
            }
        }
    }
}

/** The widest of [texts] set in [style], for a lead column of plain words. */
@Composable
internal fun widestTextWidth(texts: List<String>, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    return with(LocalDensity.current) { texts.maxOf { measurer.measure(it, style).size.width }.toDp() }
}

/** How wide a row's controls are: the stepper with the widest value it can show, [mostTokens], the gap, and the remove. */
@Composable
internal fun stepControlsWidth(mostTokens: Int): Dp =
    compactStepperWidth(stringResource(R.string.settings_step_tokens_added, mostTokens)) + StepColumnGap + MomentumButtonDefaults.QuietMinHeight

/**
 * How every row is laid out, decided once from the width the rows get and what they must hold,
 * never from what is typed: on one line while the lead column, the widest middle and the
 * controls fit; with the middle under the lead while the wider of the two still fits beside the
 * controls; and otherwise, on a narrow phone at a large font, with the lead, the middle and the
 * controls each on a line of their own, so neither the lead nor the middle is ever cut.
 */
internal enum class StepRowLayout { OneLine, MiddleUnder, Stacked }

@Composable
internal fun stepRowLayout(rowWidth: Dp, leadWidth: Dp, widestMiddle: String, controlsWidth: Dp): StepRowLayout {
    if (rowWidth == Dp.Infinity) return StepRowLayout.OneLine
    val middleWidth = widestTextWidth(listOf(widestMiddle), MaterialTheme.typography.bodyLarge)
    val besideTheControls = rowWidth - StepColumnGap - controlsWidth
    return when {
        besideTheControls - leadWidth - StepColumnGap - StepLeadGap >= middleWidth -> StepRowLayout.OneLine
        besideTheControls >= maxOf(leadWidth, middleWidth) -> StepRowLayout.MiddleUnder
        else -> StepRowLayout.Stacked
    }
}

@Composable
private fun StepRow(row: TokenStepRow, number: Int, tokens: IntRange, leadWidth: Dp, layout: StepRowLayout, testTag: String, middleTag: String) {
    val lead: @Composable () -> Unit = { row.lead(Modifier.width(leadWidth)) }
    val middle: @Composable (Modifier) -> Unit = { modifier ->
        Text(text = row.middle, modifier = modifier.testTag("${testTag}_$middleTag"), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
    val stepper: @Composable () -> Unit = {
        NumberStepper(
            label = row.tokensLabel,
            value = row.tokens,
            range = tokens,
            valueText = stringResource(R.string.settings_step_tokens_added, row.tokens),
            testTag = "${testTag}_tokens",
            onChange = row.onTokens,
            compact = true,
        )
    }
    // The first row keeps the remove's place empty, so the columns line up.
    val remove: @Composable () -> Unit = {
        val onRemove = row.onRemove
        if (onRemove == null) {
            Spacer(Modifier.width(MomentumButtonDefaults.QuietMinHeight))
        } else {
            MomentumRemoveButton(
                description = stringResource(R.string.settings_remove_step, number),
                onRemove = { onRemove() },
                testTag = "${testTag}_remove",
                // A row keeps its place when the step above it goes: an armed button must not pass to the step that moves in.
                resetKey = row.removeKey,
                variant = MomentumButtonVariant.Quiet,
            )
        }
    }
    when (layout) {
        StepRowLayout.OneLine -> Row(Modifier.testTag(testTag).heightIn(min = StepRowHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StepColumnGap)) {
            lead()
            middle(Modifier.weight(1f).padding(start = StepLeadGap))
            stepper()
            remove()
        }
        StepRowLayout.MiddleUnder -> Row(Modifier.testTag(testTag).heightIn(min = StepRowHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StepColumnGap)) {
            Column(modifier = Modifier.weight(1f)) {
                lead()
                middle(Modifier)
            }
            stepper()
            remove()
        }
        StepRowLayout.Stacked -> Column(Modifier.testTag(testTag), verticalArrangement = Arrangement.spacedBy(StepColumnGap)) {
            lead()
            middle(Modifier)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                stepper()
                Spacer(Modifier.weight(1f))
                remove()
            }
        }
    }
}

/** Clear space between the lead column and the middle beside it, over the gap the other columns keep. */
internal val StepLeadGap = 12.dp

/** A typed field's height, so a row whose lead is words stands as tall as one whose lead is a field. */
private val StepRowHeight = 56.dp

internal val StepColumnGap = 4.dp
