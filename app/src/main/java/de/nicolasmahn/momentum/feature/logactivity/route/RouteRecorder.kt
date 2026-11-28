package de.nicolasmahn.momentum.feature.logactivity.route

import android.content.Context
import android.util.Log
import de.nicolasmahn.momentum.domain.activity.route.LocationFix
import de.nicolasmahn.momentum.domain.activity.route.RouteRecording
import de.nicolasmahn.momentum.persistence.json.decodeRecordingJournal
import de.nicolasmahn.momentum.persistence.json.encodeJournal
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one route recording of the app, whichever activity it is of. The screen starts, pauses,
 * resumes, saves and discards it; [RouteRecordingService] feeds it the phone's fixes while it runs.
 *
 * Every kept point and every start, pause and resume is written to a file at once, so a recording outlives the app being stopped by the
 * system. A recording read back from that file is paused: nothing was measuring while the app was
 * gone, and only the person can say whether to go on.
 */
internal class RouteRecorder private constructor(private val journal: File) {
    /** Where the next version of [journal] is written before it takes the file's place. */
    private val nextJournal = File(journal.path + ".new")

    private val current = MutableStateFlow(readJournal())

    val recording: StateFlow<RouteRecording?> = current.asStateFlow()

    private val lastFix = MutableStateFlow<Long?>(null)

    /** When the phone last reported a fix good enough to keep, for the "looking for GPS" line. */
    val lastFixMillis: StateFlow<Long?> = lastFix.asStateFlow()

    private val unsaved = MutableStateFlow(false)

    /** Whether the last change could not be written to the file: the recording then lives only while the app does. */
    val unsavedOnPhone: StateFlow<Boolean> = unsaved.asStateFlow()

    /** Starts recording [activityId]; refused while another recording waits to be saved or discarded. */
    fun start(activityId: String, nowMillis: Long): Boolean {
        if (current.value != null) return false
        lastFix.value = null
        update(RouteRecording(activityId = activityId, startedAtMillis = nowMillis))
        return true
    }

    fun take(fix: LocationFix) {
        val recording = current.value ?: return
        if (fix.accuracyMeters <= RouteRecording.MaxAccuracyMeters) lastFix.value = fix.timeMillis
        val taken = recording.taking(fix)
        // What only steers the judging of fixes is not written; a kept point is.
        if (taken.route != recording.route) update(taken) else current.value = taken
    }

    fun pause() {
        current.value?.takeUnless { it.paused }?.let { update(it.pausing()) }
    }

    fun resume() {
        current.value?.takeIf { it.paused }?.let {
            lastFix.value = null
            update(it.resuming())
        }
    }

    /** Lets go of the recording: after it was saved as an entry, or when the person discards it. */
    fun clear() {
        current.value = null
        lastFix.value = null
        unsaved.value = false
        journal.delete()
        nextJournal.delete()
    }

    @Synchronized
    private fun update(recording: RouteRecording) {
        current.value = recording
        unsaved.value = runCatching { writeSynced(recording.encodeJournal().toByteArray(Charsets.UTF_8)) }
            .onFailure { Log.w(Tag, "The recording could not be written to its file", it) }
            .isFailure
    }

    /**
     * Writes the file whole or not at all: a new file beside it, synced to the disk, then renamed
     * over it. Unlike AtomicFile, which only logs a failed sync, every failure here throws, so the
     * panel can say the recording is not kept.
     */
    private fun writeSynced(bytes: ByteArray) {
        FileOutputStream(nextJournal).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        check(nextJournal.renameTo(journal)) { "The recording's file could not take the new version" }
    }

    private fun readJournal(): RouteRecording? = runCatching {
        if (!journal.exists()) null else decodeRecordingJournal(journal.readText(Charsets.UTF_8))
    }.onFailure { Log.w(Tag, "The recording's file could not be read", it) }
        .getOrNull()
        ?.let { if (it.paused) it else it.pausing() }

    companion object {
        private const val Tag = "RouteRecorder"

        @Volatile
        private var instance: RouteRecorder? = null

        fun of(context: Context): RouteRecorder = instance ?: synchronized(this) {
            // Not in a backup: a recording is no record until it is saved, and restores carry records.
            instance ?: RouteRecorder(File(context.applicationContext.noBackupFilesDir, "route-recording.json")).also { instance = it }
        }
    }
}
