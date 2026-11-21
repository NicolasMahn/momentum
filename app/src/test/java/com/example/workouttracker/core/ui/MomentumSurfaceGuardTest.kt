package com.example.workouttracker.core.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shape, ledge and press motion are decided once, in [MomentumButton], [MomentumCard],
 * [MomentumCheckbox], [MomentumDialog] and [MomentumTabs]. Raw Material surfaces would quietly
 * reintroduce a second answer.
 *
 * The same harness holds the project rules that are cheap to check as text, so a convention in
 * AGENTS.md fails a build instead of waiting for a reviewer to remember it.
 */
class MomentumSurfaceGuardTest {

    @Test
    fun noFileUsesMaterialButtons() = assertNoViolations(ButtonSurface)

    @Test
    fun noFileUsesMaterialCards() = assertNoViolations(CardSurface)

    @Test
    fun noFileUsesMaterialCheckboxes() = assertNoViolations(CheckboxSurface)

    @Test
    fun noFileUsesMaterialDialogs() = assertNoViolations(DialogSurface)

    @Test
    fun noFileOpensARawWindowDialog() = assertNoViolations(WindowDialogSurface)

    @Test
    fun noFileUsesMaterialTabs() = assertNoViolations(TabSurface)

    @Test
    fun colorsComeFromTheThemeNotFromLiterals() = assertNoViolations(ColorLiteralRule)

    @Test
    fun featureFieldsNeverUseASupportingLine() = assertNoViolations(SupportingTextRule)

    @Test
    fun inkIsANamedRoleNeverGuessedFromAColor() = assertNoViolations(LuminanceRule)

    @Test
    fun featureModelsNeverBuildTheirOwnDayLists() = assertNoViolations(DayListRule)

    @Test
    fun productTextComesFromResources() = assertNoViolations(ProductTextRule)

    @Test
    fun displayFormattingFollowsTheAppLanguage() = assertNoViolations(PinnedLocaleRule)

