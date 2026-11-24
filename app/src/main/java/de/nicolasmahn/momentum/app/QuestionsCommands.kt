package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.time.nowMillis
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration
import de.nicolasmahn.momentum.domain.questions.QuestionsEdit
import de.nicolasmahn.momentum.domain.questions.addQuestions
import de.nicolasmahn.momentum.domain.questions.applyingQuestionsEdits
import de.nicolasmahn.momentum.domain.questions.deleteQuestions
import de.nicolasmahn.momentum.feature.questions.QuestionsDayChange
import de.nicolasmahn.momentum.persistence.TrackerRepository
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
