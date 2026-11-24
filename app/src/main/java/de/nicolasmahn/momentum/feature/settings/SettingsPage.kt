package de.nicolasmahn.momentum.feature.settings

import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.trackerRef

/**
 * A page of Settings below its list: an app-wide part, or one view with everything about it. Its
 * [key] is plain text, so the open page survives the activity being recreated.
 */
internal sealed interface SettingsPage {
    val key: String

    data object Backups : SettingsPage {
        override val key = "backups"
    }

    data object Holidays : SettingsPage {
        override val key = "holidays"
    }

    data object SickDays : SettingsPage {
        override val key = "sick_days"
    }

    /** The wake time and the bedtime every relative reminder and Mo's sleepiness read. */
    data object Sleep : SettingsPage {
        override val key = "sleep"
    }

    data object Reminders : SettingsPage {
        override val key = "reminders"
    }

    /** The views and the way to add one, the part of the list the first start opens on. */
    data object Views : SettingsPage {
        override val key = "views"
    }

    /**
     * Activity's page ([hasSettingsPage]): its configuration and its reminder, a draft page saved
     * on purpose, over two stores ([BuiltInViewSave]).
     */
    data class Feature(val feature: DashboardFeature) : SettingsPage {
        override val key get() = "$FeaturePrefix${feature.storageId}"
    }

    /** Settings for one checklist instance. */
    data class Checklist(val checklistId: String) : SettingsPage {
        override val key get() = "$ChecklistPrefix$checklistId"
    }

    /** Settings for one measurement instance. */
    data class Measurement(val measurementId: String) : SettingsPage {
        override val key get() = "$MeasurementPrefix$measurementId"
    }

    /** Settings for one questions instance. */
    data class Questions(val viewId: String) : SettingsPage {
        override val key get() = "$QuestionsPrefix$viewId"
    }

    /** Settings for one timer instance. */
    data class Time(val viewId: String) : SettingsPage {
        override val key get() = "$TimePrefix$viewId"
    }

    /** A module owns its page; adding a kind does not add another Settings route. */
    data class Tracker(val ref: TrackerRef) : SettingsPage {
        override val key get() = "tracker:${ref.type.value}:${ref.id}"
    }

    data class NewTracker(val type: TrackerTypeId, val variant: String? = null) : SettingsPage {
        override val key get() = "new_tracker:${type.value}" + variant?.let { ":$it" }.orEmpty()
    }

    /** Asks what kind of thing to track is added, before that kind's setup opens. It holds nothing to lose. */
    data object NewView : SettingsPage {
        override val key = "new_view"
    }

    /** The guided setup of a new checklist, which is stored only when the last step creates it. */
    data object NewChecklist : SettingsPage {
        override val key = "new_checklist"
    }

    /** The guided setup of a new measurement, which is stored only when the last step creates it. */
    data object NewMeasurement : SettingsPage {
        override val key = "new_measurement"
    }

    /** The guided setup of a new questions view, which is stored only when the last step creates it. */
    data object NewQuestions : SettingsPage {
        override val key = "new_questions"
    }

    /** The guided setup of a new time view, which is stored only when the last step creates it. */
    data object NewTime : SettingsPage {
        override val key = "new_time"
    }

    /** Which substances are tracked and the person's own: a draft page, saved on purpose. */
    data object Substances : SettingsPage {
        override val key = "substances"
    }

    /** The app's version and the open-source software it ships. It holds nothing to lose. */
    data object About : SettingsPage {
        override val key = "about"
    }

    /** The two ways to send feedback: an issue on GitHub or an email. It holds nothing to lose. */
    data object Feedback : SettingsPage {
        override val key = "feedback"
    }

    /** The privacy policy in full, opened from [About]. */
    data object Privacy : SettingsPage {
        override val key = "privacy"
    }

    /** Who offers the app and where they can be reached, opened from [About]. */
    data object LegalNotice : SettingsPage {
        override val key = "legal_notice"
    }

