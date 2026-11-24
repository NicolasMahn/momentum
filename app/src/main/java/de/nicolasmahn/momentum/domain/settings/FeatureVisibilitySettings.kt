package de.nicolasmahn.momentum.domain.settings

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.checklist.Dated
import java.time.LocalDate

/**
 * The places on Home no configuration names: Activity and Substances, the two built-in views, and
 * Statistics. Every other view is named by its kind and its ID ([ViewRef]).
 */
enum class DashboardFeature(
    internal val storageId: String,
) {
    Activity("activity"),
    Substances("substances"),
    Statistics("statistics"),
    ;

    internal companion object {
        fun fromStorageId(storageId: String): DashboardFeature? =
            entries.firstOrNull { it.storageId == storageId }
    }
}

/**
 * Names one thing Home can show: a checklist or a logged view by its kind and its ID, since a
 * checklist's, a measurement's, a questions view's and a time view's IDs are each unique only
 * among their own kind, and Activity, Substances and Statistics, which no ID names, by their
 * feature. Persistence identifies visibility settings by it; runtime modules use TrackerRef.
 */
sealed interface ViewRef {
    data class Feature(val feature: DashboardFeature) : ViewRef

    data class Checklist(val id: String) : ViewRef

    data class Logged(val view: LoggedViewRef) : ViewRef
}

/**
 * Which views Home shows, day by day. A hidden entry is a dated setting, `true` while hidden:
 * turning a view off counts from today's habit day and earlier days keep what they were, turning
 * it on again counts from today too, and a view hidden once and shown again keeps its history. An
 * entry exists while the view is hidden now or was ever hidden; a view never hidden has none, and
 * a deleted view loses its entry outright ([forgetting]), never through a revision that would show
 * it. A hidden entry written before visibility was dated reads as hidden from the beginning.
 *
 * Off and leaving the streak stay two settings: off takes a view off Home and out of the streak
 * from today, with its reminder silent; leaving keeps the view on Home and only stops it being
 * judged. Hiding never writes the streak setting.
 */
