package com.yeogirokarpoc.geo

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.PathParser
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** Renders the downloaded Figma SVG assets without copying or replacing their paths. */
class ArSvg(context: Context, asset: String) : Drawable() {
    private val root = context.assets.open("figma/$asset").use {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).documentElement
    }
    private val sourceWidth = root.getAttribute("width").toFloat()
    private val sourceHeight = root.getAttribute("height").toFloat()
    private val references = (0 until root.getElementsByTagName("*").length).map { root.getElementsByTagName("*").item(it) as Element }
        .filter { it.hasAttribute("id") }.associateBy { it.getAttribute("id") }
    var tint: Int? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun getIntrinsicWidth() = (sourceWidth * contextDensity).toInt()
    override fun getIntrinsicHeight() = (sourceHeight * contextDensity).toInt()
    private val contextDensity = context.resources.displayMetrics.density
    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / sourceWidth, bounds.height() / sourceHeight)
        drawElement(canvas, root, emptyMap())
        canvas.restore()
    }
    private fun drawElement(canvas: Canvas, node: Element, inherited: Map<String, String>) {
        if (node.tagName in listOf("defs", "mask", "clipPath")) return
        canvas.save()
        for (attribute in listOf("mask", "clip-path")) {
            references[node.getAttribute(attribute).substringAfter('#').substringBefore(')')]?.let { mask ->
                val clip = Path()
                for (i in 0 until mask.childNodes.length) (mask.childNodes.item(i) as? Element)?.let { shape(it)?.let(clip::addPath) }
                canvas.clipPath(clip)
            }
        }
        val attributes = inherited.toMutableMap()
        for (key in listOf("fill", "stroke", "stroke-width", "fill-opacity", "stroke-opacity", "opacity", "fill-rule")) {
            if (node.hasAttribute(key)) attributes[key] = node.getAttribute(key)
        }
        val path = shape(node)
        if (path != null) {
            if (attributes["fill-rule"] == "evenodd") path.fillType = Path.FillType.EVEN_ODD
            for (style in listOf("fill", "stroke")) {
                val color = attributes[style] ?: if (style == "fill") "black" else "none"
                if (color == "none") continue
                paint.color = tint ?: Color.parseColor(color)
                paint.alpha = (255 * (attributes["opacity"]?.toFloatOrNull() ?: 1f) * (attributes["$style-opacity"]?.toFloatOrNull() ?: 1f)).toInt()
                paint.style = if (style == "fill") Paint.Style.FILL else Paint.Style.STROKE
                paint.strokeWidth = attributes["stroke-width"]?.toFloatOrNull() ?: 1f
                canvas.drawPath(path, paint)
            }
        }
        for (i in 0 until node.childNodes.length) (node.childNodes.item(i) as? Element)?.let { drawElement(canvas, it, attributes) }
        canvas.restore()
    }
    private fun shape(node: Element): Path? = when (node.tagName) {
            "path" -> PathParser.createPathFromPathData(node.getAttribute("d"))
            "circle" -> Path().apply { addCircle(node.getAttribute("cx").toFloat(), node.getAttribute("cy").toFloat(), node.getAttribute("r").toFloat(), Path.Direction.CW) }
            "rect" -> Path().apply { addRect(node.getAttribute("x").toFloatOrNull() ?: 0f, node.getAttribute("y").toFloatOrNull() ?: 0f,
                (node.getAttribute("x").toFloatOrNull() ?: 0f) + node.getAttribute("width").toFloat(),
                (node.getAttribute("y").toFloatOrNull() ?: 0f) + node.getAttribute("height").toFloat(), Path.Direction.CW) }
            else -> null
        }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class ArIcon(context: Context, asset: String, tint: Int? = null) : View(context) {
    private val svg = ArSvg(context, asset).also { it.tint = tint }
    override fun onDraw(canvas: Canvas) { svg.setBounds(0, 0, width, height); svg.draw(canvas) }
}
