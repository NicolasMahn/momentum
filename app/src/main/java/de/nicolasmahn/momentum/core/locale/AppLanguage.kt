package de.nicolasmahn.momentum.core.locale

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The languages Settings offers, in its order. A new language is one entry here, beside its
 * translated `values-<tag>` resources; a test keeps the two in step, so Settings offers exactly what
 * the phone's own app-language screen does.
 *
 * Android owns the choice itself (AppCompat stores it below API 33), so that screen and this
 * setting always agree, and [System] clears the app-specific override instead of storing a
 * language of its own.
 */
enum class AppLanguage(internal val tag: String?) {
    System(null),
    English("en"),
    German("de"),
    ;

    /**
     * The language's name in itself ("Deutsch", "English"), so a reader who cannot read the current
     * language still finds their own. Null for [System], which has no language of its own and is
     * worded in the current one.
     */
    val ownName: String?
        get() = tag?.let(Locale::forLanguageTag)?.let { locale ->
            locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
        }
}

/** The language Android currently holds for this app. */
fun currentAppLanguage(context: Context): AppLanguage = appLanguageOf(appLocales(context))

/** Call on the main thread. Open screens recompose in the new language; nothing is recreated. */
fun applyAppLanguage(language: AppLanguage) {
    val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
    if (Build.VERSION.SDK_INT < 33) chosenInThisProcess = locales
    AppCompatDelegate.setApplicationLocales(locales)
}

/**
 * Below API 33 AppCompat applies a new choice to its activities at once but writes it to storage
 * on a background thread, so Settings, the notification channel and the widget, which read that
 * storage, would briefly see the previous language. Only this app can change the choice there
 * (the phone's app-language screen starts at API 33), so the choice made in this process is the
 * truth until the process ends, by which time storage holds it too.
 */
@Volatile
private var chosenInThisProcess: LocaleListCompat? = null

/** Starts over as a new process would, reading only what storage holds. */
@VisibleForTesting
internal fun forgetChoiceMadeInThisProcess() {
    chosenInThisProcess = null
}

private fun appLocales(context: Context): LocaleListCompat =
    chosenInThisProcess.takeIf { Build.VERSION.SDK_INT < 33 }
        ?: LocaleManagerCompat.getApplicationLocales(context)

/** A language picked in the phone's settings that Momentum does not offer reads as following the phone. */
internal fun appLanguageOf(locales: LocaleListCompat): AppLanguage {
    val language = locales[0]?.language ?: return AppLanguage.System
    return AppLanguage.entries.firstOrNull { it.tag == language } ?: AppLanguage.System
}

/**
 * A context that speaks the chosen app language, for work outside an activity: notifications,
 * the widget and their receivers. From API 33 Android applies the app language to the whole
 * process; below it AppCompat only reaches its activities, so this applies the stored choice.
 */
fun Context.withAppLanguage(): Context {
    if (Build.VERSION.SDK_INT >= 33) return this
    val locales = appLocales(this)
    if (locales.isEmpty) return this
    val configuration = Configuration(resources.configuration)
    ConfigurationCompat.setLocales(configuration, locales)
    return createConfigurationContext(configuration)
}

/** The locale these resources speak, for formatting dates and numbers to match their words. */
val Resources.locale: Locale
    get() = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()

/** The locale the screen speaks. Reading it recomposes the caller when the language changes. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
