package com.yeogirokarpoc.geo

import com.google.ar.core.TrackingState

/** PAUSED is recoverable. Never read its pose or destroy it merely to hide a pin. */
data class GeoTrackingDecision(
    val discardAnchor: Boolean,
    val discardReference: Boolean,
    val showAnchor: Boolean,
    val showReference: Boolean,
    val canConfirm: Boolean,
)

fun geoTrackingDecision(
    camera: TrackingState,
    earthTracking: Boolean,
    anchor: TrackingState?,
    reference: TrackingState?,
    validPlacement: Boolean,
    restoring: Boolean,
): GeoTrackingDecision {
    val cameraReady = camera == TrackingState.TRACKING
    val showAnchor = cameraReady && anchor == TrackingState.TRACKING &&
        validPlacement && (!restoring || earthTracking)
    return GeoTrackingDecision(
        discardAnchor = camera == TrackingState.STOPPED || anchor == TrackingState.STOPPED,
        discardReference = camera == TrackingState.STOPPED || reference == TrackingState.STOPPED,
        showAnchor = showAnchor,
        showReference = cameraReady && reference == TrackingState.TRACKING,
        canConfirm = showAnchor && earthTracking &&
            (reference == null || reference == TrackingState.TRACKING),
    )
}
