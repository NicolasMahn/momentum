package de.nicolasmahn.momentum.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.ui.MomentumActionBar
import de.nicolasmahn.momentum.core.ui.MomentumPopOutGroup
import de.nicolasmahn.momentum.core.ui.ActionBarLine
import de.nicolasmahn.momentum.core.ui.MomentumBarAction
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonDefaults
import de.nicolasmahn.momentum.core.ui.MomentumButtonLabel
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumDialog
import de.nicolasmahn.momentum.core.ui.MomentumDialogAction
import de.nicolasmahn.momentum.core.ui.MomentumTabs
import de.nicolasmahn.momentum.core.ui.ReportUnsavedWork
import de.nicolasmahn.momentum.core.ui.ViewColors
import de.nicolasmahn.momentum.core.ui.ViewTheme
import kotlinx.coroutines.flow.drop

/** One line the bar of a page can say, and the tag it is found by. */
internal typealias BarLine = ActionBarLine

/** The one action a level of a part puts in the bar while it is shown, such as Add where something is made. */
internal class BarButton(val text: String, val enabled: Boolean, val testTag: String, val onClick: () -> Unit)

/** What a page asks before its unsaved work is lost. */
internal class DiscardQuestion(val title: String, val message: String)

/** What Delete is on the page of a view that exists. */
internal sealed interface ViewDelete {
    /** A view a person made can go, with its records: [onClick] asks before it does. */
    class Offered(val onClick: () -> Unit) : ViewDelete

    /** A built-in view cannot go, so its page offers no Delete; it is switched off in the Settings list. */
    data object BuiltIn : ViewDelete
}

/**
 * The page every view's settings are on, in the view's [colors]: a [note], the [parts] the view
 * has as tabs and the chosen part in one scroll, above a bar that stays at the bottom and rises
 * above the keyboard. A page built on it brings its parts and its buttons, and keeps no scroll,
 * tab row, bar or question of its own.
 *
 * The parts are shown in the one order every view has them in, whichever order they are given in,
 * and a part a view lacks is simply not there. The page owns which one is [selected]; each keeps
 * its fields while another is looked at, by [pageKey], so no page ever hands them to another, and
 * only the one shown is composed, so only it exists for TalkBack. A setup names its steps through
 * [partLabel] and holds the ones ahead back through [partEnabled].
 *
 * Whichever way a part is chosen, by its tab or by Next, it opens at its top.
 *
 * A view being set up for the first time ([walksParts]) is walked part by part: until its last
 * part the bar offers Next in place of the page's [buttons], so Save is pressed once everything
 * was seen and not after the first part, before its reminders were ever shown. An existing view
 * is opened to change one thing, so there Save is under every part. A level of a part that makes
 * something, such as a new activity, puts its own action in the bar instead ([barButton]), so the
 * bar never offers to go on past what is half made.
 *
 * The bar is a [MomentumActionBar]: Delete where [delete] offers it, then the page's [buttons],
 * each a [MomentumBarAction]. It says what went wrong ([error]) while it lasts, and what the page
 * still waits for ([hint]) when a button that is not ready is pressed.
 *
 * The page says whether it holds [unsavedWork]; the frame tells whoever owns the navigation
 * through [onUnsavedWorkChange] and, asked to leave through [leaveRequested], asks the person
 * [discard] and answers through [onLeaveAnswered], so the arrow, the system's back and a
 * reminder's destination all meet the same question. Dialogs the page opens itself, such as a
 * time picker, go into [dialogs], where they wear the view's colors.
 *
 * A part that shows one of several levels of its own, as Activity's Activities does, hands the
 * level it shows in [scrollKey]: when it changes the page scrolls back to its top, under the tab
 * row and over the bar, which both stay where they are.
 *
 * Tags follow from [testTag], the page's own: the note is `<testTag>_note`, the question
 * `<testTag>_discard_dialog`, Delete `<testTag>_delete`, the walk's Next `<testTag>_next`.
 * A tab's comes from [partTestTag], since a view may call a part by a word of its own.
 */
