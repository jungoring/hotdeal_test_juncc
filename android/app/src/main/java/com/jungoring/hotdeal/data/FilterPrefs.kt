package com.jungoring.hotdeal.data

import android.content.Context

/** 필터 설정을 기기에 저장한다(웹 버전의 localStorage 와 같은 역할). */
class FilterPrefs(context: Context) {

    private val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var q: String
        get() = sp.getString("q", "") ?: ""
        set(value) { sp.edit().putString("q", value).apply() }

    var exclude: String
        get() = sp.getString("ex", "") ?: ""
        set(value) { sp.edit().putString("ex", value).apply() }

    var category: String
        get() = sp.getString("cat", "전체") ?: "전체"
        set(value) { sp.edit().putString("cat", value).apply() }

    var minVotes: Int
        get() = sp.getInt("minVotes", 0)
        set(value) { sp.edit().putInt("minVotes", value).apply() }

    var hideEnded: Boolean
        get() = sp.getBoolean("hideEnded", true)
        set(value) { sp.edit().putBoolean("hideEnded", value).apply() }

    /** 새로고침 때 1회 수집 범위: 0=새 글만(자동), 3/5/15=그 페이지 수만큼 */
    var scopePages: Int
        get() {
            val value = sp.getInt("scope", 0)
            return if (value == 0 || value == 3 || value == 5 || value == 15) value else 0
        }
        set(value) { sp.edit().putInt("scope", value).apply() }

    private companion object {
        const val NAME = "fmk_filters"
    }
}
