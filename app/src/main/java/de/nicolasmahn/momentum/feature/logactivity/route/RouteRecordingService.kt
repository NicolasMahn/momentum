package de.nicolasmahn.momentum.feature.logactivity.route

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.app.activityDestinationPendingIntent
import de.nicolasmahn.momentum.core.locale.locale
import de.nicolasmahn.momentum.core.locale.withAppLanguage
import de.nicolasmahn.momentum.domain.activity.route.LocationFix
import de.nicolasmahn.momentum.domain.activity.route.RouteRecording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the GPS on while a route is recorded, with the screen off and the app in the background:
 * Android lets only a foreground service with a visible notification do that. It runs exactly
 * while the recorder holds a recording that is not paused, and stops itself as soon as it does
 * not, so a pause also saves the battery.
 *
 * It asks for nothing beyond location while the app is in use: the person starts it from the
 * screen, and that is what lets it go on in the background. If the system stops it, it is not
 * started again on its own; the recording waits, paused, for the person to come back.
 */
internal class RouteRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recorder by lazy { RouteRecorder.of(this) }
    private var listening = false
    private var shownText: String? = null

    // Not the platform's LocationListener: before Android 11 it has no default for the provider
    // callbacks, and switching location off mid-run would end the app.
    private val listener = LocationListenerCompat { location ->
        // A fix the system kept from before, such as where the phone last had one, says nothing about now.
        if (location.ageMillis() <= MaxFixAgeMillis) recorder.take(location.toFix())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recording = recorder.recording.value
        createChannel(this)
        // Android ends the app unless a service started this way goes to the foreground, even one
        // that has nothing to do: a pause tapped right after Start can get here first. Where it
        // cannot go there, because location was taken away meanwhile, Android may still end the
        // app; the recording is paused first, so it comes back with every point.
        val promoted = runCatching {
            ServiceCompat.startForeground(this, NotificationId, notification(recording ?: RouteRecording("", 0L)), foregroundType())
        }.isSuccess
        if (!promoted || recording == null || recording.paused || !hasLocationPermission(this)) {
            recorder.pause()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!listening) {
            listening = listen()
            if (!listening) {
                recorder.pause()
                stopSelf()
                return START_NOT_STICKY
            }
            scope.launch {
                recorder.recording.collect { current ->
                    if (current == null || current.paused) stopSelf() else show(current)
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Asks for GPS fixes; false where the phone refuses, such as a GPS switched off since the screen looked. */
    @SuppressLint("MissingPermission") // Asked for in onStartCommand, which stops without it.
    private fun listen(): Boolean = runCatching {
        getSystemService(LocationManager::class.java)
            .requestLocationUpdates(LocationManager.GPS_PROVIDER, FixIntervalMillis, 0f, listener, Looper.getMainLooper())
    }.isSuccess

    /** The notification follows the recording, but only when what it says changes: once a minute or so, not on every fix. */
    private fun show(recording: RouteRecording) {
        val text = notificationText(recording)
        if (text == shownText) return
        getSystemService(NotificationManager::class.java).notify(NotificationId, notification(recording))
    }

    private fun notificationText(recording: RouteRecording): String {
        val context = withAppLanguage()
        val minutes = recording.route.movingSeconds / 60
        return context.getString(
            R.string.activity_route_notification_text,
            formatKilometers(recording.route.distanceMeters, context.resources.locale),
            context.resources.getQuantityString(R.plurals.activity_route_minutes, minutes.toInt(), minutes.toInt()),
        )
    }

    private fun notification(recording: RouteRecording): Notification {
        val context = withAppLanguage()
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_workout)
            .setContentTitle(context.getString(R.string.activity_route_notification_title))
            .setContentText(notificationText(recording).also { shownText = it })
            .setContentIntent(activityDestinationPendingIntent(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        if (listening) getSystemService(LocationManager::class.java).removeUpdates(listener)
        super.onDestroy()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0

    companion object {
        private const val ChannelId = "route_recording"
        private const val NotificationId = 4_210
        private const val FixIntervalMillis = 1_000L
        private const val MaxFixAgeMillis = 10_000L

        /** Starts measuring the recording the recorder holds; the screen calls it on start and on resume, while it is in front. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RouteRecordingService::class.java))
        }

        /** Whether the phone has GPS at all; without it there is nothing to record a route with. */
        fun phoneHasGps(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Whether the phone's location is switched on at all; without it no fix ever comes. */
        fun locationIsOn(context: Context): Boolean =
            context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)

        /** Each recording creates it again, which renames it after a change of the app language. */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val resources = context.withAppLanguage().resources
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(ChannelId, resources.getString(R.string.activity_route_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = resources.getString(R.string.activity_route_channel_description)
                },
            )
        }
    }
}

private fun Location.ageMillis(): Long = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000

/** When the fix was measured, on the wall clock the recording counts by: fixes can arrive batched. */
private fun Location.toFix(): LocationFix =
    LocationFix(latitude = latitude, longitude = longitude, accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE, timeMillis = System.currentTimeMillis() - ageMillis())
