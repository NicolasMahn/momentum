package com.example.workouttracker.domain.questions


import java.time.Instant
import java.time.ZoneId
import com.example.workouttracker.core.time.requireValidDateKey
import com.example.workouttracker.domain.AppState

/**
 * One day's answers to one questions view, as the tracker state stores them: the day they are
 * about, when they were written, the answer given to each question, as question ID to answer ID,
 * and an optional note.
 *
 * A view has one entry per date. "Not sure" stores nothing, so a question without an answer here
 * reads as "Not sure", and an entry with no answer and no note is valid and keeps its day. An
 * answer naming a question or an answer its view no longer has is kept as it is: nothing shows or
 * counts it, and saving the day again leaves it until its question is given another answer
 * ([saveQuestionsDay]). An entry of a view no configuration knows is kept too, and read by nothing.
 */
data class QuestionEntry(
    val id: String,
    val viewId: String,
    val dateKey: String,
    val timestampMillis: Long,
    val answers: Map<String, String> = emptyMap(),
    val note: String? = null,
)

/** The most characters an entry's note may have. */
const val QuestionsNoteMaxLength = 240

/** Stored order: newest date and write first, then entry ID. */
val QuestionEntryOrder: Comparator<QuestionEntry> =
    compareByDescending<QuestionEntry> { it.dateKey }.thenByDescending { it.timestampMillis }.thenBy { it.id }

/**
 * What a stored state's entries must be. Each names its view, has a valid date, a time of writing
 * of zero or more, answers whose IDs are not blank and a note within its limit; no two share an
 * ID, and a view has one entry per date. A record may retain answers to prompts that its
 * configuration no longer asks.
 */
internal fun requireStorableQuestionEntries(entries: List<QuestionEntry>) {
    entries.forEach { entry ->
        require(entry.id.isNotBlank() && entry.viewId.isNotBlank()) { "An entry has an ID and names its view." }
        requireValidDateKey(entry.dateKey, subject = "The date of entry ${entry.id}")
        require(entry.timestampMillis >= 0) { "Entry ${entry.id} was written at a time before the epoch." }
        require(entry.answers.all { (questionId, answerId) -> questionId.isNotBlank() && answerId.isNotBlank() }) { "Entry ${entry.id} holds a blank answer." }
        require(entry.note.orEmpty().length <= QuestionsNoteMaxLength) { "The note of entry ${entry.id} has more than $QuestionsNoteMaxLength characters." }
    }
    require(entries.distinctBy { it.id }.size == entries.size) { "Two entries share an ID." }
    require(entries.distinctBy { it.viewId to it.dateKey }.size == entries.size) { "A questions view has two entries for one date." }
}

/** Whether one of these questions is [questionId] and offers [answerId], so its form can show that answer. */
private fun List<QuestionScale>.offer(questionId: String, answerId: String): Boolean =
    any { it.questionId == questionId && answerId in it.answerIds }

/** Whether every one of [answers] names a question of these and one of its answers. */
private fun List<QuestionScale>.answers(answers: Map<String, String>): Boolean =
    answers.all { (questionId, answerId) -> offer(questionId, answerId) }

/**
 * [entries] with the day [dateKey] of the view [viewId] answered at [timestampMillis], as its
 * screen saves it: one entry for that view and date, every other entry as it was. [answers] holds
 * the answer of each question that has one; a question of [asked] without one is "Not sure" and
 * stores nothing.
 *
 * A day saved again keeps its entry's ID and takes the new time of writing. It changes only what
 * the form could show: an answer the view still offers is replaced by [answers] or cleared. One the
 * form could not show, to a question removed since or removed from a question still asked, stays
 * until that question is given a new answer; the form showed "Not sure" over it, which is no
 * decision to drop it. A first save of a day is stored under [newEntryId].
 */
fun saveQuestionsDay(
    entries: List<QuestionEntry>,
    viewId: String,
    asked: List<QuestionScale>,
    dateKey: String,
    timestampMillis: Long,
    answers: Map<String, String>,
    note: String?,
    newEntryId: String,
): List<QuestionEntry> {
    require(asked.answers(answers)) { "An answer names a question and an answer the view has." }
    val existing = entries.firstOrNull { it.viewId == viewId && it.dateKey == dateKey }
    val saved = QuestionEntry(
        id = existing?.id ?: newEntryId,
        viewId = viewId,
        dateKey = dateKey,
        timestampMillis = timestampMillis,
        answers = existing?.answers.orEmpty().filterNot { (questionId, answerId) -> asked.offer(questionId, answerId) } + answers,
        note = note?.trim()?.ifBlank { null },
    )
    return (deleteQuestionsDay(entries, viewId, dateKey) + saved).sortedWith(QuestionEntryOrder).also(::requireStorableQuestionEntries)
}

/** [entries] without what the view [viewId] holds for [dateKey]; every other entry stays as it was. */
fun deleteQuestionsDay(entries: List<QuestionEntry>, viewId: String, dateKey: String): List<QuestionEntry> =
    entries.filterNot { it.viewId == viewId && it.dateKey == dateKey }

/**
 * The answer this entry holds on [scale], or null where the question was left as "Not sure". An
 * answer the question no longer has reads as none too: it stays stored, and nothing shows or
 * counts it.
 */
fun QuestionEntry.answerOn(scale: QuestionScale): String? = answers[scale.questionId]?.takeIf { it in scale.answerIds }

/** The entry the view [viewId] holds for [dateKey], or null on a day without one. */
fun AppState.questionsEntry(viewId: String, dateKey: String): QuestionEntry? =
    questionEntries.firstOrNull { it.viewId == viewId && it.dateKey == dateKey }

/** Whether this questions view has an entry for the requested record date. Answers may be absent. */
fun hasQuestionsEntryOn(entries: List<QuestionEntry>, viewId: String, recordDateKey: String): Boolean =
    entries.any { it.viewId == viewId && it.dateKey == recordDateKey }

/**
 * Whether an entry was saved by the end of its due habit day. The 04:00 boundary is inclusive,
 * matching historical reward decisions; a later entry remains factual data without keeping that day.
 */
fun areQuestionsLoggedOnTimeFor(
    entries: List<QuestionEntry>,
    viewId: String,
    recordDateKey: String,
    dueDateKey: String,
): Boolean {
    val cutoff = requireValidDateKey(dueDateKey).plusDays(1).atTime(4, 0)
    return entries.any { entry ->
        entry.viewId == viewId && entry.dateKey == recordDateKey &&
            !Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDateTime().isAfter(cutoff)
    }
}

/** The configured answer schema; records without a configuration supply no implied prompts. */
fun AppState.scalesOf(viewId: String): List<QuestionScale> = questions.firstOrNull { it.id == viewId }?.scales.orEmpty()
