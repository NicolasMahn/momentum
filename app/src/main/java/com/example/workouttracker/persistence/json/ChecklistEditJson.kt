package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.parseCheckpointTime
import java.time.Month
import org.json.JSONArray
import org.json.JSONObject

/*
 * The edits a checklist's page has made but not saved, as the page keeps them across process
 * death. They are never stored with the records, so they have no version of their own: the same
 * build reads what it wrote, and anything it cannot read exactly is refused.
 */

internal fun editsToJson(edits: List<ChecklistEdit>): JSONArray = JSONArray().apply { edits.forEach { put(it.encode()) } }

internal fun JSONArray.toChecklistEdits(): List<ChecklistEdit> =
    (0 until length()).map { (get(it) as? JSONObject ?: error("An edit must be an object")).decodeEdit() }

private fun ChecklistEdit.encode(): JSONObject = when (this) {
    is ChecklistEdit.Rename -> edit("rename").put("label", label)
    is ChecklistEdit.SetIcon -> edit("icon").put("icon", icon.storageId)
    is ChecklistEdit.SetHue -> edit("hue").put("hue", hue.storageId)
    is ChecklistEdit.SetMotif -> edit("motif").putOptional("motif", motif?.storageId)
    is ChecklistEdit.AddCheckpoint -> edit("addCheckpoint").put("checkpoint", checkpointId).put("label", label).put("at", at.toString()).put("remind", remind)
    is ChecklistEdit.RenameCheckpoint -> edit("renameCheckpoint").put("checkpoint", checkpointId).putOptional("label", label)
    is ChecklistEdit.SetCheckpointTime -> edit("checkpointTime").put("checkpoint", checkpointId).put("at", at.toString())
    is ChecklistEdit.SetCheckpointRemind -> edit("checkpointRemind").put("checkpoint", checkpointId).put("remind", remind)
    is ChecklistEdit.RemoveCheckpoint -> edit("removeCheckpoint").put("checkpoint", checkpointId)
    is ChecklistEdit.AddItem -> edit("addItem").put("item", itemId).put("label", label)
        .putOptional("checkpoints", checkpoints?.let { JSONArray(it) })
        .putOptional("target", target?.encode())
        .putOptional("months", months?.encode())
    is ChecklistEdit.RenameItem -> edit("renameItem").put("item", itemId).put("label", label)
    is ChecklistEdit.SetItemCheckpoints -> edit("itemCheckpoints").put("item", itemId).putOptional("checkpoints", checkpoints?.let { JSONArray(it) })
    is ChecklistEdit.SetItemTarget -> edit("itemTarget").put("item", itemId).putOptional("target", target?.encode())
    is ChecklistEdit.SetItemSeason -> edit("itemSeason").put("item", itemId).putOptional("months", months?.encode())
    is ChecklistEdit.RemoveItem -> edit("removeItem").put("item", itemId)
    is ChecklistEdit.SetItemWhenAway -> edit("itemWhenAway").put("item", itemId).put("whenAway", whenAway.storageId)
    is ChecklistEdit.SetItemAwayTimes -> edit("itemAwayTimes").put("item", itemId).putOptional("times", times)
    is ChecklistEdit.SetItemExceptionsOnly -> edit("itemExceptionsOnly").put("item", itemId).put("exceptionsOnly", exceptionsOnly)
    is ChecklistEdit.SetAbout -> edit("about").put("about", about.storageId)
    ChecklistEdit.JoinRewards -> edit("joinRewards")
    ChecklistEdit.LeaveRewards -> edit("leaveRewards")
    is ChecklistEdit.SetRewardRules -> edit("rewardRules").put("maxPasses", maxPasses).put("everyKeptDays", everyKeptDays)
}

private fun Set<Month>.encode(): JSONArray = JSONArray().apply { forEach { put(it.value) } }

private fun JSONObject.decodeEdit(): ChecklistEdit = when (val kind = word("edit")) {
    "rename" -> ChecklistEdit.Rename(word("label"))
    "icon" -> ChecklistEdit.SetIcon(ViewIcon.fromStorageId(word("icon")) ?: error("Unknown icon"))
    "hue" -> ChecklistEdit.SetHue(ViewHue.fromStorageId(word("hue")) ?: error("Unknown hue"))
    "motif" -> ChecklistEdit.SetMotif(if (has("motif")) MoMotif.fromStorageId(word("motif")) ?: error("Unknown motif") else null)
    "addCheckpoint" -> ChecklistEdit.AddCheckpoint(word("checkpoint"), word("label"), time("at"), flag("remind"))
    "renameCheckpoint" -> ChecklistEdit.RenameCheckpoint(word("checkpoint"), if (has("label")) word("label") else null)
    "checkpointTime" -> ChecklistEdit.SetCheckpointTime(word("checkpoint"), time("at"))
    "checkpointRemind" -> ChecklistEdit.SetCheckpointRemind(word("checkpoint"), flag("remind"))
    "removeCheckpoint" -> ChecklistEdit.RemoveCheckpoint(word("checkpoint"))
    "addItem" -> ChecklistEdit.AddItem(word("item"), word("label"), names("checkpoints"), target(), months())
    "renameItem" -> ChecklistEdit.RenameItem(word("item"), word("label"))
    "itemCheckpoints" -> ChecklistEdit.SetItemCheckpoints(word("item"), names("checkpoints"))
    "itemTarget" -> ChecklistEdit.SetItemTarget(word("item"), target())
    "itemSeason" -> ChecklistEdit.SetItemSeason(word("item"), months())
    "removeItem" -> ChecklistEdit.RemoveItem(word("item"))
    "itemWhenAway" -> ChecklistEdit.SetItemWhenAway(word("item"), WhenAway.fromStorageId(word("whenAway")) ?: error("Unknown whenAway"))
    "itemAwayTimes" -> ChecklistEdit.SetItemAwayTimes(word("item"), if (has("times")) number("times") else null)
    "itemExceptionsOnly" -> ChecklistEdit.SetItemExceptionsOnly(word("item"), flag("exceptionsOnly"))
    "about" -> ChecklistEdit.SetAbout(AboutDay.fromStorageId(word("about")) ?: error("Unknown about"))
    "joinRewards" -> ChecklistEdit.JoinRewards
    "leaveRewards" -> ChecklistEdit.LeaveRewards
    "rewardRules" -> ChecklistEdit.SetRewardRules(number("maxPasses"), number("everyKeptDays"))
    else -> error("Unknown edit $kind")
}

private fun JSONObject.word(name: String): String = get(name) as? String ?: error("$name must be text")

private fun JSONObject.flag(name: String): Boolean = get(name) as? Boolean ?: error("$name must be a Boolean")

private fun JSONObject.number(name: String): Int = get(name) as? Int ?: error("$name must be a whole number")

private fun JSONObject.time(name: String): CheckpointTime = parseCheckpointTime(word(name)) ?: error("Invalid checkpoint time")

private fun JSONObject.names(name: String): List<String>? = if (has(name)) {
    val raw = get(name) as? JSONArray ?: error("$name must be an array")
    (0 until raw.length()).map { raw.get(it) as? String ?: error("A name must be text") }
} else {
    null
}

private fun JSONObject.target(): ChecklistTarget? =
    if (has("target")) getJSONObject("target").decodeTarget() else null

private fun JSONObject.months(): Set<Month>? = if (has("months")) {
    val raw = get("months") as? JSONArray ?: error("months must be an array")
    (0 until raw.length()).mapTo(LinkedHashSet()) { Month.of(raw.get(it) as? Int ?: error("A month must be a number")) }
} else {
    null
}
