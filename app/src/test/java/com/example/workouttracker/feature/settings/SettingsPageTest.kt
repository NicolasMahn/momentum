package com.example.workouttracker.feature.settings

import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.settings.DashboardFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The open page of Settings is kept as its key, so it is found again after the activity was recreated. */
class SettingsPageTest {
    @Test
    fun everyCanonicalPageIsFoundAgainByItsKey() {
        val pages = listOf(
            SettingsPage.Backups,
            SettingsPage.Holidays,
            SettingsPage.SickDays,
            SettingsPage.Sleep,
            SettingsPage.Reminders,
            SettingsPage.Feature(DashboardFeature.Activity),
            SettingsPage.Tracker(TrackerRef(TrackerTypeId("checklist"), "chk_7f3a")),
            SettingsPage.Tracker(TrackerRef(TrackerTypeId("measurement"), "msr_k2p9")),
            SettingsPage.Tracker(TrackerRef(TrackerTypeId("questions"), "qst_k2p9")),
            SettingsPage.Tracker(TrackerRef(TrackerTypeId("time"), "tim_k2p9")),
            SettingsPage.Tracker(TrackerRef(TrackerTypeId("counter"), "water:daily:🫗")),
            SettingsPage.NewView,
            SettingsPage.NewTracker(TrackerTypeId("checklist")),
            SettingsPage.NewTracker(TrackerTypeId("measurement")),
            SettingsPage.NewTracker(TrackerTypeId("questions")),
            SettingsPage.NewTracker(TrackerTypeId("time")),
            SettingsPage.NewTracker(TrackerTypeId("counter")),
            SettingsPage.Substances,
            SettingsPage.About,
            SettingsPage.Feedback,
            SettingsPage.Privacy,
            SettingsPage.LegalNotice,
            SettingsPage.License("Nunito"),
            SettingsPage.License("OpenJDK libraries for Android (desugar_jdk_libs)"),
        )
        pages.forEach { page -> assertEquals(page, SettingsPage.fromKey(page.key)) }
        assertEquals(pages.size, pages.map { it.key }.toSet().size)
        assertNull(SettingsPage.fromKey("nothing"))
    }

    /** A component's page belongs to the About page, so Back from it leads there and not to the list. */
    @Test
    fun aLicensePageIsKeptWithItsComponentAndLeadsBackToAbout() {
        assertEquals("license:kotlinx.coroutines", SettingsPage.License("kotlinx.coroutines").key)
        assertEquals(SettingsPage.About, SettingsPage.License("kotlinx.coroutines").parentPage)
        assertEquals(SettingsPage.About, SettingsPage.Privacy.parentPage)
        assertEquals(SettingsPage.About, SettingsPage.LegalNotice.parentPage)
        assertNull(SettingsPage.About.parentPage)
        assertNull(SettingsPage.fromKey("license:"))
    }

    /** A tracker's page key carries both its kind and its ID, including IDs with punctuation. */
    @Test
    fun aTrackerPageIsKeptWithItsKindAndId() {
        val measurement = SettingsPage.Tracker(TrackerRef(TrackerTypeId("measurement"), "msr_k2p9"))

        assertEquals("tracker:measurement:msr_k2p9", measurement.key)
        assertNotEquals(measurement.key, SettingsPage.Tracker(TrackerRef(TrackerTypeId("measurement"), "msr_m7xd")).key)
        assertNotEquals(measurement.key, SettingsPage.Tracker(TrackerRef(TrackerTypeId("checklist"), "msr_k2p9")).key)
        assertEquals(
            SettingsPage.Tracker(TrackerRef(TrackerTypeId("counter"), "water:daily:🫗")),
            SettingsPage.fromKey("tracker:counter:water:daily:🫗"),
        )
    }

    /** Old keys still reopen the same page, but resolve to the one generic page type. */
    @Test
    fun legacyTypedPageKeysResolveToCanonicalTrackerPages() {
        val legacyPages = listOf(
            SettingsPage.Checklist("chk_7f3a").key to TrackerRef(TrackerTypeId("checklist"), "chk_7f3a"),
            SettingsPage.Measurement("msr_k2p9").key to TrackerRef(TrackerTypeId("measurement"), "msr_k2p9"),
            SettingsPage.Questions("qst_k2p9").key to TrackerRef(TrackerTypeId("questions"), "qst_k2p9"),
            SettingsPage.Time("tim_k2p9").key to TrackerRef(TrackerTypeId("time"), "tim_k2p9"),
        )

        legacyPages.forEach { (oldKey, ref) ->
            assertEquals(oldKey, SettingsPage.Tracker(ref), SettingsPage.fromKey(oldKey))
        }

        assertEquals(SettingsPage.Feature(DashboardFeature.Activity), SettingsPage.fromKey("feature:activity"))
        assertNull(SettingsPage.fromKey("feature:statistics"))
        assertNull(SettingsPage.fromKey("feature:supplements"))
        listOf("weight", "eating", "reading", "care").forEach { name ->
            assertNull(SettingsPage.fromKey("feature:$name"))
        }
    }

    @Test
    fun legacyCreationKeysResolveToGenericTrackerCreationPages() {
        listOf(
            SettingsPage.NewChecklist.key to TrackerTypeId("checklist"),
            SettingsPage.NewMeasurement.key to TrackerTypeId("measurement"),
            SettingsPage.NewQuestions.key to TrackerTypeId("questions"),
            SettingsPage.NewTime.key to TrackerTypeId("time"),
        ).forEach { (oldKey, type) ->
            assertEquals(oldKey, SettingsPage.NewTracker(type), SettingsPage.fromKey(oldKey))
        }
    }

    /** Made logged trackers use the same generic page as any module tracker. */
    @Test
    fun madeLoggedTrackerPagesNameTheirView() {
        val views = listOf(
            TrackerRef(TrackerTypeId("measurement"), "same") to LoggedViewRef(LoggedViewKind.Measurement, "same"),
            TrackerRef(TrackerTypeId("questions"), "same") to LoggedViewRef(LoggedViewKind.Questions, "same"),
            TrackerRef(TrackerTypeId("time"), "same") to LoggedViewRef(LoggedViewKind.Time, "same"),
        )
        views.forEach { (ref, view) ->
            val page = SettingsPage.Tracker(ref)
            assertEquals(page, view.settingsPage)
        }
    }
}
