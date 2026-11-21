package com.example.workouttracker.testfixtures

import com.example.workouttracker.domain.mo.TrackerMoPolicy
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

const val WeightMeasurementId = "weight"

const val WeightUnit = "kg"

val WeightRewards = MeasurementRewards(maxPasses = 7, keptDaysInWeek = 6)

val WeightWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Excused)

val WeightCheckpointTime: CheckpointTime = CheckpointTime.AfterWake(5)

fun defaultWeightCheckpoint(remind: Boolean) = Checkpoint(id = MeasurementCheckpointId, at = WeightCheckpointTime, remind = remind)

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
