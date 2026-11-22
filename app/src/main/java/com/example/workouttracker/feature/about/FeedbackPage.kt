package com.example.workouttracker.feature.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.feature.settings.SettingsLinkRow

/** Where feedback goes: the repository's issues on GitHub, or the address in the legal notice. */
private const val NewIssueUrl = "https://github.com/NicolasMahn/momentum/issues/new"
private const val FeedbackAddress = "momentum@nicolas-mahn.de"

/**
 * The two ways to reach the developer. Momentum has no internet permission, so both hand a prepared
 * text to another app, the browser or the mail app, and the person sends it from there or not at all.
 * Both texts carry the version and the phone, which a problem is found by, and nothing recorded.
 */
@Composable
internal fun FeedbackPage() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val facts = remember(context) { context.phoneFacts() }
    val issueBody = stringResource(R.string.feedback_issue_body, facts)
    val mailSubject = stringResource(R.string.feedback_mail_subject)
    val mailBody = stringResource(R.string.feedback_mail_body, facts)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsLinkRow(
            icon = Icons.Rounded.BugReport,
            title = stringResource(R.string.feedback_issue_title),
            summary = stringResource(R.string.feedback_issue_summary),
            // A phone without a browser has nowhere to open it; the page then stays as it is.
            onClick = { runCatching { uriHandler.openUri("$NewIssueUrl?body=${Uri.encode(issueBody)}") } },
            testTag = "feedback_issue",
        )
        SettingsLinkRow(
            icon = Icons.Rounded.MailOutline,
            title = stringResource(R.string.feedback_mail_title),
            summary = stringResource(R.string.feedback_mail_summary),
            onClick = { context.openMailDraft(mailSubject, mailBody) },
            testTag = "feedback_mail",
        )
        Text(
            text = stringResource(R.string.feedback_facts_note),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Version and phone, one line each, as they are added to a report. */
private fun Context.phoneFacts(): String {
    val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    return "Momentum $version\nAndroid ${Build.VERSION.RELEASE}\n${Build.MANUFACTURER} ${Build.MODEL}"
}

private fun Context.openMailDraft(subject: String, body: String) {
    val draft = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FeedbackAddress))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    // A phone without a mail app has nowhere to open it; the address is in the legal notice.
    try {
        startActivity(draft)
    } catch (_: ActivityNotFoundException) {
    }
}
