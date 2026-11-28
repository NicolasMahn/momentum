package de.nicolasmahn.momentum.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import de.nicolasmahn.momentum.app.HomeDayPicture
import de.nicolasmahn.momentum.app.HomeDayPictureStyle
import de.nicolasmahn.momentum.app.HomeRow
import de.nicolasmahn.momentum.app.HomeRowDefaults
import de.nicolasmahn.momentum.app.WeekDay
import de.nicolasmahn.momentum.core.ui.theme.MomentumHue
import de.nicolasmahn.momentum.core.ui.theme.MomentumHues
import de.nicolasmahn.momentum.core.ui.viewColors
import de.nicolasmahn.momentum.feature.tracker.TrackerHomeStatus

/** Home's rows in every look the gallery offers, with one sample of each kind of day picture. */
internal val homeRowSpecimens: List<GallerySpecimen> = listOf(
    GallerySpecimen("HomeRow, bars (Home)") { HomeRowsSpecimen(HomeDayPictureStyle.Bars) },
    GallerySpecimen("HomeRow, dots") { HomeRowsSpecimen(HomeDayPictureStyle.Dots) },
    GallerySpecimen("HomeRow, a day in progress and a day done") { HomeRowStatesSpecimen() },
    GallerySpecimen("HomeRow, a rest day, a quiet day and days a token covers") {
        val statuses = mapOf(2 to TrackerHomeStatus.Quiet, 3 to TrackerHomeStatus.Rest)
        HomeRowsSpecimen(HomeDayPictureStyle.Bars, sampleRows.mapIndexed { index, row -> SampleRow(row.title, row.icon, row.hue, row.picture, statuses[index] ?: TrackerHomeStatus.Covered) })
    },
)

private class SampleRow(
    val title: String,
    val icon: ImageVector,
    val hue: MomentumHue,
    val picture: HomeDayPicture,
    val status: TrackerHomeStatus,
)

private val sampleRows = listOf(
    SampleRow("Tagesplan", Icons.Rounded.Schedule, MomentumHues.Yellow, HomeDayPicture.Segments(listOf(1f, 0.5f, 0f), grouped = true), TrackerHomeStatus.Open(1, 3)),
    SampleRow("Morgens", Icons.Rounded.Checklist, MomentumHues.Orange, HomeDayPicture.Segments(listOf(1f, 1f, 1f, 0f)), TrackerHomeStatus.Open(0, 1)),
    SampleRow("Lesen", Icons.AutoMirrored.Rounded.MenuBook, MomentumHues.Teal, HomeDayPicture.Segments(listOf(0.55f)), TrackerHomeStatus.Open(0, 1)),
    SampleRow("Aktivität", Icons.Rounded.Hiking, MomentumHues.Green, sampleWeek(WeekDay.Recorded, WeekDay.Empty, WeekDay.Recorded, WeekDay.Today), TrackerHomeStatus.None),
    SampleRow("Substanzen", Icons.Rounded.Science, MomentumHues.Pink, sampleWeek(WeekDay.Recorded, WeekDay.Recorded, WeekDay.Recorded, WeekDay.Recorded), TrackerHomeStatus.Kept),
    SampleRow("Gewicht", Icons.Rounded.MonitorWeight, MomentumHues.Sky, HomeDayPicture.Trend(listOf(81.2, 81.0, 81.4, 80.9, 81.1, 80.7, 80.8)), TrackerHomeStatus.Kept),
    SampleRow("Tagebuch", Icons.Rounded.EditNote, MomentumHues.Purple, sampleWeek(WeekDay.Recorded, WeekDay.Empty, WeekDay.Empty, WeekDay.Today), TrackerHomeStatus.Open(0, 1)),
)

/** A week whose first days are [past], Thursday's on, and the rest still to come. */
private fun sampleWeek(vararg past: WeekDay) = HomeDayPicture.Week(past.toList() + List(7 - past.size) { WeekDay.Ahead })

@Composable
private fun HomeRowsSpecimen(style: HomeDayPictureStyle, rows: List<SampleRow> = sampleRows) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HomeRowDefaults.Gap)) {
        rows.forEach { row ->
            HomeRow(
                title = row.title,
                icon = row.icon,
                colors = row.hue.viewColors(),
                picture = row.picture,
                status = row.status,
                onClick = {},
                testTag = "gallery_home_${row.title}",
                style = style,
            )
        }
    }
}

/** The same views once each is done: every segment full, today recorded, every check set. */
@Composable
private fun HomeRowStatesSpecimen() = HomeRowsSpecimen(
    HomeDayPictureStyle.Bars,
    sampleRows.map { row ->
        val done = when (val picture = row.picture) {
            is HomeDayPicture.Segments -> picture.copy(fills = picture.fills.map { 1f })
            is HomeDayPicture.Week -> picture.copy(days = picture.days.map { if (it == WeekDay.Today) WeekDay.Recorded else it })
            else -> picture
        }
        SampleRow(row.title, row.icon, row.hue, done, TrackerHomeStatus.Kept)
    },
)
