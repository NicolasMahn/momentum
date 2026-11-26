package de.nicolasmahn.momentum.testfixtures

import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.TimeRewards
import de.nicolasmahn.momentum.domain.time.DefaultWeeklyGoalMinutes
import de.nicolasmahn.momentum.domain.time.DefaultTimeBankSize
import java.time.LocalTime

const val ReadingTimeId = "reading"

val ReadingGoal: Dated<Int> = Dated.always(DefaultWeeklyGoalMinutes)

val ReadingRewards = TimeRewards(maxPasses = DefaultTimeBankSize)

val ReadingWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Due)

val ReadingCheckpointTime: CheckpointTime = CheckpointTime.Fixed(LocalTime.of(18, 0))

fun defaultReadingCheckpoint(remind: Boolean) = Checkpoint(id = DayCheckpointId, at = ReadingCheckpointTime, remind = remind)

fun defaultReadingTime(label: String, remind: Boolean = false) = TimeConfiguration(
    id = ReadingTimeId,
    label = label,
    icon = ViewIcon.Book,
    hue = ViewHue.Yellow,
    mo = TrackerMoPolicy(enabled = true, motif = MoMotif.Book),
    goal = ReadingGoal,
    rewards = ReadingRewards,
    whenAway = ReadingWhenAway,
    checkpoint = defaultReadingCheckpoint(remind),
)
