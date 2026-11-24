package de.nicolasmahn.momentum.domain.substance

import de.nicolasmahn.momentum.domain.SubstancesTrackerRef
import de.nicolasmahn.momentum.domain.TrackerEvaluationContext
import de.nicolasmahn.momentum.domain.TrackerRuntimeProvider
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.include
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoRecordIndex
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import de.nicolasmahn.momentum.domain.mo.ObservationTarget
import de.nicolasmahn.momentum.domain.mo.TrackerObservation
import de.nicolasmahn.momentum.domain.mo.TrackerProgressPolicy
import de.nicolasmahn.momentum.domain.mo.datedActions
import de.nicolasmahn.momentum.domain.mo.rulesOn
import de.nicolasmahn.momentum.domain.reward.TrackerReward
import de.nicolasmahn.momentum.domain.reward.dailyTrackerReward
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import java.time.LocalDate

/** This kind derives runtime policy from its typed configuration and factual records. */
internal object SubstanceRuntime : TrackerRuntimeProvider {
    override fun rewards(context: TrackerEvaluationContext): List<TrackerReward> = with(context) {
        listOfNotNull(state.substances.trackerReward(records, visibility.visibilityOf(DashboardFeature.Substances), daysOff, today))
    }

    override fun observations(context: TrackerEvaluationContext): List<TrackerObservation> = with(context) {
        buildList {
            // Each due day reads the record it judges, through the substances' own rule of days; an
            // action counts on the due day that judges its record.
            val substances = state.substances
            val substanceActions = (state.substanceLogs.map { it.dateKey to it.timestampMillis } + state.substanceNoneDays.map { it.dateKey to it.timestampMillis })
                .groupBy({ it.first }, { it.second }).mapValues { (_, times) -> times.max() }.datedActions(::substanceJudgmentDayFor)
            // Under "only log uses" a day is kept with or without a record, so a record there is
            // nothing Mo acts out: whether one was made must never change what he does.
            val asked = substanceActions.filterKeys { day -> substanceRecordJudgedOn(day).let { !(substances.goal.on(it) == SubstanceGoal.StayingAway && substances.exceptionsOnly.on(it)) } }
            add(TrackerObservation(
                SubstancesTrackerRef, substanceActions.keys.minOrNull(), asked,
                ObservationTarget.Daily(
                    { day -> substances.keepsOn(substanceRecordJudgedOn(day), records) },
                    excusedOn = { day -> substanceRecordJudgedOn(day).let { daysOff.isHoliday(it) && substances.whenAway.on(it) == WhenAway.Excused } },
                    asksNothingOn = { day -> !substances.asksOn(substanceRecordJudgedOn(day)) },
                ), shown(SubstancesTrackerRef),
                shown(SubstancesTrackerRef),
                participatesInMood = substances.mo.enabled,
                progressPolicy = TrackerProgressPolicy.RecordedParts,
                partKeptOn = { _, day -> substances.keepsOn(substanceRecordJudgedOn(day), records) },
                assumedOn = { day ->
                    substanceRecordJudgedOn(day).let { recorded ->
                        substances.goal.on(recorded) == SubstanceGoal.StayingAway &&
                            substances.exceptionsOnly.on(recorded) && substances.keepsOn(recorded, records) &&
                            !records.isSubstancesLogged(recorded)
                    }
                },
                checkpoint = de.nicolasmahn.momentum.domain.checklist.Checkpoint(
                    id = de.nicolasmahn.momentum.domain.checklist.DayCheckpointId,
                    at = substances.checkIn.at,
                    remind = substances.checkIn.remind,
                ),
                label = substances.label,
                content = MoContentKey(MoViewKind.Checklist, substances.mo.motif),
            ))
        }
    }
}

/**
 * Participation, the bank's rules and whether it is shown belong to the due day; whether the day
 * is kept, asks anything or is excused belongs to the record it judges, read by that record's own
 * goal and stance.
 */
private fun SubstanceSettings.trackerReward(
    records: MoRecordIndex,
    visibility: Dated<Boolean>,
    holidays: HolidaySchedule,
    today: LocalDate,
): TrackerReward? {
    val bank = rewards ?: return null
    return dailyTrackerReward(
        SubstancesTrackerRef, today, bank::rulesOn, bank.periods?.firstOrNull()?.from, visibility,
        openOn = { day -> if (keepsOn(substanceRecordJudgedOn(day), records)) 0 else 1 },
        takesPartOn = { day -> bank.takesPartOn(day) && asksOn(substanceRecordJudgedOn(day)) },
        bankShownOn = { day -> visibility.on(day) && bank.takesPartOn(day) && asksOn(substanceRecordJudgedOn(day)) },
        excusedOn = { day -> substanceRecordJudgedOn(day).let { holidays.isHoliday(it) && whenAway.on(it) == WhenAway.Excused } },
    )
}

private fun SubstanceSettings.keepsOn(day: LocalDate, records: MoRecordIndex): Boolean = when (goal.on(day)) {
    SubstanceGoal.Logging -> records.isSubstancesLogged(day)
    SubstanceGoal.StayingAway -> !records.isSubstanceUsedOn(day, stayingAway.on(day)) && (exceptionsOnly.on(day) || records.isSubstancesLogged(day))
}
