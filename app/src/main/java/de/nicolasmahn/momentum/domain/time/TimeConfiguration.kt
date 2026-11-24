package de.nicolasmahn.momentum.domain.time


import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.include
import de.nicolasmahn.momentum.domain.checklist.isTheDayCheckpoint
import de.nicolasmahn.momentum.domain.checklist.requireId
import de.nicolasmahn.momentum.domain.checklist.requireInOrder
import de.nicolasmahn.momentum.domain.checklist.requireLabel
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * A tracker that totals recorded minutes against a weekly goal. Goal and reward revisions judge
 * their own weeks; a timer's name, identity and motif are ordinary configuration.
 */
data class TimeConfiguration(
    override val id: String,
    override val label: String,
    override val icon: ViewIcon,
    override val hue: ViewHue,
    /**
     * What a week asks, in whole minutes ([WeeklyGoalMinutes]). A week is judged by the goal its
     * Sunday has ([goalOfWeek]), and reaching it is the first of the steps the week earns by.
     */
    val goal: Dated<Int>,
    /** Reward rules and dated participation periods; null while no protection is earned. */
    val rewards: TimeRewards?,
    /**
     * What an away day means, still due or not due. Not due, the day is none of its week's days:
     * the week asks a seventh less for it, as for a sick day ([weekAsks]), and a week whose Sunday
     * is such a day is not judged. A time view has no count a week to lower.
     */
    override val whenAway: Dated<WhenAway>,
    /** The one reminder of the view, as the checkpoint [DayCheckpointId]: whether it reminds, and when. */
    override val checkpoint: Checkpoint,
    val mo: TrackerMoPolicy = TrackerMoPolicy(),
) : LoggedView {
    val motif: MoMotif? get() = mo.motif

    init {
        requireId(id)
        requireLabel(label)
        require(goal.revisions.all { it.value in WeeklyGoalMinutes }) { "Time view $id asks ${WeeklyGoalMinutes.first} to ${WeeklyGoalMinutes.last} minutes a week." }
        require(whenAway.revisions.none { it.value == WhenAway.LowerTarget }) { "Time view $id is due or not due on an away day; not due is what lowers its week." }
        require(motif == null || motif in TimerMotifs) { "Mo has nothing for a timer in the flavor ${motif?.storageId}." }
        require(checkpoint.isTheDayCheckpoint) { "Time view $id has the one checkpoint $DayCheckpointId, with no name and no dates." }
    }

    override val kind: LoggedViewKind get() = LoggedViewKind.Time

    /** A time view's due day asks about itself: its reminder is about the week that day is in, and opens on it. */
    override fun recordDayFor(dueDay: LocalDate): LocalDate = dueDay

    /** The goal that judges the week starting on [monday]: the one its Sunday has. */
    fun goalOfWeek(monday: LocalDate): Int {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        return goal.on(monday.plusDays(DaysToSunday))
    }

    /** Whether [day] is an away day this view is not due on, by the rule of that day. */
    fun isNotDueAwayOn(day: LocalDate, daysOff: HolidaySchedule): Boolean = daysOff.isHoliday(day) && whenAway.on(day) == WhenAway.Excused

    /**
     * What the week starting on [monday] asks: its Sunday's goal, less a seventh for each of its
     * days that is sick or an away day the view is not due on, each by that day's own rule. A day
     * that is both counts once, and the amount is rounded up once at the end. Minutes recorded on
     * such a day still count toward it.
     *
     * The one answer to what a week asks: the reward walk, Mo's ledger and mood, the reminder and
     * the screen's bar all read it here, so none of them can ask for another amount.
     */
    fun weekAsks(monday: LocalDate, daysOff: HolidaySchedule): Int =
        daysOff.weekAmountWithoutDaysOff(goalOfWeek(monday), monday) { day -> isNotDueAwayOn(day, daysOff) }

    /** Whether the week ending on [sunday] is judged at all: not on a sick Sunday, nor on an away one the view is not due on. */
    fun judgesWeekOn(sunday: LocalDate, daysOff: HolidaySchedule): Boolean = !daysOff.isSick(sunday) && !isNotDueAwayOn(sunday, daysOff)
}

/**
 * One bank of passes a time view fills by its goal: a Monday-to-Sunday week earns the passes of
 * every one of its [tokenSteps] it reaches, each pass covers [tokenCover] of a week that falls
 * short, as many as the bank holds. The bank's size, the steps and what a token covers are
 * settings.
 *
 * The size is dated: passes earned under an earlier size stay earned, and a smaller bank holds no
 * more than it allows from its first day. [periods] record dated participation in the streak:
 * joining starts on the saving habit day and leaving ends after yesterday.
 * Null means the entire recorded history. An empty list retains bank settings without taking
 * part on any day.
 */
