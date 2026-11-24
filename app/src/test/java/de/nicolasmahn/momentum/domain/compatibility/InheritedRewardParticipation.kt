package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.activityRules
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.include
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.mo.MoRecordIndex
import de.nicolasmahn.momentum.domain.reward.Earn
import de.nicolasmahn.momentum.domain.reward.RewardRules
import java.time.LocalDate

/**
 * The reward rules of the views that have no stored configuration yet, as contract revision 1 writes
 * them. Supplements and Care read theirs from their configurations, falling back to these; Weight's,
 * Eating's, Reading's and Activity's are their configurations' alone.
 */
internal val BuiltInRewardRules: Map<MoNeed, RewardRules> = mapOf(
    MoNeed.Care to RewardRules(maxPasses = 1, earn = Earn.EveryKeptDays(14)),
    MoNeed.Supplements to RewardRules(maxPasses = 3, earn = Earn.EveryKeptDays(7)),
)

/**
 * The away days nothing is asked of Weight on, by the rule each day had: excused while its
 * configuration says so, an ordinary day from the day it says still due.
 */
internal fun AppState.weightExcusedOn(holidays: HolidaySchedule): (LocalDate) -> Boolean = weightWhenAway.excusedOn(holidays)

/**
 * The away days nothing is asked of Eating on, by the rule each due day had: an ordinary day while
 * its configuration says still due, as it always was, and excused from the day it says so. The day
 * read is the one the reflection is due on, not the day before, which it is about.
 */
internal fun AppState.eatingExcusedOn(holidays: HolidaySchedule): (LocalDate) -> Boolean = eatingWhenAway.excusedOn(holidays)

/** The away days this rule excuses, each by the rule it had on that day. */
private fun Dated<WhenAway>.excusedOn(holidays: HolidaySchedule): (LocalDate) -> Boolean =
    { day -> holidays.isHoliday(day) && on(day) == WhenAway.Excused }

/**
 * The periods the bank of [need] takes part in, from its configuration: null for a whole history,
 * as every task of Mo's had before it could leave, and as a state without the configuration is
 * judged.
 */
internal fun MoRecordIndex.periodsOf(need: MoNeed): List<RewardPeriod>? = state.periodsOf(need)

/** The periods the bank of [need] takes part in, as [MoRecordIndex.periodsOf] reads them from the configuration. */
internal fun AppState.periodsOf(need: MoNeed): List<RewardPeriod>? = when (need) {
    MoNeed.Weight -> weightRewards.periods
    MoNeed.Eating -> eatingRewards.periods
    MoNeed.Reading -> readingRules.rewards?.periods
    MoNeed.Activity -> activityRules.rewards.periods
    MoNeed.Supplements -> supplementsChecklist?.rewards?.periods
    MoNeed.Care -> careChecklist?.rewards?.periods
}

/** Whether the bank of [need] takes part on [day]: in one of its periods, or always while it has none. */
internal fun MoRecordIndex.takesPart(need: MoNeed, day: LocalDate): Boolean = state.takesPart(need, day)

internal fun AppState.takesPart(need: MoNeed, day: LocalDate): Boolean = periodsOf(need).include(day)
