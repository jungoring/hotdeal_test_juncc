package com.jungoring.hotdeal.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView

/** 웹 버전(docs/index.html)의 다크 팔레트 */
object Colors {
    val BG = 0xFF0F1115.toInt()
    val BG2 = 0xFF151922.toInt()
    val CARD = 0xFF1A1F2A.toInt()
    val CARD2 = 0xFF202736.toInt()
    val LINE = 0xFF2A3140.toInt()
    val TXT = 0xFFE8ECF3.toInt()
    val DIM = 0xFF98A2B3.toInt()
    val DIM2 = 0xFF6B7686.toInt()
    val GREEN = 0xFF2FD07F.toInt()
    val RED = 0xFFFF7A7A.toInt()
    val ACCENT = 0xFF5B8CFF.toInt()
    val MALL = 0xFF33405A.toInt()

    /** 반투명 배경/테두리 */
    val GREEN_BG = 0x1F2FD07F
    val GREEN_LINE = 0x732FD07F
    val RED_BG = 0x1AFF7A7A
    val RED_LINE = 0x59FF7A7A
    val ACCENT_BG = 0x245B8CFF
}

class Ui(private val ctx: Context) {

    fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        ctx.resources.displayMetrics
    ).toInt()

    fun text(sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    fun round(
        fill: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
        strokeDp: Float = 1f
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fill)
        if (strokeColor != null) {
            setStroke(dp(strokeDp).coerceAtLeast(1), strokeColor)
        }
    }
}
