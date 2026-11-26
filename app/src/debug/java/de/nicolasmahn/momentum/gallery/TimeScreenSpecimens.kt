package de.nicolasmahn.momentum.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.ui.DayCover
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.changingDay
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.time.MadeTimeWhenAway
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.time.TimeLogOrder
import de.nicolasmahn.momentum.feature.time.sessionAppearance
import de.nicolasmahn.momentum.feature.time.words
import de.nicolasmahn.momentum.feature.time.TimeScreen
import de.nicolasmahn.momentum.feature.time.TimeSessionNote
import de.nicolasmahn.momentum.feature.time.TimeSessionScreen
import java.time.LocalTime

/** The reading preset uses the timer screen with its configured yellow identity and book motif. */
internal val readingScreenSpecimens = listOf(
    GallerySpecimen("TimeScreen, Reading") {
        // A week a token closed though it was read in: the mark sits under a logged day's date.
        TimeScreenSpecimen(DemoReadingTime, testTagPrefix = "gallery_reading_screen", covers = mapOf(fixtureDateKey(2) to DayCover.Token))
    },
    GallerySpecimen("TimeSessionScreen, Reading") { TimeSessionSpecimen(DemoReadingTime, minutes = 23) },
    GallerySpecimen("TimeSessionScreen, Reading, save failed") { TimeSessionSpecimen(DemoReadingTime, minutes = 41, saveFailed = true) },
    GallerySpecimen("TimeSessionScreen, Reading on the flavor Plain") { TimeSessionSpecimen(DemoReadingTime.copy(mo = DemoReadingTime.mo.copy(motif = null)), minutes = 23) },
)

/** Different timer configurations exercise labels, empty histories, session notes and motifs. */
internal val madeTimeScreenSpecimens = listOf(
    GallerySpecimen("TimeScreen, a made view") { TimeScreenSpecimen(DemoPiano) },
    GallerySpecimen("TimeScreen, after a session under a minute") {
        TimeScreenSpecimen(DemoPiano, note = TimeSessionNote(DemoPiano.id, FixtureTodayKey, minutes = 0, leftApp = true))
    },
    GallerySpecimen("TimeScreen, nothing logged") { TimeScreenSpecimen(DemoPiano, days = 0) },
    GallerySpecimen("TimeSessionScreen, a made view") { TimeSessionSpecimen(DemoPiano, minutes = 23) },
    GallerySpecimen("TimeSessionScreen, a long name") { TimeSessionSpecimen(DemoLongNamedTime, minutes = 1234, saveFailed = true) },
    GallerySpecimen("TimeSessionScreen, a made view on the flavor Reading") { TimeSessionSpecimen(DemoPiano.copy(mo = DemoPiano.mo.copy(motif = MoMotif.Book)), minutes = 23) },
)

/** Ninety minutes a week, so its numbers are not Reading's. */
internal val DemoPiano = madeTime("tim_p1an", "Piano", ViewIcon.Music, ViewHue.Plum, goal = 90)
private val DemoLongNamedTime = madeTime("tim_l0ng", "Practising the cello suites for the recital in spring", ViewIcon.Music, ViewHue.Sky, goal = 600)

private fun madeTime(id: String, label: String, icon: ViewIcon, hue: ViewHue, goal: Int) = TimeConfiguration(
    id = id,
    label = label,
    icon = icon,
    hue = hue,
    goal = Dated.always(goal),
    rewards = null,
    whenAway = MadeTimeWhenAway,
    checkpoint = Checkpoint(id = DayCheckpointId, at = CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false),
)

/**
 * The real screen over fake logs, saving into memory so minutes can be added and a day removed.
 * The state holds Reading's logs beside the made view's on the same days, so a specimen also shows
 * that a screen reads its own, and two of Reading's are marks without minutes, as its oldest are.
 * Start does nothing here: the session is the app's, and has specimens of its own. It scrolls
 * itself, so it needs a frame of fixed height.
 */
@Composable
private fun TimeScreenSpecimen(
    view: TimeConfiguration,
    days: Int = 24,
    note: TimeSessionNote? = null,
    testTagPrefix: String = "gallery_${view.id}",
    covers: Map<String, DayCover> = emptyMap(),
) {
    var state by remember {
        mutableStateOf(
            AppState(
                time = listOf(DemoReadingTime, DemoPiano),
                // Yesterday back to [days] ago, a few days left out.
                timeLogs = (1..days).filter { it % 4 != 0 }.flatMap { daysAgo ->
                    listOf(
                        TimeLog(DemoPiano.id, fixtureDateKey(daysAgo), timestampMillis = 1, minutes = 10 + daysAgo % 5 * 5),
                        TimeLog(DemoReadingId, fixtureDateKey(daysAgo), timestampMillis = 1, minutes = (15 + daysAgo % 3 * 10).takeIf { daysAgo % 9 != 0 }),
                    )
                }.sortedWith(TimeLogOrder),
            ),
        )
    }
    ScreenFrame(height = 760.dp) {
        TimeScreen(
            state = state,
            view = view,
            words = view.words(),
            onDayChange = { change -> state.changingDay(change, timestampMillis = 2) { updated -> state = updated; true } },
            todayKey = FixtureTodayKey,
            sessionNote = note,
            testTagPrefix = testTagPrefix,
            covers = covers,
        )
    }
}

/** The session as it runs, at a count that never moves. */
@Composable
private fun TimeSessionSpecimen(view: TimeConfiguration, minutes: Int, saveFailed: Boolean = false) {
    ScreenFrame(height = 560.dp) {
        TimeSessionScreen(minutes = minutes, saveFailed = saveFailed, appearance = view.sessionAppearance(), onDone = {})
    }
}
