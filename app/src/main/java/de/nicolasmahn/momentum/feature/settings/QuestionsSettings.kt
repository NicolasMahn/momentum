package de.nicolasmahn.momentum.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumDisclosure
import de.nicolasmahn.momentum.core.ui.MomentumRemoveButton
import de.nicolasmahn.momentum.core.ui.MomentumTextField
import de.nicolasmahn.momentum.core.ui.ReservedMessage
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.questions.AnswersPerQuestion
import de.nicolasmahn.momentum.domain.questions.MaximumAnswerLabelLength
import de.nicolasmahn.momentum.domain.questions.MaximumQuestionHelpLength
import de.nicolasmahn.momentum.domain.questions.MaximumQuestionTextLength
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration
import de.nicolasmahn.momentum.domain.questions.QuestionsEdit
import de.nicolasmahn.momentum.domain.questions.QuestionsPerView
import de.nicolasmahn.momentum.domain.questions.applyEdit
import de.nicolasmahn.momentum.domain.questions.applyingEdits
import de.nicolasmahn.momentum.domain.questions.isValidAnswerLabel
import de.nicolasmahn.momentum.domain.questions.isValidQuestionText
import de.nicolasmahn.momentum.domain.questions.starterAnswer
import de.nicolasmahn.momentum.domain.questions.starterQuestion
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.persistence.json.AppStateJsonCodec
import de.nicolasmahn.momentum.reminder.TestReminder
import java.time.LocalDate

/**
 * The IDs a view's questions and their answers are named by in what is stored, its entries'
 * answers to questions and answers removed since included: the ones a question or an answer added
 * on its page must not take, so nothing recorded is ever read as a new one's. A view that is not
 * stored yet, as in its setup, is named by none.
 */
internal class NamedQuestionIds(val questions: Set<String> = emptySet(), val answers: (questionId: String) -> Set<String> = { emptySet() })

/**
 * A questions view's name, look, questions, reminder and streak part as a draft
 * ([LoggedViewDraft]).
 *
 * A question's text and an answer's label are typed into the draft as they are typed. A field
 * holding nothing, or text that cannot be stored, keeps the draft's last text and holds Save back
 * ([unsettled]): what was typed is never swapped for that text behind the person's back. A
 * question or an answer added on the page starts so, with a text nobody sees until its own is
 * typed.
 */
