package com.example.workouttracker.core.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.example.workouttracker.R
import com.example.workouttracker.core.format.DateStyle
import com.example.workouttracker.core.format.formatDate
import com.example.workouttracker.core.format.formatFixedDecimals
import com.example.workouttracker.core.format.formatMetricNumber
import com.example.workouttracker.core.format.formatSignedMetric
import com.example.workouttracker.core.locale.locale
import java.time.LocalDate

/**
 * Words decided away from the screen and put into a language only where they are shown. A model,
 * a notification or the widget says what it means without holding Android resources, stays
 * comparable in plain unit tests, and reads in whatever language the screen or context speaks.
 *
 * A composable that only shows a fixed label calls `stringResource` directly; this is for text a
 * model or a shared helper composes.
 */
sealed interface UiText {
    /** Text the user wrote, shown exactly as it was entered. */
    data class Verbatim(val value: String) : UiText

    /** Arguments may be [UiText], which is resolved first, or plain values for the format. */
    data class Resource(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = listOf(count),
    ) : UiText

    /** Separators are punctuation, which reads the same in every language the app offers. */
    data class Joined(val parts: List<UiText>, val separator: String) : UiText

    data class Date(val date: LocalDate, val style: DateStyle) : UiText

    /** A measurement such as kilograms, in the locale's decimal style; [decimals] fixes how many it shows, as a distance's two do. */
    data class Number(val value: Double, val signed: Boolean = false, val decimals: Int? = null) : UiText
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Resource(id, args.toList())

/** With no [args] the count itself fills the plural's one placeholder. */
fun pluralText(@PluralsRes id: Int, count: Int, vararg args: Any): UiText =
    UiText.Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())

fun List<UiText>.joinedBy(separator: String): UiText = UiText.Joined(this, separator)

/**
 * A list inside a sentence, "a, b and c", with the language's own "and" before the last item.
 * Lists of things to do are joined here, so every one of them reads the same way.
 */
fun List<UiText>.joinedWithAnd(): UiText = when (size) {
    0 -> UiText.Verbatim("")
    1 -> single()
    else -> uiText(R.string.core_list_and, dropLast(1).joinedBy(", "), last())
}

/**
 * [text] a person typed, set apart from the sentence it is put into: it is laid out by its own
 * first letter, so text written right to left cannot reorder the number or the words beside it. For
 * a sentence that is shown; what is spoken or stored takes the text as typed.
 */
fun isolated(text: String): String = "$FirstStrongIsolate$text$PopDirectionalIsolate"

private const val FirstStrongIsolate = '\u2068'
private const val PopDirectionalIsolate = '\u2069'

fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Verbatim -> value
    is UiText.Resource ->
        if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args.resolved(resources))
    is UiText.Plural -> resources.getQuantityString(id, count, *args.resolved(resources))
    is UiText.Joined -> parts.joinToString(separator) { it.resolve(resources) }
    is UiText.Date -> formatDate(date, style, resources.locale)
    is UiText.Number -> when {
        decimals != null -> formatFixedDecimals(value, decimals, resources.locale)
        signed -> formatSignedMetric(value, resources.locale)
        else -> formatMetricNumber(value, resources.locale)
    }
}

/** Resolves in the screen's language and follows it when the language changes. */
@Composable
fun UiText.resolve(): String = resolve(LocalResources.current)

private fun List<Any>.resolved(resources: Resources): Array<Any> =
    map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
