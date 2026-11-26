package de.nicolasmahn.momentum.domain

import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.mo.MoRecordIndex
import de.nicolasmahn.momentum.domain.mo.TrackerObservation
import de.nicolasmahn.momentum.domain.reward.TrackerReward
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import java.time.LocalDate

/** A current kind owns the facts and targets derived from its typed configuration and records. */
internal interface TrackerRuntimeProvider {
    fun rewards(context: TrackerEvaluationContext): List<TrackerReward>
    fun observations(context: TrackerEvaluationContext): List<TrackerObservation>
}

/** One evaluation owns every index/cache; kind providers never keep mutable state between runs. */
internal class TrackerEvaluationContext(
    val records: MoRecordIndex,
    val visibility: FeatureVisibilitySettings,
    val today: LocalDate,
    val daysOff: HolidaySchedule,
    val rewardStart: LocalDate,
) {
    val state: AppState get() = records.state
    val checklistRecords by lazy(LazyThreadSafetyMode.NONE) { state.checklistDays.groupBy { it.checklistId } }
    val measurementRecords by lazy(LazyThreadSafetyMode.NONE) { state.measurementLogs.groupBy { it.measurementId } }
    val questionRecords by lazy(LazyThreadSafetyMode.NONE) { state.questionEntries.groupBy { it.viewId } }
    val timeRecords by lazy(LazyThreadSafetyMode.NONE) { state.timeLogs.groupBy { it.viewId } }
    private val checklistDates by lazy(LazyThreadSafetyMode.NONE) {
        checklistRecords.mapValues { (_, records) -> records.mapNotNull { parseDateKey(it.dateKey) }.toSet() }
    }

    private val firstMeasurementDates by lazy(LazyThreadSafetyMode.NONE) {
        measurementRecords.mapValues { (_, records) -> records.mapNotNull { parseDateKey(it.dateKey) }.minOrNull() }
    }
    private val firstTimeDates by lazy(LazyThreadSafetyMode.NONE) {
        timeRecords.mapValues { (_, records) -> records.mapNotNull { parseDateKey(it.dateKey) }.minOrNull() }
    }

    fun checklistRecordedDates(id: String): Set<LocalDate> = checklistDates[id].orEmpty()
    fun firstMeasurementDay(id: String): LocalDate? = firstMeasurementDates[id]
    fun firstTimeDay(id: String): LocalDate? = firstTimeDates[id]

    fun shown(ref: TrackerRef): (LocalDate) -> Boolean = { day -> ref.viewRefOrNull()?.let { visibility.isVisibleOn(it, day) } ?: true }
}
