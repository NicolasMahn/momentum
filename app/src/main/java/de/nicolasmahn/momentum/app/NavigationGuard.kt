package de.nicolasmahn.momentum.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId

/**
 * Where the app is or is going: a screen, with the checklist, the logged view or the page of
 * Settings beside it, and what happens on arrival, as a reminder may ask: a time view's session
 * started, the gym chosen, a day of a checklist or of a logged view opened. Those happen once the
 * screen is reached, never while the way still waits, and are forgotten with a way not taken.
 */
internal data class NavigationTarget(
    val screen: AppDestination,
    val checklistId: String? = null,
    /** The logged view that [screen] shows; the screen says which kind. */
    val loggedViewId: String? = null,
    val pageKey: String? = null,
    val startSession: Boolean = false,
    val activityTarget: ActivityLaunchTarget? = null,
    val checklistDateKey: String? = null,
    val substanceDateKey: String? = null,
    val loggedDateKey: String? = null,
    val tracker: TrackerRef? = null,
    /**
     * The day a lost streak asks to fill in, on a screen that cannot open on a day of its own
     * (Activity, a timer): it is shown there as a hint, apart from any reminder's date.
     */
    val correctionDateKey: String? = null,
) {
    /** The same place as [other], whatever happens on arrival. */
    fun isAt(other: NavigationTarget): Boolean =
        normalized().place() == other.normalized().place()

    private fun place() = listOf(screen, pageKey, tracker)
}

/**
 * The way away from a page that holds unsaved work. The page says whether it does; while it does,
 * any navigation away from it waits here until the person has answered, and the page asks. The
 * waiting way and the guard are kept across process death together, so a restored page is asked
 * before the way is taken: the guard trusts the last report until the page reports again.
 */
internal class NavigationGuard(unsavedWork: Boolean = false, pending: NavigationTarget? = null) {
    /** Whether the open page holds work that leaving would lose, as it last reported. */
    var unsavedWork: Boolean by mutableStateOf(unsavedWork)

    /** The way waiting for the person's answer, if one is. */
    var pending: NavigationTarget? by mutableStateOf(pending)
        private set

    /** Whether [target] has to wait: it leaves [current], which holds unsaved work. */
    fun holds(target: NavigationTarget, current: NavigationTarget): Boolean {
        if (!unsavedWork || target.isAt(current)) return false
        pending = target
        return true
    }

    /** The way that waited, once nothing guards it any more; null while something does or nothing waits. */
    fun released(): NavigationTarget? = if (unsavedWork) null else pending.also { pending = null }

    /** The person answered: the way is taken on discard and forgotten otherwise. */
    fun answered(discard: Boolean): NavigationTarget? {
        val way = pending
        pending = null
        if (discard) unsavedWork = false
        return way.takeIf { discard }
    }

    /** The page closed itself, after creating or deleting, so nothing on it is left to guard. */
    fun pageClosed() {
        unsavedWork = false
    }

    companion object {
        val Saver: Saver<NavigationGuard, Any> = listSaver(
            save = { guard ->
                val way = guard.pending
                listOf(
                    guard.unsavedWork,
                    way?.screen?.name.orEmpty(),
                    way?.checklistId.orEmpty(),
                    way?.pageKey.orEmpty(),
                    way?.startSession ?: false,
                    way?.activityTarget?.saved.orEmpty(),
                    "",
                    way?.checklistDateKey.orEmpty(),
                    way?.substanceDateKey.orEmpty(),
                    way?.loggedViewId.orEmpty(),
                    way?.loggedDateKey.orEmpty(),
                ) + (way?.tracker?.let { listOf(it.type.value, it.id) } ?: emptyList())
            },
            restore = { values ->
                NavigationGuard(
                    unsavedWork = values[0] as Boolean,
                    pending = (values[1] as String).ifEmpty { null }?.let { screen ->
                        val saved = NavigationTarget(
                            screen = AppDestination.Home,
                            checklistId = (values[2] as String).ifEmpty { null },
                            pageKey = (values[3] as String).ifEmpty { null },
                            startSession = values[4] as Boolean,
                            activityTarget = (values[5] as String).ifEmpty { null }?.let(::savedActivityLaunchTarget),
                            checklistDateKey = (values[7] as String).ifEmpty { null },
                            substanceDateKey = (values[8] as String).ifEmpty { null },
                            // A guard saved before measurements could be opened has no tenth value,
                            // and one saved before a reminder could open one on a day no eleventh.
                            loggedViewId = (values.getOrNull(9) as? String)?.ifEmpty { null },
                            loggedDateKey = (values.getOrNull(10) as? String)?.ifEmpty { null },
                            tracker = (values.getOrNull(11) as? String)?.takeIf { it.isNotEmpty() }?.let { type ->
                                (values.getOrNull(12) as? String)?.takeIf { it.isNotEmpty() }?.let { id ->
                                    runCatching { TrackerRef(TrackerTypeId(type), id) }.getOrNull()
                                }
                            },
                        )
                        runCatching { saved.at(screen) }.getOrNull()
                    },
                )
            },
        )
    }
}

private fun NavigationTarget.at(name: String): NavigationTarget = copy(screen = AppDestination.valueOf(name))

/** The target as the guard's saver writes it, `activity:<id>`, which is the one form it reads. */
private val ActivityLaunchTarget.saved: String
    get() = when (this) {
        is ActivityLaunchTarget.Activity -> "$SavedActivityTargetPrefix$activityId"
    }

private fun savedActivityLaunchTarget(saved: String): ActivityLaunchTarget =
    ActivityLaunchTarget.Activity(saved.removePrefix(SavedActivityTargetPrefix))

private const val SavedActivityTargetPrefix = "activity:"

/** Old route forms are accepted here and disappear before navigation reaches the app shell. */
internal fun NavigationTarget.normalized(): NavigationTarget {
    val ref = when (screen) {
        AppDestination.Checklist -> checklistId?.let { TrackerRef(TrackerTypeId("checklist"), it) }
        AppDestination.Measurement -> loggedViewId?.let { TrackerRef(TrackerTypeId("measurement"), it) }
        AppDestination.Questions -> loggedViewId?.let { TrackerRef(TrackerTypeId("questions"), it) }
        AppDestination.Time -> loggedViewId?.let { TrackerRef(TrackerTypeId("time"), it) }
        else -> tracker
    }
    val screen = when (screen) {
        AppDestination.Checklist, AppDestination.Measurement, AppDestination.Questions, AppDestination.Time -> AppDestination.Tracker
        else -> screen
    }
    return copy(screen = screen, tracker = ref, checklistId = null, loggedViewId = null,
        pageKey = pageKey?.let { de.nicolasmahn.momentum.feature.settings.SettingsPage.fromKey(it)?.key },
        loggedDateKey = loggedDateKey ?: checklistDateKey, checklistDateKey = null)
}

/** A restored selected screen enters the same route boundary as a saved pending navigation. */
internal fun NavigationTarget.atSavedScreen(name: String): NavigationTarget =
    runCatching { at(name) }.getOrElse { copy(screen = AppDestination.Home) }.normalized()