    @Test
    fun productTextIsCaughtWhereverItIsShown() {
        val text = ProductTextRule::violates
        assertTrue(text("""Text("Save")"""))
        assertTrue(text("""Text(text = "Saved ${'$'}count")"""))
        assertTrue(text("""MomentumButton(text = "Allow", onClick = {})"""))
        assertTrue(text("""contentDescription = "Remove""""))
        assertTrue(text("""builder.setContentTitle("Mo wants today's weight")"""))
        assertTrue(!text("""Text(stringResource(R.string.save))"""))
        assertTrue(!text("""Text(text = "—")"""))
        assertTrue(!text("""Text(text = "+${'$'}{formatMetricNumber(delta, locale)}")"""))
        assertTrue(!text("""testTag = "measurement_save""""))
        assertTrue(!text("""label = "momentum press scale","""))
    }

    @Test
    fun aSurfaceIsCaughtHoweverItIsSpelled() {
        val button = ButtonSurface::violates
        assertTrue(button("import androidx.compose.material3.Button\n"))
        assertTrue(button("import androidx.compose.material3.Button as B\n"))
        assertTrue(button("fun f() { androidx.compose.material3.Button(onClick = {}) {} }"))
        assertTrue(button("import androidx.compose.material3.*\nfun f() { Button(onClick = {}) {} }"))
        assertTrue(!button("import androidx.compose.material3.*\nfun f() { MomentumButton() }"))
        assertTrue(!button("import androidx.compose.material3.ButtonDefaults\n"))
    }

    @Test
    fun aMaterialTabIsCaughtButTheTabRoleAndMomentumTabsAreNot() {
        val tab = TabSurface::violates
        assertTrue(tab("import androidx.compose.material3.Tab\n"))
        assertTrue(tab("import androidx.compose.material3.*\nfun f() { PrimaryTabRow(selectedTabIndex = 0) {} }"))
        assertTrue(!tab("import androidx.compose.material3.*\nfun f() { MomentumTabs(tabs, tab, {}, { it }) }"))
        assertTrue(!tab("import androidx.compose.material3.*\nval role = Role.Tab"))
    }

    private fun assertNoViolations(rule: SourceRule) {
        val offenders = sourceRoots().flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { it to it.relativeTo(root).invariantSeparatorsPath }
                .filter { (_, path) -> rule.appliesTo(path) && path !in rule.allowed }
                .filter { (file, _) -> rule.violates(file.readText()) }
                .map { (_, path) -> path }
        }.toSet()
        assertEquals(
            "${rule.verdict} Offenders: ${offenders.joinToString()}.",
            emptySet<String>(),
            offenders,
        )
    }

    private fun sourceRoots(): List<File> {
        val module = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "src/main/java").isDirectory }
        assertTrue(
            "Cannot find src/main/java from ${File("").absolutePath}. " +
                "Run this test from the app module so the guard scans real sources.",
            module != null,
        )
        return listOf("src/main/java", "src/debug/java")
            .map { File(module, it) }
            .filter { it.isDirectory }
    }

    /** A convention that can be checked against a file's text. */
    private interface SourceRule {
        /** What the failure message tells the person who broke the rule. */
        val verdict: String

        /** Files allowed to break the rule, each with the reason it may. */
        val allowed: Map<String, String>

        fun appliesTo(path: String): Boolean = true

        fun violates(source: String): Boolean
    }

    /** One family of library composables that a Momentum component has replaced. */
    private data class Surface(
        val what: String,
        val replacement: String,
        val forbidden: List<String>,
        override val allowed: Map<String, String>,
        val packageName: String = MATERIAL3,
    ) : SourceRule {
        override val verdict =
            "Use $replacement from core/ui instead of raw $what."

        override fun violates(source: String): Boolean {
            val names = forbidden.joinToString("|")
            val packagePattern = Regex.escape(packageName)
            // A direct import, with or without an alias, and a fully qualified call both name the
            // composable outright.
            val named = Regex("""(?<![\w.])$packagePattern\.($names)(?![\w.])""")
            if (named.containsMatchIn(source)) return true
            // A star import only offends if one of the names is actually called.
            return "import $packageName.*" in source &&
                Regex("""(?<![\w.])($names)\s*\(""").containsMatchIn(source)
        }
    }

    /** A pattern that must not appear in the code of the files the rule covers. */
    private data class ForbiddenText(
        override val verdict: String,
        val pattern: Regex,
        override val allowed: Map<String, String> = emptyMap(),
        val scope: (String) -> Boolean = { true },
    ) : SourceRule {
        override fun appliesTo(path: String): Boolean = scope(path)

        // Prose may name what code may not use, so comment lines are not code.
        override fun violates(source: String): Boolean = source.lineSequence()
            .map(String::trim)
            .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
            .any(pattern::containsMatchIn)
    }

    private companion object {
        const val MATERIAL3 = "androidx.compose.material3"
        const val APP = "com/example/workouttracker"
        const val CORE_UI = "$APP/core/ui"

        fun isFeature(path: String) = path.startsWith("$APP/feature/")

        val ButtonSurface = Surface(
            what = "Material buttons",
            replacement = "MomentumButton",
            forbidden = listOf(
                "Button",
                "OutlinedButton",
                "TextButton",
                "FilledTonalButton",
                "ElevatedButton",
            ),
            allowed = mapOf("$CORE_UI/MomentumButton.kt" to "the one button surface"),
        )

        val CardSurface = Surface(
            what = "Material cards",
            replacement = "MomentumCard",
            forbidden = listOf("Card", "OutlinedCard", "ElevatedCard"),
            allowed = mapOf("$CORE_UI/MomentumCard.kt" to "the one card surface"),
        )

        val CheckboxSurface = Surface(
            what = "Material checkboxes",
            replacement = "MomentumCheckbox",
            // CheckboxDefaults is listed too: its only purpose is colouring a Material checkbox.
            forbidden = listOf("Checkbox", "TriStateCheckbox", "CheckboxDefaults"),
            allowed = mapOf("$CORE_UI/MomentumCheckbox.kt" to "the one checkbox surface"),
        )

        val DialogSurface = Surface(
            what = "Material dialogs",
            replacement = "MomentumDialog",
            forbidden = listOf(
                "AlertDialog",
                "BasicAlertDialog",
                "TimePickerDialog",
                "DatePickerDialog",
            ),
            allowed = mapOf(
                "$CORE_UI/MomentumDialog.kt" to "the one dialog surface",
                "$APP/feature/settings/SettingsScreen.kt" to
                    "Material's time picker is a whole control with no Momentum equivalent. Its " +
                    "buttons are MomentumButtons and its colors are Settings' own neutral roles.",
            ),
        )

        val WindowDialogSurface = Surface(
            what = "window dialogs",
            replacement = "MomentumDialog or MomentumBlockingDialog",
            forbidden = listOf("Dialog"),
            allowed = mapOf("$CORE_UI/MomentumDialog.kt" to "the one dialog surface"),
            packageName = "androidx.compose.ui.window",
        )

        val TabSurface = Surface(
            what = "Material tabs",
            replacement = "MomentumTabs",
            // TabRowDefaults is listed too: its only purpose is styling a Material tab row.
            forbidden = listOf(
                "TabRow",
                "ScrollableTabRow",
                "PrimaryTabRow",
                "SecondaryTabRow",
                "PrimaryScrollableTabRow",
                "SecondaryScrollableTabRow",
                "Tab",
                "LeadingIconTab",
                "TabRowDefaults",
            ),
            allowed = mapOf("$CORE_UI/MomentumTabs.kt" to "the one tab surface"),
        )

        val ColorLiteralRule = ForbiddenText(
            verdict = "Colors come from core/ui/theme tokens and a feature's presentation " +
                "mapping, never from a literal or a named Color constant.",
            // Color.Unspecified is the absence of a choice, not a color.
            pattern = Regex("""\bColor\s*\(\s*0x|\bColor\.(?!Unspecified\b)[A-Z]\w*"""),
            allowed = mapOf(
                "$APP/feature/logactivity/ActivityIcons.kt" to
                    "A vector icon needs some opaque fill to have a shape; Icon tints it, so the " +
                    "black never reaches the screen.",
            ),
            scope = { !it.startsWith("$CORE_UI/theme/") },
        )

        val SupportingTextRule = ForbiddenText(
            verdict = "A field's error replaces its label: use MomentumTextField or " +
                "MomentumDateField, never a supportingText line.",
            pattern = Regex("""\bsupportingText\b"""),
            scope = ::isFeature,
        )

        val LuminanceRule = ForbiddenText(
            verdict = "Every color comes with the ink and ground made for it: read the named role " +
                "instead of deciding from a color's luminance.",
            pattern = Regex("""\bluminance\s*\("""),
            scope = { !it.startsWith("$CORE_UI/") },
        )

        /**
         * Words on screen, in a notification or on the widget live in string resources, so every
         * language can translate them. Punctuation and numbers alone are not words. Debug-only
         * gallery fixtures never ship and may say what they like.
         */
        val ProductTextRule = object : SourceRule {
            override val verdict = "Shown text comes from string resources (stringResource, " +
                "UiText or Resources.getString), never from a literal."
            override val allowed = emptyMap<String, String>()
            private val shownLiteral = Regex(
                """(\b(text|label|contentDescription|description|message|title|actionLabel|""" +
                    """completedLabel|placeholder|note|timing|featureLabel)\s*=\s*|""" +
                    """\bText\(\s*|\.set(ContentTitle|ContentText|TextViewText|ContentDescription|""" +
                    """Ticker|SubText)\((\s*[\w.]+\s*,)?\s*|\bNotificationCompat\.Action\([^,]*,\s*)""" +
                    """"[^"]*[A-Za-z]{2}""",
            )
            private val template = Regex("""\$\{[^}]*\}|\$\w+""")

            // An animation's label names it for the layout inspector and is never shown or spoken;
            // by convention it is lower case, where every shown label starts with a capital.
            private val animationLabel = Regex("""\blabel\s*=\s*"[a-z ]+"""")

            override fun appliesTo(path: String) = !path.startsWith("$APP/gallery/")

            // A template's code is not words: "+${'$'}{formatMetricNumber(delta)}" shows a number.
            override fun violates(source: String): Boolean = source.lineSequence()
                .map(String::trim)
                .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
                .map { it.replace(animationLabel, "") }
                .any { shownLiteral.containsMatchIn(it.replace(template, "")) }
        }

        val PinnedLocaleRule = ForbiddenText(
            verdict = "Format shown dates and numbers with the screen's locale (currentLocale() " +
                "or Resources.locale from core/locale), never a fixed English locale. Locale.ROOT " +
                "is for identifiers and parsing.",
            pattern = Regex("""\bLocale\.(ENGLISH|US|UK)\b"""),
            scope = { !it.startsWith("$APP/gallery/") },
        )

        val DayListRule = ForbiddenText(
            verdict = "A recent-day calendar takes its days from calendarWeeksWindow in " +
                "core/time; a feature model never builds a rolling day list.",
            pattern = Regex("""\b(minusDays|plusDays)\s*\("""),
            allowed = mapOf(
                "$APP/feature/measurement/MeasurementModels.kt" to
                    "The measurement chart's axis is time itself, not a week grid: its window rolls " +
                    "and ends on today, for a range the user chooses.",
            ),
            scope = { isFeature(it) && it.endsWith("Models.kt") },
        )
    }
}
