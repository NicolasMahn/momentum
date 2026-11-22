package com.example.workouttracker.feature.settings

import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.locale.currentLocale
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.time.calendarWeekdayShortLabel
import com.example.workouttracker.core.ui.MomentumBarAction
import com.example.workouttracker.core.ui.rememberBarExplanation
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumCard
import com.example.workouttracker.core.ui.MomentumCheckboxRow
import com.example.workouttracker.core.ui.MomentumChoiceTile
import com.example.workouttracker.core.ui.MomentumChoiceTileDefaults
import com.example.workouttracker.core.ui.MomentumChoiceTileRow
import com.example.workouttracker.core.ui.MomentumDialog
import com.example.workouttracker.core.ui.MomentumDialogAction
import com.example.workouttracker.core.ui.MomentumDisclosure
import com.example.workouttracker.core.ui.MomentumPopOut
import com.example.workouttracker.core.ui.MomentumRemoveButton
import com.example.workouttracker.core.ui.MomentumTileGrid
import com.example.workouttracker.core.ui.MomentumTextField
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.core.ui.viewColors
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistProgress
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.applyEdit
import com.example.workouttracker.domain.checklist.applyingEdits
import com.example.workouttracker.domain.checklist.canAskFewerTimes
import com.example.workouttracker.domain.checklist.canBeExceptionsOnly
import com.example.workouttracker.domain.checklist.canRemoveCheckpoint
import com.example.workouttracker.domain.checklist.isSchedule
import com.example.workouttracker.domain.checklist.checkpointIds
import com.example.workouttracker.domain.checklist.freshId
import com.example.workouttracker.domain.checklist.emptyCheckpoints
import com.example.workouttracker.domain.checklist.isInUse
import com.example.workouttracker.domain.checklist.itemIds
import com.example.workouttracker.domain.checklist.liveCheckpoints
import com.example.workouttracker.domain.checklist.liveItems
import com.example.workouttracker.domain.checklist.on
import com.example.workouttracker.domain.checklist.timesAWeek
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.feature.checklist.momentumHue
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.feature.checklist.vector
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import com.example.workouttracker.reminder.MoReminderMessage
import com.example.workouttracker.reminder.TestReminder
import com.example.workouttracker.reminder.checklistTestReminder
import java.time.DayOfWeek
import com.example.workouttracker.domain.checklist.ChecklistRewards
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * The parts a view's settings page can have, in the one order every page shows them in: what the
 * view is called and how it looks, what is on it, when it reminds, and how it counts. A view shows
 * the parts it has; a checklist has all four, and its setup walks them as its steps.
 */
internal enum class ChecklistPage(@StringRes val label: Int) {
    Look(R.string.checklist_editor_tab_look),
    Items(R.string.checklist_editor_items),
    Reminders(R.string.checklist_editor_tab_reminders),
    Streak(R.string.checklist_editor_rewards);

    /** The part's name inside a test tag, which is never translated. */
    val tagName: String get() = name.lowercase(Locale.ROOT)
}

/**
 * A checklist's page as a draft: the stored configuration as its baseline, and the ordered edits
 * made on the page ([SettingsDraft]). What the page shows is the edits applied to the latest
 * stored configuration, so a change stored meanwhile, such as a renamed label, stays under them;
 * Save hands the edits over, and the store applies them again on the habit day of that moment. An
 * edit the latest configuration refuses, such as a rename of an item removed meanwhile, is left
 * out of both. The page is dirty while it holds an edit, and its edits are applied with the day
 * and the first recorded day, which leaving rewards needs.
 *
 * The page's fields belong to it: a name field left blank keeps the last name in the draft and
 * holds Save back ([blankNames]), and text typed into "New item" but not added is work not to be
 * lost ([pendingText]). It is kept across process death like the rest of the page.
 */
internal class ChecklistDraft(
    stored: ChecklistConfiguration,
    edits: List<ChecklistEdit> = emptyList(),
    blankNames: Set<String> = emptySet(),
    pendingText: Set<String> = emptySet(),
) : SettingsDraft<ChecklistConfiguration, ChecklistEdit>(stored, edits, blankNames, pendingText) {
    /** The fields holding text not yet added to the checklist, by field. */
    val pendingText: Set<String> get() = typedFields

    override val isDirty: Boolean get() = edits.isNotEmpty()

    /** A name is the checklist's own, or one of its items' or checkpoints'. */
    override fun ChecklistEdit.names(key: String): Boolean = when (this) {
        is ChecklistEdit.Rename -> key == stored.id
        is ChecklistEdit.AddItem -> key == itemId
        is ChecklistEdit.RenameItem -> key == itemId
        is ChecklistEdit.AddCheckpoint -> key == checkpointId
        is ChecklistEdit.RenameCheckpoint -> key == checkpointId
        else -> false
    }

    override fun ChecklistEdit.removes(): String? = (this as? ChecklistEdit.RemoveItem)?.itemId

    fun draft(today: LocalDate, recordedDates: Set<LocalDate>): ChecklistConfiguration =
        stored.applyingEdits(edits, today, recordedDates)

    /**
     * Whether the draft takes [edit], which it refuses as the stored configuration would. A rhythm
     * chosen right after another replaces it: choosing Weekly and then 5 is one change, so a holiday
     * rule or only misses that Weekly 1 cannot hold is not lost on the way, and back where it
     * started leaves nothing to save.
     */
    fun edit(edit: ChecklistEdit, today: LocalDate, recordedDates: Set<LocalDate>): Boolean =
        if (edit is ChecklistEdit.SetTarget && edits.lastOrNull() is ChecklistEdit.SetTarget) {
            val before = stored.applyingEdits(edits.dropLast(1), today, recordedDates)
            keepInstead(edit, undoes = edit.target == before.target) { before.applyEdit(edit, today, recordedDates) }
        } else {
            keep(edit) { draft(today, recordedDates).applyEdit(edit, today, recordedDates) }
        }

    /** The stored configuration as it is now; the edits are shown over it from here on. */
    fun follow(current: ChecklistConfiguration) {
        stored = current
    }

    companion object {
        val Saver: Saver<ChecklistDraft, Any> = listSaver(
            save = {
                listOf(
                    AppStateJsonCodec.encodeChecklist(it.stored),
                    AppStateJsonCodec.encodeChecklistEdits(it.edits),
                    ArrayList(it.blankNames),
                    ArrayList(it.pendingText),
                )
            },
            restore = {
                ChecklistDraft(
                    stored = AppStateJsonCodec.decodeChecklist(it[0] as String),
                    edits = AppStateJsonCodec.decodeChecklistEdits(it[1] as String),
                    blankNames = (it[2] as List<*>).mapTo(LinkedHashSet()) { key -> key as String },
                    pendingText = (it[3] as List<*>).mapTo(LinkedHashSet()) { key -> key as String },
                )
            },
        )
    }
}

/**
 * Checklist settings are changed as a draft and stored
 * by [onSave] at the bottom of every page, which is handed the edits made. What is due changes from
 * the day they are saved, as the note at the top says; earlier days keep the rules they had.
 *
 * Whoever owns the navigation is told through [onUnsavedWorkChange] whether the page holds work
 * that would be lost, and asks to leave through [leaveRequested]; the page asks the person and
 * answers through [onLeaveAnswered], so the arrow, the system's back and a reminder's destination
 * all meet the same question.
 *
 * A checkpoint's reminder row belongs to Settings, which owns the permissions a reminder may need,
 * so it comes in as [reminderRow] with the switch's answer to hand back, and so does sending a
 * reminder now as a test, through [onSendTest], which is null while the checklist is hidden on
 * Home and no reminder of it can come. [bedtime] is the night a
 * checkpoint after waking or before sleep is read against for today. [mood] supplies the configured
 * reminder's tone, and [progress] supplies the draft's standing today, which says what
 * each reminder would name. [recordedDates] retains every factual date so each draft can resolve its own first
 * judged day when leaving rewards; [recordedDays] is named before a delete.
 *
 * Saved, the page has done its work and returns to the list through [onClose]. Only text typed
 * into "New item" or "New checkpoint" and not yet added keeps it open, with a line saying so, as
 * leaving would lose it.
 *
 * A checklist can be deleted with its records once the dialog
 * confirmed it: that is irreversible and bulk, the one kind of edit here that may interrupt.
 * [onDelete] answers whether it happened; the page is closed by whoever opened it.
 *
 * The page is a [ViewSettingsFrame] with all four parts; the line over Save and Delete says what
 * went wrong, here or in Settings ([message]).
 */
