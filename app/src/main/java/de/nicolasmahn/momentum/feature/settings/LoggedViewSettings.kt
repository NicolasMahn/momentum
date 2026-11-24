package de.nicolasmahn.momentum.feature.settings

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.ui.MomentumBarAction
import de.nicolasmahn.momentum.core.ui.rememberBarExplanation
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumChoiceTile
import de.nicolasmahn.momentum.core.ui.MomentumChoiceTileRow
import de.nicolasmahn.momentum.core.ui.MomentumDialog
import de.nicolasmahn.momentum.core.ui.MomentumDialogAction
import de.nicolasmahn.momentum.core.ui.MomentumTextField
import de.nicolasmahn.momentum.domain.DailyView
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.ViewLook
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.on
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.feature.checklist.presentation
import de.nicolasmahn.momentum.reminder.TestReminder
import de.nicolasmahn.momentum.reminder.loggedTestReminder
import java.time.LocalDate
import java.time.LocalTime

/**
 * The changes a view's Look part makes, what the view is called and how it looks, which every
 * page's draft takes as the kind's own edit. Each answers whether the draft took it.
 */
internal interface ViewLookEdits {
    fun rename(label: String): Boolean

    fun setIcon(icon: ViewIcon): Boolean

    fun setHue(hue: ViewHue): Boolean
}

/**
 * The changes every logged view takes on its page, whichever kind it is: what the parts a
 * measurement, a questions view and a time view share ask of the page's draft, which makes each
 * the kind's own edit. Each answers whether the draft took it.
 */
internal interface LoggedViewEdits : ViewLookEdits {
    fun setRemind(remind: Boolean): Boolean

    fun setReminderTime(at: CheckpointTime): Boolean

    /** Counts toward the streak from today, or stops after yesterday. */
    fun takePart(takesPart: Boolean): Boolean

    fun setWhenAway(whenAway: WhenAway): Boolean
}

/**
 * What a view kept by logging its day takes beside them: the two numbers of the bank a made
 * checklist has, how many tokens it holds and how many kept days earn one. A time view earns by
 * its goal and has no such number.
 */
internal interface DailyViewEdits : LoggedViewEdits {
    fun setBankRules(maxPasses: Int, everyKeptDays: Int): Boolean
}

/**
 * The page of a view as a draft over the day it is shown on, a logged view's and Activity's: the
 * stored configuration as its baseline, and the ordered edits made on the page, as a checklist's
 * page keeps its edits ([SettingsDraft]). What the page shows is the edits applied to the latest
 * stored configuration on [today], and Save hands the edits over for the store to apply on the
 * habit day of that moment. So unsaved edits survive 04:00 and every dated change is dated by the
 * save's day.
 *
 * The page is dirty while what it shows differs from what is stored, never because it remembers
 * having been changed: once the edits are stored, by this page or behind its back, replaying them
 * changes nothing and nothing is left to save or to discard, also for a draft restored after the
 * process died. A name field left blank keeps the last name and holds Save back ([blankNames]).
 *
 * A kind brings how one of its edits is applied, and which of its edits each shared change is.
 */
internal abstract class ConfigurationDraft<C, E>(
    stored: C,
    today: LocalDate,
    edits: List<E>,
    blankNames: Set<String>,
    typedFields: Set<String> = emptySet(),
) : SettingsDraft<C, E>(stored, edits, blankNames, typedFields), ViewLookEdits {
    var today: LocalDate by mutableStateOf(today)
        private set

    /** This configuration with [edit] applied from [today]; throws for an edit it cannot take, as the store would. */
    protected abstract fun C.applying(edit: E, today: LocalDate): C

    /** This configuration with [edits] applied one after another from [today], one it does not take left out. */
    protected abstract fun C.applyingAll(edits: List<E>, today: LocalDate): C

    /** The configuration as the page shows it: the edits over the stored one, applied on today. */
    val edited: C get() = stored.applyingAll(edits, today)

    override val isDirty: Boolean get() = edited != stored

    /** Whether the draft takes [edit], which it refuses as the stored configuration would. */
    fun edit(edit: E): Boolean = keep(edit) { edited.applying(edit, today) }

    /** The stored configuration as it is now, and the day it is; the edits are shown over both from here on. */
    fun follow(current: C, today: LocalDate) {
        stored = current
        this.today = today
    }
}

