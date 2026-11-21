package com.example.workouttracker.persistence

import android.content.Context
import com.example.workouttracker.R
import com.example.workouttracker.core.locale.withAppLanguage
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.activity.newInstallationActivity

/** A new installation gets Activity in the app language of that moment. */
internal fun interface ConfigurationDefaults {
    fun activity(): ActivityConfiguration
}

internal class AndroidConfigurationDefaults(context: Context) : ConfigurationDefaults {
    private val applicationContext = context.applicationContext

    override fun activity(): ActivityConfiguration =
        newInstallationActivity(applicationContext.withAppLanguage().resources.getString(R.string.feature_activity))
}
