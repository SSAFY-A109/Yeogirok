package com.yeogirokarpoc.geo

import android.media.Image
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.io.Closeable

/** One current-frame image shared by all pins, owned and closed on the GL thread. */
internal class DepthOcclusion private constructor(private val frame: Frame, private val image: Image) : Closeable {
    private val texture = FloatArray(18)
    private val samples = IntArray(9)

    fun apply(pin: PinProjection): PinProjection {
        if (!pin.visible) return pin
        val bounds = pin.bounds ?: return pin
        val screen = pinDepthSamplePoints(bounds)
        // The depth image follows texture orientation, not portrait screen orientation.
        frame.transformCoordinates2d(Coordinates2d.VIEW, screen, Coordinates2d.TEXTURE_NORMALIZED, texture)
        val plane = image.planes[0]
        val buffer = plane.buffer
        for (index in samples.indices) {
            val u = texture[index * 2]
            val v = texture[index * 2 + 1]
            samples[index] = if (u.isFinite() && v.isFinite() && u >= 0f && u < 1f && v >= 0f && v < 1f)
                depthMillimeters(buffer, image.width, image.height, plane.rowStride, plane.pixelStride,
                    (u * image.width).toInt(), (v * image.height).toInt()) else 0
        }
        // All tips use the same anchor-level occlusion policy.
        return if (isDepthOccluded(samples, pin.cameraDepthMeters))
            pin.copy(x = null, y = null, occluded = true, guidance = "핀은 앞의 표면에 가려져 있어요.") else pin
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
