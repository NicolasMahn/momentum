package com.example.workouttracker.domain.questions


import com.example.workouttracker.domain.judgmentDayFor
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.StarterDayCheckpoint
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.freshId
import com.example.workouttracker.domain.checklist.joining
import com.example.workouttracker.domain.checklist.judgedBefore
import com.example.workouttracker.domain.checklist.leaving
import com.example.workouttracker.domain.checklist.starterHue
import com.example.workouttracker.domain.checklist.withRules
import java.time.LocalDate
import kotlin.random.Random
import com.example.workouttracker.core.time.parseDateKey

/**
 * Typed changes applied on their saving habit day. Prompt edits retain recorded answers;
 * reward and day-ownership revisions retain the rules that judged earlier days.
 */
sealed interface QuestionsEdit {
    data class Rename(val label: String) : QuestionsEdit

    data class SetIcon(val icon: ViewIcon) : QuestionsEdit

    data class SetHue(val hue: ViewHue) : QuestionsEdit

    /**
     * One question more, after the ones there are. Its ID and its answers' are ones the view never
     * had, an entry's included ([namedQuestionIds]), so nothing recorded for a removed question is
     * ever read as this one's.
     */
    data class AddQuestion(val question: Question) : QuestionsEdit

    data class RenameQuestion(val questionId: String, val text: String) : QuestionsEdit

    /** Null for a question without help. */
    data class SetQuestionHelp(val questionId: String, val help: String?) : QuestionsEdit

    /** A view keeps one question. */
    data class RemoveQuestion(val questionId: String) : QuestionsEdit

    /** One answer more, last on the question's scale; a question has at most five. */
    data class AddAnswer(val questionId: String, val answer: Answer) : QuestionsEdit

    data class RenameAnswer(val questionId: String, val answerId: String, val label: String) : QuestionsEdit

    /** A question keeps two answers. */
    data class RemoveAnswer(val questionId: String, val answerId: String) : QuestionsEdit

    /** Which day each due day asks about, from today on. */
    data class SetAbout(val about: AboutDay) : QuestionsEdit

    /** Counts toward the streak from today, with the rules it had or the ones every made view starts with. */
    data object JoinRewards : QuestionsEdit

    /** Stops counting after yesterday; the days it took part keep their judgments, and the bank its rules. */
    data object LeaveRewards : QuestionsEdit

    /** The bank's size and the kept days a pass takes, from today on. */
    data class SetRewardRules(val maxPasses: Int, val everyKeptDays: Int) : QuestionsEdit

    /** Excused or still due on an away day, from today on; a questions view has no count a week to lower. */
    data class SetWhenAway(val whenAway: WhenAway) : QuestionsEdit

    /** Whether this tracker reminds at its configured checkpoint. */
    data class SetRemind(val remind: Boolean) : QuestionsEdit

    /** When that reminder comes, in the three forms a checkpoint's time has. */
    data class SetReminderTime(val at: CheckpointTime) : QuestionsEdit
}

/**
 * Applies an edit from [today], rejecting invalid text, duplicate IDs and unavailable schema
 * edits. [firstRecordedDay] closes a bank's implicit historical participation when it leaves.
 */
