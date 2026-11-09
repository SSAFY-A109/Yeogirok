package com.yeogirokarpoc.geo

import android.os.SystemClock
import com.google.ar.core.*
import org.json.JSONObject

data class NearbyArPin(val tip: JSONObject, val projection: PinProjection, val distanceMeters: Double)

/** Render-thread owner; ARCore permits 40 operations including resolved cloud anchors. */
class NearbyTipAnchors(tips: List<JSONObject>, primaryId: String?) {
    private data class Entry(var tip: JSONObject, var future: ResolveCloudAnchorFuture? = null,
                             var anchor: Anchor? = null, var failed: Boolean = false,
                             val timeout: ResolveTrackingTimeout = ResolveTrackingTimeout())
    private var entries = tips.filter { it.getString("id") != primaryId }.take(39).map { Entry(it) }
    val failedCount get() = entries.count { it.failed }

    fun update(session: Session, frame: Frame, project: (Pose, JSONObject) -> PinProjection): List<NearbyArPin> {
        val now = SystemClock.elapsedRealtime()
        val tracking = frame.camera.trackingState == TrackingState.TRACKING
        val pins = mutableListOf<NearbyArPin>()
        for (entry in entries) {
            if (entry.future == null && entry.anchor == null && tracking && !entry.failed) {
                entry.timeout.reset()
                try { entry.future = session.resolveCloudAnchorAsync(entry.tip.getJSONObject("cloudRecord").getString("anchorId"), null) }
                catch (_: Exception) { entry.failed = true }
            }
            entry.future?.let { future ->
                if (future.state == FutureState.DONE) {
                    entry.future = null
                    if (future.resultCloudAnchorState == Anchor.CloudAnchorState.SUCCESS) entry.anchor = future.resultAnchor
                    if (entry.anchor == null) entry.failed = true
                } else if (entry.timeout.update(now, tracking) && future.cancel()) {
                    entry.future = null; entry.failed = true
                }
            }
            val anchor = entry.anchor ?: continue
            if (anchor.trackingState == TrackingState.STOPPED) { anchor.detach(); entry.anchor = null; entry.failed = true; continue }
            if (!tracking || anchor.trackingState != TrackingState.TRACKING) continue
            val projection = project(anchor.pose, entry.tip)
            if (!projection.visible) continue
            val distance = PublicTipPolicy.arDistance(frame.camera.pose.translation, anchor.pose.translation) ?: continue
            pins.add(NearbyArPin(entry.tip, projection, distance))
        }
        return pins
    }

    fun replaceTips(tips: List<JSONObject>, primaryId: String?) {
        fun key(tip: JSONObject) = tip.getString("id") to tip.getJSONObject("cloudRecord").getString("anchorId")
        val previous = entries.associateBy { key(it.tip) }.toMutableMap()
        entries = tips.filter { it.getString("id") != primaryId }.take(39).map { tip ->
            previous.remove(key(tip))?.also { it.tip = tip } ?: Entry(tip)
        }
        previous.values.forEach { release(it) }
    }

    private fun release(entry: Entry) {
        entry.future?.let { future ->
            if (future.state == FutureState.DONE && future.resultCloudAnchorState == Anchor.CloudAnchorState.SUCCESS)
                future.resultAnchor?.detach() else future.cancel()
        }
        entry.future = null; entry.anchor?.detach(); entry.anchor = null
        entry.failed = false
    }

    fun retryFailed() { entries.forEach { it.failed = false } }

    fun clear() { entries.forEach { release(it) } }
}
