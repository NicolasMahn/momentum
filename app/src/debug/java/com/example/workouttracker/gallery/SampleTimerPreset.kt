package com.example.workouttracker.gallery

import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.DayCheckpointId
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.DefaultWeeklyGoalMinutes
import com.example.workouttracker.domain.time.DefaultTimeBankSize
import java.time.LocalTime

const val DemoReadingId = "demo_reading"

val ReadingGoal: Dated<Int> = Dated.always(DefaultWeeklyGoalMinutes)

val ReadingRewards = TimeRewards(maxPasses = DefaultTimeBankSize)

val ReadingWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Due)

val ReadingCheckpointTime: CheckpointTime = CheckpointTime.Fixed(LocalTime.of(18, 0))

fun defaultReadingCheckpoint(remind: Boolean) = Checkpoint(id = DayCheckpointId, at = ReadingCheckpointTime, remind = remind)

fun sampleReadingTime(label: String, remind: Boolean = false) = TimeConfiguration(
    id = DemoReadingId,
    label = label,
    icon = ViewIcon.Book,
    hue = ViewHue.Yellow,
    mo = TrackerMoPolicy(enabled = true, motif = MoMotif.Book),
    goal = ReadingGoal,
    rewards = ReadingRewards,
    whenAway = ReadingWhenAway,
    checkpoint = defaultReadingCheckpoint(remind),
)
