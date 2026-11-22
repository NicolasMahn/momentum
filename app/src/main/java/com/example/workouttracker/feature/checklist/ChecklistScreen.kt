package com.example.workouttracker.feature.checklist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.format.dateText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.time.nowMillis
import com.example.workouttracker.core.ui.CompletionButton
import com.example.workouttracker.core.ui.DiscardChangesDialog
import com.example.workouttracker.core.ui.FollowTodayEffect
import com.example.workouttracker.core.ui.GuardUnsavedWork
import com.example.workouttracker.core.ui.LeaveGuard
import com.example.workouttracker.core.ui.MomentumCard
import com.example.workouttracker.core.ui.MomentumRemoveButton
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.feature.mo.PassTokenCount
import com.example.workouttracker.feature.tracker.CheckpointTokens
import com.example.workouttracker.core.ui.MomentumCheckboxLook
import com.example.workouttracker.core.ui.MomentumCheckboxRow
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.core.ui.SelectedDayLine
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.core.time.isValidDateKey
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.DayRecord
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.WeekItemProgress
import com.example.workouttracker.domain.checklist.removeChecklistDay
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.checklist.recordOn
import com.example.workouttracker.domain.checklist.setCheckpointRecord
import com.example.workouttracker.domain.checklist.setDayRecord
import com.example.workouttracker.domain.checklist.tickOf
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.settings.ViewRef
import com.example.workouttracker.core.ui.LocalDayCovers

/**
 * One screen for every checklist: pick a day in the calendar, tick what was done, save. The
 * configuration decides the items, their labels and the look; the words around them are shared.
 * The selected day is named above the items only while it is not the day the screen opens on.
 * A checklist saves each checkpoint independently, preserving the other checkpoints when one changes.
 * An item due a number of times a week is offered every day with where its week stands, which
 * [daysOff] helps read. An item that logs only misses shows an assumed check on a day it is due,
 * which a tap turns into a miss, a cross in its box, and another takes back; a tick recorded
 * before the switch stays a recorded tick. [testTagPrefix] keeps the tags a checklist's tests
 * address stable.
 * [initialDateKey] is a day a reminder asked about, selected once on arrival, which
 * [onInitialDateConsumed] acknowledges.
 * With several checkpoints, [checkpointTokens] puts the view's token and the number left beside
 * each checkpoint's name, on the day the screen opens on: the one day a token can still be said
 * ahead for. It is the switch that sets a token aside for that checkpoint where one can be, and
 * it stays in its place, not pressable, where none can.
 */
@Composable
internal fun ChecklistScreen(
    state: AppState,
    checklist: ChecklistConfiguration,
    onStateChange: (AppState) -> Boolean,
    todayKey: String,
    daysOff: HolidaySchedule,
    testTagPrefix: String = checklist.testTagPrefix,
    initialDateKey: String? = null,
    onInitialDateConsumed: () -> Unit = {},
    checkpointTokens: CheckpointTokens? = null,
    leave: LeaveGuard = LeaveGuard.None,
) {
    // A draft belongs to one checklist: opening another one, even at the same place, starts afresh.
    key(checklist.id) { ChecklistForm(state, checklist, onStateChange, todayKey, daysOff, testTagPrefix, initialDateKey, onInitialDateConsumed, checkpointTokens, leave) }
}

