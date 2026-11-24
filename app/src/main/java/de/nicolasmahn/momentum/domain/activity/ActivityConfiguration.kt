package de.nicolasmahn.momentum.domain.activity

import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.ViewLook
import de.nicolasmahn.momentum.domain.activity.routine.GymLabels
import de.nicolasmahn.momentum.domain.activity.routine.Routine
import de.nicolasmahn.momentum.domain.activity.routine.RoutineDay
import de.nicolasmahn.momentum.domain.activity.routine.RoutineExercise
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import de.nicolasmahn.momentum.domain.activity.routine.defaultGymRoutine
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.include
import de.nicolasmahn.momentum.domain.checklist.requireId
import de.nicolasmahn.momentum.domain.checklist.requireInOrder
import de.nicolasmahn.momentum.domain.checklist.requireLabel
import de.nicolasmahn.momentum.domain.checklist.timesAsked
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Activity's configuration, the one view of its kind: what a person did today, of which an
 * activity with a routine, the gym at first, is one kind. It holds what the view is called and
 * how it looks, the week it asks for, its chosen days each with the activity planned for it, and
 * what an away day means, its bank with the steps a week earns by, and the activities a person
 * lists, each with its routine if it has one; [applyEdit] is how it changes. There is exactly
 * one, under [ActivityViewId], seeded once in the app language of that moment and stored from
 * then on.
 *
 * The look, the activities, their routines and whether a duration is asked for ([logsDuration])
 * judge no day, so they are not dated. The week, the
 * holiday rule with its count, the bank's size and its steps judge weeks, so each is dated: a
 * change counts from the day it is saved and no earlier day is judged again. The number of the
 * week's days is its target, and all the judging reads of it ([daysAWeekOn]); which days are
 * chosen and what is planned on them decide the reminder and nothing a day is judged by. Two
 * rules tell how a week takes a change, as a checklist's week item takes them: the number of
 * days and the fewer-days count start a new span of free days on the day they change
 * ([spanStartOn]), while the holiday answer, which days are chosen and what is planned keep the
 * span, so the free days already used stay used ([freeDaysOn]). A change of the bank or its
 * steps resets nothing, and Monday starts afresh.
 */
