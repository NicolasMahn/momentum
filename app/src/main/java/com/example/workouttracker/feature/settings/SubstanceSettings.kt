package com.example.workouttracker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.MomentumBarAction
import com.example.workouttracker.core.ui.rememberBarExplanation
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumCheckboxRow
import com.example.workouttracker.core.ui.MomentumChoiceTile
import com.example.workouttracker.core.ui.MomentumChoiceTileRow
import com.example.workouttracker.core.ui.MomentumDropdown
import com.example.workouttracker.core.ui.MomentumRemoveButton
import com.example.workouttracker.core.ui.MomentumTextField
import com.example.workouttracker.core.ui.MomentumTileGrid
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.domain.ViewLook
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.freshId
import com.example.workouttracker.domain.checklist.on
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.substance.OwnSubstance
import com.example.workouttracker.domain.substance.Substance
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceCheckIn
import com.example.workouttracker.domain.substance.SubstanceEdit
import com.example.workouttracker.domain.substance.SubstanceGoal
import com.example.workouttracker.domain.substance.SubstanceKind
import com.example.workouttracker.domain.substance.SubstanceMotifs
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.substance.applyEdit
import com.example.workouttracker.domain.substance.applyingEdits
import com.example.workouttracker.feature.substances.label
import com.example.workouttracker.feature.substances.nameRes
import com.example.workouttracker.feature.substances.SubstancesOwnWord
import com.example.workouttracker.feature.substances.presentation
import com.example.workouttracker.feature.substances.viewIdentity
import com.example.workouttracker.feature.substances.viewLabel
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import java.time.LocalDate
import java.time.LocalTime

/**
 * The Substances page as a draft: the stored choice as its baseline, and the ordered edits made on
 * the page, as a checklist's page keeps its edits ([SettingsDraft]). What the page shows is the
 * edits applied to the latest stored choice on [today], so a change stored meanwhile stays under
 * them and a dated change is dated by the day it is shown on; Save hands the edits over, and the
 * store applies them again on the habit day of that moment. So unsaved edits survive 04:00, every
 * dated change is dated by the save's day, and an edit the latest choice refuses, such as a rename
 * of a substance removed meanwhile, is left out of both. The page is dirty while what it shows
 * differs from what is stored, so a change taken back before Save leaves nothing.
 *
 * The page's fields belong to it: a name field left blank keeps the last name and holds Save back
 * ([blankNames]), and a name typed for a new substance but not added is work not to be lost
 * ([pendingText]). Kept across process death with the page.
 */
