package de.nicolasmahn.momentum.persistence.json

import de.nicolasmahn.momentum.domain.AppState

/** The current format stores capabilities explicitly; IDs carry no schema decisions. */
internal fun requireCurrentTrackerFormat(state: AppState) {
    de.nicolasmahn.momentum.domain.checklist.requireStorableChecklists(state.checklists, state.checklistDays)
    de.nicolasmahn.momentum.domain.measurement.requireStorableMeasurements(state.measurements)
    de.nicolasmahn.momentum.domain.questions.requireStorableQuestions(state.questions, state.questionEntries)
    de.nicolasmahn.momentum.domain.time.requireStorableTime(state.time, state.timeLogs)
    de.nicolasmahn.momentum.domain.activity.requireStorableActivity(state.activity, state.activityEntries, state.sessions)
}
