package com.example.workouttracker.persistence.backup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import com.example.workouttracker.widget.MoWidgetProvider
import org.json.JSONArray
import org.json.JSONObject
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.persistence.rows.RowsBackupStore
import com.example.workouttracker.persistence.rows.SqliteRowStore
import com.example.workouttracker.persistence.rows.SyncMarks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/** Serializes repository commits, restore, and snapshot capture across activities and receivers. */
internal val PersonalDataLock = Any()

data class BackupStatus(
    val configured: Boolean,
    val lastVerifiedAt: String?,
    val pending: Boolean,
    val error: BackupError?,
    val pendingSinceMillis: Long? = null,
)

/** Why the latest backup failed, stored as a stable code so the status can be shown in any language. */
enum class BackupError(internal val code: String) {
    WriteFailed("write_failed");

    internal companion object {
        // Older versions stored an English sentence instead of a code, and every one of them
        // described this same failure, so an unknown value still reads as an honest failure.
        fun fromStored(value: String): BackupError = entries.firstOrNull { it.code == value } ?: WriteFailed
    }
}

/** A backup step the user can put right, named so Settings can explain it in the app's language. */
class BackupSetupException(val problem: Problem) : Exception(problem.name) {
    enum class Problem { NotLocalFolder, NoFolderChosen }
}

/**
 * The backups earlier versions left in one folder, as counted at one moment. Tidying removes
 * exactly the [removable] ones counted then, by the keep rule, and leaves the rest.
 */
class OldBackups internal constructor(internal val folder: String, internal val tidy: OldBackupTidy) {
    val found: Int get() = tidy.found
    val removable: Int get() = tidy.remove.size
}