internal class SubstanceSettingsDraft(
    stored: SubstanceSettings,
    today: LocalDate,
    edits: List<SubstanceEdit> = emptyList(),
    blankNames: Set<String> = emptySet(),
    pendingText: Boolean = false,
) : SettingsDraft<SubstanceSettings, SubstanceEdit>(stored, edits, blankNames, if (pendingText) setOf(NewSubstanceField) else emptySet()), ViewLookEdits {
    var today: LocalDate by mutableStateOf(today)
        private set

    /** Whether the field for a new substance holds a name not yet added. */
    val pendingText: Boolean get() = typedFields.isNotEmpty()

    /** The choice as the page shows it: the edits over the stored choice, applied on today. */
    val edited: SubstanceSettings get() = stored.applyingEdits(edits, today)

    /** Edits that take each other back leave nothing to save. */
    override val isDirty: Boolean get() = edited != stored

    /** A name is the view's own ([SubstancesNameKey]), or an own substance's by its ID. */
    override fun SubstanceEdit.names(key: String): Boolean = when (this) {
        is SubstanceEdit.Rename -> key == SubstancesNameKey
        is SubstanceEdit.AddOwn -> id == key
        is SubstanceEdit.RenameOwn -> id == key
        else -> false
    }

    override fun SubstanceEdit.removes(): String? = (this as? SubstanceEdit.RemoveOwn)?.id

    /** Whether the draft takes [edit], which it refuses as the stored choice would. */
    fun edit(edit: SubstanceEdit): Boolean = keep(edit) { edited.applyEdit(edit, today) }

    override fun rename(label: String): Boolean = edit(SubstanceEdit.Rename(label))

    override fun setIcon(icon: ViewIcon): Boolean = edit(SubstanceEdit.SetIcon(icon))

    override fun setHue(hue: ViewHue): Boolean = edit(SubstanceEdit.SetHue(hue))

    fun setMotif(motif: MoMotif?): Boolean = edit(SubstanceEdit.SetMotif(motif))

    /** Whether own substance [id] can go: not while a stance before today names it, which the choice refuses. */
    fun canRemove(id: String): Boolean = runCatching { edited.applyEdit(SubstanceEdit.RemoveOwn(id), today) }.isSuccess

    /** A new own substance, tracked from the start: that is why it was named. */
    fun add(label: String, unit: SubstanceAmountUnit): String {
        val id = freshId("sub", edited.all.map { it.id })
        edit(SubstanceEdit.AddOwn(id, label, unit))
        return id
    }

    /** The stored choice as it is now, and the day it is; the edits are shown over both from here on. */
    fun follow(current: SubstanceSettings, today: LocalDate) {
        stored = current
        this.today = today
    }

    /** The field for a new substance holds [text]. */
    fun typing(text: String) = typing(NewSubstanceField, text)

    companion object {
        val Saver: Saver<SubstanceSettingsDraft, Any> = listSaver(
            save = {
                listOf(
                    AppStateJsonCodec.encodeSubstances(it.stored),
                    AppStateJsonCodec.encodeSubstanceEdits(it.edits),
                    ArrayList(it.blankNames),
                    it.pendingText,
                    it.today.toString(),
                )
            },
            restore = {
                SubstanceSettingsDraft(
                    stored = AppStateJsonCodec.decodeSubstances(it[0] as String),
                    edits = AppStateJsonCodec.decodeSubstanceEdits(it[1] as String),
                    blankNames = (it[2] as List<*>).mapTo(LinkedHashSet()) { id -> id as String },
                    pendingText = it[3] as Boolean,
                    today = LocalDate.parse(it[4] as String),
                )
            },
        )
    }
}

/** The one field something new is named in on the Substances page, by which its draft knows text not yet added. */
internal const val NewSubstanceField = "substance_settings_new_name"

/** What the draft knows the view's own name field by. No substance can have it: an ID holds no space. */
internal const val SubstancesNameKey = "the view"

/**
 * Substances' look in the shape the shared Look part reads ([LoggedViewLook]). [label] is the name
 * the page shows, the stored one or the app's own word.
 */
private class SubstancesLook(settings: SubstanceSettings, override val label: String) : ViewLook {
    override val id: String = SubstancesNameKey
    override val icon: ViewIcon = settings.icon
    override val hue: ViewHue = settings.hue
}

/**
 * Which substances Momentum tracks and what it asks of the day, in the four parts every view's
 * page has ([ViewSettingsFrame]). Look is the one every view has ([LoggedViewLook]): the view's
 * name, which is the app's own word until a person types another and again once they type that
 * word back, its icon, its color and Mo's flavor. The second, under the app's word for
 * substances, holds the catalogue as tiles, each tracked or not by a tap, the person's own after
 * it, each renamable and removable while nothing was recorded of it, and whether each tracked one
 * is stayed away from or just tracked; Reminders holds the check-in, and Streak how Substances
 * counts toward the streak.
 * Changes stay on the page until Save hands the edits made to [onSave], which stores them on the
 * day it runs; saved, the page returns to the list through [onClose], unless a new substance's
 * name was typed and not added, which leaving would lose. [recorded] names the substances that
 * have records, which stay. [reminderRow] is the
 * check-in's switch with the permissions it may need, which Settings owns. Whoever owns the
 * navigation is told through [onUnsavedWorkChange] whether the page holds work that would be lost,
 * and asks to leave through [leaveRequested]; the page asks the person and answers through
 * [onLeaveAnswered]. The line over Save says what went wrong, here or in Settings ([message]);
 * Substances is built in, so its Delete is disabled and the line says to switch it off instead.
 */
