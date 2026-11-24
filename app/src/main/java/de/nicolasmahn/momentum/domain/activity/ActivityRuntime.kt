package de.nicolasmahn.momentum.domain.activity

import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.ActivityTrackerRef
import de.nicolasmahn.momentum.domain.TrackerEvaluationContext
import de.nicolasmahn.momentum.domain.TrackerRuntimeProvider
import de.nicolasmahn.momentum.domain.checklist.include
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import de.nicolasmahn.momentum.domain.mo.MoWeekProgress
import de.nicolasmahn.momentum.domain.mo.TrackerProgressPolicy
import de.nicolasmahn.momentum.domain.mo.ObservationTarget
import de.nicolasmahn.momentum.domain.mo.TrackerObservation
import de.nicolasmahn.momentum.domain.mo.datedActions
import de.nicolasmahn.momentum.domain.mo.monday
import de.nicolasmahn.momentum.domain.mo.rulesOn
import de.nicolasmahn.momentum.domain.reward.RewardTarget
import de.nicolasmahn.momentum.domain.reward.TrackerReward
import de.nicolasmahn.momentum.domain.reward.weeklyTrackerReward
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
            began, visibility.visibilityOf(de.nicolasmahn.momentum.domain.settings.DashboardFeature.Activity),
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
