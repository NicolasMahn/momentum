package de.nicolasmahn.momentum.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.time.isValidDateKey
import de.nicolasmahn.momentum.core.time.requireValidDateKey
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId

internal enum class AppDestination(@param:StringRes val label: Int) {
    Home(R.string.app_name),
    Streak(R.string.destination_streak),
    Activity(R.string.feature_activity),

    /** A checklist route carries its instance ID beside the destination. */
    Checklist(R.string.checklist_destination),

    /** A measurement route carries its instance ID beside the destination. */
    Measurement(R.string.measurement_destination),

    /** A questions route carries its instance ID beside the destination. */
    Questions(R.string.questions_destination),

    /** A time route carries its instance ID beside the destination. */
    Time(R.string.time_destination),
    /** A module supplies the title, identity and content of its selected tracker. */
    Tracker(R.string.app_name),
    Substances(R.string.feature_substances),
    Statistics(R.string.feature_statistics),
    Settings(R.string.destination_settings),

    /** A page below the Settings list, such as one view's settings; which one travels beside it. */
    SettingsPage(R.string.destination_settings),
}

/** Where a logged view of this kind lives; which one it is travels beside the destination. */
internal val LoggedViewKind.destination: AppDestination
    get() = when (this) {
        LoggedViewKind.Measurement -> AppDestination.Measurement
        LoggedViewKind.Questions -> AppDestination.Questions
        LoggedViewKind.Time -> AppDestination.Time
    }

/** The kind of logged view this destination shows, or null for every other place. */
internal val AppDestination.loggedViewKind: LoggedViewKind?
    get() = LoggedViewKind.entries.firstOrNull { it.destination == this }

/**
 * Whether a view of this kind opens on the day its reminder asked about. A measurement's and a
 * questions view's reminder asks about one day's record. A time view's asks about its week, so
 * its screen opens on today whenever the reminder is tapped.
 */
internal val LoggedViewKind.opensOnTheDayAsked: Boolean get() = this != LoggedViewKind.Time

/** What the Activity screen chooses on arrival, as the workout reminder's tap asks. */
internal sealed interface ActivityLaunchTarget {
    /** The activity the reminder named: its tile, or Other with it chosen where it has no tile. */
    data class Activity(val activityId: String) : ActivityLaunchTarget
}

internal data class AppNavigationRequest(
    val destination: AppDestination,
    val activityTarget: ActivityLaunchTarget? = null,
    /** Opens the time view and starts its session at once, as its reminder's start action does. */
    val startSession: Boolean = false,
    /** The checklist a [AppDestination.Checklist] request opens. */
    val checklistId: String? = null,
    /** The day it opens on: the one its reminder asked about. */
    val checklistDateKey: String? = null,
    /** The day Substances opens on: the one its check-in asked about. */
    val substanceDateKey: String? = null,
    /** The view a [AppDestination.Measurement], [AppDestination.Questions] or [AppDestination.Time] request opens; the destination says which kind it is. */
    val loggedViewId: String? = null,
    /** The day its reminder asked about, which a measurement and a questions view open on. */
    val loggedDateKey: String? = null,
    val tracker: TrackerRef? = null,
)

internal fun requestedAppDestination(action: String?): AppDestination? =
    requestedAppNavigation(action)?.destination

internal fun Intent.requestedAppNavigation(): AppNavigationRequest? =
    requestedAppNavigation(
        action = action,
        activityId = getStringExtra(ExtraActivityId),
        checklistId = getStringExtra(ExtraChecklistId),
        checklistDateKey = getStringExtra(ExtraChecklistDateKey),
        substanceDateKey = getStringExtra(ExtraSubstanceDateKey),
        measurementId = getStringExtra(ExtraMeasurementId),
        measurementDateKey = getStringExtra(ExtraMeasurementDateKey),
        questionsId = getStringExtra(ExtraQuestionsId),
        questionsDateKey = getStringExtra(ExtraQuestionsDateKey),
        timeId = getStringExtra(ExtraTimeId),
        timeDateKey = getStringExtra(ExtraTimeDateKey),
        trackerType = getStringExtra(ExtraTrackerType),
        trackerId = getStringExtra(ExtraTrackerId),
        trackerDateKey = getStringExtra(ExtraTrackerDateKey),
        trackerStartSession = getBooleanExtra(ExtraStartTrackerSession, false),
    )

