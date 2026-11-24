package de.nicolasmahn.momentum.domain.measurement

import de.nicolasmahn.momentum.domain.TrackerEvaluationContext
import de.nicolasmahn.momentum.domain.TrackerRuntimeProvider
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.isExcusedOn
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoRecordIndex
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import de.nicolasmahn.momentum.domain.mo.ObservationTarget
import de.nicolasmahn.momentum.domain.mo.TrackerObservation
import de.nicolasmahn.momentum.domain.mo.TrackerProgressPolicy
import de.nicolasmahn.momentum.domain.mo.datedActions
import de.nicolasmahn.momentum.domain.mo.rulesOn
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.reward.TrackerReward
import de.nicolasmahn.momentum.domain.reward.dailyTrackerReward
import de.nicolasmahn.momentum.domain.trackerRef
import java.time.LocalDate

/** This kind derives runtime policy from its typed configuration and factual records. */
internal object MeasurementRuntime : TrackerRuntimeProvider {
    override fun rewards(context: TrackerEvaluationContext): List<TrackerReward> = with(context) {
        state.measurements.mapNotNull { it.trackerReward(records, visibility.visibilityOf(it.ref), daysOff, today, firstMeasurementDay(it.id)) }
    }

    override fun observations(context: TrackerEvaluationContext): List<TrackerObservation> = with(context) {
        buildList {
            state.measurements.forEach { measurement ->
                val ref = measurement.ref.trackerRef
                val actions = measurementRecords[measurement.id].orEmpty()
                    .groupBy { it.dateKey }.mapValues { (_, sameDay) -> sameDay.maxOf { it.timestampMillis } }.datedActions()
                add(TrackerObservation(
                    ref, actions.keys.minOrNull(), actions,
                    ObservationTarget.Daily(actions::containsKey, excusedOn = { measurement.isExcusedOn(it, daysOff) }),
                    shown(ref), shown(ref),
                    participatesInMood = measurement.mo.enabled,
                    progressPolicy = TrackerProgressPolicy.RecordedParts,
                    label = measurement.label,
                    content = MoContentKey(MoViewKind.Measurement, measurement.mo.motif),
                    checkpoint = measurement.checkpoint,
                ))
            }
        }
    }
}

private fun MeasurementConfiguration.trackerReward(
    records: MoRecordIndex,
    visibility: Dated<Boolean>,
    holidays: HolidaySchedule,
    today: LocalDate,
    firstRecordedDay: LocalDate?,
): TrackerReward? {
    val bank = rewards ?: return null
    val periods = bank.periods
    return dailyTrackerReward(
        ref.trackerRef, today, bank::rulesOn, if (periods == null) firstRecordedDay else periods.firstOrNull()?.from, visibility,
        openOn = { day -> if (records.isLogged(ref, day)) 0 else 1 },
        takesPartOn = { day -> bank.takesPartOn(day) && !isExcusedOn(day, holidays) },
        excusedOn = { day -> isExcusedOn(day, holidays) },
        bankShownOn = { day -> visibility.on(day) && bank.takesPartOn(day) },
    )
}
