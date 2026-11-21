package com.example.workouttracker.app

import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.time.nowMillis
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.addQuestions
import com.example.workouttracker.domain.questions.applyingQuestionsEdits
import com.example.workouttracker.domain.questions.deleteQuestions
import com.example.workouttracker.feature.questions.QuestionsDayChange
import com.example.workouttracker.persistence.TrackerRepository
import java.time.LocalDate

/**
 * Stores a questions tracker's settings and entries through [LoggedViewCommands]. Its creation
 * date is the habit day the save runs on, rather than the day setup opened, so it never asks
 * about a day before the tracker existed.
 */
internal class QuestionsCommands(
    repository: TrackerRepository,
    reconcile: (AppState) -> Boolean,
    exactAlarmsAllowed: () -> Boolean,
    refresh: () -> Unit,
    stored: (AppState) -> Unit,
    hidden: () -> Set<String>,
    forgetHidden: (viewId: String) -> Unit,
    today: () -> LocalDate = ::currentHabitDate,
    now: () -> Long = ::nowMillis,
) : LoggedViewCommands<QuestionsConfiguration, QuestionsEdit, QuestionsDayChange>(repository, reconcile, exactAlarmsAllowed, refresh, stored, hidden, forgetHidden, today, now) {
    override fun AppState.view(viewId: String): QuestionsConfiguration? = questions.firstOrNull { it.id == viewId }

    override fun AppState.applyingEdits(viewId: String, edits: List<QuestionsEdit>, day: LocalDate): AppState = applyingQuestionsEdits(viewId, edits, day)

    override fun AppState.adding(starter: QuestionsConfiguration, hidden: Set<String>, day: LocalDate): AppState = addQuestions(starter.copy(madeOn = day), hidden)

    override fun AppState.deleting(viewId: String): AppState = deleteQuestions(viewId)

    override fun AppState.with(view: QuestionsConfiguration): AppState = copy(questions = questions.map { if (it.id == view.id) view else it })
}