/** The page of a logged view as a draft ([ConfigurationDraft]), a measurement's, a questions view's or a time view's, with the changes they share ([LoggedViewEdits]). */
internal abstract class LoggedViewDraft<C : LoggedView, E>(
    stored: C,
    today: LocalDate,
    edits: List<E>,
    blankNames: Set<String>,
) : ConfigurationDraft<C, E>(stored, today, edits, blankNames), LoggedViewEdits

/**
 * The part of its own a kind of logged view has, second on its page and in its setup, where a
 * checklist has its Items: a questions view's Questions, a measurement's Unit, a time view's Goal.
 * It is named by the kind's word ([label]), and by [tagName] inside a test tag. A kind whose
 * Reminders part holds more than its reminder names that one too ([remindersLabel], tagged
 * [remindersTagName]), as Activity's Week does; every other part keeps its own.
 */
internal class OwnPart(
    @param:StringRes private val label: Int,
    private val tagName: String,
    @param:StringRes private val remindersLabel: Int? = null,
    private val remindersTagName: String? = null,
) {
    @Composable
    fun nameOf(part: ChecklistPage): String = stringResource(
        when {
            part == ChecklistPage.Items -> label
            part == ChecklistPage.Reminders && remindersLabel != null -> remindersLabel
            else -> part.label
        },
    )

    fun tagNameOf(part: ChecklistPage): String = when {
        part == ChecklistPage.Items -> tagName
        part == ChecklistPage.Reminders && remindersTagName != null -> remindersTagName
        else -> part.tagName
    }
}

/** The four parts the page of a logged view has, and its setup walks as its steps: Look, the kind's own, Reminders and Streak. */
internal val LoggedViewParts = listOf(ChecklistPage.Look, ChecklistPage.Items, ChecklistPage.Reminders, ChecklistPage.Streak)

/**
 * The settings of a view over its draft, a logged view's and Activity's, on the page every view
 * has ([ViewSettingsFrame]) with the [parts] its kind has, all of them changing one [draft]. The
 * page wears the view as the draft is changing it, opens on [opensOn], and asks before its work
 * is lost under the name the view is stored by.
 *
 * What Save and Delete are is not the page's to say. A logged view stores its edits at once and
 * can be deleted ([MadeViewSettingsPage]); Activity, which is built in, saves over two stores and
 * stays ([BuiltInViewSettingsPage]). So the page is handed whether it holds [unsavedWork], whether it
 * [canSave], what Save does ([onSave]), what [delete] is and what went wrong ([error]).
 *
 * A kind brings the content of a [part]: it is handed the part, the configuration as the draft is
 * changing it, and Look's name field. What a part still waits for is the [hint] on the bar. A part
 * is called by its own word unless the kind has another ([partLabel], and [partTagName] in its
 * tab's tag). Dialogs a host opens go into [dialogs], handed that configuration too. A part with
 * levels of its own hands the one it shows in [scrollKey] ([ViewSettingsFrame]). Every tag
 * follows from [testTag].
 */
@Composable
internal fun <C : ViewLook, E> DraftSettingsPage(
    draft: ConfigurationDraft<C, E>,
    testTag: String,
    pageKey: String,
    parts: List<ChecklistPage>,
    opensOn: ChecklistPage,
    unsavedWork: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    delete: ViewDelete,
    error: BarLine?,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: BarLine? = null,
    partLabel: @Composable (ChecklistPage) -> String = { stringResource(it.label) },
    partTagName: (ChecklistPage) -> String = { it.tagName },
    dialogs: @Composable (edited: C) -> Unit = {},
    scrollKey: Any? = null,
    barButton: (shown: ChecklistPage) -> BarButton? = { null },
    part: @Composable (shown: ChecklistPage, edited: C, nameField: @Composable () -> Unit) -> Unit,
) {
    val today = draft.today
    val edited = remember(draft.stored, draft.edits, today) { draft.edited }
    var shownPart by rememberSaveable(edited.id) { mutableStateOf(opensOn) }

    ViewSettingsFrame(
        testTag = testTag,
        pageKey = pageKey,
        colors = edited.presentation().colors,
        parts = parts,
        selected = shownPart,
        onSelect = { shownPart = it },
        partTestTag = { "${testTag}_tab_${partTagName(it)}" },
        unsavedWork = unsavedWork,
        discard = DiscardQuestion(
            title = stringResource(R.string.checklist_editor_discard_title),
            message = stringResource(R.string.checklist_editor_discard_message, draft.stored.label),
        ),
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        error = error,
        delete = delete,
        buttons = {
            // Saved, yet the page stays while typed text waits to be added; it says so.
            val explain = rememberBarExplanation()
            MomentumBarAction(
                text = stringResource(R.string.settings_feature_save),
                onClick = {
                    onSave()
                    if (draft.hasUnsavedWork) explain()
                },
                ready = canSave,
                testTag = "${testTag}_save",
            )
        },
        modifier = modifier,
        note = stringResource(R.string.checklist_editor_dated_note).takeIf { LocalRecordsExist.current },
        hint = hint,
        partLabel = partLabel,
        dialogs = { dialogs(edited) },
        scrollKey = scrollKey,
        barButton = barButton(shownPart),
    ) { shown ->
        part(shown, edited) {
            NameField(
                name = edited.label,
                key = edited.id,
                label = stringResource(R.string.checklist_editor_name),
                testTag = "${testTag}_name",
                onName = { name ->
                    draft.named(edited.id, name)
                    name?.let(draft::rename)
                },
                settled = draft.nameStored(edited.id),
            )
        }
    }
}

