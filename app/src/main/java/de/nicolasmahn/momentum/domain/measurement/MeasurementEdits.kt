package de.nicolasmahn.momentum.domain.measurement

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.freshId
import de.nicolasmahn.momentum.domain.checklist.joining
import de.nicolasmahn.momentum.domain.checklist.judgedBefore
import de.nicolasmahn.momentum.domain.checklist.leaving
import de.nicolasmahn.momentum.domain.checklist.starterHue
import java.time.LocalDate
import kotlin.random.Random
import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.checklist.joiningOn
import de.nicolasmahn.momentum.domain.checklist.leavingOn

/**
 * Typed changes applied on their saving habit day. Unit, step and trend edits change how values
 * are shown without converting records; reward and away revisions retain the rules that judged earlier days.
 */
sealed interface MeasurementEdit {
    data class Rename(val label: String) : MeasurementEdit

    data class SetIcon(val icon: ViewIcon) : MeasurementEdit

    data class SetHue(val hue: ViewHue) : MeasurementEdit

    /** What is shown after the number, or nothing. It relabels the numbers already recorded and converts none. */
    data class SetUnit(val unit: String) : MeasurementEdit

    /** What the − and + buttons add and take. */
    data class SetStep(val step: MeasurementStep) : MeasurementEdit

    /** How many days the chart's trend line follows. */
    data class SetTrendDays(val days: Int) : MeasurementEdit

    /** Counts toward the streak from today, retaining its configured bank policy. */
    data object JoinRewards : MeasurementEdit

    /** Stops counting after yesterday; the days it took part keep their judgments, and the bank its rules. */
    data object LeaveRewards : MeasurementEdit

    /**
     * The size of a measurement's bank and the kept days a pass takes, from today on. A bank
     * still earning by the week earns by kept days from today with it, and never by the week again.
     */
    data class SetBankRules(val maxPasses: Int, val everyKeptDays: Int) : MeasurementEdit

    /** Excused or still due on an away day, from today on; a measurement has no count a week to lower. */
    data class SetWhenAway(val whenAway: WhenAway) : MeasurementEdit

    /** Whether this tracker reminds at its configured checkpoint. */
    data class SetRemind(val remind: Boolean) : MeasurementEdit

    /** When that reminder comes, in the three forms a checkpoint's time has. */
    data class SetReminderTime(val at: CheckpointTime) : MeasurementEdit
}

/**
 * Applies an edit from [today], rejecting invalid values and operations for a different bank
 * policy. [firstRecordedDay] closes a bank's implicit historical participation when it leaves.
 */
fun MeasurementConfiguration.applyEdit(edit: MeasurementEdit, today: LocalDate, firstRecordedDay: LocalDate? = null): MeasurementConfiguration = when (edit) {
    is MeasurementEdit.Rename -> copy(label = edit.label)
    is MeasurementEdit.SetIcon -> copy(icon = edit.icon)
    is MeasurementEdit.SetHue -> copy(hue = edit.hue)
    is MeasurementEdit.SetUnit -> copy(unit = edit.unit)
    is MeasurementEdit.SetStep -> copy(step = edit.step)
    is MeasurementEdit.SetTrendDays -> copy(trendDays = edit.days)
    MeasurementEdit.JoinRewards -> copy(
        rewards = when (val bank = rewards) {
            is MeasurementBank.Week -> MeasurementBank.Week(bank.rewards.copy(periods = bank.rewards.periods.joiningOn(today)))
            is MeasurementBank.Standard -> bank.copy(rewards = bank.rewards.joining(today))
            null -> MeasurementBank.Standard(standardRewards.joining(today))
        },
    )
    MeasurementEdit.LeaveRewards -> copy(
        rewards = when (val bank = rewards) {
            is MeasurementBank.Week -> MeasurementBank.Week(bank.rewards.copy(periods = bank.rewards.periods.leavingOn(today, firstRecordedDay)))
            is MeasurementBank.Standard -> bank.copy(rewards = bank.rewards.leaving(today, firstRecordedDay))
            null -> null
        },
    )
    is MeasurementEdit.SetBankRules -> copy(
        rewards = when (val bank = requireNotNull(rewards) { "Measurement $id has no bank." }) {
            is MeasurementBank.Standard -> {
                val judgedBefore = bank.rewards.judgedBefore(today)
                // The days an earlier week rule reaches were not judged by the kept days a pass takes.
                val byKeptDaysBefore = judgedBefore && bank.earlierWeeks?.let { it.until < today.minusDays(1) } != false
                bank.copy(
                    rewards = bank.rewards.copy(
                        maxPasses = bank.rewards.maxPasses.changedFrom(today, edit.maxPasses, judgedBefore),
                        everyKeptDays = bank.rewards.everyKeptDays.changedFrom(today, edit.everyKeptDays, byKeptDaysBefore),
                    ),
                )
            }
            is MeasurementBank.Week -> bank.rewards.earningByKeptDays(today, edit.maxPasses, edit.everyKeptDays)
        },
    )
    is MeasurementEdit.SetWhenAway -> copy(whenAway = whenAway.changedFrom(today, edit.whenAway))
    is MeasurementEdit.SetRemind -> copy(checkpoint = checkpoint.copy(remind = edit.remind))
    is MeasurementEdit.SetReminderTime -> copy(checkpoint = checkpoint.copy(at = edit.at))
}

