package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.parseCheckpointTime
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceEdit
import com.example.workouttracker.domain.substance.SubstanceGoal
import org.json.JSONArray
import org.json.JSONObject

/*
 * The edits the Substances page has made but not saved, as the page keeps them across process
 * death. They are never stored with the records, so they have no version of their own: the same
 * build reads what it wrote, and anything it cannot read exactly is refused.
 */

internal fun substanceEditsToJson(edits: List<SubstanceEdit>): JSONArray = JSONArray().apply { edits.forEach { put(it.encode()) } }

internal fun JSONArray.toSubstanceEdits(): List<SubstanceEdit> =
    (0 until length()).map { (get(it) as? JSONObject ?: error("An edit must be an object")).decodeSubstanceEdit() }

private fun SubstanceEdit.encode(): JSONObject = when (this) {
    is SubstanceEdit.Rename -> edit("rename").putOptional("label", label)
    is SubstanceEdit.SetIcon -> edit("icon").put("icon", icon.storageId)
    is SubstanceEdit.SetHue -> edit("hue").put("hue", hue.storageId)
    is SubstanceEdit.SetMotif -> edit("motif").putOptional("motif", motif?.storageId)
    is SubstanceEdit.Track -> edit("track").put("substance", id).put("tracked", tracked)
    is SubstanceEdit.StayAway -> edit("stayAway").put("substance", id).put("stayingAway", stayingAway)
    is SubstanceEdit.AddOwn -> edit("addOwn").put("substance", id).put("label", label).put("unit", unit.storageId)
    is SubstanceEdit.RenameOwn -> edit("renameOwn").put("substance", id).put("label", label)
    is SubstanceEdit.RemoveOwn -> edit("removeOwn").put("substance", id)
    is SubstanceEdit.Remind -> edit("remind").put("remind", remind)
    is SubstanceEdit.CheckInAt -> edit("checkInAt").put("at", at.toString())
    is SubstanceEdit.SetGoal -> edit("goal").putOptional("goal", goal?.storageId)
    is SubstanceEdit.SetExceptionsOnly -> edit("exceptionsOnly").put("exceptionsOnly", exceptionsOnly)
    is SubstanceEdit.SetRewardRules -> edit("rewardRules").put("maxPasses", maxPasses).put("everyKeptDays", everyKeptDays)
    is SubstanceEdit.SetWhenAway -> edit("whenAway").put("whenAway", whenAway.storageId)
}

private fun JSONObject.decodeSubstanceEdit(): SubstanceEdit = when (val kind = word("edit")) {
    "rename" -> SubstanceEdit.Rename(if (has("label")) word("label") else null)
    "icon" -> SubstanceEdit.SetIcon(ViewIcon.fromStorageId(word("icon")) ?: error("Unknown icon"))
    "hue" -> SubstanceEdit.SetHue(ViewHue.fromStorageId(word("hue")) ?: error("Unknown hue"))
    "motif" -> SubstanceEdit.SetMotif(if (has("motif")) MoMotif.fromStorageId(word("motif")) ?: error("Unknown motif") else null)
    "track" -> SubstanceEdit.Track(word("substance"), flag("tracked"))
    "stayAway" -> SubstanceEdit.StayAway(word("substance"), flag("stayingAway"))
    "addOwn" -> SubstanceEdit.AddOwn(word("substance"), word("label"), SubstanceAmountUnit.fromStorageId(word("unit")) ?: error("Unknown substance unit"))
    "renameOwn" -> SubstanceEdit.RenameOwn(word("substance"), word("label"))
    "removeOwn" -> SubstanceEdit.RemoveOwn(word("substance"))
    "remind" -> SubstanceEdit.Remind(flag("remind"))
    "checkInAt" -> SubstanceEdit.CheckInAt(parseCheckpointTime(word("at")) ?: error("Invalid check-in time"))
    "goal" -> SubstanceEdit.SetGoal(if (has("goal")) SubstanceGoal.fromStorageId(word("goal")) ?: error("Unknown goal") else null)
    "exceptionsOnly" -> SubstanceEdit.SetExceptionsOnly(flag("exceptionsOnly"))
    "rewardRules" -> SubstanceEdit.SetRewardRules(number("maxPasses"), number("everyKeptDays"))
    "whenAway" -> SubstanceEdit.SetWhenAway(WhenAway.fromStorageId(word("whenAway")) ?: error("Unknown whenAway"))
    else -> error("Unknown edit $kind")
}

private fun JSONObject.word(name: String): String = get(name) as? String ?: error("$name must be text")

private fun JSONObject.flag(name: String): Boolean = get(name) as? Boolean ?: error("$name must be a Boolean")

private fun JSONObject.number(name: String): Int = get(name) as? Int ?: error("$name must be a whole number")
