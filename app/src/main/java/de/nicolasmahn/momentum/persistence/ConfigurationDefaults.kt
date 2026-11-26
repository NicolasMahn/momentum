package de.nicolasmahn.momentum.persistence

import android.content.Context
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.locale.withAppLanguage
import de.nicolasmahn.momentum.domain.activity.ActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.newInstallationActivity

/** A new installation gets Activity in the app language of that moment. */
internal fun interface ConfigurationDefaults {
    fun activity(): ActivityConfiguration
}

internal class AndroidConfigurationDefaults(context: Context) : ConfigurationDefaults {
    private val applicationContext = context.applicationContext

    override fun activity(): ActivityConfiguration =
        newInstallationActivity(applicationContext.withAppLanguage().resources.getString(R.string.feature_activity))
}
