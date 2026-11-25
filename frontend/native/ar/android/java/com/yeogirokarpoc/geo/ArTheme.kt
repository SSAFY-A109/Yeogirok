package com.yeogirokarpoc.geo

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import org.json.JSONObject

class ArTheme(private val context: Context) {
    private val palette = JSONObject(context.assets.open("ar-theme.json").bufferedReader().use { it.readText() })
    val primary = Color.parseColor(palette.getString("primary"))
    val foreground = Color.parseColor(palette.getString("foreground"))
    val card = Color.parseColor(palette.getString("card"))
    val muted = Color.parseColor(palette.getString("muted"))
    val route = Color.parseColor(palette.getString("map-route"))
    val footprint = Color.parseColor(palette.getString("map-footprint"))
    val secondary = Color.parseColor(palette.getString("foreground-secondary"))
    val mutedText = Color.parseColor(palette.getString("muted-foreground"))
    val border = Color.parseColor(palette.getString("border"))
    val borderStrong = Color.parseColor(palette.getString("border-strong"))
    val accent = Color.parseColor(palette.getString("accent"))
    val brandMuted = Color.parseColor(palette.getString("brand-muted"))
    val positive = Color.parseColor(palette.getString("positive"))
    val destructive = Color.parseColor(palette.getString("destructive"))
    val highlightMuted = Color.parseColor(palette.getString("highlight-muted"))
    private fun font(weight: Int): Typeface = if (android.os.Build.VERSION.SDK_INT >= 26)
        Typeface.Builder(context.assets, "NotoSansKR.ttf").setFontVariationSettings("'wght' $weight").setWeight(weight).build()
        else Typeface.createFromAsset(context.assets, "NotoSansKR.ttf")
    val regular: Typeface = font(400)
    val medium: Typeface = font(500)
    val bold: Typeface = font(700)
    fun background(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }
}
