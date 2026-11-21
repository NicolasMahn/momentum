package com.example.workouttracker.domain.measurement


import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DailyView
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.include
import com.example.workouttracker.domain.checklist.requireInOrder
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.DayCheckpointId
import com.example.workouttracker.domain.checklist.DaysPerWeek
import com.example.workouttracker.domain.checklist.StarterDayCheckpoint
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.isTheDayCheckpoint
import com.example.workouttracker.domain.checklist.isTypedLine
import com.example.workouttracker.domain.checklist.requireId
import com.example.workouttracker.domain.checklist.requireLabel
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One configured numerical tracker. Its unit and step affect presentation; its bank and away
 * rules judge dated completion without judging the recorded values.
 */
data class MeasurementConfiguration(
    override val id: String,
    override val label: String,
    override val icon: ViewIcon,
    override val hue: ViewHue,
    /**
     * What is shown after the number, such as `cm`, `%` or `bpm`, or nothing, as for a mood from 1
     * to 10 ([isValidMeasurementUnit]). It is a label: changing it converts no record.
     */
    val unit: String,
    /** What the − and + buttons add and take. Like the unit it is the screen's alone: changing it changes no record. */
    val step: MeasurementStep,
    /** The bank and dated participation periods; null while completion earns no protection. */
    val rewards: MeasurementBank?,
    /** The dated policy for whether this measurement is due on away days. */
    override val whenAway: Dated<WhenAway>,
    /**
     * The one reminder of the measurement, as the checkpoint [MeasurementCheckpointId]: whether it
     * reminds, and when. It has no name and no dates of its own.
     */
    override val checkpoint: Checkpoint,
    /** Which finite numbers this tracker accepts for entry and estimates. */
    val valuePolicy: MeasurementValuePolicy = MeasurementValuePolicy.Finite,
    /**
     * How many days the chart's trend line follows ([measurementTrendSmoothing]). Like the step it
     * is presentation: it changes no record and judges nothing.
     */
    val trendDays: Int = DefaultMeasurementTrendDays,
    val mo: TrackerMoPolicy = TrackerMoPolicy(),
) : DailyView {
    init {
        requireId(id)
        requireLabel(label)
        require(isValidMeasurementUnit(unit)) { "Measurement $id has a unit that cannot be stored." }
        require(trendDays in MeasurementTrendDayRange) { "Measurement $id has a trend of $trendDays days." }
        require(whenAway.revisions.none { it.value == WhenAway.LowerTarget }) { "Measurement $id has no count a week to lower." }
        require(checkpoint.isTheDayCheckpoint) { "Measurement $id has the one checkpoint $MeasurementCheckpointId, with no name and no dates." }
    }

    fun acceptsValue(value: Double): Boolean = valuePolicy.accepts(value)

    /** The bank still earning by the week, as only one stored that way does, or null for every other. */
    val weekRewards: MeasurementRewards? get() = (rewards as? MeasurementBank.Week)?.rewards

    /** The bank earning by kept days, or null while this tracker has none or still earns by the week. */
    override val standardRewards: ChecklistRewards? get() = (rewards as? MeasurementBank.Standard)?.rewards

    override val kind: LoggedViewKind get() = LoggedViewKind.Measurement

    /** A measurement's due day asks about itself: the number is logged on the day it is of. */
    override fun recordDayFor(dueDay: LocalDate): LocalDate = dueDay

    override fun recordJudgedOn(dueDay: LocalDate): LocalDate = dueDay
}

/**
 * A measurement's bank. Every bank a measurement gets earns a pass for every so many kept days,
 * as a checklist's does ([Standard]). [Week] is the older shape, which only a bank stored with it
 * still has; changing its numbers makes it a [Standard] one from that day on ([earningByKeptDays]).
 */
sealed interface MeasurementBank {
    /** The days it takes part in the streak: null for its whole recorded history. */
    val periods: List<RewardPeriod>?

