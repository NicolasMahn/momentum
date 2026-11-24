package com.example.workouttracker.feature.statistics

import java.time.LocalDate
import androidx.annotation.StringRes
import com.example.workouttracker.R
import com.example.workouttracker.core.time.CalendarWeeksWindow
import com.example.workouttracker.core.time.calendarWeeksWindow
import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerStatistics
import com.example.workouttracker.domain.trackerStatisticsContributions
import com.example.workouttracker.domain.TrackerStatisticsContribution
import com.example.workouttracker.domain.TrackerStatisticsUnit
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.activityRules
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoDayRecord
import com.example.workouttracker.domain.mo.MoEvaluation
import com.example.workouttracker.domain.mo.MoStatus
import com.example.workouttracker.domain.mo.ObservationTarget
import com.example.workouttracker.domain.mo.TrackerObservation
import com.example.workouttracker.domain.mo.monday
import com.example.workouttracker.domain.mo.statuses
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.settings.ViewRef
import com.example.workouttracker.domain.ref
import com.example.workouttracker.feature.logactivity.ActivityMark
import com.example.workouttracker.feature.measurement.MeasurementHistoryWindow
import com.example.workouttracker.feature.measurement.measurementHistoryWindow

/**
 * How far back the whole screen looks. Every block shares one week-aligned window, so a row of a
 * calendar and a bar in a consistency row are the same week.
 */
internal enum class StatisticsRange(
    @param:StringRes val label: Int,
    /** The range as a phrase of time that ends a spoken sentence: "in the last 4 weeks". */
    @param:StringRes val spokenLabel: Int,
    val weekCount: Int,
) {
    FourWeeks(R.string.statistics_range_four_weeks, R.string.statistics_range_four_weeks_spoken, weekCount = 4),
    ThreeMonths(R.string.statistics_range_three_months, R.string.statistics_range_three_months_spoken, weekCount = 13),
    Year(R.string.statistics_range_year, R.string.statistics_range_year_spoken, weekCount = 52),
}

/**
 * The range Statistics opens on: three months, where looking further is the point, unless nothing
 * is older than [ShortHistoryWeeks]; four weeks then show all there is without empty months before it.
 */
internal fun defaultStatisticsRange(state: AppState, today: LocalDate): StatisticsRange {
    val oldest = sequenceOf(
        state.checklistDays.map { it.dateKey },
        state.measurementLogs.map { it.dateKey },
        state.activityEntries.map { it.dateKey },
        state.sessions.map { it.dateKey },
        state.substanceLogs.map { it.dateKey },
        state.substanceNoneDays.map { it.dateKey },
        state.questionEntries.map { it.dateKey },
        state.timeLogs.map { it.dateKey },
    ).flatten().mapNotNull(::parseDateKey).minOrNull()
    val shortHistory = oldest == null || oldest >= today.minusWeeks(ShortHistoryWeeks)
    return if (shortHistory) StatisticsRange.FourWeeks else StatisticsRange.ThreeMonths
}

private const val ShortHistoryWeeks = 8L

/**
 * One Monday-to-Sunday week of a view. [kept] and [counted] are what its rule judged: days for a
 * daily view, the week itself for a weekly amount, judged once it is over. [fill] is the share of
 * the whole week's goal done, so every row's bars mean the same, and the [running] week, which is
 * not over, shows how far it is instead of how it went.
 */
internal data class WeekConsistency(
    val kept: Int,
    val counted: Int,
    val fill: Float = if (counted == 0) 0f else kept.toFloat() / counted,
    /** For a weekly amount, the minutes recorded in the week and the minutes it asked. */
    val minutes: WeekMinutes? = null,
    /** The week today is in. It is drawn apart and left out of the row's number. */
    val running: Boolean = false,
    /** Whether the week has anything to draw; a week before the view's first record has nothing. */
    val started: Boolean = counted > 0 || fill > 0f || minutes != null,
)

/** A week of a weekly amount: what was recorded against what the week [asked]. */
internal data class WeekMinutes(val recorded: Int, val asked: Int)

/** What a consistency row counts in. */
internal enum class ConsistencyUnit {
    /** Days the rule was kept on, of the days it asked. */
    Days,

    /** Recorded days, told as days a week: Activity, whose goal is not every day. */
    DaysPerWeek,

    /** A weekly amount: each week that is over is judged once, by how much of its goal it reached. */
    Weeks,
}

