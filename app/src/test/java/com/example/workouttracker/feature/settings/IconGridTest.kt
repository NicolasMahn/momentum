package com.example.workouttracker.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.activity.NamedActivityIds
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.checklist.ViewIcon
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every tile of an icon grid is one size, its icon over one line: a label too long for the tile
 * shrinks to fit it and is read whole, on the narrowest phone, in English and in German. Activity's
 * pickers list the sport icons first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h2400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconGridTest {
    @get:Rule
    val compose = createComposeRule()

    private val tag = "feature_settings"

    private fun showGrid() {
        val labels = mutableMapOf<ViewIcon, String>()
        compose.setContent {
            WorkoutTrackerTheme {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ViewIcon.entries.forEach { labels[it] = stringResource(it.labelRes) }
                    IconGrid(selected = ViewIcon.Dumbbell, testTag = tag) {}
                }
            }
        }
        compose.waitForIdle()
        val heights = ViewIcon.entries.map { compose.onNodeWithTag("${tag}_icon_${it.storageId}").fetchSemanticsNode().boundsInRoot.height }
        assertEquals("Every tile is one height", 1, heights.distinct().size)
        ViewIcon.entries.forEach { icon ->
            val label = labels.getValue(icon)
            val layout = layoutOf(label, "${tag}_icon_${icon.storageId}")
            assertEquals(label, 1, layout.lineCount)
            assertFalse(label, layout.isLineEllipsized(0))
            assertTrue("$label ends at ${layout.getLineRight(0)} px in ${layout.size.width} px", layout.getLineRight(0) <= layout.size.width)
        }
    }

    private fun layoutOf(text: String, tile: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tile)), useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    @Test
    fun everyTileIsOneSizeWithItsLabelReadWholeInEnglish() = showGrid()

    @Test
    @Config(qualifiers = "+de")
    fun everyTileIsOneSizeWithItsLabelReadWholeInGerman() = showGrid()

    /** Activity's own icon and each activity's list the sport icons first, then meditation, then the rest as the catalogue has them. */
    @Test
    fun activitysPickersListTheSportIconsFirst() {
        val draft = ActivitySettingsDraft(testActivityConfiguration(), LocalDate.parse("2026-10-14"))
        compose.setContent {
            WorkoutTrackerTheme {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ActivityPart(part = ChecklistPage.Look, view = draft.edited, draft = draft, named = NamedActivityIds(), testTag = tag, nameField = {}) {}
                }
            }
        }
        // Two rows before More, the whole catalogue after it.
        compose.onNodeWithTag("${tag}_icon_${ViewIcon.Tennis.storageId}").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_icons_more").performClick()
        fun placeOf(icon: ViewIcon): Pair<Float, Float> = compose.onNodeWithTag("${tag}_icon_${icon.storageId}").fetchSemanticsNode().boundsInRoot.let { it.top to it.left }
        val order = ViewIcon.entries.sortedWith(compareBy({ placeOf(it).first }, { placeOf(it).second }))
        assertEquals(listOf(ViewIcon.Dumbbell, ViewIcon.Bouldering, ViewIcon.Bicycle, ViewIcon.Running, ViewIcon.Walk, ViewIcon.Hiking, ViewIcon.Swimming, ViewIcon.Ball), order.take(8))
        assertEquals(listOf(ViewIcon.Tennis, ViewIcon.Basketball, ViewIcon.Rowing, ViewIcon.Skiing, ViewIcon.MartialArts, ViewIcon.Golf, ViewIcon.Skateboarding, ViewIcon.Kayaking), order.drop(8).take(8))
        assertEquals(ViewIcon.Meditation, order[16])
        assertEquals(ViewIcon.Checklist, order[17])
        assertEquals(ViewIcon.entries.size, order.distinct().size)
    }
}