    fun takesPartOn(date: LocalDate): Boolean = periods.include(date)

    data class Week(val rewards: MeasurementRewards) : MeasurementBank {
        override val periods: List<RewardPeriod>? get() = rewards.periods
    }

    /**
     * [earlierWeeks] is the week rule this bank earned by before, where it had one. The days it
     * reaches keep the judgment that rule gave them, and [rewards] decide every day after.
     */
    data class Standard(val rewards: ChecklistRewards, val earlierWeeks: EarlierWeeks? = null) : MeasurementBank {
        override val periods: List<RewardPeriod>? get() = rewards.periods
    }
}

/**
 * The week rule a bank earned by until it was changed to earn by kept days: a pass for a
 * Monday-to-Sunday week with at least [keptDaysInWeek] logged days, on every day up to [until],
 * the day before the change was saved. It is kept so those days are never judged again; the
 * bank's size over the same days is the bank's own dated number.
 */
data class EarlierWeeks(val keptDaysInWeek: Dated<Int>, val until: LocalDate) {
    init {
        require(keptDaysInWeek.revisions.all { it.value in 1..DaysPerWeek }) { "A week has one to seven days to log." }
    }
}

/**
 * What a measurement's − and + buttons add and take, such as 0.5, 0.05 or 2.5: a decimal greater
 * than 0 and at most [MaximumMeasurementStep], with at most [MeasurementStepDecimals] decimal
 * places. It decides the chart's spacing too and nothing else: not what can be typed, not how a
 * number is shown.
 *
 * The rule is asked of the decimal as it is written ([parse]), before it becomes [value], the
 * binary number the app calculates with, in which most decimals have no exact form. One step is
 * one value however it was written: `0.50` is `0.5`.
 */
@JvmInline
value class MeasurementStep private constructor(private val decimal: BigDecimal) {
    /** The step [written] says, as [parse] reads it. Throws where it says none. */
    constructor(written: String) : this(requireNotNull(decimalOf(written)) { "A step is above 0 and at most $MaximumMeasurementStep, with at most $MeasurementStepDecimals decimal places." })

    /** The step as a number to add, to take and to space a chart by. */
    val value: Double get() = decimal.toDouble()

    /**
     * The one way a step is written where it is stored: plain digits with a point and no zero
     * that says nothing, such as `0.5`, `2.5` or `1`.
     */
    val written: String get() = decimal.toPlainString()

    override fun toString(): String = written

    companion object {
        /**
         * The step [written] says, or null where it is none a measurement can have. It is read as
         * plain digits with a point for the decimal separator; a sign, an exponent or anything
         * else beside them makes it no step.
         */
        fun parse(written: String): MeasurementStep? = decimalOf(written)?.let(::MeasurementStep)

        private val PlainDecimal = Regex("""\d+\.?\d*|\.\d+""")

        private fun decimalOf(written: String): BigDecimal? {
            if (!PlainDecimal.matches(written)) return null
            val decimal = BigDecimal(written)
            val allowed = decimal.signum() > 0 && decimal <= BigDecimal(MaximumMeasurementStep) && decimal.scale() <= MeasurementStepDecimals
            return decimal.stripTrailingZeros().takeIf { allowed }
        }
    }
}

/** The largest step a measurement's buttons can take. */
const val MaximumMeasurementStep = 1000

/** The most decimal places a step may be written with. */
const val MeasurementStepDecimals = 2

/** The initial step for a numerical tracker. */
val DefaultMeasurementStep = MeasurementStep("0.1")

/** Entry and estimate rules do not erase historical records, whatever their values. */
enum class MeasurementValuePolicy {
    Finite,
    Positive,
    ;

    fun accepts(value: Double): Boolean = isValidMeasurementValue(value) && (this == Finite || value > 0.0)
}

/** The generic numerical contract; a configuration may constrain it further. */
fun isValidMeasurementValue(value: Double): Boolean = value.isFinite()

