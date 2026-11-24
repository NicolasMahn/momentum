package de.nicolasmahn.momentum.core.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb

/**
 * Edge to edge with light system-bar icons. Plain `enableEdgeToEdge()` picks the icons from the
 * phone's setting, which on a phone set to light would draw them dark on the app's dark ground.
 */
fun ComponentActivity.enableDarkEdgeToEdge() {
    val bars = SystemBarStyle.dark(MomentumTransparent.toArgb())
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
}
