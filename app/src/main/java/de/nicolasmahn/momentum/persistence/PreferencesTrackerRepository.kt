package de.nicolasmahn.momentum.persistence

import android.content.Context
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.settings.NewInstallationVisibility
import de.nicolasmahn.momentum.persistence.json.AppStateJsonCodec
import de.nicolasmahn.momentum.persistence.rows.RowChanges
import de.nicolasmahn.momentum.persistence.rows.RowMerge
import de.nicolasmahn.momentum.persistence.rows.RowStore
import de.nicolasmahn.momentum.persistence.rows.SqliteRowStore
import de.nicolasmahn.momentum.persistence.rows.StoredRow
import de.nicolasmahn.momentum.persistence.rows.TrackerRows
import de.nicolasmahn.momentum.persistence.rows.RowKey
import de.nicolasmahn.momentum.persistence.sync.RowSync
import de.nicolasmahn.momentum.persistence.sync.SyncOutcome
import de.nicolasmahn.momentum.persistence.sync.SyncServer
import de.nicolasmahn.momentum.widget.MoWidgetProvider

/**
 * The tracker state, stored as rows ([RowStore], [TrackerRows]). Settings that are not tracker
 * state (holidays, pass choices, the habit rule start) stay in [store], the `workout_tracker`
 * preferences, which are also where `state_v32` lay before the rows: the first read upgrades it,
 * once, keeping its bytes through [keepBeforeRows] first.
 */