internal class QuestionsSettingsDraft(
    stored: QuestionsConfiguration,
    today: LocalDate,
    edits: List<QuestionsEdit> = emptyList(),
    blankNames: Set<String> = emptySet(),
    unsettled: Set<String> = emptySet(),
) : LoggedViewDraft<QuestionsConfiguration, QuestionsEdit>(stored, today, edits, blankNames), DailyViewEdits {
    /**
     * The fields of questions and answers whose text the draft does not hold: empty, or holding
     * text that cannot be stored. A question's is its ID, an answer's [answerField].
     */
    var unsettled: Set<String> by mutableStateOf(unsettled)
        private set

    override val heldBack: Boolean get() = super.heldBack || unsettled.isNotEmpty()

    override val hasUnsavedWork: Boolean get() = super.hasUnsavedWork || unsettled.isNotEmpty()

    override fun QuestionsConfiguration.applying(edit: QuestionsEdit, today: LocalDate): QuestionsConfiguration = applyEdit(edit, today)

    override fun QuestionsConfiguration.applyingAll(edits: List<QuestionsEdit>, today: LocalDate): QuestionsConfiguration = applyingEdits(edits, today)

    /** The one name is the view's own. */
    override fun QuestionsEdit.names(key: String): Boolean = this is QuestionsEdit.Rename && key == stored.id

    override fun QuestionsEdit.removes(): String? = null

    override fun rename(label: String): Boolean = edit(QuestionsEdit.Rename(label))

    override fun setIcon(icon: ViewIcon): Boolean = edit(QuestionsEdit.SetIcon(icon))

    override fun setHue(hue: ViewHue): Boolean = edit(QuestionsEdit.SetHue(hue))

    override fun setRemind(remind: Boolean): Boolean = edit(QuestionsEdit.SetRemind(remind))

    override fun setReminderTime(at: CheckpointTime): Boolean = edit(QuestionsEdit.SetReminderTime(at))

    override fun takePart(takesPart: Boolean): Boolean = edit(if (takesPart) QuestionsEdit.JoinRewards else QuestionsEdit.LeaveRewards)

    override fun setBankRules(maxPasses: Int, everyKeptDays: Int): Boolean = edit(QuestionsEdit.SetRewardRules(maxPasses, everyKeptDays))

    override fun setWhenAway(whenAway: WhenAway): Boolean = edit(QuestionsEdit.SetWhenAway(whenAway))

    fun setAbout(about: AboutDay): Boolean = edit(QuestionsEdit.SetAbout(about))

    /** Whether the text of [field] is stored: the field holds what the draft holds, and no edit to it waits for Save. */
    fun textStored(field: String): Boolean = field !in unsettled && edits.none { it.writes(field) }

    /** Whether the help of [questionId] is stored: no edit to it waits for Save. */
    fun helpStored(questionId: String): Boolean = edits.none { it is QuestionsEdit.SetQuestionHelp && it.questionId == questionId }

    /**
     * One question more, with an answer for each of [answerLabels], under IDs neither [named] nor
     * this page has given out. Its own text is still to be typed: until then the draft holds
     * [placeholder], which no field shows and Save never stores.
     */
    fun addQuestion(placeholder: String, answerLabels: List<String>, named: NamedQuestionIds) {
        val drawn = edits.filterIsInstance<QuestionsEdit.AddQuestion>().map { it.question.id }
        val question = starterQuestion(placeholder, answerLabels, takenQuestionIds = named.questions + edited.questions.map { it.id } + drawn)
        if (edit(QuestionsEdit.AddQuestion(question))) unsettled = unsettled + question.id
    }

    /** One answer more for [questionId], last on its scale, whose label is still to be typed over [placeholder]. */
    fun addAnswer(questionId: String, placeholder: String, named: NamedQuestionIds) {
        val drawn = edits.filterIsInstance<QuestionsEdit.AddAnswer>().filter { it.questionId == questionId }.map { it.answer.id }
        val had = edited.questions.firstOrNull { it.id == questionId }?.answers.orEmpty().map { it.id }
        val answer = starterAnswer(placeholder, takenAnswerIds = named.answers(questionId) + had + drawn)
        if (edit(QuestionsEdit.AddAnswer(questionId, answer))) unsettled = unsettled + answerField(questionId, answer.id)
    }

    /** The field of [questionId]'s text was typed into. */
    fun typeQuestion(questionId: String, typed: String) =
        type(questionId, typed, ::isValidQuestionText) { QuestionsEdit.RenameQuestion(questionId, it) }

    /** The field of an answer's label was typed into. */
    fun typeAnswer(questionId: String, answerId: String, typed: String) =
        type(answerField(questionId, answerId), typed, ::isValidAnswerLabel) { QuestionsEdit.RenameAnswer(questionId, answerId, it) }

    /** The help of [questionId] was typed: what was typed without the space around it, or none where nothing is left. */
    fun typeHelp(questionId: String, typed: String): Boolean = edit(QuestionsEdit.SetQuestionHelp(questionId, typed.trim().ifEmpty { null }))

    /** Removes a question with its answers; whatever their fields held goes with them. */
    fun removeQuestion(questionId: String) {
        if (edit(QuestionsEdit.RemoveQuestion(questionId))) unsettled = unsettled.filterNotTo(LinkedHashSet()) { it == questionId || it.startsWith(answerField(questionId, "")) }
    }

    fun removeAnswer(questionId: String, answerId: String) {
        if (edit(QuestionsEdit.RemoveAnswer(questionId, answerId))) unsettled = unsettled - answerField(questionId, answerId)
    }

    /**
     * [typed] without the spaces of the space bar around it becomes the text of [field] where it
     * can be one, through the edit [change] makes of it; otherwise the draft only knows the field
     * is unsettled. Any other character the rule refuses is put to the rule where it stands, as a
     * measurement's unit is.
     */
    private fun type(field: String, typed: String, isValid: (String) -> Boolean, change: (String) -> QuestionsEdit) {
        val text = typed.trim(' ')
        unsettled = if (isValid(text) && edit(change(text))) unsettled - field else unsettled + field
    }

    private fun QuestionsEdit.writes(field: String): Boolean = when (this) {
        is QuestionsEdit.RenameQuestion -> field == questionId
        is QuestionsEdit.RenameAnswer -> field == answerField(questionId, answerId)
        else -> false
    }

    companion object {
        /** The key of the field an answer's label is typed in. An ID holds no slash, so it is no question's. */
        fun answerField(questionId: String, answerId: String): String = "$questionId/$answerId"

        val Saver: Saver<QuestionsSettingsDraft, Any> = listSaver(
            save = {
                listOf(
                    AppStateJsonCodec.encodeQuestionsView(it.stored),
                    AppStateJsonCodec.encodeQuestionsEdits(it.edits),
                    ArrayList(it.blankNames),
                    it.today.toString(),
                    ArrayList(it.unsettled),
                )
            },
            restore = {
                QuestionsSettingsDraft(
                    stored = AppStateJsonCodec.decodeQuestionsView(it[0] as String),
                    edits = AppStateJsonCodec.decodeQuestionsEdits(it[1] as String),
                    blankNames = (it[2] as List<*>).mapTo(LinkedHashSet()) { key -> key as String },
                    today = LocalDate.parse(it[3] as String),
                    unsettled = (it[4] as List<*>).mapTo(LinkedHashSet()) { field -> field as String },
                )
            },
        )
    }
}

