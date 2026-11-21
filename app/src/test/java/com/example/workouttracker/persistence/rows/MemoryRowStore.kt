package com.example.workouttracker.persistence.rows

/** Rows in memory, for tests: each commit replaces what it names, as the SQLite store does, and nothing else. */
internal class MemoryRowStore(
    initial: List<StoredRow> = emptyList(),
    meta: Map<String, String> = emptyMap(),
    /** Whether a commit may happen now; a refused one changes nothing and says false. */
    private val canCommit: () -> Boolean = { true },
    private val device: String = "memory",
) : RowStore {
    val live = initial.associateByTo(linkedMapOf()) { RowKey(it.kind, it.id) }
    val tombstones = mutableSetOf<RowKey>()
    val metaValues = meta.toMutableMap()
    val commits = mutableListOf<RowChanges>()
    var acceptCommits = true

    /** When each row last changed, tombstones included; a tick of its own clock per change. */
    private val stamps = initial.associateTo(mutableMapOf()) { RowKey(it.kind, it.id) to "" }
    private var ticks = 0L
    private var clock: String? = null

    private fun nextStamp(): String = Hlc.next(clock, ++ticks, device).also { clock = it }

    override fun rows(): List<StoredRow> = live.values.sortedWith(compareBy({ it.kind }, { it.position }))

    override fun meta(key: String): String? = metaValues[key]

    override fun commit(changes: RowChanges): Boolean {
        if (!acceptCommits || !canCommit()) return false
        if (changes.isEmpty) return true
        commits += changes
        val stamp = if (changes.asDefaults) Hlc.before(device) else nextStamp()
        changes.written.forEach { live[RowKey(it.kind, it.id)] = it; tombstones -= RowKey(it.kind, it.id); stamps[RowKey(it.kind, it.id)] = stamp }
        changes.moved.forEach { row -> live.computeIfPresent(RowKey(row.kind, row.id)) { _, old -> old.copy(position = row.position) } }
        changes.deleted.forEach { live -= it; tombstones += it; stamps[it] = stamp }
        changes.meta.forEach { (key, value) -> if (value == null) metaValues -= key else metaValues[key] = value }
        return true
    }

    override fun snapshot(metaKeys: Set<String>) = rows() to metaValues.filterKeys { it in metaKeys }

    override fun replaceAll(rows: List<StoredRow>, meta: Map<String, String?>) {
        val stamp = nextStamp()
        val kept = rows.map { RowKey(it.kind, it.id) }.toSet()
        live.keys.filter { it !in kept }.forEach { key -> live -= key; tombstones += key; stamps[key] = stamp }
        rows.forEach { row ->
            val key = RowKey(row.kind, row.id)
            if (live[key]?.body != row.body) stamps[key] = stamp
            live[key] = row
            tombstones -= key
        }
        meta.forEach { (key, value) -> if (value == null) metaValues -= key else metaValues[key] = value }
    }

    override fun device(): String = device

    override fun changesSince(after: String?): List<SyncedRow> = stamps.filterValues { it > (after ?: "") }
        .map { (key, stamp) -> SyncedRow(key.kind, key.id, live[key]?.body, stamp, live[key]?.position ?: 0) }
        .sortedWith(compareBy({ it.changed }, { it.kind }, { it.id }))

    override fun applyRemote(remote: List<SyncedRow>, meta: Map<String, String?>, merge: RowMerge, expected: Map<String, String?>): Boolean {
        if (expected.any { (key, value) -> metaValues[key] != value }) return false
        val stored = stamps.mapValues { (key, stamp) ->
            live[key]?.let { RowEntry(it.body, it.position, stamp, deleted = false) } ?: RowEntry("", 0, stamp, deleted = true)
        }
        val clockBefore = clock
        clock = listOfNotNull(clockBefore, remote.maxOfOrNull { it.changed }).maxOrNull()
        val changes = mergeRemote(stored, remote, merge, ::nextStamp)
        if (!runCatching { merge.readable(liveRows(stored, changes)) }.getOrDefault(false)) {
            clock = clockBefore
            return false
        }
        changes.forEach { (key, entry) ->
            stamps[key] = entry.changed
            if (entry.deleted) { live -= key; tombstones += key } else { live[key] = StoredRow(key.kind, key.id, entry.body, entry.position); tombstones -= key }
        }
        meta.forEach { (key, value) -> if (value == null) metaValues -= key else metaValues[key] = value }
        return true
    }
}
