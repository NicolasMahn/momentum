package com.example.workouttracker.persistence.rows

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.workouttracker.persistence.backup.DurableBackupManager
import com.example.workouttracker.persistence.backup.PersonalDataLock

/**
 * One stored object: [kind] says what it is, [id] which one, and [body] holds its fields as the
 * JSON the codecs write. [position] keeps a collection in its order.
 */
internal data class StoredRow(val kind: String, val id: String, val body: String, val position: Long)

/** What one save changes, committed together or not at all. */
internal data class RowChanges(
    /** Rows whose body is new or changed; each is stamped with the commit's clock. */
    val written: List<StoredRow> = emptyList(),
    /** Rows that only moved within their collection: their body and clock stay. */
    val moved: List<StoredRow> = emptyList(),
    /** Rows that were removed. Each leaves a tombstone, its body emptied. */
    val deleted: List<RowKey> = emptyList(),
    /** Meta values to set; a null removes its key. */
    val meta: Map<String, String?> = emptyMap(),
    /**
     * Whether these are a new installation's defaults rather than anyone's change: they are stamped
     * before every change, so a copy holding a person's own settings keeps them when the two meet.
     */
    val asDefaults: Boolean = false,
) {
    val isEmpty: Boolean get() = written.isEmpty() && moved.isEmpty() && deleted.isEmpty() && meta.isEmpty()
}

internal data class RowKey(val kind: String, val id: String)

/**
 * A row as it travels between copies of the data: its stamp says which copy's version is the
 * later one. A null [body] is a tombstone. [position] is where the sending copy keeps it, a hint
 * only: a copy that gets the row new lines it up by it, and one that has it keeps its own order.
 */
internal data class SyncedRow(val kind: String, val id: String, val body: String?, val changed: String, val position: Long = 0)

/**
 * Where a copy keeps its place in syncing, in the rows' meta. The cursor and the push mark belong
 * to one installation of the app: a database that arrives from another starts without them.
 */
internal object SyncMarks {
    /** The server's cursor: what this copy has received. */
    const val Cursor = "syncCursor"

    /** The stamp of the last of this copy's rows the server took. */
    const val Pushed = "syncPushed"

    /**
     * Present after a restore: syncing waits until someone decides whether the restored rows
     * replace every copy's or only this one's, which this block does not decide.
     */
    const val Held = "syncHeld"
}

/** What applying another copy's rows needs to know about the rows, which the store does not. */
internal interface RowMerge {
    /** Whether rows of [kind] are shared with other copies; the rest stay where they were made. */
    fun shared(kind: String): Boolean

    /**
     * The keys that the rules allow one live row of [kind] to hold, beside its ID, such as a
     * questions entry's view and date. Two live rows of one kind sharing one are the same thing
     * recorded twice: the later stays.
     */
    fun uniqueKeys(kind: String, body: String): List<String>

    /** The rows of [live] that the others contradict, such as a day said to be without uses that has one. */
    fun contradicted(live: List<StoredRow>): Set<RowKey>

    /** Whether [rows], the live rows a merge would leave, can be read. */
    fun readable(rows: List<StoredRow>): Boolean
}

/**
 * Where every row lives. A read returns the live rows only; tombstones and the clock are for a
 * server, which does not exist yet.
 */
internal interface RowStore {
    fun rows(): List<StoredRow>

    fun meta(key: String): String?

    /** Commits [changes] in one transaction; false when nothing could be written. */
    fun commit(changes: RowChanges): Boolean

    /** Every live row and [meta] value as they are, without recovering an interrupted restore first: the restore itself reads this. */
    fun snapshot(metaKeys: Set<String>): Pair<List<StoredRow>, Map<String, String>>

    /**
     * Replaces every row with [rows] in one transaction, as a restore does, and sets each [meta]
     * value, removing those that are null. It does not recover an interrupted restore first: the
     * restore itself calls it.
     */
    fun replaceAll(rows: List<StoredRow>, meta: Map<String, String?>)

    /** This copy's ID, made the first time it is asked for. */
    fun device(): String