/**
 * A questions tracker's settings share [MadeViewSettingsPage], with four parts: Look, its name,
 * icon and color; Questions ([QuestionsPart]), its editable stored prompts and answers; Reminders
 * ([LoggedViewReminder]); and Streak ([DailyViewStreak]). All four change one draft, and Save
 * stores its edits through [onSave]; it waits while a question or an answer is without its text,
 * which the bar says.
 *
 * [named] are the IDs its stored questions and answers are named by, which new ones must not take.
 * [reminderRow], [onSendTest], [bedtime] and [daysOff] are the Reminders part's, as on a made
 * measurement's page. It can be deleted, with every day's answers, once the dialog naming it and
 * its [answeredDays] confirmed that. The page opens on Look; a host that shows another part first,
 * as the gallery does, says which through [opensOn].
 */
@Composable
internal fun MadeQuestionsSettingsPage(
    view: QuestionsConfiguration,
    today: LocalDate,
    answeredDays: Int,
    named: NamedQuestionIds,
    bedtime: Bedtime,
    daysOff: HolidaySchedule,
    onSave: (List<QuestionsEdit>) -> ViewWrite,
    reminderRow: @Composable (LoggedView, String, (Boolean) -> Unit) -> Unit,
    onSendTest: ((TestReminder.AtCheckpoint) -> Unit)?,
    onDelete: () -> Boolean,
    onClose: () -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    opensOn: ChecklistPage = LoggedViewParts.first(),
) {
    val draft = rememberSaveable(view.id, saver = QuestionsSettingsDraft.Saver) { QuestionsSettingsDraft(view, today) }
    draft.follow(view, today)
    val textNeeded = stringResource(R.string.questions_settings_text_needed)

    MadeViewSettingsPage(
        view = view,
        draft = draft,
        testTag = "questions_settings",
        pageKey = SettingsPage.Questions(view.id).key,
        parts = LoggedViewParts,
        opensOn = opensOn,
        deletion = LoggedViewDeletion(answeredDays, R.plurals.questions_settings_delete_message, R.string.questions_settings_delete_failed, onDelete),
        onSave = onSave,
        onClose = onClose,
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        modifier = modifier,
        message = message,
        hint = textNeeded.takeIf { draft.unsettled.isNotEmpty() }?.let { BarLine(it, "questions_settings_hint") },
        partLabel = { QuestionsOwnPart.nameOf(it) },
        partTagName = QuestionsOwnPart::tagNameOf,
    ) { shown, edited, onPickTime, nameField ->
        MadeQuestionsPart(
            part = shown,
            view = edited,
            draft = draft,
            today = today,
            named = named,
            bedtime = bedtime,
            daysOff = daysOff,
            testTag = "questions_settings",
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            onPickTime = onPickTime,
            nameField = nameField,
        )
    }
}

