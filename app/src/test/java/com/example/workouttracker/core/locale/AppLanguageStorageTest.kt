package com.example.workouttracker.core.locale

import android.content.Context
import androidx.core.app.AppLocalesStorageHelper
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Below API 33 AppCompat keeps the choice in its own file and only applies it to activities, so
 * a notification or the widget would speak the phone's language unless it asks for the app's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppLanguageStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun startAsANewProcess() = forgetChoiceMadeInThisProcess()

    @Test
    fun workOutsideAnActivitySpeaksTheStoredLanguage() {
        AppLocalesStorageHelper.persistLocales(context, "de")

        assertEquals(AppLanguage.German, currentAppLanguage(context))
        assertEquals("Gewicht", context.withAppLanguage().getString(R.string.feature_weight))
    }

    @Test
    fun followingThePhoneLeavesTheContextAsItIs() {
        AppLocalesStorageHelper.persistLocales(context, "")

        assertEquals(AppLanguage.System, currentAppLanguage(context))
        assertEquals("Weight", context.withAppLanguage().getString(R.string.feature_weight))
    }

    /**
     * Without an activity AppCompat never writes the choice, which is exactly the moment before its
     * background write lands: Settings, the channel and the widget must already see the new one.
     */
    @Test
    fun aChoiceIsReadBackBeforeAppCompatHasStoredIt() {
        AppLocalesStorageHelper.persistLocales(context, "")

        applyAppLanguage(AppLanguage.German)
        assertEquals(AppLanguage.German, currentAppLanguage(context))
        assertEquals("Gewicht", context.withAppLanguage().getString(R.string.feature_weight))

        AppLocalesStorageHelper.persistLocales(context, "de")
        applyAppLanguage(AppLanguage.System)
        assertEquals(AppLanguage.System, currentAppLanguage(context))
        assertEquals("Weight", context.withAppLanguage().getString(R.string.feature_weight))
    }
}
