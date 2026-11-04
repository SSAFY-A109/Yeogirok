package com.yeogirokarpoc.geo

import android.os.SystemClock
import com.google.ar.core.*
import org.json.JSONObject

data class NearbyArPin(val tip: JSONObject, val projection: PinProjection, val distanceMeters: Double)

/** Render-thread owner; ARCore permits 40 operations including resolved cloud anchors. */
class NearbyTipAnchors(tips: List<JSONObject>, primaryId: String?) {
    private data class Entry(val tip: JSONObject, var future: ResolveCloudAnchorFuture? = null,
                             var anchor: Anchor? = null, var startedAt: Long = 0, var failed: Boolean = false)
    private val entries = tips.filter { it.getString("id") != primaryId }.take(39).map { Entry(it) }
    val failedCount get() = entries.count { it.failed }

    fun update(session: Session, frame: Frame, project: (Pose, String) -> PinProjection): List<NearbyArPin> {
        val now = SystemClock.elapsedRealtime()
        val tracking = frame.camera.trackingState == TrackingState.TRACKING
        val pins = mutableListOf<NearbyArPin>()
        for (entry in entries) {
            if (entry.startedAt == 0L && tracking && !entry.failed) {
                entry.startedAt = now
                try { entry.future = session.resolveCloudAnchorAsync(entry.tip.getJSONObject("cloudRecord").getString("anchorId"), null) }
                catch (_: Exception) { entry.failed = true }
            }
            entry.future?.let { future ->
                if (future.state == FutureState.DONE) {
                    entry.future = null
                    if (future.resultCloudAnchorState == Anchor.CloudAnchorState.SUCCESS) entry.anchor = future.resultAnchor
                    if (entry.anchor == null) entry.failed = true
                } else if (now - entry.startedAt >= ArExperimentPolicy.CLOUD_TIMEOUT_MS) {
                    future.cancel(); entry.future = null; entry.failed = true
                }
            }
            val anchor = entry.anchor ?: continue
            if (anchor.trackingState == TrackingState.STOPPED) { anchor.detach(); entry.anchor = null; entry.failed = true; continue }
            if (!tracking || anchor.trackingState != TrackingState.TRACKING) continue
            val projection = project(anchor.pose, entry.tip.getString("id"))
            if (!projection.visible) continue
            val local = frame.camera.displayOrientedPose.inverse().transformPoint(anchor.pose.translation)
            val distance = kotlin.math.sqrt(local.sumOf { it.toDouble() * it })
            pins.add(NearbyArPin(entry.tip, projection, distance))
        }
        return pins
    }

    fun clear() {
        entries.forEach { entry ->
            entry.future?.let { future ->
                if (future.state == FutureState.DONE && future.resultCloudAnchorState == Anchor.CloudAnchorState.SUCCESS)
                    future.resultAnchor?.detach() else future.cancel()
            }
            entry.future = null; entry.anchor?.detach(); entry.anchor = null
            entry.startedAt = 0; entry.failed = false
        }
    }
}
