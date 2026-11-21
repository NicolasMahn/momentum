package com.example.workouttracker.domain

/** One independently completed part of a tracker's day. */
interface DayPart

/** A tracker completed with one daily record. */
data object WholeDay : DayPart

/** A configured checklist checkpoint. */
data class CheckpointPart(val checkpointId: String) : DayPart
