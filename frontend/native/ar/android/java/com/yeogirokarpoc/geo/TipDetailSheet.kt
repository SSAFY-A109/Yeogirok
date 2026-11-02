package com.yeogirokarpoc.geo

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

object TipDetailSheet {
    fun show(activity: Activity, tip: JSONObject, distanceMeters: Double?, onDismiss: () -> Unit): Dialog {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val theme = ArTheme(activity)
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        fun label(value: String, size: Float, color: Int = theme.foreground) = TextView(activity).apply {
            text = value; textSize = size; setTextColor(color); typeface = theme.regular; includeFontPadding = false
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), dp(16))
            background = theme.background(theme.card, dp(20).toFloat())
        }
        val handle = FrameLayout(activity).apply {
            addView(View(activity).apply { background = theme.background(theme.borderStrong, dp(2).toFloat()) },
                FrameLayout.LayoutParams(dp(36), dp(4), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) })
        }
        column.addView(handle, LinearLayout.LayoutParams(-1, dp(12)))
        val author = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("나", 11f).apply {
                gravity = Gravity.CENTER; typeface = theme.medium; background = theme.background(theme.accent, dp(12).toFloat())
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(label("내 팁", 14f).apply { typeface = theme.medium },
                LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            val minutes = Duration.between(Instant.parse(tip.getString("createdAt")), Instant.now()).toMinutes().coerceAtLeast(0)
            val age = if (minutes < 1) "방금" else if (minutes < 60) "" + minutes + "분 전" else if (minutes < 1440) "" + minutes / 60 + "시간 전" else "" + minutes / 1440 + "일 전"
            addView(label("· " + age + (distanceMeters?.let { " · %.1fm 앞".format(it) } ?: ""), 12f, theme.mutedText),
                LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        }
        column.addView(author, LinearLayout.LayoutParams(-1, dp(24)).apply { topMargin = dp(12) })
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; minimumHeight = dp(72)
            addView(label(tip.getString("body"), 14f).apply { setLineSpacing(dp(3).toFloat(), 1f); setTextIsSelectable(true) },
                LinearLayout.LayoutParams(0, -2, 1f))
            if (!tip.isNull("photoName")) {
                val file = LocalTipStore.photoFile(activity, tip.getString("photoName"))
                if (file.isFile) addView(ImageView(activity).apply {
                    contentDescription = "첨부한 팁 사진"; scaleType = ImageView.ScaleType.CENTER_CROP
                    background = theme.background(theme.highlightMuted, dp(12).toFloat()); clipToOutline = true
                    setImageURI(android.net.Uri.fromFile(file))
                    setOnClickListener {
                        val full = ImageView(activity).apply {
                            adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = "팁 첨부 사진 크게 보기"; setImageURI(android.net.Uri.fromFile(file))
                        }
                        android.app.AlertDialog.Builder(activity).setView(full).setPositiveButton("닫기", null).show()
                    }
                }, LinearLayout.LayoutParams(dp(72), dp(72)).apply { leftMargin = dp(12) })
                else addView(label("사진을 불러오지 못했어요", 11f, theme.mutedText), LinearLayout.LayoutParams(dp(72), -2).apply { leftMargin = dp(12) })
            }
        }
        val scroll = object : ScrollView(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(dp(180), View.MeasureSpec.AT_MOST))
            }
        }.apply { addView(body); isFillViewport = false }
        column.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val reactions = LinearLayout(activity)
        var current = LocalTipStore.reaction(activity, tip.getString("id"))
        val choices = listOf("LIKE", "DISLIKE")
        val buttons = mutableListOf<Button>()
        fun refresh() {
            buttons.forEachIndexed { index, button ->
                val selected = current == choices[index]
                val icon = ArSvg(activity, if (index == 0) "41dd1.svg" else "fdf8b.svg").apply {
                    if (selected) tint = theme.primary
                    setBounds(0, 0, dp(16), dp(16))
                }
                val text = SpannableString("  " + (if (index == 0) "도움돼요 " else "별로예요 ") + if (selected) "1" else "0")
                text.setSpan(ImageSpan(icon, ImageSpan.ALIGN_BASELINE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                button.text = text; button.isSelected = selected
                button.setTextColor(if (selected) theme.primary else theme.foreground)
                button.background = theme.background(if (selected) theme.brandMuted else theme.card, dp(8).toFloat())
                    .apply { setStroke(dp(1), if (selected) theme.primary else theme.border) }
                button.contentDescription = (if (index == 0) "도움돼요" else "별로예요") + if (selected) " 선택됨. 다시 누르면 취소" else " 선택하기"
            }
        }
        choices.forEachIndexed { index, choice ->
            val button = Button(activity).apply {
                textSize = 14f; typeface = theme.medium; isAllCaps = false; includeFontPadding = false
                minWidth = 0; minHeight = 0; setPadding(dp(8), 0, dp(8), 0); elevation = 0f; stateListAnimator = null
                setOnClickListener {
                    try { current = LocalTipStore.toggleReaction(activity, tip.getString("id"), choice); refresh() }
                    catch (_: Exception) { Toast.makeText(activity, "반응을 저장하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show() }
                }
            }
            buttons.add(button)
            reactions.addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (index > 0) leftMargin = dp(8) })
        }
        refresh()
        column.addView(reactions, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) })
        val root = FrameLayout(activity).apply {
            addView(column)
            var downY = 0f
            addView(View(activity).apply {
                contentDescription = "팁 상세 닫기"; isClickable = true
                setOnClickListener { dialog.dismiss() }
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> downY = event.rawY
                        MotionEvent.ACTION_UP -> {
                            if (event.rawY - downY > dp(24)) dialog.dismiss() else view.performClick()
                        }
                    }
                    true
                }
            }, FrameLayout.LayoutParams(-1, dp(44), Gravity.TOP))
        }
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            setGravity(Gravity.BOTTOM); setLayout(-1, -2)
            attributes = attributes.apply { y = dp(56) }
        }
        return dialog
    }
}

