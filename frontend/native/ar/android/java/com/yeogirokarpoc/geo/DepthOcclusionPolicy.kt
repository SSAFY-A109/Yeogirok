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
