package de.nicolasmahn.momentum.feature.statistics

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.ActivityTrackerRef
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.SubstancesTrackerRef
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.judgmentDayFor
import de.nicolasmahn.momentum.domain.activity.ActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistProgress
import de.nicolasmahn.momentum.domain.checklist.ChecklistTarget
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.isSchedule
import de.nicolasmahn.momentum.domain.checklist.liveCheckpoints
import de.nicolasmahn.momentum.domain.checklist.liveItems
import de.nicolasmahn.momentum.domain.checklist.recordableOn
import de.nicolasmahn.momentum.domain.checklist.tickOf
import de.nicolasmahn.momentum.domain.checklist.ticks
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoDayRecord
import de.nicolasmahn.momentum.domain.mo.MoRecordIndex
import de.nicolasmahn.momentum.domain.mo.MoStatus
import de.nicolasmahn.momentum.domain.mo.TrackerObservation
import de.nicolasmahn.momentum.domain.mo.statuses
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.substance.SubstanceGoal
import de.nicolasmahn.momentum.domain.substance.substanceJudgmentDayFor
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.feature.settings.checkpointTimingText
import de.nicolasmahn.momentum.feature.settings.workoutTimingText
import de.nicolasmahn.momentum.feature.substances.label
import de.nicolasmahn.momentum.feature.substances.suffixRes
import de.nicolasmahn.momentum.feature.substances.viewLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * What the export reads: the records, the day, the days off and reminders, and the app's own
 * judgments of every day ([observations], [history]), so the export says what the app decided and
 * never decides anything itself. [views] are the trackers to export, in Home's order.
 */
internal class StatisticsExportInput(
    val state: AppState,
    val today: LocalDate,
    val exportedAt: LocalDateTime,
    val daysOff: HolidaySchedule,
    val reminders: ReminderSettings,
    val observations: List<TrackerObservation>,
    val history: List<MoDayRecord>,
    val currentStreak: Int,
    val views: List<TrackerRef>,
)

/**
 * The export as Markdown: the streak, then each view's plan and every day since its first record,
 * each with the result the app judged. All of the history, never only what Statistics shows.
 * [text] resolves words in the app's language, and [locale] names weekdays.
 */
/** How a list nested under a plan line is indented in Markdown. */
private const val SubItem = "  "

internal fun statisticsExport(input: StatisticsExportInput, text: (UiText) -> String, locale: Locale): String =
    StatisticsExportWriter(input, text, locale).write()