data class ActivityConfiguration(
    override val label: String,
    override val icon: ViewIcon,
    override val hue: ViewHue,
    /** The week's chosen days with what is planned on each; their number is the days a week asks for ([DaysAWeek]), the rest free, and a week earns by the number its Sunday has ([daysAWeekOf]). */
    val week: Dated<WeekPlan>,
    /** Still due, not due, or fewer days with [awayDays], by the rule of each day. */
    val whenAway: Dated<WhenAway>,
    /**
     * The days a week spent entirely away asks while the week lowers ([AwayDays]). It is kept
     * whichever rule is chosen, so choosing fewer days again finds it; it is read only under
     * [WhenAway.LowerTarget], and is then below the days a week.
     */
    val awayDays: Dated<Int>,
    val rewards: ActivityRewards,
    /** The activities a person lists, in the order they pick the tiles by; a removed one stays, marked, and so does its routine. */
    val activities: List<ListedActivity>,
    val mo: TrackerMoPolicy = TrackerMoPolicy(enabled = true),
    /**
     * Whether logging an activity asks how long it took. It decides what the screen offers and
     * nothing else: a duration an entry or a session already holds stays with it either way, and
     * no day, week or token is judged by one.
     */
    val logsDuration: Boolean = false,
) : ViewLook {
    init {
        requireLabel(label)
        require(awayDays.revisions.all { it.value in AwayDays }) { "A week away asks ${AwayDays.first} to ${AwayDays.last} days." }
        require(changeDays.all { day -> whenAway.at(day) != WhenAway.LowerTarget || awayDays.at(day) < week.at(day).size }) {
            "Fewer days on holiday is for more than one day a week, and asks fewer than the days a week."
        }
        // A planned activity is one the configuration lists, removed ones included: an earlier revision is history.
        require(week.revisions.all { revision -> revision.value.days.all { it.activityId == null || activity(it.activityId) != null } }) { "A planned activity is one Activity lists." }
        require(changeDays.all { day -> rewards.tokenSteps.at(day).steps.size <= ActivityTokenSteps.roomFor(week.at(day).size) }) {
            "A week of N days leaves room for 1 + (7 − N) token steps."
        }
        require(activities.distinctBy { it.id }.size == activities.size) { "Two activities share an ID." }
        require(activities.distinctBy { activityNameKey(it.label) }.size == activities.size) { "Two activities share a name." }
        // A session names its day and a log its exercise by ID alone, so neither may be another activity's.
        val dayIds = activities.flatMap { activity -> activity.routine?.days.orEmpty().map { it.id } }
        require(dayIds.distinct().size == dayIds.size) { "Two routine days share an ID." }
        val exerciseIds = activities.flatMap { activity -> activity.routine?.days.orEmpty().flatMap { day -> day.exercises.map { it.id } } }
        require(exerciseIds.distinct().size == exerciseIds.size) { "Two exercises share an ID." }
    }

    /** The one ID Activity is stored under ([ActivityViewId]); there is no second configuration of its kind. */
    override val id: String get() = ActivityViewId

    /** The activities still offered, in the order a person picks the tiles by. */
    val retainedActivities: List<ListedActivity> get() = activities.filterNot { it.removed }

    fun activity(activityId: String): ListedActivity? = activities.firstOrNull { it.id == activityId }

    /** The activity whose name is [name], removed ones included, by the one rule names are compared by ([activityNameKey]). */
    fun activityNamed(name: String): ListedActivity? = activityNameKey(name).let { key -> activities.firstOrNull { activityNameKey(it.label) == key } }

    /** An activity's name, or the [recordedName] an entry keeps for one the configuration never knew. */
    fun activityLabel(activityId: String, recordedName: String): String = activity(activityId)?.label ?: recordedName

    /** The activity whose routine has the day [dayId], removed ones included, or null for a day no routine knows. */
    fun activityOfDay(dayId: String): ListedActivity? = activities.firstOrNull { it.routine?.day(dayId) != null }

    /** The activity whose routine has the exercise [exerciseId], removed ones included, or null for one no routine knows. */
    fun activityOfExercise(exerciseId: String): ListedActivity? = activities.firstOrNull { it.routine?.exercise(exerciseId) != null }

    /** A routine day by its ID, whichever activity's routine it is in, removed ones included. */
    fun routineDay(dayId: String): RoutineDay? = activityOfDay(dayId)?.routine?.day(dayId)

    /** An exercise by its ID, whichever activity's routine it is in, removed ones included. */
    fun routineExercise(exerciseId: String): RoutineExercise? = activityOfExercise(exerciseId)?.routine?.exercise(exerciseId)

    /** The days a week asked on [day]: the number of the week's chosen days by the revision of that day. */
    fun daysAWeekOn(day: LocalDate): Int = week.on(day).size

    /** The days a week the week starting on [monday] earns by: the number its Sunday has. */
    fun daysAWeekOf(monday: LocalDate): Int {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        return daysAWeekOn(monday.plusDays(DaysToSunday))
    }

    /** Whether [day] is an away day the view is not due on, by the rule of that day. A sick day asks nothing anyway. */
    fun isNotDueAwayOn(day: LocalDate, daysOff: HolidaySchedule): Boolean = daysOff.isHoliday(day) && whenAway.on(day) == WhenAway.Excused

    /**
     * The free days the week of [day] has by the rule of that day: seven less what the rule asks
     * before sick days. Still due and not due ask the days a week; fewer days asks
     * [timesAsked] with the week's holiday days, a holiday day that is also sick among them. The
     * walk and Mo's ledger skip sick days and not-due away days themselves, so the free days are
     * not lowered for them here as well.
     */
    fun freeDaysOn(day: LocalDate, daysOff: HolidaySchedule): Int {
        val days = daysAWeekOn(day)
        val asked = when (whenAway.on(day)) {
            WhenAway.LowerTarget -> timesAsked(days, awayDays.on(day), daysOff.holidayDaysInWeek(mondayOf(day)), DaysInAWeek)
            WhenAway.Due, WhenAway.Excused -> days
        }
        return DaysInAWeek - asked
    }

    /**
     * The day the free days of [day] are counted from: its Monday, or the latest day after it on
     * which the number of the week's days or the fewer-days count changed, which started a new
     * span as a changed week target does. A revision of the week that changes only which days, or
     * what is planned, keeps the span, since the free days dealt are by number: the count's own
     * history is read, which [Dated.map] collapses to the days the count changed on. The days
     * before a span are not its days: they use no free day of it and make no gap in it, and its
     * free days are whole.
     */
    fun spanStartOn(day: LocalDate): LocalDate {
        val monday = mondayOf(day)
        return (week.map { it.size }.revisions + awayDays.revisions).mapNotNull { it.from }.filter { it > monday && it <= day }.maxOrNull() ?: monday
    }

    /**
     * What the week starting on [monday] asks, as it is shown: its days that are judged, every
     * day that is neither sick nor an away day the view is not due on by that day's own rule,
     * less the free days its Sunday's rule leaves, and never below zero. With the seeded rules it
     * is the holiday table of 5, 5, 5, 4, 4, 3, 3, 2, a day less for each sick day.
     */
    fun weekAsks(monday: LocalDate, daysOff: HolidaySchedule): Int {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        val judged = (0L until DaysInAWeek).count { monday.plusDays(it).let { day -> !daysOff.isSick(day) && !isNotDueAwayOn(day, daysOff) } }
        return (judged - freeDaysOn(monday.plusDays(DaysToSunday), daysOff)).coerceAtLeast(0)
    }

    /** The days any of the dated settings changed on, and the beginning: every day the rules can differ on. */
    private val changeDays: List<LocalDate?>
        get() = listOf(null) + (week.revisions + whenAway.revisions + awayDays.revisions + rewards.tokenSteps.revisions).mapNotNull { it.from }
}

