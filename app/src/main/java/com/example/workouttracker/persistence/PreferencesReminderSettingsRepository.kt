package com.example.workouttracker.persistence

import android.content.Context
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.persistence.backup.PersonalDataLock
import com.example.workouttracker.widget.MoWidgetProvider

interface ReminderSettingsRepository {
    fun load(): ReminderSettings

    fun save(settings: ReminderSettings): Boolean
}

class PreferencesReminderSettingsRepository internal constructor(
    private val store: TrackerStringStore,
    private val onWritten: () -> Unit = {},
) : ReminderSettingsRepository {
    // When a reminder asks decides how long a task has waited, and so Mo's face on the widget.
    constructor(context: Context) : this(
        personalStringStore(context, PREFERENCES_NAME),
        onWritten = { MoWidgetProvider.refresh(context) },
    )

    override fun load(): ReminderSettings = synchronized(PersonalDataLock) {
        loadLocked()
    }

    private fun loadLocked(): ReminderSettings =
        runCatching { store.read(SETTINGS_KEY)?.let(ReminderSettingsCodec::decode) }.getOrNull()
            ?: ReminderSettings()

    override fun save(settings: ReminderSettings): Boolean = synchronized(PersonalDataLock) {
        runCatching { store.write(SETTINGS_KEY, ReminderSettingsCodec.encode(settings)) }.getOrDefault(false).also { if (it) onWritten() }
    }

    internal companion object {
        const val PREFERENCES_NAME = "momentum_reminders"

        const val SETTINGS_KEY = "settings_v13"
    }
}
