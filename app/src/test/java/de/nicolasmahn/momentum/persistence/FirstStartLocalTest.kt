package de.nicolasmahn.momentum.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.NewInstallationVisibility
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** When the first start comes, and what ends it. */
@RunWith(RobolectricTestRunner::class)
class FirstStartLocalTest {
    private val local = FirstStartLocal(ApplicationProvider.getApplicationContext<Context>())
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun whatIsSwitchedOnInABegunFirstStartKeepsIt() {
        assertTrue(local.waitsFor(AppState(), NewInstallationVisibility))
        val shown = NewInstallationVisibility.withVisibility(DashboardFeature.Substances, true, today)
        assertTrue(local.waitsFor(AppState(), shown))
    }

    @Test
    fun aBackupRestoredInTheFirstStartEndsIt() {
        assertTrue(local.waitsFor(AppState(), NewInstallationVisibility))
        val restored = AppState(substanceNoneDays = listOf(SubstanceNoneDay(today.toString(), timestampMillis = 1L)))
        assertFalse(local.waitsFor(restored, NewInstallationVisibility))
        assertFalse(local.waitsFor(AppState(), NewInstallationVisibility))
    }

    @Test
    fun anInstallationWithRecordsNeverMeetsIt() {
        assertFalse(local.waitsFor(AppState(substanceNoneDays = listOf(SubstanceNoneDay(today.toString(), timestampMillis = 1L))), NewInstallationVisibility))
    }
}