@Composable
internal fun ChecklistEditor(
    checklist: ChecklistConfiguration,
    today: LocalDate,
    bedtime: Bedtime,
    mood: MoMood?,
    progress: (ChecklistConfiguration) -> ChecklistProgress,
    recordedDays: Int,
    recordedDates: Set<LocalDate>,
    onSave: (List<ChecklistEdit>) -> Boolean,
    reminderRow: @Composable (ChecklistConfiguration, Checkpoint, (Boolean) -> Unit) -> Unit,
    onSendTest: ((TestReminder.AtCheckpoint) -> Unit)?,
    onDelete: () -> Boolean,
    onClose: () -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val state = rememberSaveable(checklist.id, saver = ChecklistDraft.Saver) { ChecklistDraft(checklist) }
    state.follow(checklist)
    val draft = remember(state.stored, state.edits, today, recordedDates) { state.draft(today, recordedDates) }
    var error by remember(checklist.id) { mutableStateOf<Int?>(null) }
    fun edit(change: ChecklistEdit): Boolean =
        state.edit(change, today, recordedDates).also { error = if (it) null else R.string.checklist_error_save_failed }

    // A checklist without an item is of no use until it has one, so its page opens where that is added.
    var page by rememberSaveable(checklist.id) { mutableStateOf(if (checklist.isInUse(today)) ChecklistPage.Look else ChecklistPage.Items) }
    var confirmingDelete by rememberSaveable(checklist.id) { mutableStateOf(false) }
    val typedText = stringResource(R.string.settings_typed_text_pending)
    // A part added here starts empty, and a time of the day with nothing to tick must not be saved.
    val emptyCheckpoint = draft.emptyCheckpoints(today).isNotEmpty()

    ViewSettingsFrame(
        testTag = "checklist_editor",
        pageKey = checklist.id,
        colors = draft.presentation().colors,
        parts = ChecklistPage.entries,
        selected = page,
        onSelect = { page = it },
        partTestTag = { "checklist_editor_tab_${it.tagName}" },
        unsavedWork = state.hasUnsavedWork,
        discard = DiscardQuestion(
            title = stringResource(R.string.checklist_editor_discard_title),
            message = stringResource(R.string.checklist_editor_discard_message, checklist.label),
        ),
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        error = (error?.let { stringResource(it) } ?: message)?.let { BarLine(it, "checklist_editor_error") },
        // Under whatever part is open: what was changed is saved, or the whole checklist goes.
        delete = ViewDelete.Offered { confirmingDelete = true },
        buttons = {
            // Saved, yet the page stays while typed text waits to be added; it says so.
            val explain = rememberBarExplanation()
            MomentumBarAction(
                text = stringResource(if (draft.isSchedule(today)) R.string.checklist_schedule_save else R.string.checklist_editor_save),
                onClick = {
                    if (onSave(state.edits)) {
                        state.saved()
                        error = null
                        if (!state.hasUnsavedWork) onClose() else explain()
                    } else {
                        error = R.string.checklist_error_save_failed
                    }
                },
                ready = state.canSave && !emptyCheckpoint,
                testTag = "checklist_editor_save",
            )
        },
        modifier = modifier,
        note = stringResource(R.string.checklist_editor_dated_note).takeIf { LocalRecordsExist.current },
        hint = when {
            emptyCheckpoint -> BarLine(stringResource(R.string.checklist_editor_checkpoint_items_needed), "checklist_editor_hint")
            state.holdsOnlyTypedText -> BarLine(typedText, "checklist_editor_hint")
            else -> null
        },
        dialogs = {
            if (confirmingDelete) {
                DeleteDialog(
                    checklist = checklist,
                    recordedDays = recordedDays,
                    onDismiss = { confirmingDelete = false },
                    onConfirm = {
                        confirmingDelete = false
                        if (!onDelete()) error = R.string.checklist_editor_delete_failed
                    },
                )
            }
        },
    ) { part ->
        ChecklistPageContent(
            page = part,
            checklist = draft,
            today = today,
            bedtime = bedtime,
            mood = mood,
            progress = progress(draft),
            form = state,
            onEdit = ::edit,
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            nameField = {
                NameField(
                    name = draft.label,
                    key = draft.id,
                    label = stringResource(R.string.checklist_editor_name),
                    testTag = "checklist_editor_name",
                    onName = { name ->
                        state.named(draft.id, name)
                        name?.let { edit(ChecklistEdit.Rename(it)) }
                    },
                    settled = state.nameStored(draft.id),
                )
            },
        )
    }
}

/**
 * One part of a checklist's page, the editor's and the setup's alike, over the [checklist] as it is
 * being changed. Its fields tell [form] what they hold that is not in the checklist yet. [nameField] is
 * the Look page's name, which the editor and the setup keep differently. [onSendTest] sends a
 * checkpoint's reminder now, as the Reminders page previews it; null while none can be sent.
 */
