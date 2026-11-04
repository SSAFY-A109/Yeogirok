package com.yeogirokarpoc.geo

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20.*
import android.opengl.GLUtils
import android.opengl.Matrix
import com.google.ar.core.Pose
import com.google.ar.core.examples.java.common.rendering.ShaderUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.sqrt

/** One textured, bevelled mesh shared by all tip anchors in this GL context. */
internal class TipPinRenderer(context: Context) {
    private val program = glCreateProgram()
    private val texture = IntArray(1)
    private val mvp = FloatArray(16)
    private val model = FloatArray(16)
    private val vertices = mutableListOf<FloatArray>()
    private val buffer = run {
        val uvs = mutableListOf<FloatArray>()
        val data = ArrayList<Float>()
        context.assets.open("tip-pin.obj").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val fields = line.split(' ')
                when (fields.first()) {
                    "v" -> vertices.add(fields.drop(1).map(String::toFloat).toFloatArray())
                    "vt" -> uvs.add(fields.drop(1).map(String::toFloat).toFloatArray())
                    "f" -> {
                        val corners = fields.drop(1).map { it.split('/').map(String::toInt) }
                        val (a, b, c) = corners.map { vertices[it[0] - 1] }
                        val shade = faceBrightness(a, b, c)
                        for ((v, t) in corners) {
                            for (coord in vertices[v - 1]) data.add(coord)
                            data.add(uvs[t - 1][0])
                            data.add(1f - uvs[t - 1][1]) // Bitmap rows start at the top.
                            data.add(shade)
                        }
                    }
                }
            }
        }
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(data.toFloatArray())
            position(0)
        }
    }
    private val matrixLocation: Int
    private val position: Int
    private val surface: Int

    init {
        for ((type, file) in listOf(GL_VERTEX_SHADER to "tip-pin.vert", GL_FRAGMENT_SHADER to "tip-pin.frag")) {
            val shader = ShaderUtil.loadGLShader("TipPin", context, type, "shaders/$file")
            glAttachShader(program, shader)
            glDeleteShader(shader)
        }
        glLinkProgram(program)
        val linked = IntArray(1)
        glGetProgramiv(program, GL_LINK_STATUS, linked, 0)
        check(linked[0] == GL_TRUE) { glGetProgramInfoLog(program) }
        matrixLocation = glGetUniformLocation(program, "mvp")
        position = glGetAttribLocation(program, "position")
        surface = glGetAttribLocation(program, "surface")
        glGenTextures(1, texture, 0)
        glBindTexture(GL_TEXTURE_2D, texture[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        val bitmap = context.assets.open("figma/51db5.png").use { BitmapFactory.decodeStream(it) }
        try { GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0) } finally { bitmap.recycle() }
        ShaderUtil.checkGLError("TipPin", "upload")
    }

    fun draw(vp: FloatArray, pose: Pose, selected: Boolean, width: Int, height: Int): PinBounds {
        // Keep the anchor's heading as the viewer walks around it, but always stand upright.
        val forward = pose.zAxis
        val yaw = if (forward[0]*forward[0] + forward[2]*forward[2] > 0.001f)
            atan2(forward[0], forward[2])
        else pose.xAxis.let { atan2(-it[2], it[0]) }
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, pose.tx(), pose.ty(), pose.tz())
        Matrix.rotateM(model, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        if (selected) Matrix.scaleM(model, 0, 1.4f, 1.4f, 1.4f)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)

        glUseProgram(program)
        glUniformMatrix4fv(matrixLocation, 1, false, mvp, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, texture[0])
        buffer.position(0)
        glVertexAttribPointer(position, 3, GL_FLOAT, false, 24, buffer)
        buffer.position(3)
        glVertexAttribPointer(surface, 3, GL_FLOAT, false, 24, buffer)
        glEnableVertexAttribArray(position)
        glEnableVertexAttribArray(surface)
        glDrawArrays(GL_TRIANGLES, 0, buffer.capacity() / 6)
        glDisableVertexAttribArray(position)
        glDisableVertexAttribArray(surface)

        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for ((x, y, z) in vertices) {
            val w = mvp[3]*x + mvp[7]*y + mvp[11]*z + mvp[15]
            if (w <= 0f) continue
            val px = (1f + (mvp[0]*x + mvp[4]*y + mvp[8]*z + mvp[12]) / w) * width / 2f
            val py = (1f - (mvp[1]*x + mvp[5]*y + mvp[9]*z + mvp[13]) / w) * height / 2f
            left = minOf(left, px)
            right = maxOf(right, px)
            top = minOf(top, py)
            bottom = maxOf(bottom, py)
        }
        return PinBounds(left, top, right, bottom)
    }
}

private fun faceBrightness(a: FloatArray, b: FloatArray, c: FloatArray): Float {
    val ab = FloatArray(3) { b[it] - a[it] }
    val ac = FloatArray(3) { c[it] - a[it] }
    val x = ab[1]*ac[2] - ab[2]*ac[1]
    val y = ab[2]*ac[0] - ab[0]*ac[2]
    val z = ab[0]*ac[1] - ab[1]*ac[0]
    if (x == 0f && y == 0f) return 1f // Leave the original artwork on both faces unlit.
    return 0.74f + (-0.14f*x + 0.20f*y) / sqrt(x*x + y*y + z*z)
}