/**
 * How well one view's own rule was kept. These are facts: a day a token or a freeze protected was
 * not kept, and a day the rule did not ask (sick, away, a rest day) is not counted. Days before the
 * view's first record do not count either, so a habit taken up last week is measured against last
 * week and not against the year.
 */
internal data class FeatureConsistency(
    val ref: TrackerRef,
    val label: String,
    val unit: ConsistencyUnit = ConsistencyUnit.Days,
    val weeks: List<WeekConsistency>,
) {
    private val finished: List<WeekConsistency> = weeks.filterNot { it.running }

    /** What the rule judged in the weeks that are over. */
    val kept: Int = finished.sumOf { it.kept }
    val counted: Int = finished.sumOf { it.counted }

    /** The week that is not over, once the view has begun. */
    val thisWeek: WeekConsistency? = weeks.lastOrNull()?.takeIf { it.running && it.started }

    /**
     * The row's number: how much of their goal the finished weeks reached, the average of their
     * bars. A day weighs the same in every daily week, so a short first week does not weigh as
     * much as a whole one; a weekly amount is one goal a week. Until a week is over, it is how far
     * this week is once something is done, so a new habit shows its progress instead of nothing.
     */
    val rate: Double? = when {
        counted > 0 && unit == ConsistencyUnit.Weeks -> finished.filter { it.counted > 0 }.map { it.fill.toDouble() }.average()
        counted > 0 -> kept.toDouble() / counted
        else -> thisWeek?.fill?.takeIf { it > 0f }?.toDouble()
    }

    val daysPerWeek: Double? = rate?.let { it * DaysPerWeek }
}

internal data class MeasurementSummary(
    val configuration: MeasurementConfiguration,
    val window: MeasurementHistoryWindow,
    val latestValue: Double?,
    /** The trend's movement across the range; null until two values exist to compare. */
    val change: Double?,
)

/**
 * One day of the streak calendar: what the streak made of it. This is the game's reading of the
 * day, never a record, so nothing factual on the screen is built from it.
 */
internal data class StreakCalendarDay(
    val dateKey: String,
    val mark: MoDayMark,
    /** On a [MoDayMark.Protected] day, whose tokens covered it, in the order Mo lists his tasks. */
    val tokens: List<TrackerRef> = emptyList(),
    /** On a [MoDayMark.Missed] day, whether it ended a running streak rather than finding none. */
    val restart: Boolean = false,
)

internal data class StreakCalendar(
    /** The window's days, Monday first, including the ones after today. */
    val days: List<StreakCalendarDay>,
) {
    val keptDays: Int = days.count { it.mark == MoDayMark.Perfect }
    val frozenDays: Int = days.count { it.mark == MoDayMark.Frozen }

    /** The days a token protected, whose token it was: one of Mo's tasks', or a view's he does not watch. */
    val protectedDays: Int = days.count { it.mark == MoDayMark.Protected }

    /** Whether a day was protected by no token of Mo's, a made view's or Substances', which the calendar marks plainly. */
    val protectedByOthers: Boolean = days.any { it.mark == MoDayMark.Protected && it.tokens.isEmpty() }

    /** How many days each task's token covered, in the order Mo lists his tasks; a day two tokens covered counts for both. */
    val tokensByTracker: Map<TrackerRef, Int> = days
        .flatMap { it.tokens }
        .groupingBy { it }
        .eachCount()
        .toSortedMap(compareBy<TrackerRef> { it.type.value }.thenBy { it.id })
    val missedDays: Int = days.count { it.mark == MoDayMark.Missed }
    val restarts: Int = days.count { it.restart }
}

/**
 * One kind of thing recorded: an activity, by its ID, under the label its configuration gives it
 * now, falling back on the name its first entry kept for one the configuration never knew. An
 * entry of the activity and a session of its routine with a set are one kind alike.
 */
internal data class ActivityKind(val activityId: String, val label: String) {
    val mark: ActivityMark get() = ActivityMark.Listed(activityId)
}

internal data class ActivityCalendarDay(
    val dateKey: String,
    /** What was recorded that day, sessions first; empty on a day without a recorded activity. */
    val kinds: List<ActivityKind>,
    /** A day after today: it holds its place in the week and nothing else. */
    val upcoming: Boolean = false,
)

internal data class ActivityKindCount(
    val kind: ActivityKind,
    val days: Int,
)

/**
 * The days something active was recorded: a gym session with at least one set, or any other
 * logged activity. Only records make a day here; a rest day or a token never does.
 */
