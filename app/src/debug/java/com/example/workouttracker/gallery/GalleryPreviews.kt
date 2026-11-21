package com.example.workouttracker.gallery

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workouttracker.app.AppDestination
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme

/** Previews render the same specimens the gallery does, so the two can never drift. */
@Composable
private fun SectionPreview(section: GallerySection) {
    WorkoutTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SpecimenList(
                specimens = gallerySpecimens(section),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun ViewPreview(destination: AppDestination) =
    SectionPreview(GallerySection.View(destination))

@Preview
@Composable
private fun GenericPreview() = SectionPreview(GallerySection.Generic)

@Preview
@Composable
private fun HomePreview() = ViewPreview(AppDestination.Home)

@Preview
@Composable
private fun ActivityPreview() = ViewPreview(AppDestination.Activity)

@Preview
@Composable
private fun ChecklistPreview() = ViewPreview(AppDestination.Checklist)

@Preview
@Composable
private fun MeasurementPreview() = ViewPreview(AppDestination.Measurement)

@Preview
@Composable
private fun QuestionsPreview() = ViewPreview(AppDestination.Questions)

@Preview
@Composable
private fun TimePreview() = ViewPreview(AppDestination.Time)

@Preview
@Composable
private fun SubstancesPreview() = ViewPreview(AppDestination.Substances)

@Preview
@Composable
private fun StatisticsPreview() = ViewPreview(AppDestination.Statistics)

@Preview
@Composable
private fun SettingsPreview() = ViewPreview(AppDestination.Settings)
