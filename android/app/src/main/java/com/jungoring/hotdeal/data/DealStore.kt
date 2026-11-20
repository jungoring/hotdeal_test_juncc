package com.jungoring.hotdeal.data

import android.content.Context
import com.jungoring.hotdeal.model.Deal
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 7일치 딜을 기기 안(filesDir/deals_cache.json)에 쌓아 둔다.
 *
 * - 앱을 다시 켜면 저장해 둔 목록을 곧바로 그린다.
 * - 새로 읽은 글은 같은 id 의 기존 항목에 추천수·댓글수·종료 여부·썸네일만 최신값으로 덮어쓴다.
 * - 7일(168시간)이 지난 글은 저장·병합 때마다 버린다.
 */
class DealStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    class Snapshot(
        val deals: List<Deal>,
        /** 가장 깊게 읽어 둔 목록 페이지 번호 (이전 글 더 불러오기의 시작점) */
        val deepPages: Int,
        /** 마지막으로 저장한 시각 */
        val savedAt: Long?
    )

    fun load(): Snapshot {
        if (!file.exists()) return Snapshot(emptyList(), 0, null)
        return try {
            val text = file.readText()
            val root = JSONObject(text)
            val arr = root.optJSONArray("items") ?: JSONArray()
            val out = ArrayList<Deal>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                Deal.fromJson(o)?.let { out.add(it) }
            }
            val now = System.currentTimeMillis()
            val kept = DealMerge.merge(out, emptyList(), now)
            Snapshot(
                deals = kept,
                deepPages = root.optInt("deep_pages", 0).coerceAtLeast(0),
                savedAt = if (root.has("saved_at") && !root.isNull("saved_at")) {
                    root.optLong("saved_at", 0L).takeIf { it > 0L }
                } else {
                    null
                }
            )
        } catch (e: Exception) {
            // 파일이 깨졌으면 빈 목록으로 시작한다(다음 새로고침에서 다시 쌓인다)
            Snapshot(emptyList(), 0, null)
        }
    }

    fun save(deals: List<Deal>, deepPages: Int, savedAt: Long) {
        try {
            val arr = JSONArray()
            for (d in deals) arr.put(d.toJson())
            val root = JSONObject().apply {
                put("version", VERSION)
                put("saved_at", savedAt)
                put("deep_pages", deepPages)
                put("items", arr)
            }
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // 저장 실패는 앱을 멈추지 않는다 (메모리 목록은 그대로 쓴다)
        }
    }

    private companion object {
        const val FILE_NAME = "deals_cache.json"
        const val VERSION = 1
    }
}

/** 저장된 목록과 새로 읽은 목록을 합치고, 7일이 지난 글을 정리한다. */
object DealMerge {

    const val RETENTION_MS: Long = 7L * 24L * 60L * 60L * 1000L

    fun merge(
        existing: Collection<Deal>,
        fresh: List<Deal>,
        now: Long,
        retentionMs: Long = RETENTION_MS
    ): ArrayList<Deal> {
        val byKey = LinkedHashMap<String, Deal>()
        for (d in existing) byKey[d.key] = d

        for (f in fresh) {
            val old = byKey[f.key]
            if (old == null) {
                f.firstSeen = now
                byKey[f.key] = f
                continue
            }
            // 같은 글: 바뀌는 값만 최신으로 (썸네일·추천·댓글·종료 여부)
            if (f.upvotes != null) old.upvotes = f.upvotes
            if (f.comments != null) old.comments = f.comments
            old.endedBadge = f.endedBadge
            if (!f.thumbnail.isNullOrBlank()) old.thumbnail = f.thumbnail
            if (!f.title.isBlank()) old.title = f.title
            if (!f.mall.isNullOrBlank()) old.mall = f.mall
            if (!f.price.isNullOrBlank()) old.price = f.price
            if (!f.shipping.isNullOrBlank()) old.shipping = f.shipping
            if (!f.category.isNullOrBlank()) old.category = f.category
            if (!f.regdate.isNullOrBlank()) old.regdate = f.regdate
            if (!f.author.isNullOrBlank()) old.author = f.author
            if (f.postedAt != null) old.postedAt = f.postedAt
            if (old.url.isNullOrBlank()) old.url = f.url
            // shop_url / shop_missing 은 한 번 확인한 값을 그대로 둔다
        }

        val cutoff = now - retentionMs
        val kept = byKey.values.filter { it.ageBase >= cutoff }
        return ArrayList(
            kept.sortedWith(
                compareByDescending<Deal> { it.postedAt ?: 0L }
                    .thenByDescending { it.sortId }
            )
        )
    }
}
