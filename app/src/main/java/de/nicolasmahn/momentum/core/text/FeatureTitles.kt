package de.nicolasmahn.momentum.core.text

import androidx.annotation.StringRes
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.domain.settings.DashboardFeature

/** The one name of each feature, shared by its screen title, its Home tile and every sentence about it. */
@get:StringRes
val DashboardFeature.titleRes: Int
    get() = when (this) {
        DashboardFeature.Activity -> R.string.feature_activity
        DashboardFeature.Substances -> R.string.feature_substances
        DashboardFeature.Statistics -> R.string.feature_statistics
    }
