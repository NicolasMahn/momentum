package com.example.workouttracker.persistence.json

import com.example.workouttracker.core.time.isValidDateKey
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.parseCheckpointTime
import com.example.workouttracker.domain.substance.OwnSubstance
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceCheckIn
import com.example.workouttracker.domain.substance.SubstanceGoal
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.substance.SubstanceNoneDay
import com.example.workouttracker.domain.substance.SubstanceSettings
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.decodeSubstanceSettings(): SubstanceSettings {
    requireFields(
        required = setOf("mo", "tracked", "own", "stayingAway", "checkIn", "rewards", "whenAway", "goal", "exceptionsOnly"),
        optional = setOf(SubstancesLabelField, SubstancesIconField, SubstancesHueField),
    )
    val checkIn = getJSONObject("checkIn").apply { requireFields(required = setOf("remind", "at")) }
    return SubstanceSettings(
        mo = getJSONObject("mo").decodeMoPolicy(),
        tracked = array("tracked").ids("A substance is tracked once"),
        own = array("own").objects().map { it.decodeOwnSubstance() },
        stayingAway = decodeStance("stayingAway"),
        checkIn = SubstanceCheckIn(
            remind = checkIn.get("remind") as? Boolean ?: error("remind must be a Boolean"),
            at = parseCheckpointTime(checkIn.text("at")) ?: error("Unknown check-in time"),
        ),
        rewards = if (isNull("rewards")) null else getJSONObject("rewards").decodeRewards(),
        whenAway = decodeDated("whenAway") { raw -> WhenAway.fromStorageId(raw as? String ?: error("whenAway must be text")) ?: error("Unknown whenAway") },
        goal = decodeDated("goal") { raw -> SubstanceGoal.fromStorageId(raw as? String ?: error("goal must be text")) ?: error("Unknown goal") },
        exceptionsOnly = decodeDated("exceptionsOnly") { raw -> raw as? Boolean ?: error("exceptionsOnly must be a Boolean") },
        label = optionalFact(SubstancesLabelField) { it as? String ?: error("label must be text") },
        icon = setting(SubstancesIconField, SubstancesIconWhenAbsent, ::word).let { ViewIcon.fromStorageId(it) ?: error("Unknown icon") },
        hue = setting(SubstancesHueField, SubstancesHueWhenAbsent, ::word).let { ViewHue.fromStorageId(it) ?: error("Unknown hue") },
    )
}

private fun word(raw: Any): String = raw as? String ?: error("Expected text")

private const val StanceOnce = "A substance is stayed away from once"

/** The IDs in stored order; a repeated one would be lost on the way back, so it is refused. */
private fun JSONArray.ids(repeated: String): Set<String> {
    val ids = (0 until length()).map { get(it) as? String ?: error("A substance is named by its ID") }
    return LinkedHashSet(ids).also { require(it.size == ids.size) { repeated } }
}

/**
 * The stance as its revisions, always an array of objects, the first without `from`:
 * `[ { "ids": ["alcohol", "cannabis"] }, { "from": "2026-10-07", "ids": ["cannabis"] } ]`. A set
 * never takes the plain form the shared dated codec writes for an unchanged value, so an empty
 * set and a set that never changed are both one revision object, and `ids` carries the set where
 * the shared codec expects `value`.
 */
private fun JSONObject.decodeStance(name: String): Dated<Set<String>> = Dated(
    array(name).objects().map { revision ->
        revision.requireFields(required = setOf("ids"), optional = setOf("from"))
        Dated.Revision(revision.array("ids").ids(StanceOnce), revision.date("from"))
    },
)

/** [order] puts each revision's IDs in catalogue order with the person's own after, so the same stance always reads the same. */
private fun Dated<Set<String>>.encodeStance(order: (Set<String>) -> List<String>): JSONArray = JSONArray().apply {
    revisions.forEach { revision ->
        put(JSONObject().putOptional("from", revision.from?.toString()).put("ids", JSONArray(order(revision.value))))
    }
}

private fun JSONObject.decodeOwnSubstance(): OwnSubstance {
    requireFields(required = setOf("id", "label", "unit"))
    return OwnSubstance(
        id = text("id"),
        label = text("label"),
        unit = SubstanceAmountUnit.fromStorageId(text("unit")) ?: error("Unknown substance unit"),
    )
}

