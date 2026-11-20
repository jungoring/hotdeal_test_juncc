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

    var count: Int
        get() {
            val value = sp.getInt("count", 20)
            return if (value == 20 || value == 40 || value == 60) value else 20
        }
        set(value) { sp.edit().putInt("count", value).apply() }

    private companion object {
        const val NAME = "fmk_filters"
    }
}
