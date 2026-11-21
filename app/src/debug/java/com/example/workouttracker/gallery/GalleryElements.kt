package com.example.workouttracker.gallery

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.ViewTheme
import java.util.Locale

/** Building blocks the gallery itself is made of. Product code never uses these. */
@Composable
internal fun SpecimenList(specimens: List<GallerySpecimen>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        specimens.forEach { specimen ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = specimen.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                specimen.content()
            }
        }
    }
}

@Composable
internal fun SwatchGrid(
    swatches: List<GalleryColorRole>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        swatches.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { swatch ->
                    Swatch(swatch = swatch, modifier = Modifier.weight(1f))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Swatch(swatch: GalleryColorRole, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(SwatchShape)
                .background(swatch.color)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = SwatchShape,
                ),
        )
        Text(
            text = swatch.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

private val SwatchShape = RoundedCornerShape(8.dp)

@Composable
internal fun IconTile(
    icon: ImageVector,
    name: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Where a view's palette is judged: every [com.example.workouttracker.core.ui.ViewColors] role by
 * name, then the real controls wearing it. Everything renders inside the view's own theme, so the
 * buttons and Material controls take their colors exactly the way they do on the view's screen.
 */
@Composable
internal fun IdentityShowcase(identity: GalleryIdentity, modifier: Modifier = Modifier) {
    if (identity.isOwned) {
        ViewTheme(identity.colors) { IdentityBody(identity, modifier) }
    } else {
        IdentityBody(identity, modifier)
    }
}

@Composable
private fun IdentityBody(identity: GalleryIdentity, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconTile(
            icon = identity.icon,
            name = identity.name,
            containerColor = identity.colors.container,
            contentColor = identity.colors.onContainer,
        )
        identity.fallback?.let { SpecimenCaption(it) }
        SwatchGrid(identity.colors.roleSwatches(), columns = 4)
        MomentumButtonVariant.entries.forEach { variant ->
            MomentumButton(
                text = variant.name,
                onClick = {},
                variant = variant,
                icon = identity.icon.takeIf { variant == MomentumButtonVariant.Primary },
            )
        }
        IdentityControls()
    }
}

/** The Material controls that follow the theme's primary, so a view's accent can be judged on them. */
@Composable
private fun IdentityControls() {
    var switched by remember { mutableStateOf(true) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = switched, onCheckedChange = { switched = it })
        FilterChip(selected = true, onClick = {}, label = { Text("Selected") })
    }
    // Focus cannot be held by ten specimens at once, so the focused colors are applied directly.
    OutlinedTextField(
        value = "Typed text",
        onValueChange = {},
        readOnly = true,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Focused") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun SpecimenCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A full screen brings its own vertical scroll, which cannot measure inside the gallery's scroll.
 * A fixed height gives it bounded constraints and frames it as a specimen.
 */
@Composable
internal fun ScreenFrame(
    modifier: Modifier = Modifier,
    height: Dp = 420.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/**
 * [content] in the language [languageTag] names, whatever the app speaks, so one specimen can set
 * the English words beside the German ones they must fit as well.
 */
@Composable
internal fun InLanguage(languageTag: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = LocalConfiguration.current
    val configuration = remember(base, languageTag) { Configuration(base).apply { setLocale(Locale.forLanguageTag(languageTag)) } }
    val speaking = remember(context, configuration) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(
        LocalContext provides speaking,
        LocalConfiguration provides configuration,
        LocalResources provides speaking.resources,
        content = content,
    )
}
