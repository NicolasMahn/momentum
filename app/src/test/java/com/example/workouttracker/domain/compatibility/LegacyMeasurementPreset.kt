package com.example.workouttracker.domain.compatibility

import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.DefaultMeasurementStep
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementCheckpointId
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy
import com.example.workouttracker.domain.measurement.MeasurementRewards
import java.time.LocalDate

/** The ID Weight's configuration is stored under; its records and its reminder are Weight's own. */
const val WeightMeasurementId = "weight"

/**
 * The unit inherited installations used before measurements stored their own unit.
 */
const val WeightUnit = "kg"

/** Weight's bank since the token rules began: seven passes, one for a week with six logged days. */
val WeightRewards = MeasurementRewards(maxPasses = 7, keptDaysInWeek = 6)

/** Weight away as holidays always worked: there is no scale, so nothing is asked. */
val WeightWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Excused)

/** When Weight's reminder has always come: five minutes after waking. */
val WeightCheckpointTime: CheckpointTime = CheckpointTime.AfterWake(5)

/** Weight's checkpoint with its switch as [remind]: the one a state from before it was stored is read with, with the switch the reminder settings held. */
fun defaultWeightCheckpoint(remind: Boolean) = Checkpoint(id = MeasurementCheckpointId, at = WeightCheckpointTime, remind = remind)

/**
 * Weight as it has always been judged and shown, its reminder on as [remind] says. It is written
 * once, with [label] in the app language of that moment, when a state from before measurements
 * were stored is first read, and from then on it is stored. A new installation is not seeded
 * with it.
 */
fun defaultWeightMeasurement(label: String, remind: Boolean = false) = MeasurementConfiguration(
    id = WeightMeasurementId,
    label = label,
    icon = ViewIcon.Scale,
    hue = ViewHue.Sky,
    unit = WeightUnit,
    step = DefaultMeasurementStep,
    rewards = MeasurementBank.Week(WeightRewards),
    whenAway = WeightWhenAway,
    checkpoint = defaultWeightCheckpoint(remind),
    valuePolicy = MeasurementValuePolicy.Positive,
    mo = TrackerMoPolicy(enabled = true, motif = com.example.workouttracker.domain.checklist.MoMotif.Scale),
)

/** Weight's configuration, or null where the state holds none: one never loaded through the repository, or one Weight was deleted from. */
val AppState.weightMeasurement: MeasurementConfiguration? get() = measurements.firstOrNull { it.id == WeightMeasurementId }

/** The measurements a person made, in the order they were made: every one but Weight. */
val AppState.madeMeasurements: List<MeasurementConfiguration> get() = measurements.filter { it.id != WeightMeasurementId }

/** Weight's bank as the walk reads it; the default's in a state that was never loaded through the repository. */
val AppState.weightRewards: MeasurementRewards get() = weightMeasurement?.weekRewards ?: WeightRewards

/**
 * This week bank with its two numbers changed from [today] on, as Weight's page stored a change
 * while a week's logged days could still be set there. Nothing in the app makes this change any
 * more; a stored week bank may hold such dated numbers, and its weeks are still judged by them.
 */
fun MeasurementConfiguration.withWeekRule(today: LocalDate, maxPasses: Int, keptDaysInWeek: Int): MeasurementConfiguration {
    val bank = requireNotNull(weekRewards) { "Measurement $id has no week bank." }
    return copy(
        rewards = MeasurementBank.Week(
            bank.copy(maxPasses = bank.maxPasses.changedFrom(today, maxPasses), keptDaysInWeek = bank.keptDaysInWeek.changedFrom(today, keptDaysInWeek)),
        ),
    )
}

/** Weight's week rule changed from [today] on ([withWeekRule]). */
fun AppState.withWeightWeekRule(today: LocalDate, maxPasses: Int = 7, keptDaysInWeek: Int = 6): AppState =
    copy(measurements = measurements.map { if (it.id == WeightMeasurementId) it.withWeekRule(today, maxPasses, keptDaysInWeek) else it })

/** What an away day means for Weight, day by day; the default's in a state that was never loaded through the repository. */
val AppState.weightWhenAway: Dated<WhenAway> get() = weightMeasurement?.whenAway ?: WeightWhenAway

/** The two bank shapes have distinct historical wire contracts; their ownership stays here. */
internal fun requireCompatibleMeasurementBank(id: String, rewards: MeasurementBank?) {
    if (id == WeightMeasurementId) {
        require(rewards is MeasurementBank.Week) { "The inherited measurement has its week bank." }
    } else {
        require(rewards == null || (rewards is MeasurementBank.Standard && rewards.rewards.periods != null)) {
            "Measurement $id has the standard bank with its periods, or none."
        }
    }
}

/** The unchanged wire shape has no policy field; only the inherited ID recovers positivity. */
internal fun preservedMeasurementValuePolicy(id: String): MeasurementValuePolicy =
    if (id == WeightMeasurementId) MeasurementValuePolicy.Positive else MeasurementValuePolicy.Finite

internal fun requireCompatibleMeasurementValuePolicy(measurement: MeasurementConfiguration) {
    require(measurement.valuePolicy == preservedMeasurementValuePolicy(measurement.id)) {
        "Measurement ${measurement.id} has a value policy the current format cannot preserve."
    }
}
