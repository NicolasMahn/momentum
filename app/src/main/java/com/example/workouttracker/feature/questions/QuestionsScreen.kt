package com.example.workouttracker.feature.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.format.dateText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.time.isValidDateKey
import com.example.workouttracker.core.ui.CompletionButton
import com.example.workouttracker.core.ui.DayCover
import com.example.workouttracker.core.ui.DiscardChangesDialog
import com.example.workouttracker.core.ui.FollowTodayEffect
import com.example.workouttracker.core.ui.GuardUnsavedWork
import com.example.workouttracker.core.ui.LeaveGuard
import com.example.workouttracker.core.ui.LocalViewColors
import com.example.workouttracker.core.ui.MomentumChoiceTile
import com.example.workouttracker.core.ui.MomentumChoiceTileRow
import com.example.workouttracker.core.ui.MomentumDialog
import com.example.workouttracker.core.ui.MomentumDialogAction
import com.example.workouttracker.core.ui.MomentumDisclosure
import com.example.workouttracker.core.ui.MomentumRemoveButton
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.core.ui.SelectedDayLine
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.domain.DayChangeResult
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.QuestionsNoteMaxLength
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import java.util.UUID
import kotlinx.coroutines.launch

/** How many answer tiles share a row; a last row with fewer keeps the others' width. */
private const val TilesPerRow = 3

/**
 * The screen of one questions view: each question with its answers and "Not sure", a note
 * behind "More options", Save and the two-step Remove, and recent days as a calendar, which is
 * where a day is picked. The selected day is named above the questions only while it is not the
 * day the screen opens on. Entries are scoped to [view]'s ID; [words] presents its stored prompts,
 * answers and help verbatim, and the screen wears its configured identity.
 *
 * The answers of the view's first question wear its color ramp, each the step its place falls on,
 * and the calendar colors a day by that answer; every other question's answers wear the view's
 * color.
 *
 * A day that has an entry shows it wherever that day is selected: as the screen opens, after a
 * calendar tap and a reminder's arrival, and after a save, which leaves the form on the saved day.
 * Save is complete while the form holds what the day has stored and is the action again once
 * something in it differs. What is picked and not saved belongs to the view's ID and the selected
 * day, never to today: only an untouched form moves on when the day changes, and the form survives
 * process death with its selected day.
 *
 * A save and a removal are handed to [onDayChange] as what is asked for one day, with the entry
 * the form opened on. Whoever stores it decides against the entries as they are at that moment.
 * [testTagPrefix] identifies the tracker; answer tile tags append the question ID and option
 * position. [covers] marks protected days without treating protection as a stored entry.
 *
 * [initialDateKey] is a day a reminder asked about, opened once on arrival, which
 * [onInitialDateConsumed] acknowledges.
 */
@Composable
internal fun QuestionsScreen(
    state: AppState,
    view: QuestionsConfiguration,
    words: QuestionsWords,
    onDayChange: (QuestionsDayChange) -> DayChangeResult,
    todayKey: String,
    testTagPrefix: String = view.testTagPrefix,
    covers: Map<String, DayCover> = emptyMap(),
    initialDateKey: String? = null,
    onInitialDateConsumed: () -> Unit = {},
    leave: LeaveGuard = LeaveGuard.None,
) {
    key(view.id) {
        QuestionsDays(state, view, words, onDayChange, todayKey, testTagPrefix, covers, initialDateKey, onInitialDateConsumed, leave)
    }
}

