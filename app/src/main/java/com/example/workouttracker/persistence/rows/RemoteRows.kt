package com.example.workouttracker.persistence.rows

/** A row as a store keeps it, tombstones included. */
internal data class RowEntry(val body: String, val position: Long, val changed: String, val deleted: Boolean)

/**
 * What taking [remote] rows from another copy changes in [stored]: the entries to write, each
 * whole. First every remote row replaces this copy's version when it is stamped later; one that is
 * new here, or comes back from a tombstone, goes after the live rows of its collection, in the
 * order the sending copy had it.
 *
 * Then what two copies recorded apart is settled over the result. Of live rows that share a key
 * [merge] says only one may hold, the latest stays, and a row [merge] says the others contradict
 * goes. Each removed row becomes a tombstone stamped by [newStamp], here, so its removal spreads
 * to every copy.
 */
internal fun mergeRemote(
    stored: Map<RowKey, RowEntry>,
    remote: List<SyncedRow>,
    merge: RowMerge,
    newStamp: () -> String,
): Map<RowKey, RowEntry> {
    val rows = stored.toMutableMap()
    val changed = linkedMapOf<RowKey, RowEntry>()
    fun put(key: RowKey, entry: RowEntry) {
        rows[key] = entry
        changed[key] = entry
    }
    fun remove(key: RowKey) = put(key, rows.getValue(key).copy(body = "", changed = newStamp(), deleted = true))

    val taken = remote.filter { row -> merge.shared(row.kind) && (stored[RowKey(row.kind, row.id)]?.changed ?: "") < row.changed }
    val placed = taken.filter { it.body != null && stored[RowKey(it.kind, it.id)]?.deleted != false }.groupBy { it.kind }.flatMap { (kind, arriving) ->
        val after = stored.filter { (key, entry) -> key.kind == kind && !entry.deleted }.values.maxOfOrNull { it.position } ?: -1
        arriving.sortedWith(compareBy({ it.position }, { it.id })).mapIndexed { index, row -> RowKey(kind, row.id) to after + 1 + index }
    }.toMap()
    taken.forEach { row ->
        val key = RowKey(row.kind, row.id)
        val position = placed[key] ?: stored[key]?.position ?: 0
        put(key, RowEntry(row.body.orEmpty(), position, row.changed, deleted = row.body == null))
    }
    if (changed.isEmpty()) return changed

    changed.keys.mapTo(mutableSetOf()) { it.kind }.forEach { kind ->
        val live = rows.filter { (key, entry) -> key.kind == kind && !entry.deleted }
        val byKey = live.entries.flatMap { (key, entry) -> merge.uniqueKeys(kind, entry.body).map { it to key } }.groupBy({ it.first }, { it.second })
        byKey.values.filter { it.size > 1 }.forEach { holders ->
            val standing = holders.filter { !rows.getValue(it).deleted }
            val latest = standing.maxByOrNull { rows.getValue(it).changed } ?: return@forEach
            (standing - latest).forEach(::remove)
        }
    }
    merge.contradicted(liveRows(rows, emptyMap())).forEach(::remove)
    return changed
}

/** The live rows of [stored] with [changes] applied, in their collections' order. */
internal fun liveRows(stored: Map<RowKey, RowEntry>, changes: Map<RowKey, RowEntry>): List<StoredRow> =
    (stored + changes).filterValues { !it.deleted }.map { (key, entry) -> StoredRow(key.kind, key.id, entry.body, entry.position) }
        .sortedWith(compareBy({ it.kind }, { it.position }))
