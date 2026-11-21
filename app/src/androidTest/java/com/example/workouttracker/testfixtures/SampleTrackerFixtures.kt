package com.example.workouttracker.testfixtures

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.ViewRef

internal val AppState.weightMeasurement get() = measurements.firstOrNull { it.id == WeightMeasurementId }
internal val AppState.eatingQuestions get() = questions.firstOrNull { it.id == EatingQuestionsId }
internal val AppState.readingTime get() = time.firstOrNull { it.id == ReadingTimeId }
internal val AppState.careChecklist get() = checklists.firstOrNull { it.id == CareChecklistId }
internal val AppState.supplementsChecklist get() = checklists.firstOrNull { it.id == SupplementsChecklistId }
internal val AppState.madeMeasurements get() = measurements.filter { it.id != WeightMeasurementId }
internal val AppState.madeQuestions get() = questions.filter { it.id != EatingQuestionsId }
internal val AppState.madeTime get() = time.filter { it.id != ReadingTimeId }
internal val AppState.configuredChecklists get() = checklists.filter { it.id != CareChecklistId && it.id != SupplementsChecklistId }

/** Selects explicitly configured examples for the fixture display only. */
internal enum class TestTask(val view: ViewRef) {
    Activity(ViewRef.Feature(DashboardFeature.Activity)),
    Reading(ViewRef.Logged(LoggedViewRef(LoggedViewKind.Time, ReadingTimeId))),
    Care(ViewRef.Checklist(CareChecklistId)),
    Supplements(ViewRef.Checklist(SupplementsChecklistId)),
    Eating(ViewRef.Logged(LoggedViewRef(LoggedViewKind.Questions, EatingQuestionsId))),
    Weight(ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, WeightMeasurementId))),
}
