package com.example.workouttracker.feature.questions

import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.time.calendarWeeksWindow
import com.example.workouttracker.core.time.requireValidDateKey
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.QuestionScale
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.answerOn
import com.example.workouttracker.domain.questions.questionsEntry

/**
 * What the form holds for one day before it is saved: what was picked for each question, by
 * question ID, and the note. A question nothing was picked for is not among [answers], so an
 * untouched form is equal to a fresh one.
 */
internal data class QuestionsDraft(
    val dateKey: String,
    val answers: Map<String, QuestionAnswer> = emptyMap(),
    val note: String = "",
) {
    fun answerTo(questionId: String): QuestionAnswer = answers[questionId] ?: QuestionAnswer.Unanswered

    fun answering(questionId: String, answer: QuestionAnswer): QuestionsDraft =
        copy(answers = if (answer == QuestionAnswer.Unanswered) answers - questionId else answers + (questionId to answer))

    /** The answers to store, question ID to answer ID: "Not sure" and unanswered both store nothing. */
    val storedAnswers: Map<String, String>
        get() = answers.mapNotNull { (questionId, answer) -> answer.storedAnswerId?.let { questionId to it } }.toMap()
}

/**
 * What the form shows for one question. Storage keeps only the answer, so a blank question is
 * absent there whether it was skipped or answered "Not sure"; the form has to tell them apart to
 * show which tile is selected, so the distinction lives here and never reaches the entry.
 */
internal sealed interface QuestionAnswer {
    /** Nothing picked yet. */
    data object Unanswered : QuestionAnswer

    /** "Not sure" picked: the question was considered and has no answer. */
    data object NotSure : QuestionAnswer

    data class Answered(val answerId: String) : QuestionAnswer
}

/** The answer to store: "Not sure" and unanswered both save as none. */
internal val QuestionAnswer.storedAnswerId: String?
    get() = (this as? QuestionAnswer.Answered)?.answerId

/**
 * A saved entry means the questions were considered, so a blank one reads as "Not sure". Only a
 * day without an entry is unanswered.
 */
internal fun questionAnswerFor(answerId: String?, hasEntry: Boolean): QuestionAnswer = when {
    answerId != null -> QuestionAnswer.Answered(answerId)
    hasEntry -> QuestionAnswer.NotSure
    else -> QuestionAnswer.Unanswered
}

/** Tapping the selected answer clears it, so an answer can be taken back without a second control. */
internal fun toggleAnswer(current: QuestionAnswer, tapped: String): QuestionAnswer =
    if (current == QuestionAnswer.Answered(tapped)) QuestionAnswer.Unanswered else QuestionAnswer.Answered(tapped)

internal fun toggleNotSure(current: QuestionAnswer): QuestionAnswer =
    if (current is QuestionAnswer.NotSure) QuestionAnswer.Unanswered else QuestionAnswer.NotSure

/**
 * The form a saved day opens with: every question the view [asked] shows its answer, or "Not sure"
 * where the entry holds none it still has.
 */
internal fun QuestionEntry.toDraft(asked: List<QuestionScale>): QuestionsDraft = QuestionsDraft(
    dateKey = dateKey,
    answers = asked.associate { scale -> scale.questionId to questionAnswerFor(answerOn(scale), hasEntry = true) },
    note = note.orEmpty(),
)

/**
 * The form of a questions view's screen: what it holds for the selected day ([draft]) and the entry
 * of that day it opened on ([opened], null for a day without one). A save is decided against the
 * entry the form opened on, so the two are one value and never go apart.
 */
internal data class QuestionsForm(val draft: QuestionsDraft, val opened: QuestionEntry? = null) {
    /** The selected day. */
    val dateKey: String get() = draft.dateKey

    /** Whether the form holds what it opened with: nothing was picked or typed since. */
    fun isUntouched(asked: List<QuestionScale>): Boolean = draft == (opened?.toDraft(asked) ?: QuestionsDraft(dateKey))

    /** Whether the day is stored exactly as the form shows it, so nothing is left to save. */
    fun isSaved(asked: List<QuestionScale>): Boolean = opened != null && isUntouched(asked)
}

/**
 * The form [view]'s screen shows for [dateKey]: the day's entry wherever it has one, so a day that
 * is answered is never met with nothing picked, and a fresh form for a day without one.
 */
internal fun AppState.questionsForm(view: QuestionsConfiguration, dateKey: String): QuestionsForm {
    val entry = questionsEntry(view.id, dateKey)
    return QuestionsForm(entry?.toDraft(view.scales) ?: QuestionsDraft(dateKey), entry)
}

/**
 * The day the screen opens on: the one today asks about, today for a view about today and
 * yesterday for one about yesterday, or today itself while that day is before the view was made.
 */
internal fun QuestionsConfiguration.defaultDateKey(todayKey: String): String =
    requireValidDateKey(todayKey).let { today -> recordDayFor(today) ?: today }.toString()

internal data class QuestionsHistoryDay(val dateKey: String, val entry: QuestionEntry?)

internal data class QuestionsHistoryWindow(val days: List<QuestionsHistoryDay>)

/** The recent weeks of one view, each day with the one entry [entries], the view's own, hold for it. */
internal fun questionsHistoryWindow(entries: List<QuestionEntry>, todayKey: String): QuestionsHistoryWindow {
    val weeks = calendarWeeksWindow(todayKey)
    val byDate = weeks.groupVisible(entries, QuestionEntry::dateKey)
    return QuestionsHistoryWindow(weeks.dateKeys.map { dateKey -> QuestionsHistoryDay(dateKey, byDate[dateKey]?.firstOrNull()) })
}

/** What a calendar day says for TalkBack: each question's answer, in the view's words. */
internal fun questionsHistoryStatus(day: QuestionsHistoryDay, words: QuestionsWords): UiText {
    val entry = day.entry ?: return words.dayWithoutEntry
    return words.dayAnswers(words.questions.map { question -> question.answers.firstOrNull { it.id == entry.answers[question.id] } })
}
