package com.example.workouttracker.persistence.rows

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.persistence.RecordedTimeSessionsCodec
import com.example.workouttracker.persistence.backup.BackupArchive
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import com.example.workouttracker.persistence.json.parseObject
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * The tracker state as rows. The document `AppStateJsonCodec` writes stays the one definition of
 * what is valid: a save splits it into rows, a read puts the rows back together into it and decodes
 * that, so every check the document has, the cross-checks between collections too, still runs.
 *
 * Each element of a root collection is a row of that collection's kind, and each root object a row
 * of its own with the ID [Singleton]. A row's ID is given once, when its element first becomes a
 * row, and never changes: the element's natural key ([naturalKey]: its `id`, or the view and date
 * of a record there is one of a day) when no row of its collection holds that ID yet, otherwise a
 * random one. So every copy of the data that records the same day's weigh-in names it alike, and
 * an edit keeps the row it edits. An element is found again by its natural key while that key is
 * its alone, and otherwise by its content, so identical records stay separate rows.
 */
internal object TrackerRows {
    const val Singleton = "singleton"

    /**
     * The timer session ledger, committed with the state: one row per session start. It only says
     * which of this phone's timer runs were already added up, so it is never shared: two phones can
     * start a run in the same millisecond.
     */
    const val TimerSessionsKind = "timerSessions"

    /** The root as an empty state writes it: which fields are collections, which objects, and which may be null. */
    private val template: JSONObject by lazy { parseObject(AppStateJsonCodec.encode(AppState())) }

    private const val VersionField = "schemaVersion"

    /** Every kind a row may have. */
    val kinds: Set<String> get() = template.keys().asSequence().filter { it != VersionField }.toSet() + TimerSessionsKind

    /** The document the rows hold, and the timer sessions beside it. A row of a kind this build does not know is refused. */
    fun assemble(rows: List<StoredRow>): Pair<String, Set<Long>> {
        val byKind = rows.groupBy { it.kind }
        require(kinds.containsAll(byKind.keys)) { "Rows of an unknown kind" }
        val root = JSONObject().put(VersionField, template.get(VersionField))
        template.keys().asSequence().filter { it != VersionField }.forEach { kind ->
            val ofKind = byKind[kind].orEmpty().sortedBy { it.position }
            when (template.get(kind)) {
                is JSONArray -> {
                    require(ofKind.map { it.position }.toSet().size == ofKind.size) { "Two $kind rows share a position" }
                    root.put(kind, JSONArray().apply { ofKind.forEach { put(parseObject(it.body)) } })
                }
                else -> {
                    require(ofKind.size <= 1 && ofKind.all { it.id == Singleton }) { "$kind holds one object" }
                    val row = ofKind.singleOrNull()
                    // A missing object that cannot be null is left out, so decoding refuses it as damage.
                    if (row != null) root.put(kind, parseObject(row.body)) else if (template.isNull(kind)) root.put(kind, JSONObject.NULL)
                }
            }
        }
        val sessions = byKind[TimerSessionsKind].orEmpty().map { row ->
            row.body.toLong().also { require(it >= 0 && row.id == row.body) { "Invalid timer session row" } }
        }
        require(sessions.size == sessions.toSet().size) { "Repeated timer session" }
        return root.toString() to sessions.toSet()
    }

