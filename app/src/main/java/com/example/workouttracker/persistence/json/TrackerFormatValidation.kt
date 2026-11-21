package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.AppState

/** The current format stores capabilities explicitly; IDs carry no schema decisions. */
internal fun requireCurrentTrackerFormat(state: AppState) {
    com.example.workouttracker.domain.checklist.requireStorableChecklists(state.checklists, state.checklistDays)
    com.example.workouttracker.domain.measurement.requireStorableMeasurements(state.measurements)
    com.example.workouttracker.domain.questions.requireStorableQuestions(state.questions, state.questionEntries)
    com.example.workouttracker.domain.time.requireStorableTime(state.time, state.timeLogs)
    com.example.workouttracker.domain.activity.requireStorableActivity(state.activity, state.activityEntries, state.sessions)
}