fun QuestionsConfiguration.applyEdit(edit: QuestionsEdit, today: LocalDate, firstRecordedDay: LocalDate? = null): QuestionsConfiguration = when (edit) {
    is QuestionsEdit.Rename -> copy(label = edit.label)
    is QuestionsEdit.SetIcon -> copy(icon = edit.icon)
    is QuestionsEdit.SetHue -> copy(hue = edit.hue)
    is QuestionsEdit.AddQuestion -> copy(questions = questions + edit.question)
    is QuestionsEdit.RenameQuestion -> withQuestion(edit.questionId) { it.copy(text = edit.text) }
    is QuestionsEdit.SetQuestionHelp -> withQuestion(edit.questionId) { it.copy(help = edit.help) }
    is QuestionsEdit.RemoveQuestion -> {
        require(questions.any { it.id == edit.questionId }) { "No question ${edit.questionId}." }
        copy(questions = questions.filterNot { it.id == edit.questionId })
    }
    is QuestionsEdit.AddAnswer -> withQuestion(edit.questionId) { it.copy(answers = it.answers + edit.answer) }
    is QuestionsEdit.RenameAnswer -> withQuestion(edit.questionId) { question ->
        require(question.answers.any { it.id == edit.answerId }) { "No answer ${edit.answerId}." }
        question.copy(answers = question.answers.map { if (it.id == edit.answerId) it.copy(label = edit.label) else it })
    }
    is QuestionsEdit.RemoveAnswer -> withQuestion(edit.questionId) { question ->
        require(question.answers.any { it.id == edit.answerId }) { "No answer ${edit.answerId}." }
        question.copy(answers = question.answers.filterNot { it.id == edit.answerId })
    }
    // Facts and Mo retain their day ownership even when this tracker has no reward bank.
    is QuestionsEdit.SetAbout -> copy(about = about.changedFrom(today, edit.about, dated = mo.enabled || firstRecordedDay != null || rewards?.judgedBefore(today) == true))
    QuestionsEdit.JoinRewards -> copy(rewards = rewards.joining(today))
    QuestionsEdit.LeaveRewards -> copy(rewards = rewards?.leaving(today, wholeHistoryFrom = firstRecordedDay))
    is QuestionsEdit.SetRewardRules -> requireNotNull(rewards) { "Questions $id have no bank." }.let { bank ->
        copy(rewards = bank.withRules(today, edit.maxPasses, edit.everyKeptDays, dated = bank.judgedBefore(today)))
    }
    is QuestionsEdit.SetWhenAway -> copy(whenAway = whenAway.changedFrom(today, edit.whenAway))
    is QuestionsEdit.SetRemind -> copy(checkpoint = checkpoint.copy(remind = edit.remind))
    is QuestionsEdit.SetReminderTime -> copy(checkpoint = checkpoint.copy(at = edit.at))
}

private fun QuestionsConfiguration.withQuestion(questionId: String, change: (Question) -> Question): QuestionsConfiguration {
    require(questions.any { it.id == questionId }) { "No question $questionId." }
    return copy(questions = questions.map { if (it.id == questionId) change(it) else it })
}

/**
 * [edits] applied one after another from [today]: what the page shows over the latest stored
 * configuration, and what Save stores on the day it is pressed. An edit the configuration does not
 * take is left out, so what is shown is what is saved.
 */
fun QuestionsConfiguration.applyingEdits(edits: List<QuestionsEdit>, today: LocalDate, firstRecordedDay: LocalDate? = null): QuestionsConfiguration =
    edits.fold(this) { draft, edit -> runCatching { draft.applyEdit(edit, today, firstRecordedDay) }.getOrDefault(draft) }

/** The stored configuration [viewId] with [edits] applied from [today], leaving the streak from its first due day; a state without it is left as it is. */
fun AppState.applyingQuestionsEdits(viewId: String, edits: List<QuestionsEdit>, today: LocalDate): AppState {
    val current = questions.firstOrNull { it.id == viewId } ?: return this
    val updated = current.applyingEdits(edits, today, firstDueDayOf(current))
    return if (updated == current) this else copy(questions = questions.map { if (it.id == viewId) updated else it })
}

/** The earliest actual judgment of an existing record, using the same ownership lookup as runtime. */
fun AppState.firstDueDayOf(view: QuestionsConfiguration): LocalDate? =
    questionEntries.asSequence().filter { it.viewId == view.id }
        .mapNotNull { parseDateKey(it.dateKey)?.let(view::judgmentDayFor) }.minOrNull()

/**
 * Every ID a questions view is named by in this state: a configuration's, and a stored entry's,
 * whether a configuration knows its view or not. An entry of a view nothing knows is kept as it
 * is, so a new view under its ID would be handed answers that were never its own.
 */
val AppState.namedQuestionsIds: Set<String>
    get() = questions.mapTo(HashSet()) { it.id } + questionEntries.map { it.viewId }

/**
 * Every ID a question of the view [viewId] is named by: its configuration's questions, and what
 * its entries hold an answer for. A removed question's entries keep naming it, so its ID is never
 * given to a new one.
 */
fun AppState.namedQuestionIds(viewId: String): Set<String> =
    questions.filter { it.id == viewId }.flatMapTo(HashSet()) { view -> view.questions.map { it.id } } +
        questionEntries.filter { it.viewId == viewId }.flatMap { it.answers.keys }

