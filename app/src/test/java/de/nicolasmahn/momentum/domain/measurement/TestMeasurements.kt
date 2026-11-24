package de.nicolasmahn.momentum.domain.measurement

import de.nicolasmahn.momentum.domain.compatibility.defaultWeightMeasurement

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon

/** Weight's configuration as a new English installation is seeded with it. */
internal fun testWeightMeasurement() = defaultWeightMeasurement(label = "Weight")

/** A measurement a person made, as it starts: outside the streak, still due on holiday, its reminder off at 09:00. */
internal fun testMadeMeasurement(id: String = "msr_k2p9", label: String = "Waist", unit: String = "cm", step: String = "0.1") = MeasurementConfiguration(
    id = id,
    label = label,
    icon = ViewIcon.Ruler,
    hue = ViewHue.Plum,
    unit = unit,
    step = MeasurementStep(step),
    rewards = null,
    whenAway = MadeMeasurementWhenAway,
    checkpoint = DefaultMeasurementCheckpoint,
)

/** [this] with Weight configured, as every loaded state is. */
internal fun AppState.withWeight(configuration: MeasurementConfiguration = testWeightMeasurement()): AppState =
    copy(measurements = listOf(configuration))
