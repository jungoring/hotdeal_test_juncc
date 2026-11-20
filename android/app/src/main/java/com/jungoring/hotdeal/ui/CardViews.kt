package com.jungoring.hotdeal.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.jungoring.hotdeal.model.Comparison
import com.jungoring.hotdeal.model.Deal

/** 카드 한 장을 이루는 뷰 묶음 (상세 정보가 늦게 도착해도 이 참조로 그 부분만 다시 그린다) */
class CardViews(
    val root: LinearLayout,
    val body: LinearLayout,
    val tags: LinearLayout,
    val title: TextView,
    val price: TextView,
    val ship: TextView,
    val meta: TextView,
    val badge: TextView,
    val detail: LinearLayout,
    val detailHead: TextView,
    val detailCount: TextView,
    val cmpBox: LinearLayout,
    val hint: TextView,
    val btnShop: TextView,
    val btnPost: TextView
)

class CardFactory(private val ctx: Context) {

    private val ui = Ui(ctx)

    private fun nf(value: Long?): String = String.format("%,d", value ?: 0L)

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

        val badge = ui.text(12.5f, Colors.GREEN, true).apply {
            setPadding(ui.dp(9f), ui.dp(5f), ui.dp(9f), ui.dp(5f))
            visibility = View.GONE
        }

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14f), ui.dp(13f), ui.dp(14f), ui.dp(13f))
            isClickable = true
            val outValue = TypedValue()
            if (context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground,
                    outValue,
                    true
                )
            ) {
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
            addView(badge, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = ui.dp(9f) })
        }

        val detailHead = ui.text(12.5f, Colors.TXT, true)
        val detailCount = ui.text(11f, Colors.DIM2, false)

        val headRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(detailHead, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(detailCount, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        val cmpBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(9f), 0, 0)
        }

        val hint = ui.text(11.5f, Colors.DIM2, false).apply {
            setPadding(0, ui.dp(9f), 0, 0)
            setLineSpacing(ui.dp(3f).toFloat(), 1f)
            visibility = View.GONE
        }

        val btnShop = actionButton("쇼핑몰 바로가기", true)
        val btnPost = actionButton("펨코 원문 보기", false)

        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ui.dp(12f), 0, 0)
            addView(btnShop, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnPost, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ui.dp(8f)
            })
        }

        val detailInner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14f), ui.dp(12f), ui.dp(14f), ui.dp(14f))
            addView(headRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(cmpBox, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(hint, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(actions, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        val divider = View(ctx).apply {
            setBackgroundColor(Colors.LINE)
        }

        val detail = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Colors.BG2)
            visibility = View.GONE
            addView(divider, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(1f)
            ))
            addView(detailInner, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        root.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(detail, LinearLayout.LayoutParams(
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
            badge = badge,
            detail = detail,
            detailHead = detailHead,
            detailCount = detailCount,
            cmpBox = cmpBox,
            hint = hint,
            btnShop = btnShop,
            btnPost = btnPost
        )
    }

    fun bind(
        v: CardViews,
        d: Deal,
        expanded: Boolean,
        ago: String,
        onOpen: (String) -> Unit
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
            val reports = if (d.endedReports > 0) d.endedReports else 1
            v.tags.addView(chip("종료 신고 " + reports, Colors.RED_BG, Colors.RED, false))
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

        // 배지
        val cheaper = d.isCheaper
        val best = d.bestSavings
        when {
            cheaper == true && best != null -> {
                v.badge.visibility = View.VISIBLE
                v.badge.text = "이전보다 " + nf(best) + "원 저렴"
                styleBadge(v.badge, Colors.GREEN, Colors.GREEN_BG, Colors.GREEN_LINE)
            }
            cheaper == false -> {
                val overpay = d.worstOverpay
                v.badge.visibility = View.VISIBLE
                v.badge.text = "이전 핫딜이 " + nf(if (overpay != null) -overpay else 0L) + "원 더 저렴"
                styleBadge(v.badge, Colors.RED, Colors.RED_BG, Colors.RED_LINE)
            }
            d.hasComparisons -> {
                v.badge.visibility = View.VISIBLE
                v.badge.text = "비교 문구 없음"
                styleBadge(v.badge, Colors.DIM, Colors.CARD2, null)
            }
            else -> v.badge.visibility = View.GONE
        }

        // 펼침 영역
        v.detail.visibility = if (expanded) View.VISIBLE else View.GONE
        v.detailHead.text = "최근 6개월 유사 핫딜"
        val total = d.comparisonTotal
        v.detailCount.text = when {
            total != null && total > 0 -> "총 " + nf(total.toLong()) + "건 중 " + d.comparisons.size + "건 표시"
            d.comparisons.isNotEmpty() -> d.comparisons.size.toString() + "건 표시"
            else -> "비교 목록 없음"
        }

        v.cmpBox.removeAllViews()
        if (d.comparisons.isNotEmpty()) {
            for (c in d.comparisons) v.cmpBox.addView(comparisonRow(c))
        } else {
            v.cmpBox.addView(note(
                if (d.detailLoaded) "이 글에는 아직 비교 데이터가 없습니다."
                else "비교 정보를 불러오는 중…"
            ))
        }

        val hintText = priceHint(d)
        if (hintText == null) {
            v.hint.visibility = View.GONE
        } else {
            v.hint.visibility = View.VISIBLE
            v.hint.text = hintText
        }

        val shopUrl = d.shopUrl
        if (shopUrl.isNullOrBlank()) {
            v.btnShop.alpha = 0.4f
            v.btnShop.isClickable = false
            v.btnShop.setOnClickListener(null)
        } else {
            v.btnShop.alpha = 1f
            v.btnShop.isClickable = true
            v.btnShop.setOnClickListener { onOpen(shopUrl) }
        }

        val postUrl = d.url
        if (postUrl.isNullOrBlank()) {
            v.btnPost.alpha = 0.4f
            v.btnPost.isClickable = false
            v.btnPost.setOnClickListener(null)
        } else {
            v.btnPost.alpha = 1f
            v.btnPost.isClickable = true
            v.btnPost.setOnClickListener { onOpen(postUrl) }
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

    private fun styleBadge(tv: TextView, textColor: Int, bg: Int, stroke: Int?) {
        tv.setTextColor(textColor)
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        tv.setPadding(ui.dp(9f), ui.dp(5f), ui.dp(9f), ui.dp(5f))
        tv.background = if (stroke != null) ui.round(bg, 9f, stroke, 1f) else ui.round(bg, 9f)
    }

    private fun note(text: String): TextView = ui.text(12f, Colors.DIM2, false).apply {
        this.text = text
        setPadding(ui.dp(2f), ui.dp(8f), ui.dp(2f), ui.dp(2f))
    }

    private fun comparisonRow(c: Comparison): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(10f), ui.dp(9f), ui.dp(10f), ui.dp(9f))
            background = ui.round(Colors.CARD, 10f, Colors.LINE, 1f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ui.dp(7f) }
        }

        val name = ui.text(12.5f, Colors.TXT, false).apply {
            text = c.name
            setLineSpacing(ui.dp(1f).toFloat(), 1f)
        }
        row.addView(name, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val metaRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(4f), 0, 0)
        }
        val price = ui.text(11.5f, Colors.DIM, true)
        price.text = c.price ?: "가격 미표기"
        metaRow.addView(price, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = ui.dp(8f) })

        val diffText = c.diffText
        if (!diffText.isNullOrBlank()) {
            val cheaper = c.isCheaper == true
            val diff = ui.text(11.5f, if (cheaper) Colors.GREEN else Colors.RED, true)
            diff.text = diffText
            metaRow.addView(diff, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = ui.dp(8f) })
        }

        val posted = c.postedAt
        if (!posted.isNullOrBlank()) {
            val date = ui.text(11f, Colors.DIM2, false)
            date.text = posted
            metaRow.addView(date, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        row.addView(metaRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return row
    }

    private fun actionButton(label: String, primary: Boolean): TextView {
        val tv = ui.text(13f, if (primary) 0xFFFFFFFF.toInt() else Colors.TXT, true)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(ui.dp(6f), ui.dp(11f), ui.dp(6f), ui.dp(11f))
        tv.background = if (primary) {
            ui.round(Colors.ACCENT, 11f)
        } else {
            ui.round(Colors.CARD, 11f, Colors.LINE, 1f)
        }
        tv.isClickable = true
        return tv
    }

    /**
     * 사이트가 '더 저렴/더 비쌈' 문구를 주지 않은 글은 비교 목록의 가격끼리만 계산해
     * 참고 문장을 만든다(웹 버전 priceHint 와 같은 규칙).
     */
    private fun priceHint(d: Deal): String? {
        if (!d.hasComparisons) return null
        if (d.isCheaper != null) return null
        val priceKrw = d.priceKrw ?: return null
        val prices = d.comparisons.mapNotNull { it.priceKrw }
        if (prices.isEmpty()) return null
        val lowest = prices.minOrNull() ?: return null
        val diff = lowest - priceKrw
        return when {
            diff > 0L -> "비교 목록 최저 표기(" + nf(lowest) + "원)보다 " + nf(diff) + "원 낮게 올라온 글"
            diff == 0L -> "비교 목록 최저 표기와 같은 가격(" + nf(lowest) + "원)"
            else -> "비교 목록 최저 표기(" + nf(lowest) + "원)보다 " + nf(-diff) + "원 높은 가격"
        }
    }
}
