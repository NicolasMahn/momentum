package de.nicolasmahn.momentum.domain.time

import de.nicolasmahn.momentum.domain.compatibility.madeTime

import de.nicolasmahn.momentum.domain.compatibility.defaultReadingTime

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
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
