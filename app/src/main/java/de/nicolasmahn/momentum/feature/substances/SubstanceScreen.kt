package de.nicolasmahn.momentum.feature.substances

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.format.dateText
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.time.isValidDateKey
import de.nicolasmahn.momentum.core.time.nowMillis
import de.nicolasmahn.momentum.core.ui.FollowTodayEffect
import de.nicolasmahn.momentum.core.ui.GuardUnsavedWork
import de.nicolasmahn.momentum.core.ui.LeaveGuard
import de.nicolasmahn.momentum.core.ui.MomentaryCompletionButton
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonDefaults
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumCheckboxRow
import de.nicolasmahn.momentum.core.ui.MomentumChoiceTile
import de.nicolasmahn.momentum.core.ui.DiscardChangesDialog
import de.nicolasmahn.momentum.core.ui.MomentumDisclosure
import de.nicolasmahn.momentum.core.ui.MomentumRemoveButton
import de.nicolasmahn.momentum.core.ui.MomentumTextField
import de.nicolasmahn.momentum.core.ui.MomentumTileGrid
import de.nicolasmahn.momentum.core.ui.ReservedMessage
import de.nicolasmahn.momentum.core.ui.SelectedDayLine
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.ViewTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.substance.Substance
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.domain.substance.deleteSubstanceLog
import de.nicolasmahn.momentum.domain.substance.canSayNoSubstanceOn
import de.nicolasmahn.momentum.domain.substance.deleteSubstanceNoneDay
import de.nicolasmahn.momentum.domain.substance.substanceAskedDay
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Logging a day's substances, or "none" for a day with nothing in it, and looking back over four
 * weeks in the calendar, which is where a day is picked. The selected day is named above the tiles
 * only while it is not the day the screen opens on, the one the check-in asks about. Only the
 * tracked substances are offered and get a stat; a record of any other still shows on its day and
 * can be deleted there. [onOpenSettings] opens the Substances page, the one place the choice is
 * made. [initialDateKey] is the day the check-in asked about, selected once on arrival, which
 * [onInitialDateConsumed] acknowledges.
 */
