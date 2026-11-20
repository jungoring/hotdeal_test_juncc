package com.jungoring.hotdeal.data

import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * WebView 한 개를 재사용해 '페이지 열기 → 다 그려질 때까지 대기' 와
 * '페이지 안에서 자바스크립트 실행' 을 코루틴으로 감싼다.
 * 모든 호출은 메인 스레드에서 이뤄져야 한다.
 */
class PageLoader(private val webView: WebView) {

    private var pageFinished: (() -> Unit)? = null

    /** WebViewClient.onPageFinished 에서 불러 준다. */
    fun notifyPageFinished() {
        val callback = pageFinished
        pageFinished = null
        callback?.invoke()
    }

    suspend fun load(url: String, timeoutMs: Long = 45_000L): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                pageFinished = { if (cont.isActive) cont.resume(true) }
                cont.invokeOnCancellation { pageFinished = null }
                webView.loadUrl(url)
            }
        } ?: false
    }

    suspend fun eval(script: String, timeoutMs: Long = 20_000L): String? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<String?> { cont ->
                webView.evaluateJavascript(script) { result ->
                    if (cont.isActive) cont.resume(result)
                }
            }
        }
    }
}
