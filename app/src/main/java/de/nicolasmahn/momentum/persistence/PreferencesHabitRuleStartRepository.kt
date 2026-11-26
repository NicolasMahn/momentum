package de.nicolasmahn.momentum.persistence

import android.content.Context
import de.nicolasmahn.momentum.widget.MoWidgetProvider
import java.time.LocalDate

/** The rules start when this installation first opens them, never on an earlier hard-coded date. */
class PreferencesHabitRuleStartRepository internal constructor(
    private val store: TrackerStringStore,
    private val onWritten: () -> Unit = {},
) {
    constructor(context: Context) : this(
        personalStringStore(context, PreferencesTrackerRepository.PREFERENCES_NAME),
        onWritten = { MoWidgetProvider.refresh(context) },
    )

    fun loadOrStart(today: LocalDate): LocalDate = synchronized(de.nicolasmahn.momentum.persistence.backup.PersonalDataLock) {
        store.read(SettingsKey)?.let(::parseHabitRuleStart)?.let { return@synchronized it }
        check(store.write(SettingsKey, today.toString())) { "Could not save the habit rule start date" }
        onWritten()
        today
    }

    internal companion object { const val SettingsKey = "habit_rules_started_v1" }
}

internal fun parseHabitRuleStart(value: String): LocalDate {
    require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
    return LocalDate.parse(value)
}
