package de.nicolasmahn.momentum.persistence.sync

import de.nicolasmahn.momentum.persistence.rows.SyncedRow

/**
 * One exchange with a server: this copy's [rows] changed since its last push, and the [cursor] the
 * server handed it last time, null before the first exchange. [format] is the layout the rows'
 * bodies are in, such as `rows-1`.
 */
internal data class SyncRequest(val device: String, val format: String, val cursor: String?, val rows: List<SyncedRow>)

/** The rows changed on the server since the request's cursor, in [format], and the cursor to send next time. */
internal data class SyncResponse(val format: String, val cursor: String, val rows: List<SyncedRow>)

/** Where rows are brought together. It keeps, per row, the version stamped latest. */
internal fun interface SyncServer {
    fun exchange(request: SyncRequest): SyncResponse
}

/** What one exchange came to. */
internal sealed interface SyncOutcome {
    data class Synced(val sent: Int, val received: Int) : SyncOutcome

    /** The rows do not hold the state yet, such as during the upgrade into rows: nothing was sent. */
    data object NotReady : SyncOutcome

    /** A backup was restored here since the last exchange: nothing is sent or taken until that is settled. */
    data object HeldAfterRestore : SyncOutcome

    /** The server could not be reached or answered nonsense: nothing changed here. */
    data object Unreachable : SyncOutcome

    /**
     * The server's rows would leave what this build cannot read, such as a kind or a field a newer
     * build added, or come in another layout. Nothing changed here, and the same rows come again
     * until an update can take them.
     */
    data object Refused : SyncOutcome
}
