package de.nicolasmahn.momentum.feature.tracker

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.neutralViewColors
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerStatistics
import de.nicolasmahn.momentum.domain.TrackerTypeId

/** A kind unknown to LoggedViewKind, with no production configuration or record model. */
internal class TestTrackerModule(
    override val typeId: TrackerTypeId = TrackerTypeId("counter"),
    override val views: List<TrackerView> = listOf(TestTrackerView(TrackerRef(typeId, "water"))),
    override val creation: TrackerCreation? = TestTrackerCreation(typeId),
    override val rewards: List<de.nicolasmahn.momentum.domain.reward.TrackerReward> = emptyList(),
    override val observations: List<de.nicolasmahn.momentum.domain.mo.TrackerObservation> = emptyList(),
    override val chosenPasses: Set<TrackerRef> = emptySet(),
) : TrackerModule

internal class TestTrackerView(
    override val ref: TrackerRef,
    override val reminders: List<TrackerReminderCommand> = emptyList(),
    override val opensOnReminderDate: Boolean = true,
    override val onSetAside: ((Boolean) -> Unit)? = null,
    override val statistics: TrackerStatistics? = null,
    private val screenContent: @Composable (TrackerScreenHost) -> Unit = {},
    private val settingsContent: @Composable (TrackerSettingsHost) -> Unit = {},
) : TrackerView {
    override val label = "Water counter"
    override val tagName = "${ref.type.value}_${ref.id}"
    override var visible: Boolean = true
        private set

    override fun setVisible(visible: Boolean): Boolean {
        this.visible = visible
        return true
    }

    @Composable
    override fun identity() = ViewIdentity(Icons.Rounded.Circle, neutralViewColors)

    @Composable
    override fun Screen(host: TrackerScreenHost) {
        Box(Modifier.testTag("${tagName}_screen"))
        screenContent(host)
    }

    @Composable
    override fun Settings(host: TrackerSettingsHost) {
        Box(Modifier.testTag("${tagName}_settings"))
        settingsContent(host)
    }
}

internal class TestTrackerCreation(override val typeId: TrackerTypeId) : TrackerCreation {
    override val title = uiText(R.string.core_completion_completed)
    override val pageTitle = uiText(R.string.core_completion_completed)
    override val description = uiText(R.string.core_completion_not_completed)
    override val icon = Icons.Rounded.Circle
    override val testTag = "${typeId.value}_new"

    @Composable
    override fun Setup(host: TrackerSettingsHost) {
        Box(Modifier.testTag("${typeId.value}_setup"))
    }
}
