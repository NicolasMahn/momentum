package de.nicolasmahn.momentum.feature.questions

import androidx.compose.runtime.Composable
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.joinedBy
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration

/** Every questions tracker shares controls; only its configured prompts and help differ. */
internal fun QuestionsConfiguration.words(): QuestionsWords = QuestionsWords(this)

/** Stable UI identity includes both the kind and instance. */
internal val QuestionsConfiguration.testTagPrefix: String get() = "questions_$id"

/** One question as the screen asks it. Its ID and its answers' are the ones an entry stores. */
internal data class AskedQuestion(
    val id: String,
    val text: UiText,
    val answers: List<AskedAnswer>,
    val help: QuestionHelp? = null,
)

internal data class AskedAnswer(val id: String, val label: UiText)

/**
 * What a question's help button says for TalkBack and what it opens. [dialog] is handed the tag
 * the button has, to name its own nodes after, and what closes it.
 */
internal class QuestionHelp(
    val description: UiText,
    val dialog: @Composable (testTag: String, onDismiss: () -> Unit) -> Unit,
)

/** Generic action wording is independent of a tracker's preset, name and question schema. */
internal class QuestionsWords(view: QuestionsConfiguration) {
    val questions: List<AskedQuestion> = view.presentedQuestions()

    val notSure = uiText(R.string.questions_not_sure)
    val moreOptions = uiText(R.string.questions_more_options)
    val noteLabel = uiText(R.string.questions_note_label)
    val save = uiText(R.string.questions_save)
    val saveChanges = uiText(R.string.questions_save_changes)
    val saved = uiText(R.string.questions_saved)

    fun remove(day: UiText) = uiText(R.string.questions_remove, day)

    val saveFailed = uiText(R.string.questions_error_save_failed)
    val removalFailed = uiText(R.string.questions_error_delete_failed)
    val dayWithoutEntry = uiText(R.string.questions_status_none)

    fun dayAnswers(answers: List<AskedAnswer?>): UiText =
        answers.map { it?.label ?: uiText(R.string.questions_status_not_sure) }.joinedBy("; ")

    val selectDay = uiText(R.string.questions_action_select)
    val openDay = uiText(R.string.questions_action_open)
}