class PreferencesTrackerRepository internal constructor(
    private val store: TrackerStringStore,
    private val onWritten: () -> Unit = {},
    private val defaults: ConfigurationDefaults,
    private val onNewInstallation: () -> Unit = {},
    private val rows: RowStore,
    private val keepBeforeRows: (Map<String, String>) -> Boolean,
) : TrackerRepository {
    constructor(context: Context) : this(
        personalStringStore(context, PREFERENCES_NAME),
        onWritten = { MoWidgetProvider.refresh(context) },
        defaults = AndroidConfigurationDefaults(context),
        onNewInstallation = { PreferencesFeatureVisibilityRepository(context).saveIfNoneStored(NewInstallationVisibility) },
        rows = SqliteRowStore.personal(context),
        keepBeforeRows = { values -> keepBeforeRowsCopy(context, values) },
    )

    /**
     * The rows each of the last few reads was made from, by the very state each read returned, so a
     * save given the state a screen read knows exactly which rows the screen changed, even when a
     * later read returned an equal state from other rows.
     */
    private val reads = ArrayDeque<Pair<AppState, List<StoredRow>>>()

    private fun remember(state: AppState, rows: List<StoredRow>) {
        reads.addFirst(state to rows)
        while (reads.size > RememberedReads) reads.removeLast()
    }

    private fun rowsRead(state: AppState): List<StoredRow>? = reads.firstOrNull { it.first === state }?.second

    /** Background readers get no facts when access is refused; writes always check the store again. */
    override fun load(): AppState = loadIfReadable() ?: AppState()

    override fun loadIfReadable(): AppState? {
        val result = synchronized(StorageLock) {
            runCatching { readState(seedInstallation = true) }.getOrDefault(StateReadResult.Unreadable)
        }
        return when (result) {
            is StateReadResult.Available -> result.state
            is StateReadResult.Empty -> result.seed.also { if (result.written) onNewInstallation() }
            StateReadResult.Unreadable -> null
        }
    }

    override fun save(state: AppState): Boolean = saveFrom(state, base = null)

    override fun save(state: AppState, base: AppState): Boolean = saveFrom(state, base)

    private fun saveFrom(state: AppState, base: AppState?): Boolean = synchronized(StorageLock) {
        when (val read = runCatching { readState(seedInstallation = false) }.getOrNull()) {
            is StateReadResult.Available -> read.writable && writeState(state, base)
            is StateReadResult.Empty -> writeState(state)
            else -> false
        }
    }

    /**
     * One exchange with [server]. It runs only while the rows hold the state, and takes nothing the
     * state could not be read from. What it brings is on screen after the next read.
     */
    internal fun sync(server: SyncServer): SyncOutcome =
        RowSync(rows, server, TrackerRowMerge, format = RowsFormat, ready = ::holdsState).exchange().also { if (it is SyncOutcome.Synced && it.received > 0) onWritten() }

    private fun holdsState(): Boolean = synchronized(StorageLock) {
        runCatching { readState(seedInstallation = false) }.getOrNull().let { it is StateReadResult.Available && it.writable && rows.meta(FormatKey) == RowsFormat }
    }

    override fun update(transform: (AppState) -> AppState): TrackerUpdateResult = synchronized(StorageLock) {
        val read = runCatching { readState(seedInstallation = false) }
            .getOrElse { return@synchronized TrackerUpdateResult.SaveFailed }
        val current = when (read) {
            is StateReadResult.Available -> read.state.takeIf { read.writable } ?: return@synchronized TrackerUpdateResult.SaveFailed
            is StateReadResult.Empty -> read.seed
            StateReadResult.Unreadable -> return@synchronized TrackerUpdateResult.UnreadableState
        }
        val updated = runCatching { transform(current) }.getOrElse { return@synchronized TrackerUpdateResult.SaveFailed }
        if (writeState(updated)) TrackerUpdateResult.Updated(updated) else TrackerUpdateResult.SaveFailed
    }

    private fun readState(seedInstallation: Boolean): StateReadResult {
        val keys = store.keys()
        // Unsupported entries, including preserved damage, must never turn an existing
        // installation into an apparently empty one.
        if (keys.any { it !in CurrentKeys }) return StateReadResult.Unreadable
        runCatching {
            store.read(PreferencesHolidayRepository.SettingsKey)?.let(HolidayScheduleCodec::decode)
            store.read(PreferencesHabitRuleStartRepository.SettingsKey)?.let(::parseHabitRuleStart)
            store.read(PreferencesPassChoiceRepository.SettingsKey)?.let(PassChoicesCodec::decode)
        }.getOrElse { return StateReadResult.Unreadable }
        val documentKeys = keys.filter { it in DocumentKeys }.toSet()
        // Rows exist exactly while they hold the state: a stored state always has its Substances
        // row, and restoring a backup from before the rows empties them.
        val stored = rows.rows()
        val pending = rows.meta(ImportedKey)
        if (stored.isNotEmpty()) {
            if (rows.meta(FormatKey) != RowsFormat) return StateReadResult.Unreadable
            val state = runCatching {
                val (document, sessions) = TrackerRows.assemble(stored)
                AppStateJsonCodec.decodeVersion32(document).copy(recordedTimeSessions = sessions)
            }.getOrElse { return StateReadResult.Unreadable }
            if (documentKeys.isNotEmpty()) {
                // A document left by an upgrade that stopped before removing it is spent: the
                // import's marker names it until it is removed. Any other document beside the rows
                // came from elsewhere, such as an older build restoring a backup, and which one
                // holds the person's records is not for this read to guess.
                val leftover = documentKeys.associateWith { requireNotNull(store.read(it)) }
                if (pending == null || fingerprint(leftover) != pending) return StateReadResult.Unreadable
            }
            remember(state, stored)
            // An import whose cleanup is unfinished refuses writes until it is finished.
            if (pending != null) return StateReadResult.Available(state, writable = finishImport())
            return StateReadResult.Available(state)
        }
        // Without rows, a layout or an import marker means the rows were lost, not that none were ever made.
        if (rows.meta(FormatKey) != null || pending != null) return StateReadResult.Unreadable
        if (documentKeys.isEmpty()) {
            val seed = AppState(activity = defaults.activity())
            return StateReadResult.Empty(seed, written = seedInstallation && writeState(seed, asDefaults = true))
        }
        val raw = store.read(STATE_V32_KEY) ?: return StateReadResult.Unreadable
        val ledger = store.read(RECORDED_TIME_SESSIONS_KEY) ?: return StateReadResult.Unreadable
        val state = runCatching { AppStateJsonCodec.decodeVersion32(raw).copy(recordedTimeSessions = RecordedTimeSessionsCodec.decode(ledger)) }
            .getOrElse { return StateReadResult.Unreadable }
        // Until the rows hold it, the document is read but not written over.
        return StateReadResult.Available(state, writable = upgradeToRows(state, mapOf(STATE_V32_KEY to raw, RECORDED_TIME_SESSIONS_KEY to ledger)))
    }

    /**
     * Moves the document into rows: its bytes are kept first, then the rows, the layout and the
     * import's marker are committed together, then the document is removed, and only then is the
     * marker retired. A step that fails leaves what is stored readable, and the next read goes on
     * from there. Says whether every step happened; until then nothing is written.
     */
    private fun upgradeToRows(state: AppState, document: Map<String, String>): Boolean {
        if (!runCatching { keepBeforeRows(document) }.getOrDefault(false)) return false
        val changes = TrackerRows.changes(rows.rows(), state).withFormat()
        val committed = runCatching { rows.commit(changes.copy(meta = changes.meta + (ImportedKey to fingerprint(document)))) }.getOrDefault(false)
        if (committed) remember(state, rows.rows())
        return committed && finishImport()
    }

    /**
     * Removes the document the rows were made from, then retires the import's marker. Both keys are
     * removed in a commit that must succeed even when they look absent already: SharedPreferences
     * forgets a key in memory before it knows whether the file changed. Says whether both happened.
     */
    private fun finishImport(): Boolean =
        runCatching { store.remove(DocumentKeys) && rows.commit(RowChanges(meta = mapOf(ImportedKey to null))) }.getOrDefault(false)

    /**
     * Decode failure leaves the stored rows in place; no write can replace that history. Given the
     * [base] a screen read, only what the screen changed is written, and a change that cannot stand
     * beside what was stored since is refused, so the screen reads again; so is one whose read is
     * no longer known, rather than guessing what it changed.
     */
    private fun writeState(state: AppState, base: AppState? = null, asDefaults: Boolean = false): Boolean = runCatching {
        val stored = rows.rows()
        val changes = if (base == null) {
            TrackerRows.changes(stored, state)
        } else {
            val read = rowsRead(base) ?: return@runCatching false
            ownChanges(stored, read, state) ?: return@runCatching false
        }
        rows.commit(changes.withFormat().copy(asDefaults = asDefaults)).also { committed ->
            // Written whole, the rows now hold exactly [state], which a caller may show and save
            // from next. Saved against a base they can hold more, so the caller reads again.
            if (committed && base == null) remember(state, rows.rows())
        }
    }.getOrDefault(false).also { if (it) onWritten() }

    /**
     * What a screen changed from the [read] rows it was shown, applied to the [stored] rows: a row
     * changed since, by a notification's action or another copy, stays as it now is unless the
     * screen changed that row too. Null when the result would not read.
     */
    private fun ownChanges(stored: List<StoredRow>, read: List<StoredRow>, state: AppState): RowChanges? {
        val now = stored.associateBy { RowKey(it.kind, it.id) }
        val wasRead = read.mapTo(HashSet()) { RowKey(it.kind, it.id) }
        // A new record never takes the ID of another record that arrived since under it.
        val own = TrackerRows.changes(read, state).let { changes ->
            changes.copy(written = changes.written.map { row ->
                val there = now[RowKey(row.kind, row.id)]
                if (RowKey(row.kind, row.id) in wasRead || there == null || TrackerRows.sameRecord(row.kind, there.body, row.body)) row
                else row.copy(id = java.util.UUID.randomUUID().toString())
            })
        }
        if (own.isEmpty) return RowChanges()
        val moved = own.moved.mapNotNull { row -> now[RowKey(row.kind, row.id)]?.copy(position = row.position) }
        val merged = (now - own.deleted.toSet() + (moved + own.written).associateBy { RowKey(it.kind, it.id) }).values
            // Rows that arrived since and rows added here may share a position: order them again.
            .groupBy { it.kind }.values.flatMap { ofKind ->
                ofKind.sortedWith(compareBy({ it.position }, { it.id })).mapIndexed { index, row -> row.copy(position = index.toLong()) }
            }
        if (!TrackerRowMerge.readable(merged)) return null
        val written = own.written.mapTo(HashSet()) { RowKey(it.kind, it.id) }
        return RowChanges(
            written = merged.filter { RowKey(it.kind, it.id) in written },
            moved = merged.filter { RowKey(it.kind, it.id) !in written && now[RowKey(it.kind, it.id)]?.position != it.position },
            deleted = own.deleted.filter { it in now },
        )
    }

    private fun RowChanges.withFormat() = copy(meta = meta + (FormatKey to RowsFormat))

    private sealed interface StateReadResult {
        /** [writable] is false while moving the document into rows is unfinished: no save may happen beside it. */
        data class Available(val state: AppState, val writable: Boolean = true) : StateReadResult
        data class Empty(val seed: AppState, val written: Boolean) : StateReadResult
        data object Unreadable : StateReadResult
    }

    internal companion object {
        private val StorageLock = de.nicolasmahn.momentum.persistence.backup.PersonalDataLock
        private const val RememberedReads = 4
        const val PREFERENCES_NAME = "workout_tracker"
        /** The tracker state as one document, before the rows. A read moves it into the rows. */
        const val STATE_V32_KEY = "state_v32"
        /** The timer sessions beside that document, moved into the rows with it. */
        const val RECORDED_TIME_SESSIONS_KEY = "reading_sessions_recorded_v1"
        /** The layout the rows hold the tracker state in. */
        const val FormatKey = "trackerFormat"
        const val RowsFormat = "rows-1"
        /**
         * The fingerprint of the document the rows were just made from, present only until that
         * document is removed, so only it is ever removed as spent.
         */
        const val ImportedKey = "importedDocument"
        private val DocumentKeys = setOf(STATE_V32_KEY, RECORDED_TIME_SESSIONS_KEY)
        private val CurrentKeys = DocumentKeys + setOf(
            PreferencesHolidayRepository.SettingsKey,
            PreferencesHabitRuleStartRepository.SettingsKey,
            PreferencesPassChoiceRepository.SettingsKey,
        )
    }
}