/** The IDs in catalogue order with the person's own after, so the same choice always reads the same. */
internal fun encodeSubstanceSettings(settings: SubstanceSettings): JSONObject = JSONObject()
    .put("mo", settings.mo.encode())
    .put("tracked", JSONArray(settings.trackedSubstances.map { it.id }))
    .put(
        "own",
        JSONArray().apply {
            settings.own.forEach { own ->
                put(
                    JSONObject()
                        .put("id", own.id)
                        .put("label", own.label)
                        .put("unit", own.unit.storageId),
                )
            }
        },
    )
    .put("stayingAway", settings.stayingAway.encodeStance { stance -> settings.all.map { it.id }.filter { it in stance } })
    .put("checkIn", JSONObject().put("remind", settings.checkIn.remind).put("at", settings.checkIn.at.toString()))
    .put("rewards", settings.rewards?.encode() ?: JSONObject.NULL)
    .put("whenAway", settings.whenAway.encode { it.storageId })
    .put("goal", settings.goal.encode { it.storageId })
    .put("exceptionsOnly", settings.exceptionsOnly.encode { it })
    .putOptional(SubstancesLabelField, settings.label)
    .put(SubstancesIconField, settings.icon.storageId)
    .put(SubstancesHueField, settings.hue.storageId)

internal fun JSONObject.decodeSubstanceLogs(settings: SubstanceSettings): List<SubstanceLog> =
    array("substanceLogs").objects().map { it.decodeSubstanceLog(settings) }.also { logs ->
        require(logs.distinctBy { it.id }.size == logs.size) { "Two substance logs share an ID" }
    }

internal fun encodeSubstanceLogs(logs: List<SubstanceLog>) = JSONArray().apply {
    logs.forEach { log ->
        put(
            JSONObject()
                .put("id", log.id)
                .put("kind", log.substanceId)
                .put("dateKey", log.dateKey)
                .put("timestampMillis", log.timestampMillis)
                .putOptional("amount", log.amount)
                .putOptional("amountUnit", log.amountUnit?.storageId),
        )
    }
}

private fun JSONObject.decodeSubstanceLog(settings: SubstanceSettings): SubstanceLog {
    requireFields(required = setOf("id", "kind", "dateKey", "timestampMillis"), optional = setOf("amount", "amountUnit"))
    val substance = settings.substance(text("kind")) ?: error("Unknown substance")
    val amount = if (has("amount")) decimalNumber(get("amount")) else null
    val unit = if (has("amountUnit")) SubstanceAmountUnit.fromStorageId(text("amountUnit")) ?: error("Unknown unit") else null
    require((amount == null) == (unit == null)) { "An amount has a unit" }
    require(amount == null || (amount.isFinite() && amount > 0.0 && unit == substance.unit)) { "Invalid substance amount" }
    val id = text("id").also { require(it.isNotBlank()) { "A substance log needs an ID" } }
    val dateKey = text("dateKey").also { require(isValidDateKey(it)) { "Invalid substance date" } }
    val timestamp = optLongOrNull("timestampMillis")?.takeIf { it >= 0 } ?: error("Invalid timestamp")
    return SubstanceLog(id, substance.id, dateKey, timestamp, amount, unit)
}

internal fun JSONObject.decodeSubstanceNoneDays(logs: List<SubstanceLog>): List<SubstanceNoneDay> {
    val useDays = logs.mapTo(HashSet()) { it.dateKey }
    return array("substanceNoneDays").objects().map { entry ->
        entry.requireFields(required = setOf("dateKey", "timestampMillis"))
        val date = entry.text("dateKey").also { require(isValidDateKey(it) && it !in useDays) { "A none day has a valid date and no uses" } }
        val timestamp = entry.optLongOrNull("timestampMillis")?.takeIf { it >= 0 } ?: error("Invalid timestamp")
        SubstanceNoneDay(date, timestamp)
    }.also { days -> require(days.distinctBy { it.dateKey }.size == days.size) { "Two none days share a date" } }
}

internal fun encodeSubstanceNoneDays(noneDays: List<SubstanceNoneDay>) = JSONArray().apply {
    noneDays.forEach { none -> put(JSONObject().put("dateKey", none.dateKey).put("timestampMillis", none.timestampMillis)) }
}