    /** One open-source component, by its name, with the full text of its license. It is opened from [About]. */
    data class License(val component: String) : SettingsPage {
        override val key get() = "$LicensePrefix$component"
    }

    companion object {
        fun fromKey(key: String): SettingsPage? = when {
            key.startsWith("tracker:") -> key.removePrefix("tracker:").let { target ->
                if (':' !in target) null else runCatching {
                    Tracker(TrackerRef(TrackerTypeId(target.substringBefore(':')), target.substringAfter(':')))
                }.getOrNull()
            }
            key.startsWith("new_tracker:") -> key.removePrefix("new_tracker:").let { target ->
                val variant = if (':' in target) target.substringAfter(':').takeIf { it.matches(VariantShape) } ?: return null else null
                runCatching { NewTracker(TrackerTypeId(target.substringBefore(':')), variant) }.getOrNull()
            }
            key == Backups.key -> Backups
            key == Holidays.key -> Holidays
            key == SickDays.key -> SickDays
            key == Sleep.key -> Sleep
            key == Reminders.key -> Reminders
            key == Views.key -> Views
            key == NewView.key -> NewView
            key == NewChecklist.key -> NewTracker(TrackerTypeId("checklist"))
            key == NewMeasurement.key -> NewTracker(TrackerTypeId("measurement"))
            key == NewQuestions.key -> NewTracker(TrackerTypeId("questions"))
            key == NewTime.key -> NewTracker(TrackerTypeId("time"))
            key == Substances.key -> Substances
            key == About.key -> About
            key == Feedback.key -> Feedback
            key == Privacy.key -> Privacy
            key == LegalNotice.key -> LegalNotice
            key.startsWith(LicensePrefix) -> key.removePrefix(LicensePrefix).takeIf { it.isNotBlank() }?.let(::License)
            key.startsWith(FeaturePrefix) -> key.removePrefix(FeaturePrefix).let { id ->
                DashboardFeature.fromStorageId(id)?.takeIf { it.hasSettingsPage }?.let(::Feature)
            }
            key.startsWith(ChecklistPrefix) -> trackerPage("checklist", key.removePrefix(ChecklistPrefix))
            key.startsWith(MeasurementPrefix) -> trackerPage("measurement", key.removePrefix(MeasurementPrefix))
            key.startsWith(QuestionsPrefix) -> trackerPage("questions", key.removePrefix(QuestionsPrefix))
            key.startsWith(TimePrefix) -> trackerPage("time", key.removePrefix(TimePrefix))
            else -> null
        }

        private fun trackerPage(type: String, id: String): SettingsPage? =
            runCatching { Tracker(TrackerRef(TrackerTypeId(type), id)) }.getOrNull()

        private const val FeaturePrefix = "feature:"
        private const val LicensePrefix = "license:"

        private const val ChecklistPrefix = "checklist:"
        private const val MeasurementPrefix = "measurement:"
        private const val QuestionsPrefix = "questions:"
        private const val TimePrefix = "time:"
    }
}

/** Compatibility adapter for callers holding the stored logged-view reference. */
internal val LoggedViewRef.settingsPage: SettingsPage
    get() = SettingsPage.Tracker(trackerRef)

/**
 * Whether the feature has a page of its own ([SettingsPage.Feature]): Activity alone. Substances
 * has its own page ([SettingsPage.Substances]), and Statistics has nothing more to set.
 */
internal val DashboardFeature.hasSettingsPage: Boolean
    get() = this == DashboardFeature.Activity

/** The page Back leads to from this one, where that is not the Settings list. */
internal val SettingsPage.parentPage: SettingsPage?
    get() = when (this) {
        is SettingsPage.License, SettingsPage.Privacy, SettingsPage.LegalNotice -> SettingsPage.About
        else -> null
    }

/** A creation's variant in a page key: lowercase words, so a malformed key has no route. */
private val VariantShape = Regex("[a-z]+(_[a-z]+)*")
