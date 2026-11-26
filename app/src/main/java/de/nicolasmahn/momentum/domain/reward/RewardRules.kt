package de.nicolasmahn.momentum.domain.reward

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule

/**
 * How one view takes part in rewards, in the shape of contract revision 1: one bank of passes and
 * how the bank fills. The bank is the only limit on how many gaps its passes cover.
 */
data class RewardRules(
    val maxPasses: Int,
    val earn: Earn,
) {
    init {
        require(maxPasses >= 1) { "A bank holds at least one pass." }
    }
}

/** How a bank fills. Extra ticks earn nothing; only kept days or recorded amounts do. */
sealed interface Earn {
    /** One pass for every [days] kept days, counted across weeks. */
    data class EveryKeptDays(val days: Int) : Earn {
        init {
            require(days >= 1)
        }
    }

    /**
     * A Monday-to-Sunday week's kept days earn the passes of the highest of its [steps] they
     * reach, and nothing below the first, judged once when the week closes. Each step asks more
     * days than the one before and has more passes.
     */
    data class KeptDaysInWeek(val steps: List<KeptDaysStep>) : Earn {
        /** One pass for a week with at least [days] kept days, which is what every bank earning by kept days started with. */
        constructor(days: Int) : this(listOf(KeptDaysStep(days, 1)))

        init {
            require(steps.isNotEmpty() && steps.first().passes >= 1)
            require(steps.all { it.days in 1..7 })
            require(steps.zipWithNext().all { (lower, higher) -> lower.days < higher.days && lower.passes < higher.passes })
        }

        /** What [kept] days in a week earn: the passes of the highest step they reach. */
        fun passesFor(kept: Int): Int = steps.lastOrNull { kept >= it.days }?.passes ?: 0

        /** The days the next pass takes once the week holds [kept]: the next step's, and the last step's own once it is reached. */
        fun nextDaysAfter(kept: Int): Int = steps.firstOrNull { it.days > kept }?.days ?: steps.last().days
    }

    /** One step of a week's kept days: a week that keeps [days] has earned [passes] in all. */
    data class KeptDaysStep(val days: Int, val passes: Int)

    /**
     * A week's recorded amount earns the passes of the highest of its [steps] it reaches, and
     * nothing below the first, judged once when the week closes. Each step has more passes than
     * the one before and asks at least as much; two may ask the same where a small goal rounds
     * their shares to the same minute.
     */
    data class AmountStepsInWeek(val steps: List<AmountStep>) : Earn {
        init {
            require(steps.isNotEmpty() && steps.first().amount >= 1 && steps.first().passes >= 1)
            require(steps.zipWithNext().all { (lower, higher) -> lower.amount <= higher.amount && lower.passes < higher.passes })
        }

        /** What [amount] in a week earns: the passes of the highest step it reaches. */
        fun passesFor(amount: Int): Int = steps.lastOrNull { amount >= it.amount }?.passes ?: 0

        /** The amount the next pass takes once the week holds [amount]: the next step's, and the last step's own once it is reached. */
        fun nextAmountAfter(amount: Int): Int = steps.firstOrNull { it.amount > amount }?.amount ?: steps.last().amount
    }

    /** One step of a weekly amount: a week that reaches [amount] has earned [passes] in all. */
    data class AmountStep(val amount: Int, val passes: Int)
}

/**
 * What a view requires, as the reward walk judges it. Each shape reads only the facts it needs, so
 * the walk never sees a record.
 */
sealed interface RewardTarget {
    /**
     * Due every day. [openOn] counts the checkpoints with something missing, which is the number of
     * gaps; zero keeps the day. On a day [excusedOn], the view is not due at all.
     */
    class Daily(
        val openOn: (LocalDate) -> Int,
        val excusedOn: (LocalDate) -> Boolean = { false },
    ) : RewardTarget

    /**
     * Due some days of each Monday-to-Sunday week, on any days: the week's free days are dealt to
     * its empty days in order, and every empty day after them is one gap. [freeDaysOn] is how many
     * the week of a day has by the rule of that day, and [spanStartOn] the day they are counted
     * from: its Monday, or a later day on which the target changed and started a new span, whose
     * free days are whole. Both are the view's to say, day by day, so a rule that changed on a day
     * judges no earlier day.
     */
    class WeekTimes(
        val keptOn: (LocalDate) -> Boolean,
        val freeDaysOn: (LocalDate) -> Int,
        val spanStartOn: (LocalDate) -> LocalDate = { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) },
    ) : RewardTarget

    /**
     * An amount per week, judged on its Sunday. [requiredIn] is what the week starting on a Monday
     * asks, by the goal it has and less for the days that ask nothing of the view, and every
     * started [gapSizeIn] of it still missing is one gap. Both are the view's to say, week by
     * week, so a goal that changed on a day judges no earlier week.
     *
     * What a week's amount earns is decided apart from its judgment: it earns on a Sunday the
     * view is not due on too, in every week the view says can earn ([earnsIn]). Every week can,
     * unless the view says otherwise.
     */
    class WeekAmount(
        val requiredIn: (monday: LocalDate) -> Int,
        val gapSizeIn: (monday: LocalDate) -> Int,
        val amountIn: (monday: LocalDate) -> Int,
        val earnsIn: (monday: LocalDate) -> Boolean = { true },
    ) : RewardTarget
}

/**
 * One view in the walk. [key] names it in the result; [began] is the first day it was due, and on
 * a day it is not shown ([visibility]) it is not due, like a sick day, although its bank is kept.
 */
class RewardParticipant<K>(
    val key: K,
    /** Today's rules, which the next pass and the bank's size are shown by. */
    val rules: RewardRules,
    val target: RewardTarget,
    val began: LocalDate?,
    /** Whether the view is shown, day by day: true while shown. A view hidden from the beginning was never shown. */
    val visibility: Dated<Boolean>,
    /** Whether it takes part on a day: a checklist that joined or left rewards does only in its periods. */
    val takesPartOn: (LocalDate) -> Boolean = { true },
    /**
     * The rules of each day, for a view whose rules changed on a dated day: its numbers, and for a
     * bank that earned by the week before, the day it began to earn by kept days across weeks.
     */
    val rulesOn: (LocalDate) -> RewardRules = { rules },
) {
    fun visibleOn(day: LocalDate): Boolean = visibility.on(day)

    /** Whether it was ever shown; one hidden from the beginning and never shown stays out of the walk's start. */
    val everShown: Boolean get() = visibility.ever { shown -> shown }

    /**
     * Whether [day] is hidden by a dated change, a revision with a start, which pauses a bank that
     * grants weekly for the whole week: a day hidden from the beginning does not, so a view never
     * shown earns as it always has, and a dated hide stops no grant made before it.
     */
    fun hiddenByAChangeOn(day: LocalDate): Boolean = visibility.revisionOn(day).let { !it.value && it.from != null }

    /**
     * Whether the walk judges it on [day]: shown, begun, taking part, and the day not sick. The
     * walk's own rule, also read by what is offered ahead of a close, so neither can drift.
     */
    fun isDueOn(day: LocalDate, daysOff: HolidaySchedule): Boolean =
        visibleOn(day) && began != null && began <= day && takesPartOn(day) && !daysOff.isSick(day)
}
