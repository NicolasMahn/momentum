package com.example.workouttracker.feature.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.MomentumActionBar
import com.example.workouttracker.core.ui.MomentumBarAction
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumTabs
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.feature.mo.Mo
import com.example.workouttracker.feature.mo.MoLook

/**
 * The first start walks Settings' own parts in this order, each the page Settings has: what is
 * tracked, then sleep, which reminders and Mo read, then what keeps the records and the reminders
 * working. Nothing in it is chosen for the person.
 */
internal val FirstStartSteps: List<SettingsPage> =
    listOf(SettingsPage.Views, SettingsPage.Sleep, SettingsPage.Backups, SettingsPage.Reminders)

@Composable
private fun SettingsPage.stepName(): String = stringResource(
    when (this) {
        SettingsPage.Views -> R.string.settings_first_start_track
        SettingsPage.Sleep -> R.string.settings_sleep_title
        SettingsPage.Backups -> R.string.settings_backups_title
        else -> R.string.settings_reminders_title
    },
)

/** Mo, what the app is in one line, and the one way on at the bottom, in reach of a thumb. */
@Composable
internal fun FirstStartWelcome(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Mo and the line take the room left and scroll in it, so on a small phone with a large
        // font the way on below them is never pushed out.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // He draws a little past his bounds, so the words under him keep clear of his feet.
            Mo(look = MoLook(state = MoState.Blazing), modifier = Modifier.size(200.dp).padding(bottom = 16.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_first_start_welcome),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        MomentumButton(
            text = stringResource(R.string.settings_first_start_begin),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            testTag = "first_start_begin",
        )
    }
}

/**
 * One step of the first start: the steps as numbered tabs over the step's page ([content]), and
 * Next under it, Done on the last; Back is the header's arrow or the gesture. A step ahead opens
 * only once the open one can be left ([canContinue]): an app with nothing to track has nothing
 * for the rest to serve.
 */
@Composable
internal fun FirstStartFrame(
    step: SettingsPage,
    onStep: (SettingsPage) -> Unit,
    onDone: () -> Unit,
    /** Whether the open step can be left forward, as Track waits for something to track. */
    canContinue: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val index = FirstStartSteps.indexOf(step)
    val last = index == FirstStartSteps.lastIndex
    Column(modifier = modifier.fillMaxSize()) {
        MomentumTabs(
            tabs = FirstStartSteps,
            selected = step,
            onSelect = onStep,
            enabled = { FirstStartSteps.indexOf(it) <= index || canContinue },
            label = { stringResource(R.string.checklist_setup_step, FirstStartSteps.indexOf(it) + 1, it.stepName()) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            testTag = { "first_start_step_${it.key}" },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        MomentumActionBar {
            MomentumBarAction(
                text = stringResource(if (last) R.string.settings_first_start_done else R.string.checklist_setup_next),
                onClick = { if (last) onDone() else onStep(FirstStartSteps[index + 1]) },
                ready = canContinue,
                testTag = if (last) "first_start_done" else "first_start_next",
            )
        }
    }
}
