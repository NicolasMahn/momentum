package de.nicolasmahn.momentum.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.domain.mo.MoLevel
import de.nicolasmahn.momentum.domain.mo.MoLevelArrival
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoStreakLoss
import de.nicolasmahn.momentum.feature.mo.MoAtHisBest
import de.nicolasmahn.momentum.feature.mo.MoBackfill
import de.nicolasmahn.momentum.feature.mo.MoLevelTakeover
import de.nicolasmahn.momentum.feature.mo.MoLook
import de.nicolasmahn.momentum.feature.mo.MoLossTakeover
import de.nicolasmahn.momentum.feature.mo.renderStreakShareImage
import java.time.LocalDate

/** The two big moments, with fake state, and the picture a streak is shared as. */
internal val moMomentSpecimens: List<GallerySpecimen> = listOf(
    GallerySpecimen("Mo, a level reached (tap Weiter to replay)") {
        ScreenFrame(height = 720.dp) {
            var round by remember { mutableIntStateOf(0) }
            val arrival = MoLevelArrival(MoLevel.Ember, LocalDate.parse("2026-11-12"), LocalDate.parse("2026-10-14"))
            key(round) { MoLevelTakeover(arrival, days = 30, onShare = {}, onDone = { round += 1 }) }
        }
    },
    GallerySpecimen("Mo, a streak lost (tap Neu anfangen to replay)") {
        ScreenFrame(height = 720.dp) {
            var round by remember { mutableIntStateOf(0) }
            val backfills = listOf(MoBackfill("Supplements", "Di., 10. Nov.") {}, MoBackfill("Eating", "Mo., 9. Nov.") {})
            key(round) {
                MoLossTakeover(MoStreakLoss(23, LocalDate.parse("2026-11-11")), backfills, onRestart = { round += 1 }, onStrategy = {}, onTalk = {})
            }
        }
    },
    GallerySpecimen("A streak shared as a picture: at a level, and below the first") {
        val context = LocalContext.current
        val shared = remember { renderStreakShareImage(context, MoAtHisBest, days = 30) }
        val early = remember { renderStreakShareImage(context, MoLook(MoState.Steady), days = 4) }
        Image(shared.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
        Image(early.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
    },
)