    /** What makes [current] rows hold [state]: the rows to write, to move and to delete. */
    fun changes(current: List<StoredRow>, state: AppState, newId: () -> String = { UUID.randomUUID().toString() }): RowChanges {
        val document = parseObject(AppStateJsonCodec.encode(state))
        // The ledger is checked as the document is, before anything is written.
        RecordedTimeSessionsCodec.encode(state.recordedTimeSessions)
        require(document.keys().asSequence().toSet() == template.keys().asSequence().toSet()) { "The document's fields changed" }
        val wanted = buildMap {
            template.keys().asSequence().filter { it != VersionField }.forEach { kind ->
                put(kind, when (val value = document.get(kind)) {
                    is JSONArray -> {
                        val objects = (0 until value.length()).map { value.getJSONObject(it) }
                        objects.map { json -> Element(json.toString(), naturalKey(kind, json)) }
                    }
                    is JSONObject -> listOf(Element(value.toString(), Singleton))
                    else -> emptyList()
                })
            }
            put(TimerSessionsKind, state.recordedTimeSessions.sortedDescending().map { Element(it.toString(), it.toString()) })
        }
        val byKind = current.groupBy { it.kind }
        val written = mutableListOf<StoredRow>()
        val moved = mutableListOf<StoredRow>()
        val deleted = mutableListOf<RowKey>()
        wanted.forEach { (kind, elements) ->
            val old = byKind[kind].orEmpty().sortedBy { it.position }
            // A singleton's and a timer session's row ID is its key; a collection element's is in its body.
            fun keyOf(row: StoredRow) = if (template.opt(kind) is JSONArray) naturalKey(kind, parseObject(row.body)) else row.id
            val oldKeys = old.groupBy(::keyOf)
            val wantedKeys = elements.mapNotNull { it.naturalKey }.groupingBy { it }.eachCount()
            val unclaimed = old.toMutableSet()
            val matches = arrayOfNulls<StoredRow>(elements.size)
            // An element whose natural key only it and one row hold is that row, whatever its content.
            elements.forEachIndexed { index, element ->
                val key = element.naturalKey ?: return@forEachIndexed
                val row = oldKeys[key]?.singleOrNull()?.takeIf { wantedKeys[key] == 1 } ?: return@forEachIndexed
                matches[index] = row
                unclaimed -= row
            }
            // The rest are matched by content, each old row at most once, so identical records stay
            // separate rows; of identical rows, the one in the element's own place first.
            val byContent = unclaimed.groupByTo(mutableMapOf()) { canonical(it.body) }
            elements.forEachIndexed { index, element ->
                if (matches[index] != null) return@forEachIndexed
                val alike = byContent[element.canonical] ?: return@forEachIndexed
                val row = alike.firstOrNull { it.position == index.toLong() } ?: alike.firstOrNull() ?: return@forEachIndexed
                alike -= row
                unclaimed -= row
                matches[index] = row
            }
            // What is left of a key several records share pairs up in order: an edited one of two
            // sessions with one ID keeps the row it was in rather than taking a new one.
            val leftOfKey = unclaimed.sortedBy { it.position }.groupByTo(mutableMapOf(), ::keyOf)
            elements.forEachIndexed { index, element ->
                if (matches[index] == null) matches[index] = leftOfKey[element.naturalKey]?.removeFirstOrNull()?.also { unclaimed -= it }
            }
            val taken = old.mapTo(mutableSetOf()) { it.id }
            elements.forEachIndexed { index, element ->
                val position = index.toLong()
                val match = matches[index]
                when {
                    match == null -> {
                        val id = element.naturalKey?.takeIf { it !in taken } ?: newId()
                        taken += id
                        written += StoredRow(kind, id, element.body, position)
                    }
                    canonical(match.body) != element.canonical -> written += StoredRow(kind, match.id, element.body, position)
                    match.position != position -> moved += match.copy(position = position)
                }
            }
            unclaimed.forEach { deleted += RowKey(kind, it.id) }
        }
        return RowChanges(written = written, moved = moved, deleted = deleted)
    }

    private class Element(val body: String, val naturalKey: String?) {
        val canonical: String = canonical(body)
    }

    /**
     * What identifies an element of [kind] by the domain's own rules: its `id`, or for a day that a
     * checklist, a timer, a measurement or the substance "none" record has once, its owner and date.
     * Null for a record that has neither.
     */
    private fun naturalKey(kind: String, element: JSONObject): String? = when (kind) {
        "checklistDays" -> "${element.optString("checklistId")}/${element.optString("dateKey")}"
        "substanceNoneDays" -> element.optString("dateKey")
        "timeLogs" -> "${element.optString("time")}/${element.optString("dateKey")}"
        "measurementLogs" -> "${element.optString("measurement")}/${element.optString("dateKey")}"
        else -> element.opt("id") as? String
    }

    /**
     * The keys the stored format allows one element of [kind] to hold, beside its row ID: a view's
     * or a record's `id` where IDs must differ, and the owner and date of a record there may be
     * only one of a day. Two copies that each recorded such a thing hold two rows of one key. Kinds
     * that allow two of one key (weigh-ins of a day, sessions sharing an ID) have none: both stay.
     */
    fun uniqueKeys(kind: String, body: String): List<String> {
        val element = runCatching { parseObject(body) }.getOrNull() ?: return emptyList()
        fun id() = "id:${element.optString("id")}"
        fun day(owner: String) = "day:${element.optString(owner)}/${element.optString("dateKey")}"
        return when (kind) {
            "checklists", "measurements", "questions", "time", "substanceLogs" -> listOf(id())
            "questionEntries" -> listOf(id(), day("questions"))
            "checklistDays" -> listOf(day("checklistId"))
            "timeLogs" -> listOf(day("time"))
            "substanceNoneDays" -> listOf(day(""))
            else -> emptyList()
        }
    }

    /**
     * Whether rows of [kind] with bodies [a] and [b] are surely the same record: a singleton, or two
     * that share a key the stored format allows only one of. Two weigh-ins of a day, or two sessions
     * sharing an ID, may be different records.
     */
    fun sameRecord(kind: String, a: String, b: String): Boolean =
        template.opt(kind) !is JSONArray || uniqueKeys(kind, a).any { it in uniqueKeys(kind, b) }

    /**
     * Substance "none" days that a use recorded the same date contradicts: two copies said both of
     * one day. The use is the fact, so the "none" goes.
     */
    fun contradicted(live: List<StoredRow>): Set<RowKey> {
        val useDays = live.filter { it.kind == "substanceLogs" }.mapNotNullTo(HashSet()) { runCatching { parseObject(it.body).optString("dateKey") }.getOrNull() }
        return live.filter { it.kind == "substanceNoneDays" && runCatching { parseObject(it.body).optString("dateKey") }.getOrNull() in useDays }
            .mapTo(mutableSetOf()) { RowKey(it.kind, it.id) }
    }

    private fun canonical(body: String): String = runCatching { BackupArchive.canonicalJson(parseObject(body)) }.getOrDefault(body)
}
