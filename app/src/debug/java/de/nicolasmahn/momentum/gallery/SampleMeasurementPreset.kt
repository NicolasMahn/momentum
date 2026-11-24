package de.nicolasmahn.momentum.gallery

import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.measurement.DefaultMeasurementStep
import de.nicolasmahn.momentum.domain.measurement.MeasurementBank
import de.nicolasmahn.momentum.domain.measurement.MeasurementCheckpointId
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementValuePolicy
import de.nicolasmahn.momentum.domain.measurement.MeasurementRewards

const val DemoWeightId = "demo_weight"

const val WeightUnit = "kg"

val WeightRewards = MeasurementRewards(maxPasses = 7, keptDaysInWeek = 6)

val WeightWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Excused)

val WeightCheckpointTime: CheckpointTime = CheckpointTime.AfterWake(5)

fun defaultWeightCheckpoint(remind: Boolean) = Checkpoint(id = MeasurementCheckpointId, at = WeightCheckpointTime, remind = remind)

fun sampleWeightMeasurement(label: String, remind: Boolean = false) = MeasurementConfiguration(
    id = DemoWeightId,
    label = label,
    icon = ViewIcon.Scale,
    hue = ViewHue.Sky,
    unit = WeightUnit,
    step = DefaultMeasurementStep,
    rewards = MeasurementBank.Week(WeightRewards),
    whenAway = WeightWhenAway,
    checkpoint = defaultWeightCheckpoint(remind),
    valuePolicy = MeasurementValuePolicy.Positive,
    mo = TrackerMoPolicy(enabled = true, motif = de.nicolasmahn.momentum.domain.checklist.MoMotif.Scale),
)
