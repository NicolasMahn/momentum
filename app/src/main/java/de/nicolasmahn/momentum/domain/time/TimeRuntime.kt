package de.nicolasmahn.momentum.domain.time

import de.nicolasmahn.momentum.domain.TrackerEvaluationContext
import de.nicolasmahn.momentum.domain.TrackerRuntimeProvider
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.include
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoRecordIndex
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import de.nicolasmahn.momentum.domain.mo.MoWeekProgress
import de.nicolasmahn.momentum.domain.mo.ObservationTarget
import de.nicolasmahn.momentum.domain.mo.TrackerObservation
import de.nicolasmahn.momentum.domain.mo.TrackerProgressPolicy
import de.nicolasmahn.momentum.domain.mo.datedActions
import de.nicolasmahn.momentum.domain.mo.rulesOn
import de.nicolasmahn.momentum.domain.mo.weekTarget
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.reward.TrackerReward
import de.nicolasmahn.momentum.domain.reward.weeklyTrackerReward
import de.nicolasmahn.momentum.domain.trackerRef
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** This kind derives runtime policy from its typed configuration and factual records. */
internal object TimeRuntime : TrackerRuntimeProvider {
    override fun rewards(context: TrackerEvaluationContext): List<TrackerReward> = with(context) {
        state.time.mapNotNull { it.trackerReward(records, visibility.visibilityOf(it.ref), daysOff, today, firstTimeDay(it.id)) }
    }

    override fun observations(context: TrackerEvaluationContext): List<TrackerObservation> = with(context) {
        buildList {
            state.time.forEach { timer ->
                val ref = timer.ref.trackerRef
                val actions = timeRecords[timer.id].orEmpty().groupBy { it.dateKey }
                    .mapValues { (_, sameDay) -> sameDay.maxOf { it.timestampMillis } }.datedActions()
                add(TrackerObservation(
                    ref, actions.keys.minOrNull(), actions,
                    ObservationTarget.WeeklyAmount(
                        { records.minutesInWeek(timer.id, it) }, { timer.weekAsks(it, daysOff) },
                        { timer.judgesWeekOn(it, daysOff) },
                    ),
                    shown(ref), shown(ref),
                    expectedInWeek = 1,
                    celebratesWeeklyGoal = true,
                    weekProgress = { monday -> MoWeekProgress(if (records.minutesInWeek(timer.id, monday) >= timer.weekAsks(monday, daysOff)) 1 else 0, 1) },
                    participatesInMood = timer.mo.enabled,
                    progressPolicy = TrackerProgressPolicy.RecordedParts,
                    label = timer.label,
                    content = MoContentKey(MoViewKind.Time, timer.mo.motif),
                    checkpoint = timer.checkpoint,
                ))
            }
        }
    }
}

private fun TimeConfiguration.trackerReward(
    records: MoRecordIndex,
    visibility: Dated<Boolean>,
    holidays: HolidaySchedule,
    today: LocalDate,
    firstRecordedDay: LocalDate?,
): TrackerReward? {
    val bank = rewards ?: return null
    val isWhole = { monday: LocalDate -> (0L until 7L).all { bank.takesPartOn(monday.plusDays(it)) } }
    return weeklyTrackerReward(
        ref.trackerRef, today, ::rulesOn,
        target = weekTarget(holidays, { records.minutesInWeek(id, it) }, isWhole),
        began = if (bank.periods == null) firstRecordedDay else bank.periods.firstOrNull()?.from,
        visibility = visibility,
        takesPartOn = { day -> isWhole(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) && !isNotDueAwayOn(day, holidays) },
        bankShownOn = { day -> visibility.on(day) && bank.takesPartOn(day) },
        recordedOn = { records.isLogged(ref, it) },
        earningBasisOn = { goalOfWeek(it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) },
    )
}
