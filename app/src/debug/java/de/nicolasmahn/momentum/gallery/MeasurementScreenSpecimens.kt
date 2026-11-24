package de.nicolasmahn.momentum.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.measurement.DefaultMeasurementCheckpoint
import de.nicolasmahn.momentum.domain.measurement.MadeMeasurementWhenAway
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.MeasurementStep
import de.nicolasmahn.momentum.feature.measurement.MeasurementScreen
import de.nicolasmahn.momentum.domain.changingDay

/**
 * The measurement screen in Weight's words, as Weight's destination shows it, and Weight in pounds
 * and steps of 0.5, where it speaks as a measurement a person made does and says kilograms nowhere.
 */
internal val weightScreenSpecimens = listOf(
    GallerySpecimen("MeasurementScreen, Weight") { MeasurementScreenSpecimen(DemoWeight, start = 82.4, drift = -0.1) },
    GallerySpecimen("MeasurementScreen, Weight in pounds") { MeasurementScreenSpecimen(DemoWeightInPounds, start = 181.5, drift = -0.2) },
)

/**
 * The same screen in the words of measurements a person made: one with a unit, one without, and
 * one whose unit is as long as a unit may be, so its place beside the large number can be judged.
 */
internal val madeMeasurementScreenSpecimens = listOf(
    GallerySpecimen("MeasurementScreen, a made measurement") { MeasurementScreenSpecimen(DemoWaist, start = 92.5, drift = -0.1) },
    GallerySpecimen("MeasurementScreen, without a unit") { MeasurementScreenSpecimen(DemoMood, start = 6.0, drift = 0.0) },
    GallerySpecimen("MeasurementScreen, a long unit") { MeasurementScreenSpecimen(DemoPulse, start = 61.0, drift = 0.0) },
    GallerySpecimen("MeasurementScreen, steps of 0.01") { MeasurementScreenSpecimen(DemoBodyFat, start = 0.21, drift = -0.0005) },
    GallerySpecimen("MeasurementScreen, signed values") { MeasurementScreenSpecimen(DemoMood, start = -2.0, drift = 0.2) },
    GallerySpecimen("MeasurementScreen, nothing logged") { MeasurementScreenSpecimen(DemoWaist, start = 0.0, drift = 0.0, days = 0) },
)

private val DemoWeight = sampleWeightMeasurement(label = "Weight")
private val DemoWeightInPounds = DemoWeight.copy(unit = "lb", step = MeasurementStep("0.5"))
internal val DemoWaist = madeMeasurement("msr_wa1s", "Waist", ViewIcon.Ruler, ViewHue.Plum, unit = "cm", step = "0.1")
internal val DemoMood = madeMeasurement("msr_m00d", "Mood", ViewIcon.Mood, ViewHue.Sky, unit = "", step = "1")
private val DemoPulse = madeMeasurement("msr_pu1s", "Resting pulse", ViewIcon.Pulse, ViewHue.Plum, unit = "beats / min.", step = "1")
private val DemoBodyFat = madeMeasurement("msr_fat1", "Body fat", ViewIcon.Ruler, ViewHue.Sky, unit = "ratio", step = "0.01")

private fun madeMeasurement(id: String, label: String, icon: ViewIcon, hue: ViewHue, unit: String, step: String) =
    MeasurementConfiguration(
        id = id,
        label = label,
        icon = icon,
        hue = hue,
        unit = unit,
        step = MeasurementStep(step),
        rewards = null,
        whenAway = MadeMeasurementWhenAway,
        checkpoint = DefaultMeasurementCheckpoint,
    )

/**
 * The real screen over fake records, saving into memory so a number can be logged, changed and
 * removed. The state holds every demo measurement's records on the same days, so a specimen also
 * shows that a screen reads its own. It scrolls itself, so it needs a frame of fixed height.
 */
@Composable
private fun MeasurementScreenSpecimen(measurement: MeasurementConfiguration, start: Double, drift: Double, days: Int = 24) {
    var state by remember {
        mutableStateOf(
            AppState(
                // The measurement shown first, so Weight is held as the specimen shows it.
                measurements = listOf(measurement, DemoWeight, DemoWaist, DemoMood, DemoPulse, DemoBodyFat).distinctBy { it.id },
                // Yesterday back to [days] ago, a few days left out, with another measurement's record on each.
                measurementLogs = (1..days).filter { it % 6 != 0 }.flatMap { daysAgo ->
                    val value = start + (days - daysAgo) * drift + (daysAgo % 3 - 1) * measurement.step.value * 3
                    listOf(
                        MeasurementLog(measurement.id, fixtureDateKey(daysAgo), timestampMillis = 1, value),
                        MeasurementLog("msr_other", fixtureDateKey(daysAgo), timestampMillis = 2, 1000.0),
                    )
                },
            ),
        )
    }
    ScreenFrame(height = 760.dp) {
        MeasurementScreen(
            state = state,
            // As stored, so a step changed on the screen shows on its buttons.
            measurement = state.measurements.first { it.id == measurement.id },
            onDayChange = { change -> state.changingDay(change, timestampMillis = 3) { updated -> state = updated; true } },
            onStepChange = { step ->
                state = state.copy(measurements = state.measurements.map { if (it.id == measurement.id) it.copy(step = step) else it })
                true
            },
            todayKey = FixtureTodayKey,
        )
    }
}
