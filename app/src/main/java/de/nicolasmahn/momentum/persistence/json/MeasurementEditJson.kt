package de.nicolasmahn.momentum.persistence.json

import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.parseCheckpointTime
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import org.json.JSONArray
import org.json.JSONObject

/*
 * The edits a measurement's page has made but not saved, as the page keeps them across process
 * death. They are never stored with the records, so they have no version of their own: this reads
 * what the same build wrote, and anything that cannot be read exactly is refused. `bankRules`
 * changes a bank's two numbers.
 */

internal fun measurementEditsToJson(edits: List<MeasurementEdit>): JSONArray = JSONArray().apply { edits.forEach { put(it.encode()) } }

internal fun JSONArray.toMeasurementEdits(): List<MeasurementEdit> =
    (0 until length()).map { (get(it) as? JSONObject ?: error("An edit must be an object")).decodeMeasurementEdit() }

private fun MeasurementEdit.encode(): JSONObject = when (this) {
    is MeasurementEdit.Rename -> edit("rename").put("label", label)
    is MeasurementEdit.SetIcon -> edit("icon").put("icon", icon.storageId)
    is MeasurementEdit.SetHue -> edit("hue").put("hue", hue.storageId)
    is MeasurementEdit.SetUnit -> edit("unit").put("unit", unit)
    is MeasurementEdit.SetStep -> edit("step").put("step", step.written)
    is MeasurementEdit.SetTrendDays -> edit("trendDays").put("days", days)
    MeasurementEdit.JoinRewards -> edit("joinRewards")
    MeasurementEdit.LeaveRewards -> edit("leaveRewards")
    is MeasurementEdit.SetBankRules -> edit("bankRules").put("maxPasses", maxPasses).put("everyKeptDays", everyKeptDays)
    is MeasurementEdit.SetWhenAway -> edit("whenAway").put("whenAway", whenAway.storageId)
    is MeasurementEdit.SetRemind -> edit("remind").put("remind", remind)
    is MeasurementEdit.SetReminderTime -> edit("reminderTime").put("at", at.toString())
}

private fun JSONObject.decodeMeasurementEdit(): MeasurementEdit = when (val kind = text("edit")) {
    "rename" -> MeasurementEdit.Rename(text("label"))
    "icon" -> MeasurementEdit.SetIcon(ViewIcon.fromStorageId(text("icon")) ?: error("Unknown icon"))
    "hue" -> MeasurementEdit.SetHue(ViewHue.fromStorageId(text("hue")) ?: error("Unknown hue"))
    "unit" -> MeasurementEdit.SetUnit(text("unit"))
    "step" -> MeasurementEdit.SetStep(storedStep(get("step")))
    "trendDays" -> MeasurementEdit.SetTrendDays(wholeNumber(get("days")))
    "joinRewards" -> MeasurementEdit.JoinRewards
    "leaveRewards" -> MeasurementEdit.LeaveRewards
    "bankRules" -> MeasurementEdit.SetBankRules(wholeNumber(get("maxPasses")), wholeNumber(get("everyKeptDays")))
    "whenAway" -> MeasurementEdit.SetWhenAway(WhenAway.fromStorageId(text("whenAway")) ?: error("Unknown whenAway"))
    "remind" -> MeasurementEdit.SetRemind(get("remind") as? Boolean ?: error("remind must be a Boolean"))
    "reminderTime" -> MeasurementEdit.SetReminderTime(parseCheckpointTime(text("at")) ?: error("Invalid reminder time"))
    else -> error("Unknown edit $kind")
}
