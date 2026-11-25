package com.yeogirokarpoc.geo

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class PinTapTarget(val projection: PinProjection, val atMs: Long) {
    fun contains(px: Float, py: Float, density: Float, nowMs: Long): Boolean {
        val baseX = projection.x ?: return false
        val baseY = projection.y ?: return false
        if (nowMs - atMs !in 0..500) return false
        projection.bounds?.let { box ->
            val padX = maxOf(12 * density, (48 * density - (box.right - box.left)) / 2f)
            val padY = maxOf(12 * density, (48 * density - (box.bottom - box.top)) / 2f)
            return px in box.left - padX..box.right + padX && py in box.top - padY..box.bottom + padY
        }
        // Undo the pin's world-up tilt so the hit box rotates with the drawn pin.
        val rad = Math.toRadians(-projection.tiltDegrees.toDouble())
        val dx = px - baseX; val dy = py - baseY
        val localX = (dx * cos(rad) - dy * sin(rad)).toFloat()
        val localY = (dx * sin(rad) + dy * cos(rad)).toFloat()
        val padding = 12 * density
        // Follow the visible pin while retaining a minimum 48 dp touch target at a distance.
        val halfWidth = maxOf(projection.widthPx / 2f + padding, 24 * density)
        val above = maxOf(projection.heightPx + padding, 36 * density)
        return localX in -halfWidth..halfWidth &&
            localY in -above..padding
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
