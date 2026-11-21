package com.example.workouttracker.domain.time

import com.example.workouttracker.domain.compatibility.madeTime

import com.example.workouttracker.domain.compatibility.defaultReadingTime

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.DayCheckpointId
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import java.time.LocalTime

/** Reading's configuration as a new English installation is seeded with it. */
internal fun testReadingTime() = defaultReadingTime(label = "Reading")

/** [this] with Reading configured, as every loaded state is, before any view a person made. */
internal fun AppState.withReading(configuration: TimeConfiguration = testReadingTime()): AppState =
    copy(time = listOf(configuration) + madeTime)

/** A time view a person made, as it starts: an hour a week, outside the streak, still due on holiday, its reminder off at 09:00. */
internal fun testMadeTime(
    id: String = "tim_k2p9",
    label: String = "Piano",
    goal: Int = DefaultWeeklyGoalMinutes,
    rewards: TimeRewards? = null,
) = TimeConfiguration(
    id = id,
    label = label,
    icon = ViewIcon.Music,
    hue = ViewHue.Navy,
    goal = Dated.always(goal),
    rewards = rewards,
    whenAway = Dated.always(WhenAway.Due),
    checkpoint = Checkpoint(id = DayCheckpointId, at = CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false),
)
