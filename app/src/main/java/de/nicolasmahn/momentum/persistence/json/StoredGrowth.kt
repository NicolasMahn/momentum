package de.nicolasmahn.momentum.persistence.json

import org.json.JSONArray
import org.json.JSONObject

/*
 * How stored data grows without a conversion (`docs/configurable-views.md`, "How stored data
 * grows"). Schema 32 is the baseline: everything it stored stays required, so a damaged file is
 * still refused. Anything added after it was missing from every file and backup written before
 * it, so it is never required: a setting reads its fallback, a fact reads as not recorded, and a
 * collection reads as empty.
 */

/** What a field added after schema 32 means where older data lacks it. */
internal sealed interface WhenAbsent {
    /** The setting's fallback: what the app did before it existed, unless its declaration says why not. It never changes, and every save writes the setting. */
    data class Fallback(val value: Any) : WhenAbsent

    /** Not recorded. The field is written only while it holds a value. */
    data object NotRecorded : WhenAbsent

    /** No records yet. The collection is written on every save. */
    data object NoRecords : WhenAbsent
}

/**
 * One field added after schema 32: [name] on the objects at [owner] in the stored [payload].
 * [owner] is a path from the payload's root, with `[]` for every element of an array, such as
 * `measurements[]`; the root itself is the empty path.
 */
internal data class AddedField(val payload: StoredPayload, val owner: String, val name: String, val absent: WhenAbsent)

/** The stored payloads a backup checks, each one JSON document. */
internal enum class StoredPayload { TrackerState, TimerSessions, Holidays, PassChoices, Reminders, Visibility }

/** Everything added to the stored shapes after schema 32, in the one place codecs and the lossless backup check read. */
internal object AddedAfterBaseline {
    val fields: List<AddedField> = listOf(
        AddedField(StoredPayload.TrackerState, "measurements[]", TrendDaysField, WhenAbsent.Fallback(TrendDaysWhenAbsent)),
        AddedField(StoredPayload.TrackerState, "time[].rewards", TokenCoverField, WhenAbsent.Fallback(QuarterOfGoalCover)),
        AddedField(StoredPayload.TrackerState, "measurements[].rewards", EarlierWeeksField, WhenAbsent.NotRecorded),
        AddedField(StoredPayload.TrackerState, SubstancesOwner, SubstancesLabelField, WhenAbsent.NotRecorded),
        AddedField(StoredPayload.TrackerState, SubstancesOwner, SubstancesIconField, WhenAbsent.Fallback(SubstancesIconWhenAbsent)),
        AddedField(StoredPayload.TrackerState, SubstancesOwner, SubstancesHueField, WhenAbsent.Fallback(SubstancesHueWhenAbsent)),
        AddedField(StoredPayload.TrackerState, ActivityOwner, LogsDurationField, WhenAbsent.Fallback(LogsDurationWhenAbsent)),
        AddedField(StoredPayload.TrackerState, ListedActivitiesOwner, RecordsRouteField, WhenAbsent.Fallback(RecordsRouteWhenAbsent)),
        AddedField(StoredPayload.TrackerState, ActivityEntriesOwner, RouteField, WhenAbsent.NotRecorded),
        AddedField(StoredPayload.PassChoices, "", CheckpointChoicesField, WhenAbsent.NoRecords),
        AddedField(StoredPayload.PassChoices, "", WeekChoicesField, WhenAbsent.NoRecords),
    )

    /** Every store added after schema 32. A backup from before it lacks the store, and restoring that backup leaves it empty. */
    val stores: Set<String> = setOf(RowsStoreName)

    /** The addition [name] at [owner] of [payload], or null where nothing of that name was added. */
    fun field(payload: StoredPayload, owner: String, name: String): AddedField? =
        fields.firstOrNull { it.payload == payload && it.owner == owner && it.name == name }

    private fun declared(name: String, kind: (WhenAbsent) -> Boolean) {
        require(fields.any { it.name == name && kind(it.absent) }) { "$name is not declared as added after schema 32" }
    }

