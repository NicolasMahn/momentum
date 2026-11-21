package com.example.workouttracker.feature.statistics

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.care.testCareChecklist
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.questions.testEatingQuestions
import com.example.workouttracker.domain.time.testReadingTime
import com.example.workouttracker.domain.mo.MoDayRecord
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings

/** Historical reward fixtures explicitly include the configurations their records belonged to. */
internal fun historicalSummary(state: AppState, todayKey: String, range: StatisticsRange, visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(), streakHistory: List<MoDayRecord>? = null): StatisticsSummary =
    statisticsSummary(state.copy(
        checklists = state.checklists + listOf(testSupplementsChecklist(), testCareChecklist()).filter { preset -> state.checklists.none { it.id == preset.id } },
        measurements = state.measurements.ifEmpty { listOf(testWeightMeasurement()) },
        questions = state.questions.ifEmpty { listOf(testEatingQuestions()) },
        time = state.time.ifEmpty { listOf(testReadingTime()) },
    ), todayKey, range, visibility, streakHistory)
