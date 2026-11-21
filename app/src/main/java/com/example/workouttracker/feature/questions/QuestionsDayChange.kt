package com.example.workouttracker.feature.questions

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DayChange
import com.example.workouttracker.domain.DayChangeRefusal
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.deleteQuestionsDay
import com.example.workouttracker.domain.questions.questionsEntry
import com.example.workouttracker.domain.questions.saveQuestionsDay
import com.example.workouttracker.domain.questions.scalesOf

/**
 * What a questions view's screen asks to be stored for one day: its answers to save or the day's
 * entry to remove. It names the view and the day, and holds the entry the draft opened on
 * ([opened], null for a day without one), so it is decided against the entries as they are stored
 * at that moment and never against the state the screen happened to be shown.
 */
internal sealed interface QuestionsDayChange : DayChange {
    val viewId: String
    val dateKey: String
    val opened: QuestionEntry?

    /**
     * [answers] holds the answer of each question that has one, by question ID; a question
     * without one is "Not sure". A day saved for the first time is stored under [newEntryId].
     */
    data class Save(
        override val viewId: String,
        override val dateKey: String,
        override val opened: QuestionEntry?,
        val answers: Map<String, String>,
        val note: String?,
        val newEntryId: String,
    ) : QuestionsDayChange

    data class Remove(
        override val viewId: String,
        override val dateKey: String,
        override val opened: QuestionEntry?,
    ) : QuestionsDayChange

    /**
     * The view must still exist, by its configuration, and the day's entry must still be the one
     * the draft opened on. A draft or notification reply that outlives deletion must not recreate
     * that tracker or overwrite another entry.
     */
    override fun refusalIn(state: AppState): DayChangeRefusal? = when {
        state.questions.none { it.id == viewId } -> DayChangeRefusal.Deleted
        state.questionsEntry(viewId, dateKey) != opened -> DayChangeRefusal.ChangedElsewhere
        else -> null
    }

    /**
     * The answers are those the view offers as it is stored at this moment. What the entry holds
     * that the form could not show stays: an answer to a removed question, or a removed answer.
     */
    override fun appliedTo(state: AppState, timestampMillis: Long): AppState = when (this) {
        is Save -> state.copy(questionEntries = saveQuestionsDay(state.questionEntries, viewId, state.scalesOf(viewId), dateKey, timestampMillis, answers, note, newEntryId))
        is Remove -> state.copy(questionEntries = deleteQuestionsDay(state.questionEntries, viewId, dateKey))
    }
}