data class TimeRewards(
    val maxPasses: Dated<Int>,
    val periods: List<RewardPeriod>? = null,
    /**
     * The steps a week earns by ([TokenSteps]), dated as the goal is: a week earns by the steps
     * its Sunday has ([stepsOfWeek]), so a change never credits an earlier week differently.
     */
    val tokenSteps: Dated<TokenSteps> = Dated.always(DefaultTokenSteps),
    /**
     * How much of a week that falls short one token covers ([TokenCover]), dated as the steps are:
     * a week is covered by what its Sunday has ([coverOfWeek]), so a change never re-judges an
     * earlier week.
     */
    val tokenCover: Dated<TokenCover> = Dated.always(TokenCover.QuarterOfGoal),
) {
    constructor(maxPasses: Int, periods: List<RewardPeriod>? = null, tokenSteps: TokenSteps = DefaultTokenSteps, tokenCover: TokenCover = TokenCover.QuarterOfGoal) :
        this(Dated.always(maxPasses), periods, Dated.always(tokenSteps), Dated.always(tokenCover))

    init {
        require(maxPasses.revisions.all { it.value >= 1 }) { "A bank holds at least one pass." }
        periods?.requireInOrder()
    }

    fun takesPartOn(date: LocalDate): Boolean = periods.include(date)

    /** What one token covers in the week starting on [monday]: what its Sunday has. */
    fun coverOfWeek(monday: LocalDate): TokenCover {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        return tokenCover.on(monday.plusDays(DaysToSunday))
    }

    /** The steps the week starting on [monday] earns by: the ones its Sunday has. */
    fun stepsOfWeek(monday: LocalDate): TokenSteps {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        return tokenSteps.on(monday.plusDays(DaysToSunday))
    }
}

/**
 * One step of what a week earns: once the week's minutes reach [percent] of its goal, reaching
 * it adds [tokens] to what the week earns. A step is a share of the goal, not minutes, so a
 * changed goal moves it along ([stepMinutes]).
 */
data class TokenStep(val percent: Int, val tokens: Int)

/**
 * The steps a week earns tokens by, read from the goal up: the first is the goal itself, at
 * [GoalStepPercent], and each further one a higher share of the goal ([FurtherStepPercent]), each
 * with tokens of its own ([StepTokens]), at most [TokenStepCount] in all. A week earns the tokens
 * of every step it reaches, added up, and nothing below the first. Anything else is refused here,
 * so a list that could not be stored or shown is never built.
 */
data class TokenSteps(val steps: List<TokenStep>) {
    init {
        require(steps.size in TokenStepCount) { "A week earns by ${TokenStepCount.first} to ${TokenStepCount.last} steps." }
        require(steps.first().percent == GoalStepPercent) { "The first step is the goal itself, at $GoalStepPercent %." }
        require(steps.drop(1).all { it.percent in FurtherStepPercent }) { "A further step is at ${FurtherStepPercent.first} to ${FurtherStepPercent.last} % of the goal." }
        require(steps.all { it.tokens in StepTokens }) { "A step adds ${StepTokens.first} to ${StepTokens.last} tokens." }
        require(steps.zipWithNext().all { (lower, higher) -> lower.percent < higher.percent }) { "Each step is a higher share of the goal than the one before it." }
    }

    val last: TokenStep get() = steps.last()

    /** The most a week can earn: what every step adds, together. */
    val total: Int get() = steps.sumOf { it.tokens }

    /**
     * What a week has earned once it reaches each step, in the steps' order: the tokens of that
     * step and of every one below it, which is what the walk is handed as a step's passes.
     */
    val runningTotals: List<Int> get() = steps.runningFold(0) { earned, step -> earned + step.tokens }.drop(1)

    /**
     * The percents the step at [index] may have, beside its neighbors: above the one before it
     * and below the one after it, within what a further step may be. The first step has the
     * goal's alone.
     */
    fun percentsAllowedAt(index: Int): IntRange {
        if (index == 0) return GoalStepPercent..GoalStepPercent
        val lowest = maxOf(FurtherStepPercent.first, steps[index - 1].percent + 1)
        val highest = steps.getOrNull(index + 1)?.let { it.percent - 1 } ?: FurtherStepPercent.last
        return lowest..highest
    }

    /** These steps with the one at [index] as [step]; refused where the list could not hold it there. */
    fun withStep(index: Int, step: TokenStep): TokenSteps = TokenSteps(steps.mapIndexed { at, each -> if (at == index) step else each })

    /** These steps without the one at [index], which is never the first. */
    fun withoutStep(index: Int): TokenSteps {
        require(index > 0) { "The first step is the goal itself and stays." }
        return TokenSteps(steps.filterIndexed { at, _ -> at != index })
    }

