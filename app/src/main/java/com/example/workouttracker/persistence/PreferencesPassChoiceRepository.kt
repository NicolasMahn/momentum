package com.example.workouttracker.persistence

import android.content.Context
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.mo.MoChoices
import com.example.workouttracker.domain.mo.monday
import com.example.workouttracker.persistence.json.CheckpointChoicesField
import com.example.workouttracker.persistence.json.WeekChoicesField
import com.example.workouttracker.persistence.json.addedCollection
import com.example.workouttracker.persistence.backup.PersonalDataLock
import com.example.workouttracker.widget.MoWidgetProvider
import java.time.DayOfWeek
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * What a person said ahead of a close. A manual choice quiets the nudges, and the derived ledger
 * settles it at 04:00 whatever was said.
 *
 * [trackers] and [checkpoints] hold for [day] alone: whole tasks, and single checkpoints of a
 * checklist that has several. [weeks] names tasks judged by their week's amount, each with the
 * Monday of the week it was chosen for, and holds until that week ends.
 */
data class PassChoices(
    val day: LocalDate,
    val trackers: Set<TrackerRef> = emptySet(),
    val checkpoints: Map<TrackerRef, Set<String>> = emptyMap(),
    val weeks: Map<TrackerRef, LocalDate> = emptyMap(),
) {
    fun forDay(today: LocalDate): Set<TrackerRef> = if (day == today) trackers else emptySet()

    /** The choices that hold on [today]: the day's own, and those of the week [today] is in. */
    fun on(today: LocalDate): MoChoices = MoChoices(
        tasks = forDay(today) + weeks.filterValues { it == today.monday() }.keys,
        checkpoints = if (day == today) checkpoints else emptyMap(),
    )

    /** These choices carried to [today]: a day's choices end with their day, a week's with its week. */
    fun carriedTo(today: LocalDate): PassChoices =
        if (day == today) this else PassChoices(today, weeks = weeks.filterValues { it == today.monday() })
}

class PreferencesPassChoiceRepository internal constructor(
    private val store: TrackerStringStore,
    private val onWritten: () -> Unit = {},
) {
    constructor(context: Context) : this(
        personalStringStore(context, PreferencesTrackerRepository.PREFERENCES_NAME),
        onWritten = { MoWidgetProvider.refresh(context) },
    )

    fun load(): PassChoices? = synchronized(PersonalDataLock) {
        store.read(SettingsKey)?.let(PassChoicesCodec::decode)
    }

    fun save(choices: PassChoices): Boolean = synchronized(PersonalDataLock) {
        runCatching {
            // Never replace an unreadable or newer payload with a choice made by this version.
            store.read(SettingsKey)?.let(PassChoicesCodec::decode)
            store.write(SettingsKey, PassChoicesCodec.encode(choices))
        }.getOrDefault(false)
    }.also { if (it) onWritten() }

    internal companion object {
        const val SettingsKey = "habit_pass_choices_v2"
    }
}

internal object PassChoicesCodec {
    private val order = compareBy<TrackerRef>({ it.type.value }, { it.id })

    fun encode(choices: PassChoices): String = JSONObject()
        .put("version", 2)
        .put("day", choices.day.toString())
        .put("trackers", JSONArray().apply { choices.trackers.sortedWith(order).forEach { put(it.toJson()) } })
        .put(CheckpointChoicesField, JSONArray().apply {
            choices.checkpoints.toSortedMap(order).forEach { (ref, ids) -> ids.sorted().forEach { put(ref.toJson().put("checkpoint", it)) } }
        })
        .put(WeekChoicesField, JSONArray().apply {
            choices.weeks.toSortedMap(order).forEach { (ref, monday) -> put(ref.toJson().put("monday", monday.toString())) }
        })
        .toString()

    fun decode(raw: String): PassChoices {
        val json = readObject(raw)
        val trackers = json.array("trackers").map { it.toRef(extra = null) }
        require(trackers.size == trackers.toSet().size) { "Duplicate pass tracker" }
        val checkpoints = json.addedCollection(CheckpointChoicesField).objects().map { entry ->
            val id = entry.get("checkpoint") as? String ?: error("Invalid pass checkpoint")
            require(id.isNotBlank()) { "Invalid pass checkpoint" }
            entry.toRef(extra = "checkpoint") to id
        }
        require(checkpoints.size == checkpoints.toSet().size) { "Duplicate pass checkpoint" }
        val weeks = json.addedCollection(WeekChoicesField).objects().map { entry ->
            entry.toRef(extra = "monday") to readDate(entry.get("monday")).also { require(it.dayOfWeek == DayOfWeek.MONDAY) { "A pass week starts on a Monday" } }
        }
        require(weeks.size == weeks.map { it.first }.toSet().size) { "Duplicate pass week" }
        return PassChoices(
            day = readDate(json.get("day")),
            trackers = trackers.toSet(),
            checkpoints = checkpoints.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() },
            weeks = weeks.toMap(),
        )
    }

    private fun TrackerRef.toJson(): JSONObject = JSONObject().put("type", type.value).put("id", id)

    private fun JSONObject.array(name: String): List<JSONObject> = (get(name) as? JSONArray ?: error("Invalid pass $name")).objects()

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { get(it) as? JSONObject ?: error("Invalid pass entry") }

    /** The tracker an entry names, which holds nothing but its reference and [extra]. */
    private fun JSONObject.toRef(extra: String?): TrackerRef {
        require(keys().asSequence().toSet() == setOfNotNull("type", "id", extra)) { "Invalid pass tracker fields" }
        return TrackerRef(
            TrackerTypeId(get("type") as? String ?: error("Invalid pass tracker type")),
            get("id") as? String ?: error("Invalid pass tracker ID"),
        )
    }

    private fun readObject(raw: String): JSONObject {
        require('\u0000' !in raw) { "Invalid JSON control character" }
        val parser = JSONTokener(raw)
        val json = parser.nextValue() as? JSONObject ?: error("Invalid pass choices")
        require(parser.nextClean() == '\u0000') { "Unexpected content after pass choices" }
        val fields = json.keys().asSequence().toSet()
        // What was added after schema 32 is missing from a value stored before it.
        require(fields.containsAll(Baseline) && (fields - Baseline).all { it in Added }) { "Invalid pass choice fields" }
        require(json.get("version") is Int && json.getInt("version") == 2) { "Unsupported pass choice version" }
        return json
    }

    private fun readDate(value: Any): LocalDate {
        val raw = value as? String ?: error("Invalid pass day")
        return LocalDate.parse(raw).also { require(it.toString() == raw) { "Invalid pass day" } }
    }

    private val Baseline = setOf("version", "day", "trackers")
    private val Added = setOf(CheckpointChoicesField, WeekChoicesField)
}
