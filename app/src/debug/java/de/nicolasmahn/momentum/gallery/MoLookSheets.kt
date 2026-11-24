package de.nicolasmahn.momentum.gallery

import androidx.compose.ui.geometry.Offset
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.domain.mo.MoProp
import de.nicolasmahn.momentum.domain.mo.MoSaveAct
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoThrivingLook
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoBrowKind
import de.nicolasmahn.momentum.feature.mo.MoBrows
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoEyeShape
import de.nicolasmahn.momentum.feature.mo.MoLook
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoNeglectedDrawing
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.acts.MoAct
import de.nicolasmahn.momentum.feature.mo.acts.MoScene
import de.nicolasmahn.momentum.feature.mo.acts.MoVariant
import de.nicolasmahn.momentum.feature.mo.acts.bodyKind

/** Looks that belong on one sheet, each with the words that tell them apart. */
internal class MoLookSet(val name: String, val looks: List<Pair<String, MoLook>>)

/** The looks drawn without a scene from the act book. */
internal val MoClassicLooks: List<MoLookSet> = buildList {
    val burning = listOf(MoState.Blazing, MoState.Steady, MoState.Dimming)
    val all = MoState.entries.reversed()
    val thriving = MoThrivingLook.entries - MoThrivingLook.None
    fun teeth(level: MoNeglect) = listOf(MoNeglectedDrawing(MoProp.LostTeeth, level))

    add(MoLookSet("plain", all.map { "$it" to MoLook(it) }))
    add(
        MoLookSet(
            "angry",
            burning.map { "$it angry" to MoLook(it, angry = true) } +
                ("Steady angry, teeth Severe" to MoLook(MoState.Steady, angry = true, neglect = teeth(MoNeglect.Severe))),
        ),
    )
    add(
        MoLookSet(
            "frozen",
            all.map { "$it frozen" to MoLook(it, frozen = true) } +
                ("Blazing frozen, strong arms" to MoLook(MoState.Blazing, frozen = true, thriving = setOf(MoThrivingLook.StrongArms))) +
                ("Steady frozen, angry" to MoLook(MoState.Steady, frozen = true, angry = true)),
        ),
    )
    add(
        MoLookSet(
            "thriving",
            thriving.flatMap { look -> listOf(MoState.Blazing, MoState.Steady).map { "$it $look" to MoLook(it, thriving = setOf(look)) } } +
                ("Blazing, all three" to MoLook(MoState.Blazing, thriving = thriving.toSet())),
        ),
    )
    add(
        MoLookSet(
            "teeth",
            MoNeglect.entries.flatMap { level ->
                listOf(MoState.Steady, MoState.Cold).map { "$it teeth $level" to MoLook(it, neglect = teeth(level)) }
            } + ("Dimming teeth Dire" to MoLook(MoState.Dimming, neglect = teeth(MoNeglect.Dire))) +
                ("Steady brushing, teeth Mild" to MoLook(MoState.Steady, doing = MoSaveAct.Brushing, neglect = teeth(MoNeglect.Mild))),
        ),
    )
    val props = listOf(MoProp.Book, MoProp.Weights, MoProp.PillBottle, MoProp.EmptyBowl, MoProp.Scale)
    add(
        MoLookSet(
            "props",
            all.mapIndexed { index, state ->
                val (first, second) = props[index] to props[(index + 1) % props.size]
                "$state: $first Severe, $second Mild" to
                    MoLook(state, neglect = listOf(MoNeglectedDrawing(first, MoNeglect.Severe, 0), MoNeglectedDrawing(second, MoNeglect.Mild, 1)))
            } +
                // A web on either side, and the bowl, the widest of them by its rim, at the edge.
                ("Blazing: EmptyBowl Severe, Weights Severe" to
                    MoLook(MoState.Blazing, neglect = listOf(MoNeglectedDrawing(MoProp.EmptyBowl, MoNeglect.Severe, 0), MoNeglectedDrawing(MoProp.Weights, MoNeglect.Severe, 1)))),
        ),
    )
}

// A moment at which the drool has formed and no blink is due.
private const val KIT_MOMENT = 0.5f

private val Mouths: List<Pair<String, MoMouth>> = listOf(
    "Smile" to MoMouth.Smile(),
    "Smile 9 x 6" to MoMouth.Smile(9f, 6f),
    "Smile 26 x 22" to MoMouth.Smile(26f, 22f),
    "Grin 0" to MoMouth.Grin(0f),
    "Grin 0.3" to MoMouth.Grin(0.3f),
    "Grin 0.6" to MoMouth.Grin(0.6f),
    "Grin 1" to MoMouth.Grin(1f),
    "Grin 1, no tongue" to MoMouth.Grin(1f, tongue = false),
    "Frown" to MoMouth.Frown(),
    "Frown 18" to MoMouth.Frown(18f),
    "Flat" to MoMouth.Flat(),
    "Flat 22" to MoMouth.Flat(22f),
    "Oh 5" to MoMouth.Oh(5f),
    "Oh 9" to MoMouth.Oh(9f),
    "Oh 14" to MoMouth.Oh(14f),
    "Gritted" to MoMouth.Gritted,
    "Beam" to MoMouth.Beam,
    "Wavy" to MoMouth.Wavy,
    "Zigzag" to MoMouth.Zigzag,
    "TongueOut" to MoMouth.TongueOut,
    "Drool" to MoMouth.Drool,
    "Chew 0" to MoMouth.Chew(0f),
    "Chew 0.5" to MoMouth.Chew(0.5f),
    "Chew 1" to MoMouth.Chew(1f),
    "Yawn 0" to MoMouth.Yawn(0f),
    "Yawn 0.3" to MoMouth.Yawn(0.3f),
    "Yawn 0.6" to MoMouth.Yawn(0.6f),
    "Yawn 1" to MoMouth.Yawn(1f),
    "Whistle" to MoMouth.Whistle,
    "Pout" to MoMouth.Pout,
    "Smirk" to MoMouth.Smirk,
)

