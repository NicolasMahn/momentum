package de.nicolasmahn.momentum.testing

import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry

/** The app's own text in the language the device under test speaks, so tests find what users see. */
fun appString(@StringRes id: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