@Composable
private fun ChecklistForm(
    state: AppState,
    checklist: ChecklistConfiguration,
    onStateChange: (AppState) -> Boolean,
    todayKey: String,
    daysOff: HolidaySchedule,
    testTagPrefix: String,
    initialDateKey: String?,
    onInitialDateConsumed: () -> Unit,
    checkpointTokens: CheckpointTokens?,
    leave: LeaveGuard,
) {
    val presentation = checklist.presentation()
    // The day picked survives the activity being recreated, as a correction must stay on its day.
    var selectedDateKey by rememberSaveable { mutableStateOf(checklist.openingDayKey(todayKey)) }
    val persistedSelection = checklistSelection(state, checklist, selectedDateKey, daysOff)
    var selection by remember(
        selectedDateKey,
        persistedSelection.progress,
        persistedSelection.persisted,
        persistedSelection.hasPersistedRecord,
    ) { mutableStateOf(persistedSelection) }
    var saveError by remember(selectedDateKey, persistedSelection) { mutableStateOf(false) }
    var deleteError by remember(selectedDateKey, persistedSelection) { mutableStateOf(false) }
    val history = remember(checklist, state.checklistDays, todayKey, daysOff) {
        checklistHistory(state, checklist, todayKey, daysOff)
    }
    // A checkpoint's unsaved ticks live in its own form, keyed on the selected date. Moving the date
    // replaces them, so the screen only follows a new day while no form has changes.
    var unsavedCheckpoints by remember { mutableStateOf(emptySet<String>()) }
    val severalCheckpoints = checklist.checkpoints.size > 1
    // A notification's day moves an untouched screen, as the new day at 04:00 does below. A screen
    // holding unsaved ticks stays as it is; the request is answered either way.
    LaunchedEffect(initialDateKey) {
        if (initialDateKey != null) {
            val untouched = !selection.hasChanges && unsavedCheckpoints.isEmpty()
            if (isValidDateKey(initialDateKey) && untouched) selectedDateKey = initialDateKey
            onInitialDateConsumed()
        }
    }
    FollowTodayEffect(
        todayKey = todayKey,
        canFollow = { previousTodayKey ->
            !selection.hasChanges &&
                unsavedCheckpoints.isEmpty() &&
                selectedDateKey == checklist.openingDayKey(previousTodayKey)
        },
        onFollow = { selectedDateKey = checklist.openingDayKey(it) },
    )
    val unsaved = selection.hasChanges || unsavedCheckpoints.isNotEmpty()
    // Another day replaces the ticks not saved yet, so a tap on one asks first while there are any.
    var pendingDateKey by remember { mutableStateOf<String?>(null) }

    fun openDay(dateKey: String) {
        selectedDateKey = dateKey
        pendingDateKey = null
        saveError = false
        deleteError = false
    }

    ViewTheme(presentation.colors) {
        GuardUnsavedWork(unsaved, leave, testTag = "${testTagPrefix}_discard")
        pendingDateKey?.let { dateKey ->
            DiscardChangesDialog(testTag = "${testTagPrefix}_discard", onDiscard = { openDay(dateKey) }, onKeep = { pendingDateKey = null })
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SelectedDayLine(
                    selectedDateKey = selectedDateKey,
                    openingDateKey = checklist.openingDayKey(todayKey),
                    testTag = "${testTagPrefix}_selected_day",
                )

                // Every item can be out of season or ended on the selected day: then there is
                // nothing to tick, and a form with a dead button would only ask why.
                val forms = if (severalCheckpoints) checkpointSelections(state, checklist, selectedDateKey, daysOff) else emptyList()
                val nothingToTick = if (severalCheckpoints) forms.isEmpty() else selection.recordable.checkpoints.isEmpty()
                if (nothingToTick) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.checklist_nothing_due),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("${testTagPrefix}_nothing_due"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // A record can outlive what is recordable, as when an item's season moved
                        // after it was ticked: it is still this day's, and still removable here.
                        RemoveDayButton(
                            dateKey = selectedDateKey,
                            hasRecord = !state.recordOn(checklist.id, selectedDateKey).isEmpty,
                            testTagPrefix = testTagPrefix,
                            onRemove = { deleteError = !onStateChange(removeChecklistDay(state, checklist.id, selectedDateKey)) },
                        )
                    }
                    ReservedMessage(
                        text = if (deleteError) stringResource(R.string.checklist_error_remove_failed) else null,
                        testTag = "${testTagPrefix}_delete_error",
                    )
                } else if (severalCheckpoints) {
                    // Without boxes the checkpoints need real air between them to stay distinct.
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        forms.forEach { persisted ->
                            key(persisted.checkpoint.id) {
                                CheckpointForm(
                                    persisted = persisted,
                                    weekItems = selection.progress.weekItems,
                                    dateKey = selectedDateKey,
                                    onSave = { selected ->
                                        onStateChange(
                                            setCheckpointRecord(
                                                state = state,
                                                checklist = checklist,
                                                dateKey = selectedDateKey,
                                                checkpointId = persisted.checkpoint.id,
                                                selected = selected,
                                                timestampMillis = nowMillis(),
                                            ),
                                        )
                                    },
                                    icon = presentation.icon,
                                    token = checkpointTokens?.takeIf { selectedDateKey == checklist.openingDayKey(todayKey) }?.let { tokens ->
                                        val id = persisted.checkpoint.id
                                        CheckpointToken(
                                            identity = presentation,
                                            viewLabel = checklist.label,
                                            left = tokens.left,
                                            offered = id in tokens.offered,
                                            setAside = id in tokens.setAside,
                                            onChange = { chosen -> tokens.onChange(id, chosen) },
                                        )
                                    },
                                    testTagPrefix = "${testTagPrefix}_${persisted.checkpoint.id}",
                                    onUnsavedChange = { unsaved ->
                                        val id = persisted.checkpoint.id
                                        unsavedCheckpoints = if (unsaved) unsavedCheckpoints + id else unsavedCheckpoints - id
                                    },
                                )
                            }
                        }
                    }
                } else {
                    selection.recordable.checkpoints.forEach { (checkpoint, items) ->
                        items.forEach { item ->
                            val tick = tickOf(checkpoint.id, item.id)
                            ChecklistItemRow(
                                item = item,
                                week = selection.progress.weekItems.of(item),
                                mark = selection.markOf(tick),
                                testTag = "${testTagPrefix}_status_${item.id}",
                                onToggle = {
                                    selection = selection.toggle(tick)
                                    saveError = false
                                },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        key(selectedDateKey) {
                            // A save that returned true is the completion; the persisted logs may
                            // take a frame to come back, and until then this is what "taken" means.
                            var saved by remember { mutableStateOf<DayRecord?>(null) }
                            var celebrationSignal by remember { mutableIntStateOf(0) }
                            val completed = !selection.selected.isEmpty && (
                                (selection.hasPersistedRecord && !selection.hasChanges) ||
                                    selection.selected == saved
                                )

                            CompletionButton(
                                actionLabel = when {
                                    selection.selected.isEmpty && selection.hasPersistedRecord ->
                                        stringResource(R.string.checklist_remove_all)
                                    selection.hasPersistedRecord -> stringResource(R.string.checklist_save_changes)
                                    // A miss is saved, never "done".
                                    selection.selected.misses.isNotEmpty() -> stringResource(R.string.checklist_save)
                                    selection.dueTicks.isNotEmpty() && selection.selected.ticks == selection.dueTicks ->
                                        stringResource(R.string.checklist_mark_all_done)
                                    selection.selected.ticks.size == 1 -> {
                                        val item = selection.recordable.checkpoints.firstNotNullOf { (checkpoint, items) ->
                                            items.firstOrNull { tickOf(checkpoint.id, it.id) in selection.selected.ticks }
                                        }
                                        stringResource(R.string.checklist_mark_one_done, item.label)
                                    }
                                    selection.selected.isEmpty -> stringResource(R.string.checklist_select)
                                    else -> stringResource(R.string.checklist_save)
                                },
                                completedLabel = stringResource(R.string.checklist_saved),
                                completed = completed,
                                onClick = {
                                    // Only a tick added is celebrated: saving nothing for a day that
                                    // has a record is a removal, and a miss is recorded, never earned.
                                    val celebrates = selection.addsATick
                                    val wasSaved = onStateChange(
                                        setDayRecord(
                                            state = state,
                                            checklist = checklist,
                                            dateKey = selectedDateKey,
                                            selected = selection.selected,
                                            timestampMillis = nowMillis(),
                                        ),
                                    )
                                    saveError = !wasSaved
                                    if (wasSaved && !selection.selected.isEmpty) saved = selection.selected
                                    if (wasSaved && celebrates) celebrationSignal += 1
                                },
                                icon = presentation.icon,
                                testTag = "${testTagPrefix}_save",
                                modifier = Modifier.weight(1f),
                                enabled = (!selection.hasPersistedRecord || selection.hasChanges) &&
                                    (!selection.selected.isEmpty || selection.hasPersistedRecord),
                                celebrationSignal = celebrationSignal,
                            )
                        }

                        RemoveDayButton(
                            dateKey = selectedDateKey,
                            hasRecord = selection.hasPersistedRecord,
                            testTagPrefix = testTagPrefix,
                            onRemove = { deleteError = !onStateChange(removeChecklistDay(state, checklist.id, selectedDateKey)) },
                        )
                    }

                    ReservedMessage(
                        text = when {
                            deleteError -> stringResource(R.string.checklist_error_remove_failed)
                            saveError -> stringResource(R.string.checklist_error_save_failed)
                            else -> null
                        },
                        testTag = if (deleteError) {
                            "${testTagPrefix}_delete_error"
                        } else {
                            "${testTagPrefix}_save_error"
                        },
                    )
                }
            }

            ChecklistHistory(
                days = history,
                identity = presentation,
                selectedDateKey = selectedDateKey,
                todayKey = todayKey,
                testTagPrefix = testTagPrefix,
                covers = LocalDayCovers.current[ViewRef.Checklist(checklist.id).trackerRef].orEmpty(),
                onDayClick = { day ->
                    when {
                        day.dateKey == selectedDateKey -> Unit
                        unsaved -> pendingDateKey = day.dateKey
                        else -> openDay(day.dateKey)
                    }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Removes the selected day's record. It keeps its slot on days with nothing saved, so moving
 * between days never moves what sits beside it.
 */
@Composable
private fun RemoveDayButton(
    dateKey: String,
    hasRecord: Boolean,
    testTagPrefix: String,
    onRemove: () -> Unit,
) {
    MomentumRemoveButton(
        description = stringResource(R.string.checklist_remove_day, dateText(dateKey).resolve()),
        resetKey = dateKey,
        enabled = hasRecord,
        testTag = "${testTagPrefix}_remove_day",
        onRemove = onRemove,
    )
}

/**
 * The token beside one checkpoint: the tokens [left] in the bank of the view called [viewLabel],
 * whether one is [setAside] for this checkpoint, whether it can be set aside or taken back now
 * ([offered]), and the command that does so.
 */
private class CheckpointToken(
    val identity: ViewIdentity,
    val viewLabel: String,
    val left: Int,
    val offered: Boolean,
    val setAside: Boolean,
    val onChange: (Boolean) -> Unit,
)

/**
 * One checkpoint of a checklist that has several: its items and its own save. Its draft is kept for
 * [dateKey] until the recorded ticks change underneath it. A [token] stands beside its name; it
 * records nothing, so it stands apart from the save.
 */
@Composable
private fun CheckpointForm(
    persisted: CheckpointSelection,
    weekItems: List<WeekItemProgress>,
    dateKey: String,
    onSave: (DayRecord) -> Boolean,
    icon: ImageVector,
    testTagPrefix: String,
    onUnsavedChange: (Boolean) -> Unit,
    token: CheckpointToken? = null,
) {
    var selection by remember(dateKey, persisted) { mutableStateOf(persisted) }
    var saveError by remember(dateKey, persisted) { mutableStateOf(false) }
    val currentOnUnsavedChange by rememberUpdatedState(onUnsavedChange)
    LaunchedEffect(selection.hasChanges) { currentOnUnsavedChange(selection.hasChanges) }
    DisposableEffect(Unit) { onDispose { currentOnUnsavedChange(false) } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val label = persisted.checkpoint.label
        if (label != null || token != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A step above the item labels, so a checkpoint reads as the name of what follows it.
                Text(text = label.orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (token != null) CheckpointTokenSwitch(token, checkpointLabel = label, testTag = "${testTagPrefix}_token")
            }
        }
        selection.items.forEach { item ->
            val tick = tickOf(selection.checkpoint.id, item.id)
            ChecklistItemRow(
                item = item,
                week = weekItems.of(item),
                mark = selection.markOf(tick),
                testTag = "${testTagPrefix}_${item.id}",
                onToggle = {
                    selection = selection.toggle(tick)
                    saveError = false
                },
            )
        }
        key(dateKey) {
            // A save that returned true is the completion; the recorded marks may take a frame to
            // come back, and until then this is what "saved" means here.
            var saved by remember { mutableStateOf<DayRecord?>(null) }
            var celebrationSignal by remember { mutableIntStateOf(0) }
            val completed = !selection.selected.isEmpty &&
                (!selection.hasChanges || selection.selected == saved)

            CompletionButton(
                actionLabel = stringResource(
                    if (!selection.persisted.isEmpty) R.string.checklist_save_changes else R.string.checklist_save,
                ),
                completedLabel = stringResource(R.string.checklist_saved),
                completed = completed,
                onClick = {
                    // Only a tick added is celebrated; a miss is recorded, never earned.
                    val celebrates = selection.addsATick
                    val wasSaved = onSave(selection.selected)
                    saveError = !wasSaved
                    if (wasSaved) saved = selection.selected
                    if (wasSaved && celebrates) celebrationSignal += 1
                },
                icon = icon,
                testTag = "${testTagPrefix}_save",
                modifier = Modifier.fillMaxWidth(),
                enabled = selection.hasChanges,
                celebrationSignal = celebrationSignal,
            )
        }
        ReservedMessage(
            text = if (saveError) stringResource(R.string.checklist_error_save_failed) else null,
            testTag = "${testTagPrefix}_save_error",
        )
    }
}

/**
 * The view's token with the number of tokens left, as every checkpoint of the checklist shows it,
 * so the counts move together. It is one switch: pressed in while this checkpoint holds a token,
 * and dimmed in its place while none can be set aside for it, so a token taken by one checkpoint
 * moves nothing at another. The token is drawn solid while the bank holds one or this checkpoint
 * does, and faded otherwise.
 */
@Composable
private fun CheckpointTokenSwitch(token: CheckpointToken, checkpointLabel: String?, testTag: String) {
    val count = pluralStringResource(R.plurals.mo_task_tokens, token.left, token.left, token.viewLabel)
    val action = when {
        !token.offered -> null
        checkpointLabel != null -> stringResource(R.string.checklist_use_token_for, checkpointLabel)
        else -> stringResource(R.string.checklist_use_token)
    }
    val spoken = if (action == null) count else "$count. $action"
    val state = if (token.offered) stringResource(if (token.setAside) R.string.mo_reserved_today else R.string.mo_can_reserve_today) else null
    MomentumCard(
        onClick = { token.onChange(!token.setAside) },
        selected = token.setAside,
        enabled = token.offered,
        role = Role.Switch,
        testTag = testTag,
    ) {
        // Said inside the card, so the pressed node itself carries the words and TalkBack reads them with the switch.
        PassTokenCount(
            identity = token.identity,
            count = token.left,
            faded = token.left == 0 && !token.setAside,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics {
                    contentDescription = spoken
                    if (state != null) stateDescription = state
                },
        )
    }
}

/**
 * One item to tick. A week item also says where its week stands, as saved, and whether skipping
 * it today would cost; the saved count is what the streak reads, so the line follows the save. An
 * item that asks fewer times on holiday says the same of its away days on an away day. An item
 * that logs only misses shows an assumed check, quieter than a recorded one, a miss shows a cross,
 * and TalkBack says whether the check is assumed or the item was marked missed; a recorded tick
 * reads as any tick does.
 */
@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    week: WeekItemProgress?,
    mark: ItemMark,
    testTag: String,
    onToggle: () -> Unit,
) {
    MomentumCheckboxRow(
        look = mark.look,
        onCheckedChange = { onToggle() },
        stateDescription = when (mark) {
            ItemMark.Assumed -> stringResource(R.string.checklist_item_assumed_done)
            ItemMark.Missed -> stringResource(R.string.checklist_item_missed)
            ItemMark.Open, ItemMark.Ticked -> null
        },
        testTag = testTag,
    ) {
        Column {
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (week != null) {
                Text(
                    text = week.count(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The box a mark shows. Only a miss wears the cross: an item not ticked yet keeps the empty box. */
internal val ItemMark.look: MomentumCheckboxLook
    get() = when (this) {
        ItemMark.Open -> MomentumCheckboxLook.Empty
        ItemMark.Ticked -> MomentumCheckboxLook.Recorded
        ItemMark.Assumed -> MomentumCheckboxLook.Assumed
        ItemMark.Missed -> MomentumCheckboxLook.Crossed
    }

/**
 * The line under a week item. A week that asks nothing of it yet, as the one it was added in, says
 * when it starts asking, since "0 of 0" reads as broken.
 */
@Composable
private fun WeekItemProgress.count(): String {
    val target = target
    return if (target is ChecklistTarget.PerWeek && asked == 0) {
        pluralStringResource(R.plurals.checklist_item_week_from_monday, target.times, target.times)
    } else {
        stringResource(countRes, done, asked)
    }
}

/** The week's count under an item: its week, or, for an item that asks fewer times on holiday, the week's away days. */
@get:StringRes
private val WeekItemProgress.countRes: Int
    get() = when {
        isWeekItem -> if (isGap) R.string.checklist_item_week_due else R.string.checklist_item_week
        else -> if (isGap) R.string.checklist_item_away_week_due else R.string.checklist_item_away_week
    }
