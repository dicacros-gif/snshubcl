package com.snshubcl.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 네이티브 앱 제어 실구동 백엔드.
 */
class SNSAutomationService : AccessibilityService() {

    private val prefs by lazy { getSharedPreferences("snshub_prefs", Context.MODE_PRIVATE) }
    private var lastActionTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 자동화 모드 여부 실시간 체크
        if (!prefs.getBoolean("is_auto_mode", true)) return

        val packageName = event.packageName?.toString() ?: return
        val activeSnsId = prefs.getString("active_sns_id", null) ?: return
        val net = Sns.byId(activeSnsId)

        if (packageName != net.pkg) return

        val now = System.currentTimeMillis()
        if (now - lastActionTime < getInterval()) return

        val rootNode = rootInActiveWindow ?: return
        
        if (processAutomation(rootNode, net)) {
            lastActionTime = now
            AutomationStatsManager.incrementCount(this, net.id, "action")
        }
    }

    private fun processAutomation(root: AccessibilityNodeInfo, net: Sns.Net): Boolean {
        // 1. 친구 추가
        if (prefs.getBoolean("auto_friend", true)) {
            val targets = listOf("친구 추가", "Add Friend", "팔로우", "Follow", "Connect")
            if (findAndTap(root, targets)) return true
        }

        // 2. 좋아요
        if (prefs.getBoolean("auto_like", true)) {
            val targets = listOf("좋아요", "Like")
            if (findAndTap(root, targets)) return true
        }

        return false
    }

    private fun findAndTap(node: AccessibilityNodeInfo, targets: List<String>): Boolean {
        for (text in targets) {
            val nodes = node.findAccessibilityNodeInfosByText(text)
            for (target in nodes) {
                if (target.isVisibleToUser && target.isClickable) {
                    return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                // 부모 노드 클릭 시도
                var p = target.parent
                while (p != null) {
                    if (p.isClickable && p.isVisibleToUser) {
                        return p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    p = p.parent
                }
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndTap(child, targets)) return true
        }
        return false
    }

    private fun getInterval(): Long {
        return if (prefs.getBoolean("interval_random", true)) {
            (8000..16000).random().toLong()
        } else {
            prefs.getInt("fixed_interval", 10) * 1000L
        }
    }

    override fun onInterrupt() {}
}
