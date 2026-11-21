package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.parseCheckpointTime
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.TokenStep
import com.example.workouttracker.domain.time.TokenSteps
import org.json.JSONArray
import org.json.JSONObject

/*
 * The edits a time view's page or setup has made but not saved, as it keeps them across process
 * death. They are never stored with the records, so they have no version of their own: what is
 * read is what the same build wrote, and anything that cannot be read exactly is refused.
 */

internal fun timeEditsToJson(edits: List<TimeEdit>): JSONArray = JSONArray().apply { edits.forEach { put(it.encode()) } }

internal fun JSONArray.toTimeEdits(): List<TimeEdit> = objects().map { it.decodeTimeEdit() }

private fun TimeEdit.encode(): JSONObject = when (this) {
    is TimeEdit.Rename -> edit("rename").put("label", label)
    is TimeEdit.SetIcon -> edit("icon").put("icon", icon.storageId)
    is TimeEdit.SetHue -> edit("hue").put("hue", hue.storageId)
    is TimeEdit.SetMotif -> edit("motif").putOptional("motif", motif?.storageId)
    is TimeEdit.SetGoal -> edit("goal").put("minutes", minutes)
    TimeEdit.JoinRewards -> edit("joinRewards")
    TimeEdit.LeaveRewards -> edit("leaveRewards")
    is TimeEdit.SetBankSize -> edit("bankSize").put("maxPasses", maxPasses)
    is TimeEdit.SetTokenCover -> edit("tokenCover").put("cover", cover.encode())
    is TimeEdit.SetTokenSteps -> edit("tokenSteps").put("steps", JSONArray().apply { steps.steps.forEach { put(JSONObject().put("percent", it.percent).put("tokens", it.tokens)) } })
    is TimeEdit.SetWhenAway -> edit("whenAway").put("whenAway", whenAway.storageId)
    is TimeEdit.SetRemind -> edit("remind").put("remind", remind)
    is TimeEdit.SetReminderTime -> edit("reminderTime").put("at", at.toString())
}

private fun JSONObject.decodeTimeEdit(): TimeEdit = when (val kind = text("edit")) {
    "rename" -> TimeEdit.Rename(text("label"))
    "icon" -> TimeEdit.SetIcon(ViewIcon.fromStorageId(text("icon")) ?: error("Unknown icon"))
    "hue" -> TimeEdit.SetHue(ViewHue.fromStorageId(text("hue")) ?: error("Unknown hue"))
    "motif" -> TimeEdit.SetMotif(if (has("motif")) MoMotif.fromStorageId(text("motif")) ?: error("Unknown motif") else null)
    "goal" -> TimeEdit.SetGoal(wholeNumber(get("minutes")))
    "joinRewards" -> TimeEdit.JoinRewards
    "leaveRewards" -> TimeEdit.LeaveRewards
    "bankSize" -> TimeEdit.SetBankSize(wholeNumber(get("maxPasses")))
    "tokenCover" -> TimeEdit.SetTokenCover(decodeTokenCover(get("cover")))
    "tokenSteps" -> TimeEdit.SetTokenSteps(TokenSteps(array("steps").objects().map { TokenStep(wholeNumber(it.get("percent")), wholeNumber(it.get("tokens"))) }))
    "whenAway" -> TimeEdit.SetWhenAway(WhenAway.fromStorageId(text("whenAway")) ?: error("Unknown whenAway"))
    "remind" -> TimeEdit.SetRemind(get("remind") as? Boolean ?: error("remind must be a Boolean"))
    "reminderTime" -> TimeEdit.SetReminderTime(parseCheckpointTime(text("at")) ?: error("Invalid reminder time"))
    else -> error("Unknown edit $kind")
}