@Composable
internal fun SubstanceScreen(
    state: AppState,
    onStateChange: (AppState) -> Boolean,
    todayKey: String,
    onOpenSettings: () -> Unit,
    initialDateKey: String? = null,
    onInitialDateConsumed: () -> Unit = {},
    leave: LeaveGuard = LeaveGuard.None,
) {
    val locale = currentLocale()
    val substances = state.substances
    fun draftOf(state: AppState, dateKey: String) = substanceDayDraft(state.substanceLogs, dateKey, locale, substances, state.substanceNoneDays)
    // It opens on the day the check-in asks about, the one the streak judges now; see substanceAskedDay.
    fun askedKey(todayKey: String) = substanceAskedDay(LocalDate.parse(todayKey)).toString()
    var draft by remember { mutableStateOf(draftOf(state, askedKey(todayKey))) }
    var pendingDateKey by remember { mutableStateOf<String?>(null) }
    var invalidAmountKeys by remember { mutableStateOf(emptySet<String>()) }
    var saveError by remember { mutableStateOf(false) }
    var removeError by remember { mutableStateOf(false) }
    // Why "none" was refused: the day still holds a use, whose uses go first, inline, or it is not over.
    var noneRefusal by remember { mutableStateOf<Int?>(null) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val window = remember(state.substanceLogs, state.substanceNoneDays, substances, todayKey) {
        substanceHistoryWindow(state.substanceLogs, todayKey, substances, state.substanceNoneDays)
    }
    val featurePresentation = substances.viewIdentity()
    var detailsExpanded by remember(draft.dateKey) {
        mutableStateOf(draft.needsDetails(substances))
    }

    fun clearErrors() {
        invalidAmountKeys = emptySet()
        saveError = false
        removeError = false
        noneRefusal = null
    }

    fun loadDate(dateKey: String) {
        val loadedDraft = draftOf(state, dateKey)
        draft = loadedDraft
        detailsExpanded = loadedDraft.needsDetails(substances)
        pendingDateKey = null
        clearErrors()
        focusManager.clearFocus()
        coroutineScope.launch { scrollState.animateScrollTo(0) }
    }

    fun requestDateChange(dateKey: String) {
        if (dateKey == draft.dateKey) return
        if (draft.isDirty) pendingDateKey = dateKey else loadDate(dateKey)
    }

    fun updateDraft(updated: SubstanceDayDraft) {
        draft = updated
        if (updated.rows.isEmpty()) detailsExpanded = false
        clearErrors()
    }

    var saveCelebrationSignal by remember { mutableIntStateOf(0) }

    fun save() {
        invalidAmountKeys = draft.invalidAmountKeys()
        saveError = false
        if (invalidAmountKeys.isNotEmpty()) {
            detailsExpanded = true
            return
        }
        if (hasSubstanceDayConflict(state, draft)) {
            saveError = true
            return
        }

        val updated = reconcileSubstanceDayDraft(
            state = state,
            draft = draft,
            idFactory = { UUID.randomUUID().toString() },
            timestampMillis = nowMillis(),
        )
        if (onStateChange(updated)) {
            saveCelebrationSignal += 1
            draft = draftOf(updated, draft.dateKey)
            detailsExpanded = false
            clearErrors()
            focusManager.clearFocus()
        } else {
            saveError = true
        }
    }

    // An unlogged day stores nothing, so removing a day is the same result as saving it empty: the
    // trash can is only the fast way there, and it deletes every record of the day at once, "none" too.
    fun removeDay() {
        val updated = state.substanceLogs
            .filter { it.dateKey == draft.dateKey }
            .fold(deleteSubstanceNoneDay(state, draft.dateKey)) { current, log -> deleteSubstanceLog(current, log.id) }
        if (updated == state) return
        if (onStateChange(updated)) {
            draft = draftOf(updated, draft.dateKey)
            detailsExpanded = false
            clearErrors()
            focusManager.clearFocus()
        } else {
            removeError = true
        }
    }

    // The check-in's day moves an untouched screen, as the new day at 04:00 does below. A screen
    // holding unsaved work stays as it is; the request is answered either way.
    LaunchedEffect(initialDateKey) {
        if (initialDateKey != null) {
            if (isValidDateKey(initialDateKey) && !draft.isDirty) loadDate(initialDateKey)
            onInitialDateConsumed()
        }
    }

    FollowTodayEffect(
        todayKey = todayKey,
        canFollow = { previousTodayKey -> !draft.isDirty && draft.dateKey == askedKey(previousTodayKey) },
        onFollow = { draft = draftOf(state, askedKey(it)) },
    )

    LaunchedEffect(state.substanceLogs, state.substanceNoneDays, substances, draft.dateKey, locale) {
        if (!draft.isDirty) draft = draftOf(state, draft.dateKey)
    }

    ViewTheme(featurePresentation.colors) {
        GuardUnsavedWork(draft.isDirty, leave, testTag = "substance_discard")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("substance_editor_${draft.dateKey}"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SelectedDayLine(
                    selectedDateKey = draft.dateKey,
                    openingDateKey = askedKey(todayKey),
                    testTag = "substance_selected_day",
                )

                val tracked = substances.trackedSubstances
                if (tracked.isEmpty()) {
                    // Nothing to offer: say so where the tiles would be, beside the way to change it.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("substance_nothing_tracked"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.substance_nothing_tracked),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MomentumButton(
                            text = stringResource(R.string.substance_choose),
                            onClick = onOpenSettings,
                            variant = MomentumButtonVariant.Secondary,
                            icon = Icons.Rounded.Settings,
                            testTag = "substance_choose",
                        )
                    }
                } else {
                    // Three to a row, as the three always were; a fourth starts a row below.
                    MomentumTileGrid(tracked, columns = LoggingColumns) { substance, tileModifier ->
                        val presentation = substances.presentation(substance)
                        MomentumChoiceTile(
                            label = substance.label().resolve(),
                            selected = draft.rows.any { it.substanceId == substance.id },
                            onClick = { updateDraft(draft.toggle(substance.id)) },
                            modifier = tileModifier,
                            icon = presentation.icon,
                            // Each substance owns its color, so a chosen tile wears that identity.
                            accent = presentation.colors.accent,
                            role = Role.Checkbox,
                            testTag = "substance_choice_${substance.id}",
                        )
                    }
                }

                // The whole day's answer when there is nothing to log: a fact like a use, as one
                // quiet row under the tiles rather than a tile of its own, so the calendar keeps
                // its place. It cannot stand beside a use, so while the day holds one it asks for
                // the uses to go first, in the line below the buttons.
                MomentumCheckboxRow(
                    checked = draft.none,
                    onCheckedChange = {
                        val refusal = when {
                            draft.none -> null
                            !canSayNoSubstanceOn(LocalDate.parse(draft.dateKey), LocalDate.parse(todayKey)) -> R.string.substance_none_not_over
                            !draft.canSayNone -> R.string.substance_none_blocked
                            else -> null
                        }
                        if (refusal == null) updateDraft(draft.toggleNone()) else {
                            clearErrors()
                            noneRefusal = refusal
                        }
                    },
                    testTag = "substance_none_today",
                ) {
                    Text(
                        text = stringResource(
                            when (draft.dateKey) {
                                todayKey -> R.string.substance_none_today
                                askedKey(todayKey) -> R.string.substance_none_yesterday
                                else -> R.string.substance_none_day
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Always present, so choosing a substance reveals details instead of moving them.
                MomentumDisclosure(
                    label = stringResource(R.string.substance_optional_details),
                    expanded = detailsExpanded,
                    onExpandedChange = { detailsExpanded = it },
                    testTag = "substance_optional_details",
                    enabled = draft.rows.isNotEmpty(),
                ) {
                    // The details keep the editor's own rhythm below the toggle.
                    Spacer(modifier = Modifier.height(2.dp))
                    substances.all.forEach { substance ->
                        val rows = draft.rows.filter { it.substanceId == substance.id }
                        if (rows.isNotEmpty()) {
                            SubstanceDetails(
                                substance = substance,
                                presentation = substances.presentation(substance),
                                tracked = substances.isTracked(substance.id),
                                rows = rows,
                                invalidAmountKeys = invalidAmountKeys,
                                onAmountChange = { rowKey, amountText ->
                                    updateDraft(draft.updateAmount(rowKey, amountText))
                                },
                                onRemove = { row ->
                                    row.id?.let { id -> updateDraft(draft.markDeleted(id)) }
                                },
                                onSave = ::save,
                            )
                        }
                    }
                }

                val hasSavedDay = state.substanceLogs.any { it.dateKey == draft.dateKey } || state.substanceNoneDays.any { it.dateKey == draft.dateKey }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Momentary, not a lasting check: an unlogged day stores nothing, so persisted
                    // state cannot tell "saved" from "never opened". The acknowledgement is the
                    // same for every day so honest logging is rewarded, never the content.
                    MomentaryCompletionButton(
                        actionLabel = stringResource(
                            when {
                                hasSavedDay && !draft.isDirty -> R.string.substance_saved
                                hasSavedDay -> R.string.substance_save_changes
                                else -> R.string.substance_save_day
                            },
                        ),
                        completedLabel = stringResource(R.string.substance_saved),
                        onClick = ::save,
                        icon = featurePresentation.icon,
                        testTag = "substance_save",
                        celebrationSignal = saveCelebrationSignal,
                        modifier = Modifier.weight(1f),
                        enabled = draft.isDirty,
                    )
                    MomentumRemoveButton(
                        description = stringResource(R.string.substance_remove_day, dateText(draft.dateKey).resolve()),
                        resetKey = draft.dateKey,
                        onRemove = ::removeDay,
                        testTag = "substance_remove_day",
                        enabled = hasSavedDay,
                    )
                }

                ReservedMessage(
                    text = when {
                        removeError -> stringResource(R.string.substance_error_remove_failed)
                        saveError -> stringResource(R.string.substance_error_save_failed)
                        else -> noneRefusal?.let { stringResource(it) }
                    },
                    testTag = "substance_save_error",
                )
            }

            // The tiles are the calendar's legend, so they sit close to it and keep the full gap
            // to the editor above, where the reserved error line already holds a space open.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SubstanceSummary(window)
                SubstanceHistory(
                    window = window,
                    selectedDateKey = draft.dateKey,
                    todayKey = todayKey,
                    onDayClick = { day -> requestDateChange(day.dateKey) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        pendingDateKey?.let { targetDateKey ->
            DiscardChangesDialog(testTag = "substance_discard", onDiscard = { loadDate(targetDateKey) }, onKeep = { pendingDateKey = null })
        }
    }
}

/**
 * The day's records of one substance, each with its optional amount. A record its tile cannot
 * untick, one of several left by an older version or one of a substance no longer tracked,
 * carries its own Delete, so a fact is never stuck.
 */
@Composable
private fun SubstanceDetails(
    substance: Substance,
    presentation: ViewIdentity,
    tracked: Boolean,
    rows: List<SubstanceDayRow>,
    invalidAmountKeys: Set<String>,
    onAmountChange: (String, String) -> Unit,
    onRemove: (SubstanceDayRow) -> Unit,
    onSave: () -> Unit,
) {
    val label = substance.label().resolve()
    val numbered = rows.size > 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("substance_kind_${substance.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (numbered) {
            Text(
                text = pluralStringResource(R.plurals.substance_legacy_entries, rows.size, rows.size, label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rows.forEachIndexed { index, row ->
            key(row.key) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("substance_record_${row.id ?: row.key}"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (numbered || !tracked) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // The name yields to Delete, so a long own name never squeezes it out.
                            Text(
                                text = if (numbered) stringResource(R.string.substance_legacy_entry, label, index + 1) else label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MomentumButton(
                                text = stringResource(R.string.substance_delete),
                                onClick = { onRemove(row) },
                                variant = MomentumButtonVariant.Quiet,
                                colors = MomentumButtonDefaults.destructiveColors(),
                                testTag = if (row.id == null) {
                                    "substance_record_remove_${row.key}"
                                } else {
                                    "substance_record_delete_${row.id}"
                                },
                            )
                        }
                    }
                    // Two substances may share a unit, so TalkBack hears whose field this is.
                    val described = stringResource(R.string.substance_amount_described, label, stringResource(substance.unit.amountLabelRes))
                    MomentumTextField(
                        value = row.amountText,
                        onValueChange = { onAmountChange(row.key, it) },
                        label = stringResource(substance.unit.amountLabelRes),
                        error = stringResource(R.string.substance_error_amount)
                            .takeIf { row.key in invalidAmountKeys },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = described },
                        testTag = "substance_amount_${row.key}",
                        leadingIcon = {
                            Icon(
                                imageVector = presentation.icon,
                                contentDescription = null,
                            )
                        },
                        suffix = stringResource(substance.unit.suffixRes),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSave() }),
                    )
                }
            }
        }
    }
}

/** Three to a row, as the three always were. */
private const val LoggingColumns = 3

/**
 * Whether the day holds a record no tile shows: several of one substance left by an older
 * version, or one of a substance no longer tracked. Such a record is only seen in the details,
 * so they open with the day.
 */
private fun SubstanceDayDraft.needsDetails(substances: SubstanceSettings): Boolean =
    rows.groupingBy(SubstanceDayRow::substanceId).eachCount().values.any { it > 1 } ||
        rows.any { !substances.isTracked(it.substanceId) }
