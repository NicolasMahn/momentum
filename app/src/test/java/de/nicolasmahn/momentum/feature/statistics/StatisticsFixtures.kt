package de.nicolasmahn.momentum.feature.statistics

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.care.testCareChecklist
import de.nicolasmahn.momentum.domain.supplement.testSupplementsChecklist
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.questions.testEatingQuestions
import de.nicolasmahn.momentum.domain.time.testReadingTime
import de.nicolasmahn.momentum.domain.mo.MoDayRecord
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings

/** Historical reward fixtures explicitly include the configurations their records belonged to. */
internal fun historicalSummary(state: AppState, todayKey: String, range: StatisticsRange, visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(), streakHistory: List<MoDayRecord>? = null): StatisticsSummary =
    statisticsSummary(state.copy(
        checklists = state.checklists + listOf(testSupplementsChecklist(), testCareChecklist()).filter { preset -> state.checklists.none { it.id == preset.id } },
        measurements = state.measurements.ifEmpty { listOf(testWeightMeasurement()) },
        questions = state.questions.ifEmpty { listOf(testEatingQuestions()) },
        time = state.time.ifEmpty { listOf(testReadingTime()) },
    ), todayKey, range, visibility, streakHistory)