/** The tracker's rules for merging rows from another copy: its unique keys, and whether the rows read. */
internal object TrackerRowMerge : RowMerge {
    override fun shared(kind: String) = kind != TrackerRows.TimerSessionsKind

    override fun uniqueKeys(kind: String, body: String): List<String> = TrackerRows.uniqueKeys(kind, body)

    override fun contradicted(live: List<StoredRow>) = TrackerRows.contradicted(live)

    override fun readable(rows: List<StoredRow>): Boolean = rows.isNotEmpty() && runCatching {
        val (document, _) = TrackerRows.assemble(rows)
        AppStateJsonCodec.decodeVersion32(document)
    }.isSuccess
}

/** The document's entries in one hash: which keys hold exactly which bytes. */
internal fun fingerprint(document: Map<String, String>): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    document.toSortedMap().forEach { (key, value) ->
        digest.update("${key.length}:$key${value.length}:".toByteArray(Charsets.UTF_8))
        digest.update(value.toByteArray(Charsets.UTF_8))
    }
    return digest.digest().joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
}

/**
 * Keeps the document's bytes in `no_backup/` before the rows replace it, each time under a name of
 * its own, and reads the copy back before saying it is kept.
 */
private fun keepBeforeRowsCopy(context: Context, values: Map<String, String>): Boolean {
    val bytes = org.json.JSONObject(values).toString().toByteArray(Charsets.UTF_8)
    val file = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, "before-rows-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.json"))
    val stream = file.startWrite()
    return try {
        stream.write(bytes)
        file.finishWrite(stream)
        file.readFully().contentEquals(bytes)
    } catch (failure: java.io.IOException) {
        file.failWrite(stream)
        false
    }
}

/** A plain JSON array of session start times, in milliseconds since the epoch. */
internal object RecordedTimeSessionsCodec {
    fun encode(starts: Set<Long>): String {
        require(starts.all { it >= 0 }) { "Invalid recorded timer sessions" }
        return org.json.JSONArray(starts.sortedDescending()).toString()
    }

    fun decode(raw: String): Set<Long> {
        require('\u0000' !in raw) { "Invalid JSON control character" }
        val parser = org.json.JSONTokener(raw)
        val array = parser.nextValue() as? org.json.JSONArray ?: error("Recorded timer sessions must be a list")
        require(parser.nextClean() == '\u0000') { "Unexpected trailing content" }
        val starts = (0 until array.length()).map { index ->
            val value = array.get(index)
            require(value is Long || value is Int) { "Invalid timer session start" }
            (value as Number).toLong().also { require(it >= 0) { "Invalid timer session start" } }
        }
        require(starts.size == starts.toSet().size) { "Duplicate timer session start" }
        return starts.toSet()
    }
}
