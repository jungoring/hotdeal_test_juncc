package com.jungoring.hotdeal.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * 목록의 작은 제품 썸네일(image.fmkorea.com)을 받아 와서 보여 준다.
 *
 * 2026-11-20 실제 확인:
 *  - 주소는 `https://image.fmkorea.com/filesn/cache/thumb/.../xxxx_70x50.crop.webp`
 *  - Referer 없이 요청하면 200 (다른 사이트 Referer 를 붙이면 302 로 막힘)
 *    → 그래서 Referer 를 붙이지 않고 요청한다.
 *  - 한 번 받은 이미지는 메모리와 cacheDir/thumbs 에 남겨 두어, 7일 누적 목록을
 *    다시 그릴 때(또는 잠깐 오프라인일 때)도 곧바로 뜬다.
 */
class ThumbLoader(context: Context) {

    private val appContext = context.applicationContext
    private val diskDir = File(appContext.cacheDir, "thumbs")
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)

    private val memory = object : LruCache<String, Bitmap>(memorySize()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** 카드에 썸네일을 붙인다. 주소가 없으면 이미지 자리를 숨긴다. */
    fun load(view: ImageView, url: String?) {
        view.tag = url
        if (url.isNullOrBlank()) {
            view.setImageDrawable(null)
            view.visibility = ImageView.GONE
            return
        }
        view.visibility = ImageView.VISIBLE

        val cached = memory.get(url)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        view.setImageDrawable(null) // 자리만 잡아 두고(배경색) 아래에서 채운다

        pool.execute {
            val bitmap = readFromDisk(url) ?: download(url)?.also { writeToDisk(url, it) }
            if (bitmap != null) memory.put(url, bitmap)
            main.post {
                if (view.tag == url && bitmap != null) view.setImageBitmap(bitmap)
            }
        }
    }

    // ── 저장/읽기 ─────────────────────────────────────────

    private fun readFromDisk(url: String): Bitmap? = try {
        val f = File(diskDir, keyOf(url))
        if (f.exists() && f.length() > 0L) decode(f.readBytes()) else null
    } catch (e: Exception) {
        null
    }

    private fun writeToDisk(url: String, bitmap: Bitmap) {
        try {
            if (!diskDir.exists()) diskDir.mkdirs()
            val f = File(diskDir, keyOf(url))
            f.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out) }
        } catch (e: Exception) {
            // 캐시 실패는 무시 (메모리 캐시만으로도 동작한다)
        }
    }

    private fun download(url: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "image/webp,image/*,*/*;q=0.8")
                // Referer 는 일부러 넣지 않는다 (넣으면 302 로 막히는 것을 확인함)
            }
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            decode(bytes)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** 64dp 카드 썸네일이라 원본(70x50)을 그대로 쓰지 않고 화면 밀도에 맞춰 줄여 디코드한다. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val target = targetPx()
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun targetPx(): Int {
        val density = appContext.resources.displayMetrics.density
        return (THUMB_DP * density).toInt().coerceAtLeast(64)
    }

    private fun keyOf(url: String): String {
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(url.toByteArray())
        val sb = StringBuilder(hash.size * 2)
        for (b in hash) sb.append(String.format("%02x", b))
        return sb.toString() + ".webp"
    }

    private fun memorySize(): Int {
        val max = Runtime.getRuntime().maxMemory() / 8
        return max.coerceAtMost(24L * 1024L * 1024L).toInt()
    }

    private companion object {
        const val THUMB_DP = 64
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"
    }
}
