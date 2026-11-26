package de.nicolasmahn.momentum.domain.settings

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.compatibility.moNeed
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureVisibilitySettingsTest {
    private val monday = LocalDate.parse("2026-10-12")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private val waist = LoggedViewRef(LoggedViewKind.Measurement, "msr_k2p9")

    /** The features are the places no configuration names; every other view is named by its kind and ID, Mo's tasks' too. */
    @Test
    fun dashboardFeaturesAreThePlacesNoConfigurationNames() {
        assertEquals(setOf(DashboardFeature.Activity, DashboardFeature.Substances, DashboardFeature.Statistics), DashboardFeature.entries.toSet())
        assertEquals(
            listOf(
                ViewRef.Feature(DashboardFeature.Activity),
                ViewRef.Logged(LoggedViewRef(LoggedViewKind.Time, "reading")),
                ViewRef.Checklist("care"),
                ViewRef.Checklist("supplements"),
                ViewRef.Logged(LoggedViewRef(LoggedViewKind.Questions, "eating")),
                ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, "weight")),
            ),
            MoNeed.entries.map { it.view },
        )
        MoNeed.entries.forEach { need -> assertEquals(need, need.view.moNeed) }
        assertEquals(null, ViewRef.Feature(DashboardFeature.Substances).moNeed)
        assertEquals(null, ViewRef.Checklist("chk_7f3a").moNeed)
    }

    @Test
    fun existingTrackingFeaturesAreVisibleButStatisticsIsHiddenByDefault() {
        val settings = FeatureVisibilitySettings()

        (DashboardFeature.entries.filterNot { it == DashboardFeature.Statistics }.map { ViewRef.Feature(it) } + MoNeed.entries.map { it.view })
            .forEach { view ->
                assertTrue(view.toString(), settings.isVisible(view))
                assertTrue(view.toString(), settings.isVisibleOn(view, on(-400)))
            }
        assertFalse(settings.isVisible(DashboardFeature.Statistics))
        assertFalse(settings.isVisibleOn(DashboardFeature.Statistics, on(-400)))
        assertEquals(mapOf<ViewRef, Dated<Boolean>>(ViewRef.Feature(DashboardFeature.Statistics) to HiddenFromTheBeginning), settings.hidden)
        // A new installation starts with nothing on Home; the first start turns on what is chosen.
        assertEquals(DashboardFeature.entries.toSet(), DashboardFeature.entries.filterNot(NewInstallationVisibility::isVisible).toSet())
    }

    /** Off counts from the day it is chosen; earlier days keep what they were, and on brings the view back from its day. */
    @Test
    fun hidingIsDatedFromTodayAndEarlierDaysKeepWhatTheyWere() {
        val hiddenWednesday = FeatureVisibilitySettings().withVisibility(MoNeed.Weight.view, visible = false, today = on(2))

        assertFalse(hiddenWednesday.isVisible(MoNeed.Weight.view))
        assertTrue(hiddenWednesday.isVisibleOn(MoNeed.Weight.view, on(1)))
        assertFalse(hiddenWednesday.isVisibleOn(MoNeed.Weight.view, on(2)))
        assertFalse(hiddenWednesday.isVisibleOn(MoNeed.Weight.view, on(30)))
        assertEquals(Dated(listOf(Dated.Revision(true), Dated.Revision(false, on(2)))), hiddenWednesday.visibilityOf(MoNeed.Weight.view))
        (MoNeed.entries.filterNot { it == MoNeed.Weight }.map { it.view } + ViewRef.Feature(DashboardFeature.Substances))
            .forEach { view -> assertTrue(view.toString(), hiddenWednesday.isVisible(view)) }

        val shownFriday = hiddenWednesday.withVisibility(MoNeed.Weight.view, visible = true, today = on(4))
        assertTrue(shownFriday.isVisible(MoNeed.Weight.view))
        assertTrue(shownFriday.isVisibleOn(MoNeed.Weight.view, on(1)))
        assertFalse(shownFriday.isVisibleOn(MoNeed.Weight.view, on(2)))
        assertFalse(shownFriday.isVisibleOn(MoNeed.Weight.view, on(3)))
        assertTrue(shownFriday.isVisibleOn(MoNeed.Weight.view, on(4)))
        // Shown again, it keeps its history: the entry stays, with every revision.
        assertEquals(
            Dated(listOf(Dated.Revision(false), Dated.Revision(true, on(2)), Dated.Revision(false, on(4)))),
            shownFriday.hidden.getValue(ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, "weight"))),
        )
    }

    /** A change taken back on the same day leaves nothing behind, and a view never hidden has no entry. */
    @Test
    fun aChangeTakenBackTheSameDayLeavesNoEntry() {
        val initial = FeatureVisibilitySettings()
        val hidden = initial.withVisibility(waist, visible = false, today = on(2))
        assertFalse(hidden.shows(waist))
        assertEquals(initial, hidden.withVisibility(waist, visible = true, today = on(2)))
        assertEquals(initial, initial.withVisibility(waist, visible = true, today = on(2)))
        assertEquals(initial, initial.withVisibility(DashboardFeature.Statistics, visible = true, today = on(2)).withVisibility(DashboardFeature.Statistics, visible = false, today = on(2)))
    }

    /** A view made today and hidden tomorrow reads as shown today and hidden from tomorrow. */
    @Test
    fun aViewHiddenTheDayAfterItWasMadeIsShownOnItsFirstDay() {
        val settings = FeatureVisibilitySettings().withVisibility(waist, visible = false, today = on(1))

        assertTrue(settings.showsOn(waist, on(0)))
        assertFalse(settings.showsOn(waist, on(1)))
        assertEquals(Dated(listOf(Dated.Revision(false), Dated.Revision(true, on(1)))), settings.hidden.getValue(ViewRef.Logged(waist)))
    }

    /** Hidden from the beginning is what a payload before version 7 and a test mean: one plain entry, under the view's kind. */
    @Test
    fun hiddenFromTheBeginningIsOnePlainEntryUnderTheViewsKind() {
        val settings = FeatureVisibilitySettings(
            disabledFeatures = setOf(DashboardFeature.Substances),
            hiddenChecklists = setOf("care", "chk_7f3a"),
            hiddenMeasurements = setOf("weight", "msr_k2p9"),
            hiddenTime = setOf("same"),
        )

        assertEquals(
            setOf(
                ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, "weight")),
                ViewRef.Checklist("care"),
                ViewRef.Feature(DashboardFeature.Substances),
                ViewRef.Checklist("chk_7f3a"),
                ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, "msr_k2p9")),
                ViewRef.Logged(LoggedViewRef(LoggedViewKind.Time, "same")),
            ),
            settings.hidden.keys,
        )
        assertTrue(settings.hidden.values.all { it == HiddenFromTheBeginning })
        assertFalse(settings.isVisible(MoNeed.Weight.view))
        assertFalse(settings.isChecklistVisible("care"))
        assertFalse(settings.isVisible(MoNeed.Care.view))
        assertTrue(settings.isVisible(DashboardFeature.Statistics))
        assertTrue(settings.shows(LoggedViewRef(LoggedViewKind.Questions, "same")))
        assertEquals(setOf("weight", "msr_k2p9"), settings.namedIds(LoggedViewKind.Measurement))
        assertEquals(setOf("care", "chk_7f3a"), settings.namedChecklistIds)
    }

    /** Forgetting drops the entry outright, as a delete must: nothing is left to date. */
    @Test
    fun forgettingDropsTheEntryWhateverItsHistory() {
        val settings = FeatureVisibilitySettings()
            .withVisibility(waist, visible = false, today = on(1))
            .withVisibility(waist, visible = true, today = on(3))
            .withChecklistVisibility("care", visible = false, today = on(2))

        assertEquals(FeatureVisibilitySettings().withChecklistVisibility("care", visible = false, today = on(2)), settings.forgetting(waist))
        assertEquals(FeatureVisibilitySettings().withVisibility(waist, visible = false, today = on(1)).withVisibility(waist, visible = true, today = on(3)), settings.forgettingChecklist("care"))
        assertEquals(settings, settings.forgetting(LoggedViewRef(LoggedViewKind.Questions, waist.id)))
    }

    /** An entry exists while hidden now or ever: one never hidden says nothing, so it cannot be held. */
    @Test
    fun anEntryNeverHiddenIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { FeatureVisibilitySettings(mapOf(ViewRef.Checklist("care") to Dated.always(false))) }
    }
}
