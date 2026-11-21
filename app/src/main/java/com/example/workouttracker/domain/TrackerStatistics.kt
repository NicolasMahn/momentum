package com.example.workouttracker.domain

import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.core.time.isValidDateKey
import com.example.workouttracker.core.time.requireValidDateKey
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.recordJudgedOn
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.settings.ViewRef
import java.time.LocalDate

/** Factual recorded days supplied by a tracker module, independent of rewards and protection. */
data class TrackerStatistics(
    val recordedDateKeys: Set<String>,
    val unit: TrackerStatisticsUnit = TrackerStatisticsUnit.RecordedDays,
    /** A delayed record is assigned once, including on a transition between today and yesterday. */
    private val recordDateForDisplayDay: (LocalDate) -> LocalDate? = { it },
) {
    init {
        recordedDateKeys.forEach(::requireValidDateKey)
    }

    fun isRecordedFor(dateKey: String): Boolean =
        parseDateKey(dateKey)?.let { recordDateForDisplayDay(it)?.toString() in recordedDateKeys } ?: false
}

/** A daily share or recorded days per week; the tracker chooses how its facts are best understood. */
enum class TrackerStatisticsUnit { RecordedDays, DaysPerWeek }

/** Configuration names the tracker; facts describe only records that actually exist. */
data class TrackerStatisticsContribution(
    val ref: TrackerRef,
    val label: String,
    val statistics: TrackerStatistics,
)

/** Canonical kinds share one indexed factual reader with their modules and standalone reports. */
class TrackerStatisticsIndex(state: AppState) {
    private val checklistDates by lazy {
        state.checklistDays.groupBy { it.checklistId }.mapValues { (_, records) ->
            records.mapNotNullTo(HashSet()) { it.dateKey.takeIf(::isValidDateKey) }
        }
    }
    private val measurementDates by lazy {
        state.measurementLogs.groupBy { it.measurementId }.mapValues { (_, records) ->
            records.mapNotNullTo(HashSet()) { it.dateKey.takeIf(::isValidDateKey) }
        }
    }
    private val questionDates by lazy {
        state.questionEntries.groupBy { it.viewId }.mapValues { (_, records) ->
            records.mapNotNullTo(HashSet()) { it.dateKey.takeIf(::isValidDateKey) }
        }
    }
    private val timerDates by lazy {
        state.timeLogs.groupBy { it.viewId }.mapValues { (_, records) ->
            records.mapNotNullTo(HashSet()) { it.dateKey.takeIf(::isValidDateKey) }
        }
    }

    fun facts(view: ChecklistConfiguration) = TrackerStatistics(checklistDates[view.id].orEmpty(), recordDateForDisplayDay = view::recordJudgedOn)
    fun facts(view: MeasurementConfiguration) = TrackerStatistics(measurementDates[view.id].orEmpty())
    fun facts(view: QuestionsConfiguration) = TrackerStatistics(questionDates[view.id].orEmpty(), recordDateForDisplayDay = view::recordJudgedOn)
    fun facts(view: TimeConfiguration) = TrackerStatistics(timerDates[view.id].orEmpty())
}

/** Standalone reports use the same providers that canonical modules expose through their capability. */
fun AppState.trackerStatisticsContributions(visibility: FeatureVisibilitySettings): List<TrackerStatisticsContribution> {
    val index = TrackerStatisticsIndex(this)
    return buildList {
        checklists.filter { visibility.isChecklistVisible(it.id) }.forEach { view ->
            add(TrackerStatisticsContribution(ViewRef.Checklist(view.id).trackerRef, view.label, index.facts(view)))
        }
        measurements.filter { visibility.shows(it.ref) }.forEach { view ->
            add(TrackerStatisticsContribution(view.ref.trackerRef, view.label, index.facts(view)))
        }
        questions.filter { visibility.shows(it.ref) }.forEach { view ->
            add(TrackerStatisticsContribution(view.ref.trackerRef, view.label, index.facts(view)))
        }
        time.filter { visibility.shows(it.ref) }.forEach { view ->
            add(TrackerStatisticsContribution(view.ref.trackerRef, view.label, index.facts(view)))
        }
    }
}
