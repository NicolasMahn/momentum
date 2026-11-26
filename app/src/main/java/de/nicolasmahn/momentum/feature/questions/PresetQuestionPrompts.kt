package de.nicolasmahn.momentum.feature.questions

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration

/** Prompts, answer labels and help are the stored user data of this tracker. */
internal fun QuestionsConfiguration.presentedQuestions(): List<AskedQuestion> = questions.map { question ->
    AskedQuestion(
        id = question.id,
        text = UiText.Verbatim(question.text),
        answers = question.answers.map { AskedAnswer(it.id, UiText.Verbatim(it.label)) },
        help = question.help?.let { text ->
            QuestionHelp(uiText(R.string.questions_help_description)) { testTag, onDismiss ->
                QuestionHelpDialog(text, testTag, onDismiss)
            }
        },
    )
}