internal data class ActivitySummary(
    val days: List<ActivityCalendarDay>,
    /** How regularly, measured from the first record on like every other feature. */
    val consistency: FeatureConsistency,
    /** Each kind with the days it was recorded on, most frequent first. */
    val kinds: List<ActivityKindCount>,
)

/** Everything the Statistics screen draws. A hidden feature has no entry, so it has no block. */
internal data class StatisticsSummary(
    val range: StatisticsRange,
    /** The Monday every block's time axis starts on. */
    val startDateKey: String,
    val consistency: List<FeatureConsistency>,
    val measurements: List<MeasurementSummary>,
    val activity: ActivitySummary?,
    /** Null when no streak was given, as in a screen shown without Mo. */
    val streak: StreakCalendar?,
)

internal fun statisticsSummary(
    state: AppState,
    todayKey: String,
    range: StatisticsRange,
    visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
    streakHistory: List<MoDayRecord>? = null,
    statisticsContributions: List<TrackerStatisticsContribution>? = null,
    /** Each view's rule; without them they are read from [state] as Mo reads them. */
    observations: List<TrackerObservation>? = null,
    daysOff: HolidaySchedule = HolidaySchedule(),
): StatisticsSummary {
    val window = calendarWeeksWindow(todayKey, range.weekCount)
    val today = requireNotNull(parseDateKey(todayKey))
    val rules = (observations ?: MoEvaluation(state, visibility, today, daysOff).observations).associateBy { it.ref }
    val recordsByMeasurement = state.measurementLogs.groupBy { it.measurementId }.mapValues { (_, logs) ->
        logs.map { MeasurementRecord(it.dateKey, it.timestampMillis, it.value) }
    }
    val contributions = statisticsContributions ?: state.trackerStatisticsContributions(visibility)
    val consistency = contributions.map { contribution ->
        // A view whose rule is unknown here can only say on which days it holds a record.
        rules[contribution.ref]?.let { rule -> keptConsistency(rule, contribution.label, window, today, daysOff) }
            ?: featureConsistency(
                facts = contribution.statistics,
                ref = contribution.ref,
                label = contribution.label,
                window = window,
            )
    }
    require(consistency.distinctBy { it.ref }.size == consistency.size) { "A tracker contributes statistics once." }
    return StatisticsSummary(
        range = range,
        startDateKey = window.dateKeys.first(),
        consistency = consistency,
        measurements = state.measurements.filter { visibility.shows(it.ref) }
            .map { measurementSummary(recordsByMeasurement[it.id].orEmpty(), it, todayKey, window) },
        activity = if (visibility.isVisible(DashboardFeature.Activity)) {
            activitySummary(state, window)
        } else {
            null
        },
        streak = streakHistory?.let { streakCalendar(it, window) },
    )
}

private fun featureConsistency(
    facts: TrackerStatistics,
    ref: TrackerRef,
    label: String,
    window: CalendarWeeksWindow,
): FeatureConsistency {
    val happened = window.dateKeys.filter { it in window.visibleDateKeys }
    val logged = happened.filterTo(mutableSetOf(), facts::isRecordedFor)
    val windowStart = window.dateKeys.first()
    // A record older than the window means the habit was already running when the window opened.
    val firstCountedKey = if (facts.recordedDateKeys.any { it < windowStart }) {
        windowStart
    } else {
        happened.firstOrNull { it in logged }
    }

    return FeatureConsistency(
        ref = ref,
        label = label,
        unit = when (facts.unit) {
            TrackerStatisticsUnit.RecordedDays -> ConsistencyUnit.Days
            TrackerStatisticsUnit.DaysPerWeek -> ConsistencyUnit.DaysPerWeek
        },
        weeks = window.dateKeys.chunked(DaysPerWeek).mapIndexed { index, week ->
            val ownDays = week.filter { key -> firstCountedKey != null && key >= firstCountedKey }
            val counted = ownDays.filter { it in window.visibleDateKeys }
            val kept = counted.count { it in logged }
            // The window ends with the week today is in; its days still to come are part of its goal.
            val running = index == window.dateKeys.size / DaysPerWeek - 1
            WeekConsistency(
                kept = kept,
                counted = counted.size,
                fill = if (ownDays.isEmpty()) 0f else kept.toFloat() / (if (running) ownDays.size else counted.size),
                running = running,
                started = ownDays.isNotEmpty() && counted.isNotEmpty(),
            )
        },
    )
}

