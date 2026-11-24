package de.nicolasmahn.momentum.feature.about

import android.content.Context
import org.json.JSONObject

/**
 * One piece of open-source software the app ships. Its [name], its [notice] and its license are
 * proper nouns and legal text: they are shown as written, in English, in every app language.
 */
internal data class OpenSourceComponent(
    val name: String,
    val license: License,
    /** Where the component comes from: its repository or project page. */
    val source: String,
    /** The attribution its authors ask to be shown with it, if any. */
    val notice: String?,
)

/** A license by its [id], which names its text in the assets, and the [name] it is known by. */
internal data class License(val id: String, val name: String)

private const val LicensesFolder = "licenses"

/**
 * The components `assets/licenses/components.json` lists, in its order. That file is the one list
 * of what ships: the `checkOpenSourceLicenses` build task compares it with the release classpath.
 */
internal fun Context.openSourceComponents(): List<OpenSourceComponent> =
    parseOpenSourceComponents(assets.open("$LicensesFolder/components.json").bufferedReader().use { it.readText() })

/** The full text of [license], as its authors published it. */
internal fun Context.licenseText(license: License): String =
    assets.open("$LicensesFolder/${license.id}.txt").bufferedReader().use { it.readText() }

internal fun parseOpenSourceComponents(json: String): List<OpenSourceComponent> {
    val root = JSONObject(json)
    val licenseNames = root.getJSONObject("licenses")
    val components = root.getJSONArray("components")
    return List(components.length()) { index ->
        val component = components.getJSONObject(index)
        val licenseId = component.getString("license")
        OpenSourceComponent(
            name = component.getString("name"),
            license = License(licenseId, licenseNames.getString(licenseId)),
            source = component.getString("source"),
            notice = component.optString("notice").ifEmpty { null },
        )
    }
}

/**
 * A license text as paragraphs a phone can wrap. Upstream texts are broken by hand at about 80
 * columns, which a narrow screen breaks again in the middle of every line; here a line that was
 * broken only because it was full is joined to the one after it. Every word stays, in its order.
 *
 * A line counts as full when it is at least [FullLineShare] of the longest one and is prose, so a
 * heading, a list's last line and a rule of dashes still end where their authors ended them.
 */
internal fun licenseParagraphs(text: String): List<String> {
    val lines = text.lines().map { it.trim() }
    val fullLength = (lines.maxOfOrNull { it.length } ?: 0) * FullLineShare
    val paragraphs = mutableListOf<String>()
    var open = false
    for (line in lines) {
        if (line.isEmpty()) {
            open = false
            continue
        }
        if (open) paragraphs[paragraphs.lastIndex] += " $line" else paragraphs += line
        open = line.length >= fullLength && line.any(Char::isLetter)
    }
    return paragraphs
}

private const val FullLineShare = 0.7
