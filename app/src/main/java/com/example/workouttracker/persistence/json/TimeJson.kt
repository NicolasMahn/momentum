package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeLogOrder
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.time.TokenCover
import com.example.workouttracker.domain.time.TokenStep
import com.example.workouttracker.domain.time.TokenSteps
import org.json.JSONArray
import org.json.JSONObject

internal fun encodeTime(time: List<TimeConfiguration>) = JSONArray().apply { time.forEach { put(it.encode()) } }

internal fun JSONObject.decodeTime(): List<TimeConfiguration> = array("time").objects().map { it.decodeTimeView() }

private const val TokenStepsField = "tokenSteps"
private const val StepsField = "steps"

internal fun TimeConfiguration.encode(): JSONObject = JSONObject()
    .put("view", "time")
    .put("id", id)
    .put("label", label)
    .put("icon", icon.storageId)
    .put("hue", hue.storageId)
    .put("mo", mo.encode())
    .put("goal", goal.encode { it })
    .putOptional("rewards", rewards?.encode())
    .put("whenAway", whenAway.encode { it.storageId })
    .put("checkpoints", JSONArray().put(checkpoint.encode()))

private fun TimeRewards.encode(): JSONObject = JSONObject()
    .put("maxPasses", maxPasses.encode { it })
    .put(TokenStepsField, tokenSteps.encode { it.encode() })
    .put(TokenCoverField, tokenCover.encode { it.encode() })
    .putOptional("periods", periods?.encode())

/** A token's cover as stored: the word for a quarter of the goal, or the minutes as a whole number. */
internal fun TokenCover.encode(): Any = when (this) {
    TokenCover.QuarterOfGoal -> QuarterOfGoalCover
    is TokenCover.Minutes -> minutes
}

internal fun decodeTokenCover(raw: Any): TokenCover = when (raw) {
    QuarterOfGoalCover -> TokenCover.QuarterOfGoal
    is Int -> TokenCover.Minutes(raw)
    else -> error("A token covers a quarter of the goal or whole minutes")
}

private fun TokenSteps.encode(): JSONObject =
    JSONObject().put(StepsField, JSONArray().apply { steps.forEach { put(JSONObject().put("percent", it.percent).put("tokens", it.tokens)) } })

private fun decodeTokenSteps(raw: Any): TokenSteps {
    val stored = raw as? JSONObject ?: error("tokenSteps must be an object")
    stored.requireFields(required = setOf(StepsField))
    return TokenSteps(
        stored.array(StepsField).objects().map { step ->
            step.requireFields(required = setOf("percent", "tokens"))
            TokenStep(percent = wholeNumber(step.get("percent")), tokens = wholeNumber(step.get("tokens")))
        },
    )
}

internal fun JSONObject.decodeTimeView(): TimeConfiguration {
    requireFields(required = setOf("view", "id", "label", "icon", "hue", "mo", "goal", "whenAway", "checkpoints"), optional = setOf("rewards"))
    require(text("view") == "time") { "Unsupported view kind" }
    return TimeConfiguration(
        id = text("id"),
        label = text("label"),
        icon = decodeIcon(),
        hue = ViewHue.fromStorageId(text("hue")) ?: error("Unknown hue"),
        mo = getJSONObject("mo").decodeMoPolicy(),
        goal = decodeDated("goal", ::wholeNumber),
        rewards = if (has("rewards")) getJSONObject("rewards").decodeTimeRewards() else null,
        whenAway = decodeDated("whenAway") { raw -> WhenAway.fromStorageId(raw as? String ?: error("whenAway must be text")) ?: error("Unknown whenAway") },
        checkpoint = array("checkpoints").objects().single().decodeCheckpoint(),
    )
}

private fun JSONObject.decodeTimeRewards(): TimeRewards {
    requireFields(required = setOf("maxPasses", TokenStepsField), optional = setOf("periods", TokenCoverField))
    return TimeRewards(
        maxPasses = decodeDated("maxPasses", ::wholeNumber),
        periods = decodePeriods(),
        tokenSteps = decodeDated(TokenStepsField, ::decodeTokenSteps),
        tokenCover = setting(TokenCoverField, Dated.always(TokenCover.QuarterOfGoal)) { decodeDated(TokenCoverField, ::decodeTokenCover) },
    )
}

internal fun encodeTimeLogs(logs: List<TimeLog>) = JSONArray().apply {
    logs.forEach { put(it.encode()) }
}

internal fun TimeLog.encode(): JSONObject = JSONObject()
    .put("time", viewId)
    .put("dateKey", dateKey)
    .put("timestampMillis", timestampMillis)
    .putOptional("minutes", minutes)

/**
 * The `timeLogs` list, every log taken exactly as written: nothing is coerced, merged or left
 * out, so a list that holds anything but logs cannot be read at all. They come back in the order
 * the state holds them ([TimeLogOrder]). What a log may say is the caller's to ask.
 */
internal fun JSONObject.decodeTimeLogs(): List<TimeLog> =
    array("timeLogs").objects().map { it.decodeTimeLog() }

internal fun JSONObject.decodeTimeLog(): TimeLog {
    requireFields(required = setOf("time", "dateKey", "timestampMillis"), optional = setOf("minutes"))
    return TimeLog(
        viewId = text("time"),
        dateKey = text("dateKey"),
        timestampMillis = optLongOrNull("timestampMillis") ?: error("timestampMillis must be a whole number"),
        minutes = if (has("minutes")) wholeNumber(get("minutes")) else null,
    )
}