/** Every ID an answer of the question [questionId] of [viewId] is named by: the question's answers, and what its entries hold for it. */
fun AppState.namedAnswerIds(viewId: String, questionId: String): Set<String> =
    questions.filter { it.id == viewId }.flatMapTo(HashSet()) { view -> view.questions.filter { it.id == questionId }.flatMap { question -> question.answers.map { it.id } } } +
        questionEntries.filter { it.viewId == viewId }.mapNotNull { it.answers[questionId] }

/** An ID for a questions view a person makes, such as `qst_k2p9`: one nothing names yet, the [hidden] entries of the visibility settings included. */
fun AppState.freshQuestionsId(hidden: Set<String> = emptySet(), random: Random = Random.Default): String =
    freshId(MadeQuestionsIdPrefix, namedQuestionsIds + hidden, random)

/**
 * A new questions view, as its setup made it. Throws for an ID that anything names by now: a
 * setup keeps the ID it was started with, so a creation that is retried or restored is refused
 * rather than made twice or given another ID.
 *
 * An entry among the [hidden] views of the visibility settings names an ID too. A delete can leave
 * one behind, and a view made under it would start out hidden.
 */
fun AppState.addQuestions(view: QuestionsConfiguration, hidden: Set<String> = emptySet()): AppState {
    require(view.id !in namedQuestionsIds && view.id !in hidden) { "Questions ${view.id} are named already." }
    return copy(questions = questions + view)
}

/**
 * A questions view as a person starts one on [today]: the journal, about today, outside the
 * streak, still due on holiday, its reminder off at 09:00, in the first starter color no
 * checklist, no measurement and no questions view wears ([starterHue]), asking [question] alone
 * ([starterQuestion]). Its ID is one nothing names yet ([freshQuestionsId]). It is a one-time
 * copy; everything else is set with [QuestionsEdit]s, and the store writes the day it is created
 * over [today].
 */
fun AppState.starterQuestions(
    label: String,
    question: Question,
    today: LocalDate,
    hidden: Set<String> = emptySet(),
    random: Random = Random.Default,
): QuestionsConfiguration = QuestionsConfiguration(
    id = freshQuestionsId(hidden, random),
    label = label,
    icon = ViewIcon.Journal,
    hue = starterHue(worn = checklists.mapTo(HashSet()) { it.hue } + measurements.map { it.hue } + questions.map { it.hue }),
    questions = listOf(question),
    about = Dated.always(AboutDay.Today),
    rewards = null,
    whenAway = MadeQuestionsWhenAway,
    checkpoint = StarterDayCheckpoint,
    madeOn = today,
)

/** A questions view a person made is still due on an away day until they say otherwise. */
val MadeQuestionsWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Due)

/**
 * A question as a person starts one: [text], and an answer for each of [answerLabels] in their
 * order, which are the person's own text from then on. Its ID is none of [takenQuestionIds], the
 * ones its view is named by and the ones its page has drawn since, and its answers' IDs are its
 * own, so nothing recorded for another question is ever read as its.
 */
fun starterQuestion(
    text: String,
    answerLabels: List<String>,
    takenQuestionIds: Collection<String> = emptySet(),
    random: Random = Random.Default,
): Question = Question(
    id = freshId(QuestionIdPrefix, takenQuestionIds, random),
    text = text,
    answers = answerLabels.fold(emptyList()) { answers, label -> answers + starterAnswer(label, answers.map { it.id }, random) },
)

/** One answer more for a question, under an ID that is none of [takenAnswerIds]: the ones its question is named by, and the ones its page has drawn since. */
fun starterAnswer(label: String, takenAnswerIds: Collection<String>, random: Random = Random.Default): Answer =
    Answer(id = freshId(AnswerIdPrefix, takenAnswerIds, random), label = label)

private const val QuestionIdPrefix = "qn"
private const val AnswerIdPrefix = "an"

/**
 * Removes a questions tracker with all its entries, including future-dated records. The caller
 * also removes its reminder and visibility entries.
 */
fun AppState.deleteQuestions(viewId: String): AppState {
    require(questions.any { it.id == viewId }) { "No questions $viewId." }
    return copy(
        questions = questions.filterNot { it.id == viewId },
        questionEntries = questionEntries.filterNot { it.viewId == viewId },
    )
}

/** How many days hold an entry of [viewId], named before it is deleted; a view has one entry a day. */
fun AppState.answeredDaysOf(viewId: String): Int = questionEntries.count { it.viewId == viewId }
