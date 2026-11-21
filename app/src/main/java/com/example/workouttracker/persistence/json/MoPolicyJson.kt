package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.mo.TrackerMoPolicy
import org.json.JSONObject

internal fun TrackerMoPolicy.encode(): JSONObject = JSONObject()
    .put("enabled", enabled)
    .put("motif", motif?.storageId ?: JSONObject.NULL)

internal fun JSONObject.decodeMoPolicy(): TrackerMoPolicy {
    requireFields(required = setOf("enabled", "motif"))
    return TrackerMoPolicy(
        enabled = get("enabled") as? Boolean ?: error("enabled must be a Boolean"),
        motif = if (isNull("motif")) null else MoMotif.fromStorageId(text("motif")) ?: error("Unknown motif"),
    )
}