@Composable
internal fun SubstanceSettingsPage(
    substances: SubstanceSettings,
    recorded: Set<String>,
    today: LocalDate,
    bedtime: Bedtime,
    onSave: (List<SubstanceEdit>) -> Boolean,
    reminderRow: @Composable (SubstanceCheckIn, (Boolean) -> Unit) -> Unit,
    onClose: () -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    /** Saving switches Substances on, as in the first start, so Save is offered even with nothing edited. */
    switchesOn: Boolean = false,
    /** The part the page opens on: Look, as every view's does, unless a host shows another first, as Settings does while nothing is tracked. */
    opensOn: ChecklistPage = ChecklistPage.Look,
) {
    val draft = rememberSaveable(saver = SubstanceSettingsDraft.Saver) { SubstanceSettingsDraft(substances, today) }
    draft.follow(substances, today)
    val edited = remember(draft.stored, draft.edits, today) { draft.edited }
    var error by remember { mutableStateOf<Int?>(null) }
    var part by rememberSaveable { mutableStateOf(opensOn) }
    val ownWord = SubstancesOwnWord.resolve()
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val typedText = stringResource(R.string.settings_typed_text_pending)
    fun edit(change: SubstanceEdit): Boolean = draft.edit(change)

    ViewSettingsFrame(
        testTag = "substance_settings",
        pageKey = SettingsPage.Substances.key,
        colors = edited.viewIdentity().colors,
        parts = LoggedViewParts,
        selected = part,
        onSelect = { part = it },
        partTestTag = { "substance_settings_tab_${if (it == ChecklistPage.Items) "substances" else it.tagName}" },
        unsavedWork = draft.hasUnsavedWork,
        discard = DiscardQuestion(
            title = stringResource(R.string.checklist_editor_discard_title),
            message = stringResource(R.string.checklist_editor_discard_message, draft.stored.viewLabel().resolve()),
        ),
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        error = (error?.let { stringResource(it) } ?: message)?.let { BarLine(it, "substance_settings_error") },
        delete = ViewDelete.BuiltIn,
        buttons = {
            // Saved, yet the page stays while typed text waits to be added; it says so.
            val explain = rememberBarExplanation()
            MomentumBarAction(
                text = stringResource(R.string.substance_settings_save),
                onClick = {
                    if (onSave(draft.edits)) {
                        draft.saved()
                        error = null
                        if (!draft.hasUnsavedWork) onClose() else explain()
                    } else {
                        error = R.string.substance_settings_error_save_failed
                    }
                },
                ready = draft.canSave || (switchesOn && !draft.heldBack),
                testTag = "substance_settings_save",
            )
        },
        modifier = modifier,
        note = stringResource(R.string.substance_settings_note),
        hint = typedText.takeIf { draft.holdsOnlyTypedText }?.let { BarLine(it, "substance_settings_hint") },
        // The kind's own part wears the app's word for substances, in its label and its tag: "Items" is
        // not what substances are, and a view renamed to "Alcohol" still lists substances there.
        partLabel = { stringResource(if (it == ChecklistPage.Items) R.string.feature_substances else it.label) },
        dialogs = {
            if (pickingTime) {
                val at = edited.checkIn.at
                SettingsTimePickerDialog(
                    initialTime = (at as? CheckpointTime.Fixed)?.time?.let { ReminderTime(it.hour, it.minute) } ?: ReminderTime(at.on(bedtime).hour, at.on(bedtime).minute),
                    onDismiss = { pickingTime = false },
                    onConfirm = { time ->
                        pickingTime = false
                        edit(SubstanceEdit.CheckInAt(CheckpointTime.Fixed(LocalTime.of(time.hour, time.minute))))
                    },
                )
            }
        },
    ) { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when (shown) {
                ChecklistPage.Items -> {
                    // Read from what is stored, so a tile switched off here stays to be switched on again.
                    val offered = SubstanceKind.entries.filter { it in SubstanceKind.Offered || substances.isTracked(it.id) || it.id in recorded }
                    MomentumTileGrid(offered, columns = CatalogueColumns) { kind, tileModifier ->
                        TrackTile(edited, kind, tileModifier) { edit(SubstanceEdit.Track(kind.id, !edited.isTracked(kind.id))) }
                    }
                    edited.own.forEach { own ->
                        key(own.id) {
                            OwnSubstanceEditor(own = own, settings = edited, recorded = own.id in recorded, draft = draft, onEdit = ::edit)
                        }
                    }
                    AddOwnSubstance(draft)
                    edited.trackedSubstances.forEach { substance ->
                        key(substance.id) { StanceEditor(substance, edited.isStayingAway(substance.id)) { edit(SubstanceEdit.StayAway(substance.id, it)) } }
                    }
                }

                ChecklistPage.Reminders -> CheckInEditor(
                    checkIn = edited.checkIn,
                    bedtime = bedtime,
                    reminderRow = reminderRow,
                    onRemind = { edit(SubstanceEdit.Remind(it)) },
                    onChange = { edit(SubstanceEdit.CheckInAt(it)) },
                    onPickTime = { pickingTime = true },
                )

                ChecklistPage.Streak -> StreakEditor(edited, today, onEdit = ::edit)

                ChecklistPage.Look -> LoggedViewLook(
                    view = SubstancesLook(edited, edited.viewLabel().resolve()),
                    edits = draft,
                    testTag = "substance_settings",
                    icons = SubstanceIcons,
                    end = {
                        MoFlavorPicker(
                            selected = edited.mo.motif,
                            // A flavor stored from before the choice was limited stays on show, so the page says what is stored.
                            motifs = (SubstanceMotifs + listOfNotNull(substances.mo.motif)).distinct(),
                            testTag = "substance_settings",
                            watched = edited.mo.enabled || edited.countsTowardStreakOn(today),
                        ) { draft.setMotif(it) }
                    },
                ) {
                    NameField(
                        name = edited.viewLabel().resolve(),
                        key = SubstancesNameKey,
                        label = stringResource(R.string.checklist_editor_name),
                        testTag = "substance_settings_name",
                        onName = { name ->
                            draft.named(SubstancesNameKey, name)
                            // The app's own word typed back is no name of one's own: the view follows the app's language again.
                            name?.let { edit(SubstanceEdit.Rename(it.takeUnless { typed -> typed == ownWord })) }
                        },
                        settled = draft.nameStored(SubstancesNameKey),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackTile(settings: SubstanceSettings, substance: Substance, modifier: Modifier, onToggle: () -> Unit) {
    val presentation = settings.presentation(substance)
    MomentumChoiceTile(
        label = substance.label().resolve(),
        selected = settings.isTracked(substance.id),
        onClick = onToggle,
        modifier = modifier,
        icon = presentation.icon,
        // The tile shows the hue the substance will wear in the calendar.
        accent = presentation.colors.accent,
        role = Role.Checkbox,
        testTag = "substance_settings_track_${substance.id}",
    )
}

/**
 * One own substance: its tile, its name, and the way out while nothing was recorded of it and no
 * day before today was judged against it.
 */
@Composable
private fun OwnSubstanceEditor(
    own: OwnSubstance,
    settings: SubstanceSettings,
    recorded: Boolean,
    draft: SubstanceSettingsDraft,
    onEdit: (SubstanceEdit) -> Boolean,
) {
    val stayedAwayBefore = !recorded && !draft.canRemove(own.id)
    Column(
        modifier = Modifier.testTag("substance_settings_own_${own.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrackTile(settings, own, Modifier.weight(1f)) { onEdit(SubstanceEdit.Track(own.id, !settings.isTracked(own.id))) }
            // A record is a fact that outlives the choice, so a substance with records stays; so
            // does one an earlier stance names, which judged the days it covered.
            MomentumRemoveButton(
                description = stringResource(R.string.checklist_editor_remove, own.label),
                onRemove = { onEdit(SubstanceEdit.RemoveOwn(own.id)) },
                testTag = "substance_settings_remove_${own.id}",
                variant = MomentumButtonVariant.Quiet,
                enabled = !recorded && !stayedAwayBefore,
            )
        }
        NameField(
            name = own.label,
            key = own.id,
            label = stringResource(R.string.checklist_editor_name),
            testTag = "substance_settings_name_${own.id}",
            onName = { name ->
                draft.named(own.id, name)
                name?.let { onEdit(SubstanceEdit.RenameOwn(own.id, it)) }
            },
            settled = draft.nameStored(own.id),
        )
        NoteText(
            text = stringResource(R.string.substance_settings_counted_in, stringResource(own.unit.nameRes)),
            testTag = "substance_settings_unit_${own.id}",
        )
        if (recorded) {
            NoteText(text = stringResource(R.string.substance_settings_keeps_records), testTag = "substance_settings_keep_${own.id}")
        } else if (stayedAwayBefore) {
            NoteText(text = stringResource(R.string.substance_settings_keeps_stance), testTag = "substance_settings_keep_${own.id}")
        }
    }
}

/** Names a new substance and the unit its amounts are counted in; Add puts it after the person's own, tracked. */
@Composable
private fun AddOwnSubstance(draft: SubstanceSettingsDraft) {
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(SubstanceAmountUnit.USES) }
    val trimmed = name.trim()
    fun type(text: String) {
        name = text
        draft.typing(text)
    }
    fun add() {
        if (trimmed.isEmpty()) return
        draft.add(trimmed, unit)
        type("")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MomentumTextField(
            value = name,
            onValueChange = ::type,
            label = stringResource(R.string.substance_settings_new),
            error = null,
            modifier = Modifier.fillMaxWidth(),
            testTag = NewSubstanceField,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MomentumDropdown(
                label = stringResource(R.string.substance_settings_unit),
                options = SubstanceAmountUnit.entries,
                selected = unit,
                onSelect = { unit = it },
                optionLabel = { stringResource(it.nameRes) },
                modifier = Modifier.weight(1f),
                testTag = "substance_settings_new_unit",
                optionTestTag = { "substance_settings_new_unit_${it.storageId}" },
            )
            MomentumButton(
                text = stringResource(R.string.checklist_editor_add),
                onClick = ::add,
                enabled = trimmed.isNotEmpty(),
                variant = MomentumButtonVariant.Secondary,
                testTag = "substance_settings_add",
            )
        }
    }
}

/**
 * Whether one tracked substance is stayed away from, so its stat counts days without and a use of
 * it is a gap under the staying-away goal, or just tracked, so its stat only says when it was last
 * logged and logging it never costs anything.
 */
@Composable
private fun StanceEditor(substance: Substance, stayingAway: Boolean, onStayAway: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.testTag("substance_settings_stance_${substance.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = substance.label().resolve(), style = MaterialTheme.typography.bodyLarge)
        MomentumChoiceTileRow {
            MomentumChoiceTile(
                label = stringResource(R.string.substance_settings_staying_away),
                selected = stayingAway,
                onClick = { if (!stayingAway) onStayAway(true) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTag = "substance_settings_staying_away_${substance.id}",
            )
            MomentumChoiceTile(
                label = stringResource(R.string.substance_settings_just_tracking),
                selected = !stayingAway,
                onClick = { if (stayingAway) onStayAway(false) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTag = "substance_settings_just_tracking_${substance.id}",
            )
        }
    }
}

/** The check-in: its switch, with what Settings has to say about it, and when it asks. */
@Composable
private fun CheckInEditor(
    checkIn: SubstanceCheckIn,
    bedtime: Bedtime,
    reminderRow: @Composable (SubstanceCheckIn, (Boolean) -> Unit) -> Unit,
    onRemind: (Boolean) -> Unit,
    onChange: (CheckpointTime) -> Unit,
    onPickTime: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag("substance_settings_check_in"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        reminderRow(checkIn, onRemind)
        TimeEditor(
            at = checkIn.at,
            today = checkIn.at.on(bedtime),
            testTag = "substance_settings_check_in",
            onChange = { onChange(it); true },
            onPickTime = onPickTime,
        )
    }
}

/**
 * How Substances counts toward the streak: Off, Logging or Staying away, with the line under the
 * tiles saying plainly what keeps a day under the choice. Staying away adds "Only log uses", the
 * dated exceptions-only setting as it is stored: checked, a day with nothing logged counts, so no
 * check-in is needed on a day without a use, as a checklist's item that logs only misses needs no
 * tick. It also says while nothing is chosen to stay away from, since such a day asks nothing.
 * While it counts, the bank's two steppers a checklist has and what an away day asks follow.
 */
@Composable
private fun StreakEditor(settings: SubstanceSettings, today: LocalDate, onEdit: (SubstanceEdit) -> Boolean) {
    val goal = settings.goalOn(today)
    val exceptionsOnly = settings.exceptionsOnly.on(today)
    Column(
        modifier = Modifier.testTag("substance_settings_streak"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.checklist_settings_rewards), style = MaterialTheme.typography.bodyLarge)
        StreakNotes("substance_settings")
        MomentumChoiceTileRow {
            listOf(
                Triple(null, R.string.substance_settings_goal_off, "off"),
                Triple(SubstanceGoal.Logging, R.string.substance_settings_goal_logging, "logging"),
                Triple(SubstanceGoal.StayingAway, R.string.substance_settings_goal_staying_away, "staying_away"),
            ).forEach { (option, text, tag) ->
                MomentumChoiceTile(
                    label = stringResource(text),
                    selected = goal == option,
                    onClick = { if (goal != option) onEdit(SubstanceEdit.SetGoal(option)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    testTag = "substance_settings_goal_$tag",
                )
            }
        }
        // Each goal explains itself at the height of the longest explanation, so choosing a tile
        // never moves the controls under it.
        val explanations = listOf(
            R.string.substance_settings_goal_off_note,
            R.string.substance_settings_streak_note,
            R.string.substance_settings_staying_away_note,
            R.string.substance_settings_staying_away_check_in_note,
        ).map { stringResource(it) }
        ReservedMessage(
            text = when (goal) {
                null -> explanations[0]
                SubstanceGoal.Logging -> explanations[1]
                SubstanceGoal.StayingAway -> if (exceptionsOnly) explanations[2] else explanations[3]
            },
            testTag = "substance_settings_streak_note",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            reserving = explanations,
        )
        if (goal == SubstanceGoal.StayingAway) {
            val nothingStayedAway = stringResource(R.string.substance_settings_nothing_stayed_away)
            ReservedMessage(
                text = nothingStayedAway.takeIf { settings.stayingAway.on(today).isEmpty() },
                testTag = "substance_settings_nothing_stayed_away",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                reserving = listOf(nothingStayedAway),
            )
            MomentumCheckboxRow(
                checked = exceptionsOnly,
                onCheckedChange = { onEdit(SubstanceEdit.SetExceptionsOnly(it)) },
                testTag = "substance_settings_only_uses",
            ) {
                Text(text = stringResource(R.string.substance_settings_only_uses), style = MaterialTheme.typography.bodyLarge)
            }
        }
        val rewards = settings.rewards
        if (goal != null && rewards != null) {
            RewardRuleSteppers(rewards, today, testTag = "substance_settings") { maxPasses, everyKeptDays ->
                onEdit(SubstanceEdit.SetRewardRules(maxPasses, everyKeptDays))
            }
            val rule = settings.whenAway.on(today)
            Text(text = stringResource(R.string.checklist_editor_away), style = MaterialTheme.typography.bodyLarge)
            MomentumChoiceTileRow {
                listOf(
                    Triple(WhenAway.Due, R.string.checklist_editor_away_due, "due"),
                    Triple(WhenAway.Excused, R.string.checklist_editor_away_excused, "excused"),
                ).forEach { (option, text, tag) ->
                    MomentumChoiceTile(
                        label = stringResource(text),
                        selected = rule == option,
                        onClick = { if (rule != option) onEdit(SubstanceEdit.SetWhenAway(option)) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        testTag = "substance_settings_away_$tag",
                    )
                }
            }
        }
    }
}

/**
 * Two to a row: a quarter-width tile cannot hold "Benzodiazepines" or "Nikotinbeutel", and sixteen
 * is eight full rows of two.
 */
private const val CatalogueColumns = 2
