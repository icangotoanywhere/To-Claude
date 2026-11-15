package com.toclaude

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * 「Claude 打开时先检查网络」：用户在系统设置里打开本无障碍服务后，Claude 打开或从后台切到前台时，
 * 立刻弹出检查卡片盖在 Claude 上面，点「打开」露出 Claude，点「取消」回到桌面。
 *
 * 只接收「窗口切换」事件，只看是哪个 App 到了前台（包名），不读取任何界面内容（canRetrieveWindowContent=false）。
 * 需要所有 App 的切换事件，才能分辨「从别的 App 切进 Claude」和「在 Claude 里面换页面」。
 * 安卓不允许拦住别的 App 启动：卡片弹出前那一瞬间 Claude 已经在前台、可能已经联网了。
 *
 * 顺带：服务运行时监听 Claude 安装 / 升级，让桌面上的「Claude」图标跟着换。
 */
class GateService : AccessibilityService() {

    private var lastPkg: String? = null

    private val claudeUpdated = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.data?.schemeSpecificPart == MainActivity.CLAUDE_PKG) ClaudeShortcut.refresh(this@GateService)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val f = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package") }
        runCatching { registerReceiver(claudeUpdated, f) }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        // 下拉通知栏、弹出输入法不算离开 Claude
        if (pkg == "com.android.systemui" || pkg == imePackage()) return
        val prev = lastPkg
        lastPkg = pkg
        if (pkg != MainActivity.CLAUDE_PKG) return
        // 在 Claude 里换页面；或者刚从检查卡片点「打开」过来的
        if (prev == MainActivity.CLAUDE_PKG || prev == packageName) return
        if (!GatePrefs.shouldCheck(this)) return
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_INTERCEPT, true),
            )
        }
    }

    private fun imePackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    override fun onInterrupt() {}

    override fun onDestroy() {
        runCatching { unregisterReceiver(claudeUpdated) }
        super.onDestroy()
    }
}
