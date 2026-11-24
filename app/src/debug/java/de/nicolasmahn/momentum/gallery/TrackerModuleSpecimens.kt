package de.nicolasmahn.momentum.gallery

import android.app.NotificationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.app.AppDestination
import de.nicolasmahn.momentum.app.AppNavigationRequest
import de.nicolasmahn.momentum.app.MomentumApp
import de.nicolasmahn.momentum.core.format.formatMetricNumber
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.ViewTheme
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.time.TimeSession
import de.nicolasmahn.momentum.feature.checklist.vector
import de.nicolasmahn.momentum.feature.checklist.viewIdentity
import de.nicolasmahn.momentum.feature.time.DoNotDisturb
import de.nicolasmahn.momentum.feature.time.FocusChange
import de.nicolasmahn.momentum.feature.time.TimeSessionNote
import de.nicolasmahn.momentum.feature.time.TimeSessionStore
import de.nicolasmahn.momentum.feature.time.TimeSessions
import de.nicolasmahn.momentum.feature.tracker.TrackerCreation
import de.nicolasmahn.momentum.feature.tracker.TrackerModule
import de.nicolasmahn.momentum.feature.tracker.TrackerReminderCommand
import de.nicolasmahn.momentum.feature.tracker.TrackerScreenHost
import de.nicolasmahn.momentum.feature.tracker.TrackerSettingsHost
import de.nicolasmahn.momentum.feature.tracker.TrackerView

internal fun counterTrackerCreation(): TrackerCreation = CounterTrackerModule().creation

internal val trackerModuleSpecimens = listOf(
    GallerySpecimen("Additional tracker kind, Home and Settings navigation") { TrackerModuleSpecimen() },
    GallerySpecimen("Additional tracker kind, direct screen navigation") { TrackerModuleSpecimen(openCounter = true) },
)

/** The fake kind has no LoggedView configuration and shares one identity across every surface. */
@Composable
internal fun counterTrackerIdentity(): ViewIdentity = viewIdentity(ViewIcon.Dumbbell, ViewHue.Sky)

/** Real app discovery and routing, with the extra kind and all writes confined to memory. */
@Composable
private fun TrackerModuleSpecimen(openCounter: Boolean = false) {
    var state by remember { mutableStateOf(AppState()) }
    val module = remember { CounterTrackerModule() }
    val sessions = remember { galleryTimeSessions() }
    var requested by remember {
        mutableStateOf(
            if (openCounter) AppNavigationRequest(AppDestination.Tracker, tracker = module.views.first().ref) else null,
        )
    }
    ScreenFrame(height = 760.dp) {
        MomentumApp(
            state = state,
            onStateChange = { updated -> state = updated; true },
            onActivitySave = { error("The counter specimen has no Activity store.") },
            featureVisibility = FeatureVisibilitySettings(disabledFeatures = DashboardFeature.entries.toSet()),
            additionalTrackerModules = listOf(module),
            requestedNavigation = requested,
            onDestinationConsumed = { requested = null },
            timeSessions = sessions,
        )
    }
}

private val CounterType = TrackerTypeId("gallery_counter")

/** An additional kind is registered without extending LoggedViewKind or the shell's dispatch tables. */
private class CounterTrackerModule : TrackerModule {
    override val typeId = CounterType
    override var views: List<TrackerView> by mutableStateOf(listOf(CounterTrackerView("push-ups", "Push-ups")))
        private set

    override val creation = object : TrackerCreation {
        override val typeId = CounterType
        override val title = UiText.Verbatim("Counter")
        override val pageTitle = title
        override val description = uiText(R.string.settings_new_measurement_description)
        override val icon = ViewIcon.Dumbbell.vector
        override val testTag = "settings_gallery_counter_new"

        @Composable
        override fun Setup(host: TrackerSettingsHost) {
            FollowPageLifecycle(host)
            ViewTheme(counterTrackerIdentity().colors) {
                Column(host.modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // These labels stand in for names entered by a person, just as other gallery fixtures do.
                    Text("Pull-ups")
                    MomentumButton(
                        text = stringResource(R.string.checklist_editor_add),
                        onClick = {
                            views = views + CounterTrackerView("pull-ups-${views.size}", "Pull-ups")
                            host.onClose()
                        },
                        modifier = Modifier.testTag("gallery_counter_create"),
                    )
                }
            }
        }
    }
}

private class CounterTrackerView(id: String, override val label: String) : TrackerView {
    override val ref = TrackerRef(CounterType, id)
    override val tagName = "gallery_counter_$id"
    override var visible by mutableStateOf(true)
        private set
    override val reminders = emptyList<TrackerReminderCommand>()
    override val opensOnReminderDate = false
    private var count by mutableIntStateOf(0)
    private var recordedDates by mutableStateOf(emptySet<String>())
    override val statistics get() = de.nicolasmahn.momentum.domain.TrackerStatistics(recordedDates)

    override fun setVisible(visible: Boolean): Boolean {
        this.visible = visible
        return true
    }

    @Composable
    override fun identity(): ViewIdentity = counterTrackerIdentity()

    @Composable
    override fun Screen(host: TrackerScreenHost) {
        ViewTheme(identity().colors) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = formatMetricNumber(count.toDouble(), currentLocale()),
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.testTag("gallery_counter_value"),
                )
                MomentumButton(
                    text = stringResource(R.string.checklist_editor_add),
                    onClick = { count++; recordedDates = recordedDates + host.todayKey },
                    modifier = Modifier.testTag("gallery_counter_add"),
                )
            }
        }
    }

    @Composable
    override fun Settings(host: TrackerSettingsHost) {
        FollowPageLifecycle(host)
        ViewTheme(identity().colors) {
            Column(host.modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(label)
                MomentumButton(
                    text = stringResource(R.string.questions_help_close),
                    onClick = host.onClose,
                    modifier = Modifier.testTag("gallery_counter_settings_close"),
                )
            }
        }
    }
}

/** The demonstration pages have no draft; leaving never discards a counter change. */
@Composable
private fun FollowPageLifecycle(host: TrackerSettingsHost) {
    SideEffect { host.onUnsavedWorkChange(false) }
    LaunchedEffect(host.leaveRequested) {
        if (host.leaveRequested) host.onLeaveAnswered(true)
    }
}

private fun galleryTimeSessions() = TimeSessions(
    store = object : TimeSessionStore {
        override var session: TimeSession? = null
        override var focusChange: FocusChange? = null
        override var focusAsked = true
        override var note: TimeSessionNote? = null
        override fun writeSession(session: TimeSession?): Boolean {
            this.session = session
            return true
        }
    },
    doNotDisturb = object : DoNotDisturb {
        override val hasAccess = true
        override var filter = NotificationManager.INTERRUPTION_FILTER_ALL
    },
)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun TrackerModulePreview() {
    WorkoutTrackerTheme { trackerModuleSpecimens.first().content() }
}