/** A questions view's second part holds questions, not items. */
internal val QuestionsOwnPart = OwnPart(R.string.questions_settings_questions, tagName = "questions")

/**
 * One part of a made questions view's page, the page's and the setup's alike, over [view] as
 * [draft] is changing it. [nameField] is Look's name, which the page and the setup keep
 * differently; [reminderRow], [onSendTest] and [onPickTime] are the Reminders part's. Every tag
 * follows from [testTag].
 */
@Composable
internal fun MadeQuestionsPart(
    part: ChecklistPage,
    view: QuestionsConfiguration,
    draft: QuestionsSettingsDraft,
    today: LocalDate,
    named: NamedQuestionIds,
    bedtime: Bedtime,
    daysOff: HolidaySchedule,
    testTag: String,
    reminderRow: @Composable (LoggedView, String, (Boolean) -> Unit) -> Unit,
    onSendTest: ((TestReminder.AtCheckpoint) -> Unit)?,
    onPickTime: () -> Unit,
    nameField: @Composable () -> Unit,
) {
    when (part) {
        ChecklistPage.Look -> LoggedViewLook(view = view, edits = draft, testTag = testTag, icons = QuestionsIcons) { nameField() }

        ChecklistPage.Items -> QuestionsPart(view = view, draft = draft, today = today, testTag = testTag, named = named)

        ChecklistPage.Reminders -> LoggedViewReminder(
            view = view,
            edits = draft,
            today = today,
            bedtime = bedtime,
            daysOff = daysOff,
            testTag = testTag,
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            onPickTime = onPickTime,
        )

        ChecklistPage.Streak -> DailyViewStreak(view = view, edits = draft, today = today, testTag = testTag, rewards = view.rewards)
    }
}

/**
 * What a questions view asks, on its setup and on its page alike, over [view] as [draft] is
 * changing it: which day a day's answers are about, then each question as a group, its text, its
 * answers in the order of their scale, and its help text behind "More options". Each is typed into
 * the draft as it is typed, a question and an answer are removed with the two-step button, and
 * one more of either is added while it has fewer than five, under an ID [named] does not hold.
 * Questions and answers stay in the order they were added.
 *
 * Tags follow from [testTag]: a question is `<testTag>_question_<id>`, its text `…_text`, an
 * answer `…_answer_<id>`, each remove button `…_remove`, and the two buttons that add
 * `<testTag>_add_question` and `<testTag>_question_<id>_add_answer`.
 */
@Composable
internal fun QuestionsPart(
    view: QuestionsConfiguration,
    draft: QuestionsSettingsDraft,
    today: LocalDate,
    testTag: String,
    named: NamedQuestionIds = NamedQuestionIds(),
) {
    val questions = view.shownQuestions(draft)
    val defaultAnswers = DefaultAnswerLabels.map { stringResource(it) }
    val questionWord = stringResource(R.string.questions_settings_question)
    val answerWord = stringResource(R.string.questions_settings_answer)

    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        EditorGroup(stringResource(R.string.questions_settings_about)) {
            AboutEditor(
                about = view.about.on(today),
                testTag = testTag,
                label = { if (it == AboutDay.Today) R.string.questions_settings_about_today else R.string.questions_settings_about_yesterday },
                note = { if (it == AboutDay.Today) R.string.questions_settings_about_today_note else R.string.questions_settings_about_yesterday_note },
                onChoose = { draft.setAbout(it) },
            )
        }

        questions.forEachIndexed { index, question ->
            key(question.id) {
                QuestionEditor(
                    question = question,
                    number = index + 1,
                    // The last question stays: a view always has something to ask.
                    removable = questions.size > QuestionsPerView.first,
                    draft = draft,
                    testTag = "${testTag}_question_${question.id}",
                    onAddAnswer = { draft.addAnswer(question.id, answerWord, named) },
                )
            }
        }

        // At five it stays where it is and cannot be pressed, as a remove button stays at the least.
        MomentumButton(
            text = stringResource(R.string.questions_settings_add_question),
            onClick = { draft.addQuestion(questionWord, defaultAnswers, named) },
            modifier = Modifier.fillMaxWidth(),
            variant = MomentumButtonVariant.Secondary,
            icon = Icons.Rounded.Add,
            enabled = questions.size < QuestionsPerView.last,
            testTag = "${testTag}_add_question",
        )
        EditorNote(stringResource(R.string.questions_settings_removal_note), testTag = "${testTag}_removal_note")
    }
}