/**
 * How a logged view's page deletes it: the dialog names it and its [recordedDays] in the kind's
 * words ([message]), [onDelete] answers whether it happened, and the bar says so where it did not
 * ([failed]).
 */
internal class LoggedViewDeletion(
    val recordedDays: Int,
    @param:PluralsRes val message: Int,
    @param:StringRes val failed: Int,
    val onDelete: () -> Boolean,
)

/**
 * The shared settings page for a configured logged tracker.
 * Save stores the [draft]'s edits through [onSave], in the tracker state alone, its reminder
 * among them; saved, the page returns to the list through [onClose]. Where the edits are stored
 * but the alarm they ask for could not be set, the page stays, shows what is stored and says so.
 * Where the reminder they switch on waits for the phone, the rest is stored and the page holds
 * that switch alone until the host has the answer.
 *
 * A kind's [part] is handed what opens the picker of the reminder's fixed time as well.
 *
 * The view can be deleted, with every record of it, once the dialog [deletion] describes is
 * confirmed, and the page is closed by whoever opened it. What went wrong, here or in Settings
 * ([message]), is on the bar.
 */
@Composable
internal fun <C : LoggedView, E> MadeViewSettingsPage(
    view: C,
    draft: LoggedViewDraft<C, E>,
    testTag: String,
    pageKey: String,
    parts: List<ChecklistPage>,
    opensOn: ChecklistPage,
    deletion: LoggedViewDeletion,
    onSave: (List<E>) -> ViewWrite,
    onClose: () -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    hint: BarLine? = null,
    partLabel: @Composable (ChecklistPage) -> String = { stringResource(it.label) },
    partTagName: (ChecklistPage) -> String = { it.tagName },
    part: @Composable (shown: ChecklistPage, edited: C, onPickTime: () -> Unit, nameField: @Composable () -> Unit) -> Unit,
) {
    var error by remember(view.id) { mutableStateOf<Int?>(null) }
    var confirmingDelete by rememberSaveable(view.id) { mutableStateOf(false) }
    var pickingTime by rememberSaveable(view.id) { mutableStateOf(false) }

    DraftSettingsPage(
        draft = draft,
        testTag = testTag,
        pageKey = pageKey,
        parts = parts,
        opensOn = opensOn,
        unsavedWork = draft.hasUnsavedWork,
        canSave = draft.canSave,
        onSave = {
            when (onSave(draft.edits)) {
                ViewWrite.Stored -> {
                    draft.saved()
                    error = null
                    if (!draft.hasUnsavedWork) onClose()
                }
                ViewWrite.NotStored -> error = R.string.settings_error_view
                // The edits are stored, so nothing is left to save; only the alarm is missing.
                ViewWrite.StoredWithoutAlarm -> {
                    draft.saved()
                    error = R.string.measurement_settings_alarm_not_set
                }
                // The page holds the switch alone until the phone answers, and after a refusal.
                ViewWrite.ReminderWaits -> {
                    draft.saved()
                    draft.setRemind(true)
                    error = null
                }
            }
        },
        delete = ViewDelete.Offered { confirmingDelete = true },
        error = (error?.let { stringResource(it) } ?: message)?.let { BarLine(it, "${testTag}_error") },
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        modifier = modifier,
        hint = hint,
        partLabel = partLabel,
        partTagName = partTagName,
        dialogs = { edited ->
            if (pickingTime) LoggedViewReminderTimePicker(edited, draft, onDismiss = { pickingTime = false })
            if (confirmingDelete) {
                LoggedViewDeleteDialog(
                    view = view,
                    recordedDays = deletion.recordedDays,
                    message = deletion.message,
                    testTag = testTag,
                    onDismiss = { confirmingDelete = false },
                    onConfirm = {
                        confirmingDelete = false
                        if (!deletion.onDelete()) error = deletion.failed
                    },
                )
            }
        },
    ) { shown, edited, nameField -> part(shown, edited, { pickingTime = true }, nameField) }
}

