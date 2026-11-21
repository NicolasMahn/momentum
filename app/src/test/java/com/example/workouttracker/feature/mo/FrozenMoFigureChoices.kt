package com.example.workouttracker.feature.mo

import com.example.workouttracker.domain.mo.MoFigureContent
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.domain.mo.MoProp
import com.example.workouttracker.domain.mo.MoSaveAct
import com.example.workouttracker.domain.mo.MoThrivingLook

/**
 * Recorded from the pre-Block-6 MoOccasion and MoFigure branches. Keeping this table independent
 * makes a changed catalogue choice fail even when the drawing consumes it consistently.
 */
internal fun MoNeed.frozenFigureContent(): MoFigureContent = when (this) {
    MoNeed.Activity -> MoFigureContent(MoOccasion.Lazy, MoProp.Weights, MoSaveAct.Running, MoThrivingLook.StrongArms)
    MoNeed.Reading -> MoFigureContent(MoOccasion.Dumb, MoProp.Book, MoSaveAct.Reading, MoThrivingLook.Glasses)
    MoNeed.Weight -> MoFigureContent(MoOccasion.Unweighed, MoProp.Scale, MoSaveAct.Weighing, MoThrivingLook.None)
    MoNeed.Eating -> MoFigureContent(MoOccasion.Hungry, MoProp.EmptyBowl, MoSaveAct.Eating, MoThrivingLook.None)
    MoNeed.Supplements -> MoFigureContent(MoOccasion.Sickly, MoProp.PillBottle, MoSaveAct.Pills, MoThrivingLook.None)
    MoNeed.Care -> MoFigureContent(MoOccasion.Stinky, MoProp.LostTeeth, MoSaveAct.Brushing, MoThrivingLook.BrightSmile, persistentNeglect = true)
}
