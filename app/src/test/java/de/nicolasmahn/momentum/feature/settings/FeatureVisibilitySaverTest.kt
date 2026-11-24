package de.nicolasmahn.momentum.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A visibility change that waits for the phone's answer is kept whole across the permission screen, the day it was made included. */
class FeatureVisibilitySaverTest {
    private fun saved(settings: FeatureVisibilitySettings?) = requireNotNull(with(FeatureVisibilitySettingsSaver) { SaverScope { true }.save(settings) })

    @Test
    fun aWaitingChangeKeepsEveryEntryWithItsRevisions() {
        val today = LocalDate.parse("2026-10-14")
        val waiting = FeatureVisibilitySettings(
            disabledFeatures = setOf(DashboardFeature.Statistics),
            hiddenChecklists = setOf("chk_7f3a", "same"),
            hiddenMeasurements = setOf("weight", "msr_k2p9", "same"),
            hiddenQuestions = setOf("qst_k2p9", "same"),
            hiddenTime = setOf("tim_k2p9", "same"),
        )
            .withVisibility(DashboardFeature.Substances, visible = false, today = today)
            .withVisibility(LoggedViewRef(LoggedViewKind.Measurement, "same"), visible = true, today = today)

        assertEquals(waiting, FeatureVisibilitySettingsSaver.restore(saved(waiting)))
        assertEquals(FeatureVisibilitySettings(emptySet()), FeatureVisibilitySettingsSaver.restore(saved(FeatureVisibilitySettings(emptySet()))))
        // While nothing waits, nothing is saved, and nothing saved restores nothing.
        assertNull(with(FeatureVisibilitySettingsSaver) { SaverScope { true }.save(null) })
        assertNull(FeatureVisibilitySettingsSaver.restore(emptyList<String>()))
    }
}
