package com.jungoring.hotdeal.model

import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/** 글 상세 페이지에서 확인한 쇼핑몰 링크 */
data class ShopLookup(
    val shopUrl: String?,
    /** 핫딜 정보 표를 확인했는데 본문에 외부 쇼핑몰 링크가 없었으면 true */
    val inspected: Boolean
)

/** 목록 한 줄(=딜 하나). 쇼핑몰 링크는 카드를 눌렀을 때 그 글만 확인해 채운다. */
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

    /** 실제 쇼핑몰 주소 (상세 페이지의 '링크' 행에서 확인한 값) */
    var shopUrl: String? = null

    /** 지금 이 글의 쇼핑몰 링크를 확인하는 중 */
    var shopLoading: Boolean = false

    /** 확인해 봤지만 본문에 외부 쇼핑몰 링크가 없었음 (다음부터는 펨코 원문으로) */
    var shopMissing: Boolean = false

    val key: String
        get() = id ?: url ?: title

    val isEnded: Boolean
        get() = endedBadge

    companion object {
        private const val BASE = "https://www.fmkorea.com"
        private val KST: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

        fun parseList(json: String): List<Deal> {
            val out = ArrayList<Deal>()
            val arr = try {
                org.json.JSONArray(json)
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

        fun parseShopLookup(json: String): ShopLookup {
            val o = try {
                JSONObject(json)
            } catch (e: Exception) {
                JSONObject()
            }
            return ShopLookup(
                shopUrl = o.str("shop_url"),
                inspected = o.optBoolean("inspected", false)
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
    }
}