/** The five answers a new question starts with, least to most, in the app's language at the moment it is added. */
internal val DefaultAnswerLabels = listOf(
    R.string.questions_answer_default_1,
    R.string.questions_answer_default_2,
    R.string.questions_answer_default_3,
    R.string.questions_answer_default_4,
    R.string.questions_answer_default_5,
)

/** One question as the part shows it: its text and each answer's label, null while the field holds none the draft has. */
private class ShownQuestion(val id: String, val text: String?, val answers: List<ShownAnswer>, val help: String?)

private class ShownAnswer(val id: String, val label: String?)

/** A made view's questions as the draft holds them; a field the draft holds no text of shows none. */
private fun QuestionsConfiguration.shownQuestions(draft: QuestionsSettingsDraft): List<ShownQuestion> = questions.map { question ->
    ShownQuestion(
        id = question.id,
        text = question.text.takeUnless { question.id in draft.unsettled },
        answers = question.answers.map { answer -> ShownAnswer(answer.id, answer.label.takeUnless { QuestionsSettingsDraft.answerField(question.id, answer.id) in draft.unsettled }) },
        help = question.help,
    )
}

/**
 * The line that says why a question or an answer cannot be removed. It comes and goes as questions
 * and answers are added and removed, so it keeps its space while it says nothing and the fields
 * under it never move.
 */
@Composable
internal fun StayNote(sentence: String, shown: Boolean, testTag: String) {
    ReservedMessage(
        text = sentence.takeIf { shown },
        testTag = testTag,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        reserving = listOf(sentence),
    )
}

/**
 * One question: its text beside its remove button, its answers each beside theirs, the button that
 * adds an answer, which stays and cannot be pressed at five, and its help text behind "More
 * options", whose summary shows the help's first line so a folded one never hides it. A question
 * and an answer that must stay say why under the button that would remove them, on a line that
 * keeps its space ([StayNote]).
 */
