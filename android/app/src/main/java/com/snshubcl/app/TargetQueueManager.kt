package com.snshubcl.app

import android.content.Context

/**
 * SNS별 추천 프로필/친구 찾기 URL 및 현재 진행 상태 관리.
 */
object TargetQueueManager {
    private const val PREFS_NAME = "target_queue_prefs"

    // SNS별 탐색 및 프로필 루트 URL
    val EXPLORE_URLS = mapOf(
        "facebook" to "https://m.facebook.com/friends/center/suggestions",
        "instagram" to "https://www.instagram.com/explore/people/",
        "linkedin" to "https://www.linkedin.com/mynetwork/",
        "x" to "https://x.com/i/connect_people",
        "threads" to "https://www.threads.net/"
    )

    fun getIndex(context: Context, snsId: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("${snsId}_index", 0)
    }

    fun incrementIndex(context: Context, snsId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getIndex(context, snsId)
        prefs.edit().putInt("${snsId}_index", current + 1).apply()
    }

    fun getExploreUrl(snsId: String): String = EXPLORE_URLS[snsId] ?: "https://google.com"
}
