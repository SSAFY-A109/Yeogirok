package com.yeogirokarpoc.geo

import kotlin.math.hypot

data class PinTapTarget(val projection: PinProjection, val atMs: Long) {
    fun contains(px: Float, py: Float, density: Float, nowMs: Long): Boolean {
        val x = projection.x ?: return false
        val y = projection.y ?: return false
        val padding = 12 * density
        // Follow the visible pin while retaining a minimum 48 dp touch target at a distance.
        val halfWidth = maxOf(projection.widthPx / 2f + padding, 24 * density)
        val above = maxOf(projection.heightPx + padding, 36 * density)
        return nowMs - atMs in 0..500 && px in (x - halfWidth)..(x + halfWidth) &&
            py in (y - above)..(y + padding)
    }
}

/** A release outside the pin, drag, second finger, or stale projection cannot open content. */
class PinTapPolicy(private val slop: Float) {
    private var down: Pair<Float, Float>? = null
    fun cancel() { down = null }
    fun down(x: Float, y: Float, target: PinTapTarget?, density: Float, nowMs: Long) {
        down = if (target?.contains(x, y, density, nowMs) == true) x to y else null
    }
    fun move(x: Float, y: Float) {
        val start = down ?: return
        if (hypot(x - start.first, y - start.second) > slop) cancel()
    }
    fun up(x: Float, y: Float, target: PinTapTarget?, density: Float, nowMs: Long): Boolean {
        move(x, y)
        val accepted = down != null && target?.contains(x, y, density, nowMs) == true
        cancel()
        return accepted
    }
}