/**
 * The week Activity asks for: its chosen days, Monday first, each with the activity planned for
 * it or none, "Any activity". One to seven days, each once, in the week's order. The number of
 * days is the week's target and all the judging reads; which days and what is planned decide
 * the reminder and name the rest days.
 */
data class WeekPlan(val days: List<PlannedDay>) {
    init {
        require(days.size in DaysAWeek) { "A week chooses ${DaysAWeek.first} to ${DaysAWeek.last} days." }
        require(days.map { it.day } == days.map { it.day }.distinct().sorted()) { "A week's days are each chosen once, in the week's order." }
    }

    val size: Int get() = days.size

    val chosen: Set<DayOfWeek> get() = days.mapTo(LinkedHashSet()) { it.day }

    /** The ID of the activity planned on [day], or null for any activity or a day not chosen. */
    fun plannedOn(day: DayOfWeek): String? = days.firstOrNull { it.day == day }?.activityId

    /** This week with [chosen] as its days: a day that stays keeps what is planned on it, a day new to the week plans any activity. */
    fun withDays(chosen: Set<DayOfWeek>): WeekPlan = WeekPlan(chosen.sorted().map { day -> days.firstOrNull { it.day == day } ?: PlannedDay(day) })

    /** This week with [activityId] planned on [day], null for any activity; a day not chosen is left as it is. */
    fun planning(day: DayOfWeek, activityId: String?): WeekPlan = WeekPlan(days.map { if (it.day == day) it.copy(activityId = activityId) else it })

    /** This week with any activity planned where [activityId] was. */
    fun withoutActivity(activityId: String): WeekPlan = WeekPlan(days.map { if (it.activityId == activityId) it.copy(activityId = null) else it })

    companion object {
        /** A week of [days], in any order, each planning any activity. */
        fun of(vararg days: DayOfWeek): WeekPlan = WeekPlan(days.sorted().map { PlannedDay(it) })
    }
}

/** One chosen day of the week, with the activity planned for it or null for any activity. */
data class PlannedDay(val day: DayOfWeek, val activityId: String? = null)

/**
 * One bank of passes, which Activity's weeks fill by their active days: its size, and the steps a
 * week earns by ([tokenSteps]), each dated like every setting that judges weeks; and [periods],
 * the days it takes part in the streak, as a checklist's are: null for its whole history, as
 * Activity's always was, a list once it ever left. Activity is judged day by day, so a day
 * outside its periods is not one of its week's days, as an away day it is not due on is not.
 */
data class ActivityRewards(
    val maxPasses: Dated<Int>,
    val tokenSteps: Dated<ActivityTokenSteps> = Dated.always(DefaultActivityTokenSteps),
    val periods: List<RewardPeriod>? = null,
) {
    constructor(maxPasses: Int, tokenSteps: ActivityTokenSteps = DefaultActivityTokenSteps, periods: List<RewardPeriod>? = null) :
        this(Dated.always(maxPasses), Dated.always(tokenSteps), periods)

    init {
        require(maxPasses.revisions.all { it.value in ActivityBankSize }) { "Activity's bank holds ${ActivityBankSize.first} to ${ActivityBankSize.last} passes." }
        periods?.requireInOrder()
    }

    fun takesPartOn(date: LocalDate): Boolean = periods.include(date)
}