class DurableBackupManager internal constructor(
    private val context: Context,
    /** The folder behind a tree the person chose; tests put one in memory here. */
    private val folder: (Uri) -> BackupDestination = { tree -> DocumentBackupDestination(context, tree) },
) {
    private val preferences = context.getSharedPreferences("momentum_backup", Context.MODE_PRIVATE)
    private val storedIndex = PreferencesGenerationIndex(preferences)
    private val executor = Executors.newSingleThreadExecutor()
    private val operationLock = Any()
    private val storage = AndroidRestoreStorage(context)
    private val transaction = BackupRestoreTransaction(storage)
    private val mutableStatus = MutableStateFlow(loadStatus())
    val status: StateFlow<BackupStatus> = mutableStatus

    init {
        synchronized(PersonalDataLock) {
            transaction.recover()
            if (preferences.getBoolean("pending", true) && !preferences.contains("pending_since")) {
                check(preferences.edit().putLong("pending_since", System.currentTimeMillis()).commit())
                publish()
            }
        }
        if (status.value.configured) executor.execute { runCatching { backupNow() } }
    }

    internal fun recoverBeforeAccess() = synchronized(PersonalDataLock) { transaction.recover(); Unit }

    /** Called before each commit so process death cannot leave a stale 'protected' status. */
    internal fun beforeChange() = synchronized(PersonalDataLock) {
        check(preferences.edit()
            .putLong("revision", preferences.getLong("revision", 0) + 1)
            .putBoolean("pending", true)
            .putLong("pending_since", pendingStartedAt()).commit()) { "Cannot record backup freshness" }
        publish()
    }

    internal fun afterChange() {
        if (status.value.configured) enqueueBackup()
    }

    fun configure(treeUri: Uri) = synchronized(operationLock) {
        if (treeUri.authority != "com.android.externalstorage.documents") {
            throw BackupSetupException(BackupSetupException.Problem.NotLocalFolder)
        }
        context.contentResolver.takePersistableUriPermission(
            treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        // Verify the destination before replacing an already working configuration.
        val capture = capture()
        // What lies in another folder is left alone from now on, so its backups leave the index,
        // together with the folder and only once the new one has proved it can be written.
        val sameFolder = treeUri.toString() == preferences.getString("tree", null)
        val index = if (sameFolder) storedIndex else MemoryGenerationIndex()
        BackupGenerations(folder(treeUri), index).write(capture.second)
        recordVerification(capture.first, treeUri, index.read().takeUnless { sameFolder })
        if (status.value.pending) enqueueBackup()
    }

    fun backupNow() = synchronized(operationLock) {
        try {
            val tree = preferences.getString("tree", null)
                ?: throw BackupSetupException(BackupSetupException.Problem.NoFolderChosen)
            val capture = capture()
            generations(tree).hold(capture.second)
            recordVerification(capture.first)
        } catch (failure: Exception) {
            synchronized(PersonalDataLock) {
                preferences.edit().putBoolean("pending", true)
                    .putLong("pending_since", pendingStartedAt())
                    .putString("error", BackupError.WriteFailed.code)
                    .commit()
                publish()
            }
            throw failure
        }
    }

    fun readForRestore(uri: Uri): BackupSnapshot =
        BackupArchive.decode(readBounded(context, uri)).also(::validateRestoreCompatibility)

    fun restore(snapshot: BackupSnapshot) = synchronized(operationLock) {
        validateRestoreCompatibility(snapshot)
        // Restoring is how damaged records get replaced, so their copy must not have to pass the
        // restore checks they fail. It is kept, read back, and not counted as protection.
        preferences.getString("tree", null)?.let { tree -> generations(tree).preserve(capture().second) }
        synchronized(PersonalDataLock) {
            // Preserve the current installation before replacement; the user confirms replacement in UI.
            storage.saveRescueCopy(storage.readSnapshot())
            beforeChange()
            transaction.restore(snapshot)
        }
        afterChange()
        // The restore replaced every record behind the repository's back.
        MoWidgetProvider.refresh(context)
    }

    /** The backups earlier versions left in the chosen folder, or null while no folder is chosen. */
    fun oldBackups(): OldBackups? = synchronized(operationLock) {
        preferences.getString("tree", null)?.let { tree -> OldBackups(tree, generations(tree).oldBackupTidy()) }
    }

    /**
     * Removes what [old] said it would, and nothing it did not show. The records are backed up
     * and verified first: until then one of the files about to go may be their only copy.
     */
    fun tidy(old: OldBackups) = synchronized(operationLock) {
        check(old.folder == preferences.getString("tree", null)) { "The backup folder changed since its old backups were counted" }
        backupNow()
        generations(old.folder).tidy(old.tidy)
    }

    private fun generations(tree: String) = BackupGenerations(folder(Uri.parse(tree)), storedIndex)

    private fun capture(): Pair<Long, BackupSnapshot> = synchronized(PersonalDataLock) {
        transaction.recover()
        preferences.getLong("revision", 0) to storage.readSnapshot()
    }

    private fun enqueueBackup() {
        executor.execute {
            // A previous queued snapshot may already include this change.
            if (status.value.pending || status.value.lastVerifiedAt == null) {
                runCatching { backupNow() }
            }
        }
    }

    private fun pendingStartedAt(): Long =
        if (preferences.getBoolean("pending", true)) {
            preferences.getLong("pending_since", System.currentTimeMillis())
        } else System.currentTimeMillis()

    /** [newFolder] is given when the folder changes: its first generation becomes the whole index in the same commit. */
    private fun recordVerification(revision: Long, tree: Uri? = null, newFolder: List<Generation>? = null) = synchronized(PersonalDataLock) {
        val pending = revision != preferences.getLong("revision", 0)
        val editor = preferences.edit().putString("verified", Instant.now().toString())
            .putBoolean("pending", pending).remove("error")
        if (tree != null) editor.putString("tree", tree.toString())
        if (newFolder != null) editor.putString(PreferencesGenerationIndex.Key, encodeGenerations(newFolder))
        if (pending) editor.putLong("pending_since", pendingStartedAt()) else editor.remove("pending_since")
        check(editor.commit()) { "Cannot save backup verification" }
        publish()
    }

    private fun loadStatus() = BackupStatus(
        configured = preferences.contains("tree"),
        lastVerifiedAt = preferences.getString("verified", null),
        pending = preferences.getBoolean("pending", true),
        error = preferences.getString("error", null)?.let(BackupError::fromStored),
        pendingSinceMillis = if (preferences.contains("pending_since")) preferences.getLong("pending_since", 0) else null,
    )

    private fun publish() { mutableStatus.value = loadStatus() }

    companion object {
        @Volatile private var instance: DurableBackupManager? = null
        fun get(context: Context): DurableBackupManager = instance ?: synchronized(this) {
            instance ?: DurableBackupManager(context.applicationContext).also { instance = it }
        }
    }
}

/** The index in the backup preferences, which Android's own backup leaves out: it describes files on this phone. */
private class PreferencesGenerationIndex(private val preferences: SharedPreferences) : GenerationIndex {
    // An index that cannot be read removes nothing: its files simply stay.
    override fun read(): List<Generation> = preferences.getString(Key, null)
        ?.let { stored -> runCatching { decodeGenerations(stored) }.getOrNull() }.orEmpty()

    override fun write(generations: List<Generation>) {
        check(preferences.edit().putString(Key, encodeGenerations(generations)).commit()) { "Cannot record the kept backups" }
    }

    companion object { const val Key = "generations" }
}

private class MemoryGenerationIndex : GenerationIndex {
    private var generations = emptyList<Generation>()
    override fun read() = generations
    override fun write(generations: List<Generation>) { this.generations = generations }
}

private fun encodeGenerations(generations: List<Generation>): String = JSONArray().apply {
    generations.forEach { put(JSONObject().put("id", it.id).put("writtenAt", it.writtenAt.toString())) }
}.toString()

private fun decodeGenerations(stored: String): List<Generation> = JSONArray(stored).let { array ->
    List(array.length()) { index ->
        array.getJSONObject(index).let { Generation(it.getString("id"), Instant.parse(it.getString("writtenAt"))) }
    }
}

private class DocumentBackupDestination(private val context: Context, private val tree: Uri) : BackupDestination {
    override fun writeNew(name: String, bytes: ByteArray): String {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val document = DocumentsContract.createDocument(context.contentResolver, parent, "application/json", name)
            ?: error("Could not create backup document")
        val descriptor = context.contentResolver.openFileDescriptor(document, "w")
            ?: error("Could not open backup document")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { stream ->
            stream.write(bytes)
            stream.flush()
            stream.fd.sync()
        }
        return childUri(DocumentsContract.getDocumentId(document))
    }

    // One spelling for a document, whether the folder created it or listed it, so the index and a listing can be compared.
    private fun childUri(documentId: String) = DocumentsContract.buildDocumentUriUsingTree(tree, documentId).toString()
    override fun read(id: String): ByteArray = readBounded(context, Uri.parse(id))

    override fun delete(id: String) {
        check(DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(id))) { "Could not remove a backup" }
    }

    override fun list(): List<BackupFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    add(BackupFile(childUri(cursor.getString(0)), cursor.getString(1)))
                }
            }
        } ?: error("Could not list the backup folder")
    }
}

