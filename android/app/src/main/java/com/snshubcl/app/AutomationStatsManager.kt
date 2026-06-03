package com.snshubcl.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object AutomationStatsManager {
    private const val PREFS_NAME = "snshub_stats"
    
    fun getTodayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    fun incrementCount(context: Context, snsId: String, actionType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${getTodayKey()}_${snsId}_$actionType"
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    fun getTodayCount(context: Context, snsId: String, actionType: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${getTodayKey()}_${snsId}_$actionType"
        return prefs.getInt(key, 0)
    }

    fun isUnderLimit(context: Context, snsId: String): Boolean {
        val limit = Sns.byId(snsId).dailyLimit
        val safeLimit = limit - 10
        val totalToday = getTodayCount(context, snsId, "action")
        return totalToday < safeLimit
    }
}