/**
 * One step of what a week earns: once its recorded active days reach the week's days and
 * [daysMore], reaching it adds [tokens] to what the week earns. A step is days above the week's
 * target, not a number of days, so a changed week moves it along.
 */
data class ActivityTokenStep(val daysMore: Int, val tokens: Int)

/**
 * The steps a week earns tokens by, in days: the first is the week's days themselves, each
 * further one a day more than the one before, so [ActivityTokenStep.daysMore] runs 0, 1, 2, …,
 * each with tokens of its own ([TokensAStep]). A week earns the tokens of every step it reaches,
 * added up, and nothing below the first. How many steps there is room for follows from the
 * week's days ([roomFor]), which the configuration asks on every day. Anything else is refused
 * here, so a list that could not be stored or shown is never built.
 */
data class ActivityTokenSteps(val steps: List<ActivityTokenStep>) {
    init {
        require(steps.isNotEmpty() && steps.size <= DaysInAWeek) { "A week earns by 1 to $DaysInAWeek steps." }
        require(steps.withIndex().all { (index, step) -> step.daysMore == index }) { "The first step is the week's days, and each further step one day more than the one before." }
        require(steps.all { it.tokens in TokensAStep }) { "A step adds ${TokensAStep.first} to ${TokensAStep.last} tokens." }
    }

    /** The most a week can earn: what every step adds, together. */
    val total: Int get() = steps.sumOf { it.tokens }

    /**
     * What a week has earned once it reaches each step, in the steps' order: the tokens of that
     * step and of every one below it, which is what the walk is handed as a step's passes.
     */
    val runningTotals: List<Int> get() = steps.runningFold(0) { earned, step -> earned + step.tokens }.drop(1)

    /** These steps cut to the room a week of [days] leaves, the first always kept. */
    fun fitting(days: Int): ActivityTokenSteps = ActivityTokenSteps(steps.take(roomFor(days)))

    fun canGrow(days: Int): Boolean = steps.size < roomFor(days)

    /** One step more, a day above the last, adding one token. */
    fun grown(): ActivityTokenSteps = ActivityTokenSteps(steps + ActivityTokenStep(steps.size, TokensAStep.first))

    /** These steps without the further step at [index], the ones after it moved down a day, since the steps are consecutive; the first stays. */
    fun without(index: Int): ActivityTokenSteps {
        require(index in 1 until steps.size) { "The first step stays, and there is no step $index." }
        return ActivityTokenSteps(steps.filterIndexed { at, _ -> at != index }.mapIndexed { at, step -> step.copy(daysMore = at) })
    }

    fun withTokens(index: Int, tokens: Int): ActivityTokenSteps = ActivityTokenSteps(steps.mapIndexed { at, step -> if (at == index) step.copy(tokens = tokens) else step })

    companion object {
        /** The most steps a week of [days] leaves room for: one at the days and one for each day up to seven. */
        fun roomFor(days: Int): Int = 1 + (DaysInAWeek - days)
    }
}


/**
 * One activity a person lists: what it is called, the icon it wears, which decides its color
 * through Activity's presentation, and its routine if it has one ([routine]), the days and
 * exercises a session of it is recorded by. A later part of an activity, a timer, a map or notes,
 * is one more optional field beside the routine. A removed one is no longer offered; its entries
 * and sessions keep its name and count, and saving its name again brings it back.
 */
data class ListedActivity(
    val id: String,
    val label: String,
    val icon: ViewIcon,
    val removed: Boolean = false,
    /** Null for an activity that never had a routine; one whose routine was removed keeps it with every day marked removed. */
    val routine: Routine? = null,
    /** Whether the activity is recorded with GPS: today's log of it starts a recording of the route, as running or cycling does. */
    val recordsRoute: Boolean = false,
) {
    init {
        requireId(id)
        requireLabel(label)
    }

    /** Whether the activity offers a routine on the screen: it has one with a day still retained. */
    val hasRoutine: Boolean get() = routine?.isOffered == true

    /** Whether the activity offers a recording: a routine, where it has one, is what its log is. */
    val offersRecording: Boolean get() = recordsRoute && !hasRoutine

    fun day(dayId: String): RoutineDay? = routine?.day(dayId)

    fun exercise(exerciseId: String): RoutineExercise? = routine?.exercise(exerciseId)
}