@Composable
private fun QuestionsDays(
    state: AppState,
    view: QuestionsConfiguration,
    words: QuestionsWords,
    onDayChange: (QuestionsDayChange) -> DayChangeResult,
    todayKey: String,
    testTagPrefix: String,
    covers: Map<String, DayCover>,
    initialDateKey: String?,
    onInitialDateConsumed: () -> Unit,
    leave: LeaveGuard,
) {
    val entries = remember(state.questionEntries, view.id) { state.questionEntries.filter { it.viewId == view.id } }
    val asked = remember(view) { view.scales }
    val defaultDateKey = view.defaultDateKey(todayKey)
    val validInitialDateKey = initialDateKey?.takeIf(::isValidDateKey)
    val historyWindow = remember(entries, todayKey) { questionsHistoryWindow(entries, todayKey) }
    val presentation = view.presentation()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // Kept across process death with the day it belongs to. It sits inside the view's own key and
    // is never keyed on today, so it is neither another view's nor dropped when the day changes.
    var form by rememberSaveable(stateSaver = QuestionsFormSaver) { mutableStateOf(state.questionsForm(view, defaultDateKey)) }
    var saveError by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf(false) }
    var appliedInitialDateKey by rememberSaveable { mutableStateOf(validInitialDateKey) }
    var helpFor by remember { mutableStateOf<String?>(null) }
    // Folded by default even when the day has a note: the summary shows it, so nothing is hidden.
    var noteExpanded by rememberSaveable { mutableStateOf(false) }

    fun show(shown: QuestionsForm) {
        form = shown
        saveError = false
        deleteError = false
    }

    fun edit(change: (QuestionsDraft) -> QuestionsDraft) {
        form = form.copy(draft = change(form.draft))
        saveError = false
    }

    // Another day replaces what was picked and not saved, so a tap on one asks first while there is any.
    var pendingDateKey by remember { mutableStateOf<String?>(null) }

    fun openDay(dateKey: String) {
        show(state.questionsForm(view, dateKey))
        pendingDateKey = null
        noteExpanded = false
        coroutineScope.launch { scrollState.animateScrollTo(0) }
    }

    fun requestDay(day: QuestionsHistoryDay) {
        when {
            day.dateKey == form.dateKey -> Unit
            !form.isUntouched(asked) -> pendingDateKey = day.dateKey
            else -> openDay(day.dateKey)
        }
    }

    var saveCelebrationSignal by remember { mutableIntStateOf(0) }

    fun save() {
        saveError = false
        val change = QuestionsDayChange.Save(
            viewId = view.id,
            dateKey = form.dateKey,
            opened = form.opened,
            answers = form.draft.storedAnswers,
            note = form.draft.note.trim().ifBlank { null },
            newEntryId = UUID.randomUUID().toString(),
        )
        when (val result = onDayChange(change)) {
            is DayChangeResult.Stored -> {
                // The form stays on the day and shows it as it was stored, whenever this screen is handed the new state.
                show(result.after.questionsForm(view, change.dateKey))
                saveCelebrationSignal += 1
                focusManager.clearFocus()
            }

            else -> saveError = true
        }
    }

    fun delete() {
        val opened = form.opened ?: return
        when (val result = onDayChange(QuestionsDayChange.Remove(view.id, opened.dateKey, opened))) {
            is DayChangeResult.Stored -> {
                show(result.after.questionsForm(view, opened.dateKey))
                noteExpanded = false
                focusManager.clearFocus()
            }

            else -> deleteError = true
        }
    }

    LaunchedEffect(initialDateKey) {
        if (initialDateKey != null) {
            validInitialDateKey?.let { dateKey ->
                show(state.questionsForm(view, dateKey))
                appliedInitialDateKey = dateKey
            }
            onInitialDateConsumed()
        }
    }

    // An untouched form follows what its day has stored, so an entry stored from elsewhere is on
    // show and never saved over; a form something was picked in is left to the store's own refusal.
    LaunchedEffect(entries) {
        if (form.isUntouched(asked)) form = state.questionsForm(view, form.dateKey)
    }

    // Only an untouched default day moves on at the habit-day boundary, whether it shows an entry
    // or none. Answers picked and not saved belong to the day they were picked for, so a started
    // form stays exactly as it is.
    FollowTodayEffect(
        todayKey = todayKey,
        canFollow = { previousTodayKey ->
            appliedInitialDateKey == null &&
                form.dateKey == view.defaultDateKey(previousTodayKey) &&
                form.isUntouched(asked)
        },
        onFollow = { form = state.questionsForm(view, defaultDateKey) },
    )

    ViewTheme(presentation.colors) {
        GuardUnsavedWork(!form.isUntouched(asked), leave, testTag = "${testTagPrefix}_discard")
        pendingDateKey?.let { dateKey ->
            DiscardChangesDialog(testTag = "${testTagPrefix}_discard", onDiscard = { openDay(dateKey) }, onKeep = { pendingDateKey = null })
        }
        words.questions.firstOrNull { it.id == helpFor }?.let { question ->
            question.help?.dialog?.invoke(helpTag(testTagPrefix, question)) { helpFor = null }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SelectedDayLine(
                    selectedDateKey = form.dateKey,
                    openingDateKey = defaultDateKey,
                    testTag = "${testTagPrefix}_selected_day",
                )

                words.questions.forEachIndexed { index, question ->
                    QuestionChoices(
                        question = question,
                        // The same ramp step the calendar will paint the day with, so the first
                        // question's answers show their result; the calendar marks any other
                        // question only as answered or not, so its tiles keep the view identity.
                        rampOf = view.hue.takeIf { index == 0 },
                        notSure = words.notSure.resolve(),
                        answer = form.draft.answerTo(question.id),
                        onAnswer = { answer -> edit { it.answering(question.id, answer) } },
                        testTagPrefix = "${testTagPrefix}_${question.id}",
                        onHelpClick = { helpFor = question.id },
                    )
                }

                MomentumDisclosure(
                    label = words.moreOptions.resolve(),
                    expanded = noteExpanded,
                    onExpandedChange = { noteExpanded = it },
                    testTag = "${testTagPrefix}_more_options",
                    summary = form.draft.note.trim().ifBlank { null },
                ) {
                    OutlinedTextField(
                        value = form.draft.note,
                        onValueChange = { value ->
                            if (value.length <= QuestionsNoteMaxLength) edit { it.copy(note = value) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("${testTagPrefix}_note"),
                        label = { Text(words.noteLabel.resolve()) },
                        minLines = 1,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Selecting another day is the way out of an edit, so there is no Cancel.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Keyed by the day, inside the view's own key, so a celebration never carries into another selection.
                        key(form.dateKey) {
                            CompletionButton(
                                actionLabel = (if (form.opened == null) words.save else words.saveChanges).resolve(),
                                completedLabel = words.saved.resolve(),
                                completed = form.isSaved(asked),
                                onClick = ::save,
                                icon = presentation.icon,
                                testTag = "${testTagPrefix}_save",
                                modifier = Modifier.weight(1f),
                                celebrationSignal = saveCelebrationSignal,
                            )
                        }
                        MomentumRemoveButton(
                            description = words.remove(dateText(form.dateKey)).resolve(),
                            resetKey = form.opened?.id,
                            onRemove = ::delete,
                            testTag = "${testTagPrefix}_delete",
                            enabled = form.opened != null,
                        )
                    }

                    ReservedMessage(
                        text = when {
                            deleteError -> words.removalFailed.resolve()
                            saveError -> words.saveFailed.resolve()
                            else -> null
                        },
                        testTag = "${testTagPrefix}_persistence_error",
                    )
                }
            }

            QuestionsHistory(
                window = historyWindow,
                asked = asked,
                hue = view.hue,
                words = words,
                selectedDateKey = form.dateKey,
                todayKey = todayKey,
                testTagPrefix = testTagPrefix,
                onDayClick = ::requestDay,
                covers = covers,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun helpTag(testTagPrefix: String, question: AskedQuestion) = "${testTagPrefix}_${question.id}_help"

/**
 * Keeps a form across process death: the selected day with what was picked and typed for it, and
 * the entry it opened on, written as storage writes it, so a restored form is still decided
 * against the entry the store holds.
 */
internal val QuestionsFormSaver: Saver<QuestionsForm, Any> = listSaver<QuestionsForm, Any?>(
    save = { form ->
        listOf(
            form.draft.dateKey,
            form.draft.note,
            form.draft.answers.keys.toList(),
            // "Not sure" is the question without an answer, as an entry stores it.
            form.draft.answers.values.map { it.storedAnswerId },
            form.opened?.let(AppStateJsonCodec::encodeQuestionEntry),
        )
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        val answers = (saved[2] as List<String>).zip(saved[3] as List<String?>) { questionId, answerId ->
            questionId to (answerId?.let(QuestionAnswer::Answered) ?: QuestionAnswer.NotSure)
        }
        QuestionsForm(
            draft = QuestionsDraft(dateKey = saved[0] as String, answers = answers.toMap(), note = saved[1] as String),
            opened = (saved[4] as String?)?.let(AppStateJsonCodec::decodeQuestionEntry),
        )
    },
)

/**
 * One question: its text, its help button where it has help, and its answers and "Not sure" as
 * tiles. [rampOf] is the hue whose ramp the answers wear, or null for tiles in the view's color.
 */
@Composable
private fun QuestionChoices(
    question: AskedQuestion,
    rampOf: ViewHue?,
    notSure: String,
    answer: QuestionAnswer,
    onAnswer: (QuestionAnswer) -> Unit,
    testTagPrefix: String,
    onHelpClick: () -> Unit,
) {
    val options = question.answers.mapIndexed { index, choice ->
        ChoiceOption(
            answerId = choice.id,
            label = choice.label.resolve(),
            testTag = "${testTagPrefix}_$index",
            accent = rampOf?.let { answerStep(it, index, question.answers.size).fill },
        )
    } + ChoiceOption(
        // "Not sure" is an answer of its own, so it wears the grey the calendar will paint that
        // day with, exactly as the ramp's steps do.
        answerId = null,
        label = notSure,
        testTag = "${testTagPrefix}_not_sure",
        accent = NotSureStep.fill,
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = question.text.resolve(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            question.help?.let { help ->
                IconButton(
                    onClick = onHelpClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_help"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = help.description.resolve(),
                        tint = LocalViewColors.current.label,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(TilesPerRow).forEach { rowOptions ->
                MomentumChoiceTileRow {
                    rowOptions.forEach { option ->
                        val answerId = option.answerId
                        MomentumChoiceTile(
                            label = option.label,
                            selected = if (answerId == null) answer is QuestionAnswer.NotSure else answer == QuestionAnswer.Answered(answerId),
                            onClick = { onAnswer(if (answerId == null) toggleNotSure(answer) else toggleAnswer(answer, answerId)) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            accent = option.accent,
                            swatch = option.accent,
                            testTag = option.testTag,
                        )
                    }
                    repeat(TilesPerRow - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private data class ChoiceOption(
    val answerId: String?,
    val label: String,
    val testTag: String,
    val accent: Color? = null,
)

/** The help of a question a person wrote, shown as typed. Its nodes are tagged after the button's [testTag]. */
@Composable
internal fun QuestionHelpDialog(text: String, testTag: String, onDismiss: () -> Unit) {
    MomentumDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("${testTag}_dialog"),
        message = text,
        confirm = MomentumDialogAction(
            text = stringResource(R.string.questions_help_close),
            onClick = onDismiss,
            testTag = "${testTag}_close",
        ),
    )
}
