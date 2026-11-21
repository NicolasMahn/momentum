package com.example.workouttracker.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag

/**
 * Every color [screen]'s window draws inside the bounds of the node tagged [tag]: how a test reads
 * a view's color, since none is said to TalkBack. It needs the native graphics mode.
 */
internal fun ComposeContentTestRule.colorsDrawnIn(screen: View, tag: String): Set<Int> {
    waitForIdle()
    val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    val drawn = Bitmap.createBitmap(screen.rootView.width, screen.rootView.height, Bitmap.Config.ARGB_8888)
    runOnUiThread { screen.rootView.draw(Canvas(drawn)) }
    return buildSet {
        for (x in bounds.left.toInt() until bounds.right.toInt()) for (y in bounds.top.toInt() until bounds.bottom.toInt()) add(drawn.getPixel(x, y))
    }
}
