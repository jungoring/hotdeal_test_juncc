package com.jungoring.hotdeal.ui

import android.content.Context
import android.graphics.Outline
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatImageView

/**
 * 목록 카드의 작은 제품 사진용 둥근 정사각형 이미지.
 * (모서리를 잘라 내고, 사진이 없을 때는 배경만 남는다)
 */
class RoundedImageView(context: Context, private val radiusDp: Float) : AppCompatImageView(context) {

    private val radiusPx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        radiusDp,
        context.resources.displayMetrics
    )

    init {
        scaleType = ScaleType.CENTER_CROP
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        clipToOutline = true
        // 사진을 받아 오기 전 잠깐 보이는 자리 (사진이 없는 글은 이 상자를 숨긴다)
        setBackgroundColor(Colors.CARD2)
    }
}