/** The routine day next in turn for this activity, by its own sessions among [sessions]; null while it offers no routine. */
fun ListedActivity.nextRoutineDay(sessions: List<WorkoutSession>): RoutineDay? = routine?.nextDay(sessions.filter { it.activityId == id })

/**
 * The one way two activity names are compared, everywhere a name is matched: trimmed and ignoring
 * case, as Statistics grouped entries by their names before activities were stored.
 */
fun activityNameKey(name: String): String = name.trim().lowercase(Locale.ROOT)

private fun <T> Dated<T>.at(day: LocalDate?): T = if (day == null) revisions.first().value else on(day)

private fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private const val DaysInAWeek = 7
private const val DaysToSunday = 6L

/** The ID Activity's configuration is stored under; there is one. */
const val ActivityViewId = "activity"

/** What a week can ask: one day to every day. */
val DaysAWeek = 1..7

/**
 * The week every installation starts with, which is the week the code planned before it was
 * stored: Monday, Wednesday and Friday the gym, Tuesday bouldering, Thursday any activity, the
 * weekend free. Five days, so every number Activity was seeded with holds.
 */
val DefaultActivityWeek = WeekPlan(
    listOf(
        PlannedDay(DayOfWeek.MONDAY, GymActivityId),
        PlannedDay(DayOfWeek.TUESDAY, BoulderingActivityId),
        PlannedDay(DayOfWeek.WEDNESDAY, GymActivityId),
        PlannedDay(DayOfWeek.THURSDAY),
        PlannedDay(DayOfWeek.FRIDAY, GymActivityId),
    ),
)

/** What a week spent entirely away can ask: one day to a day below the most a week asks. */
val AwayDays = 1..6

val ActivityBankSize = 1..9

/** What every installation asks a week at first, the days of [DefaultActivityWeek], and what Activity always asked. */
const val DefaultActivityDaysAWeek = 5

/** What a step adds, as a time view's step does. */
val TokensAStep = 1..5

/** What every installation earns by at first: one token at the week's days, which is what Activity always earned. */
val DefaultActivityTokenSteps = ActivityTokenSteps(listOf(ActivityTokenStep(0, 1)))

/** What a week away asked at first: with five days a week exactly the holiday table Activity always had. */
const val DefaultActivityAwayDays = 2

const val DefaultActivityBankSize = 3

/** The IDs of the seeded activities: the gym, which the first tile always was, and the three activities beside it. */
const val GymActivityId = "gym"
const val BoulderingActivityId = "bouldering"
const val CyclingActivityId = "cycling"
const val JoggingActivityId = "jogging"

/** The names the seeded configuration is written with, in the app language of that moment. */
data class ActivityLabels(
    val activity: String,
    val bouldering: String,
    val cycling: String,
    val jogging: String,
    val gym: GymLabels,
)

/**
 * Activity as a new installation stores it: nothing listed, and off Home from the beginning
 * ([de.nicolasmahn.momentum.domain.settings.NewInstallationVisibility]), so a person meets no
 * activities or week they did not choose. A week has at least one day, so these three stand until
 * the first start or Settings changes them; while Activity is off nothing judges or reminds by them.
 */
fun newInstallationActivity(label: String) = ActivityConfiguration(
    label = label,
    icon = ViewIcon.Hiking,
    hue = ViewHue.Green,
    week = Dated.always(WeekPlan(listOf(PlannedDay(DayOfWeek.MONDAY), PlannedDay(DayOfWeek.WEDNESDAY), PlannedDay(DayOfWeek.FRIDAY)))),
    whenAway = Dated.always(WhenAway.LowerTarget),
    awayDays = Dated.always(DefaultActivityAwayDays),
    rewards = ActivityRewards(DefaultActivityBankSize),
    activities = emptyList(),
)

/**
 * Activity as it was judged and shown before an installation started empty: five days a week, two
 * of them free on a week away, three tokens, the gym with the routine the app always had and the
 * three activities beside it. A state from before Activity was stored reads these rules, written
 * once with [labels] in the app language of that moment.
 */