internal fun requestedAppNavigation(
    action: String?,
    activityId: String? = null,
    checklistId: String? = null,
    checklistDateKey: String? = null,
    substanceDateKey: String? = null,
    measurementId: String? = null,
    measurementDateKey: String? = null,
    questionsId: String? = null,
    questionsDateKey: String? = null,
    timeId: String? = null,
    timeDateKey: String? = null,
    trackerType: String? = null,
    trackerId: String? = null,
    trackerDateKey: String? = null,
    trackerStartSession: Boolean = false,
): AppNavigationRequest? =
    when (action) {
        ActionOpenTracker -> if (trackerType == null || trackerId == null) null else runCatching {
            AppNavigationRequest(
                destination = AppDestination.Tracker,
                tracker = TrackerRef(TrackerTypeId(trackerType), trackerId),
                loggedDateKey = trackerDateKey?.takeIf(::isValidDateKey),
                startSession = trackerStartSession,
            )
        }.getOrNull()
        ActionOpenMeasurement -> loggedViewRequest(LoggedViewKind.Measurement, measurementId, measurementDateKey)
        ActionOpenQuestions -> loggedViewRequest(LoggedViewKind.Questions, questionsId, questionsDateKey)
        ActionOpenTime -> loggedViewRequest(LoggedViewKind.Time, timeId, timeDateKey)
        ActionStartTime -> loggedViewRequest(LoggedViewKind.Time, timeId, dateKey = null)?.copy(startSession = true)
        ActionOpenChecklist -> checklistId?.let { AppNavigationRequest(AppDestination.Checklist, checklistId = it, checklistDateKey = checklistDateKey?.takeIf(::isValidDateKey)) }
        ActionOpenHome -> AppNavigationRequest(AppDestination.Home)
        ActionOpenActivity -> AppNavigationRequest(AppDestination.Activity)
        // The reminder names the activity it planned; without one the screen opens as it is.
        ActionOpenWorkout -> AppNavigationRequest(
            destination = AppDestination.Activity,
            activityTarget = activityId?.let { ActivityLaunchTarget.Activity(it) },
        )
        ActionOpenSubstances -> AppNavigationRequest(
            destination = AppDestination.Substances,
            substanceDateKey = substanceDateKey?.takeIf(::isValidDateKey),
        )
        else -> null
    }

/**
 * An intent identifies a tracker by [kind] and [id], or makes no request without an ID.
 * A valid [dateKey] selects the record day its reminder asked about.
 */
private fun loggedViewRequest(kind: LoggedViewKind, id: String?, dateKey: String?): AppNavigationRequest? =
    id?.let { AppNavigationRequest(kind.destination, loggedViewId = it, loggedDateKey = dateKey?.takeIf(::isValidDateKey)) }