    /** Whether one more step can be added after the last: there is room in the count and above its percent. */
    val canGrow: Boolean get() = steps.size < TokenStepCount.last && last.percent < FurtherStepPercent.last

    /** These steps with one more after the last, a hundred points above it, kept within reach, adding one token. */
    fun grown(): TokenSteps {
        require(canGrow) { "No step can be added after ${last.percent} % with ${steps.size} steps." }
        return TokenSteps(steps + TokenStep(minOf(last.percent + GoalStepPercent, FurtherStepPercent.last), StepTokens.first))
    }
}

/** How many steps a week can earn by. */
val TokenStepCount = 1..5

/** The first step's share of the goal: the goal itself. */
const val GoalStepPercent = 100

/** What a further step's share of the goal can be. */
val FurtherStepPercent = 101..1000

/** What reaching one step can add. */
val StepTokens = 1..5

/** The steps every bank starts with, which is what every week earned before they could be listed: a token at the goal, one more at twice it. */
val DefaultTokenSteps = TokenSteps(listOf(TokenStep(GoalStepPercent, 1), TokenStep(2 * GoalStepPercent, 1)))

/**
 * The steps that earn exactly what [tokensPerWeek] a week earned while that was the bank's
 * number, a token for each full goal up to it: a step of one token at 100 %, at 200 %, up to
 * N × 100 % for N. The number was 1 to 5, which is asked before any step is built, so a stored
 * number that is no such thing is refused without a list the size of it.
 */
fun tokenStepsOfTokensPerWeek(tokensPerWeek: Int): TokenSteps {
    require(tokensPerWeek in StepTokens) { "A week earned ${StepTokens.first} to ${StepTokens.last} tokens, not $tokensPerWeek." }
    return TokenSteps((1..tokensPerWeek).map { TokenStep(it * GoalStepPercent, StepTokens.first) })
}

/** The minutes a step at [percent] of a week whose goal is [goal] asks for: rounded up to a whole minute, so 150 % of 45 is 68. */
fun stepMinutes(percent: Int, goal: Int): Int = (percent * goal + GoalStepPercent - 1) / GoalStepPercent

/**
 * The visual motifs supported while a timer runs. A book is a presentation choice for any timer.
 */
val TimerMotifs: List<MoMotif> = listOf(MoMotif.Book)

/** What a week can ask of a time view: one minute to every minute a week has. */
val WeeklyGoalMinutes = 1..10080

/** Initial weekly goal for a timer. */
const val DefaultWeeklyGoalMinutes = 60

/** Initial timer bank capacity. */
const val DefaultTimeBankSize = 3

/**
 * How much of a week that falls short one token covers. Every started amount still missing on
 * Sunday takes one token, as many as the bank holds.
 */
sealed interface TokenCover {
    /** The minutes one token covers in a week whose goal is [goal]. */
    fun minutesOf(goal: Int): Int

    /**
     * A quarter of the week's goal, rounded up to a whole minute: what a token covered before it
     * could be set, so every time view stored before then keeps it. A changed goal moves it along.
     */
    data object QuarterOfGoal : TokenCover {
        override fun minutesOf(goal: Int): Int = (goal + GapsPerGoal - 1) / GapsPerGoal
    }

    /** A fixed number of [minutes], whatever the goal. */
    data class Minutes(val minutes: Int) : TokenCover {
        init {
            require(minutes in TokenCoverMinutes) { "A token covers ${TokenCoverMinutes.first} to ${TokenCoverMinutes.last} minutes." }
        }

        override fun minutesOf(goal: Int): Int = minutes
    }
}

/** What a token can cover: one minute to every minute a week has. */
val TokenCoverMinutes = WeeklyGoalMinutes

/** How far one press of − or + moves what a token covers. */
const val TokenCoverStep = 5

private const val GapsPerGoal = 4
private const val DaysToSunday = 6L

/**
 * Configured timers have distinct stable IDs and logs following [requireStorableTimeLogs].
 * The decoder and the store's write guard both ask this, so a state that could not be read back
 * is never written. Everything one configuration must be, it refuses itself.
 */
fun requireStorableTime(time: List<TimeConfiguration>, logs: List<TimeLog>) {
    require(time.distinctBy { it.id }.size == time.size) { "Two time views share an ID." }
    requireStorableTimeLogs(logs)
}

/** Prefix of a generated timer ID, such as `tim_k2p9`. */
const val MadeTimeIdPrefix = "tim"

internal val MadeTimeId = Regex("${MadeTimeIdPrefix}_[a-z0-9]{4}")

/** New timers start due on away days. */
val MadeTimeWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Due)
