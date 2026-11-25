package com.yeogirokarpoc.geo

import kotlin.math.*

/** PoC guidance thresholds, never a server-side nearby/detail access decision. */
object NavigationPolicy {
    const val MAX_ACCURACY_METERS = 25f
    const val RESOLVE_RADIUS_METERS = 50.0
    const val MAX_HEADING_AGE_MS = 2_000L
    fun coordinates(lat: Double, lon: Double) = lat.isFinite() && lon.isFinite() &&
        lat in -90.0..90.0 && lon in -180.0..180.0
    fun usableLocation(ageMs: Long, accuracy: Float, hasAccuracy: Boolean) =
        ArExperimentPolicy.freshLocation(ageMs, accuracy, hasAccuracy) && accuracy <= MAX_ACCURACY_METERS
    fun wrap(angle: Double): Double = ((angle + 180) % 360 + 360) % 360 - 180
    fun cameraSpaceAngle(point: FloatArray): Double? {
        if (point.size != 3 || point.any { !it.isFinite() } || hypot(point[0].toDouble(), point[2].toDouble()) < 0.05) return null
        return wrap(Math.toDegrees(atan2(point[0].toDouble(), -point[2].toDouble())))
    }
    fun arrowAngle(resolved: Boolean, trackingLost: Boolean, liveAr: Boolean,
                   precise: Double?, coarse: Double?): Double? {
        if (trackingLost || (resolved && !liveAr)) return null
        // A vertical resolved target must not fall back to a GPS turn instruction.
        return (if (resolved) precise else coarse)?.takeIf { it.isFinite() }?.let { wrap(it) }
    }
    fun distance(lat: Double, lon: Double, targetLat: Double, targetLon: Double): Double {
        val a = Math.toRadians(lat); val b = Math.toRadians(targetLat)
        val d = Math.toRadians(wrap(targetLon - lon))
        val h = (sin((b - a) / 2).pow(2) + cos(a) * cos(b) * sin(d / 2).pow(2)).coerceIn(0.0, 1.0)
        return 6371008.8 * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
    fun bearing(lat: Double, lon: Double, targetLat: Double, targetLon: Double): Double {
        val a = Math.toRadians(lat); val b = Math.toRadians(targetLat)
        val d = Math.toRadians(wrap(targetLon - lon))
        return wrap(Math.toDegrees(atan2(sin(d) * cos(b), cos(a) * sin(b) - sin(a) * cos(b) * cos(d))))
    }
    /** Rotation-vector matrix maps device axes into magnetic East/North/Up.
     * Back camera looks along device -Z, NOT the device top edge or GPS course. */
    fun cameraHeading(matrix: FloatArray, declination: Float): Double? {
        if (matrix.size != 9 || matrix.any { !it.isFinite() } || !declination.isFinite()) return null
        val east = -matrix[2].toDouble(); val north = -matrix[5].toDouble()
        if (hypot(east, north) < 0.25) return null // Camera points almost vertically.
        return wrap(Math.toDegrees(atan2(east, north)) + declination)
    }
    fun turn(angle: Double): String = when {
        abs(angle) <= 15 -> "↑ 정면"
        abs(angle) >= 165 -> "↶ 뒤쪽"
        angle > 0 -> "→ 오른쪽 %.0f°".format(angle)
        else -> "← 왼쪽 %.0f°".format(-angle)
    }
}
