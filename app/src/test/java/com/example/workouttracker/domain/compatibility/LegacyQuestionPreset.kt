package com.example.workouttracker.domain.compatibility

import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.questions.Question
import com.example.workouttracker.domain.questions.Answer
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.DayCheckpointId
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.questions.QuestionScale
import com.example.workouttracker.domain.questions.QuestionsConfiguration

/** Historical answer IDs remain stable while current records use generic question answers. */
object LegacyQuestionPreset {
    val qualityAnswerIds = listOf("poor", "fair", "good", "very_good", "excellent")
    val amountAnswerIds = listOf("much_less", "a_little_less", "about_the_same", "a_little_more", "much_more")
}

/** The ID Eating's configuration is stored under; its entries name it. */
const val EatingQuestionsId = "eating"

/** The inherited nutrition preset's quality question ID. */
const val EatingQualityQuestionId = "quality"

/** The inherited nutrition preset's amount question ID. */
const val EatingAmountQuestionId = "amount"

/**
 * Eating's two questions, under the names its reflections always stored: quality from `poor` to
 * `excellent` and amount from `much_less` to `much_more`.
 */
val EatingScales: List<QuestionScale> = listOf(
    QuestionScale(EatingQualityQuestionId, LegacyQuestionPreset.qualityAnswerIds),
    QuestionScale(EatingAmountQuestionId, LegacyQuestionPreset.amountAnswerIds),
)

/** Eating asks about yesterday: the day reflected on is the one that just ended. */
val EatingAbout: Dated<AboutDay> = Dated.always(AboutDay.Yesterday)

/** Eating's bank since the token rules began: three passes, one for every seven kept days. */
val EatingRewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7)

/** Eating away as holidays always worked: still due. */
val EatingWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Due)

/** When Eating's reminder has always come: fifteen minutes after waking. */
val EatingCheckpointTime: CheckpointTime = CheckpointTime.AfterWake(15)

/** Eating's checkpoint with its switch as [remind]: the one a state from before it was stored is read with, with the switch the reminder settings held. */
fun defaultEatingCheckpoint(remind: Boolean) = Checkpoint(id = DayCheckpointId, at = EatingCheckpointTime, remind = remind)

/** A complete synthetic question tracker for tests that already name these stable record IDs. */
fun defaultEatingQuestions(label: String, remind: Boolean = false) = QuestionsConfiguration(
    id = EatingQuestionsId,
    label = label,
    icon = ViewIcon.Food,
    hue = ViewHue.Orange,
    questions = sampleQuestions(),
    about = EatingAbout,
    rewards = EatingRewards,
    whenAway = EatingWhenAway,
    checkpoint = defaultEatingCheckpoint(remind),
    madeOn = null,
    mo = TrackerMoPolicy(enabled = true, motif = MoMotif.Food),
)

/** Eating's configuration, or null where the state holds none: one never loaded through the repository, or one Eating was deleted from. */
val AppState.eatingQuestions: QuestionsConfiguration? get() = questions.firstOrNull { it.id == EatingQuestionsId }

/** The questions views a person made, in the order they were made: every one but Eating, which is built in. */
val AppState.madeQuestions: List<QuestionsConfiguration> get() = questions.filter { it.id != EatingQuestionsId }

/** Eating's bank as the walk reads it; the default's in a state that was never loaded through the repository. */
val AppState.eatingRewards: ChecklistRewards get() = eatingQuestions?.rewards ?: EatingRewards

/** What an away day means for Eating, day by day; the default's in a state that was never loaded through the repository. */
val AppState.eatingWhenAway: Dated<WhenAway> get() = eatingQuestions?.whenAway ?: EatingWhenAway

/** Synthetic stored English prompts used by tests, with stable answer IDs for their existing facts. */
private fun sampleQuestions(): List<Question> = listOf(
    Question(
        "quality", "How was the day?",
        LegacyQuestionPreset.qualityAnswerIds.mapIndexed { index, id -> Answer(id, "Level ${index + 1}") },
        "Think about the whole day.",
    ),
    Question(
        "amount", "How much did you do?",
        LegacyQuestionPreset.amountAnswerIds.mapIndexed { index, id -> Answer(id, "Level ${index + 1}") },
    ),
)
