package com.jungoring.hotdeal.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.jungoring.hotdeal.model.Deal

/** 카드 한 장을 이루는 뷰 묶음 (쇼핑몰 링크를 확인하는 동안 이 참조로 버튼만 다시 그린다) */
class CardViews(
    val root: LinearLayout,
    val body: LinearLayout,
    val tags: LinearLayout,
    val title: TextView,
    val price: TextView,
    val ship: TextView,
    val meta: TextView,
    val actions: LinearLayout,
    val btnShop: TextView,
    val btnPost: TextView
)

class CardFactory(private val ctx: Context) {

    private val ui = Ui(ctx)

    fun create(): CardViews {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.round(Colors.CARD, 16f, Colors.LINE, 1f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ui.dp(9f) }
        }

        val tags = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = ui.text(15.5f, Colors.TXT, true).apply {
            setPadding(0, ui.dp(7f), 0, ui.dp(8f))
            setLineSpacing(ui.dp(2f).toFloat(), 1f)
        }

        val price = ui.text(17f, Colors.TXT, true)
        val ship = ui.text(12f, Colors.DIM, false).apply {
            setPadding(ui.dp(8f), 0, 0, 0)
        }
        val priceRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(price, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(ship, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        val meta = ui.text(12f, Colors.DIM2, false).apply {
            setPadding(0, ui.dp(8f), 0, 0)
        }

        val btnShop = actionButton("쇼핑몰 바로가기", true)
        val btnPost = actionButton("펨코 원문", false)

        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ui.dp(12f), 0, 0)
            addView(btnShop, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnPost, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ui.dp(8f)
            })
        }

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14f), ui.dp(13f), ui.dp(14f), ui.dp(13f))
            // 카드를 눌렀을 때 반응이 보이도록 기본 선택 효과를 준다
            val outValue = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                setBackgroundResource(outValue.resourceId)
            }
            addView(tags, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(title, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(priceRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(meta, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(actions, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        root.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        return CardViews(
            root = root,
            body = body,
            tags = tags,
            title = title,
            price = price,
            ship = ship,
            meta = meta,
            actions = actions,
            btnShop = btnShop,
            btnPost = btnPost
        )
    }

    fun bind(
        v: CardViews,
        d: Deal,
        ago: String,
        onShop: () -> Unit,
        onPost: () -> Unit
    ) {
        // 태그 줄 (쇼핑몰 / 카테고리 / 종료)
        v.tags.removeAllViews()
        val mall = d.mall
        if (!mall.isNullOrBlank()) {
            v.tags.addView(chip(mall, Colors.MALL, 0xFFFFFFFF.toInt(), true))
        }
        val category = d.category
        if (!category.isNullOrBlank()) {
            v.tags.addView(chip(category, 0x00000000, Colors.DIM, false))
        }
        if (d.isEnded) {
            v.tags.addView(chip("종료", Colors.RED_BG, Colors.RED, false))
        }
        v.tags.visibility = if (v.tags.childCount > 0) View.VISIBLE else View.GONE

        v.title.text = d.title

        val priceText = d.price
        v.price.text = if (priceText.isNullOrBlank()) "가격 미표기" else priceText
        val shipping = d.shipping
        if (shipping.isNullOrBlank()) {
            v.ship.visibility = View.GONE
        } else {
            v.ship.visibility = View.VISIBLE
            v.ship.text = "배송 " + shipping
        }

        v.meta.text = "▲ " + (d.upvotes ?: 0) + "   💬 " + (d.comments ?: 0) + "   " + ago

        // 쇼핑몰 바로가기: 링크를 이미 알고 있으면 바로, 아니면 누른 순간 그 글만 확인한다
        if (d.shopLoading) {
            v.btnShop.text = "쇼핑몰 링크 확인 중…"
            v.btnShop.alpha = 0.7f
            v.btnShop.isClickable = false
            v.btnShop.setOnClickListener(null)
        } else {
            v.btnShop.text = "쇼핑몰 바로가기"
            v.btnShop.alpha = 1f
            v.btnShop.isClickable = true
            v.btnShop.setOnClickListener { onShop() }
        }

        val postUrl = d.url
        if (postUrl.isNullOrBlank()) {
            v.btnPost.alpha = 0.4f
            v.btnPost.isClickable = false
            v.btnPost.setOnClickListener(null)
        } else {
            v.btnPost.alpha = 1f
            v.btnPost.isClickable = true
            v.btnPost.setOnClickListener { onPost() }
        }
    }

    // ── 조각들 ────────────────────────────────────────────

    private fun chip(label: String, fill: Int, textColor: Int, solid: Boolean): TextView {
        val tv = ui.text(11f, textColor, true)
        tv.text = label
        tv.setPadding(ui.dp(7f), ui.dp(2f), ui.dp(7f), ui.dp(2f))
        tv.background = if (solid) {
            ui.round(fill, 6f)
        } else {
            ui.round(fill, 6f, if (fill == Colors.RED_BG) Colors.RED_LINE else Colors.LINE, 1f)
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = ui.dp(5f)
        tv.layoutParams = params
        return tv
    }

    private fun actionButton(label: String, primary: Boolean): TextView {
        val tv = ui.text(13f, if (primary) 0xFFFFFFFF.toInt() else Colors.TXT, true)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(ui.dp(6f), ui.dp(11f), ui.dp(6f), ui.dp(11f))
        tv.background = if (primary) {
            ui.round(Colors.ACCENT, 11f)
        } else {
            ui.round(Colors.CARD2, 11f, Colors.LINE, 1f)
        }
        tv.isClickable = true
        return tv
    }
}