@Composable
private fun QuestionEditor(
    question: ShownQuestion,
    number: Int,
    removable: Boolean,
    draft: QuestionsSettingsDraft,
    testTag: String,
    onAddAnswer: () -> Unit,
) {
    var moreOptions by rememberSaveable(question.id) { mutableStateOf(false) }
    val answersRemovable = question.answers.size > AnswersPerQuestion.first
    Column(modifier = Modifier.testTag(testTag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypedLineField(
                    text = question.text,
                    key = question.id,
                    label = stringResource(R.string.questions_settings_question),
                    missing = stringResource(R.string.questions_settings_question_missing),
                    maximum = MaximumQuestionTextLength,
                    isValid = ::isValidQuestionText,
                    settled = draft.textStored(question.id),
                    enabled = true,
                    testTag = "${testTag}_text",
                    onType = { draft.typeQuestion(question.id, it) },
                    modifier = Modifier.weight(1f),
                )
                MomentumRemoveButton(
                    description = stringResource(R.string.questions_settings_remove_question, number),
                    onRemove = { draft.removeQuestion(question.id) },
                    testTag = "${testTag}_remove",
                    variant = MomentumButtonVariant.Quiet,
                    enabled = removable,
                )
            }
            StayNote(stringResource(R.string.questions_settings_last_question), shown = !removable, testTag = "${testTag}_keep")
        }

        Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            question.answers.forEachIndexed { index, answer ->
                key(answer.id) {
                    val field = QuestionsSettingsDraft.answerField(question.id, answer.id)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypedLineField(
                            text = answer.label,
                            key = field,
                            label = stringResource(R.string.questions_settings_answer),
                            missing = stringResource(R.string.questions_settings_answer_missing),
                            maximum = MaximumAnswerLabelLength,
                            isValid = ::isValidAnswerLabel,
                            settled = draft.textStored(field),
                            enabled = true,
                            testTag = "${testTag}_answer_${answer.id}",
                            onType = { draft.typeAnswer(question.id, answer.id, it) },
                            modifier = Modifier.weight(1f),
                        )
                        MomentumRemoveButton(
                            description = stringResource(R.string.questions_settings_remove_answer, index + 1, number),
                            onRemove = { draft.removeAnswer(question.id, answer.id) },
                            testTag = "${testTag}_answer_${answer.id}_remove",
                            variant = MomentumButtonVariant.Quiet,
                            enabled = answersRemovable,
                        )
                    }
                }
            }
            StayNote(stringResource(R.string.questions_settings_two_answers), shown = !answersRemovable, testTag = "${testTag}_keep_answers")
            // At five it stays where it is and cannot be pressed, so the fifth answer moves nothing but itself.
            MomentumButton(
                text = stringResource(R.string.questions_settings_add_answer),
                onClick = onAddAnswer,
                variant = MomentumButtonVariant.Quiet,
                icon = Icons.Rounded.Add,
                enabled = question.answers.size < AnswersPerQuestion.last,
                testTag = "${testTag}_add_answer",
            )
        }

        MomentumDisclosure(
            label = stringResource(R.string.questions_more_options),
            expanded = moreOptions,
            onExpandedChange = { moreOptions = it },
            testTag = "${testTag}_more_options",
            summary = question.help?.lineSequence()?.firstOrNull(),
        ) {
            HelpField(
                help = question.help,
                key = "${question.id}/help",
                settled = draft.helpStored(question.id),
                testTag = "${testTag}_help",
                onType = { draft.typeHelp(question.id, it) },
            )
        }
    }
}

/**
 * One line of text, a question's or an answer's, taken into the draft through [onType] as it is
 * typed. An empty field says what is [missing] and one holding text that cannot be stored says the
 * rule, each in the label's place, once it was typed into; a field nobody touched yet, as a
 * question's just added, says nothing. The field keeps what was typed by [key], as a name field
 * does, and shows the latest [text] while nothing typed waits for Save ([settled]).
 */
@Composable
private fun TypedLineField(
    text: String?,
    key: String,
    label: String,
    missing: String,
    maximum: Int,
    isValid: (String) -> Boolean,
    settled: Boolean,
    enabled: Boolean,
    testTag: String,
    onType: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = rememberSaveable(key, saver = NameFieldText.Saver) { NameFieldText(text.orEmpty()) }
    field.follow(text, settled)
    val typed = field.text.trim(' ')
    MomentumTextField(
        value = field.text,
        onValueChange = { value ->
            field.type(value)
            onType(value)
        },
        label = label,
        error = when {
            !field.typed -> null
            typed.isEmpty() -> missing
            !isValid(typed) -> stringResource(R.string.questions_settings_text_invalid, maximum)
            else -> null
        },
        modifier = modifier,
        testTag = testTag,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
    )
}

/**
 * A question's help text, over several lines, taken into the draft as it is typed. It takes no
 * more than a help text may hold, so it never holds one that cannot be stored.
 */
@Composable
private fun HelpField(help: String?, key: String, settled: Boolean, testTag: String, onType: (String) -> Unit) {
    val field = rememberSaveable(key, saver = NameFieldText.Saver) { NameFieldText(help.orEmpty()) }
    field.follow(help, settled)
    OutlinedTextField(
        value = field.text,
        onValueChange = { value ->
            val kept = value.trim()
            if (kept.codePointCount(0, kept.length) <= MaximumQuestionHelpLength) {
                field.type(value)
                onType(value)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(testTag),
        label = { Text(stringResource(R.string.questions_settings_help)) },
        minLines = 2,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}
