package com.yeogirokarpoc.geo

import kotlin.math.abs

// Run with PinProjection.kt and PinTapPolicy.kt using kotlinc, then execute this main.
fun main() {
    fun pin(depth: Float, focalX: Float = 2f, focalY: Float = 2f) =
        projectPin(floatArrayOf(0f, 0f, 0f, depth), 1080, 1920, focalX, focalY)
    fun close(actual: Float, expected: Float) = check(abs(actual - expected) < 0.001f) {
        "Expected $expected, got $actual"
    }
    val near = pin(1f)
    val far = pin(2f)
    check(near.visible && far.visible)
    close(near.heightPx, 307.2f * 3f) // 48 cm at 1 m, three times the initial dimensions.
    close(near.widthPx, 146.88f * 3f)
    close(far.heightPx, near.heightPx / 2f)
    close(far.widthPx, near.widthPx / 2f)
    close(near.x!!, far.x!!)
    close(near.y!!, far.y!!) // The surface contact point does not move when only depth changes.
    val side = projectPin(floatArrayOf(0.5f, 0f, 0f, 1f), 1080, 1920, 2f, 2f)
    close(side.heightPx, near.heightPx) // Use forward depth, not radial distance to the pin.
    close(side.x!!, 810f)
    close(pin(1f, 4f, 4f).heightPx, near.heightPx * 2f)
    val landscape = projectPin(floatArrayOf(0f, 0f, 0f, 1f), 1920, 1080, 2f, 2f)
    close(landscape.heightPx, 172.8f * 3f)
    check(!pin(-1f).visible && !pin(0f).visible && !pin(Float.NaN).visible)
    check(!pin(1f, focalX = Float.NaN).visible)
    check(!projectPin(floatArrayOf(0f, 0f, -2f, 1f), 1080, 1920, 2f, 2f).visible)
    check(!projectPin(floatArrayOf(0f, 0f, 2f, 1f), 1080, 1920, 2f, 2f).visible)
    check(!projectPin(floatArrayOf(3f, 0f, 0f, 1f), 1080, 1920, 2f, 2f).visible)
    val belowViewport = projectPin(floatArrayOf(0f, -1.1f, 0f, 1f), 1080, 1920, 2f, 2f, allowOutsideViewport = true)
    check(belowViewport.visible && belowViewport.y!! > 1920f)
    check(pinBoundsOnScreen(PinBounds(400f, 1400f, 700f, 2100f), 1080, 1920))
    check(!pinBoundsOnScreen(PinBounds(400f, 2000f, 700f, 2100f), 1080, 1920))
    check(!pinBoundsOnScreen(PinBounds(1100f, 400f, 1200f, 600f), 1080, 1920))
    check(!pinBoundsOnScreen(PinBounds(Float.NaN, 400f, 700f, 600f), 1080, 1920))
    check(!pinBoundsOnScreen(PinBounds(700f, 400f, 400f, 600f), 1080, 1920))
    check(!projectPin(floatArrayOf(0f, 0f, 0f, -1f), 1080, 1920, 2f, 2f, allowOutsideViewport = true).visible)

    val nearTarget = PinTapTarget(near, 1000L)
    val farTarget = PinTapTarget(far, 1000L)
    val tipX = near.x!!
    val tipY = near.y!!
    val upperPinY = tipY - near.heightPx * 0.8f
    check(nearTarget.contains(tipX, upperPinY, 1f, 1100L))
    check(!farTarget.contains(tipX, upperPinY, 1f, 1100L))
    check(PinTapTarget(pin(100f), 1000L).contains(tipX + 23f, tipY - 35f, 1f, 1100L))
    check(!nearTarget.contains(tipX, tipY, 1f, 1501L))
    val tap = PinTapPolicy(8f)
    tap.down(tipX, upperPinY, nearTarget, 1f, 1100L)
    check(tap.up(tipX, upperPinY, nearTarget, 1f, 1101L))
    tap.down(tipX, tipY, nearTarget, 1f, 1100L)
    tap.move(tipX + 9f, tipY)
    check(!tap.up(tipX + 9f, tipY, nearTarget, 1f, 1101L))

    // World up projected straight up on screen: no tilt. Phone rolled: the pin follows world up.
    val base = floatArrayOf(0f, 0f, 0f, 1f)
    close(pinTiltDegrees(base, floatArrayOf(0f, 0.1f, 0f, 1f), 1080, 1920), 0f)
    close(pinTiltDegrees(base, floatArrayOf(0.1f, 0f, 0f, 1f), 1080, 1080), 90f)
    close(pinTiltDegrees(base, floatArrayOf(-0.1f, 0.1f, 0f, 1f), 1080, 1080), -45f)
    close(pinTiltDegrees(base, floatArrayOf(0f, 0.1f, 0f, -1f), 1080, 1920), 0f) // Probe behind camera.
    close(pinTiltDegrees(base, base, 1080, 1920), 0f) // Degenerate direction stays upright.
    // A tilted pin keeps its hit box along its own axis, not the screen's vertical.
    val tilted = PinTapTarget(near.copy(tiltDegrees = 90f), 1000L)
    check(tilted.contains(tipX + near.heightPx * 0.8f, tipY, 1f, 1100L))
    check(!tilted.contains(tipX, upperPinY, 1f, 1100L))
    val edgeOn = PinTapTarget(near.copy(bounds = PinBounds(503f, 600f, 509f, 820f)), 1000L)
    check(edgeOn.contains(526f, 700f, 1f, 1100L)) // A thin side retains a 48 dp target.
    check(!edgeOn.contains(550f, 700f, 1f, 1100L))
    check(!edgeOn.contains(506f, 700f, 1f, 1501L))

    val tag = tipTagVertices(0.48f, 5f / 3f).toList().chunked(6)
    close(TIP_TAG_SCALE, 2.5f)
    close(tag.maxOf { it[1] } - tag.minOf { it[1] }, 0.15f)
    close(tag.maxOf { it[0] } - tag.minOf { it[0] }, 0.25f)
    close(tag.minOf { it[1] }, 0.505f)
    check(tag.all { it[2] == 0f }) // Same local plane and heading as the marker.
    fun tagPoint(depth: Float, rolled: Boolean): PinProjection {
        val y = tag.minOf { it[1] }
        return projectPin(if (rolled) floatArrayOf(-y, 0f, 0f, depth) else floatArrayOf(0f, y, 0f, depth),
            1080, 1080, 1f, 1f)
    }
    val uprightTag = tagPoint(2f, false)
    val rolledTag = tagPoint(2f, true)
    close(uprightTag.x!!, 540f)
    close(rolledTag.y!!, 540f)
    close(540f - rolledTag.x!!, 540f - uprightTag.y!!) // Roll rotates the world offset around the anchor.
    close(540f - tagPoint(4f, false).y!!, (540f - uprightTag.y!!) / 2f)
    check(runCatching { tipTagVertices(0.48f, Float.NaN) }.isFailure)
    check(runCatching { tipTagVertices(0.48f, 0f) }.isFailure)
    println("AR pin perspective and touch checks passed")
    println("AR tag world placement, camera roll, perspective and 2.5x size checks passed")
}
