package com.yeogirokarpoc.geo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Screen-fixed turn indicator over the camera image, not a ground/path anchor. */
class DirectionArrowView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrow = Path().apply {
        moveTo(0f, -29f); lineTo(25f, -3f); lineTo(9f, -3f)
        lineTo(9f, 25f); lineTo(-9f, 25f); lineTo(-9f, -3f)
        lineTo(-25f, -3f); close()
    }
    private var angle: Double? = null
    private var precise = false
    init { visibility = INVISIBLE; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    fun update(value: Double?, anchorBased: Boolean) {
        angle = value?.takeIf { it.isFinite() }
        precise = anchorBased
        visibility = if (angle == null) INVISIBLE else VISIBLE
        contentDescription = angle?.let {
            "${if (precise) "복원된 팁 방향" else "대략적인 탐색 방향"}, ${NavigationPolicy.turn(it)}"
        }
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val direction = angle ?: return
        canvas.save()
        canvas.scale(width / 128f, height / 154f)
        paint.color = Color.argb(215, 17, 24, 39)
        canvas.drawRoundRect(0f, 0f, 128f, 154f, 16f, 16f, paint)
        paint.textAlign = Paint.Align.CENTER; paint.textSize = 12f
        paint.color = Color.WHITE
        canvas.drawText(if (precise) "복원된 팁 방향" else "대략적인 방향", 64f, 22f, paint)
        canvas.save(); canvas.translate(64f, 76f); canvas.rotate(direction.toFloat())
        paint.color = if (precise) Color.rgb(255, 142, 65) else Color.rgb(80, 210, 255)
        canvas.drawPath(arrow, paint); canvas.restore()
        paint.color = Color.WHITE; paint.textSize = 13f
        // "Behind" is a turn-around instruction, never an instruction to go downstairs.
        canvas.drawText(NavigationPolicy.turn(direction), 64f, 131f, paint)
        canvas.restore()
    }
}
