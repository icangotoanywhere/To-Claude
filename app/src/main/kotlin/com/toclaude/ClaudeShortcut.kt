package com.toclaude

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * 桌面上的「Claude」快捷方式：图标取手机上已安装的 Claude App 自己的图标（运行时向系统要，不在本 App 里画或打包
 * Claude 的标志），点它先进本 App 的网络检查，确认后再打开 Claude。桌面会自动在角上叠一个 To Claude 的小标，
 * 和原版 Claude 图标区分开——**不要自己再加角标**（EasyClash v1.0.36 犯过：两个小标错开，看着像出错）。
 */
object ClaudeShortcut {
    private const val ID = "open-claude"

    /** 请桌面添加（已经有了就更新样子）。返回给用户看的提示 */
    fun pin(ctx: Context): String {
        if (ctx.packageManager.getLaunchIntentForPackage(MainActivity.CLAUDE_PKG) == null) {
            return "这台手机还没有安装 Claude App：请先安装，再来创建图标。"
        }
        val info = info(ctx) ?: return "读不到 Claude App 的图标，请稍后再试。"
        if (pinned(ctx)) {
            val ok = runCatching { ShortcutManagerCompat.updateShortcuts(ctx, listOf(info)) }.getOrDefault(false)
            return if (ok) "桌面上已经有这个 Claude 图标了，已更新它的样子。" else "桌面上已经有这个 Claude 图标了。"
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return "这台手机的桌面不支持由 App 添加快捷方式。"
        val ok = runCatching { ShortcutManagerCompat.requestPinShortcut(ctx, info, null) }.getOrDefault(false)
        return if (ok) "已请求添加。如果桌面上没出现，请到系统设置 → 应用 → To Claude → 权限，允许「创建桌面快捷方式」后再试。"
        else "添加失败：桌面拒绝了请求。请到系统设置 → 应用 → To Claude → 权限，允许「创建桌面快捷方式」后再试。"
    }

    /** 每次打开本 App 时调用：桌面上已有这个图标的话，刷新成 Claude 当前的图标（Claude 升级换了图标也能跟上） */
    fun refresh(ctx: Context) {
        runCatching { if (pinned(ctx)) info(ctx)?.let { ShortcutManagerCompat.updateShortcuts(ctx, listOf(it)) } }
    }

    private fun pinned(ctx: Context) = runCatching {
        ShortcutManagerCompat.getShortcuts(ctx, ShortcutManagerCompat.FLAG_MATCH_PINNED).any { it.id == ID }
    }.getOrDefault(false)

    private fun info(ctx: Context): ShortcutInfoCompat? {
        val icon = claudeIcon(ctx) ?: return null
        return ShortcutInfoCompat.Builder(ctx, ID)
            .setShortLabel("Claude")
            .setLongLabel("先看网络再打开 Claude")
            .setIcon(icon)
            .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
            .build()
    }

    /** Claude App 自己的图标。自适应图标按前景 + 背景两层交给系统，让桌面按自己的形状裁切，和原版一样大小 */
    private fun claudeIcon(ctx: Context): IconCompat? = runCatching {
        val d = ctx.packageManager.getApplicationIcon(MainActivity.CLAUDE_PKG)
        if (d is AdaptiveIconDrawable) {
            val size = 432   // 自适应图标是 108dp，四周各留 18dp 给桌面裁切
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.background?.let { it.setBounds(0, 0, size, size); it.draw(c) }
            d.foreground?.let { it.setBounds(0, 0, size, size); it.draw(c) }
            IconCompat.createWithAdaptiveBitmap(bmp)
        } else {
            val size = 192
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size); d.draw(Canvas(bmp))
            IconCompat.createWithBitmap(bmp)
        }
    }.getOrNull()
}
