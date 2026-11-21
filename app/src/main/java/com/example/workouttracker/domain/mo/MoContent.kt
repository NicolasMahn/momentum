package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.TrackerRef

/** Content follows the view's kind and flavor; task identity stays in the ledger and rewards. */
enum class MoViewKind { Activity, Time, Measurement, Questions, Checklist }

data class MoContentKey(val kind: MoViewKind, val motif: MoMotif? = null)

/** A missing or deleted tracker has a neutral figure; its old kind cannot define a new one. */
fun MoMood.contentKey(ref: TrackerRef): MoContentKey = trackerContent[ref] ?: MoContentKey(MoViewKind.Checklist)

enum class MoProp { None, Book, Weights, PillBottle, EmptyBowl, Scale, LostTeeth }
enum class MoSaveAct { Generic, Running, Reading, Brushing, Eating, Pills, Weighing }
enum class MoThrivingLook { None, StrongArms, Glasses, BrightSmile }

data class MoFigureContent(
    val occasion: MoOccasion,
    val prop: MoProp,
    val saveAct: MoSaveAct,
    val thrivingLook: MoThrivingLook,
    val persistentNeglect: Boolean = false,
)

/** Each moment falls back independently, so a motif can add only the drawings it needs. */
val MoContentKey.figure: MoFigureContent
    get() {
        val generic = kind.genericFigure
        val specific = motif?.figureOverrides ?: return generic
        return generic.copy(
            occasion = specific.occasion ?: generic.occasion,
            prop = specific.prop ?: generic.prop,
            saveAct = specific.saveAct ?: generic.saveAct,
            thrivingLook = specific.thrivingLook ?: generic.thrivingLook,
            persistentNeglect = specific.persistentNeglect ?: generic.persistentNeglect,
        )
    }

/**
 * What Mo acts out for a view that wears this motif once its day is saved. A timer of this flavor
 * shows him at it while it runs; a book motif uses the reading animation.
 */
val MoMotif.saveAct: MoSaveAct get() = MoContentKey(MoViewKind.Checklist, this).figure.saveAct

private val MoViewKind.genericFigure: MoFigureContent
    get() = when (this) {
        MoViewKind.Activity -> MoFigureContent(MoOccasion.Lazy, MoProp.Weights, MoSaveAct.Running, MoThrivingLook.StrongArms)
        MoViewKind.Time -> NeutralFigure
        MoViewKind.Measurement -> NeutralFigure
        MoViewKind.Questions -> NeutralFigure
        // An arbitrary checklist has no physical object to act out. The ordinary pose and
        // save hop already express attention and success without inventing an item or symptom.
        MoViewKind.Checklist -> MoFigureContent(MoOccasion.Idle, MoProp.None, MoSaveAct.Generic, MoThrivingLook.None)
    }

private data class MoFigureOverrides(
    val occasion: MoOccasion? = null,
    val prop: MoProp? = null,
    val saveAct: MoSaveAct? = null,
    val thrivingLook: MoThrivingLook? = null,
    val persistentNeglect: Boolean? = null,
)

private val MoMotif.figureOverrides: MoFigureOverrides?
    get() = when (this) {
        MoMotif.Food -> MoFigureOverrides(MoOccasion.Hungry, MoProp.EmptyBowl, MoSaveAct.Eating, MoThrivingLook.None)
        MoMotif.Running -> MoFigureOverrides(MoOccasion.Lazy, MoProp.Weights, MoSaveAct.Running, MoThrivingLook.StrongArms)
        MoMotif.Book -> MoFigureOverrides(MoOccasion.Dumb, MoProp.Book, MoSaveAct.Reading, MoThrivingLook.Glasses)
        MoMotif.Scale -> MoFigureOverrides(MoOccasion.Unweighed, MoProp.Scale, MoSaveAct.Weighing, MoThrivingLook.None)
        MoMotif.Pills -> MoFigureOverrides(
            occasion = MoOccasion.Sickly,
            prop = MoProp.PillBottle,
            saveAct = MoSaveAct.Pills,
            thrivingLook = MoThrivingLook.None,
        )
        MoMotif.Teeth -> MoFigureOverrides(
            occasion = MoOccasion.Stinky,
            prop = MoProp.LostTeeth,
            saveAct = MoSaveAct.Brushing,
            thrivingLook = MoThrivingLook.BrightSmile,
            // Teeth do not heal as soon as today's checkpoint is ticked.
            persistentNeglect = true,
        )
    }

private val NeutralFigure = MoFigureContent(MoOccasion.Idle, MoProp.None, MoSaveAct.Generic, MoThrivingLook.None)
