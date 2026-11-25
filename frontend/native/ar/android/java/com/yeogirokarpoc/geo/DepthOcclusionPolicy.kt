package com.yeogirokarpoc.geo

import java.nio.ByteBuffer
import kotlin.math.max

/** DEPTH16 is unsigned little-endian millimeters; rows may contain padding. */
internal fun depthMillimeters(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int,
                             pixelStride: Int, x: Int, y: Int): Int {
    if (x !in 0 until width || y !in 0 until height || rowStride <= 0 || pixelStride < 2) return 0
    val index = y.toLong() * rowStride + x.toLong() * pixelStride
    if (index < 0 || index + 1 >= buffer.limit()) return 0
    return (buffer.get(index.toInt()).toInt() and 255) or
        ((buffer.get(index.toInt() + 1).toInt() and 255) shl 8)
}

internal fun isDepthOccluded(samples: IntArray, cameraDepthMeters: Float): Boolean {
    if (!cameraDepthMeters.isFinite() || cameraDepthMeters <= 0 || samples.isEmpty()) return false
    val pinMm = cameraDepthMeters * 1000f
    // Keep a surface-attached pin visible despite depth noise at its own wall/floor.
    val toleranceMm = max(150f, pinMm * 0.05f)
    // Unknown/near/far depth never counts as a blocking wall. Require an absolute majority.
    return samples.count { it in 200..8000 && it < pinMm - toleranceMm } > samples.size / 2
}

internal fun pinDepthSamplePoints(bounds: PinBounds): FloatArray = FloatArray(18).also { points ->
    for (row in 0..2) for (column in 0..2) {
        val index = (row * 3 + column) * 2
        points[index] = bounds.left + (bounds.right - bounds.left) * (0.3f + column * 0.2f)
        points[index + 1] = bounds.top + (bounds.bottom - bounds.top) * (0.25f + row * 0.2f)
    }
}

internal class DepthOcclusionGate {
    private var blockedSince: Long? = null
    private var lastSampleAt: Long? = null
    fun hidden(blocked: Boolean, nowMs: Long): Boolean {
        val previous = lastSampleAt
        if (!blocked || previous == null || nowMs - previous !in 0..250L) blockedSince = null
        lastSampleAt = nowMs
        if (!blocked) return false
        val start = blockedSince ?: nowMs.also { blockedSince = it }
        return nowMs - start >= 400L
    }
}
