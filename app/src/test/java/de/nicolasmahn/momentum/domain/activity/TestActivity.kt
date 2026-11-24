package de.nicolasmahn.momentum.domain.activity

import de.nicolasmahn.momentum.domain.AppState

/** Activity's configuration as a new English installation is seeded with it: the seeded rules in their English words. */
internal fun testActivityConfiguration() = defaultActivityConfiguration(EnglishActivityLabels)

/** [this] with Activity configured, as every loaded state is. */
internal fun AppState.withActivity(configuration: ActivityConfiguration = testActivityConfiguration()): AppState = copy(activity = configuration)