/** The most Unicode code points a unit may have. */
const val MaximumMeasurementUnitLength = 12

/**
 * Whether [unit] can be a measurement's unit: nothing at all, or one line as typed
 * ([isTypedLine]) of at most [MaximumMeasurementUnitLength] code points.
 *
 * The one rule for a unit, asked by a draft before it can be saved, by the configuration and so by
 * the decoder.
 */
fun isValidMeasurementUnit(unit: String): Boolean = isTypedLine(unit, 0..MaximumMeasurementUnitLength)

/**
 * Configured measurements each have a stable ID of their own. The decoder and the store's
 * write guard both ask this, so a state
 * that could not be read back is never written. Everything one configuration must be, it refuses
 * itself.
 */
fun requireStorableMeasurements(measurements: List<MeasurementConfiguration>) {
    require(measurements.distinctBy { it.id }.size == measurements.size) { "Two measurements share an ID." }
}

/**
 * One bank of passes, filled a week at a time, as Weight's was before every measurement earned by
 * kept days: a pass for a Monday-to-Sunday week with at least
 * [keptDaysInWeek] logged days, the contract's second earn shape, where a checklist's bank has a
 * pass for every so many kept days. A measurement stays asked for every day; the number only
 * decides what a week earns. [periods] are the days it takes part in the streak, as a checklist's
 * are: null for its whole recorded history, or explicit dated participation periods.
 *
 * The two numbers are dated: a week earns by the number on its Sunday, passes earned under an
 * earlier rule stay earned, and a smaller bank holds no more than it allows from its first day.
 */
data class MeasurementRewards(
    val maxPasses: Dated<Int>,
    val keptDaysInWeek: Dated<Int>,
    val periods: List<RewardPeriod>? = null,
) {
    constructor(maxPasses: Int, keptDaysInWeek: Int, periods: List<RewardPeriod>? = null) :
        this(Dated.always(maxPasses), Dated.always(keptDaysInWeek), periods)

    init {
        require(maxPasses.revisions.all { it.value >= 1 }) { "A bank holds at least one pass." }
        require(keptDaysInWeek.revisions.all { it.value in 1..DaysPerWeek }) { "A week has one to seven days to log." }
        periods?.requireInOrder()
    }

    fun takesPartOn(date: LocalDate): Boolean = periods.include(date)

    /**
     * These numbers as a bank earning by kept days has them, a pass for every [keptDaysInWeek]
     * kept days. A page shows them in the two steppers every daily bank has; the bank earns that
     * way only once a change is saved ([earningByKeptDays]).
     */
    fun acrossWeeks(): ChecklistRewards = ChecklistRewards(maxPasses, keptDaysInWeek, periods)

    /**
     * This bank earning a pass for every [everyKeptDays] kept days from [today] on, wherever in a
     * week they fall. Every day before [today] keeps the week rule and the bank size it was judged
     * by, so no mark, no pass earned or spent and no streak changes; the walk takes the kept days
     * of the week in progress along toward the first pass.
     */
    fun earningByKeptDays(today: LocalDate, maxPasses: Int, everyKeptDays: Int): MeasurementBank.Standard {
        val until = today.minusDays(1)
        return MeasurementBank.Standard(
            rewards = ChecklistRewards(this.maxPasses.changedFrom(today, maxPasses), Dated.always(everyKeptDays), periods),
            // A week number saved on the day of the change judged no day, so it is not kept.
            earlierWeeks = EarlierWeeks(Dated(keptDaysInWeek.revisions.filter { it.from == null || it.from <= until }), until),
        )
    }
}

/** New numerical trackers start due on away days. */
val MadeMeasurementWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Due)

/** The ID of the one checkpoint every measurement has. */
const val MeasurementCheckpointId = DayCheckpointId

/**
 * The initial numerical-tracker reminder, also used to recover configurations stored before
 * reminders existed: off, at 09:00.
 */
val DefaultMeasurementCheckpoint = StarterDayCheckpoint