data class FeatureVisibilitySettings(val hidden: Map<ViewRef, Dated<Boolean>>) {
    /**
     * Hidden from the beginning for each of [disabledFeatures] and each ID of the four kinds:
     * what the visibility settings meant before version 7, and what a test or a new installation
     * starts from. Statistics starts hidden.
     */
    constructor(
        disabledFeatures: Set<DashboardFeature> = setOf(DashboardFeature.Statistics),
        hiddenChecklists: Set<String> = emptySet(),
        hiddenMeasurements: Set<String> = emptySet(),
        hiddenQuestions: Set<String> = emptySet(),
        hiddenTime: Set<String> = emptySet(),
    ) : this(
        (disabledFeatures.map { ViewRef.Feature(it) } +
            hiddenChecklists.map { ViewRef.Checklist(it) } +
            hiddenMeasurements.map { ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, it)) } +
            hiddenQuestions.map { ViewRef.Logged(LoggedViewRef(LoggedViewKind.Questions, it)) } +
            hiddenTime.map { ViewRef.Logged(LoggedViewRef(LoggedViewKind.Time, it)) })
            .associateWith { HiddenFromTheBeginning },
    )

    init {
        // An entry that was never hidden says nothing; it is never written, so it is never held.
        require(hidden.values.all { it.isDated || it.current }) { "A view never hidden has no entry." }
    }

    /** When [key] is shown, day by day: true while shown. A view without an entry was always shown. */
    fun visibilityOf(key: ViewRef): Dated<Boolean> = (hidden[key] ?: NeverHidden).map { !it }

    fun isVisibleOn(key: ViewRef, day: LocalDate): Boolean = hidden[key]?.on(day) != true

    /** Whether [key] is shown now: the answer of the latest revision, which never lies after today. */
    fun isVisible(key: ViewRef): Boolean = hidden[key]?.current != true

    /**
     * [key] shown or hidden from [today] on, every earlier day keeping what it had. Changing back
     * on the same day leaves no revision, and a view never hidden keeps no entry.
     */
    fun withVisibility(key: ViewRef, visible: Boolean, today: LocalDate): FeatureVisibilitySettings {
        val entry = (hidden[key] ?: NeverHidden).changedFrom(today, !visible)
        return copy(hidden = if (entry.isDated || entry.current) hidden + (key to entry) else hidden - key)
    }

    /** Without the entry of [key], for a view that was deleted: nothing is left to hide, and nothing to date. */
    fun forgetting(key: ViewRef): FeatureVisibilitySettings = if (key in hidden) copy(hidden = hidden - key) else this

    /** With [key] hidden from the beginning, as a hidden entry written before visibility was dated reads. */
    fun hiding(key: ViewRef): FeatureVisibilitySettings = copy(hidden = hidden + (key to HiddenFromTheBeginning))

    fun hiding(feature: DashboardFeature): FeatureVisibilitySettings = hiding(ViewRef.Feature(feature))

    fun hiding(view: LoggedViewRef): FeatureVisibilitySettings = hiding(ViewRef.Logged(view))

    fun hidingChecklist(checklistId: String): FeatureVisibilitySettings = hiding(ViewRef.Checklist(checklistId))

    fun isVisible(feature: DashboardFeature): Boolean = isVisible(ViewRef.Feature(feature))

    fun isVisibleOn(feature: DashboardFeature, day: LocalDate): Boolean = isVisibleOn(ViewRef.Feature(feature), day)

    fun visibilityOf(feature: DashboardFeature): Dated<Boolean> = visibilityOf(ViewRef.Feature(feature))

    fun withVisibility(feature: DashboardFeature, visible: Boolean, today: LocalDate): FeatureVisibilitySettings =
        withVisibility(ViewRef.Feature(feature), visible, today)

    fun isChecklistVisible(checklistId: String): Boolean = isVisible(ViewRef.Checklist(checklistId))

    fun isChecklistVisibleOn(checklistId: String, day: LocalDate): Boolean = isVisibleOn(ViewRef.Checklist(checklistId), day)

    fun checklistVisibility(checklistId: String): Dated<Boolean> = visibilityOf(ViewRef.Checklist(checklistId))

    fun withChecklistVisibility(checklistId: String, visible: Boolean, today: LocalDate): FeatureVisibilitySettings =
        withVisibility(ViewRef.Checklist(checklistId), visible, today)

    fun forgettingChecklist(checklistId: String): FeatureVisibilitySettings = forgetting(ViewRef.Checklist(checklistId))

    /** Whether the logged view [view] names is shown now, whichever kind it is. */
    fun shows(view: LoggedViewRef): Boolean = isVisible(ViewRef.Logged(view))

    fun showsOn(view: LoggedViewRef, day: LocalDate): Boolean = isVisibleOn(ViewRef.Logged(view), day)

    fun visibilityOf(view: LoggedViewRef): Dated<Boolean> = visibilityOf(ViewRef.Logged(view))

    fun withVisibility(view: LoggedViewRef, visible: Boolean, today: LocalDate): FeatureVisibilitySettings =
        withVisibility(ViewRef.Logged(view), visible, today)

    fun forgetting(view: LoggedViewRef): FeatureVisibilitySettings = forgetting(ViewRef.Logged(view))

    /** The IDs of [kind] an entry names, hidden now or once: a new view is never given one of them, since a delete can leave an entry behind. */
    fun namedIds(kind: LoggedViewKind): Set<String> =
        hidden.keys.filterIsInstance<ViewRef.Logged>().filter { it.view.kind == kind }.mapTo(LinkedHashSet()) { it.view.id }

    /** The checklist IDs an entry names, as [namedIds] names a logged kind's. */
    val namedChecklistIds: Set<String> get() = hidden.keys.filterIsInstance<ViewRef.Checklist>().mapTo(LinkedHashSet()) { it.id }
}

/** Hidden on every day, as a hidden entry written before visibility was dated reads. */
val HiddenFromTheBeginning: Dated<Boolean> = Dated.always(true)

private val NeverHidden: Dated<Boolean> = Dated.always(false)

/** What a new installation starts with: nothing on Home until a person chooses it. */
val NewInstallationVisibility = FeatureVisibilitySettings(disabledFeatures = setOf(DashboardFeature.Activity, DashboardFeature.Substances, DashboardFeature.Statistics))
