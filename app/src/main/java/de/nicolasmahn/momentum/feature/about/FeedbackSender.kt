package de.nicolasmahn.momentum.feature.about

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Where the app posts feedback: Momentum's own server, which turns each message into an issue on
 * GitHub under the Momentum feedback app (`deploy/feedback`). It is the app's only use of the
 * internet.
 */
private const val FeedbackEndpoint = "https://momentum.nicolas-mahn.de/api/feedback"

/** The longest message the server takes, in characters; the field stops there too. */
internal const val FeedbackMaxLength = 4000

internal enum class FeedbackKind(val wireName: String) {
    Problem("problem"),
    Idea("idea"),
    Other("other"),
}

/** One message as the person wrote it. [facts] is the version and phone, or null when left out. */
internal data class FeedbackMessage(
    val kind: FeedbackKind,
    val text: String,
    val facts: String?,
    val language: String,
) {
    fun toJson(): String = JSONObject()
        .put("kind", kind.wireName)
        .put("text", text.trim())
        .put("facts", facts ?: JSONObject.NULL)
        .put("language", language)
        .toString()
}

internal enum class FeedbackOutcome { Sent, TooMany, Offline, Failed }

/** What the server's answer means for the person: only a refusal to take more is theirs to wait out. */
internal fun feedbackOutcome(status: Int): FeedbackOutcome = when (status) {
    in 200..299 -> FeedbackOutcome.Sent
    429 -> FeedbackOutcome.TooMany
    else -> FeedbackOutcome.Failed
}

internal suspend fun sendFeedback(message: FeedbackMessage): FeedbackOutcome = withContext(Dispatchers.IO) {
    try {
        val connection = URL(FeedbackEndpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(message.toJson().toByteArray(Charsets.UTF_8)) }
            feedbackOutcome(connection.responseCode)
        } finally {
            connection.disconnect()
        }
    } catch (_: IOException) {
        FeedbackOutcome.Offline
    }
}
