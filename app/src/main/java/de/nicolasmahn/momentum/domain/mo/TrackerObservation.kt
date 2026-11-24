package de.nicolasmahn.momentum.domain.mo



import de.nicolasmahn.momentum.domain.DayPart
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.WholeDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Factual observations and the explicit policy under which Mo judges them. */
data class TrackerObservation(
    val ref: TrackerRef,
    val firstDay: LocalDate?,
    val recordedAt: Map<LocalDate, Long>,
    val target: ObservationTarget,
    val visibleOn: (LocalDate) -> Boolean,
    val countsOn: (LocalDate) -> Boolean,
    /**
     * The stored flag that enrolled a view with Mo before one rule decided it ([watchedBy]): on,
     * Mo watches the view whether or not it counts toward the streak, as he always did.
     */
    val participatesInMood: Boolean = false,
    val ignoreDaysOff: Boolean = false,
    val expectedInWeek: Int = 7,
    val weekProgress: ((LocalDate) -> MoWeekProgress)? = null,
    val partsOn: (LocalDate) -> List<DayPart> = { listOf(WholeDay) },
    val partKeptOn: (DayPart, LocalDate) -> Boolean = { _, day -> day in recordedAt },
    val owedPartsOn: ((LocalDate) -> List<DayPart>)? = null,
    /**
     * Whether Mo still asks for a day a free day covers. A plan can rest on a day, or its week can
     * already be done, and then an open day is nothing to wait for. A missed day is always asked.
     */
    val asksWhileFreeOn: (LocalDate) -> Boolean = { true },
    /** Whether the shared workout reminder asks for this task on the day, for a task with no checkpoint of its own. */
    val workoutReminderOn: (LocalDate) -> Boolean = { false },
    val partialActionRelief: Boolean = false,
    val label: String? = null,
    val content: MoContentKey,
    val checklist: ChecklistConfiguration? = null,
    val checkpoint: Checkpoint? = null,
    val order: Int = Int.MAX_VALUE,
    val progressPolicy: TrackerProgressPolicy = TrackerProgressPolicy.ParticipatingDay,
    val assumedOn: (LocalDate) -> Boolean = { false },
    /** Stable factual action keys let completion observers distinguish ticks from undoing a miss. */
    val recordedActionKeysOn: (LocalDate) -> Set<String> = { emptySet() },
    /** When present, only a newly recorded action that was owed may trigger a celebration. */
    val owedActionKeysOn: ((LocalDate) -> Set<String>)? = null,
    /** A Mo-enrolled tracker may announce its factual weekly goal when this policy opts in. */
    val celebratesWeeklyGoal: Boolean = false,
)

enum class TrackerProgressPolicy { RecordedParts, ParticipatingParts, ParticipatingDay }

sealed interface ObservationTarget {
    class Daily(
        val keptOn: (LocalDate) -> Boolean,
        val freeDaysOn: (day: LocalDate, firstWeek: Boolean) -> Int = { _, _ -> 0 },
        val spanStartOn: (LocalDate) -> LocalDate = { it.monday() },
        val excusedOn: (LocalDate) -> Boolean = { false },
        val asksNothingOn: (LocalDate) -> Boolean = { false },
    ) : ObservationTarget

    class WeeklyAmount(
        val amountIn: (monday: LocalDate) -> Int,
        val requiredIn: (monday: LocalDate) -> Int,
        val judgesOn: (sunday: LocalDate) -> Boolean,
    ) : ObservationTarget
}

/** Each observation is walked once; no identity or original-feature dispatch belongs here. */
internal fun TrackerObservation.statuses(today: LocalDate, daysOff: HolidaySchedule): Map<LocalDate, MoStatus> = buildMap {
    var firstWeek = true
    var spanStart: LocalDate? = null
    var freeDaysUsed = 0
    generateSequence(firstDay) { it.plusDays(1) }.takeWhile { it <= today }.forEach { day ->
        if (day.dayOfWeek == DayOfWeek.MONDAY) firstWeek = false
        when (val target = target) {
            is ObservationTarget.WeeklyAmount -> {
                if (day.dayOfWeek != DayOfWeek.SUNDAY || !countsOn(day) || !target.judgesOn(day)) return@forEach
                put(day, if (target.amountIn(day.minusDays(6)) >= target.requiredIn(day.minusDays(6))) MoStatus.Kept else MoStatus.Missed)
            }
            is ObservationTarget.Daily -> {
                val start = target.spanStartOn(day)
                if (start != spanStart) {
                    spanStart = start
                    freeDaysUsed = 0
                }
                if (!countsOn(day) || (!ignoreDaysOff && (daysOff.isSick(day) || target.excusedOn(day) || target.asksNothingOn(day)))) return@forEach
                val status = when {
                    target.keptOn(day) -> MoStatus.Kept
                    freeDaysUsed < target.freeDaysOn(day, firstWeek) -> MoStatus.Free
                    else -> MoStatus.Missed
                }
                if (status == MoStatus.Free) freeDaysUsed++
                put(day, status)
            }
        }
    }
}

internal fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
