package de.nicolasmahn.momentum.persistence.sync

import de.nicolasmahn.momentum.persistence.backup.PersonalDataLock
import de.nicolasmahn.momentum.persistence.rows.RowMerge
import de.nicolasmahn.momentum.persistence.rows.RowStore
import de.nicolasmahn.momentum.persistence.rows.SyncMarks

/**
 * Brings this copy's rows together with a server's, one [exchange] at a time. Two marks in the
 * rows' meta remember how far: the server's cursor, and the stamp of the last of this copy's rows
 * the server took. Both move only together with the rows received, so an exchange that fails
 * anywhere is simply repeated, and the server keeps a row it already has.
 */
internal class RowSync(
    private val rows: RowStore,
    private val server: SyncServer,
    private val merge: RowMerge,
    /** The layout the rows hold the state in; both sides must hold the same one. */
    private val format: String,
    /** Whether the rows hold the state, so they may be sent. */
    private val ready: () -> Boolean,
) {
    fun exchange(): SyncOutcome = synchronized(ExchangeLock) {
        if (!ready()) return SyncOutcome.NotReady
        // What is sent is read in one step, so a restore lands wholly before it or wholly after.
        val (found, request, sentUpTo) = synchronized(PersonalDataLock) {
            // First: a database that came from another phone takes this one's ID and forgets its marks here.
            val device = rows.device()
            val found = mapOf(SyncMarks.Held to rows.meta(SyncMarks.Held), SyncMarks.Cursor to rows.meta(SyncMarks.Cursor), SyncMarks.Pushed to rows.meta(SyncMarks.Pushed))
            if (found[SyncMarks.Held] != null) return SyncOutcome.HeldAfterRestore
            val outgoing = rows.changesSince(found[SyncMarks.Pushed])
            Triple(found, SyncRequest(device, format, found[SyncMarks.Cursor], outgoing.filter { merge.shared(it.kind) }), outgoing.maxOfOrNull { it.changed })
        }
        val response = runCatching { server.exchange(request) }.getOrElse { return SyncOutcome.Unreachable }
        if (response.format != format) return SyncOutcome.Refused
        // A row changed here meanwhile is stamped after every row read for this push, so it goes next time.
        val marks = mapOf(SyncMarks.Cursor to response.cursor, SyncMarks.Pushed to (sentUpTo ?: found[SyncMarks.Pushed]))
        // A restore since the request was read takes nothing from the answer.
        if (!rows.applyRemote(response.rows, marks, merge, expected = found)) {
            return if (rows.meta(SyncMarks.Held) != null) SyncOutcome.HeldAfterRestore else SyncOutcome.Refused
        }
        return SyncOutcome.Synced(sent = request.rows.size, received = response.rows.size)
    }

    private companion object {
        /** One exchange at a time in this process, so no two take the same marks. */
        val ExchangeLock = Any()
    }
}