@Composable
internal fun ChecklistPageContent(
    page: ChecklistPage,
    checklist: ChecklistConfiguration,
    today: LocalDate,
    bedtime: Bedtime,
    mood: MoMood?,
    progress: ChecklistProgress,
    form: ChecklistDraft,
    onEdit: (ChecklistEdit) -> Boolean,
    reminderRow: @Composable (ChecklistConfiguration, Checkpoint, (Boolean) -> Unit) -> Unit,
    onSendTest: ((TestReminder.AtCheckpoint) -> Unit)?,
    nameField: @Composable () -> Unit,
) {
    val checkpoints = checklist.liveCheckpoints(today)
    // The checkpoint whose fixed time is being picked, if one is.
    var pickingTimeOf by rememberSaveable(checklist.id) { mutableStateOf<String?>(null) }
    val picking = checkpoints.firstOrNull { it.id == pickingTimeOf }
    (picking?.at as? CheckpointTime.Fixed)?.let { at ->
        SettingsTimePickerDialog(
            initialTime = ReminderTime(at.time.hour, at.time.minute),
            onDismiss = { pickingTimeOf = null },
            onConfirm = { selected ->
                pickingTimeOf = null
                onEdit(ChecklistEdit.SetCheckpointTime(picking.id, CheckpointTime.Fixed(LocalTime.of(selected.hour, selected.minute))))
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        when (page) {
            ChecklistPage.Look -> {
                nameField()
                IconPicker(
                    selected = checklist.icon,
                    testTag = "checklist_editor",
                    icons = if (checklist.isSchedule(today)) ScheduleIcons else ChecklistIcons,
                ) { onEdit(ChecklistEdit.SetIcon(it)) }
                HuePicker(selected = checklist.hue, testTag = "checklist_editor") { onEdit(ChecklistEdit.SetHue(it)) }
                MoFlavorPicker(
                    selected = checklist.motif,
                    motifs = MoMotif.entries,
                    testTag = "checklist_editor",
                    watched = checklist.mo.enabled || checklist.rewards?.takesPartOn(today) == true,
                ) { onEdit(ChecklistEdit.SetMotif(it)) }
            }

            ChecklistPage.Items -> {
                if (!checklist.isSchedule(today)) RhythmEditor(checklist.target) { onEdit(ChecklistEdit.SetTarget(it)) }
                AboutEditor(checklist.about.on(today), testTag = "checklist_editor", label = { it.labelRes }, note = { it.noteRes }) { onEdit(ChecklistEdit.SetAbout(it)) }
                val items = checklist.liveItems(today)
                @Composable
                fun ItemsOf(shown: List<ChecklistItem>) = shown.forEach { item ->
                    ItemEditor(
                        item = item,
                        checklistTarget = checklist.target,
                        followsChecklist = item.target == null && !checklist.isSchedule(today),
                        checkpoints = checkpoints,
                        today = today,
                        // The last item stays, and in a schedule the last of a part: there is always something to tick.
                        removable = shown.size > 1,
                        form = form,
                        onEdit = onEdit,
                    )
                }
                if (!checklist.isSchedule(today)) {
                    // A checklist is ticked once a day: its items, and nothing about times.
                    ItemsOf(items)
                    AddField(
                        label = stringResource(R.string.checklist_editor_add_item),
                        testTag = AddItemField,
                        form = form,
                        // The only checkpoint holds every item there is, which is said by naming none.
                        onAdd = { label -> onEdit(ChecklistEdit.AddItem(freshId("itm", checklist.itemIds), label)) },
                    )
                } else {
                    // A schedule's checkpoints are its sections, each with its own items, ticked and
                    // reminded at its own time. An item saved at several before each held its own
                    // stands under the first.
                    checkpoints.forEachIndexed { index, checkpoint ->
                        // A hairline where a part begins: the parts are sections, and nothing else marks the turn.
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CheckpointGroup(
                            checklist = checklist,
                            checkpoint = checkpoint,
                            today = today,
                            settled = form.nameStored(checkpoint.id),
                            onEdit = onEdit,
                        ) {
                            ItemsOf(items.filter { item -> (checkpoints.firstOrNull { item.checkpoints?.contains(it.id) ?: true } ?: checkpoints.first()).id == checkpoint.id })
                            AddField(
                                label = stringResource(R.string.checklist_editor_add_item),
                                testTag = "${AddItemField}_${checkpoint.id}",
                                form = form,
                                onAdd = { label -> onEdit(ChecklistEdit.AddItem(freshId("itm", checklist.itemIds), label, checkpoints = listOf(checkpoint.id))) },
                            )
                        }
                    }
                    // A new part is a section of its own, so it is added the way one begins: after the
                    // hairline, by a button and not the field each part has for its items.
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MomentumButton(
                        text = stringResource(R.string.checklist_editor_add_checkpoint),
                        onClick = { onEdit(ChecklistEdit.AddCheckpoint(freshId("cpt", checklist.checkpointIds), label = null, NewCheckpointTime, remind = false)) },
                        modifier = Modifier.fillMaxWidth(),
                        variant = MomentumButtonVariant.Secondary,
                        icon = Icons.Rounded.Add,
                        testTag = AddCheckpointButton,
                    )
                }
            }

            ChecklistPage.Reminders -> {
                checkpoints.forEach { checkpoint ->
                    CheckpointEditor(
                        checklist = checklist,
                        checkpoint = checkpoint,
                        several = checkpoints.size > 1,
                        today = today,
                        bedtime = bedtime,
                        mood = mood,
                        progress = progress,
                        onEdit = onEdit,
                        reminderRow = { reminderRow(checklist, checkpoint) { remind -> onEdit(ChecklistEdit.SetCheckpointRemind(checkpoint.id, remind)) } },
                        onSendTest = onSendTest,
                        onPickTime = { pickingTimeOf = checkpoint.id },
                    )
                }
            }

            ChecklistPage.Streak -> {
                RewardsEditor(
                    rewards = checklist.rewards,
                    today = today,
                    testTag = "checklist_editor",
                    onTakePart = { takePart -> onEdit(if (takePart) ChecklistEdit.JoinRewards else ChecklistEdit.LeaveRewards) },
                    onRules = { maxPasses, everyKeptDays -> onEdit(ChecklistEdit.SetRewardRules(maxPasses, everyKeptDays)) },
                    // A day ticked at several times costs a token for each time missed, which the
                    // bank's size alone does not say: a bank of one cannot cover a whole day.
                    covers = checklist.checkpoints.size.takeIf { it > 1 }?.let { stringResource(R.string.checklist_editor_token_covers_checkpoint, it) },
                )
                // Both groups are read from the draft each time, so an item added or changed on
                // the Items page is here as it is now.
                val items = checklist.liveItems(today)
                // What the page opened on, which Save compares against; an item added on the page has only itself.
                // Read against the stored rhythm, so a rhythm chosen on the page does not change what it opened on.
                val opened = form.stored.liveItems(today).associate { it.id to it.copy(target = it.target ?: form.stored.target) }
                val holidayChoices = items.associate { it.id to awayOptionsOf(it, opened[it.id] ?: it, checklist.target, today) }
                // Whether an item counts without an entry also decides what the day's screen and
                // the reminders ask, so the choice stands whether or not the checklist counts
                // toward the streak. It stands above the holiday choices because the two exclude
                // each other for an item.
                val canLogOnlyMisses = checklist.itemsThatCanLogOnlyMisses(today)
                ExceptionsOnlyGroup(
                    items = canLogOnlyMisses,
                    hasWeekItems = canLogOnlyMisses.size < items.size,
                    canAskFewerTimes = { WhenAway.LowerTarget in holidayChoices.getValue(it.id) },
                    today = today,
                    onEdit = onEdit,
                )
                // A holiday can reach any item in use, whether or not one is planned, so the
                // choice stands whenever there is an item to make it for.
                if (items.isNotEmpty()) {
                    EditorGroup(stringResource(R.string.checklist_editor_away)) {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            items.forEach { item ->
                                ItemAwayRuleEditor(item, opened[item.id] ?: item, holidayChoices.getValue(item.id), checklist.target, today, onEdit)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A new checkpoint checks at noon until its time is set. */
private val NewCheckpointTime = CheckpointTime.Fixed(LocalTime.NOON)

/** The fields something new is named in, by which the draft knows text not yet added. */
internal const val AddItemField = "checklist_editor_add_item"
internal const val AddCheckpointButton = "checklist_editor_add_checkpoint"

/** Names the checklist and what goes with it, so the one irreversible edit here is taken knowingly. */
@Composable
private fun DeleteDialog(checklist: ChecklistConfiguration, recordedDays: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MomentumDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("checklist_editor_delete_dialog"),
        title = stringResource(R.string.checklist_editor_delete_title, checklist.label),
        message = if (recordedDays == 0) {
            stringResource(R.string.checklist_editor_delete_message_none)
        } else {
            pluralStringResource(R.plurals.checklist_editor_delete_message, recordedDays, recordedDays)
        },
        confirm = MomentumDialogAction(
            text = stringResource(R.string.checklist_editor_delete_confirm),
            onClick = onConfirm,
            destructive = true,
            testTag = "checklist_editor_delete_confirm",
        ),
        dismiss = MomentumDialogAction(
            text = stringResource(R.string.settings_cancel),
            onClick = onDismiss,
            testTag = "checklist_editor_delete_cancel",
        ),
    )
}

/**
 * One part of a schedule on the Items page, a checkpoint with the items checked there: its name,
 * and the two-step remove, offered once there are three and held back while every item is here; then
 * the part's items with the field that adds one to it ([content]). Something done at two times is
 * an item in each.
 */
@Composable
private fun CheckpointGroup(
    checklist: ChecklistConfiguration,
    checkpoint: Checkpoint,
    today: LocalDate,
    settled: Boolean,
    onEdit: (ChecklistEdit) -> Boolean,
    content: @Composable () -> Unit,
) {
    val name = checkpoint.label ?: stringResource(R.string.checklist_editor_checkpoint_unnamed)
    val tag = "checklist_editor_checkpoint_${checkpoint.id}"
    // A schedule keeps two times, or it would be a checklist: that is a choice its setup makes.
    val keepsTwo = checklist.liveCheckpoints(today).size <= 2
    val removable = !keepsTwo && checklist.canRemoveCheckpoint(checkpoint.id, today)
    // A part just added, or one a schedule starts with, is not wrong for having no name yet:
    // it is said missing once it was typed in, and until then Next says what it waits for.
    var typedIn by rememberSaveable(checkpoint.id) { mutableStateOf(checkpoint.label != null) }
    Column(
        modifier = Modifier.testTag("${tag}_group"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The part's name heads it, in a title's type over the items' plain one.
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
                    NameField(
                        name = checkpoint.label,
                        key = checkpoint.id,
                        label = stringResource(R.string.checklist_editor_checkpoint_unnamed),
                        testTag = "${tag}_name",
                        onName = {
                            typedIn = true
                            onEdit(ChecklistEdit.RenameCheckpoint(checkpoint.id, it))
                        },
                        modifier = Modifier.weight(1f),
                        settled = settled,
                        missingShown = typedIn,
                    )
                }
                // With two, neither can go, so neither offers to: there is nothing to explain.
                if (!keepsTwo) {
                    MomentumRemoveButton(
                        description = stringResource(R.string.checklist_editor_remove, name),
                        onRemove = { onEdit(ChecklistEdit.RemoveCheckpoint(checkpoint.id)) },
                        testTag = "${tag}_remove",
                        variant = MomentumButtonVariant.Quiet,
                        enabled = removable,
                    )
                }
            }
            if (!keepsTwo && !removable) EditorNote(stringResource(R.string.checklist_editor_checkpoint_holds_items), testTag = "${tag}_keep")
        }
        // A section, not a box: the name heads it and the space before the next part ends it, so
        // the one item popped out to be set ([MomentumPopOut]) is the only thing standing up.
        content()
    }
}

/**
 * One checkpoint on the Reminders page: when it checks and what that is today, whether it
 * reminds, and the notification that reminder sends, which [onSendTest] sends now. A checklist
 * with several names each over it; they are made, named and removed with their items on Items.
 */
@Composable
private fun CheckpointEditor(
    checklist: ChecklistConfiguration,
    checkpoint: Checkpoint,
    several: Boolean,
    today: LocalDate,
    bedtime: Bedtime,
    mood: MoMood?,
    progress: ChecklistProgress,
    onEdit: (ChecklistEdit) -> Boolean,
    reminderRow: @Composable () -> Unit,
    onSendTest: ((TestReminder.AtCheckpoint) -> Unit)?,
    onPickTime: () -> Unit,
) {
    val tag = "checklist_editor_checkpoint_${checkpoint.id}"
    Column(
        modifier = Modifier.testTag(tag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (several) {
            Text(
                text = checkpoint.label ?: stringResource(R.string.checklist_editor_checkpoint_unnamed),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("${tag}_title"),
            )
        }
        TimeEditor(
            at = checkpoint.at,
            today = checkpoint.at.on(bedtime),
            testTag = tag,
            onChange = { onEdit(ChecklistEdit.SetCheckpointTime(checkpoint.id, it)) },
            onPickTime = onPickTime,
        )
        reminderRow()
        val test = checklistTestReminder(checklist, checkpoint, progress, today)
        ReminderPreview(
            message = test?.words(mood),
            sent = checkpoint.remind,
            onSendTest = if (test != null && onSendTest != null) ({ onSendTest(test) }) else null,
            testTag = tag,
            // Something is due here, and no reminder: everything due logs only misses.
            onlyMisses = test == null && progress.checkpoints.any { it.checkpoint.id == checkpoint.id },
        )
    }
}

/**
 * The notification as it would arrive: its title over its line, on one ground so it reads as one
 * thing, and under it the way to send it now ([onSendTest], null while there is nothing to send or
 * the view is hidden). The slot is the same whether or not the reminder is [sent], so the
 * switch above never moves what sits below: the words only dim, a line under them says why nothing
 * goes out, and the button sits on a line of its own, whose height owes nothing to that note, so
 * it stays in its place either way: a reminder can be tried before it is switched on. Without a
 * message, [onlyMisses] says whether that is because everything due here logs only misses.
 */
@Composable
internal fun ReminderPreview(message: MoReminderMessage?, sent: Boolean, onSendTest: (() -> Unit)?, testTag: String, onlyMisses: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (message != null) {
            MomentumCard(
                modifier = Modifier.fillMaxWidth(),
                contentColor = if (sent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = message.title.resolve(),
                        modifier = Modifier.testTag("${testTag}_preview_title"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = message.text.resolve(),
                        modifier = Modifier.testTag("${testTag}_preview_text"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        val nothingDue = stringResource(R.string.checklist_editor_reminder_nothing_due)
        val nothingOwed = stringResource(R.string.checklist_editor_reminder_only_misses)
        val off = stringResource(R.string.checklist_editor_reminder_off)
        // The slot holds the whole height any sentence wraps to, so the switch moves nothing.
        ReservedMessage(
            text = when {
                message == null -> if (onlyMisses) nothingOwed else nothingDue
                !sent -> off
                else -> null
            },
            testTag = "${testTag}_preview_note",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            reserving = listOf(nothingDue, nothingOwed, off),
        )
        MomentumButton(
            text = stringResource(R.string.settings_send_test),
            onClick = { onSendTest?.invoke() },
            modifier = Modifier.align(Alignment.End),
            variant = MomentumButtonVariant.Quiet,
            enabled = onSendTest != null,
            testTag = "${testTag}_test",
        )
    }
}

/**
 * The three forms a checkpoint's time takes, each with the one number it needs. A relative form
 * also says what it comes to [today], so "after waking" reads as a clock time too.
 */
@Composable
internal fun TimeEditor(
    at: CheckpointTime,
    today: LocalTime,
    testTag: String,
    onChange: (CheckpointTime) -> Boolean,
    onPickTime: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MomentumChoiceTileRow {
            listOf(
                Triple(at is CheckpointTime.Fixed, R.string.checklist_editor_time_fixed, "fixed") to { onChange(NewCheckpointTime) },
                Triple(at is CheckpointTime.AfterWake, R.string.checklist_editor_time_after_wake, "after_wake") to { onChange(CheckpointTime.AfterWake(DefaultOffset)) },
                Triple(at is CheckpointTime.BeforeSleep, R.string.checklist_editor_time_before_sleep, "before_sleep") to { onChange(CheckpointTime.BeforeSleep(DefaultOffset)) },
            ).forEach { (option, choose) ->
                val (selected, label, tag) = option
                MomentumChoiceTile(
                    label = stringResource(label),
                    selected = selected,
                    onClick = { if (!selected) choose() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    testTag = "${testTag}_time_$tag",
                )
            }
        }
        when (at) {
            is CheckpointTime.Fixed -> {
                val time = formatTime(ReminderTime(at.time.hour, at.time.minute))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.checklist_editor_time), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    MomentumButton(
                        text = time,
                        onClick = onPickTime,
                        variant = MomentumButtonVariant.Secondary,
                        testTag = "${testTag}_time",
                    )
                }
            }
            is CheckpointTime.AfterWake -> NumberStepper(
                label = stringResource(R.string.checklist_editor_offset_after_wake),
                value = at.minutes,
                range = OffsetRange,
                step = OffsetStep,
                valueText = pluralStringResource(R.plurals.checklist_editor_minutes, at.minutes, at.minutes),
                testTag = "${testTag}_offset",
                onChange = { onChange(CheckpointTime.AfterWake(it)) },
            )
            is CheckpointTime.BeforeSleep -> NumberStepper(
                label = stringResource(R.string.checklist_editor_offset_before_sleep),
                value = at.minutes,
                range = OffsetRange,
                step = OffsetStep,
                valueText = pluralStringResource(R.plurals.checklist_editor_minutes, at.minutes, at.minutes),
                testTag = "${testTag}_offset",
                onChange = { onChange(CheckpointTime.BeforeSleep(it)) },
            )
        }
        if (at !is CheckpointTime.Fixed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.checklist_editor_time_today), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = formatTime(ReminderTime(today.hour, today.minute)),
                    modifier = Modifier.testTag("${testTag}_today"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private const val DefaultOffset = 15
private const val OffsetStep = 5

/** Up to half a day from waking or before sleep; a checkpoint further away is better a fixed time. */
private val OffsetRange = 0..720

@Composable
private fun ItemEditor(
    item: ChecklistItem,
    checklistTarget: ChecklistTarget,
    followsChecklist: Boolean,
    checkpoints: List<Checkpoint>,
    today: LocalDate,
    removable: Boolean,
    form: ChecklistDraft,
    onEdit: (ChecklistEdit) -> Boolean,
) {
    val locale = currentLocale()
    val target = item.target ?: checklistTarget
    // The part an item stands in says where it is ticked. Only one saved at several, before each
    // part held its own items, stands under the first of them, so its summary names them all.
    val namedCheckpoints = (item.checkpoints ?: checkpoints.map { it.id }).takeIf { checkpoints.size > 1 && it.size > 1 }
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    MomentumPopOut(open = expanded, onClose = { expanded = false }) {
        Column(
            modifier = Modifier.testTag("checklist_editor_item_${item.id}"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NameField(
                    name = item.label,
                    key = item.id,
                    label = stringResource(R.string.checklist_editor_item_name),
                    testTag = "checklist_editor_item_${item.id}_name",
                    onName = { name ->
                        form.named(item.id, name)
                        name?.let { onEdit(ChecklistEdit.RenameItem(item.id, it)) }
                    },
                    modifier = Modifier.weight(1f),
                    settled = form.nameStored(item.id),
                )
                MomentumRemoveButton(
                    description = stringResource(R.string.checklist_editor_remove, item.label),
                    onRemove = { onEdit(ChecklistEdit.RemoveItem(item.id)) },
                    testTag = "checklist_editor_item_${item.id}_remove",
                    variant = MomentumButtonVariant.Quiet,
                    enabled = removable,
                )
            }
            if (!removable) EditorNote(stringResource(if (checkpoints.size > 1) R.string.checklist_schedule_last_item else R.string.checklist_editor_last_item), testTag = "checklist_editor_item_${item.id}_keep")
            // When it is due, said in full even where it is the usual, so a folded item never hides
            // it; one that follows the checklist says so, as the checklist's rhythm stands above.
            val summary = listOfNotNull(
                if (followsChecklist) stringResource(R.string.checklist_editor_due_as_checklist) else when (target) {
                    is ChecklistTarget.Weekdays -> target.days.sorted().joinToString { it.getDisplayName(TextStyle.SHORT, locale) }
                    is ChecklistTarget.PerWeek -> pluralStringResource(R.plurals.checklist_editor_week_times, target.times, target.times)
                    ChecklistTarget.PerDay -> stringResource(R.string.checklist_editor_every_day)
                },
                namedCheckpoints?.let { names -> checkpoints.filter { it.id in names }.mapNotNull { it.label }.joinToString() },
                item.months?.let(::seasonOrder)?.joinToString { it.getDisplayName(TextStyle.SHORT, locale) }
                    ?: stringResource(R.string.checklist_editor_all_year),
                stringResource(R.string.checklist_editor_exceptions_only_summary).takeIf { item.exceptionsOnly.on(today) },
            ).joinToString(" · ")
            MomentumDisclosure(
                label = stringResource(R.string.checklist_editor_due),
                expanded = expanded,
                onExpandedChange = { expanded = it },
                testTag = "checklist_editor_item_${item.id}_due",
                summary = summary,
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TargetEditor(item, target, checklistTarget, today, onEdit)
                    SeasonEditor(item, today, onEdit)
                }
            }
        }
    }
}

/**
 * When an item is due: every day, on some days, or a number of times a week on any days. What a
 * holiday makes of that, and whether the item logs only misses, are the Streak page's
 * ([AwayRuleEditor], [ExceptionsOnlyGroup]).
 */
@Composable
private fun TargetEditor(
    item: ChecklistItem,
    target: ChecklistTarget,
    checklistTarget: ChecklistTarget,
    today: LocalDate,
    onEdit: (ChecklistEdit) -> Boolean,
) {
    fun setTarget(updated: ChecklistTarget) = onEdit(ChecklistEdit.SetItemTarget(item.id, updated.takeIf { it != checklistTarget }))
    val tag = "checklist_editor_item_${item.id}"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MomentumChoiceTileRow {
            MomentumChoiceTile(
                label = stringResource(R.string.checklist_editor_every_day),
                selected = target == ChecklistTarget.PerDay,
                onClick = { setTarget(ChecklistTarget.PerDay) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTag = "${tag}_every_day",
            )
            MomentumChoiceTile(
                label = stringResource(R.string.checklist_editor_some_days),
                selected = target is ChecklistTarget.Weekdays,
                onClick = { if (target !is ChecklistTarget.Weekdays) setTarget(ChecklistTarget.Weekdays(setOf(today.dayOfWeek))) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTag = "${tag}_some_days",
            )
            MomentumChoiceTile(
                label = stringResource(R.string.checklist_editor_times_a_week),
                selected = target is ChecklistTarget.PerWeek,
                onClick = { if (target !is ChecklistTarget.PerWeek) setTarget(ChecklistTarget.PerWeek()) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTag = "${tag}_times_a_week",
            )
        }
        when (target) {
            is ChecklistTarget.Weekdays -> WeekdayToggles(
                chosen = target.days,
                testTag = { "${tag}_day_${it.value}" },
                onChange = { days -> setTarget(ChecklistTarget.Weekdays(days)) },
            )
            is ChecklistTarget.PerWeek -> CountRow(
                counts = (1..DaysPerWeek).toList(),
                chosen = target.times,
                testTag = { "${tag}_times_$it" },
                spoken = { pluralStringResource(R.plurals.checklist_editor_week_times, it, it) },
                onChoose = { times -> setTarget(ChecklistTarget.PerWeek(times)) },
            )
            ChecklistTarget.PerDay -> Unit
        }
    }
}

/**
 * Which items log only misses, a checkbox for each: checked, the item is kept on a day it is due
 * until it is marked missed, so a habit long established asks for no tick. [items] are the ones
 * that can, due every day or on chosen weekdays; one due a number of times a week keeps its ticks,
 * which a line under the list says while the checklist has one ([hasWeekItems]). Without an item
 * to list there is no group.
 *
 * It is folded away under "Advanced" ([MomentumDisclosure]), closed until a person opens it: a
 * checked item counts with no entry at all, which nobody setting up a first checklist can judge
 * and nobody should choose by a stray tap. Beside "Advanced" the fold names the checked items, so
 * what is chosen shows while it is closed, and open it pops out as an item's "When it's due" does
 * ([MomentumPopOut]). The fold is tagged `checklist_editor_advanced`.
 *
 * Only misses and fewer times on holiday exclude each other, and both are chosen on this page, so
 * each half says what choosing it changes under the choice it warns about. [canAskFewerTimes] says
 * which items have the other half ([AwayRuleEditor]).
 */
@Composable
private fun ExceptionsOnlyGroup(
    items: List<ChecklistItem>,
    hasWeekItems: Boolean,
    canAskFewerTimes: (ChecklistItem) -> Boolean,
    today: LocalDate,
    onEdit: (ChecklistEdit) -> Boolean,
) {
    if (items.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chosen = items.filter { it.exceptionsOnly.on(today) }
    MomentumPopOut(open = expanded, onClose = { expanded = false }) {
        MomentumDisclosure(
            label = stringResource(R.string.checklist_editor_advanced),
            expanded = expanded,
            onExpandedChange = { expanded = it },
            testTag = "checklist_editor_advanced",
            summary = chosen.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.checklist_editor_advanced_only_misses, it.joinToString { item -> item.label }) },
        ) {
            EditorGroup(stringResource(R.string.checklist_editor_exceptions_only)) {
                EditorNote(stringResource(R.string.checklist_editor_exceptions_only_note), testTag = "checklist_editor_exceptions_only_note")
                Column {
                    items.forEach { item ->
                        key(item.id) { ExceptionsOnlyEditor(item, canAskFewerTimes(item), today, onEdit) }
                    }
                }
                if (hasWeekItems) {
                    EditorNote(stringResource(R.string.checklist_editor_exceptions_only_week_items), testTag = "checklist_editor_exceptions_only_week_items")
                }
            }
        }
    }
}

/**
 * Whether one item logs only misses. The row shows the item's name, and TalkBack hears the choice
 * with it. Under an item that [canAskFewerTimes] on holiday, a line says that checking the box
 * ends that, while it would; the line keeps its room whether or not it speaks, so neither this
 * box nor the holiday choice under it moves anything.
 */
@Composable
private fun ExceptionsOnlyEditor(item: ChecklistItem, canAskFewerTimes: Boolean, today: LocalDate, onEdit: (ChecklistEdit) -> Boolean) {
    val onlyMisses = item.exceptionsOnly.on(today)
    val tag = "checklist_editor_item_${item.id}_exceptions_only"
    val spoken = stringResource(R.string.checklist_editor_exceptions_only_item, item.label)
    Column {
        MomentumCheckboxRow(
            checked = onlyMisses,
            onCheckedChange = { onEdit(ChecklistEdit.SetItemExceptionsOnly(item.id, it)) },
            testTag = tag,
        ) {
            Text(
                text = item.label,
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (canAskFewerTimes) {
            val endsFewerTimes = stringResource(R.string.checklist_editor_exceptions_only_ends_fewer_times)
            ReservedMessage(
                text = endsFewerTimes.takeIf { !onlyMisses && item.whenAway.on(today) == WhenAway.LowerTarget },
                testTag = "${tag}_note",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                reserving = listOf(endsFewerTimes),
            )
        }
    }
}

/**
 * The items the Streak page lists under "Only log misses": the ones in use from [today] on that
 * are due every day or on chosen weekdays, each as it is now. Asked of the page's draft, so an
 * item added on the page is listed, and one made due a number of times a week is not.
 */
internal fun ChecklistConfiguration.itemsThatCanLogOnlyMisses(today: LocalDate): List<ChecklistItem> =
    liveItems(today).filter { (it.target ?: target).canBeExceptionsOnly }

/**
 * What a holiday means for one item ([AwayRuleEditor]): still due, not due, or, for an item due
 * more than once a week, fewer times, with how many times a week on holiday asks. Every row reads
 * alike, so TalkBack names the item with each choice. [options] are the choices it offers
 * ([awayOptionsOf]), and [opened] is the item as the page opened on it: a once-a-week item lowered
 * before Block 17 keeps Fewer times and the count's slot after it goes back to due, whichever page
 * is visited in between, so nothing below moves and the choice can be undone, and fewer times
 * chosen again means what it meant, not a first choice.
 */
@Composable
private fun ItemAwayRuleEditor(
    item: ChecklistItem,
    opened: ChecklistItem,
    options: List<WhenAway>,
    checklistTarget: ChecklistTarget,
    today: LocalDate,
    onEdit: (ChecklistEdit) -> Boolean,
) {
    val target = item.target ?: checklistTarget
    val rule = item.whenAway.on(today)
    val legacy = legacyAwayTimes(opened, target, opened.whenAway.on(today))
    val tag = "checklist_editor_item_${item.id}_away"
    val offered = awayTimesOffered(target)
    // The stored count shows as a tile of its own while it is the item's; its slot stays, so
    // picking an offered count moves nothing either.
    val current = legacyAwayTimes(item, target, rule)
    AwayRuleEditor(
        rule = rule,
        options = options,
        testTag = tag,
        onChoose = { option -> fewerTimesEdits(item, target, option, legacy).forEach { edit -> onEdit(edit) } },
        count = AwayCountChoice(
            label = stringResource(R.string.checklist_editor_away_times),
            counts = listOfNotNull(legacy.takeIf { it == 0 }) + offered + listOfNotNull(legacy.takeIf { it != 0 }),
            chosen = item.awayTimes ?: target.timesAWeek,
            spoken = { pluralStringResource(R.plurals.checklist_editor_away_week_times, it, it) },
            tag = { "${tag}_times_$it" },
            onChoose = { away -> onEdit(ChecklistEdit.SetItemAwayTimes(item.id, away)) },
            shown = { it in offered || it == current },
        ),
        title = item.label,
        spoken = { label -> stringResource(R.string.checklist_editor_away_item_option, item.label, label) },
        note = {
            // Fewer times cannot stand beside only misses, so while the item logs only misses a
            // line says what choosing it changes. It takes no room while it has nothing to say:
            // a list of items each holding an empty line read as holes in the page.
            if (target.canBeExceptionsOnly && item.exceptionsOnly.on(today)) {
                EditorNote(stringResource(R.string.checklist_editor_fewer_times_ends_exceptions_only), testTag = "${tag}_exceptions_only_note")
            }
        },
    )
}

/**
 * The count fewer times asks for while away, under the rule's tiles ([AwayRuleEditor]): [label]
 * over the row, the [counts] offered with the one [chosen] and those [shown] among them, what
 * TalkBack says for a count ([spoken]), each tile's tag ([tag]) and what choosing one does.
 */
internal class AwayCountChoice(
    val label: String,
    val counts: List<Int>,
    val chosen: Int,
    val spoken: @Composable (Int) -> String,
    val tag: (Int) -> String,
    val onChoose: (Int) -> Boolean,
    val shown: (Int) -> Boolean = { true },
)

/**
 * What a holiday means for one rule, a checklist item's or a whole view's: [rule] among the
 * [options] it offers, and under them, while fewer times is chosen, the [count] a week away
 * asks. The count opens under the tile that was just pressed and takes no room before: several
 * rules on one page, each holding an empty count's room, read as holes in it. A page that lists
 * several rules names each by its [title]; [optionLabel] is a rule's word where a view has its
 * own, [spoken] what TalkBack says for one, and [note] stands between the tiles and the count.
 * A tile is tagged `<testTag>_<rule>`.
 */
@Composable
internal fun AwayRuleEditor(
    rule: WhenAway,
    options: List<WhenAway>,
    testTag: String,
    onChoose: (WhenAway) -> Unit,
    count: AwayCountChoice? = null,
    title: String? = null,
    optionLabel: @Composable (WhenAway) -> String = { stringResource(it.labelRes) },
    spoken: @Composable (String) -> String = { it },
    note: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(text = title, style = MaterialTheme.typography.bodyLarge)
        MomentumChoiceTileRow {
            options.forEach { option ->
                val label = optionLabel(option)
                MomentumChoiceTile(
                    label = label,
                    selected = rule == option,
                    onClick = { if (rule != option) onChoose(option) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    testTag = "${testTag}_${option.storageId}",
                    spoken = spoken(label),
                )
            }
        }
        if (count != null && WhenAway.LowerTarget in options) {
            note()
            if (rule == WhenAway.LowerTarget) {
                EditorNote(count.label)
                CountRow(
                    counts = count.counts,
                    chosen = count.chosen,
                    testTag = count.tag,
                    spoken = count.spoken,
                    shown = count.shown,
                    onChoose = count.onChoose,
                )
            }
        }
    }
}

/**
 * Which day each day of a view asks about: that day, or the day before for a checklist ticked or
 * questions answered the morning after. The line under the choice says what the chosen one means,
 * in the words of the view's kind ([label], [note]). A view whose day is fixed shows the choice
 * without offering it ([enabled]). A tile is tagged `<testTag>_about_<day>`, the line
 * `<testTag>_about_note`.
 */
@Composable
internal fun AboutEditor(
    about: AboutDay,
    testTag: String,
    label: (AboutDay) -> Int,
    note: (AboutDay) -> Int,
    enabled: Boolean = true,
    onChoose: (AboutDay) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MomentumChoiceTileRow {
            AboutDay.entries.forEach { option ->
                MomentumChoiceTile(
                    label = stringResource(label(option)),
                    selected = about == option,
                    onClick = { if (about != option) onChoose(option) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    enabled = enabled,
                    testTag = "${testTag}_about_${option.storageId}",
                )
            }
        }
        EditorNote(stringResource(note(about)), testTag = "${testTag}_about_note")
    }
}

/**
 * How often every item of a plain checklist is due: daily, or a number of times a week on any days.
 * An item can still differ under its own "When it's due". The count row and the note keep their
 * space while the checklist is daily, so choosing weekly moves nothing below them. A checklist stored on chosen
 * weekdays shows neither choice selected until one is made.
 */
@Composable
private fun RhythmEditor(target: ChecklistTarget, onChoose: (ChecklistTarget) -> Unit) {
    val weekly = target as? ChecklistTarget.PerWeek
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MomentumChoiceTileRow {
            MomentumChoiceTile(
                label = stringResource(R.string.checklist_editor_rhythm_daily),
                selected = target == ChecklistTarget.PerDay,
                onClick = { if (target != ChecklistTarget.PerDay) onChoose(ChecklistTarget.PerDay) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTag = "checklist_editor_rhythm_daily",
            )
            MomentumChoiceTile(
                label = stringResource(R.string.checklist_editor_rhythm_weekly),
                selected = weekly != null,
                onClick = { if (weekly == null) onChoose(ChecklistTarget.PerWeek()) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTag = "checklist_editor_rhythm_weekly",
            )
        }
        CountRow(
            counts = (1..DaysPerWeek).toList(),
            chosen = weekly?.times ?: 0,
            testTag = { "checklist_editor_rhythm_times_$it" },
            spoken = { pluralStringResource(R.plurals.checklist_editor_week_times, it, it) },
            onChoose = { times -> onChoose(ChecklistTarget.PerWeek(times)).let { true } },
            shown = { weekly != null },
        )
        val daily = stringResource(R.string.checklist_editor_rhythm_daily_note)
        val weeklyNotes = (1..DaysPerWeek).map { pluralStringResource(R.plurals.checklist_editor_rhythm_weekly_note, it, it) }
        ReservedMessage(
            text = when {
                weekly != null -> weeklyNotes[weekly.times - 1]
                target == ChecklistTarget.PerDay -> daily
                else -> null
            },
            testTag = "checklist_editor_rhythm_note",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            reserving = weeklyNotes + daily,
            announced = false,
        )
    }
}

@get:StringRes
private val AboutDay.labelRes: Int
    get() = when (this) {
        AboutDay.Today -> R.string.checklist_editor_about_today
        AboutDay.Yesterday -> R.string.checklist_editor_about_yesterday
    }

@get:StringRes
private val AboutDay.noteRes: Int
    get() = when (this) {
        AboutDay.Today -> R.string.checklist_editor_about_today_note
        AboutDay.Yesterday -> R.string.checklist_editor_about_yesterday_note
    }

/**
 * The ways a holiday can meet an item: fewer times is offered to an item due more than once a week,
 * and stays while it is the item's choice, as a once-a-week item lowered before Block 17 keeps it.
 */
/**
 * The holiday choices [item] offers on a page opened on [opened]. A once-a-week item that opened
 * lowered keeps fewer times while its target is the one it opened with, so going back to due can
 * be undone; any other item offers what its current target allows.
 */
internal fun awayOptionsOf(item: ChecklistItem, opened: ChecklistItem, checklistTarget: ChecklistTarget, today: LocalDate): List<WhenAway> {
    val target = item.target ?: checklistTarget
    val openedRule = opened.whenAway.on(today)
    val keepsOpenedChoice = openedRule == WhenAway.LowerTarget &&
        (opened.target ?: checklistTarget).let { !it.canAskFewerTimes && it == target }
    return awayOptions(target, if (keepsOpenedChoice) openedRule else item.whenAway.on(today))
}

internal fun awayOptions(target: ChecklistTarget, rule: WhenAway): List<WhenAway> =
    listOf(WhenAway.Due, WhenAway.Excused) + listOfNotNull(WhenAway.LowerTarget.takeIf { target.canAskFewerTimes || rule == WhenAway.LowerTarget })

/**
 * The counts fewer times offers, for a week item as for any other: one to one below the usual
 * count, so no tile means the same as still due (the usual count) or not due (none over a whole
 * away week). Empty for an item due once a week, which has nothing between the two.
 */
internal fun awayTimesOffered(target: ChecklistTarget): IntRange = 1 until target.timesAWeek

/**
 * A count a week item could store before Block 17 and is offered no more: none while the item
 * lowers, which asks the usual count, or nothing over a whole away week. It keeps its meaning and
 * shows as a tile of its own while it is the item's; null while the item's count is an offered one.
 */
internal fun legacyAwayTimes(item: ChecklistItem, target: ChecklistTarget, rule: WhenAway): Int? = when {
    item.awayTimes == 0 -> 0
    item.awayTimes == null && rule == WhenAway.LowerTarget -> target.timesAWeek
    else -> null
}

/**
 * The edits that give an item the holiday rule [option]. Choosing fewer times for the first time
 * picks about half the usual count, rounded down and at least one, before the rule, so the count's
 * entry starts today and the rule is set plainly on it. A count once chosen is found again, and an
 * item that lowered without one before Block 17 ([legacy], its usual count) asks that again rather
 * than a first choice, so still due and back on one page leaves it as it was.
 */
internal fun fewerTimesEdits(item: ChecklistItem, target: ChecklistTarget, option: WhenAway, legacy: Int? = null): List<ChecklistEdit> = buildList {
    if (option == WhenAway.LowerTarget && target.canAskFewerTimes && item.awayTimes == null && legacy == null) {
        add(ChecklistEdit.SetItemAwayTimes(item.id, (target.timesAWeek / 2).coerceAtLeast(1)))
    }
    add(ChecklistEdit.SetItemWhenAway(item.id, option))
}

@get:StringRes
private val WhenAway.labelRes: Int
    get() = when (this) {
        WhenAway.Due -> R.string.checklist_editor_away_due
        WhenAway.Excused -> R.string.checklist_editor_away_excused
        WhenAway.LowerTarget -> R.string.checklist_editor_away_lower
    }

/**
 * How many, one tile per number; [spoken] is what TalkBack says for a number, such as "3 times a
 * week". A number [shown] says no to keeps its slot and is neither seen, pressed nor spoken.
 */
@Composable
internal fun CountRow(
    counts: List<Int>,
    chosen: Int,
    testTag: (Int) -> String,
    spoken: @Composable (Int) -> String,
    onChoose: (Int) -> Boolean,
    enabled: Boolean = true,
    shown: (Int) -> Boolean = { true },
) {
    FingertipRow(counts) { count, tileModifier ->
        val visible = shown(count)
        MomentumChoiceTile(
            label = count.toString(),
            selected = count == chosen,
            onClick = { if (count != chosen) onChoose(count) },
            modifier = tileModifier
                .graphicsLayer { alpha = if (visible) 1f else 0f }
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
            enabled = enabled && visible,
            testTag = testTag(count),
            spoken = spoken(count),
        )
    }
}

private const val DaysPerWeek = 7

@Composable
private fun SeasonEditor(item: ChecklistItem, today: LocalDate, onEdit: (ChecklistEdit) -> Boolean) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MomentumChoiceTileRow {
            MomentumChoiceTile(
                label = stringResource(R.string.checklist_editor_all_year),
                selected = item.months == null,
                onClick = { onEdit(ChecklistEdit.SetItemSeason(item.id, null)) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTag = "checklist_editor_item_${item.id}_all_year",
            )
            MomentumChoiceTile(
                label = stringResource(R.string.checklist_editor_some_months),
                selected = item.months != null,
                onClick = { if (item.months == null) onEdit(ChecklistEdit.SetItemSeason(item.id, setOf(today.month))) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTag = "checklist_editor_item_${item.id}_some_months",
            )
        }
        item.months?.let { months ->
            ToggleGrid(
                options = Month.entries,
                columns = 4,
                chosen = months,
                label = { it.getDisplayName(TextStyle.SHORT, locale) },
                testTag = { "checklist_editor_item_${item.id}_month_${it.value}" },
                onChange = { onEdit(ChecklistEdit.SetItemSeason(item.id, it)) },
            )
        }
    }
}

/** A season in the order it is lived: from its first month after a gap, so winter reads Oct, …, Mar. */
private fun seasonOrder(months: Set<Month>): List<Month> {
    val start = months.firstOrNull { it.minus(1) !in months } ?: Month.JANUARY
    return months.sortedBy { (it.value - start.value + 12) % 12 }
}

/**
 * Whether a view a person made counts toward the streak, from today on, and how its tokens are
 * earned. A day it took part keeps its judgment when it leaves. A checklist's page and a made
 * measurement's share it, so joining, leaving and the bank are set the same way on both: the
 * switch is tagged `<testTag>_rewards`, and the steppers as [RewardRuleSteppers] tags them.
 * [covers] says what one token covers where the bank's size does not, tagged
 * `<testTag>_token_covers`, and [earnNote] how the bank earns where the steppers do not, tagged
 * `<testTag>_earn_note`.
 */
@Composable
internal fun RewardsEditor(
    rewards: ChecklistRewards?,
    today: LocalDate,
    testTag: String,
    onTakePart: (Boolean) -> Unit,
    onRules: (maxPasses: Int, everyKeptDays: Int) -> Boolean,
    covers: String? = null,
    earnNote: String? = null,
) {
    StreakSwitch(takesPart = rewards?.takesPartOn(today) == true, testTag = testTag, onTakePart = onTakePart) {
        if (rewards != null) {
            RewardRuleSteppers(rewards, today, testTag, onRules)
            if (earnNote != null) EditorNote(earnNote, testTag = "${testTag}_earn_note")
            if (covers != null) EditorNote(covers, testTag = "${testTag}_token_covers")
        }
    }
}

/**
 * "Counts toward the streak", the switch every view a person made has, and Activity, tagged
 * `<testTag>_rewards`, with the lines that say what it decides ([StreakNotes]), and under
 * them, while the view counts, what its [bank] can be set to: a
 * checklist's and a daily view's two steppers, a time view's one. A page whose bank stands in
 * groups of their own, as Activity's steps and bank do, shows them after the switch instead.
 */
@Composable
internal fun StreakSwitch(takesPart: Boolean, testTag: String, onTakePart: (Boolean) -> Unit, bank: @Composable ColumnScope.() -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorSwitch(label = stringResource(R.string.checklist_settings_rewards), checked = takesPart, testTag = "${testTag}_rewards", onCheckedChange = onTakePart)
        StreakNotes(testTag)
        if (takesPart) bank()
    }
}

/** A setting that is on or off: its [label] with the switch beside it, which is spoken by that label. */
@Composable
internal fun EditorSwitch(label: String, checked: Boolean, testTag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .testTag(testTag)
                .semantics { contentDescription = label },
        )
    }
}

/**
 * What "Counts toward the streak" decides, said where it is decided, on every page that has it:
 * what the streak and a token are, since a setup asks for a bank's numbers before a person has
 * met either, tagged `<testTag>_streak_explained`; then the one rule for which views Mo watches,
 * tagged `<testTag>_mo_watches`. The lines hold for every kind of view, so they name no amount:
 * how much one token covers is its kind's to say beside the bank.
 */
@Composable
internal fun ColumnScope.StreakNotes(testTag: String) {
    EditorNote(stringResource(R.string.checklist_settings_rewards_explained), testTag = "${testTag}_streak_explained")
    EditorNote(stringResource(R.string.checklist_settings_rewards_mo), testTag = "${testTag}_mo_watches")
}

/**
 * The two steppers of a bank that counts toward the streak, how many tokens it holds and how many
 * kept days earn one. A checklist's page, a made measurement's and the Substances page share them,
 * so one bank is set the same way everywhere; [onChange] gets both values, as the edit that stores
 * them does, and the steppers are tagged `<testTag>_max_passes` and `<testTag>_every_kept_days`.
 */
@Composable
internal fun RewardRuleSteppers(
    rewards: ChecklistRewards,
    today: LocalDate,
    testTag: String,
    onChange: (maxPasses: Int, everyKeptDays: Int) -> Boolean,
) {
    val maxPasses = rewards.maxPasses.on(today)
    val everyKeptDays = rewards.everyKeptDays.on(today)
    BankSizeStepper(maxPasses, testTag) { onChange(it, everyKeptDays) }
    NumberStepper(
        label = stringResource(R.string.checklist_editor_every_kept_days),
        value = everyKeptDays,
        range = 1..MaxKeptDaysPerPass,
        valueText = pluralStringResource(R.plurals.checklist_editor_days, everyKeptDays, everyKeptDays),
        testTag = "${testTag}_every_kept_days",
        onChange = { onChange(maxPasses, it) },
    )
}

/**
 * "Max. savable tokens", the one number every bank has, whatever fills it: a checklist's, a daily
 * view's or a timer's. It is tagged `<testTag>_max_passes`.
 */
@Composable
internal fun BankSizeStepper(maxPasses: Int, testTag: String, onChange: (Int) -> Boolean) {
    NumberStepper(
        label = stringResource(R.string.checklist_editor_max_passes),
        value = maxPasses,
        range = 1..MaxBank,
        valueText = maxPasses.toString(),
        testTag = "${testTag}_max_passes",
        onChange = onChange,
    )
}

/** The most tokens any bank can be set to hold. */
internal const val MaxBank = 9
private const val MaxKeptDaysPerPass = 30

/**
 * A number changed a step at a time; each step changes the draft, so there is nothing to confirm.
 * [compact], the label is left to TalkBack and the stepper is as wide as its buttons and value,
 * for a row whose other columns say what the number is.
 */
@Composable
internal fun NumberStepper(
    label: String,
    value: Int,
    range: IntRange,
    valueText: String,
    testTag: String,
    onChange: (Int) -> Boolean,
    step: Int = 1,
    compact: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!compact) Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        SmallIconButton(
            description = stringResource(R.string.checklist_editor_decrease, label),
            icon = Icons.Rounded.Remove,
            enabled = value > range.first,
            testTag = "${testTag}_less",
            onClick = { onChange((value - step).coerceAtLeast(range.first)) },
        )
        Text(
            text = valueText,
            modifier = Modifier
                .widthIn(min = if (compact) CompactStepperValueWidth else 88.dp)
                .testTag("${testTag}_value"),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        SmallIconButton(
            description = stringResource(R.string.checklist_editor_increase, label),
            icon = Icons.Rounded.Add,
            enabled = value < range.last,
            testTag = "${testTag}_more",
            onClick = { onChange((value + step).coerceAtMost(range.last)) },
        )
    }
}

/** Room for one digit between the buttons of a compact stepper. */
private val CompactStepperValueWidth = 28.dp

private val StepButtonWidth = 48.dp

/** How wide a compact stepper is with [widestValue] between its buttons, for a row that lays its other columns out around it. */
@Composable
internal fun compactStepperWidth(widestValue: String): Dp {
    val measurer = rememberTextMeasurer()
    val value = with(LocalDensity.current) { measurer.measure(widestValue, MaterialTheme.typography.titleMedium).size.width.toDp() }
    return StepButtonWidth * 2 + maxOf(CompactStepperValueWidth, value)
}

/** A square icon button without a ledge, for a small action beside what it acts on: a stepper's − and +, or moving a row up. */
@Composable
internal fun SmallIconButton(
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    MomentumButton(
        onClick = onClick,
        modifier = Modifier.width(StepButtonWidth),
        variant = MomentumButtonVariant.Quiet,
        enabled = enabled,
        testTag = testTag,
        faceModifier = Modifier.semantics { contentDescription = description },
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

/**
 * What a name field shows: the text typed into it, or, while nothing was typed, the latest
 * [name], so a name stored elsewhere meanwhile reaches an untouched field. Once what was typed
 * is stored, the field is untouched again. Kept across process death with the field.
 */
internal class NameFieldText(text: String, typed: Boolean = false) {
    var text: String by mutableStateOf(text)
        private set
    var typed: Boolean by mutableStateOf(typed)
        private set

    fun type(text: String) {
        this.text = text
        typed = true
    }

    fun follow(name: String?, settled: Boolean) {
        if (settled) typed = false
        if (!typed && text != name.orEmpty()) text = name.orEmpty()
    }

    companion object {
        val Saver: Saver<NameFieldText, Any> = listSaver(
            save = { listOf(it.text, it.typed) },
            restore = { NameFieldText(it[0] as String, it[1] as Boolean) },
        )
    }
}

/**
 * A text the user names something with, taken into the draft as it is typed: [onName] gets the
 * trimmed name with every change, or null while the field is blank, which the field says is
 * missing unless the name is [optional] or [missingShown] holds it back, as the setup does until
 * something was typed. A name the draft refuses for another reason, such as one another activity
 * has, is said by [error] in the label's place. What is typed is kept by [key], the thing being
 * named, so an item removed above never hands its text to the one that moves into its place; a
 * field nothing was typed into, or whose typing is stored ([settled]), shows the latest [name].
 * A draft that keeps what the field holds while it is out of sight hears it as typed through
 * [onText], and hands it back as [name] with [settled] on.
 */
@Composable
internal fun NameField(
    name: String?,
    key: String,
    label: String,
    testTag: String,
    onName: (String?) -> Unit,
    modifier: Modifier = Modifier,
    settled: Boolean = false,
    optional: Boolean = false,
    missingShown: Boolean = true,
    error: String? = null,
    onText: (String) -> Unit = {},
) {
    val field = rememberSaveable(key, saver = NameFieldText.Saver) { NameFieldText(name.orEmpty()) }
    field.follow(name, settled)
    val text = field.text
    val missing = text.isBlank() && !optional && missingShown
    MomentumTextField(
        value = text,
        onValueChange = { typed ->
            field.type(typed)
            onText(typed)
            onName(typed.trim().ifEmpty { null })
        },
        label = label,
        error = if (missing) stringResource(R.string.checklist_editor_name_missing) else error,
        modifier = modifier.fillMaxWidth(),
        testTag = testTag,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
    )
}

/**
 * Names something new and adds it; the field empties once it is added. Until then its text is
 * work [form] knows not to lose, under [key], the field's name to the draft. A name that cannot
 * be added as it stands, such as one another activity has, is said by [refused] in the label's
 * place, and Add waits. A field that comes back into sight starts from [kept], the text a draft
 * that keeps it holds.
 */
@Composable
internal fun AddField(
    label: String,
    testTag: String,
    form: SettingsDraft<*, *>,
    onAdd: (String) -> Boolean,
    key: String = testTag,
    refused: @Composable (String) -> String? = { null },
    kept: String = "",
) {
    var draft by rememberSaveable { mutableStateOf(kept) }
    val trimmed = draft.trim()
    val error = trimmed.takeIf { it.isNotEmpty() }?.let { refused(it) }
    fun type(text: String) {
        draft = text
        form.typing(key, text)
    }
    // Enter adds, and the field stays in sight and keeps the keyboard, so a list is typed one
    // line after another. The item lands above it, so it is brought back once that is laid out.
    val inSight = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    fun add() {
        if (trimmed.isNotEmpty() && error == null && onAdd(trimmed)) {
            type("")
            scope.launch {
                withFrameNanos { }
                inSight.bringIntoView()
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(inSight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MomentumTextField(
            value = draft,
            onValueChange = ::type,
            label = label,
            error = error,
            modifier = Modifier.weight(1f),
            testTag = testTag,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
        MomentumButton(
            text = stringResource(R.string.checklist_editor_add),
            onClick = ::add,
            enabled = trimmed.isNotEmpty() && error == null,
            variant = MomentumButtonVariant.Secondary,
            testTag = "${testTag}_button",
        )
    }
}

/**
 * The icons a view can wear, as a grid of tiles under its title. Every page whose look is a
 * setting shows the same one, listing [icons], the catalogue in its order unless a kind puts its
 * own first; a tile is tagged `<testTag>_icon_<id>`.
 */
@Composable
internal fun IconPicker(selected: ViewIcon, testTag: String, icons: List<ViewIcon> = ViewIcon.entries, onSelect: (ViewIcon) -> Unit) {
    EditorGroup(stringResource(R.string.checklist_editor_icon)) { IconGrid(selected, testTag, icons, onSelect) }
}

/**
 * The icon picker's grid alone, for a place that names it otherwise, as an activity's page does.
 * Every tile is one size, its icon over one line, so a long name shrinks rather than make its row
 * taller than the others.
 */
@Composable
internal fun IconGrid(selected: ViewIcon?, testTag: String, icons: List<ViewIcon> = ViewIcon.entries, onSelect: (ViewIcon) -> Unit) {
    // Two rows of the icons that suit the kind, and the whole catalogue behind More: scrolling past
    // fifty icons to reach a name or a color is too far. One chosen further down opens it.
    val first = icons.take(PickerColumns * IconPreviewRows)
    var all by rememberSaveable(testTag) { mutableStateOf(selected != null && selected !in first) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTiles(if (all) icons else first, selected, testTag, onSelect)
        if (icons.size > first.size) {
            MomentumButton(
                text = stringResource(if (all) R.string.checklist_editor_icons_fewer else R.string.checklist_editor_icons_more),
                onClick = { all = !all },
                modifier = Modifier.fillMaxWidth(),
                variant = MomentumButtonVariant.Quiet,
                icon = if (all) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                testTag = "${testTag}_icons_more",
            )
        }
    }
}

/** Rows of the icon picker shown before More. */
private const val IconPreviewRows = 2

@Composable
private fun IconTiles(icons: List<ViewIcon>, selected: ViewIcon?, testTag: String, onSelect: (ViewIcon) -> Unit) {
    MomentumTileGrid(icons, columns = PickerColumns) { icon, tileModifier ->
        MomentumChoiceTile(
            label = stringResource(icon.labelRes),
            selected = icon == selected,
            onClick = { onSelect(icon) },
            modifier = tileModifier,
            icon = icon.vector,
            testTag = "${testTag}_icon_${icon.storageId}",
            oneLine = true,
        )
    }
}

/** The colors a view can wear ([PickerHues]), each tile showing its own; a tile is tagged `<testTag>_hue_<id>`. */
@Composable
internal fun HuePicker(selected: ViewHue, testTag: String, onSelect: (ViewHue) -> Unit) {
    EditorGroup(stringResource(R.string.checklist_editor_hue)) {
        MomentumTileGrid(PickerHues, columns = PickerColumns) { hue, tileModifier ->
            val accent = hue.momentumHue.viewColors().accent
            MomentumChoiceTile(
                label = stringResource(hue.labelRes),
                selected = hue == selected,
                onClick = { onSelect(hue) },
                modifier = tileModifier,
                accent = accent,
                swatch = accent,
                testTag = "${testTag}_hue_${hue.storageId}",
            )
        }
    }
}

/** One setting under its title, as every page whose look is a setting groups them. */
@Composable
internal fun EditorGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupTitle(title)
        content()
    }
}

/** A line of small print under a setting, saying what it means or why it cannot be changed. */
@Composable
internal fun EditorNote(text: String, testTag: String? = null) {
    Text(
        text = text,
        modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Several of a short set chosen at once, at least one of them. */
@Composable
private fun <T> ToggleGrid(
    options: List<T>,
    columns: Int,
    chosen: Set<T>,
    label: @Composable (T) -> String,
    testTag: (T) -> String,
    onChange: (Set<T>) -> Boolean,
) {
    MomentumTileGrid(options, columns) { option, tileModifier ->
        ToggleTile(option, options, chosen, label(option), testTag(option), onChange, tileModifier)
    }
}

/**
 * The week as one row of seven, Monday first like every calendar, in the calendars' two letters;
 * TalkBack says each whole name. A some-days item and the workout reminder pick their days with it.
 */
@Composable
internal fun WeekdayToggles(
    chosen: Set<DayOfWeek>,
    testTag: (DayOfWeek) -> String,
    onChange: (Set<DayOfWeek>) -> Boolean,
) {
    val locale = currentLocale()
    val days = DayOfWeek.entries
    FingertipRow(days) { day, tileModifier ->
        ToggleTile(
            option = day,
            options = days,
            chosen = chosen,
            label = stringResource(calendarWeekdayShortLabel(day)),
            testTag = testTag(day),
            onChange = onChange,
            modifier = tileModifier,
            spoken = day.getDisplayName(TextStyle.FULL, locale),
        )
    }
}

/**
 * A short set as one full row of equal tiles. Where fingertip-wide tiles do not fit, the row
 * scrolls rather than shrink them, so no option is ever harder to hit than its neighbours.
 */
@Composable
private fun <T> FingertipRow(options: List<T>, tile: @Composable RowScope.(T, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fits = (maxWidth - TileGap * (options.size - 1)) / options.size >= MinimumTileWidth
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState())),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            options.forEach { option -> tile(option, if (fits) Modifier.weight(1f) else Modifier.width(MinimumTileWidth)) }
        }
    }
}

/** One option of several chosen at once; the last one chosen cannot be taken away. */
@Composable
private fun <T> ToggleTile(
    option: T,
    options: List<T>,
    chosen: Set<T>,
    label: String,
    testTag: String,
    onChange: (Set<T>) -> Boolean,
    modifier: Modifier,
    spoken: String? = null,
) {
    val on = option in chosen
    MomentumChoiceTile(
        label = label,
        selected = on,
        onClick = {
            val updated = if (on) chosen - option else chosen + option
            if (updated.isNotEmpty()) onChange(options.filter { it in updated }.toSet())
        },
        modifier = modifier,
        role = Role.Checkbox,
        testTag = testTag,
        spoken = spoken,
    )
}

/** A tile narrower than a fingertip is hard to hit next to its neighbours. */
private val MinimumTileWidth = 48.dp
private val TileGap = 4.dp

/** The name an icon is picked by, which the picker's tile shows and an activity's folded icon names. */
@get:StringRes
internal val ViewIcon.labelRes: Int
    get() = when (this) {
        ViewIcon.Medication -> R.string.checklist_icon_medication
        ViewIcon.Checklist -> R.string.checklist_icon_checklist
        ViewIcon.Schedule -> R.string.checklist_icon_schedule
        ViewIcon.Toothbrush -> R.string.checklist_icon_toothbrush
        ViewIcon.Water -> R.string.checklist_icon_water
        ViewIcon.Shower -> R.string.checklist_icon_shower
        ViewIcon.Spa -> R.string.checklist_icon_spa
        ViewIcon.Meditation -> R.string.checklist_icon_meditation
        ViewIcon.Sleep -> R.string.checklist_icon_sleep
        ViewIcon.Food -> R.string.checklist_icon_food
        ViewIcon.Walk -> R.string.checklist_icon_walk
        ViewIcon.Heart -> R.string.checklist_icon_heart
        ViewIcon.Mind -> R.string.checklist_icon_mind
        ViewIcon.Study -> R.string.checklist_icon_study
        ViewIcon.Music -> R.string.checklist_icon_music
        ViewIcon.Plant -> R.string.checklist_icon_plant
        ViewIcon.Pets -> R.string.checklist_icon_pets
        ViewIcon.Cleaning -> R.string.checklist_icon_cleaning
        ViewIcon.Journal -> R.string.checklist_icon_journal
        ViewIcon.Savings -> R.string.checklist_icon_savings
        ViewIcon.Sun -> R.string.checklist_icon_sun
        ViewIcon.Star -> R.string.checklist_icon_star
        ViewIcon.Coffee -> R.string.checklist_icon_coffee
        ViewIcon.Calendar -> R.string.checklist_icon_calendar
        ViewIcon.Scale -> R.string.checklist_icon_scale
        ViewIcon.Ruler -> R.string.checklist_icon_ruler
        ViewIcon.Pulse -> R.string.checklist_icon_pulse
        ViewIcon.Mood -> R.string.checklist_icon_mood
        ViewIcon.Book -> R.string.checklist_icon_book
        ViewIcon.Timer -> R.string.checklist_icon_timer
        ViewIcon.Language -> R.string.checklist_icon_language
        ViewIcon.Art -> R.string.checklist_icon_art
        ViewIcon.Dumbbell -> R.string.checklist_icon_dumbbell
        ViewIcon.Bouldering -> R.string.checklist_icon_bouldering
        ViewIcon.Bicycle -> R.string.checklist_icon_bicycle
        ViewIcon.Running -> R.string.checklist_icon_running
        ViewIcon.Hiking -> R.string.checklist_icon_hiking
        ViewIcon.Bolt -> R.string.checklist_icon_bolt
        ViewIcon.Swimming -> R.string.checklist_icon_swimming
        ViewIcon.Ball -> R.string.checklist_icon_ball
        ViewIcon.Tennis -> R.string.checklist_icon_tennis
        ViewIcon.Basketball -> R.string.checklist_icon_basketball
        ViewIcon.Rowing -> R.string.checklist_icon_rowing
        ViewIcon.Skiing -> R.string.checklist_icon_skiing
        ViewIcon.MartialArts -> R.string.checklist_icon_martial_arts
        ViewIcon.Golf -> R.string.checklist_icon_golf
        ViewIcon.Skateboarding -> R.string.checklist_icon_skateboarding
        ViewIcon.Kayaking -> R.string.checklist_icon_kayaking
        ViewIcon.Science -> R.string.checklist_icon_science
        ViewIcon.Drink -> R.string.checklist_icon_drink
        ViewIcon.Smoking -> R.string.checklist_icon_smoking
        ViewIcon.Sweets -> R.string.checklist_icon_sweets
    }

/** How many icons or colors sit in a row. Each catalogue is a multiple of it, so a picker's last row is never short. */
internal const val PickerColumns = 4

/**
 * "Mo's flavor", on the Look part of every view that has one: Plain, and under it the [motifs] the
 * view can wear, three to a row. A checklist can wear every motif; a time view only those Mo has
 * something for while a timer runs. Mo acts a flavor out only for a view he watches, so a view he
 * does not ([watched]) says what would change that. Tags follow from [testTag]:
 * `<testTag>_motif_none` for Plain and `<testTag>_motif_<motif>` for a motif.
 */
@Composable
internal fun MoFlavorPicker(selected: MoMotif?, motifs: List<MoMotif>, testTag: String, watched: Boolean = true, onChoose: (MoMotif?) -> Unit) {
    EditorGroup(stringResource(R.string.checklist_editor_motif)) {
        Column(verticalArrangement = Arrangement.spacedBy(MomentumChoiceTileDefaults.Spacing)) {
            // Plain is Mo as he is, not a motif, so it stands alone above the motifs.
            MomentumChoiceTileRow {
                MomentumChoiceTile(
                    label = stringResource(R.string.checklist_motif_none),
                    selected = selected == null,
                    onClick = { onChoose(null) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    testTag = "${testTag}_motif_none",
                )
            }
            MomentumTileGrid(motifs, columns = MotifColumns) { motif, tileModifier ->
                MomentumChoiceTile(
                    label = stringResource(motif.labelRes),
                    selected = motif == selected,
                    onClick = { onChoose(motif) },
                    modifier = tileModifier,
                    testTag = "${testTag}_motif_${motif.storageId}",
                )
            }
        }
        // Said only while it is true, under the tiles and in room that is always kept, so nothing moves for it.
        val unwatched = stringResource(R.string.checklist_editor_motif_unwatched)
        ReservedMessage(
            text = unwatched.takeUnless { watched },
            testTag = "${testTag}_motif_unwatched",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            reserving = listOf(unwatched),
        )
    }
}

/** Mo's six motifs are two full rows of three; Plain, which is no motif, has its own row above. */
internal const val MotifColumns = 3

/**
 * Each vivid hue beside its deep tone, so a family reads as one pair. Red is kept for warnings and
 * not offered; a checklist that already wears it keeps it.
 */
internal val PickerHues: List<ViewHue> = ViewHue.entries.filterNot { it.isDeep || it == ViewHue.Red }
    .zip(ViewHue.entries.filter { it.isDeep })
    .flatMap { (vivid, deep) -> listOf(vivid, deep) }

@get:StringRes
private val ViewHue.labelRes: Int
    get() = when (this) {
        ViewHue.Orange -> R.string.checklist_hue_orange
        ViewHue.Yellow -> R.string.checklist_hue_yellow
        ViewHue.Green -> R.string.checklist_hue_green
        ViewHue.Teal -> R.string.checklist_hue_teal
        ViewHue.Sky -> R.string.checklist_hue_sky
        ViewHue.Indigo -> R.string.checklist_hue_indigo
        ViewHue.Purple -> R.string.checklist_hue_purple
        ViewHue.Pink -> R.string.checklist_hue_pink
        ViewHue.Red -> R.string.checklist_hue_red
        ViewHue.Rust -> R.string.checklist_hue_rust
        ViewHue.Olive -> R.string.checklist_hue_olive
        ViewHue.Forest -> R.string.checklist_hue_forest
        ViewHue.Pine -> R.string.checklist_hue_pine
        ViewHue.Navy -> R.string.checklist_hue_navy
        ViewHue.Violet -> R.string.checklist_hue_violet
        ViewHue.Plum -> R.string.checklist_hue_plum
        ViewHue.Berry -> R.string.checklist_hue_berry
    }

@get:StringRes
private val MoMotif.labelRes: Int
    get() = when (this) {
        MoMotif.Pills -> R.string.checklist_motif_pills
        MoMotif.Teeth -> R.string.checklist_motif_teeth
        MoMotif.Food -> R.string.checklist_motif_food
        MoMotif.Running -> R.string.checklist_motif_running
        MoMotif.Book -> R.string.checklist_motif_reading
        MoMotif.Scale -> R.string.checklist_motif_scale
    }
