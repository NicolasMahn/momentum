package de.nicolasmahn.momentum.persistence.sync

import de.nicolasmahn.momentum.persistence.rows.RowKey
import de.nicolasmahn.momentum.persistence.rows.SyncedRow

/**
 * A server in memory, by the rule a real one keeps: per row the version stamped latest, and a
 * cursor that counts changes. Every exchange goes through [SyncWire] both ways, as over a network.
 */
internal class MemorySyncServer : SyncServer {
    private val held = linkedMapOf<RowKey, Pair<Long, SyncedRow>>()
    private var changes = 0L
    var reachable = true

    val rows: List<SyncedRow> get() = held.values.map { it.second }

    override fun exchange(request: SyncRequest): SyncResponse {
        check(reachable) { "Offline" }
        val received = SyncWire.decodeRequest(SyncWire.encode(request))
        check(received.format == Format) { "Another layout" }
        received.rows.forEach { row ->
            val key = RowKey(row.kind, row.id)
            if ((held[key]?.second?.changed ?: "") < row.changed) held[key] = ++changes to row
        }
        val since = received.cursor?.toLong() ?: 0
        val response = SyncResponse(Format, changes.toString(), held.values.filter { it.first > since }.map { it.second })
        return SyncWire.decodeResponse(SyncWire.encode(response))
    }

    private companion object {
        const val Format = "rows-1"
    }

    /** Puts [row] on the server as another copy would. */
    fun hold(row: SyncedRow) {
        held[RowKey(row.kind, row.id)] = ++changes to row
    }
}