@Composable
internal fun ViewSettingsFrame(
    testTag: String,
    pageKey: String,
    colors: ViewColors,
    parts: List<ChecklistPage>,
    selected: ChecklistPage,
    onSelect: (ChecklistPage) -> Unit,
    partTestTag: (ChecklistPage) -> String,
    unsavedWork: Boolean,
    discard: DiscardQuestion,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    error: BarLine?,
    delete: ViewDelete?,
    buttons: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    hint: BarLine? = null,
    partLabel: @Composable (ChecklistPage) -> String = { stringResource(it.label) },
    partEnabled: (ChecklistPage) -> Boolean = { true },
    dialogs: @Composable () -> Unit = {},
    scrollKey: Any? = null,
    walksParts: Boolean = LocalSettingUp.current,
    barButton: BarButton? = null,
    part: @Composable (ChecklistPage) -> Unit,
) {
    val scroll = rememberScrollState()
    val shownParts = ChecklistPage.entries.filter { it in parts }
    val shownPart by rememberUpdatedState(selected)
    // A part chosen opens at its top; the part a page, or a restored page, starts on keeps its scroll.
    LaunchedEffect(scroll) { snapshotFlow { shownPart }.drop(1).collect { scroll.scrollTo(0) } }
    val partStates = rememberSaveableStateHolder()
    val shownKey by rememberUpdatedState(scrollKey)
    // Only a change scrolls: the first key, and one a restored page starts on, leave the scroll as it was.
    LaunchedEffect(scroll) { snapshotFlow { shownKey }.drop(1).collect { scroll.scrollTo(0) } }
    ReportUnsavedWork(unsavedWork, onUnsavedWorkChange)

    ViewTheme(colors) {
        if (leaveRequested && unsavedWork) {
            DiscardDialog(
                question = discard,
                testTag = "${testTag}_discard_dialog",
                onDismiss = { onLeaveAnswered(false) },
                onConfirm = { onLeaveAnswered(true) },
            )
        }
        dialogs()
        MomentumPopOutGroup {
            Column(modifier = modifier.testTag(testTag)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (note != null) NoteText(text = note, testTag = "${testTag}_note")

                    MomentumTabs(
                        tabs = shownParts,
                        selected = selected,
                        onSelect = onSelect,
                        label = partLabel,
                        testTag = partTestTag,
                        enabled = partEnabled,
                    )

                    partStates.SaveableStateProvider("$pageKey/${selected.name}") { part(selected) }
                }

                MomentumActionBar(
                    error = error,
                    // What Save waits for is beside the point while the bar holds a level's own action.
                    waitingFor = hint.takeIf { barButton == null },
                    leading = { if (delete is ViewDelete.Offered) DeleteButton(delete, testTag = "${testTag}_delete") },
                ) {
                    val after = shownParts.getOrNull(shownParts.indexOf(selected) + 1)
                    if (barButton != null) {
                        MomentumBarAction(
                            text = barButton.text,
                            onClick = barButton.onClick,
                            ready = barButton.enabled,
                            testTag = barButton.testTag,
                        )
                    } else if (walksParts && after != null) {
                        MomentumBarAction(
                            text = stringResource(R.string.checklist_setup_next),
                            onClick = { onSelect(after) },
                            ready = partEnabled(after),
                            testTag = "${testTag}_next",
                        )
                    } else {
                        buttons()
                    }
                }
            }
        }
    }
}

/**
 * Whether anything at all is recorded yet. A view's page says that changes count from today only
 * then: with nothing recorded there are no earlier days for the old rules to keep.
 */
internal val LocalRecordsExist = staticCompositionLocalOf { true }

/** Whether the views opened now are being set up for the first time, as in the first start. */
internal val LocalSettingUp = staticCompositionLocalOf { false }

/** Delete is the rare way out, so it stays small and to the side, as Back does in setup. */
@Composable
private fun RowScope.DeleteButton(delete: ViewDelete.Offered, testTag: String) {
    MomentumButton(
        onClick = delete.onClick,
        modifier = Modifier.fillMaxHeight(),
        variant = MomentumButtonVariant.Secondary,
        colors = MomentumButtonDefaults.destructiveColors(MomentumButtonVariant.Secondary),
        testTag = testTag,
    ) {
        MomentumButtonLabel(stringResource(R.string.checklist_editor_delete))
    }
}

/** Guards work not yet saved on the way out: a page's changes, or the setup's whole checklist. */
@Composable
private fun DiscardDialog(question: DiscardQuestion, testTag: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MomentumDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(testTag),
        title = question.title,
        message = question.message,
        confirm = MomentumDialogAction(
            text = stringResource(R.string.checklist_editor_discard_confirm),
            onClick = onConfirm,
            destructive = true,
            testTag = "${testTag}_confirm",
        ),
        dismiss = MomentumDialogAction(
            text = stringResource(R.string.checklist_editor_discard_stay),
            onClick = onDismiss,
            testTag = "${testTag}_stay",
        ),
    )
}
