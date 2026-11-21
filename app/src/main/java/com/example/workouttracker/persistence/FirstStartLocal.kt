package com.example.workouttracker.persistence

import android.content.Context
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.settings.NewInstallationVisibility

/**
 * Where this installation stands with its first start: begun, which holds until it is answered
 * however much is set up meanwhile, or answered. Neither is a record, so neither is backed up nor
 * synced: a restored phone has records or views, which keeps the first start away by itself
 * ([waitsFor]).
 */
internal class FirstStartLocal(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    /**
     * Whether the first start comes before Home: begun and not answered, or never answered here
     * with nothing made, recorded or turned on yet. Anything of that means someone has been here,
     * as on a restored phone. A first start found waiting is marked begun, so what is set up in it
     * never ends it before it is answered, across a rotation or a restart; a backup restored in
     * it brings records, which answer it.
     */
    fun waitsFor(state: AppState, visibility: FeatureVisibilitySettings): Boolean {
        if (preferences.getBoolean(AnsweredKey, false)) return false
        if (preferences.getBoolean(BegunKey, false)) {
            // The first start shows only Settings, where nothing is recorded, so records found
            // now came from a restored backup: that installation was set up long ago.
            if (!state.hasRecords) return true
            answer()
            return false
        }
        val waits = state.isUntouched() && visibility == NewInstallationVisibility
        if (waits) preferences.edit().putBoolean(BegunKey, true).apply()
        return waits
    }

    fun answer() {
        preferences.edit().putBoolean(AnsweredKey, true).remove(BegunKey).apply()
    }

    private companion object {
        const val PreferencesName = "momentum_first_start"
        const val AnsweredKey = "answered"
        const val BegunKey = "begun"
    }
}

/** Nothing made and nothing recorded: what a new installation holds. */
internal fun AppState.isUntouched(): Boolean =
    checklists.isEmpty() && measurements.isEmpty() && questions.isEmpty() && time.isEmpty() &&
        activity?.activities.isNullOrEmpty() && !hasRecords

/** Whether anything at all has been recorded, of any view. */
internal val AppState.hasRecords: Boolean
    get() = measurementLogs.isNotEmpty() || sessions.isNotEmpty() || activityEntries.isNotEmpty() || checklistDays.isNotEmpty() ||
        questionEntries.isNotEmpty() || timeLogs.isNotEmpty() || substanceLogs.isNotEmpty() || substanceNoneDays.isNotEmpty()
