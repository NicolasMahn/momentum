package com.example.workouttracker.persistence.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.settings.NewInstallationVisibility
import com.example.workouttracker.persistence.FeatureVisibilitySettingsCodec
import com.example.workouttracker.persistence.PreferencesFeatureVisibilityRepository
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A restore can bring back no records at all. The first load after it seeds the records as a new
 * installation's, and the visibility the archive held stays as it was restored; only where the
 * archive held none either does it start as a new installation's, Substances off beside Statistics.
 */
@RunWith(RobolectricTestRunner::class)
class RestoredVisibilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // The manager is one per process and keeps the application it was first built with; each test
    // here has an application of its own, so the manager is built again for it.
    @Before fun managerOfThisApplication() {
        DurableBackupManager::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
    }

    private fun restore(visibility: Map<String, String>) = DurableBackupManager.get(context).restore(
        BackupSnapshot(mapOf("workout_tracker" to emptyMap(), "momentum_reminders" to emptyMap(), "momentum_feature_visibility" to visibility)),
    )

    private fun storedVisibility(): FeatureVisibilitySettings? =
        context.getSharedPreferences(PreferencesFeatureVisibilityRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PreferencesFeatureVisibilityRepository.SETTINGS_KEY, null)
            ?.let(FeatureVisibilitySettingsCodec::decode)

    @Test
    fun aRestoreWithoutRecordsKeepsTheVisibilityItRestored() {
        restore(mapOf(PreferencesFeatureVisibilityRepository.SETTINGS_KEY to """{"version":7,"features":{"activity":true}}"""))

        PreferencesTrackerRepository(context).load()

        assertEquals(FeatureVisibilitySettings(disabledFeatures = setOf(DashboardFeature.Activity)), storedVisibility())
    }

    @Test
    fun aRestoreOfNothingStartsAsANewInstallation() {
        restore(emptyMap())

        PreferencesTrackerRepository(context).load()

        assertEquals(NewInstallationVisibility, storedVisibility())
    }
}