private class StatisticsExportWriter(
    private val input: StatisticsExportInput,
    private val text: (UiText) -> String,
    private val locale: Locale,
) {
    private val state = input.state
    private val today = input.today
    private val records = MoRecordIndex(state, input.daysOff)
    private val statuses: Map<TrackerRef, Map<LocalDate, MoStatus>> =
        input.observations.associate { it.ref to it.statuses(today, input.daysOff) }
    private val historyByDay = input.history.associateBy { it.date }
    private val out = StringBuilder()

    private fun t(id: Int, vararg args: Any) = text(uiText(id, *args))
    private fun line(value: String = "") = out.append(value).append('\n')
    private fun number(value: Double) = text(UiText.Number(value))

    fun write(): String {
        val first = firstRecord() ?: today
        line(t(R.string.statistics_export_title, first.toString(), today.toString()))
        line()
        line(t(R.string.statistics_export_exported, input.exportedAt.truncatedTo(ChronoUnit.MINUTES).toString().replace('T', ' ')))
        line()
        terms()
        streak()
        input.views.forEach { ref ->
            when {
                ref == ActivityTrackerRef -> state.activity?.let { activity(it) }
                ref == SubstancesTrackerRef -> substances()
                ref.type == TrackerTypeId("checklist") -> state.checklists.firstOrNull { it.id == ref.id }?.let { checklist(it) }
                else -> state.measurements.firstOrNull { LoggedViewRef(LoggedViewKind.Measurement, it.id).trackerRef == ref }?.let { measurement(it) }
                    ?: state.questions.firstOrNull { LoggedViewRef(LoggedViewKind.Questions, it.id).trackerRef == ref }?.let { questions(it) }
                    ?: state.time.firstOrNull { LoggedViewRef(LoggedViewKind.Time, it.id).trackerRef == ref }?.let { time(it) }
            }
        }
        return out.toString().trimEnd() + "\n"
    }

    private fun firstRecord(): LocalDate? = sequenceOf(
        state.checklistDays.map { it.dateKey },
        state.measurementLogs.map { it.dateKey },
        state.activityEntries.map { it.dateKey },
        state.sessions.map { it.dateKey },
        state.substanceLogs.map { it.dateKey },
        state.substanceNoneDays.map { it.dateKey },
        state.questionEntries.map { it.dateKey },
        state.timeLogs.map { it.dateKey },
    ).flatten().mapNotNull(::parseDateKey).minOrNull()

    private fun terms() {
        line(t(R.string.statistics_export_terms))
        line()
        listOf(
            R.string.statistics_export_term_day,
            R.string.statistics_export_term_kept,
            R.string.statistics_export_term_missed,
            R.string.statistics_export_term_excused,
            R.string.statistics_export_term_rest,
            R.string.statistics_export_term_free,
            R.string.statistics_export_term_protected,
            R.string.statistics_export_term_streak,
        ).forEach { line(t(it)) }
        line(t(R.string.statistics_export_term_today, today.toString()))
        line()
    }

    private fun streak() {
        line(t(R.string.statistics_export_streak))
        line()
        // The longest run of kept days, counted as the streak counts them: a miss without a freeze ends it.
        var run = 0
        var longest = 0
        var since: LocalDate? = null
        input.history.sortedBy { it.date }.forEach { day ->
            when (day.mark) {
                MoDayMark.Perfect -> {
                    if (run == 0) since = day.date
                    run++
                    longest = maxOf(longest, run)
                }
                MoDayMark.Missed -> {
                    run = 0
                    since = null
                }
                else -> Unit
            }
        }
        line(t(R.string.statistics_export_streak_header))
        line("|---|---|---|")
        val days = { count: Int -> text(pluralText(R.plurals.statistics_export_days, count, count)) }
        line("| ${days(input.currentStreak)} | ${days(maxOf(longest, input.currentStreak))} | ${since?.toString().orEmpty()} |")
        line()
        val freezes = input.history.filter { it.mark == MoDayMark.Frozen }.map { it.date.toString() }
        line(t(R.string.statistics_export_freezes_used, freezes.joinToString(", ").ifEmpty { t(R.string.statistics_export_none) }))
        val sick = input.daysOff.sickDays.filter { it.start <= today }
        if (sick.isNotEmpty()) line(t(R.string.statistics_export_sick, sick.joinToString(", ") { range -> if (range.start == range.end) range.start.toString() else t(R.string.statistics_export_range, range.start.toString(), range.end.toString()) }))
        line()
    }

    /**
     * The result the app gave the due day [judged] of [ref], or blank where it judged nothing yet.
     * A record about the day before ([recorded]) is excused by that day's sickness as by its own.
     */
    private fun result(ref: TrackerRef, judged: LocalDate, rest: Boolean = false, recorded: LocalDate = judged): String {
        if (judged >= today) return ""
        if (input.daysOff.isSick(judged) || input.daysOff.isSick(recorded)) return t(R.string.statistics_export_excused_sick)
        return when (statuses[ref]?.get(judged)) {
            MoStatus.Kept -> t(R.string.statistics_export_kept)
            MoStatus.Free -> t(if (rest) R.string.statistics_export_rest_day else R.string.statistics_export_free_day)
            MoStatus.Missed -> missed(ref, judged)
            null -> if (input.daysOff.isHoliday(judged)) t(R.string.statistics_export_excused_away) else ""
        }
    }

    private fun missed(ref: TrackerRef, day: LocalDate): String {
        val record = historyByDay[day]
        return t(
            when {
                record?.mark == MoDayMark.Frozen -> R.string.statistics_export_protected_freeze
                record != null && ref in record.coveredTrackers -> R.string.statistics_export_protected_token
                else -> R.string.statistics_export_missed
            },
        )
    }

    private fun days(from: LocalDate?): Sequence<LocalDate> =
        if (from == null) emptySequence() else generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }

    private fun <T> dated(value: Dated<T>, show: (T) -> String): String {
        val now = show(value.current)
        val last = value.revisions.last()
        val before = value.revisions.dropLast(1).lastOrNull() ?: return now
        return t(R.string.statistics_export_since, now, requireNotNull(last.from).toString(), show(before.value))
    }

    private fun weekdays(days: Collection<DayOfWeek>) = days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.FULL, locale) }

    private fun heading(label: String) {
        line("## $label")
        line()
        line(t(R.string.statistics_export_plan))
    }

    private fun cell(value: String) = value.replace("|", "/").replace("\n", " ")

    // Activity

    private fun activity(activity: ActivityConfiguration) {
        heading(activity.label)
        val days = activity.week.current.size
        line(t(R.string.statistics_export_goal, dated(activity.week) { plan -> t(R.string.statistics_export_activity_days, plan.size, 7 - plan.size) } + ". " + t(R.string.statistics_export_activity_rule)))
        val planned = activity.week.current.days.map { planned ->
            val name = planned.day.getDisplayName(TextStyle.FULL, locale)
            planned.activityId?.let { activity.activity(it)?.label }?.let { "$name ($it)" } ?: name
        }
        if (days in 1..6) line(t(R.string.statistics_export_activity_planned, planned.joinToString(", ")))
        line(t(R.string.statistics_export_activity_activities, activity.retainedActivities.joinToString(", ") { listed ->
            if (listed.hasRoutine) t(R.string.statistics_export_activity_with_routine, listed.label) else listed.label
        }))
        activity.retainedActivities.filter { it.hasRoutine }.forEach { listed ->
            line(t(R.string.statistics_export_activity_routine, listed.label))
            requireNotNull(listed.routine).retainedDays.forEach { day ->
                // Resources lose leading spaces, so the nesting is added here.
                line(SubItem + t(R.string.statistics_export_routine_day, day.label, day.retainedExercises.joinToString("; ") { exercise ->
                    exercise.weight?.let { weight ->
                        t(R.string.statistics_export_exercise_weight, exercise.label, exercise.sets, exercise.reps.first, exercise.reps.last, number(weight.startKg), number(weight.stepKg))
                    } ?: t(R.string.statistics_export_exercise, exercise.label, exercise.sets, exercise.reps.first, exercise.reps.last)
                }))
            }
        }
        if (input.reminders.workout.enabled) {
            line(t(R.string.statistics_export_reminder, text(workoutTimingText(activity.week.current.chosen, input.reminders.workout.fixedTime, locale))))
        }
        line()
        line(t(R.string.statistics_export_activity_header))
        line("|---|---|---|")
        val first = (state.activityEntries.map { it.dateKey } + state.sessions.map { it.dateKey }).mapNotNull(::parseDateKey).minOrNull()
        days(first).forEach { day ->
            val key = day.toString()
            // Each activity with its own minutes and sets beside its name, in the order recorded, so
            // a day with several never leaves a reader matching a duration to the wrong one.
            val entries = state.activityEntries.filter { it.dateKey == key }.map { entry ->
                entry.timestampMillis to recorded(activity.activityLabel(entry.activityId, entry.name), entry.durationMinutes, detail = "")
            }
            val sessions = state.sessions.filter { it.dateKey == key }.map { session ->
                val name = activity.activity(session.activityId)?.label.orEmpty()
                val label = activity.routineDay(session.day)?.label?.let { t(R.string.statistics_export_session, name, it) } ?: name
                session.timestampMillis to recorded(label, session.durationMinutes, sessionDetail(session))
            }
            val done = (entries + sessions).sortedBy { it.first }.map { it.second }.filter { it.isNotEmpty() }
            line("| $key | ${cell(done.joinToString("; "))} | ${result(ActivityTrackerRef, day, rest = true)} |")
        }
        line()
    }

    /** An activity's name with what was recorded of it; nothing is added for what was not, never "0 min". */
    private fun recorded(label: String, minutes: Int?, detail: String): String {
        val duration = minutes?.let { t(R.string.statistics_export_minutes, it) }
        val facts = if (duration != null && detail.isNotEmpty()) t(R.string.statistics_export_minutes_and_sets, duration, detail) else duration ?: detail
        return if (facts.isEmpty()) label else t(R.string.statistics_export_activity_done, label, facts)
    }

    private fun sessionDetail(session: WorkoutSession): String = session.exerciseLogs.filter { it.sets.isNotEmpty() }.joinToString("; ") { log ->
        t(R.string.statistics_export_exercise_done, log.exerciseName, runs(log.sets).joinToString(", ") { (set, count) ->
            val reps = number(set.reps)
            set.weightKg?.let { t(R.string.statistics_export_sets_weight, count, reps, number(it)) } ?: t(R.string.statistics_export_sets, count, reps)
        })
    }

    /** Equal sets in a row, as one "3 × 8 at 60 kg". */
    private fun runs(sets: List<LoggedSet>): List<Pair<LoggedSet, Int>> = sets.fold(mutableListOf()) { runs, set ->
        if (runs.isNotEmpty() && runs.last().first == set) runs[runs.lastIndex] = set to runs.last().second + 1 else runs += set to 1
        runs
    }

    // Checklists

    private fun checklist(checklist: ChecklistConfiguration) {
        val ref = TrackerRef(TrackerTypeId("checklist"), checklist.id)
        heading(checklist.label)
        val checkpoints = checklist.liveCheckpoints(today)
        val items = checklist.liveItems(today)
        // The checklist's target is only its items' default, so the goal names one target only while
        // every item follows it; otherwise each item names its own.
        val shared = items.map { it.target ?: checklist.target }.ifEmpty { listOf(checklist.target) }.distinct().singleOrNull()
        line(t(R.string.statistics_export_goal, shared?.let(::targetText) ?: t(R.string.statistics_export_checklist_per_item)))
        aboutLine(checklist.about)
        fun itemText(item: de.nicolasmahn.momentum.domain.checklist.ChecklistItem): String {
            var label = item.label
            if (shared == null) label = t(R.string.statistics_export_item_due, label, itemTargetText(item.target ?: checklist.target))
            item.from?.let { label = t(R.string.statistics_export_item_from, label, it.toString()) }
            item.until?.let { label = t(R.string.statistics_export_item_until, label, it.toString()) }
            return label
        }
        if (checklist.isSchedule(today)) {
            checkpoints.forEach { checkpoint ->
                val at = items.filter { it.checkpoints?.contains(checkpoint.id) ?: true }
                line(t(R.string.statistics_export_checkpoint, checkpoint.label ?: checklist.label, text(checkpointTimingText(checkpoint.at, input.reminders)), at.joinToString(", ") { itemText(it) }))
            }
        } else {
            line(t(R.string.statistics_export_items, items.joinToString(", ") { itemText(it) }))
            checkpoints.singleOrNull()?.takeIf { it.remind }?.let { line(t(R.string.statistics_export_reminder, text(checkpointTimingText(it.at, input.reminders)))) }
        }
        line()
        line(t(R.string.statistics_export_checklist_header))
        line("|---|---|---|---|")
        val first = state.checklistDays.filter { it.checklistId == checklist.id }.mapNotNull { parseDateKey(it.dateKey) }.minOrNull()
        val schedule = checklist.isSchedule(today)
        days(first).forEach { day ->
            val progress = records.progress(checklist, day)
            fun named(checkpointLabel: String?, item: String) = if (schedule && checkpointLabel != null) t(R.string.statistics_export_at_checkpoint, checkpointLabel, item) else item
            val notDone = progress.checkpoints.flatMap { checkpoint -> checkpoint.unmet.map { named(checkpoint.checkpoint.label, it.label) } }
            line("| $day | ${cell(doneOn(checklist, progress, ::named))} | ${cell(notDone.joinToString(", "))} | ${checklist.judgmentDayFor(day)?.let { result(ref, it, recorded = day) }.orEmpty()} |")
        }
        line()
    }

    /**
     * What a day's row says was done, as the day's form shows it: every recorded tick, due that day
     * or not, even of an item whose dates or season have since moved, and an item that logs only
     * misses with no miss recorded as assumed done, never as done. "All" only where the ticks are
     * exactly the boxes the day offered, so a part never reads as the whole.
     */
    private fun doneOn(checklist: ChecklistConfiguration, progress: ChecklistProgress, named: (String?, String) -> String): String {
        val record = progress.recorded
        // An edited item or checkpoint is kept as dated versions of one ID, so each tick is named once.
        val known = checklist.checkpoints.flatMap { checkpoint -> checklist.items.map { item -> Triple(tickOf(checkpoint.id, item.id), checkpoint, item) } }
            .distinctBy { it.first }
        val ticked = known.mapTo(HashSet()) { it.first }.filterTo(HashSet()) { it in record.ticks }
        if (ticked.isNotEmpty() && ticked == checklist.recordableOn(progress.date).ticks) return t(R.string.statistics_export_all)
        return known.mapNotNull { (tick, checkpoint, item) ->
            when {
                tick in ticked -> named(checkpoint.label, item.label)
                tick in progress.exceptionsOnly && tick !in record.misses -> t(R.string.statistics_export_assumed, named(checkpoint.label, item.label))
                else -> null
            }
        }.joinToString(", ")
    }

    private fun itemTargetText(target: ChecklistTarget): String = when (target) {
        ChecklistTarget.PerDay -> t(R.string.statistics_export_item_every_day)
        is ChecklistTarget.Weekdays -> weekdays(target.days)
        is ChecklistTarget.PerWeek -> text(pluralText(R.plurals.statistics_export_item_per_week, target.times, target.times))
    }

    private fun targetText(target: ChecklistTarget): String = when (target) {
        ChecklistTarget.PerDay -> t(R.string.statistics_export_checklist_every_day)
        is ChecklistTarget.Weekdays -> t(R.string.statistics_export_checklist_weekdays, weekdays(target.days))
        is ChecklistTarget.PerWeek -> text(pluralText(R.plurals.statistics_export_checklist_per_week, target.times, target.times))
    }

    // Timers

    private fun time(view: TimeConfiguration) {
        val ref = LoggedViewRef(LoggedViewKind.Time, view.id).trackerRef
        heading(view.label)
        line(t(R.string.statistics_export_goal, dated(view.goal) { t(R.string.statistics_export_time_goal, it) } + "."))
        line(t(R.string.statistics_export_time_rule))
        line(t(R.string.statistics_export_time_days_off))
        if (view.checkpoint.remind) line(t(R.string.statistics_export_reminder, text(checkpointTimingText(view.checkpoint.at, input.reminders))))
        line()
        line(t(R.string.statistics_export_time_header))
        line("|---|---|---|---|")
        val logs = state.timeLogs.filter { it.viewId == view.id }
        val first = logs.mapNotNull { parseDateKey(it.dateKey) }.minOrNull() ?: run { line(); return }
        generateSequence(first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) { it.plusWeeks(1) }.takeWhile { it <= today }.forEach { monday ->
            val perDay = (0L until 7L).map { monday.plusDays(it) }.filter { it <= today }.map { day -> logs.filter { it.dateKey == day.toString() }.sumOf { it.minutes ?: 0 } }
            val sunday = monday.plusDays(6)
            // What the week asked, as the screen and the judgment read it: its goal less its days off.
            line("| $monday | ${perDay.joinToString(", ")} | ${t(R.string.statistics_export_of, perDay.sum(), view.weekAsks(monday, input.daysOff))} | ${result(ref, sunday)} |")
        }
        line()
    }

    // Measurements

    private fun measurement(view: MeasurementConfiguration) {
        val ref = LoggedViewRef(LoggedViewKind.Measurement, view.id).trackerRef
        heading(view.label)
        line(t(R.string.statistics_export_goal, t(R.string.statistics_export_measurement_goal, view.unit)))
        if (view.checkpoint.remind) line(t(R.string.statistics_export_reminder, text(checkpointTimingText(view.checkpoint.at, input.reminders))))
        line()
        line(t(R.string.statistics_export_measurement_header, cell(view.unit)))
        line("|---|---|---|")
        val logs = state.measurementLogs.filter { it.measurementId == view.id }
        days(logs.mapNotNull { parseDateKey(it.dateKey) }.minOrNull()).forEach { day ->
            val value = logs.firstOrNull { it.dateKey == day.toString() }?.let { number(it.value) }.orEmpty()
            line("| $day | $value | ${result(ref, day)} |")
        }
        line()
    }

    // Questions

    /**
     * Which day a view's record is about. A reader cannot see it in the dates alone: a record about
     * yesterday is made the morning after, and each row's date is the day it is about.
     */
    private fun aboutLine(about: Dated<de.nicolasmahn.momentum.domain.checklist.AboutDay>) {
        line(t(R.string.statistics_export_about, dated(about) { day ->
            t(if (day == de.nicolasmahn.momentum.domain.checklist.AboutDay.Yesterday) R.string.statistics_export_about_yesterday else R.string.statistics_export_about_today)
        }))
    }

    private fun questions(view: QuestionsConfiguration) {
        val ref = LoggedViewRef(LoggedViewKind.Questions, view.id).trackerRef
        heading(view.label)
        line(t(R.string.statistics_export_goal, t(R.string.statistics_export_questions_goal)))
        aboutLine(view.about)
        line(t(R.string.statistics_export_questions_title))
        view.questions.forEach { question -> line(SubItem + t(R.string.statistics_export_question, question.text, question.answers.joinToString(" / ") { it.label })) }
        if (view.checkpoint.remind) line(t(R.string.statistics_export_reminder, text(checkpointTimingText(view.checkpoint.at, input.reminders))))
        line()
        line("| ${t(R.string.statistics_export_date)} | ${view.questions.joinToString(" | ") { cell(it.text) }} | ${t(R.string.statistics_export_result)} |")
        line("|---|" + view.questions.joinToString("") { "---|" } + "---|")
        val entries = state.questionEntries.filter { it.viewId == view.id }
        days(entries.mapNotNull { parseDateKey(it.dateKey) }.minOrNull()).forEach { day ->
            val answers = entries.filter { it.dateKey == day.toString() }.maxByOrNull { it.timestampMillis }?.answers.orEmpty()
            val cells = view.questions.joinToString(" | ") { question -> cell(question.answers.firstOrNull { it.id == answers[question.id] }?.label.orEmpty()) }
            line("| $day | $cells | ${view.judgmentDayFor(day)?.let { result(ref, it, recorded = day) }.orEmpty()} |")
        }
        line()
    }

    // Substances

    private fun substances() {
        val settings = state.substances
        heading(text(settings.viewLabel()))
        val stayingAway = settings.trackedSubstances.filter { settings.isStayingAway(it.id) }
        val tracking = settings.trackedSubstances.filterNot { settings.isStayingAway(it.id) }
        line(t(R.string.statistics_export_goal, t(if (settings.goal.current == SubstanceGoal.StayingAway) R.string.statistics_export_substances_staying_away else R.string.statistics_export_substances_logging)))
        if (stayingAway.isNotEmpty()) line(t(R.string.statistics_export_staying_away, stayingAway.joinToString(", ") { text(it.label()) }))
        if (tracking.isNotEmpty()) line(t(R.string.statistics_export_just_tracking, tracking.joinToString(", ") { text(it.label()) }))
        if (settings.checkIn.remind) line(t(R.string.statistics_export_check_in, text(checkpointTimingText(settings.checkIn.at, input.reminders))))
        line()
        line(t(R.string.statistics_export_substances_header))
        line("|---|---|---|")
        val first = (state.substanceLogs.map { it.dateKey } + state.substanceNoneDays.map { it.dateKey }).mapNotNull(::parseDateKey).minOrNull()
        days(first).forEach { day ->
            val key = day.toString()
            val uses = state.substanceLogs.filter { it.dateKey == key }.sortedBy { it.timestampMillis }.map { log ->
                val name = settings.substance(log.substanceId)?.let { text(it.label()) } ?: log.substanceId
                val amount = log.amount?.let { amount -> "${number(amount)} ${log.amountUnit?.let { t(it.suffixRes) }.orEmpty()}".trim() }
                amount?.let { t(R.string.statistics_export_use, name, it) } ?: name
            }
            val none = state.substanceNoneDays.any { it.dateKey == key }
            val logged = if (uses.isEmpty() && none) t(R.string.statistics_export_none) else uses.joinToString(", ")
            // A day is judged by the due day that asks about it, the morning after.
            line("| $key | ${cell(logged)} | ${result(SubstancesTrackerRef, substanceJudgmentDayFor(day), recorded = day)} |")
        }
        line()
    }
}