/**
 * A view's row as its own rule judges it, the rule Mo and the export read. Today counts once it is
 * kept: until then it is not over, and not yet a miss.
 */
private fun keptConsistency(
    rule: TrackerObservation,
    label: String,
    window: CalendarWeeksWindow,
    today: LocalDate,
    daysOff: HolidaySchedule,
): FeatureConsistency {
    val statuses = rule.statuses(today, daysOff)
    val target = rule.target
    val weekCount = window.dateKeys.size / DaysPerWeek
    val weeks = window.dateKeys.chunked(DaysPerWeek).mapIndexed { index, week ->
        val monday = LocalDate.parse(week.first())
        val running = index == weekCount - 1
        when (target) {
            is ObservationTarget.WeeklyAmount -> {
                val sunday = LocalDate.parse(week.last())
                val began = rule.firstDay != null && rule.firstDay <= sunday && monday <= today
                val required = target.requiredIn(monday)
                val status = statuses[sunday]?.takeIf { sunday < today }
                // A week that is over and was not judged, such as one ending on a sick Sunday, is
                // drawn as not measured, never as a short week.
                val measured = began && required > 0 && (sunday >= today || status != null)
                val minutes = if (measured) WeekMinutes(target.amountIn(monday), required) else null
                WeekConsistency(
                    kept = if (status == MoStatus.Kept) 1 else 0,
                    counted = if (status == MoStatus.Kept || status == MoStatus.Missed) 1 else 0,
                    fill = minutes?.let { (it.recorded.toFloat() / it.asked).coerceAtMost(1f) } ?: 0f,
                    minutes = minutes,
                    running = running,
                )
            }
            is ObservationTarget.Daily -> {
                val days = week.map(LocalDate::parse)
                val judged = days.mapNotNull { day ->
                    statuses[day]?.takeIf { it == MoStatus.Kept || (it == MoStatus.Missed && day < today) }
                }
                val kept = judged.count { it == MoStatus.Kept }
                if (running) {
                    val began = rule.firstDay != null && rule.firstDay <= today
                    val asked = rule.daysAskedIn(days, today, target, daysOff)
                    WeekConsistency(
                        kept = kept,
                        counted = judged.size,
                        fill = if (asked > 0) (kept.toFloat() / asked).coerceAtMost(1f) else 0f,
                        running = true,
                        started = began && asked > 0,
                    )
                } else {
                    WeekConsistency(kept = kept, counted = judged.size)
                }
            }
        }
    }
    return FeatureConsistency(
        ref = rule.ref,
        label = label,
        unit = if (target is ObservationTarget.WeeklyAmount) ConsistencyUnit.Weeks else ConsistencyUnit.Days,
        weeks = weeks,
    )
}

/**
 * The days the running week asks of a daily rule, the ones still to come included: its days from
 * the rule's first day on that the rule counts and does not excuse, less the free days the week
 * leaves. A sick day is only known once it comes, so the goal can still shrink as the week goes.
 */
private fun TrackerObservation.daysAskedIn(
    week: List<LocalDate>,
    today: LocalDate,
    target: ObservationTarget.Daily,
    daysOff: HolidaySchedule,
): Int {
    val first = firstDay ?: return 0
    val asking = week.count { day ->
        day >= first && countsOn(day) &&
            (ignoreDaysOff || !(daysOff.isSick(day) || target.excusedOn(day) || target.asksNothingOn(day)))
    }
    // As the rule's own walk has it: the first week is the one the rule began in after its Monday.
    val firstWeek = first > today.monday()
    return (asking - target.freeDaysOn(today, firstWeek)).coerceAtLeast(0)
}

private fun measurementSummary(
    records: List<MeasurementRecord>,
    configuration: MeasurementConfiguration,
    todayKey: String,
    window: CalendarWeeksWindow,
): MeasurementSummary {
    val history = measurementHistoryWindow(
        logs = records,
        todayKey = todayKey,
        dayCount = window.visibleDateKeys.size,
        trendDays = configuration.trendDays,
        valuePolicy = configuration.valuePolicy,
    )
    val line = history.trendPoints
    // A lone reading is carried to today as a flat line; that is one value, not a change of zero.
    val startsBeforeWindow = line.firstOrNull()?.let { first ->
        history.recordedDays.none { it.dayIndex == first.dayIndex }
    } ?: false
    val comparable = history.recordedDays.size >= 2 ||
        (startsBeforeWindow && history.recordedDays.isNotEmpty())

    return MeasurementSummary(
        configuration = configuration,
        window = history,
        latestValue = line.lastOrNull()?.value,
        change = if (comparable) line.last().value - line.first().value else null,
    )
}

