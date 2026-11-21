package com.example.workouttracker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import com.example.workouttracker.R
import com.example.workouttracker.app.MainActivity
import com.example.workouttracker.core.locale.withAppLanguage
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.theme.MomentumDarkBackground
import com.example.workouttracker.domain.mo.moMoodWithStoredChoices
import com.example.workouttracker.feature.mo.description
import com.example.workouttracker.feature.mo.drawFreezeCube
import com.example.workouttracker.feature.mo.drawStreakCoin
import com.example.workouttracker.feature.mo.moPicture
import com.example.workouttracker.feature.mo.renderMoIcon
import com.example.workouttracker.feature.mo.renderMoBitmap
import com.example.workouttracker.feature.mo.summary
import com.example.workouttracker.persistence.PreferencesFeatureVisibilityRepository
import com.example.workouttracker.persistence.PreferencesReminderSettingsRepository
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.core.time.habitDate
import com.example.workouttracker.reminder.currentBedtime
import com.example.workouttracker.reminder.moRewardInputsSource
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Mo on the home screen, where he can go cold while the app stays closed. He is a still picture of
 * the same drawing the app animates: widgets cannot run free animation, and a still one costs no
 * battery. He redraws whenever records are saved, whenever one of his check-ins speaks, and on
 * his own quarter-hour tick, because how long a task has waited changes his face with no save at
 * all. The system's periodic update is too inexact to rely on for that.
 */
class MoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        if (widgetIds.isEmpty()) return
        // Opening the store can fail, for instance while a restore is unfinished. The app shows a
        // retry screen for that; a widget broadcast must never take the process down over it, so
        // the widget keeps what it shows until the next update. The log is how a Mo that differs
        // from Home's can be traced to a redraw that failed.
        runCatching { moWidgetViews(context) }
            .onSuccess { views -> widgetIds.forEach { manager.updateAppWidget(it, views) } }
            .onFailure { Log.w(WidgetLogTag, "Mo widget redraw failed; keeping the last picture", it) }
        runCatching { scheduleTick(context) }
            .onFailure { Log.w(WidgetLogTag, "Mo widget tick could not be scheduled", it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // The phone's language, or from API 33 this app's own language, changed while the app was closed.
            ActionTick, Intent.ACTION_LOCALE_CHANGED -> updateAll(context.applicationContext)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    internal companion object {
        private const val ActionTick = "com.example.workouttracker.widget.MO_WIDGET_TICK"

        /**
         * A non-waking alarm: it never wakes a sleeping phone, and it is delivered as soon as the
         * phone wakes for anything else, so he is current by the time anyone looks at him. A plain
         * inexact alarm may drift by most of the quarter hour; without exact-alarm access Android
         * widens this window to ten minutes, which is still far tighter.
         */
        private fun scheduleTick(context: Context) {
            val at = nextMoWidgetTick(ZonedDateTime.now()).toInstant().toEpochMilli()
            context.getSystemService(AlarmManager::class.java)
                ?.setWindow(AlarmManager.RTC, at, TickWindowMillis, tickIntent(context))
        }

        private const val TickWindowMillis = 60_000L

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, MoWidgetProvider::class.java).setAction(ActionTick),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * Redraws now, for a broadcast receiver: whatever it posts may never run once onReceive
         * returns, because Android can end the process. Call it on the main thread, outside the
         * storage lock.
         */
        fun updateNow(context: Context) {
            runCatching { updateAll(context.applicationContext) }
        }

        /** Safe to call from anywhere, including while the storage lock is held: it only posts. */
        fun refresh(context: Context) {
            val appContext = context.applicationContext
            Handler(Looper.getMainLooper()).post { updateAll(appContext) }
        }

        private fun updateAll(appContext: Context) {
            val manager = AppWidgetManager.getInstance(appContext) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(appContext, MoWidgetProvider::class.java))
            MoWidgetProvider().onUpdate(appContext, manager, ids)
        }
    }
}

private fun moWidgetViews(context: Context): RemoteViews {
    val now = ZonedDateTime.now()
    val today = habitDate(now)
    val rewards = moRewardInputsSource(context).load(today)
    val mood = moMoodWithStoredChoices(
        state = PreferencesTrackerRepository(context).load(),
        featureVisibility = PreferencesFeatureVisibilityRepository(context).load(),
        today = today,
        holidays = rewards.holidays,
        storedChoices = rewards.choices,
        rewardStart = rewards.rewardStart,
    )
    // The quarter-hour tick is what lets him turn red on the home screen as the day runs late.
    val reminders = PreferencesReminderSettingsRepository(context).load()
    val look = moPicture(
        mood = mood,
        reminders = reminders,
        now = LocalDateTime.of(today, now.toLocalTime()),
        bedtime = context.currentBedtime(reminders, now),
    ).look
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val words = context.withAppLanguage().resources
    return RemoteViews(context.packageName, R.layout.mo_widget).apply {
        // The widget's own background is the app's dark background, so the Husk's hollow matches it.
        setImageViewBitmap(
            R.id.mo_widget_figure,
            renderMoBitmap(context, look, FIGURE_HEIGHT_DP, ground = MomentumDarkBackground),
        )
        setContentDescription(R.id.mo_widget_figure, mood.state.description().resolve(words))
        setImageViewBitmap(R.id.mo_widget_coin, renderMoIcon(context, SCORE_ICON_DP) { drawStreakCoin() })
        setImageViewBitmap(R.id.mo_widget_cube, renderMoIcon(context, SCORE_ICON_DP) { drawFreezeCube() })
        setTextViewText(R.id.mo_widget_streak, mood.streak.days.toString())
        setTextViewText(R.id.mo_widget_freezes, mood.streak.freezes.toString())
        setContentDescription(R.id.mo_widget_score, mood.streak.summary().resolve(words))
        setOnClickPendingIntent(R.id.mo_widget_root, open)
    }
}

/** The next quarter hour, when a waiting task may have crossed the minutes that change his face. */
internal fun nextMoWidgetTick(now: ZonedDateTime): ZonedDateTime {
    val hour = now.truncatedTo(ChronoUnit.HOURS)
    val quarters = now.minute / 15 + 1
    return hour.plusMinutes(quarters * 15L)
}

private const val WidgetLogTag = "MomentumWidget"
private const val FIGURE_HEIGHT_DP = 170f
private const val SCORE_ICON_DP = 22f