fun defaultActivityConfiguration(labels: ActivityLabels) = ActivityConfiguration(
    label = labels.activity,
    icon = ViewIcon.Hiking,
    hue = ViewHue.Green,
    week = Dated.always(DefaultActivityWeek),
    whenAway = Dated.always(WhenAway.LowerTarget),
    awayDays = Dated.always(DefaultActivityAwayDays),
    rewards = ActivityRewards(DefaultActivityBankSize),
    activities = listOf(
        ListedActivity(GymActivityId, labels.gym.gym, ViewIcon.Dumbbell, routine = defaultGymRoutine(labels.gym)),
        ListedActivity(BoulderingActivityId, labels.bouldering, ViewIcon.Bouldering),
        ListedActivity(CyclingActivityId, labels.cycling, ViewIcon.Bicycle),
        ListedActivity(JoggingActivityId, labels.jogging, ViewIcon.Running),
    ),
)

/** The IDs of the seeded activities, which a person cannot add again and the migration from version 29 maps the old names to. */
val SeededActivityIds: Set<String> = setOf(GymActivityId, BoulderingActivityId, CyclingActivityId, JoggingActivityId)

private val EnglishExerciseNames: Map<String, String> = mapOf(
    "bench_press" to "Bench Press",
    "back_squat" to "Back Squat",
    "row" to "Row",
    "lateral_raises" to "Lateral Raises",
    "triceps_pushdowns" to "Triceps Pushdowns",
    "pull_ups" to "Pull Ups",
    "dips" to "Dips",
    "bulgarian_split_squat" to "Bulgarian Split Squat",
    "preacher_hammer_curls" to "Preacher Hammer Curls",
    "barbell_deadlift" to "Barbell Deadlift",
    "overhead_press" to "Overhead Press",
    "face_pulls" to "Face Pulls",
    "hamstrings" to "Hamstrings",
)

/**
 * The names the seeded configuration had before it was stored, in every language: what the
 * tiles saved an entry under, and the names an exercise's log keeps. The migration from version
 * 29 reads them, and a seeded label that another activity's name would take keeps them.
 */
val EnglishActivityLabels = ActivityLabels(
    activity = "Activity",
    bouldering = "Bouldering",
    cycling = "Cycling",
    jogging = "Jogging",
    gym = GymLabels(gym = "Gym", dayA = "Day A", dayB = "Day B", dayC = "Day C", exercise = EnglishExerciseNames::getValue),
)


/**
 * Activity's rules as a new installation has them, for judging where no configuration is at hand,
 * and what a state from before the configuration was stored reads as before the repository puts
 * its words in the app language ([withSeededLabelsOf]). It wears the English names, which are
 * what the records of those states hold.
 */
val SeededActivityRules: ActivityConfiguration = defaultActivityConfiguration(EnglishActivityLabels)

/** The gym as every installation starts with it, in English: the first tile, with the routine the app always had. */
val SeededGym: ListedActivity get() = requireNotNull(SeededActivityRules.activity(GymActivityId))

/**
 * This configuration, read from a state that stored none, with the seeded words of [seed], the
 * default in the app language: the view's name, the four seeded activities' and the names of the
 * gym's routine days and exercises. Everything a person's own keeps its name. A seeded activity
 * whose name in the app language another activity already has keeps its English name, so names
 * stay unique and neither is merged.
 */
fun ActivityConfiguration.withSeededLabelsOf(seed: ActivityConfiguration): ActivityConfiguration {
    val taken = activities.filterNot { it.id in SeededActivityIds }.mapTo(HashSet()) { activityNameKey(it.label) }
    return copy(
        label = seed.label,
        activities = activities.map { activity ->
            val seeded = seed.activity(activity.id)?.takeIf { activity.id in SeededActivityIds }
            val wanted = seeded?.label?.takeIf { activityNameKey(it) !in taken }
            activity.copy(
                label = wanted ?: activity.label,
                routine = activity.routine?.let { routine -> seeded?.routine?.let { routine.withLabelsOf(it) } ?: routine },
            )
        },
    )
}

/** This routine with the names [seed] has for the days and exercises it knows; the rest keep theirs. */
private fun Routine.withLabelsOf(seed: Routine): Routine = copy(
    days = days.map { day ->
        day.copy(
            label = seed.day(day.id)?.label ?: day.label,
            exercises = day.exercises.map { exercise -> exercise.copy(label = seed.exercise(exercise.id)?.label ?: exercise.label) },
        )
    },
)

/**
 * Activity as the walk, Mo's ledger and the screens judge and show it: its stored configuration,
 * or the seeded rules in a state that was never loaded through the repository and holds none.
 */
val AppState.activityRules: ActivityConfiguration get() = activity ?: SeededActivityRules
