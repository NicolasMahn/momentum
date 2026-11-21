package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.parseCheckpointTime
import com.example.workouttracker.domain.questions.QuestionsEdit
import org.json.JSONArray
import org.json.JSONObject

/*
 * The edits a questions view's page or setup has made but not saved, as it keeps them across
 * process death. They are never stored with the records, so they have no version of their own:
 * what is read is what the same build wrote, and anything that cannot be read exactly is refused.
 */

internal fun questionsEditsToJson(edits: List<QuestionsEdit>): JSONArray = JSONArray().apply { edits.forEach { put(it.encode()) } }

internal fun JSONArray.toQuestionsEdits(): List<QuestionsEdit> = objects().map { it.decodeQuestionsEdit() }

private fun QuestionsEdit.encode(): JSONObject = when (this) {
    is QuestionsEdit.Rename -> edit("rename").put("label", label)
    is QuestionsEdit.SetIcon -> edit("icon").put("icon", icon.storageId)
    is QuestionsEdit.SetHue -> edit("hue").put("hue", hue.storageId)
    is QuestionsEdit.AddQuestion -> edit("addQuestion").put("question", question.encode())
    is QuestionsEdit.RenameQuestion -> edit("renameQuestion").put("question", questionId).put("text", text)
    is QuestionsEdit.SetQuestionHelp -> edit("questionHelp").put("question", questionId).putOptional("help", help)
    is QuestionsEdit.RemoveQuestion -> edit("removeQuestion").put("question", questionId)
    is QuestionsEdit.AddAnswer -> edit("addAnswer").put("question", questionId).put("answer", answer.encode())
    is QuestionsEdit.RenameAnswer -> edit("renameAnswer").put("question", questionId).put("answer", answerId).put("label", label)
    is QuestionsEdit.RemoveAnswer -> edit("removeAnswer").put("question", questionId).put("answer", answerId)
    is QuestionsEdit.SetAbout -> edit("about").put("about", about.storageId)
    QuestionsEdit.JoinRewards -> edit("joinRewards")
    QuestionsEdit.LeaveRewards -> edit("leaveRewards")
    is QuestionsEdit.SetRewardRules -> edit("rewardRules").put("maxPasses", maxPasses).put("everyKeptDays", everyKeptDays)
    is QuestionsEdit.SetWhenAway -> edit("whenAway").put("whenAway", whenAway.storageId)
    is QuestionsEdit.SetRemind -> edit("remind").put("remind", remind)
    is QuestionsEdit.SetReminderTime -> edit("reminderTime").put("at", at.toString())
}

private fun JSONObject.decodeQuestionsEdit(): QuestionsEdit = when (val kind = text("edit")) {
    "rename" -> QuestionsEdit.Rename(text("label"))
    "icon" -> QuestionsEdit.SetIcon(ViewIcon.fromStorageId(text("icon")) ?: error("Unknown icon"))
    "hue" -> QuestionsEdit.SetHue(ViewHue.fromStorageId(text("hue")) ?: error("Unknown hue"))
    "addQuestion" -> QuestionsEdit.AddQuestion(getJSONObject("question").decodeQuestion())
    "renameQuestion" -> QuestionsEdit.RenameQuestion(text("question"), text("text"))
    "questionHelp" -> QuestionsEdit.SetQuestionHelp(text("question"), if (has("help")) text("help") else null)
    "removeQuestion" -> QuestionsEdit.RemoveQuestion(text("question"))
    "addAnswer" -> QuestionsEdit.AddAnswer(text("question"), getJSONObject("answer").decodeAnswer())
    "renameAnswer" -> QuestionsEdit.RenameAnswer(text("question"), text("answer"), text("label"))
    "removeAnswer" -> QuestionsEdit.RemoveAnswer(text("question"), text("answer"))
    "about" -> QuestionsEdit.SetAbout(AboutDay.fromStorageId(text("about")) ?: error("Unknown about"))
    "joinRewards" -> QuestionsEdit.JoinRewards
    "leaveRewards" -> QuestionsEdit.LeaveRewards
    "rewardRules" -> QuestionsEdit.SetRewardRules(wholeNumber(get("maxPasses")), wholeNumber(get("everyKeptDays")))
    "whenAway" -> QuestionsEdit.SetWhenAway(WhenAway.fromStorageId(text("whenAway")) ?: error("Unknown whenAway"))
    "remind" -> QuestionsEdit.SetRemind(get("remind") as? Boolean ?: error("remind must be a Boolean"))
    "reminderTime" -> QuestionsEdit.SetReminderTime(parseCheckpointTime(text("at")) ?: error("Invalid reminder time"))
    else -> error("Unknown edit $kind")
}
