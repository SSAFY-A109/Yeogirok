package com.yeogirokarpoc.geo

/** Camera uses restored 3D anchors; map/refresh use GPS. APIs still verify every interaction. */
object PublicTipPolicy {
    const val CAMERA_RADIUS_METERS = 5.0
    const val MAP_RADIUS_METERS = 50.0
    const val REFRESH_DISTANCE_METERS = 10.0
    fun inCamera(distance: Double?) = distance != null && distance.isFinite() && distance in 0.0..CAMERA_RADIUS_METERS
    fun inMap(distance: Double?) = distance != null && distance.isFinite() && distance in 0.0..MAP_RADIUS_METERS
    fun needsRefresh(distance: Double) = distance.isFinite() && distance >= REFRESH_DISTANCE_METERS
    fun arDistance(camera: FloatArray, anchor: FloatArray): Double? {
        if (camera.size != 3 || anchor.size != 3 || camera.any { !it.isFinite() } || anchor.any { !it.isFinite() }) return null
        return kotlin.math.sqrt((0..2).sumOf { val delta = anchor[it].toDouble() - camera[it]; delta * delta })
    }
}