internal fun homeDestinationPendingIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        HomeDestinationRequestCode,
        Intent(context, MainActivity::class.java).apply {
            action = ActionOpenHome
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun activityDestinationPendingIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        ActivityDestinationRequestCode,
        Intent(context, MainActivity::class.java).apply {
            action = ActionOpenActivity
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/** Opens Activity on [activityId], the activity the workout reminder planned for its day. */
internal fun workoutDestinationPendingIntent(context: Context, activityId: String): PendingIntent =
    PendingIntent.getActivity(
        context,
        WorkoutDestinationRequestCode,
        Intent(context, MainActivity::class.java).apply {
            action = ActionOpenWorkout
            putExtra(ExtraActivityId, activityId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/** Wire identities remain stable for notifications already held by Android across an update. */
internal fun checklistDestinationPendingIntent(context: Context, checklistId: String, dateKey: String): PendingIntent {
    requireValidDateKey(dateKey, subject = "Checklist date")
    return PendingIntent.getActivity(
        context, ChecklistDestinationRequestCode,
        Intent(context, MainActivity::class.java).apply {
            action = ActionOpenChecklist
            data = android.net.Uri.Builder().scheme("momentum").authority("checklist").appendPath(checklistId).appendPath(dateKey).build()
            putExtra(ExtraChecklistId, checklistId)
            putExtra(ExtraChecklistDateKey, dateKey)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal fun loggedViewDestinationPendingIntent(context: Context, view: LoggedViewRef, dateKey: String): PendingIntent {
    requireValidDateKey(dateKey, subject = "The day a view opens on")
    val names = view.kind.intentNames
    return PendingIntent.getActivity(
        context, names.requestCode,
        Intent(context, MainActivity::class.java).apply {
            action = names.action
            data = android.net.Uri.Builder().scheme("momentum").authority(names.authority).appendPath(view.id).appendPath(dateKey).build()
            putExtra(names.idExtra, view.id)
            putExtra(names.dateExtra, dateKey)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private class LoggedViewIntentNames(val action: String, val authority: String, val idExtra: String, val dateExtra: String, val requestCode: Int)

private val LoggedViewKind.intentNames: LoggedViewIntentNames
    get() = when (this) {
        LoggedViewKind.Measurement -> LoggedViewIntentNames(ActionOpenMeasurement, "measurement", ExtraMeasurementId, ExtraMeasurementDateKey, MeasurementDestinationRequestCode)
        LoggedViewKind.Questions -> LoggedViewIntentNames(ActionOpenQuestions, "questions", ExtraQuestionsId, ExtraQuestionsDateKey, QuestionsDestinationRequestCode)
        LoggedViewKind.Time -> LoggedViewIntentNames(ActionOpenTime, "time", ExtraTimeId, ExtraTimeDateKey, TimeDestinationRequestCode)
    }

/** Kind, instance and date are part of intent identity, so notifications cannot overwrite each other. */
internal fun trackerDestinationPendingIntent(context: Context, view: TrackerRef, dateKey: String): PendingIntent =
    PendingIntent.getActivity(
        context,
        TrackerDestinationRequestCode,
        trackerDestinationIntent(context, view, dateKey),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun trackerDestinationIntent(context: Context, view: TrackerRef, dateKey: String?, startSession: Boolean = false): Intent {
    if (dateKey != null) requireValidDateKey(dateKey, subject = "The day a tracker opens on")
    return Intent(context, MainActivity::class.java).apply {
        action = ActionOpenTracker
        data = android.net.Uri.Builder().scheme("momentum").authority("tracker")
            .appendPath(view.type.value).appendPath(view.id).apply {
                if (dateKey != null) appendPath(dateKey)
                if (startSession) appendQueryParameter("start", "true")
            }.build()
        putExtra(ExtraTrackerType, view.type.value)
        putExtra(ExtraTrackerId, view.id)
        putExtra(ExtraTrackerDateKey, dateKey)
        putExtra(ExtraStartTrackerSession, startSession)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

/** Existing session-start notification identity, decoded into the current tracker capability. */
internal fun startTimePendingIntent(context: Context, viewId: String): PendingIntent =
    PendingIntent.getActivity(
        context, StartTimeRequestCode,
        Intent(context, MainActivity::class.java).apply {
            action = ActionStartTime
            data = android.net.Uri.Builder().scheme("momentum").authority("start").appendPath(viewId).build()
            putExtra(ExtraTimeId, viewId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/**
 * Opens Substances on [dateKey], the day its check-in asked about. One per day, told apart by its
 * data as a checklist's is, so a check-in about yesterday still opens yesterday beside today's.
 */
internal fun substancesDestinationPendingIntent(context: Context, dateKey: String): PendingIntent =
    PendingIntent.getActivity(
        context,
        SubstancesDestinationRequestCode,
        substancesDestinationIntent(context, dateKey),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun substancesDestinationIntent(context: Context, dateKey: String): Intent {
    requireValidDateKey(dateKey, subject = "Substances date")
    return Intent(context, MainActivity::class.java).apply {
        action = ActionOpenSubstances
        data = android.net.Uri.Builder().scheme("momentum").authority("substances").appendPath(dateKey).build()
        putExtra(ExtraSubstanceDateKey, dateKey)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

/**
 * Opens the app where it was, asking for no destination: for a notification about something that
 * is not stored yet, such as a test of a checklist still being set up, which has no screen to open.
 */
internal fun appPendingIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        AppRequestCode,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal const val ActionOpenChecklist = "de.nicolasmahn.momentum.action.OPEN_CHECKLIST"
internal const val ActionOpenTracker = "de.nicolasmahn.momentum.action.OPEN_TRACKER"
internal const val ExtraTrackerType = "de.nicolasmahn.momentum.extra.TRACKER_TYPE"
internal const val ExtraTrackerId = "de.nicolasmahn.momentum.extra.TRACKER_ID"
internal const val ExtraTrackerDateKey = "de.nicolasmahn.momentum.extra.TRACKER_DATE_KEY"
internal const val ExtraChecklistId = "de.nicolasmahn.momentum.extra.CHECKLIST_ID"
internal const val ExtraChecklistDateKey = "de.nicolasmahn.momentum.extra.CHECKLIST_DATE_KEY"
internal const val ActionOpenMeasurement = "de.nicolasmahn.momentum.action.OPEN_MEASUREMENT"
internal const val ExtraMeasurementId = "de.nicolasmahn.momentum.extra.MEASUREMENT_ID"
internal const val ExtraMeasurementDateKey = "de.nicolasmahn.momentum.extra.MEASUREMENT_DATE_KEY"
internal const val ActionOpenQuestions = "de.nicolasmahn.momentum.action.OPEN_QUESTIONS"
internal const val ExtraQuestionsId = "de.nicolasmahn.momentum.extra.QUESTIONS_ID"
internal const val ExtraQuestionsDateKey = "de.nicolasmahn.momentum.extra.QUESTIONS_DATE_KEY"
internal const val ActionOpenTime = "de.nicolasmahn.momentum.action.OPEN_TIME"
internal const val ExtraTimeId = "de.nicolasmahn.momentum.extra.TIME_ID"
internal const val ExtraTimeDateKey = "de.nicolasmahn.momentum.extra.TIME_DATE_KEY"
internal const val ActionStartTime = "de.nicolasmahn.momentum.action.START_TIME"
internal const val ActionOpenHome = "de.nicolasmahn.momentum.action.OPEN_HOME"
internal const val ActionOpenActivity = "de.nicolasmahn.momentum.action.OPEN_ACTIVITY"
internal const val ActionOpenWorkout = "de.nicolasmahn.momentum.action.OPEN_WORKOUT"
internal const val ExtraActivityId = "de.nicolasmahn.momentum.extra.ACTIVITY_ID"
internal const val ActionOpenSubstances = "de.nicolasmahn.momentum.action.OPEN_SUBSTANCES"
internal const val ExtraSubstanceDateKey = "de.nicolasmahn.momentum.extra.SUBSTANCE_DATE_KEY"
internal const val ActivityDestinationRequestCode = 8_001
internal const val WorkoutDestinationRequestCode = 8_102
internal const val HomeDestinationRequestCode = 8_602
internal const val ChecklistDestinationRequestCode = 8_702
internal const val AppRequestCode = 8_802
internal const val SubstancesDestinationRequestCode = 8_902
internal const val MeasurementDestinationRequestCode = 9_002
internal const val QuestionsDestinationRequestCode = 9_102
internal const val TimeDestinationRequestCode = 9_202
internal const val StartTimeRequestCode = 9_204
internal const val TrackerDestinationRequestCode = 9_302

internal const val ExtraStartTrackerSession = "de.nicolasmahn.momentum.extra.START_TRACKER_SESSION"
