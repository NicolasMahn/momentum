package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.questions.Answer
import com.example.workouttracker.domain.questions.Question
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.QuestionEntryOrder
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import org.json.JSONArray
import org.json.JSONObject

internal fun encodeQuestions(questions: List<QuestionsConfiguration>) = JSONArray().apply {
    questions.forEach { put(it.encode()) }
}

internal fun JSONObject.decodeQuestions(): List<QuestionsConfiguration> =
    array("questions").objects().map { it.decodeQuestionsView() }

internal fun QuestionsConfiguration.encode(): JSONObject = JSONObject()
    .put("view", "questions")
    .put("id", id)
    .put("label", label)
    .put("icon", icon.storageId)
    .put("hue", hue.storageId)
    .put("mo", mo.encode())
    .put("questions", JSONArray().apply { questions.forEach { put(it.encode()) } })
    .put("about", about.encode { it.storageId })
    .putOptional("rewards", rewards?.encode())
    .put("whenAway", whenAway.encode { it.storageId })
    .put("checkpoints", JSONArray().put(checkpoint.encode()))
    .putOptional("madeOn", madeOn?.toString())

internal fun Question.encode(): JSONObject = JSONObject()
    .put("id", id)
    .put("text", text)
    .put("answers", JSONArray().apply { answers.forEach { put(it.encode()) } })
    .putOptional("help", help)

internal fun Answer.encode(): JSONObject = JSONObject().put("id", id).put("label", label)

internal fun JSONObject.decodeQuestionsView(): QuestionsConfiguration {
    requireFields(
        required = setOf("view", "id", "label", "icon", "hue", "mo", "questions", "about", "whenAway", "checkpoints"),
        optional = setOf("rewards", "madeOn"),
    )
    require(text("view") == "questions") { "Unsupported view kind" }
    return QuestionsConfiguration(
        id = text("id"),
        label = text("label"),
        icon = decodeIcon(),
        hue = ViewHue.fromStorageId(text("hue")) ?: error("Unknown hue"),
        mo = getJSONObject("mo").decodeMoPolicy(),
        questions = array("questions").objects().map { it.decodeQuestion() },
        about = decodeDated("about") { raw -> AboutDay.fromStorageId(raw as? String ?: error("about must be text")) ?: error("Unknown about") },
        rewards = if (has("rewards")) getJSONObject("rewards").decodeRewards() else null,
        whenAway = decodeDated("whenAway") { raw -> WhenAway.fromStorageId(raw as? String ?: error("whenAway must be text")) ?: error("Unknown whenAway") },
        checkpoint = array("checkpoints").objects().single().decodeCheckpoint(),
        madeOn = date("madeOn"),
    )
}

internal fun JSONObject.decodeQuestion(): Question {
    requireFields(required = setOf("id", "text", "answers"), optional = setOf("help"))
    return Question(
        id = text("id"),
        text = text("text"),
        answers = array("answers").objects().map { it.decodeAnswer() },
        help = if (has("help")) text("help") else null,
    )
}

internal fun JSONObject.decodeAnswer(): Answer {
    requireFields(required = setOf("id", "label"))
    return Answer(id = text("id"), label = text("label"))
}

internal fun encodeQuestionEntries(entries: List<QuestionEntry>) = JSONArray().apply {
    entries.forEach { put(it.encode()) }
}

internal fun QuestionEntry.encode(): JSONObject = JSONObject()
    .put("id", id)
    .put("questions", viewId)
    .put("dateKey", dateKey)
    .put("timestampMillis", timestampMillis)
    .put("answers", JSONObject().apply { answers.forEach { (questionId, answerId) -> put(questionId, answerId) } })
    .putOptional("note", note)

/**
 * The `questionEntries` list, every entry taken exactly as written: nothing is trimmed, coerced,
 * merged or left out, so a list that holds anything but entries cannot be read at all. They come
 * back in the order the state holds them ([QuestionEntryOrder]). What an entry may say is the
 * caller's to ask, with the configurations at hand.
 */
internal fun JSONObject.decodeQuestionEntries(): List<QuestionEntry> =
    array("questionEntries").objects().map { it.decodeQuestionEntry() }

internal fun JSONObject.decodeQuestionEntry(): QuestionEntry {
    requireFields(required = setOf("id", "questions", "dateKey", "timestampMillis", "answers"), optional = setOf("note"))
    val answers = get("answers") as? JSONObject ?: error("answers must be an object")
    return QuestionEntry(
        id = text("id"),
        viewId = text("questions"),
        dateKey = text("dateKey"),
        timestampMillis = optLongOrNull("timestampMillis") ?: error("timestampMillis must be a whole number"),
        answers = answers.keys().asSequence().associateWith { questionId -> answers.text(questionId) },
        note = if (has("note")) text("note") else null,
    )
}