private val Eyes: List<Pair<String, MoEye>> = listOf(
    "Round" to MoEye(),
    "Happy" to MoEye(MoEyeShape.Happy),
    "Shut" to MoEye(MoEyeShape.Shut),
    "Cross" to MoEye(MoEyeShape.Cross),
    "Spiral" to MoEye(MoEyeShape.Spiral),
    "Star" to MoEye(MoEyeShape.Star),
    "Heart" to MoEye(MoEyeShape.Heart),
    "open 0.5" to MoEye(open = 0.5f),
    "open 0.15" to MoEye(open = 0.15f),
    "iris 0.5" to MoEye(iris = 0.5f),
    "iris 0.3" to MoEye(iris = 0.3f),
    "lid 0.5 (glare)" to MoEye(lid = 0.5f),
    "lid 1 (glare)" to MoEye(lid = 1f),
    "lid -0.5 (sorrow)" to MoEye(lid = -0.5f),
    "lid -1 (sorrow)" to MoEye(lid = -1f),
    "lidDrop 0.1" to MoEye(lidDrop = 0.1f),
    "lidDrop 0.2" to MoEye(lidDrop = 0.2f),
    "lidDrop 0.45" to MoEye(lidDrop = 0.45f),
    "lidDrop 0.7" to MoEye(lidDrop = 0.7f),
    "lidDrop 0.9" to MoEye(lidDrop = 0.9f),
    "lidDrop 0.5, lid 0.6" to MoEye(lidDrop = 0.5f, lid = 0.6f),
    "lidDrop 0.5, lid -0.6" to MoEye(lidDrop = 0.5f, lid = -0.6f),
    "scale 0.8" to MoEye(scale = 0.8f),
    "scale 1.25" to MoEye(scale = 1.25f),
    "gaze -1, -1" to MoEye(gaze = Offset(-1f, -1f)),
    "gaze 1, 1" to MoEye(gaze = Offset(1f, 1f)),
    "tear 0.05" to MoEye(tear = 0.05f),
    "tear 0.4" to MoEye(tear = 0.4f),
    "tear 0.85" to MoEye(tear = 0.85f),
)

private val Brows: List<Pair<String, MoBrows>> = listOf(
    "brows Angry" to MoBrows(MoBrowKind.Angry),
    "brows Worried" to MoBrows(MoBrowKind.Worried),
    "brows Raised" to MoBrows(MoBrowKind.Raised),
    "brows Flat" to MoBrows(MoBrowKind.Flat),
)

private val Arms: List<Pair<String, MoArm>> = listOf(
    "Down" to MoArm.Down,
    "Up" to MoArm.Up,
    "Out" to MoArm.Out,
    "Hip" to MoArm.Hip,
    "Cheek" to MoArm.Cheek,
    "Front" to MoArm.Front,
    "Flex" to MoArm.Flex,
    "across -80, 10" to MoArm(Offset(-80f, 10f)),
    "to the mouth" to MoArm(Offset(-70f, -6f), bend = 0.4f),
    "to the mouth, warm" to MoArm(Offset(-70f, -6f), bend = 0.4f, warm = 1f),
)

/** One sheet of the kit's parts: cells that differ in one thing, on one body. */
internal class MoKitSheet(val name: String, val close: MoCloseUp, val rows: List<List<MoShot>>)

/**
 * Everything the kit can put on his face and every way it hangs his arms, on each body an act can
 * pose. An act only chooses among these, so a part that is drawn badly here is drawn badly in every
 * act that uses it.
 */
internal val MoKitSheets: List<MoKitSheet> = listOf(MoState.Steady, MoState.Dimming, MoState.Cold).flatMap { state ->
    val body = state.bodyKind().name.lowercase()
    fun pages(name: String, close: MoCloseUp, columns: Int, poses: List<Pair<String, MoPose>>) =
        poses.chunked(columns * columns).mapIndexed { page, cells ->
            MoKitSheet("${name}_${body}_${page + 1}", close, cells.chunked(columns).map { row -> row.map { (label, pose) -> MoShot(posed(state, pose), KIT_MOMENT, label) } })
        }
    pages("mouths", MoCloseUp.Face, 4, Mouths.map { (label, mouth) -> label to MoPose(mouth = mouth, blinks = false) }) +
        pages("eyes", MoCloseUp.Face, 4, Eyes.map { (label, eye) -> label to MoPose(blinks = false).eyes(eye) } + Brows.map { (label, brows) -> label to MoPose(brows = brows, blinks = false) }) +
        pages("arms", MoCloseUp.Body, 3, Arms.flatMap { (label, arm) -> listOf(false, true).map { front -> "$label, ${if (front) "in front" else "behind"}" to MoPose(leftArm = arm, rightArm = arm, armsInFront = front, blinks = false) } })
}

private fun posed(state: MoState, pose: MoPose) = MoLook(state, scene = MoScene(Posed(pose), MoVariant(1)))

private class Posed(private val pose: MoPose) : MoAct("kit", MoOccasion.Idle, MoBodyKind.entries.toSet()) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = pose
}
