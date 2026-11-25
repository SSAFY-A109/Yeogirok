package com.yeogirokarpoc.geo

import java.nio.ByteBuffer

fun main() {
    // A nearer wall hides a pin; a pin on that wall or in front remains visible.
    check(isDepthOccluded(IntArray(9) { 1000 }, 2f))
    check(!isDepthOccluded(IntArray(9) { 2000 }, 2f))
    check(!isDepthOccluded(IntArray(9) { 1900 }, 2f))
    check(!isDepthOccluded(IntArray(9) { 3000 }, 2f))
    check(!isDepthOccluded(IntArray(9), 2f))
    check(!isDepthOccluded(IntArray(9) { 199 }, 2f))
    check(!isDepthOccluded(IntArray(9) { 8001 }, 10f))
    check(!isDepthOccluded(intArrayOf(1000, 1000, 1000, 1000, 0, 0, 0, 0, 0), 2f))
    check(isDepthOccluded(intArrayOf(1000, 1000, 1000, 1000, 1000, 0, 0, 0, 0), 2f))
    check(!isDepthOccluded(IntArray(9) { 1000 }, Float.NaN))
    check(!isDepthOccluded(IntArray(9) { 1000 }, -1f))
    check(!isDepthOccluded(IntArray(9) { 1000 }, 0f))
    check(!isDepthOccluded(IntArray(0), 2f))
    // Padded rows/pixels, unsigned values, zero values, and cropped/boundary samples.
    val bytes = ByteBuffer.allocate(16)
    bytes.put(12, 0x34.toByte()); bytes.put(13, 0xF2.toByte())
    check(depthMillimeters(bytes, 2, 2, 8, 4, 1, 1) == 0xF234)
    check(depthMillimeters(bytes, 2, 2, 8, 4, 0, 0) == 0)
    check(depthMillimeters(bytes, 2, 2, 8, 4, -1, 0) == 0)
    check(depthMillimeters(bytes, 2, 2, 8, 4, 2, 0) == 0)
    check(depthMillimeters(bytes, 2, 2, 8, 4, 0, 2) == 0)
    bytes.limit(13)
    check(depthMillimeters(bytes, 2, 2, 8, 4, 1, 1) == 0)
    check(depthMillimeters(bytes, 2, 2, Int.MAX_VALUE, 4, 1, 1) == 0)
    val samplePoints = pinDepthSamplePoints(PinBounds(100f, 200f, 300f, 600f)).toList().chunked(2)
    check(samplePoints.size == 9)
    samplePoints.forEachIndexed { index, point ->
        check(kotlin.math.abs(point[0] - listOf(160f, 200f, 240f)[index % 3]) < 0.001f)
        check(kotlin.math.abs(point[1] - listOf(300f, 380f, 460f)[index / 3]) < 0.001f)
    }
    check(samplePoints.none { it[1] >= 580f }) // The floor contact point cannot hide the whole body.
    val gate = DepthOcclusionGate()
    check(!gate.hidden(true, 0))
    check(!gate.hidden(true, 200))
    check(!gate.hidden(true, 399))
    check(gate.hidden(true, 400))
    check(!gate.hidden(false, 401)) // Reveal immediately when a blocker clears or depth is unavailable.
    check(!gate.hidden(true, 402))
    check(!gate.hidden(true, 602))
    check(gate.hidden(true, 802))
    check(!gate.hidden(true, 1200)) // Old samples cannot bridge a tracking/data gap.
    check(!gate.hidden(true, 1000)) // A backwards clock resets the streak.
    check(!DepthOcclusionGate().hidden(true, 2000)) // Different anchors have independent histories.
    println("AR depth occlusion checks passed")
    println("AR body sampling and transient occlusion recovery checks passed")
}
