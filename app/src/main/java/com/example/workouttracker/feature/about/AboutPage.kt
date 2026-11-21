package com.example.workouttracker.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumCard

/**
 * What Settings' last entry opens: the version a bug report names, the privacy policy, who offers
 * the app, and every open-source component the app ships with the license it comes under. A component opens its page
 * through [onOpenComponent]. The page holds nothing, so leaving it asks nothing.
 */
@Composable
internal fun AboutPage(onOpenPrivacy: () -> Unit, onOpenLegalNotice: () -> Unit, onOpenComponent: (OpenSourceComponent) -> Unit) {
    val context = LocalContext.current
    // Read where it is shown: the list is a few kilobytes inside the app, opened once for the page.
    val components = remember(context) { context.openSourceComponents() }
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (version != null) {
            // Selectable, so a tester can copy it into a report.
            SelectionContainer {
                Text(
                    text = stringResource(R.string.about_version, version),
                    modifier = Modifier.testTag("about_version"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        AboutRow(title = stringResource(R.string.about_privacy_title), detail = null, onClick = onOpenPrivacy, testTag = "about_privacy")
        AboutRow(title = stringResource(R.string.about_legal_notice_title), detail = null, onClick = onOpenLegalNotice, testTag = "about_legal_notice")
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.about_licenses_title), style = MaterialTheme.typography.titleMedium)
        components.forEachIndexed { index, component ->
            AboutRow(component.name, component.license.name, onClick = { onOpenComponent(component) }, testTag = "about_component_$index")
        }
    }
}

@Composable
private fun AboutRow(title: String, detail: String?, onClick: () -> Unit, testTag: String) {
    MomentumCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), testTag = testTag) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/**
 * One component: its name, its license's name, what its authors ask to be said, where it comes
 * from, and the full text of its license. A page and not a dialog or an expanding row: a license
 * runs to hundreds of lines, which need the whole screen and its scrolling, and Back leads to the
 * list again. A name that is not in the list shows nothing.
 */
@Composable
internal fun LicensePage(componentName: String) {
    val context = LocalContext.current
    val component = remember(context, componentName) { context.openSourceComponents().firstOrNull { it.name == componentName } } ?: return
    val paragraphs = remember(context, component) { licenseParagraphs(context.licenseText(component.license)) }
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = component.name, modifier = Modifier.testTag("license_component"), style = MaterialTheme.typography.titleLarge)
        Text(text = component.license.name, style = MaterialTheme.typography.titleMedium)
        if (component.notice != null) {
            Text(text = component.notice, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("license_notice"))
        }
        MomentumButton(
            text = stringResource(R.string.about_license_source),
            // A phone without a browser has nowhere to open it; the page then stays as it is.
            onClick = { runCatching { uriHandler.openUri(component.source) } },
            variant = MomentumButtonVariant.Secondary,
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            testTag = "license_source",
        )
        Spacer(Modifier.height(4.dp))
        // One text for each paragraph, so TalkBack reads a license in steps and not as one node.
        paragraphs.forEachIndexed { index, paragraph ->
            Text(
                text = paragraph,
                modifier = Modifier.testTag("license_paragraph_$index"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