private fun readBounded(context: Context, uri: Uri): ByteArray =
    context.contentResolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(output.size() + count <= BackupArchive.MaxArchiveBytes) { "Backup is too large" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: error("Could not read backup document")

/** Every store as a backup captures it right now, the rows from the database included; for device drills that check a backup against the installation. */
internal fun personalSnapshot(context: Context): BackupSnapshot = synchronized(PersonalDataLock) { AndroidRestoreStorage(context).readSnapshot() }

private class AndroidRestoreStorage(private val context: Context) : BackupRestoreStorage {
    private val journal = AtomicFile(File(context.noBackupFilesDir, "restore-journal.json"))
    private val rows by lazy { SqliteRowStore.personal(context) }

    override fun readSnapshot() = BackupSnapshot(BackupArchive.StoreNames.associateWith { name ->
        if (name == RowsBackupStore.StoreName) {
            val (stored, meta) = rows.snapshot(setOf(PreferencesTrackerRepository.FormatKey))
            return@associateWith RowsBackupStore.values(stored, meta[PreferencesTrackerRepository.FormatKey])
        }
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapValues { (_, value) ->
            value as? String ?: error("Unsupported stored value; backup has not been completed")
        }
    })
    override fun replaceStore(name: String, values: Map<String, String>) {
        if (name == RowsBackupStore.StoreName) {
            val (restored, format) = RowsBackupStore.rows(values)
            // A restored backup is not an import: no document beside it may be taken as spent. It
            // holds syncing until someone decides whether it replaces every copy's rows or this one's.
            rows.replaceAll(restored, mapOf(
                PreferencesTrackerRepository.FormatKey to format,
                PreferencesTrackerRepository.ImportedKey to null,
                SyncMarks.Cursor to null,
                SyncMarks.Pushed to null,
                SyncMarks.Held to "restored",
            ))
            return
        }
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        values.forEach { (key, value) -> editor.putString(key, value) }
        check(editor.commit()) { "Could not restore $name" }
    }
    override fun readJournal(): BackupSnapshot? = try {
        parseSnapshot(journal.openRead().use { it.readBytes() })
    } catch (_: java.io.FileNotFoundException) { null }
    override fun writeJournal(snapshot: BackupSnapshot) { atomicWrite(journal, rawSnapshot(snapshot)) }
    override fun clearJournal() {
        journal.delete()
        check(listOf("", ".bak", ".new").none { File(journal.baseFile.path + it).exists() }) { "Could not finish restore journal" }
    }
    fun saveRescueCopy(snapshot: BackupSnapshot) {
        atomicWrite(AtomicFile(File(context.noBackupFilesDir, "before-restore-${UUID.randomUUID()}.momentum.json")), BackupArchive.encode(snapshot))
    }
}

private fun rawSnapshot(snapshot: BackupSnapshot): ByteArray = JSONObject().apply {
    snapshot.stores.forEach { (name, values) -> put(name, JSONObject(values)) }
}.toString().toByteArray(Charsets.UTF_8)

private fun parseSnapshot(bytes: ByteArray): BackupSnapshot {
    val json = JSONObject(bytes.toString(Charsets.UTF_8))
    return BackupArchive.withAddedStores(json.keys().asSequence().associateWith { name ->
        val store = json.getJSONObject(name)
        store.keys().asSequence().associateWith { key -> store.get(key) as? String ?: error("Invalid restore journal") }
    })
}

private fun atomicWrite(file: AtomicFile, bytes: ByteArray) {
    val stream = file.startWrite()
    try {
        stream.write(bytes)
        stream.fd.sync()
        file.finishWrite(stream)
        check(file.openRead().use { it.readBytes() }.contentEquals(bytes)) {
            "Recovery journal verification failed"
        }
    } catch (failure: Exception) {
        file.failWrite(stream)
        throw failure
    }
}