private fun activitySummary(
    state: AppState,
    window: CalendarWeeksWindow,
): ActivitySummary {
    val kindsByDate = activityKindsByDate(state)
    val days = window.dateKeys.map { key ->
        if (key in window.visibleDateKeys) {
            ActivityCalendarDay(key, kindsByDate[key].orEmpty())
        } else {
            ActivityCalendarDay(key, emptyList(), upcoming = true)
        }
    }
    // Most frequent first; among equals the activities in the configuration's order, which is the
    // person's with Gym first at the start, then one the configuration never knew, by its name.
    val order = state.activityRules.activities.withIndex().associate { (index, activity) -> activity.id to index }
    val kinds = days
        .flatMap { day -> day.kinds }
        .groupingBy { it }
        .eachCount()
        .map { (kind, count) -> ActivityKindCount(kind, count) }
        .sortedWith(
            compareByDescending<ActivityKindCount> { it.days }
                .thenBy { order[it.kind.activityId] ?: Int.MAX_VALUE }
                .thenBy { it.kind.label },
        )

    return ActivitySummary(
        days = days,
        consistency = featureConsistency(
            TrackerStatistics(kindsByDate.keys, TrackerStatisticsUnit.DaysPerWeek),
            ActivityTrackerRef,
            state.activityRules.label,
            window,
        ),
        kinds = kinds,
    )
}

/**
 * A session opened and left without a set is not a workout, and an entry without a name names
 * nothing, so neither makes a day active. A day holds each activity once, its sessions' first.
 */
private fun activityKindsByDate(state: AppState): Map<String, List<ActivityKind>> {
    val rules = state.activityRules
    val kindById = mutableMapOf<String, ActivityKind>()
    fun kindOf(activityId: String, recordedName: String) = kindById.getOrPut(activityId) { ActivityKind(activityId, rules.activityLabel(activityId, recordedName)) }
    val sessionKinds = state.sessions
        .filter { it.totalLoggedSets > 0 }
        .sortedBy { it.timestampMillis }
        .groupBy({ it.dateKey }) { session -> kindOf(session.activityId, session.activityId) }
    val entryKinds = state.activityEntries
        .filter { it.name.isNotBlank() }
        .sortedBy(ActivityEntry::timestampMillis)
        .groupBy(ActivityEntry::dateKey) { entry -> kindOf(entry.activityId, entry.name.trim()) }
    return (sessionKinds.keys + entryKinds.keys).associateWith { key -> (sessionKinds[key].orEmpty() + entryKinds[key].orEmpty()).distinct() }
}

private fun streakCalendar(
    history: List<MoDayRecord>,
    window: CalendarWeeksWindow,
): StreakCalendar {
    val byDate = history.associateBy { it.date.toString() }
    // A miss only restarts a streak that was running: the first due day ever, or one after another
    // miss, found it at zero already. Days that asked nothing leave the streak as it was.
    val restartKeys = buildSet {
        var previous: MoDayMark? = null
        history.sortedBy(MoDayRecord::date).forEach { record ->
            if (record.mark == MoDayMark.NothingDue || record.mark == MoDayMark.Sick) return@forEach
            if (record.mark == MoDayMark.Missed && previous != null && previous != MoDayMark.Missed) {
                add(record.date.toString())
            }
            previous = record.mark
        }
    }
    return StreakCalendar(
        days = window.dateKeys.map { key ->
            val record = byDate[key]
            when {
                key !in window.visibleDateKeys -> StreakCalendarDay(key, MoDayMark.Upcoming)
                // Before the streak's first day nothing was asked yet.
                record == null -> StreakCalendarDay(key, MoDayMark.NothingDue)
                else -> StreakCalendarDay(
                    dateKey = key,
                    mark = record.mark,
                    tokens = if (record.mark == MoDayMark.Protected) record.tokenTrackers() else emptyList(),
                    restart = key in restartKeys,
                )
            }
        },
    )
}

private const val DaysPerWeek = 7

/** Attribution uses the same tracker identity as banks and reservations. */
private fun MoDayRecord.tokenTrackers(): List<TrackerRef> =
    coveredTrackers.sortedWith(compareBy<TrackerRef> { it.type.value }.thenBy { it.id })
