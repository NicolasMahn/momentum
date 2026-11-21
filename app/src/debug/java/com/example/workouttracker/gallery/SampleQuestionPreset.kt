package com.example.workouttracker.gallery

import com.example.workouttracker.domain.mo.TrackerMoPolicy
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

object DemoQuestionAnswers {
    val qualityAnswerIds = listOf("poor", "fair", "good", "very_good", "excellent")
    val amountAnswerIds = listOf("much_less", "a_little_less", "about_the_same", "a_little_more", "much_more")
}

const val DemoEatingId = "demo_eating"

const val EatingQualityQuestionId = "quality"

const val EatingAmountQuestionId = "amount"

val EatingScales: List<QuestionScale> = listOf(
    QuestionScale(EatingQualityQuestionId, DemoQuestionAnswers.qualityAnswerIds),
    QuestionScale(EatingAmountQuestionId, DemoQuestionAnswers.amountAnswerIds),
)

val EatingAbout: Dated<AboutDay> = Dated.always(AboutDay.Yesterday)

val EatingRewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7)

val EatingWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Due)

val EatingCheckpointTime: CheckpointTime = CheckpointTime.AfterWake(15)

fun defaultEatingCheckpoint(remind: Boolean) = Checkpoint(id = DayCheckpointId, at = EatingCheckpointTime, remind = remind)

fun sampleEatingQuestions(label: String, remind: Boolean = false) = QuestionsConfiguration(
    id = DemoEatingId,
    label = label,
    icon = ViewIcon.Food,
    hue = ViewHue.Orange,
    questions = listOf(
        com.example.workouttracker.domain.questions.Question("quality", "How did you eat?", DemoQuestionAnswers.qualityAnswerIds.map { com.example.workouttracker.domain.questions.Answer(it, it.replace('_', ' ')) }, help = "Consider the whole day, including meals and snacks."),
        com.example.workouttracker.domain.questions.Question("amount", "How much did you eat?", DemoQuestionAnswers.amountAnswerIds.map { com.example.workouttracker.domain.questions.Answer(it, it.replace('_', ' ')) }),
    ),
    about = EatingAbout,
    rewards = EatingRewards,
    whenAway = EatingWhenAway,
    checkpoint = defaultEatingCheckpoint(remind),
    madeOn = null,
    mo = TrackerMoPolicy(enabled = true, motif = com.example.workouttracker.domain.checklist.MoMotif.Food),
)
