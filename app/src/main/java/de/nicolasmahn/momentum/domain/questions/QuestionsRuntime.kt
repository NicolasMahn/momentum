package de.nicolasmahn.momentum.domain.questions

import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.judgmentDayFor
import de.nicolasmahn.momentum.domain.TrackerEvaluationContext
import de.nicolasmahn.momentum.domain.TrackerRuntimeProvider
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.include
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
internal object QuestionsRuntime : TrackerRuntimeProvider {
    override fun rewards(context: TrackerEvaluationContext): List<TrackerReward> = with(context) {
        state.questions.mapNotNull { questions ->
            val firstDueDay = questionRecords[questions.id].orEmpty().mapNotNull { entry ->
                parseDateKey(entry.dateKey)?.let(questions::judgmentDayFor)
            }.minOrNull()
            questions.trackerReward(records, visibility.visibilityOf(questions.ref), daysOff, today, firstDueDay)
        }
    }

    override fun observations(context: TrackerEvaluationContext): List<TrackerObservation> = with(context) {
        buildList {
            state.questions.forEach { questions ->
                val ref = questions.ref.trackerRef
                val actions = questionRecords[questions.id].orEmpty().groupBy { it.dateKey }
                    .mapValues { (_, sameDay) -> sameDay.maxOf { it.timestampMillis } }.datedActions(questions::judgmentDayFor)
                add(TrackerObservation(
                    ref, actions.keys.minOrNull(), actions,
                    ObservationTarget.Daily(
                        keptOn = { day -> questions.recordJudgedOn(day)?.let { records.isLogged(questions.ref, it) } ?: false },
                        excusedOn = { day -> questions.recordJudgedOn(day)?.let { questions.isExcusedOn(it, daysOff) || daysOff.isSick(it) } ?: true },
                    ),
                    shown(ref), shown(ref),
                    participatesInMood = questions.mo.enabled,
                    progressPolicy = TrackerProgressPolicy.RecordedParts,
                    label = questions.label,
                    content = MoContentKey(MoViewKind.Questions, questions.mo.motif),
                    checkpoint = questions.checkpoint,
                ))
            }
        }
    }
}

private fun QuestionsConfiguration.trackerReward(
    records: MoRecordIndex,
    visibility: Dated<Boolean>,
    holidays: HolidaySchedule,
    today: LocalDate,
    firstRecordedDay: LocalDate?,
): TrackerReward? {
    val bank = rewards ?: return null
    return dailyTrackerReward(
        ref.trackerRef, today, bank::rulesOn, if (bank.periods == null) firstRecordedDay else bank.periods.firstOrNull()?.from, visibility,
        openOn = { day -> recordJudgedOn(day)?.let { if (records.isLogged(ref, it)) 0 else 1 } ?: 0 },
        takesPartOn = { day -> bank.takesPartOn(day) && recordJudgedOn(day)?.let { !holidays.isSick(it) && !isExcusedOn(it, holidays) } == true },
        bankShownOn = { day -> visibility.on(day) && bank.takesPartOn(day) },
    )
}