/**
 * [edits] applied one after another from [today]: what the page shows over the latest stored
 * configuration, and what Save stores on the day it is pressed. An edit the configuration does not
 * take is left out, so what is shown is what is saved.
 */
fun MeasurementConfiguration.applyingEdits(edits: List<MeasurementEdit>, today: LocalDate, firstRecordedDay: LocalDate? = null): MeasurementConfiguration =
    edits.fold(this) { draft, edit -> runCatching { draft.applyEdit(edit, today, firstRecordedDay) }.getOrDefault(draft) }

/** The stored configuration [measurementId] with [edits] applied from [today], leaving the streak from its first record; a state without it is left as it is. */
fun AppState.applyingMeasurementEdits(measurementId: String, edits: List<MeasurementEdit>, today: LocalDate): AppState {
    val current = measurements.firstOrNull { it.id == measurementId } ?: return this
    val updated = current.applyingEdits(edits, today, firstMeasuredDay(measurementId))
    return if (updated == current) this else copy(measurements = measurements.map { if (it.id == measurementId) updated else it })
}

/** The earliest recorded date, where a bank without explicit periods began. */
fun AppState.firstMeasuredDay(measurementId: String): LocalDate? =
    recordsOf(measurementId).mapNotNull { parseDateKey(it.dateKey) }.minOrNull()

/**
 * A measurement as a person starts one: the ruler, steps of 0.1, no unit, outside the streak, still
 * due on holiday, its reminder off at 09:00, in the first starter color no checklist and no
 * measurement wears ([starterHue]). Its ID is one nothing names yet: no configuration, no record
 * ([namedMeasurementIds]) and no entry among the [hidden] measurements of the visibility settings.
 * It is a one-time copy; everything else is set with [MeasurementEdit]s.
 */
fun AppState.starterMeasurement(label: String, hidden: Set<String> = emptySet(), random: Random = Random.Default): MeasurementConfiguration = MeasurementConfiguration(
    id = freshId("msr", namedMeasurementIds + hidden, random),
    label = label,
    icon = ViewIcon.Ruler,
    hue = starterHue(worn = checklists.mapTo(HashSet()) { it.hue } + measurements.map { it.hue }),
    unit = "",
    step = DefaultMeasurementStep,
    rewards = null,
    whenAway = MadeMeasurementWhenAway,
    checkpoint = DefaultMeasurementCheckpoint,
)

/**
 * Every ID a measurement is named by in this state: a configuration's, and a stored record's,
 * whether a configuration knows its measurement or not. A record of a measurement nothing knows is
 * kept as it is, so a new measurement under its ID would be handed numbers that were never its own.
 */
val AppState.namedMeasurementIds: Set<String>
    get() = measurements.mapTo(HashSet()) { it.id } + measurementLogs.map { it.measurementId }

/**
 * A new measurement, as the setup made it. Throws for an ID that anything names by now:
 * a setup keeps the ID it was started with, so a creation that is retried or restored is refused
 * rather than made twice or given another ID.
 *
 * An entry among the [hidden] measurements of the visibility settings names an ID too. A delete
 * can leave one behind, and a measurement made under it would start out hidden.
 */
fun AppState.addMeasurement(measurement: MeasurementConfiguration, hidden: Set<String> = emptySet()): AppState {
    require(measurement.id !in namedMeasurementIds && measurement.id !in hidden) { "Measurement ${measurement.id} is named already." }
    return copy(measurements = measurements + measurement)
}

/**
 * Removes a numerical tracker with all its records, including future-dated records. The caller
 * also removes its reminder and visibility entries.
 */
fun AppState.deleteMeasurement(measurementId: String): AppState {
    require(measurements.any { it.id == measurementId }) { "No measurement $measurementId." }
    return copy(
        measurements = measurements.filterNot { it.id == measurementId },
        measurementLogs = measurementLogs.filterNot { it.measurementId == measurementId },
    )
}

/** How many days hold a record of [measurementId], named before it is deleted; several records of one day are one day. */
fun AppState.recordedDaysOf(measurementId: String): Int =
    measurementLogs.asSequence().filter { it.measurementId == measurementId }.distinctBy { it.dateKey }.count()