/**
 * What a view is called and how it looks, on its setup and on its page alike, over [view] as
 * [edits] change it: [fields], its name, then its icon, from [icons] in their order, and its
 * color, and at the [end] what a kind's look has beside them. Tags follow from [testTag].
 */
@Composable
internal fun LoggedViewLook(
    view: ViewLook,
    edits: ViewLookEdits,
    testTag: String,
    icons: List<ViewIcon> = ViewIcon.entries,
    end: @Composable ColumnScope.() -> Unit = {},
    fields: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        fields()
        IconPicker(selected = view.icon, testTag = testTag, icons = icons) { edits.setIcon(it) }
        HuePicker(selected = view.hue, testTag = testTag) { edits.setHue(it) }
        end()
    }
}

/**
 * The one reminder of a logged view a person made, over [view] as its draft is changing it, as a
 * checkpoint's is on a checklist's page: when it comes, in the three forms a checkpoint's time
 * has, with what a relative time comes to on [bedtime]'s day; its switch, which belongs to
 * Settings with the permissions it may need ([reminderRow]); and the notification it sends, read
 * from the draft as if nothing were logged yet, the day today asks about or, for a time view, its
 * week, which [onSendTest] sends now, with the switch off too. Where today asks about no day, or
 * about an away day the draft is not due on, there is nothing to preview or to send; without a
 * sender, as while the view is hidden, no test is offered.
 *
 * Tags follow from [testTag]: the switch is `<testTag>_reminder`, which [reminderRow] is handed,
 * and every other control here starts with that.
 */
@Composable
internal fun LoggedViewReminder(
    view: LoggedView,
    edits: LoggedViewEdits,
    today: LocalDate,
    bedtime: Bedtime,
    daysOff: HolidaySchedule,
    testTag: String,
    reminderRow: @Composable (LoggedView, String, (Boolean) -> Unit) -> Unit,
    onSendTest: ((TestReminder.AtCheckpoint) -> Unit)?,
    onPickTime: () -> Unit,
) {
    val checkpoint = view.checkpoint ?: return
    val tag = "${testTag}_reminder"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeEditor(
            at = checkpoint.at,
            today = checkpoint.at.on(bedtime),
            testTag = tag,
            onChange = edits::setReminderTime,
            onPickTime = onPickTime,
        )
        reminderRow(view, tag) { remind -> edits.setRemind(remind) }
        val test = loggedTestReminder(view, today, daysOff)
        ReminderPreview(
            message = test?.words(mood = null),
            sent = checkpoint.remind,
            onSendTest = if (test != null && onSendTest != null) ({ onSendTest(test) }) else null,
            testTag = tag,
            onlyMisses = false,
        )
    }
}

/** Picks the fixed time of [view]'s reminder, starting on the one it has. */
@Composable
internal fun LoggedViewReminderTimePicker(view: LoggedView, edits: LoggedViewEdits, onDismiss: () -> Unit) {
    val at = view.checkpoint.at as? CheckpointTime.Fixed ?: return
    SettingsTimePickerDialog(
        initialTime = ReminderTime(at.time.hour, at.time.minute),
        onDismiss = onDismiss,
        onConfirm = { selected ->
            onDismiss()
            edits.setReminderTime(CheckpointTime.Fixed(LocalTime.of(selected.hour, selected.minute)))
        },
    )
}

/**
 * How a logged view stands to the streak, over [view] as its draft is changing it: [bank], what
 * its kind has to say of counting and of its bank, each group it emits spaced as the holiday
 * group is, and last what a holiday means for the view, which is there whether or not it counts,
 * because its reminder reads it too. A holiday answer is tagged `<testTag>_away_<rule>`.
 */