    /** Every row stamped later than [after] (every row for null), tombstones included, in stamp order. */
    fun changesSince(after: String?): List<SyncedRow>

    /**
     * Takes [remote] rows from another copy in one transaction: each one stamped later than this
     * copy's version of it replaces it, a tombstone included, and a new one goes last in its
     * collection. Two live rows that [merge] says are the same thing keep the later; the other
     * becomes a tombstone stamped here, so its removal spreads. The clock moves past every stamp
     * received, so what is changed here next sorts after it. The [meta] values are set with the
     * rows. Nothing is written and false is returned if a meta value is no longer as [expected]
     * (a restore came in between), or if [RowMerge.readable] refuses what the rows would be.
     */
    fun applyRemote(remote: List<SyncedRow>, meta: Map<String, String?>, merge: RowMerge, expected: Map<String, String?> = emptyMap()): Boolean
}

/**
 * The rows in SQLite: one `entity` table for every kind, so a new kind or a new field never
 * changes the database's schema.
 */
internal class SqliteRowStore(
    context: Context,
    /** Reached lazily: the backup manager's own restore uses this store while it is being made. */
    private val backupsOf: (() -> DurableBackupManager)? = null,
    name: String = DatabaseName,
    private val clock: () -> Long = System::currentTimeMillis,
) : RowStore {
    private val backups: DurableBackupManager? get() = backupsOf?.invoke()

    /** Which device ID this installation gave the database, kept where Android never copies it to another phone. */
    private val installation = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, "$name.device"))

    private val helper = object : SQLiteOpenHelper(context, name, null, SchemaVersion) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.enableWriteAheadLogging()
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE entity (kind TEXT NOT NULL, id TEXT NOT NULL, body TEXT NOT NULL, position INTEGER NOT NULL, " +
                    "changed TEXT NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (kind, id))",
            )
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        }

        // The table holds every kind as rows, so its schema has no reason to change.
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected database version $oldVersion")
    }

    override fun rows(): List<StoredRow> = synchronized(PersonalDataLock) {
        backups?.recoverBeforeAccess()
        helper.readableDatabase.rawQuery("SELECT kind, id, body, position FROM entity WHERE deleted = 0 ORDER BY kind, position", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(StoredRow(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)))
            }
        }
    }

    override fun meta(key: String): String? = synchronized(PersonalDataLock) {
        backups?.recoverBeforeAccess()
        helper.readableDatabase.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
    }

    override fun commit(changes: RowChanges): Boolean = synchronized(PersonalDataLock) {
        if (changes.isEmpty) return@synchronized true
        backups?.recoverBeforeAccess()
        backups?.beforeChange()
        val db = helper.writableDatabase
        db.beginTransaction()
        val committed = try {
            val stamp = if (changes.asDefaults) Hlc.before(deviceIn(db)) else Hlc.next(metaIn(db, ClockKey), clock(), deviceIn(db))
            changes.written.forEach { row -> write(db, row, stamp) }
            changes.moved.forEach { row ->
                db.execSQL("UPDATE entity SET position = ? WHERE kind = ? AND id = ? AND deleted = 0", arrayOf<Any>(row.position, row.kind, row.id))
            }
            changes.deleted.forEach { key -> tombstone(db, key, stamp) }
            if (!changes.asDefaults) putMeta(db, ClockKey, stamp)
            changes.meta.forEach { (key, value) -> if (value == null) db.delete("meta", "key = ?", arrayOf(key)) else putMeta(db, key, value) }
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
        backups?.afterChange()
        committed
    }

    override fun snapshot(metaKeys: Set<String>): Pair<List<StoredRow>, Map<String, String>> = synchronized(PersonalDataLock) {
        val db = helper.readableDatabase
        val rows = db.rawQuery("SELECT kind, id, body, position FROM entity WHERE deleted = 0 ORDER BY kind, position, id", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(StoredRow(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3))) }
        }
        rows to metaKeys.mapNotNull { key -> metaIn(db, key)?.let { key to it } }.toMap()
    }

    /**
     * A restore is a change like any other, so it spreads to other copies: a row it does not hold
     * becomes a tombstone, and a row it holds is stamped unless it is already stored as it is.
     */
    override fun replaceAll(rows: List<StoredRow>, meta: Map<String, String?>) = synchronized(PersonalDataLock) {
        require(rows.map { RowKey(it.kind, it.id) }.toSet().size == rows.size) { "Two restored rows share a key" }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val stored = entriesIn(db)
            val stamp = Hlc.next(metaIn(db, ClockKey), clock(), deviceIn(db))
            val kept = rows.mapTo(mutableSetOf()) { RowKey(it.kind, it.id) }
            stored.filter { (key, entry) -> !entry.deleted && key !in kept }.keys.forEach { key -> tombstone(db, key, stamp) }
            rows.forEach { row ->
                val entry = stored[RowKey(row.kind, row.id)]
                if (entry != null && !entry.deleted && entry.body == row.body) {
                    if (entry.position != row.position) db.execSQL("UPDATE entity SET position = ? WHERE kind = ? AND id = ?", arrayOf<Any>(row.position, row.kind, row.id))
                } else {
                    write(db, row, stamp)
                }
            }
            putMeta(db, ClockKey, stamp)
            meta.forEach { (key, value) -> if (value == null) db.delete("meta", "key = ?", arrayOf(key)) else putMeta(db, key, value) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun device(): String = synchronized(PersonalDataLock) {
        backups?.recoverBeforeAccess()
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            deviceIn(db).also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    override fun changesSince(after: String?): List<SyncedRow> = synchronized(PersonalDataLock) {
        backups?.recoverBeforeAccess()
        helper.readableDatabase.rawQuery(
            "SELECT kind, id, body, deleted, changed, position FROM entity WHERE changed > ? ORDER BY changed, kind, id",
            arrayOf(after ?: ""),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(SyncedRow(cursor.getString(0), cursor.getString(1), cursor.getString(2).takeIf { cursor.getInt(3) == 0 }, cursor.getString(4), cursor.getLong(5)))
                }
            }
        }
    }

    override fun applyRemote(remote: List<SyncedRow>, meta: Map<String, String?>, merge: RowMerge, expected: Map<String, String?>): Boolean = synchronized(PersonalDataLock) {
        backups?.recoverBeforeAccess()
        if (expected.any { (key, value) -> metaIn(helper.readableDatabase, key) != value }) return@synchronized false
        backups?.beforeChange()
        val db = helper.writableDatabase
        db.beginTransaction()
        val applied = try {
            val device = deviceIn(db)
            val stored = entriesIn(db)
            var latest = listOfNotNull(metaIn(db, ClockKey), remote.maxOfOrNull { it.changed }).maxOrNull()
            val changes = mergeRemote(stored, remote, merge) { Hlc.next(latest, clock(), device).also { latest = it } }
            if (runCatching { merge.readable(liveRows(stored, changes)) }.getOrDefault(false)) {
                changes.forEach { (key, entry) ->
                    if (entry.deleted) tombstone(db, key, entry.changed, entry.position) else write(db, StoredRow(key.kind, key.id, entry.body, entry.position), entry.changed)
                }
                latest?.let { putMeta(db, ClockKey, it) }
                meta.forEach { (key, value) -> if (value == null) db.delete("meta", "key = ?", arrayOf(key)) else putMeta(db, key, value) }
                db.setTransactionSuccessful()
                true
            } else {
                false
            }
        } finally {
            db.endTransaction()
        }
        backups?.afterChange()
        applied
    }

    private fun entriesIn(db: SQLiteDatabase): Map<RowKey, RowEntry> =
        db.rawQuery("SELECT kind, id, body, position, changed, deleted FROM entity", null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(RowKey(cursor.getString(0), cursor.getString(1)), RowEntry(cursor.getString(2), cursor.getLong(3), cursor.getString(4), cursor.getInt(5) != 0))
                }
            }
        }

    private fun write(db: SQLiteDatabase, row: StoredRow, stamp: String) {
        db.insertWithOnConflict("entity", null, ContentValues().apply {
            put("kind", row.kind); put("id", row.id); put("body", row.body); put("position", row.position)
            put("changed", stamp); put("deleted", 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Empties a row's body and marks it deleted, or records a tombstone for a row this copy never had, at [position]. */
    private fun tombstone(db: SQLiteDatabase, key: RowKey, stamp: String, position: Long = 0) {
        db.insertWithOnConflict("entity", null, ContentValues().apply {
            put("kind", key.kind); put("id", key.id); put("body", ""); put("position", position)
            put("changed", stamp); put("deleted", 1)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        db.execSQL("UPDATE entity SET body = '', deleted = 1, changed = ? WHERE kind = ? AND id = ?", arrayOf<Any>(stamp, key.kind, key.id))
    }

    /**
     * This installation's device ID, which the marker in `no_backup/` keeps. A database holding
     * another one (it came from another phone by Android's transfer, or was made before the marker)
     * takes this installation's and forgets where it was in syncing, so two phones never stamp
     * alike; a restore's hold stays, and its rows keep their stamps. A marker is made once and read
     * back before it is trusted, so a transaction that rolls back leaves it to be used next time.
     */
    private fun deviceIn(db: SQLiteDatabase): String {
        val own = runCatching { String(installation.readFully(), Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() } ?: newDevice().also { fresh ->
            val stream = installation.startWrite()
            try {
                stream.write(fresh.toByteArray(Charsets.UTF_8))
                installation.finishWrite(stream)
            } catch (failure: java.io.IOException) {
                installation.failWrite(stream)
                throw failure
            }
            if (String(installation.readFully(), Charsets.UTF_8) != fresh) throw java.io.IOException("The device marker was not kept")
        }
        if (metaIn(db, DeviceKey) != own) {
            putMeta(db, DeviceKey, own)
            listOf(SyncMarks.Cursor, SyncMarks.Pushed).forEach { db.delete("meta", "key = ?", arrayOf(it)) }
        }
        return own
    }

    private fun metaIn(db: SQLiteDatabase, key: String): String? =
        db.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun putMeta(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("meta", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun newDevice(): String = java.util.UUID.randomUUID().toString()

    internal companion object {
        const val DatabaseName = "momentum.db"
        private const val SchemaVersion = 1
        private const val DeviceKey = "device"
        private const val ClockKey = "clock"

        @Volatile private var personal: Pair<String, SqliteRowStore>? = null

        /**
         * The one store of this process, so every reader and writer shares its connection and lock.
         * It belongs to its database file, so an app whose data folder is new gets a new one.
         */
        fun personal(context: Context): SqliteRowStore {
            val path = context.applicationContext.getDatabasePath(DatabaseName).absolutePath
            personal?.takeIf { it.first == path }?.let { return it.second }
            return synchronized(this) {
                personal?.takeIf { it.first == path }?.second
                    ?: SqliteRowStore(context.applicationContext, { DurableBackupManager.get(context) }).also { personal = path to it }
            }
        }
    }
}

/**
 * A hybrid logical clock: when a row last changed, ordered across devices even when a phone's
 * clock is wrong. It never goes back: a commit after one stamped in the future still sorts after
 * it. Written as `millis-counter-device`, with fixed widths so the text sorts as the clock does.
 */
internal object Hlc {
    fun next(previous: String?, nowMillis: Long, device: String): String {
        val (lastMillis, lastCounter) = previous?.split('-', limit = 3)?.let { it[0].toLong() to it[1].toInt() } ?: (0L to 0)
        val millis = maxOf(nowMillis, lastMillis)
        val counter = if (millis == lastMillis) lastCounter + 1 else 0
        // The counter has five digits; a sixth would sort before them, so a full one moves the time on.
        return if (counter > MaxCounter) format(millis + 1, 0, device) else format(millis, counter, device)
    }

    private const val MaxCounter = 99_999

    /** The copy that made [stamp]. */
    fun device(stamp: String): String = stamp.split('-', limit = 3).getOrElse(2) { "" }

    /** A reading before every other one: what a new installation's defaults are stamped with. */
    fun before(device: String): String = format(0, 0, device)

    private fun format(millis: Long, counter: Int, device: String) = String.format(java.util.Locale.ROOT, "%015d-%05d-%s", millis, counter, device)
}
