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
    println("AR depth occlusion checks passed")
}