    internal fun requireSetting(name: String) = declared(name) { it is WhenAbsent.Fallback }
    internal fun requireFact(name: String) = declared(name) { it == WhenAbsent.NotRecorded }
    internal fun requireCollection(name: String) = declared(name) { it == WhenAbsent.NoRecords }
}

/**
 * A setting added after schema 32. Absent, it reads as [fallback], which must be its declared
 * fallback; the default a new view starts with is a separate value. List [name] among its object's
 * optional fields and write it on every save.
 */
internal fun <T> JSONObject.setting(name: String, fallback: T, read: (Any) -> T): T {
    AddedAfterBaseline.requireSetting(name)
    return if (has(name)) read(get(name)) else fallback
}

/** A fact added after schema 32. Absent, it was not recorded: it is null, never a value. */
internal fun <T : Any> JSONObject.optionalFact(name: String, read: (Any) -> T): T? {
    AddedAfterBaseline.requireFact(name)
    return if (has(name)) read(get(name)) else null
}

/** A collection added after schema 32. Absent, it holds no records yet. */
internal fun JSONObject.addedCollection(name: String): JSONArray {
    AddedAfterBaseline.requireCollection(name)
    return if (has(name)) array(name) else JSONArray()
}

internal const val TrendDaysField = "trendDays"

internal const val TokenCoverField = "tokenCover"

/**
 * The week rule a measurement's bank earned by before it earned by kept days, with its last day.
 * Where it is missing the bank never earned by the week.
 */
internal const val EarlierWeeksField = "earlierWeeks"

/**
 * What a time view's token covers where its bank was stored without [TokenCoverField]: a quarter
 * of the week's goal, what every token covered before it could be set. Never changes.
 */
internal const val QuarterOfGoalCover = "quarterOfGoal"

/** Where Substances' settings stand in the tracker state. */
internal const val SubstancesOwner = "substances"

/** The name a person gave Substances. Where it is missing they gave none, and the view is called by the app's own word. */
internal const val SubstancesLabelField = "label"

internal const val SubstancesIconField = "icon"

internal const val SubstancesHueField = "hue"

/** The icon and the color Substances wore while its look was fixed, the flask in pink, which settings stored without a look keep. Never change. */
internal const val SubstancesIconWhenAbsent = "science"
internal const val SubstancesHueWhenAbsent = "pink"

/** Where Activity's configuration stands in the tracker state. */
internal const val ActivityOwner = "activity"

/** Whether logging an activity asks how long it took. */
internal const val LogsDurationField = "logsDuration"

/**
 * Activity stored without [LogsDurationField] asks for no duration. It is the one fallback that is
 * not what the app did before the setting existed, when the field always stood on the screen: the
 * owner, the only user, asked for the field to be gone, and switches it on himself where he wants
 * it. A duration recorded before stays with its entry or session. Never changes.
 */
internal const val LogsDurationWhenAbsent = false

/** Where the activities a person lists stand in the tracker state. */
internal const val ListedActivitiesOwner = "activity.activities[]"

/** Whether an activity is recorded with GPS. */
internal const val RecordsRouteField = "recordsRoute"

/** An activity stored without [RecordsRouteField] was never recorded with GPS, which no build before it could. Never changes. */
internal const val RecordsRouteWhenAbsent = false

/** Where Activity's entries stand in the tracker state. */
internal const val ActivityEntriesOwner = "activityEntries[]"

/** The route an entry recorded with GPS went. Where it is missing, none was recorded. */
internal const val RouteField = "route"

/** The checkpoints a token is set aside for on the choices' day, each with its checklist. No choice was made where it is missing. */
internal const val CheckpointChoicesField = "checkpointChoices"

/** The tasks judged by their week that tokens are set aside for, each with its week's Monday. No choice was made where it is missing. */
internal const val WeekChoicesField = "weekChoices"

/** The tracker state as rows (Block 38), named here so the declaration does not reach into the rows package. */
internal const val RowsStoreName = "momentum_rows"

/** What a measurement stored without [TrendDaysField] follows: 21 days, the span schema 32 gave every measurement. Never changes. */
internal const val TrendDaysWhenAbsent = 21
