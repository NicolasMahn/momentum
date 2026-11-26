package de.nicolasmahn.momentum.testfixtures

import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.questions.QuestionScale
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration

object LegacyQuestionPreset {
    val qualityAnswerIds = listOf("poor", "fair", "good", "very_good", "excellent")
    val amountAnswerIds = listOf("much_less", "a_little_less", "about_the_same", "a_little_more", "much_more")
}

const val EatingQuestionsId = "eating"

const val EatingQualityQuestionId = "quality"

const val EatingAmountQuestionId = "amount"

val EatingScales: List<QuestionScale> = listOf(
    QuestionScale(EatingQualityQuestionId, LegacyQuestionPreset.qualityAnswerIds),
    QuestionScale(EatingAmountQuestionId, LegacyQuestionPreset.amountAnswerIds),
)

val EatingAbout: Dated<AboutDay> = Dated.always(AboutDay.Yesterday)

val EatingRewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7)

val EatingWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Due)

val EatingCheckpointTime: CheckpointTime = CheckpointTime.AfterWake(15)

fun defaultEatingCheckpoint(remind: Boolean) = Checkpoint(id = DayCheckpointId, at = EatingCheckpointTime, remind = remind)

fun defaultEatingQuestions(label: String, remind: Boolean = false) = QuestionsConfiguration(
    id = EatingQuestionsId,
    label = label,
    icon = ViewIcon.Food,
    hue = ViewHue.Orange,
    questions = listOf(
        de.nicolasmahn.momentum.domain.questions.Question("quality", "How did you eat?", LegacyQuestionPreset.qualityAnswerIds.map { de.nicolasmahn.momentum.domain.questions.Answer(it, it.replace('_', ' ')) }),
        de.nicolasmahn.momentum.domain.questions.Question("amount", "How much did you eat?", LegacyQuestionPreset.amountAnswerIds.map { de.nicolasmahn.momentum.domain.questions.Answer(it, it.replace('_', ' ')) }),
    ),
    about = EatingAbout,
    rewards = EatingRewards,
    whenAway = EatingWhenAway,
    checkpoint = defaultEatingCheckpoint(remind),
    madeOn = null,
    mo = TrackerMoPolicy(enabled = true, motif = de.nicolasmahn.momentum.domain.checklist.MoMotif.Food),
)
