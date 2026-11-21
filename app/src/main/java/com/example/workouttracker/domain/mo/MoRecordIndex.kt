package com.example.workouttracker.domain.mo




import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.time.minutesInWeek
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistProgress
import com.example.workouttracker.domain.checklist.ChecklistRecords
import com.example.workouttracker.domain.checklist.DayRecord
import com.example.workouttracker.domain.checklist.merged
import com.example.workouttracker.domain.checklist.progressOn
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.substance.isSubstanceUsedOn
import com.example.workouttracker.domain.substance.isSubstancesLoggedFor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.IdentityHashMap

/**
 * One evaluation's records grouped by the day they are dated, so judging a long history looks a day
 * up instead of scanning every record for every day. A group is built the first time something asks
 * for it, so a hidden feature costs nothing. It belongs to one evaluation of one [AppState] and is
 * never kept beyond it, so there is nothing to invalidate.
 *
 * Every answer still comes from the feature's own predicate, asked about that one day's records, so
 * what counts as kept here cannot drift from what the feature itself says. A checklist's days are
 * read against [daysOff], which a week item's free days depend on.
 */
internal class MoRecordIndex(val state: AppState, private val daysOff: HolidaySchedule) {
    private val recordsByChecklist by lazy(LazyThreadSafetyMode.NONE) {
        state.checklistDays.groupBy { it.checklistId }.mapValues { (_, days) ->
            days.groupBy { it.dateKey }.mapValues { (_, sameDay) -> sameDay.merged() }
        }
    }
    private val activityDays by lazy(LazyThreadSafetyMode.NONE) { state.activityEntries.mapTo(HashSet()) { it.dateKey } }

    // A gym session counts once it has a set; an opened, empty one is not a workout.
    private val workoutDays by lazy(LazyThreadSafetyMode.NONE) {
        state.sessions.filter { it.totalLoggedSets > 0 }.mapTo(HashSet()) { it.dateKey }
    }
    // The days only, never what was logged on them: a use and "none" are the same day here.
    private val substanceDays by lazy(LazyThreadSafetyMode.NONE) {
        state.substanceLogs.mapTo(HashSet()) { it.dateKey } + state.substanceNoneDays.map { it.dateKey }
    }
    // Each day's uses, read only for which substances they name, never how much.
    private val substanceUses by lazy(LazyThreadSafetyMode.NONE) { state.substanceLogs.groupBy { it.dateKey } }

    // The days each measurement has a record on, never the number: any record keeps its day.
    private val measurementDays by lazy(LazyThreadSafetyMode.NONE) {
        state.measurementLogs.groupBy({ it.measurementId }, { it.dateKey }).mapValues { (_, days) -> days.toHashSet() }
    }

    private val questionsByDay by lazy(LazyThreadSafetyMode.NONE) {
        state.questionEntries.groupBy { it.viewId }.mapValues { (_, entries) -> entries.groupBy { it.dateKey } }
    }
    // Reward predicates read dates, never the answers to a question.
    private val questionsDays by lazy(LazyThreadSafetyMode.NONE) {
        questionsByDay.mapValues { (_, days) -> days.keys }
    }

    // The days each time view has a log on, never its minutes: any log is a logged day.
    private val timeDays by lazy(LazyThreadSafetyMode.NONE) {
        state.timeLogs.groupBy({ it.viewId }, { it.dateKey }).mapValues { (_, days) -> days.toHashSet() }
    }

    // Each time view's logs by their week, so a week's minutes are added up from that week's logs alone.
    private val timeWeeks by lazy(LazyThreadSafetyMode.NONE) {
        state.timeLogs.groupBy { it.viewId }.mapValues { (_, logs) -> logs.groupBy { mondayOf(it.dateKey) } }
    }

    // The ledger and the reward walk ask about the same checklist and day many times over. Progress
    // is a pure answer for one evaluation, so each is worked out once. Keyed by the configuration's
    // identity: comparing a whole configuration on every lookup would cost what the cache saves.
    private val progressByChecklist = IdentityHashMap<ChecklistConfiguration, HashMap<LocalDate, ChecklistProgress>>()

    /** The progress of any checklist on [day], its week's records included. */
    fun progress(checklist: ChecklistConfiguration, day: LocalDate): ChecklistProgress =
        progressByChecklist.getOrPut(checklist, ::HashMap).getOrPut(day) {
            checklist.progressOn(day, ChecklistRecords { recordsByChecklist[checklist.id]?.get(it.toString()) ?: DayRecord() }, daysOff)
        }

    /** Entries for one configured questions tracker and record day. */
    fun questionsOn(viewId: String, day: LocalDate): List<QuestionEntry> =
        questionsByDay[viewId]?.get(day.toString()).orEmpty()

    /** Any activity entry, or a gym session with a logged set. */
    fun isActive(day: LocalDate): Boolean = day.toString().let { it in activityDays || it in workoutDays }

    /** The minutes the time view [viewId] holds in the week starting on [monday]; only recorded minutes count, as in [minutesInWeek]. */
    fun minutesInWeek(viewId: String, monday: LocalDate): Int = minutesInWeek(timeWeeks[viewId]?.get(monday).orEmpty(), viewId, monday)

    /** Whether Substances was logged on [day], with a use or with "none"; see [isSubstancesLoggedFor]. */
    fun isSubstancesLogged(day: LocalDate): Boolean = day.toString() in substanceDays

    /**
     * Whether [day] holds a record of the view [view] names: a measurement's, whatever its number
     * and however many, a questions view's entry, whatever it says, or a time view's log, whatever
     * minutes it holds.
     */
    fun isLogged(view: LoggedViewRef, day: LocalDate): Boolean = when (view.kind) {
        LoggedViewKind.Measurement -> measurementDays
        LoggedViewKind.Questions -> questionsDays
        LoggedViewKind.Time -> timeDays
    }[view.id]?.contains(day.toString()) == true

    /** Whether [day] holds a use of any substance in [among]; see [isSubstanceUsedOn]. */
    fun isSubstanceUsedOn(day: LocalDate, among: Set<String>): Boolean =
        day.toString().let { isSubstanceUsedOn(substanceUses[it].orEmpty(), it, among) }

    private fun mondayOf(dateKey: String): LocalDate? = parseDateKey(dateKey)?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
