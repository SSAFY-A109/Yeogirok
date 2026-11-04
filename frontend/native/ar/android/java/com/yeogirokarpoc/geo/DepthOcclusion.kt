package com.yeogirokarpoc.geo

import android.media.Image
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.io.Closeable

/** One current-frame image shared by all pins, owned and closed on the GL thread. */
internal class DepthOcclusion private constructor(private val frame: Frame, private val image: Image) : Closeable {
    private val screen = FloatArray(2)
    private val texture = FloatArray(2)
    private val samples = IntArray(9)

    fun apply(pin: PinProjection): PinProjection {
        if (!pin.visible) return pin
        screen[0] = pin.x!!; screen[1] = pin.y!!
        // The depth image follows texture orientation, not portrait screen orientation.
        frame.transformCoordinates2d(Coordinates2d.VIEW, screen, Coordinates2d.TEXTURE_NORMALIZED, texture)
        if (!texture.all { it.isFinite() && it >= 0f && it < 1f }) return pin
        val x = (texture[0] * image.width).toInt()
        val y = (texture[1] * image.height).toInt()
        val plane = image.planes[0]
        val buffer = plane.buffer
        var index = 0
        for (dy in -1..1) for (dx in -1..1) {
            samples[index++] = depthMillimeters(buffer, image.width, image.height,
                plane.rowStride, plane.pixelStride, x + dx, y + dy)
        }
        // All tips use the same anchor-level occlusion policy.
        return if (isDepthOccluded(samples, pin.cameraDepthMeters))
            pin.copy(x = null, y = null, guidance = "핀은 앞의 표면에 가려져 있어요.") else pin
    }

    override fun close() = image.close()

    companion object {
        fun acquire(frame: Frame, supported: Boolean): DepthOcclusion? {
            if (!supported || frame.camera.trackingState != TrackingState.TRACKING) return null
            return try { DepthOcclusion(frame, frame.acquireDepthImage16Bits()) }
            catch (_: NotYetAvailableException) { null }
        }
    }
}
