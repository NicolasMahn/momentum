package com.example.workouttracker.feature.logactivity.route

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.example.workouttracker.domain.activity.route.LocationFix
import com.example.workouttracker.domain.activity.route.RouteRecording
import com.example.workouttracker.persistence.json.decodeRecordingJournal
import com.example.workouttracker.persistence.json.encodeJournal
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one route recording of the app, whichever activity it is of. The screen starts, pauses,
 * resumes, saves and discards it; [RouteRecordingService] feeds it the phone's fixes while it runs.
 *
 * Every change is written to a file at once, so a recording outlives the app being stopped by the
 * system. A recording read back from that file is paused: nothing was measuring while the app was
 * gone, and only the person can say whether to go on.
 */
internal class RouteRecorder private constructor(private val journal: AtomicFile) {
    private val current = MutableStateFlow(readJournal())

    val recording: StateFlow<RouteRecording?> = current.asStateFlow()

    private val lastFix = MutableStateFlow<Long?>(null)

    /** When the phone last reported a fix good enough to keep, for the "looking for GPS" line. */
    val lastFixMillis: StateFlow<Long?> = lastFix.asStateFlow()

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
        if (taken != recording) update(taken)
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
        journal.delete()
    }

    @Synchronized
    private fun update(recording: RouteRecording) {
        current.value = recording
        runCatching {
            val stream = journal.startWrite()
            runCatching { stream.write(recording.encodeJournal().toByteArray(Charsets.UTF_8)) }
                .onSuccess { journal.finishWrite(stream) }
                .onFailure { journal.failWrite(stream) }
                .getOrThrow()
        }.onFailure { Log.w(Tag, "The recording could not be written to its file", it) }
    }

    private fun readJournal(): RouteRecording? = runCatching {
        if (!journal.baseFile.exists()) null else decodeRecordingJournal(journal.readFully().toString(Charsets.UTF_8))
    }.onFailure { Log.w(Tag, "The recording's file could not be read", it) }
        .getOrNull()
        ?.let { if (it.paused) it else it.pausing() }

    companion object {
        private const val Tag = "RouteRecorder"

        @Volatile
        private var instance: RouteRecorder? = null

        fun of(context: Context): RouteRecorder = instance ?: synchronized(this) {
            // Not in a backup: a recording is no record until it is saved, and restores carry records.
            instance ?: RouteRecorder(AtomicFile(File(context.applicationContext.noBackupFilesDir, "route-recording.json"))).also { instance = it }
        }
    }
}
