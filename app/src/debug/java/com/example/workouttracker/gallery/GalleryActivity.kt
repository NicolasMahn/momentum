package com.example.workouttracker.gallery

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.workouttracker.app.destination
import com.example.workouttracker.core.ui.LocalTrackerIdentity
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumCard
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.core.ui.theme.enableDarkEdgeToEdge

/**
 * Debug-only launcher for the component gallery. It hosts the real shared components with fake
 * local state so specimens can be clicked on an emulator without touching any repository.
 */
class GalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        setContent { GalleryScreen() }
    }
}

@Composable
internal fun GalleryScreen() {
    var openSectionId by rememberSaveable { mutableStateOf<String?>(null) }
    val openSection = openSectionId?.let(::gallerySection)

    WorkoutTrackerTheme {
        // Each task of Mo's wears the look it was built with, as the app shows it before a view stores its own.
        CompositionLocalProvider(LocalTrackerIdentity provides { ref -> demoTrackerIdentity(ref) }) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (openSection == null) {
                        GalleryIndex(onOpen = { openSectionId = it.id })
                    } else {
                        BackHandler { openSectionId = null }
                        GallerySectionScreen(
                            section = openSection,
                            onBack = { openSectionId = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryIndex(onOpen: (GallerySection) -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gallerySections.forEach { section ->
            GalleryIndexRow(section = section, onClick = { onOpen(section) })
        }
        DemoDataPanel(modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun GalleryIndexRow(section: GallerySection, onClick: () -> Unit) {
    val identity = when (section) {
        GallerySection.Generic -> null
        is GallerySection.View -> viewIdentities(section.destination).first()
    }
    MomentumCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = identity?.icon ?: Icons.Rounded.Widgets,
                contentDescription = null,
                tint = identity?.colors?.accent ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(text = section.title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun GallerySectionScreen(section: GallerySection, onBack: () -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MomentumButton(
                text = "Back",
                onClick = onBack,
                variant = MomentumButtonVariant.Quiet,
            )
            Text(text = section.title, style = MaterialTheme.typography.titleLarge)
        }
        SpecimenList(gallerySpecimens(section))
    }
}