@Composable
internal fun LoggedViewStreak(
    view: LoggedView,
    edits: LoggedViewEdits,
    today: LocalDate,
    testTag: String,
    bank: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        bank()
        EditorGroup(stringResource(R.string.checklist_editor_away)) {
            // Still due first, as it is until the person says otherwise.
            AwayRuleTiles(view.whenAway.on(today), listOf(WhenAway.Due, WhenAway.Excused), testTag) { edits.setWhenAway(it) }
        }
    }
}

/**
 * The Streak part of a view kept by logging its day ([LoggedViewStreak]): whether it counts, with
 * the switch and the bank's two steppers a made checklist's page has ([RewardsEditor]). [rewards]
 * supplies the configured bank independently of Mo enrollment, and [earnNote] what the steppers
 * leave unsaid of how it earns. Tags follow from [testTag]: the switch is `<testTag>_rewards`.
 */
@Composable
internal fun DailyViewStreak(
    view: DailyView,
    edits: DailyViewEdits,
    today: LocalDate,
    testTag: String,
    rewards: ChecklistRewards? = view.standardRewards,
    earnNote: String? = null,
) {
    LoggedViewStreak(view, edits, today, testTag) {
        RewardsEditor(
            rewards = rewards,
            today = today,
            testTag = testTag,
            onTakePart = { takePart -> edits.takePart(takePart) },
            onRules = edits::setBankRules,
            earnNote = earnNote,
        )
    }
}

/**
 * What a holiday means for a logged view, as the two answers it has, in the order of [options]:
 * such a view has no count a week to lower. A tile is tagged `<testTag>_away_<rule>`.
 */
@Composable
internal fun AwayRuleTiles(whenAway: WhenAway, options: List<WhenAway>, testTag: String, onChoose: (WhenAway) -> Unit) {
    MomentumChoiceTileRow {
        options.forEach { option ->
            MomentumChoiceTile(
                label = stringResource(if (option == WhenAway.Excused) R.string.checklist_editor_away_excused else R.string.checklist_editor_away_due),
                selected = whenAway == option,
                onClick = { if (whenAway != option) onChoose(option) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTag = "${testTag}_away_${option.storageId}",
            )
        }
    }
}

/**
 * Names the view and the days that go with it, in the kind's words ([message], a plural of the
 * days), so the one irreversible edit here is taken knowingly.
 */
@Composable
private fun LoggedViewDeleteDialog(view: LoggedView, recordedDays: Int, @PluralsRes message: Int, testTag: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MomentumDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("${testTag}_delete_dialog"),
        title = stringResource(R.string.measurement_settings_delete_title, view.label),
        message = if (recordedDays == 0) {
            stringResource(R.string.measurement_settings_delete_message_none)
        } else {
            pluralStringResource(message, recordedDays, recordedDays)
        },
        confirm = MomentumDialogAction(
            text = stringResource(R.string.checklist_editor_delete_confirm),
            onClick = onConfirm,
            destructive = true,
            testTag = "${testTag}_delete_confirm",
        ),
        dismiss = MomentumDialogAction(
            text = stringResource(R.string.settings_cancel),
            onClick = onDismiss,
            testTag = "${testTag}_delete_cancel",
        ),
    )
}

/**
 * One typed field of a kind's own part, taken into the draft through [onType] as it is typed: a
 * measurement's unit and step, a time view's goal and the percent of a step. Text the draft
 * refuses stays in the field, which says why in its label's place ([error]), until it is changed.
 * The field keeps what was typed by [key], as a name field does, and shows the latest [text]
 * while nothing typed waits for Save ([settled]). A [suffix] stands after the text inside the
 * field, the unit of the number typed, and the number is set against it ([textAlign]).
 */
@Composable
internal fun TypedPartField(
    text: String,
    key: String,
    label: String,
    error: String?,
    settled: Boolean,
    testTag: String,
    onType: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    val field = rememberSaveable(key, saver = NameFieldText.Saver) { NameFieldText(text) }
    field.follow(text, settled)
    MomentumTextField(
        value = field.text,
        onValueChange = { typed ->
            field.type(typed)
            onType(typed)
        },
        label = label,
        error = error,
        modifier = modifier.fillMaxWidth(),
        testTag = testTag,
        suffix = suffix,
        textAlign = textAlign,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
    )
}
