package com.toclaude

import android.content.Context
import android.net.ConnectivityManager
import android.provider.Settings

/** 「Claude 打开时先检查网络」的设置和状态（存在本 App 的 SharedPreferences 里） */
object GatePrefs {
    private const val PREFS = "toclaude"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 多久内不重复检查（分钟）：0 = 每次切到 Claude 都检查；默认 10 */
    fun intervalMin(ctx: Context) = p(ctx).getInt("interval", 10)
    fun setInterval(ctx: Context, m: Int) = p(ctx).edit().putInt("interval", m).apply()

    /** 网络变了（开关代理、换 Wi-Fi / 流量、代理重连）就重新检查，哪怕还在上面的时间内；默认开 */
    fun netRecheck(ctx: Context) = p(ctx).getBoolean("netRecheck", true)
    fun setNetRecheck(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("netRecheck", v).apply()

    /** 当前网络的「指纹」：活动网络的编号 + 有没有 VPN。代理重连、换网络都会变 */
    fun netSig(ctx: Context): String {
        val r = NetCheck.route(ctx)
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return "${cm?.activeNetwork}|${r.vpn}|${r.proxy}"
    }

    /** 用户在检查卡片上点了「打开」 */
    fun markConfirmed(ctx: Context) {
        p(ctx).edit().putLong("lastConfirm", System.currentTimeMillis()).putString("netSig", netSig(ctx)).apply()
    }

    /** 这次切到 Claude 要不要弹检查卡片 */
    fun shouldCheck(ctx: Context): Boolean {
        val last = p(ctx).getLong("lastConfirm", 0)
        if (last == 0L) return true
        if (netRecheck(ctx) && p(ctx).getString("netSig", null) != netSig(ctx)) return true
        val m = intervalMin(ctx)
        // 「每次都检查」也留 15 秒：Claude 刚被「打开」拉起时会连着触发几次窗口切换
        val window = if (m <= 0) 15_000L else m * 60_000L
        return System.currentTimeMillis() - last > window
    }

    /** 无障碍服务有没有在系统设置里打开 */
    fun serviceEnabled(ctx: Context): Boolean {
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { it.startsWith(ctx.packageName + "/") }
    }
}
