package com.example.workouttracker.app

import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.feature.settings.ViewWrite

/** Activity's save for a test that never saves Activity's page; one that does gets its own, so a save nobody expected fails loudly. */
internal val ActivitySaveNotExpected: (List<ActivityEdit>) -> ViewWrite = { error("This test does not save Activity's page.") }
