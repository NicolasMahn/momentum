package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.EarlierWeeks
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementRewards
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy
import com.example.workouttracker.domain.measurement.MeasurementStep
import org.json.JSONArray
import org.json.JSONObject

internal fun encodeMeasurements(measurements: List<MeasurementConfiguration>) = JSONArray().apply {
    measurements.forEach { put(it.encode()) }
}

internal fun JSONObject.decodeMeasurements(): List<MeasurementConfiguration> =
    array("measurements").objects().map { it.decodeMeasurement() }

internal fun MeasurementConfiguration.encode(): JSONObject = JSONObject()
    .put("view", "measurement")
    .put("id", id)
    .put("label", label)
    .put("icon", icon.storageId)
    .put("hue", hue.storageId)
    .put("mo", mo.encode())
    .put("unit", unit)
    .put("step", step.written)
    .put("valuePolicy", valuePolicy.storageId())
    .put(TrendDaysField, trendDays)
    .put("checkpoints", JSONArray().put(checkpoint.encode()))
    .putOptional("rewards", rewards?.encode())
    .put("whenAway", whenAway.encode { it.storageId })

private fun MeasurementBank.encode(): JSONObject = when (this) {
    is MeasurementBank.Week -> JSONObject()
        .put("maxPasses", rewards.maxPasses.encode { it })
        .put("earn", JSONObject().put("keptDaysInWeek", rewards.keptDaysInWeek.encode { it }))
        .putOptional("periods", rewards.periods?.encode())
    is MeasurementBank.Standard -> rewards.encode().putOptional(
        EarlierWeeksField,
        earlierWeeks?.let { JSONObject().put("keptDaysInWeek", it.keptDaysInWeek.encode { days -> days }).put("until", it.until.toString()) },
    )
}

internal fun JSONObject.decodeMeasurement(): MeasurementConfiguration {
    requireFields(required = setOf("view", "id", "label", "icon", "hue", "mo", "unit", "step", "valuePolicy", "checkpoints", "whenAway"), optional = setOf("rewards", TrendDaysField))
    require(text("view") == "measurement") { "Unsupported view kind" }
    return MeasurementConfiguration(
        id = text("id"),
        label = text("label"),
        icon = decodeIcon(),
        hue = ViewHue.fromStorageId(text("hue")) ?: error("Unknown hue"),
        mo = getJSONObject("mo").decodeMoPolicy(),
        unit = text("unit"),
        step = storedStep(get("step")),
        valuePolicy = decodeMeasurementValuePolicy(text("valuePolicy")),
        trendDays = setting(TrendDaysField, TrendDaysWhenAbsent, ::wholeNumber),
        rewards = if (has("rewards")) decodeBank() else null,
        whenAway = decodeDated("whenAway") { raw -> WhenAway.fromStorageId(raw as? String ?: error("whenAway must be text")) ?: error("Unknown whenAway") },
        checkpoint = array("checkpoints").objects().single().decodeCheckpoint(),
    )
}

internal fun storedStep(raw: Any?): MeasurementStep {
    val written = raw as? String ?: error("step must be text")
    return MeasurementStep.parse(written)?.takeIf { it.written == written } ?: error("A step is written as its decimal")
}

private fun JSONObject.decodeBank(): MeasurementBank {
    val rewards = getJSONObject("rewards")
    val earn = rewards.optJSONObject("earn") ?: error("earn must be an object")
    return when (earn.keys().asSequence().toSet()) {
        setOf("keptDaysInWeek") -> {
            rewards.requireFields(required = setOf("maxPasses", "earn"), optional = setOf("periods"))
            MeasurementBank.Week(
                MeasurementRewards(
                    maxPasses = rewards.decodeDated("maxPasses", ::wholeNumber),
                    keptDaysInWeek = earn.decodeDated("keptDaysInWeek", ::wholeNumber),
                    periods = rewards.decodePeriods(),
                ),
            )
        }
        setOf("everyKeptDays") -> MeasurementBank.Standard(
            rewards = rewards.decodeRewards(optional = setOf(EarlierWeeksField)),
            earlierWeeks = rewards.optionalFact(EarlierWeeksField) { raw ->
                val weeks = raw as? JSONObject ?: error("$EarlierWeeksField must be an object")
                weeks.requireFields(required = setOf("keptDaysInWeek", "until"))
                EarlierWeeks(weeks.decodeDated("keptDaysInWeek", ::wholeNumber), weeks.date("until") ?: error("An earlier week rule needs its last day"))
            },
        )
        else -> error("Unknown way of earning")
    }
}

private fun MeasurementValuePolicy.storageId(): String = when (this) {
    MeasurementValuePolicy.Finite -> "finite"
    MeasurementValuePolicy.Positive -> "positive"
}

private fun decodeMeasurementValuePolicy(id: String): MeasurementValuePolicy = when (id) {
    "finite" -> MeasurementValuePolicy.Finite
    "positive" -> MeasurementValuePolicy.Positive
    else -> error("Unknown measurement value policy")
}
