package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.feature.settings.ViewWrite

/** Activity's save for a test that never saves Activity's page; one that does gets its own, so a save nobody expected fails loudly. */
internal val ActivitySaveNotExpected: (List<ActivityEdit>) -> ViewWrite = { error("This test does not save Activity's page.") }
