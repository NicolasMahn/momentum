package de.nicolasmahn.momentum.feature.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumCheckboxRow
import de.nicolasmahn.momentum.core.ui.MomentumChoiceTile
import de.nicolasmahn.momentum.core.ui.MomentumChoiceTileRow
import de.nicolasmahn.momentum.core.ui.ReservedMessage
import kotlinx.coroutines.launch

/** The address in the legal notice, for what someone would rather not post publicly. */
private const val FeedbackAddress = "momentum@nicolas-mahn.de"

/**
 * Feedback written in the app and posted, on Send, as a public issue on GitHub through Momentum's
 * server, so nobody needs an account of their own. The version and phone go along unless the
 * person unticks them; nothing recorded ever does. The draft survives rotation and leaving the
 * page by Back is free, since a message not sent was never anyone's record.
 */
@Composable
internal fun FeedbackPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val facts = remember(context) { context.phoneFacts() }
    val language = LocalConfiguration.current.locales[0].toLanguageTag()

    var kind by rememberSaveable { mutableStateOf(FeedbackKind.Problem) }
    var text by rememberSaveable { mutableStateOf("") }
    var withFacts by rememberSaveable { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var outcome by rememberSaveable { mutableStateOf<FeedbackOutcome?>(null) }

    val outcomes = FeedbackOutcome.entries.associateWith { stringResource(it.message) }
    val mailSubject = stringResource(R.string.feedback_mail_subject)
    val mailBody = stringResource(R.string.feedback_mail_body, facts)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MomentumChoiceTileRow {
            FeedbackKind.entries.forEach { option ->
                MomentumChoiceTile(
                    label = stringResource(option.label),
                    selected = kind == option,
                    onClick = { kind = option },
                    modifier = Modifier.weight(1f),
                    testTag = "feedback_kind_${option.wireName}",
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                if (typed.length <= FeedbackMaxLength) {
                    text = typed
                    outcome = null
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("feedback_text"),
            label = { Text(stringResource(R.string.feedback_text_label)) },
            minLines = 5,
            maxLines = 12,
            enabled = !sending,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        MomentumCheckboxRow(
            checked = withFacts,
            onCheckedChange = { withFacts = it },
            testTag = "feedback_with_facts",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(R.string.feedback_with_facts), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = facts.replace("\n", " · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MomentumButton(
            text = stringResource(R.string.feedback_send),
            onClick = {
                sending = true
                val message = FeedbackMessage(kind, text, facts.takeIf { withFacts }, language)
                scope.launch {
                    val result = sendFeedback(message)
                    if (result == FeedbackOutcome.Sent) text = ""
                    outcome = result
                    sending = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.AutoMirrored.Rounded.Send,
            enabled = text.isNotBlank() && !sending,
            testTag = "feedback_send",
        )
        ReservedMessage(
            text = outcome?.let(outcomes::getValue),
            color = if (outcome == FeedbackOutcome.Sent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            reserving = outcomes.values.toList(),
            testTag = "feedback_outcome",
        )
        Text(
            text = stringResource(R.string.feedback_public_note),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MomentumButton(
            text = stringResource(R.string.feedback_mail_instead),
            onClick = { context.openMailDraft(mailSubject, mailBody) },
            modifier = Modifier.fillMaxWidth(),
            variant = MomentumButtonVariant.Quiet,
            testTag = "feedback_mail",
        )
    }
}

private val FeedbackKind.label: Int
    get() = when (this) {
        FeedbackKind.Problem -> R.string.feedback_kind_problem
        FeedbackKind.Idea -> R.string.feedback_kind_idea
        FeedbackKind.Other -> R.string.feedback_kind_other
    }

private val FeedbackOutcome.message: Int
    get() = when (this) {
        FeedbackOutcome.Sent -> R.string.feedback_sent
        FeedbackOutcome.TooMany -> R.string.feedback_too_many
        FeedbackOutcome.Offline -> R.string.feedback_offline
        FeedbackOutcome.Failed -> R.string.feedback_failed
    }

/** Version and phone, one line each, as they are added to a message. */
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
