package com.jungoring.hotdeal.model

import org.json.JSONObject

/**
 * org.json 의 optString 은 값이 JSON null 일 때 "null" 이라는 문자열을 돌려주기도 해서,
 * 앱에서는 아래 헬퍼로 '없음(null)' 을 분명하게 다룬다.
 */
internal fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val raw = opt(key) ?: return null
    val s = if (raw is String) raw else raw.toString()
    val t = s.trim()
    return if (t.isEmpty() || t == "null" || t == "undefined") null else t
}

internal fun JSONObject.intOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull()
        else -> null
    }
}

internal fun JSONObject.longOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.trim().replace(",", "").toLongOrNull()
        else -> null
    }
}

internal fun JSONObject.boolOrNull(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Boolean -> v
        is String -> when (v.trim()) {
            "true" -> true
            "false" -> false
            else -> null
        }
        else -> null
    }
}
