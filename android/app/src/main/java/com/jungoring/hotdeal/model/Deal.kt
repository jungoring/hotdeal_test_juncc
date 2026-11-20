package com.jungoring.hotdeal.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/** '유사한 최근 6개월 핫딜들' 한 줄 */
data class Comparison(
    val name: String,
    val price: String?,
    val priceKrw: Long?,
    val diffText: String?,
    val isCheaper: Boolean?,
    val savingsKrw: Long?,
    val postedAt: String?
)

/** 글 상세 페이지에서만 얻을 수 있는 정보 */
data class DealDetail(
    val postedAt: Long?,
    val shopUrl: String?,
    val comparisonTotal: Int?,
    val endedReports: Int,
    val endedText: Boolean,
    val comparisons: List<Comparison>
)

/** 목록 한 줄(=딜 하나). 상세 정보는 applyDetail() 로 채운다. */
class Deal(
    var id: String?,
    var title: String,
    var mall: String?,
    var price: String?,
    var shipping: String?,
    var category: String?,
    var upvotes: Int?,
    var comments: Int?,
    var author: String?,
    var regdate: String?,
    var url: String?,
    var endedBadge: Boolean
) {
    var postedAt: Long? = null
    var endedReports: Int = 0
    var shopUrl: String? = null
    var comparisonTotal: Int? = null
    var comparisons: List<Comparison> = emptyList()
    var detailLoaded: Boolean = false

    val key: String
        get() = id ?: url ?: title

    val priceKrw: Long?
        get() = parseKrw(price)

    val hasComparisons: Boolean
        get() = comparisons.isNotEmpty()

    val isEnded: Boolean
        get() = endedReports > 0 || endedBadge

    /** 사이트가 '더 저렴/더 비쌈' 문구를 준 비교가 하나라도 있으면 true/false, 없으면 null */
    val isCheaper: Boolean?
        get() {
            val seen = comparisons.filter { it.isCheaper != null }
            if (seen.isEmpty()) return null
            return seen.any { it.isCheaper == true }
        }

    /** 이전보다 얼마나 저렴한지(최대값) */
    val bestSavings: Long?
        get() = comparisons
            .mapNotNull { c -> if (c.isCheaper == true) c.savingsKrw else null }
            .filter { it > 0 }
            .maxOrNull()

    /** 이전 핫딜이 더 저렴했던 폭(음수 savings 중 가장 큰 손해) */
    val worstOverpay: Long?
        get() = comparisons
            .mapNotNull { c -> if (c.isCheaper == false) c.savingsKrw else null }
            .minOrNull()

    fun applyDetail(detail: DealDetail) {
        if (detail.postedAt != null) postedAt = detail.postedAt
        if (!detail.shopUrl.isNullOrBlank()) shopUrl = detail.shopUrl
        if (detail.comparisonTotal != null) comparisonTotal = detail.comparisonTotal
        if (detail.endedReports > 0) endedReports = detail.endedReports
        if (detail.endedText) endedBadge = true
        comparisons = detail.comparisons
        detailLoaded = true
    }

    companion object {
        private const val BASE = "https://www.fmkorea.com"
        private val KST: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

        fun parseList(json: String): List<Deal> {
            val out = ArrayList<Deal>()
            val arr = try {
                JSONArray(json)
            } catch (e: Exception) {
                return out
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.str("title") ?: continue
                if (title.isEmpty()) continue
                val id = o.str("id")
                val deal = Deal(
                    id = id,
                    title = title,
                    mall = o.str("mall"),
                    price = o.str("price"),
                    shipping = o.str("shipping"),
                    category = o.str("category"),
                    upvotes = o.intOrNull("upvotes"),
                    comments = o.intOrNull("comments"),
                    author = o.str("author"),
                    regdate = o.str("regdate"),
                    url = if (id != null) BASE + "/" + id else null,
                    endedBadge = o.optBoolean("ended_badge", false)
                )
                deal.postedAt = resolvePostedAt(deal.regdate)
                out.add(deal)
            }
            return out
        }

        fun parseDetail(json: String): DealDetail {
            val o = try {
                JSONObject(json)
            } catch (e: Exception) {
                JSONObject()
            }
            val comparisons = ArrayList<Comparison>()
            val arr = o.optJSONArray("comparisons")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    val name = c.str("name") ?: continue
                    if (name.isEmpty()) continue
                    comparisons.add(
                        Comparison(
                            name = name,
                            price = c.str("price"),
                            priceKrw = c.longOrNull("price_krw"),
                            diffText = c.str("diff_text"),
                            isCheaper = c.boolOrNull("is_cheaper"),
                            savingsKrw = c.longOrNull("savings_krw"),
                            postedAt = c.str("posted_at")
                        )
                    )
                }
            }
            return DealDetail(
                postedAt = resolveDetailTime(o.str("posted_at")),
                shopUrl = o.str("shop_url"),
                comparisonTotal = o.intOrNull("comparison_total"),
                endedReports = o.intOrNull("ended_reports") ?: 0,
                endedText = o.optBoolean("ended_text", false),
                comparisons = comparisons
            )
        }

        /** '14:52' / '11-18 23:33' / '2026.11.18' 표기를 밀리초로 */
        fun resolvePostedAt(reg: String?): Long? {
            if (reg.isNullOrBlank()) return null
            val r = reg.trim()
            val now = System.currentTimeMillis()

            var m = Regex("^(\\d{2}):(\\d{2})$").find(r)
            if (m != null) {
                val cal = Calendar.getInstance(KST)
                cal.timeInMillis = now
                cal.set(Calendar.HOUR_OF_DAY, m.groupValues[1].toInt())
                cal.set(Calendar.MINUTE, m.groupValues[2].toInt())
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis > now + 60_000L) cal.add(Calendar.DAY_OF_MONTH, -1)
                return cal.timeInMillis
            }

            m = Regex("^(\\d{2})[-.](\\d{2})\\s+(\\d{2}):(\\d{2})$").find(r)
            if (m != null) {
                val cal = Calendar.getInstance(KST)
                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.MONTH, m.groupValues[1].toInt() - 1)
                cal.set(Calendar.DAY_OF_MONTH, m.groupValues[2].toInt())
                cal.set(Calendar.HOUR_OF_DAY, m.groupValues[3].toInt())
                cal.set(Calendar.MINUTE, m.groupValues[4].toInt())
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis > now + 60_000L) cal.add(Calendar.YEAR, -1)
                return cal.timeInMillis
            }

            m = Regex("^(20\\d{2})[-.](\\d{2})[-.](\\d{2})").find(r)
            if (m != null) {
                val cal = Calendar.getInstance(KST)
                cal.clear()
                cal.set(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt() - 1,
                    m.groupValues[3].toInt(),
                    0,
                    0,
                    0
                )
                return cal.timeInMillis
            }
            return null
        }

        /** 본문 상단의 '2026.11.20 15:59' */
        fun resolveDetailTime(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val m = Regex("(20\\d{2})\\.(\\d{2})\\.(\\d{2})\\s+(\\d{2}):(\\d{2})").find(raw)
                ?: return null
            val cal = Calendar.getInstance(KST)
            cal.clear()
            cal.set(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt() - 1,
                m.groupValues[3].toInt(),
                m.groupValues[4].toInt(),
                m.groupValues[5].toInt(),
                0
            )
            return cal.timeInMillis
        }

        fun parseKrw(text: String?): Long? {
            if (text.isNullOrBlank()) return null
            val m = Regex("([\\d,]+)\\s*원").find(text) ?: return null
            return m.groupValues[1].replace(",", "").toLongOrNull()
        }
    }
}
