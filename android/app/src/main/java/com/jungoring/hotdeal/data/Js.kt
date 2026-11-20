package com.jungoring.hotdeal.data

import org.json.JSONTokener

object Js {

    /**
     * WebView.evaluateJavascript 의 결과는 자바스크립트 값을 JSON 으로 감싼 '문자열 리터럴' 이다.
     * (예: "\"[{\\\"id\\\":1}]\"") 그래서 한 번 풀어서 실제 문자열을 꺼낸다.
     */
    fun decode(raw: String?): String? {
        if (raw == null) return null
        val text = raw.trim()
        if (text.isEmpty() || text == "null" || text == "undefined") return null
        return try {
            val value = JSONTokener(text).nextValue()
            if (value is String) value else text
        } catch (e: Exception) {
            text
        }
    }
}
