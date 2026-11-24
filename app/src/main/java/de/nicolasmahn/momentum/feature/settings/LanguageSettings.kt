package de.nicolasmahn.momentum.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.locale.AppLanguage
import de.nicolasmahn.momentum.core.locale.applyAppLanguage
import de.nicolasmahn.momentum.core.locale.currentAppLanguage
import de.nicolasmahn.momentum.core.ui.MomentumDropdown
import java.util.Locale

/**
 * The app language. Android holds the choice, so the phone's own app-language screen can change it
 * while this is open: it is read again on every configuration change rather than kept here.
 *
 * The field's label names the group, so it has no title of its own.
 */
@Composable
internal fun LanguageSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Returning to the phone's language may change no configuration at all when it already matches
    // the chosen one, so the tapped choice is also shown directly.
    var selected by remember(configuration) { mutableStateOf(currentAppLanguage(context)) }

    MomentumDropdown(
        label = stringResource(R.string.settings_language_title),
        options = AppLanguage.entries,
        selected = selected,
        onSelect = { language ->
            if (language != selected) {
                selected = language
                applyAppLanguage(language)
            }
        },
        optionLabel = { language -> language.ownName ?: stringResource(R.string.settings_language_system) },
        modifier = modifier.fillMaxWidth(),
        testTag = "settings_language",
        optionTestTag = { language -> "settings_language_${language.name.lowercase(Locale.ROOT)}" },
    )
}
