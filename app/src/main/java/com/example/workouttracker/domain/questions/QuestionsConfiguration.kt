package com.example.workouttracker.domain.questions

import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.DailyView
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.DayCheckpointId
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.isTheDayCheckpoint
import com.example.workouttracker.domain.checklist.isTypedLine
import com.example.workouttracker.domain.checklist.recordDayFor
import com.example.workouttracker.domain.checklist.recordJudgedOn
import com.example.workouttracker.domain.checklist.requireId
import com.example.workouttracker.domain.checklist.requireLabel
import java.time.LocalDate

/**
 * A tracker that asks ordered questions about a configured day. Answers are recorded facts;
 * ordering controls presentation and never makes an answer earn a different reward.
 */
data class QuestionsConfiguration(
    override val id: String,
    override val label: String,
    override val icon: ViewIcon,
    override val hue: ViewHue,
    /** The complete editable prompts, answer labels and optional help shown for this tracker. */
    val questions: List<Question>,
    /**
     * Which day a due day asks about ([recordDayFor]): the day itself, or the day before for
     * questions answered the morning after. An entry belongs to the day it is about either way.
     */
    val about: Dated<AboutDay>,
    /** Reward rules and dated participation periods, or null while no protection is earned. */
    val rewards: ChecklistRewards?,
    /** What an away day means, due or excused; a questions view has no count a week to lower. */
    override val whenAway: Dated<WhenAway>,
    /** The one reminder of the view, as the checkpoint [DayCheckpointId]: whether it reminds, and when. */
    override val checkpoint: Checkpoint,
    /**
     * The tracker's creation habit day. A day before it is never asked about, so a view about
     * yesterday that is made today does not owe yesterday ([recordDayFor]). Null when the
     * configuration's history begins with its first recorded entry.
     */
    val madeOn: LocalDate?,
    val mo: TrackerMoPolicy = TrackerMoPolicy(),
) : DailyView {
    init {
        requireId(id)
        requireLabel(label)
        require(whenAway.revisions.none { it.value == WhenAway.LowerTarget }) { "Questions $id have no count a week to lower." }
        require(questions.size in QuestionsPerView) { "Questions $id ask one to five questions." }
        require(questions.distinctBy { it.id }.size == questions.size) { "Questions $id repeat a prompt ID." }
        require(checkpoint.isTheDayCheckpoint) { "Questions $id have the one checkpoint $DayCheckpointId, with no name and no dates." }
    }

    /**
     * What the view asks, as its records and its colors read it: each question's ID with its
     * answers' IDs in order.
     */
    val scales: List<QuestionScale>
        get() = questions.map { question -> QuestionScale(question.id, question.answers.map { it.id }) }

    override val kind: LoggedViewKind get() = LoggedViewKind.Questions

    /** The bank earned by completing this tracker's daily task. */
    override val standardRewards: ChecklistRewards? get() = rewards

    /**
     * The day whose entry due day [dueDay] asks for, by the rule of the due day: the day itself, or
     * the day before for a view about yesterday; null when that day is before the view was made, so
     * nothing is asked. Everything that asks about a due day reads the entry through this one place.
     */
    override fun recordDayFor(dueDay: LocalDate): LocalDate? = about.recordDayFor(dueDay).takeUnless(::isBeforeItWasMade)

    /**
     * [recordDayFor], or null on the day the view switches to being about yesterday, whose entry the
     * day before already asked for: an entry is asked for once, as a look-back checklist's record is.
     */
    override fun recordJudgedOn(dueDay: LocalDate): LocalDate? = about.recordJudgedOn(dueDay)?.takeUnless(::isBeforeItWasMade)

    private fun isBeforeItWasMade(day: LocalDate): Boolean = madeOn != null && day < madeOn
}

/**
 * One configured prompt with ordered answers and optional help text.
 * The order of the answers is the scale: first to last is "less" to "more". It decides the color
 * of an answer and nothing else; no answer is good or bad to the app.
 */
data class Question(
    val id: String,
    val text: String,
    val answers: List<Answer>,
    /** Shown behind the question's help button; null for a question without one. */
    val help: String? = null,
) {
    init {
        requireId(id)
        require(isValidQuestionText(text)) { "Question $id has a text that cannot be stored." }
        require(answers.size in AnswersPerQuestion) { "Question $id has two to five answers." }
        require(answers.distinctBy { it.id }.size == answers.size) { "Question $id has two answers with one ID." }
        require(help == null || isValidQuestionHelp(help)) { "Question $id has a help text that cannot be stored." }
    }
}

/** One answer a question can be given. */
data class Answer(val id: String, val label: String) {
    init {
        requireId(id)
        require(isValidAnswerLabel(label)) { "Answer $id has a label that cannot be stored." }
    }
}

/**
 * A question as records and colors read it: its ID and its answers' IDs, first to last "less" to
 * "more". Everything that reads an entry asks its configuration for these stable IDs,
 * independently of translated or user-entered prompt text.
 */
data class QuestionScale(val questionId: String, val answerIds: List<String>)

/** Allowed number of configured prompts. */
val QuestionsPerView = 1..5

/** How many answers a question offers: five is every step a color ramp has. */
val AnswersPerQuestion = 2..5

/** The most Unicode code points a question's text may have. */
const val MaximumQuestionTextLength = 120

/** The most Unicode code points an answer's label may have. */
const val MaximumAnswerLabelLength = 24

/** The most Unicode code points a question's help text may have. */
const val MaximumQuestionHelpLength = 500

/**
 * Whether [text] can be a question's text: one line as typed ([isTypedLine]) of 1 to
 * [MaximumQuestionTextLength] code points. The one rule for it, asked by a draft before it can be
 * saved, by the question itself and so by the decoder.
 */
fun isValidQuestionText(text: String): Boolean = isTypedLine(text, 1..MaximumQuestionTextLength)

/** Whether [label] can be an answer's label: one line as typed of 1 to [MaximumAnswerLabelLength] code points. */
fun isValidAnswerLabel(label: String): Boolean = isTypedLine(label, 1..MaximumAnswerLabelLength)

/**
 * Whether [help] can be a question's help text: no space around it and 1 to
 * [MaximumQuestionHelpLength] code points. It may hold line breaks. A question without help holds
 * none at all rather than an empty one.
 */
fun isValidQuestionHelp(help: String): Boolean = help == help.trim() && help.codePointCount(0, help.length) in 1..MaximumQuestionHelpLength

/**
 * Configured questions views each have a stable ID of their own, with entries following
 * [requireStorableQuestionEntries]. The decoder and the store's write guard both ask this, so a
 * state that could not be read back is never written. Everything one configuration must be, it
 * refuses itself.
 */
fun requireStorableQuestions(questions: List<QuestionsConfiguration>, entries: List<QuestionEntry>) {
    require(questions.distinctBy { it.id }.size == questions.size) { "Two questions views share an ID." }
    requireStorableQuestionEntries(entries)
}

/** Prefix of generated question-tracker IDs, such as `qst_k2p9`. */
const val MadeQuestionsIdPrefix = "qst"

internal val MadeQuestionsId = Regex("${MadeQuestionsIdPrefix}_[a-z0-9]{4}")
