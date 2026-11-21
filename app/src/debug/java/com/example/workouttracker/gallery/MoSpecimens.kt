package com.example.workouttracker.gallery



import com.example.workouttracker.domain.trackerRef

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.domain.mo.MoContentKey
import com.example.workouttracker.domain.mo.MoFigureContent
import com.example.workouttracker.domain.mo.MoEvent
import com.example.workouttracker.domain.mo.MoNeglect
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.domain.mo.MoViewKind
import com.example.workouttracker.domain.mo.contentKey
import com.example.workouttracker.domain.mo.figure
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.mo.Mo
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoEventTakeover
import com.example.workouttracker.feature.mo.PassToken
import com.example.workouttracker.feature.mo.RestToken
import com.example.workouttracker.feature.mo.drawFreezeCube
import com.example.workouttracker.feature.mo.drawStreakCoin
import com.example.workouttracker.feature.mo.MoLook
import com.example.workouttracker.feature.mo.MoNeglectedDrawing
import com.example.workouttracker.feature.mo.MoTaskStanding
import com.example.workouttracker.feature.mo.acts.MoActBook
import com.example.workouttracker.feature.mo.acts.MoScene
import com.example.workouttracker.feature.mo.acts.MoVariant

internal val moSpecimens: List<GallerySpecimen> = listOf(
    GallerySpecimen("Mo event, a special moment takes over Home (tap Continue for the next)") {
        ScreenFrame(height = 720.dp) {
            var round by remember { mutableIntStateOf(0) }
            val mood = remember {
                moMood(fixtureAppState, FeatureVisibilitySettings(disabledFeatures = emptySet()), requireNotNull(parseDateKey(FixtureTodayKey)))
            }
            val events = listOfNotNull(
                MoEvent.TokenEarned(DemoTask.Weight.view.trackerRef),
                mood.weeklyGoalTrackers.firstOrNull { it in mood.weekProgressByTracker }?.let(MoEvent::WeekGoalReached),
                MoEvent.StreakMilestone(30),
                MoEvent.FreezeEarned,
                MoEvent.PerfectWeek,
                MoEvent.Comeback,
            )
            key(round) { MoEventTakeover(events[round % events.size], mood, onDone = { round += 1 }) }
        }
    },
    GallerySpecimen("Mo's tokens: a pass per habit, an empty bank, rest days and a freeze") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DemoTask.entries.forEach { PassToken(it.view.trackerRef, Modifier.size(48.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PassToken(DemoTask.Weight.view.trackerRef, Modifier.size(48.dp), empty = true)
                RestToken(Modifier.size(40.dp))
                RestToken(Modifier.size(40.dp), empty = true)
                Canvas(Modifier.size(40.dp)) { drawFreezeCube() }
                Canvas(Modifier.size(40.dp)) { drawStreakCoin() }
            }
        }
    },
    GallerySpecimen("A task header's standing: tokens, Activity's rest days, empty banks, a reserved token, a rest day taken, and a week's tokens") {
        val rows = listOf(
            DemoTask.Activity to (2 to 1),
            DemoTask.Activity to (0 to 0),
            DemoTask.Weight to (null to 3),
        )
        var reserved by remember { mutableStateOf(false) }
        var resting by remember { mutableStateOf(false) }
        var weekReserved by remember { mutableStateOf(false) }
        var quiet by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) {
            rows.forEach { (need, standing) ->
                ViewTheme(demoTrackerIdentity(need.view.trackerRef).colors) {
                    MoTaskStanding(need.view.trackerRef, label = demoTrackerLabel(need), tokens = standing.second, restDays = standing.first)
                }
            }
            // Still open today with tokens in the bank, so pressing the tokens reserves one for today.
            ViewTheme(demoTrackerIdentity(DemoTask.Eating.view.trackerRef).colors) {
                MoTaskStanding(DemoTask.Eating.view.trackerRef, label = demoTrackerLabel(DemoTask.Eating), tokens = if (reserved) 1 else 2, restDays = null, reserved = reserved, onReserve = { reserved = it })
            }
            // A planned day a rest day covers: pressing the rest days says today is one, and holds no token.
            ViewTheme(demoTrackerIdentity(DemoTask.Activity.view.trackerRef).colors) {
                MoTaskStanding(DemoTask.Activity.view.trackerRef, label = demoTrackerLabel(DemoTask.Activity), tokens = 1, restDays = 2, resting = resting, onRest = { resting = it })
            }
            // Before its Sunday, the same week: Mo's "Zz" quiets him for today, and the tokens stay.
            ViewTheme(demoTrackerIdentity(DemoTask.Reading.view.trackerRef).colors) {
                MoTaskStanding(DemoTask.Reading.view.trackerRef, label = demoTrackerLabel(DemoTask.Reading), tokens = 3, restDays = null, quiet = quiet, onQuiet = { quiet = it })
            }
            // On its Sunday, a week short of its goal: the tokens are reserved for the week, as many as it lacks.
            ViewTheme(demoTrackerIdentity(DemoTask.Reading.view.trackerRef).colors) {
                MoTaskStanding(DemoTask.Reading.view.trackerRef, label = demoTrackerLabel(DemoTask.Reading), tokens = if (weekReserved) 1 else 3, restDays = null, reserved = weekReserved, onReserve = { weekReserved = it }, thisWeek = true)
            }
        }
    },
    GallerySpecimen("Mo, five states") {
        MoRow(MoState.entries.reversed().map { MoLook(it) })
    },
    GallerySpecimen("Mo, doing what was just logged") {
        MoRow(
            listOf(
                MoLook(MoState.Steady, doing = DemoTask.Reading.figure.saveAct),
                MoLook(MoState.Blazing, doing = DemoTask.Activity.figure.saveAct),
                MoLook(MoState.Steady, doing = DemoTask.Care.figure.saveAct),
            ),
        )
    },
    GallerySpecimen("Mo, doing the quieter things") {
        MoRow(
            listOf(
                MoLook(MoState.Steady, doing = DemoTask.Eating.figure.saveAct),
                MoLook(MoState.Steady, doing = DemoTask.Supplements.figure.saveAct),
                MoLook(MoState.Blazing, doing = DemoTask.Weight.figure.saveAct),
            ),
        )
    },
    GallerySpecimen("Mo, a checklist without a motif") {
        val content = MoContentKey(MoViewKind.Checklist).figure
        MoRow(
            listOf(
                MoLook(MoState.Steady, neglect = listOf(MoNeglectedDrawing(content.prop, MoNeglect.Severe))),
                MoLook(MoState.Steady, doing = content.saveAct),
                MoLook(MoState.Blazing, thriving = setOf(content.thrivingLook)),
            ),
        )
    },
    GallerySpecimen("Mo, angry about something overdue") {
        MoRow(
            listOf(
                MoLook(MoState.Blazing, angry = true),
                MoLook(MoState.Steady, angry = true, neglect = listOf(DemoTask.Care.neglected(MoNeglect.Severe))),
                MoLook(MoState.Dimming, angry = true),
            ),
        )
    },
    GallerySpecimen("Mo, thriving") {
        MoRow(
            listOf(
                MoLook(MoState.Blazing, thriving = setOf(DemoTask.Activity.figure.thrivingLook)),
                MoLook(MoState.Steady, thriving = setOf(DemoTask.Reading.figure.thrivingLook)),
                MoLook(MoState.Steady, thriving = setOf(DemoTask.Care.figure.thrivingLook)),
                MoLook(MoState.Blazing, thriving = setOf(DemoTask.Activity, DemoTask.Reading, DemoTask.Care).map { it.figure.thrivingLook }.toSet()),
            ),
        )
    },
    GallerySpecimen("Mo, frozen by a freeze") {
        MoRow(
            listOf(
                MoLook(MoState.Steady, frozen = true),
                MoLook(MoState.Dimming, frozen = true),
                MoLook(MoState.Cold, frozen = true),
            ),
        )
    },
    GallerySpecimen("Mo, neglected Care") {
        MoRow(
            listOf(
                MoLook(MoState.Steady, listOf(DemoTask.Care.neglected(MoNeglect.Mild))),
                MoLook(MoState.Steady, listOf(DemoTask.Care.neglected(MoNeglect.Severe))),
                MoLook(MoState.Steady, listOf(DemoTask.Care.neglected(MoNeglect.Dire))),
                MoLook(MoState.Cold, listOf(DemoTask.Care.neglected(MoNeglect.Dire))),
            ),
        )
    },
    GallerySpecimen("Mo, what lies beside him") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MoRow(
                listOf(
                    MoLook(MoState.Steady, listOf(DemoTask.Reading.neglected(MoNeglect.Mild), DemoTask.Activity.neglected(MoNeglect.Severe))),
                    MoLook(MoState.Dimming, listOf(DemoTask.Supplements.neglected(MoNeglect.Mild), DemoTask.Eating.neglected(MoNeglect.Severe))),
                    MoLook(MoState.Cold, listOf(DemoTask.Weight.neglected(MoNeglect.Severe), DemoTask.Care.neglected(MoNeglect.Severe))),
                ),
            )
            Text(
                text = "Only the two most neglected tasks lie beside him. Care always shows in his mouth.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    },
)

