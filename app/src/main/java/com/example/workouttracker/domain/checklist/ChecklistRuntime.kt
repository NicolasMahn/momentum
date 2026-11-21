package com.example.workouttracker.domain.checklist

import com.example.workouttracker.domain.judgmentDayFor
import com.example.workouttracker.domain.firstJudgmentDay
import com.example.workouttracker.domain.CheckpointPart
import com.example.workouttracker.domain.TrackerEvaluationContext
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerRuntimeProvider
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.mo.MoContentKey
import com.example.workouttracker.domain.mo.MoRecordIndex
import com.example.workouttracker.domain.mo.MoViewKind
import com.example.workouttracker.domain.mo.ObservationTarget
import com.example.workouttracker.domain.mo.TrackerObservation
import com.example.workouttracker.domain.mo.TrackerProgressPolicy
import com.example.workouttracker.domain.mo.datedActions
import com.example.workouttracker.domain.mo.rulesOn
import com.example.workouttracker.domain.reward.TrackerReward
import com.example.workouttracker.domain.reward.dailyTrackerReward
import java.time.LocalDate

/** This kind derives runtime policy from its typed configuration and factual records. */
internal object ChecklistRuntime : TrackerRuntimeProvider {
    override fun rewards(context: TrackerEvaluationContext): List<TrackerReward> = with(context) {
        state.checklists.mapNotNull { it.trackerReward(records, visibility.checklistVisibility(it.id), daysOff, today, checklistRecordedDates(it.id)) }
    }

    override fun observations(context: TrackerEvaluationContext): List<TrackerObservation> = with(context) {
        buildList {
            state.checklists.forEach { checklist ->
                val ref = TrackerRef(TrackerTypeId("checklist"), checklist.id)
                val actions = checklistRecords[checklist.id].orEmpty().groupBy { it.dateKey }
                    .filter { (day, sameDay) ->
                        val progress = records.progress(checklist, LocalDate.parse(day))
                        sameDay.any { entry -> entry.ticks.any { it in progress.dueTicks } }
                    }
                    .mapValues { (_, sameDay) -> sameDay.maxOf { it.timestampMillis } }.datedActions(checklist::judgmentDayFor)
                val parts = { day: LocalDate -> checklist.recordJudgedOn(day)?.let { records.progress(checklist, it).checkpoints.map { CheckpointPart(it.checkpoint.id) } }.orEmpty() }
                add(TrackerObservation(
                    ref = ref,
                    firstDay = checklist.firstJudgmentDay(checklistRecordedDates(checklist.id)),
                    recordedAt = actions,
                    target = ObservationTarget.Daily(
                        keptOn = { day -> checklist.recordJudgedOn(day)?.let { records.progress(checklist, it).isComplete } ?: false },
                        asksNothingOn = { day -> checklist.recordJudgedOn(day)?.let { records.progress(checklist, it).asksNothing || daysOff.isSick(it) } ?: true },
                    ),
                    visibleOn = shown(ref),
                    countsOn = shown(ref),
                    partialActionRelief = true,
                    partsOn = parts,
                    partKeptOn = { part, day -> part is CheckpointPart && checklist.recordJudgedOn(day)?.let { recorded ->
                        records.progress(checklist, recorded).checkpoints.filter { it.checkpoint.id == part.checkpointId }.all { it.unmet.isEmpty() }
                    } == true },
                    owedPartsOn = { day -> checklist.recordJudgedOn(day)?.let { recorded -> parts(day).filter { records.progress(checklist, recorded).owesAt(it.checkpointId) } }.orEmpty() },
                    participatesInMood = checklist.mo.enabled,
                    label = checklist.label,
                    content = MoContentKey(MoViewKind.Checklist, checklist.mo.motif),
                    checklist = checklist,
                    progressPolicy = TrackerProgressPolicy.RecordedParts,
                    recordedActionKeysOn = { day -> checklist.recordJudgedOn(day)?.let { records.progress(checklist, it).recorded.ticks }.orEmpty() },
                    owedActionKeysOn = { day -> checklist.recordJudgedOn(day)?.let { records.progress(checklist, it).owedTicks }.orEmpty() },
                    assumedOn = { day -> checklist.recordJudgedOn(day)?.let { records.progress(checklist, it).isAssumed } == true },
                ))
            }
        }
    }
}

private fun ChecklistConfiguration.trackerReward(
    records: MoRecordIndex,
    visibility: Dated<Boolean>,
    holidays: HolidaySchedule,
    today: LocalDate,
    recordedDates: Set<LocalDate>,
): TrackerReward? {
    val bank = rewards ?: return null
    return dailyTrackerReward(
        TrackerRef(TrackerTypeId("checklist"), id), today, bank::rulesOn,
        began = if (bank.periods == null) firstJudgmentDay(recordedDates) else bank.periods.firstOrNull()?.from,
        visibility = visibility,
        openOn = { day -> recordJudgedOn(day)?.let { records.progress(this, it).gaps } ?: 0 },
        takesPartOn = { day -> bank.takesPartOn(day) && recordJudgedOn(day)?.let { !holidays.isSick(it) && !records.progress(this, it).asksNothing } == true },
        bankShownOn = { day -> visibility.on(day) && bank.takesPartOn(day) && isInUse(day) },
    )
}
