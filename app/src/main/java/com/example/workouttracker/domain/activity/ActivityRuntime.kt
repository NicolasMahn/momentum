package com.example.workouttracker.domain.activity

import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.TrackerEvaluationContext
import com.example.workouttracker.domain.TrackerRuntimeProvider
import com.example.workouttracker.domain.checklist.include
import com.example.workouttracker.domain.mo.MoContentKey
import com.example.workouttracker.domain.mo.MoViewKind
import com.example.workouttracker.domain.mo.MoWeekProgress
import com.example.workouttracker.domain.mo.TrackerProgressPolicy
import com.example.workouttracker.domain.mo.ObservationTarget
import com.example.workouttracker.domain.mo.TrackerObservation
import com.example.workouttracker.domain.mo.datedActions
import com.example.workouttracker.domain.mo.monday
import com.example.workouttracker.domain.mo.rulesOn
import com.example.workouttracker.domain.reward.RewardTarget
import com.example.workouttracker.domain.reward.TrackerReward
import com.example.workouttracker.domain.reward.weeklyTrackerReward
import java.time.DayOfWeek
import java.time.LocalDate

/** This kind derives runtime policy from its typed configuration and factual records. */
internal object ActivityRuntime : TrackerRuntimeProvider {
    override fun rewards(context: TrackerEvaluationContext): List<TrackerReward> = with(context) {
        if (state.activity == null) return@with emptyList()
        val activity = state.activityRules
        val began = (state.activityEntries.map { it.dateKey } + state.sessions.filter { it.totalLoggedSets > 0 }.map { it.dateKey }).mapNotNull(::parseDateKey).minOrNull()
        listOf(weeklyTrackerReward(
            ActivityTrackerRef, today, activity::rulesOn,
            RewardTarget.WeekTimes(records::isActive, { activity.freeDaysOn(it, daysOff) }, activity::spanStartOn),
            began, visibility.visibilityOf(com.example.workouttracker.domain.settings.DashboardFeature.Activity),
            takesPartOn = { activity.rewards.periods.include(it) && !activity.isNotDueAwayOn(it, daysOff) },
            bankShownOn = { shown(ActivityTrackerRef)(it) && activity.rewards.periods.include(it) },
        ))
    }

    override fun observations(context: TrackerEvaluationContext): List<TrackerObservation> = with(context) {
        buildList {
            if (state.activity != null) {
                val activity = state.activityRules
                val actions = (state.activityEntries.map { it.dateKey to it.timestampMillis } + state.sessions.filter { it.totalLoggedSets > 0 }.map { it.dateKey to it.timestampMillis })
                    .groupBy({ it.first }, { it.second }).mapValues { (_, times) -> times.max() }.datedActions()
                fun weekProgress(monday: LocalDate): MoWeekProgress {
                    val kept = (0L until 7L).count { offset ->
                        val day = monday.plusDays(offset)
                        !daysOff.isSick(day) && !activity.isNotDueAwayOn(day, daysOff) && records.isActive(day)
                    }
                    return MoWeekProgress(kept, activity.weekAsks(monday, daysOff))
                }
                fun planned(day: LocalDate) = day.dayOfWeek in activity.week.on(day).chosen
                // The plan's days ask for a workout until the week is done. A day the week names a rest
                // day asks too once the planned days left cannot finish the week: resting on a planned
                // day took its rest day.
                fun asks(day: LocalDate): Boolean {
                    val progress = weekProgress(day.monday())
                    val needed = progress.expected - progress.done
                    if (needed <= 0) return false
                    if (planned(day)) return true
                    val plannedLeft = generateSequence(day.plusDays(1)) { it.plusDays(1) }
                        .takeWhile { it.dayOfWeek != DayOfWeek.MONDAY }
                        .count { planned(it) && !daysOff.isSick(it) && !activity.isNotDueAwayOn(it, daysOff) }
                    return needed > plannedLeft
                }
                add(TrackerObservation(
                    ActivityTrackerRef, actions.keys.minOrNull(), actions,
                    ObservationTarget.Daily(records::isActive, { day, _ -> activity.freeDaysOn(day, daysOff) }, activity::spanStartOn, { activity.isNotDueAwayOn(it, daysOff) }),
                    shown(ActivityTrackerRef), shown(ActivityTrackerRef),
                    expectedInWeek = activity.daysAWeekOf(today.monday()),
                    participatesInMood = activity.mo.enabled,
                    celebratesWeeklyGoal = true,
                    progressPolicy = TrackerProgressPolicy.RecordedParts,
                    weekProgress = ::weekProgress,
                    asksWhileFreeOn = ::asks,
                    workoutReminderOn = ::planned,
                    label = activity.label,
                    content = MoContentKey(MoViewKind.Activity, activity.mo.motif),
                ))
            }
        }
    }
}