private fun DemoTask.neglected(level: MoNeglect) = MoNeglectedDrawing(figure.prop, level, ordinal)

private val fixturePresentationMood by lazy {
    moMood(fixtureAppState, FeatureVisibilitySettings(disabledFeatures = emptySet()), requireNotNull(parseDateKey(FixtureTodayKey)))
}

/** Specimens use the same configured content as the evaluated trackers. */
private val DemoTask.figure: MoFigureContent
    get() = fixturePresentationMood.contentKey(view.trackerRef).figure

@Composable
private fun MoRow(looks: List<MoLook>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        looks.forEach { Mo(it, Modifier.weight(1f)) }
    }
}

/**
 * Every act, alive, grouped by the occasion that plays it: the place to look at an act without
 * waiting for its day. Each is shown on the first body it claims.
 */
internal val moActSpecimens: List<GallerySpecimen> = MoActBook.groupBy { it.occasion }.toSortedMap().map { (occasion, acts) ->
    GallerySpecimen("Mo acts, $occasion (${acts.size})") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            acts.sortedBy { it.id }.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { act ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Mo(
                                MoLook(
                                    state = act.bodies.first().specimenState(),
                                    frozen = occasion == MoOccasion.Frozen,
                                    scene = MoScene(act, MoVariant(1)),
                                ),
                            )
                            Text(
                                text = act.id.substringAfter('_').replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun MoBodyKind.specimenState(): MoState = when (this) {
    MoBodyKind.Tall -> MoState.Steady
    MoBodyKind.Low -> MoState.Dimming
    MoBodyKind.Droplet -> MoState.Cold
    MoBodyKind.Husk -> MoState.Husk
}
