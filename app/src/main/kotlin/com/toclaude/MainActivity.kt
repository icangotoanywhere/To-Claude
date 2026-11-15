package com.toclaude

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * To Claude：点开先看一眼网络（有没有走代理、出口在哪、离哪个机房最近、Claude 的服务器连不连得上），
 * 确认后点「打开」拉起手机上的 Claude App。不依赖任何代理软件，也不收集任何信息。
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val CLAUDE_PKG = "com.anthropic.claude"
        private const val PREFS = "toclaude"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val firstRun = !prefs.getBoolean("tipsShown", false)
        ClaudeShortcut.refresh(this)   // 桌面上的 Claude 图标跟上 Claude 当前的图标
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = if (dark) darkColorScheme(primary = Color(0xFF6FD6BC)) else lightColorScheme(primary = Color(0xFF0E7C66))
            MaterialTheme(colorScheme = scheme) {
                var showTips by remember { mutableStateOf(firstRun) }
                Gate(onCancel = { finish() }, onOpen = { openClaude() }, onTips = { showTips = true })
                if (showTips) Tips(
                    onClose = { showTips = false; prefs.edit().putBoolean("tipsShown", true).apply() },
                    onClaudeInfo = { openClaudeInfo() },
                    onPin = { Toast.makeText(this, ClaudeShortcut.pin(this), Toast.LENGTH_LONG).show() },
                )
            }
        }
    }

    private fun openClaude() {
        val i = packageManager.getLaunchIntentForPackage(CLAUDE_PKG)
        if (i != null) {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            val ok = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$CLAUDE_PKG")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (!ok) Toast.makeText(this, "这台手机上没有安装 Claude App", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    /** Claude 的系统应用信息页（各家手机的「隐藏」「桌面图标」等设置多半能从这里进） */
    private fun openClaudeInfo() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$CLAUDE_PKG")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(this, "打不开 Claude 的应用信息（可能没安装）", Toast.LENGTH_SHORT).show() }
    }
}

private val GOOD = Color(0xFF2E7D32)
private val WARN = Color(0xFFE65100)

private class Result(
    val route: NetCheck.Route,
    val exit: NetCheck.Exit?,
    val exitErr: String?,
    val nearest: NetCheck.Nearest?,
    val claudeMs: Long?,
    val claudeErr: String?,
)

private suspend fun runChecks(ctx: Context): Result = coroutineScope {
    val route = NetCheck.route(ctx)
    val exit = async { runCatching { NetCheck.exit() } }
    val near = async { runCatching { NetCheck.nearest() }.getOrNull() }
    val claude = async { runCatching { NetCheck.claudeReach() } }
    val e = exit.await()
    val c = claude.await()
    Result(route, e.getOrNull(), e.exceptionOrNull()?.let { it.message ?: "网络错误" }, near.await(), c.getOrNull(), c.exceptionOrNull()?.let { it.message ?: "网络错误" })
}

@Composable
private fun Gate(onCancel: () -> Unit, onOpen: () -> Unit, onTips: () -> Unit) {
    val ctx = LocalContext.current
    var result by remember { mutableStateOf<Result?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val installed = remember { ctx.packageManager.getLaunchIntentForPackage(MainActivity.CLAUDE_PKG) != null }

    LaunchedEffect(refresh) {
        result = null
        result = runChecks(ctx)
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.padding(20.dp).widthIn(max = 440.dp).fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("打开 Claude 前看一眼网络", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    TextButton(onClick = onTips) { Text("使用提示") }
                }
                Spacer(Modifier.height(10.dp))

                val r = result
                if (r == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 14.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  正在检查网络…（实测延迟要几秒）", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Content(r, installed)
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { refresh++ }, enabled = result != null) { Text("刷新") }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onCancel) { Text("取消") }
                        Button(onClick = onOpen) { Text("打开") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Content(r: Result, installed: Boolean) {
    val warnings = mutableListOf<String>()
    val notes = mutableListOf<String>()

    val routeText = when {
        r.route.vpn -> "已开启 VPN（代理软件）"
        r.route.proxy != null -> "系统代理 ${r.route.proxy}"
        else -> "没有开启代理"
    }
    if (!r.route.vpn && r.route.proxy == null) {
        warnings += "没有开启 VPN 或代理：Claude 会直接用你本来的网络，可能打不开，或者被判定为不支持的地区。"
    }
    val ex = r.exit
    if (ex != null && ex.countryCode in NetCheck.UNSUPPORTED) {
        val near = r.nearest
        if (near != null && near.dc.countryCode != ex.countryCode && near.dc.countryCode !in NetCheck.UNSUPPORTED) {
            warnings += "IP 库说出口在「${ex.place}」，这是 Claude 不提供服务的地区；但实测离${near.dc.name}机房最近，节点实际多半在${near.dc.name}附近。" +
                "Claude 可能按 IP 库判断地区，如果打不开，换一个节点试试。"
        } else {
            warnings += "出口在「${ex.place}」，这个地区不在 Claude 的服务范围内，可能无法使用。请在代理软件里换一个出口地区（比如日本、美国、新加坡）。"
        }
    } else if (ex != null && r.nearest != null) {
        val near = r.nearest
        // IP 库说的国家正好也是我们测了的机房所在国，但实测最近的是别的国家：提示两者不一致
        val measuredCountries = setOf("JP", "KR", "HK", "SG", "US", "DE", "GB")
        if (ex.countryCode in measuredCountries && near.dc.countryCode != ex.countryCode) {
            notes += "IP 库说出口在「${ex.place}」，实测却离${near.dc.name}机房最近，两者不一致。IP 库常按 IP 的登记信息填写，实测更接近节点的真实位置。"
        }
    }
    if (r.claudeMs == null) warnings += "连不上 Claude 的服务器，Claude 可能打不开。检查代理是否正常，或者换个节点再试。"

    val good = warnings.isEmpty()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(if (good) GOOD else WARN, CircleShape))
        Text(if (good) "  可以放心打开" else "  有需要注意的地方", fontWeight = FontWeight.SemiBold, color = if (good) GOOD else WARN)
    }
    Spacer(Modifier.height(8.dp))
    Line("网络", routeText, if (r.route.vpn || r.route.proxy != null) GOOD else WARN)
    if (ex != null) {
        Line("出口 IP", ex.ip)
        Line("IP 库所在地", ex.place)
        if (ex.isp.isNotBlank()) Line("运营商", ex.isp)
    } else {
        Line("出口 IP", "查不到（${r.exitErr}）", WARN)
    }
    r.nearest?.let { n ->
        val second = n.second?.let { "，其次${it.first.name} ${it.second} ms" } ?: ""
        Line("实测最近", "${n.dc.name}机房 ${n.ms} ms$second")
    } ?: Line("实测最近", "测不出来")
    Line("Claude 服务器", r.claudeMs?.let { "可以连接（$it ms）" } ?: "连不上", if (r.claudeMs != null) GOOD else WARN)

    warnings.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
    notes.forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
    if (!installed) Text("• 这台手机还没有安装 Claude App，点「打开」会跳到应用商店。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Line(key: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Tips(onClose: () -> Unit, onClaudeInfo: () -> Unit, onPin: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("使用提示") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Tip(
                    "在桌面放一个「Claude」图标",
                    "点下面的按钮，桌面上会多一个「Claude」图标：样子就是手机上 Claude App 自己的图标（角上会有 To Claude 的小标，" +
                        "和原版区分开），点它先看网络，确认后再打开 Claude。以后就用它代替原版图标，To Claude 自己的图标也可以收起来。",
                )
                Button(onClick = onPin, modifier = Modifier.padding(top = 6.dp)) { Text("在桌面创建 Claude 图标") }
                Tip(
                    "把原来的图标从桌面拿掉",
                    "原版 Claude 图标（和 To Claude 自己的图标）可以从桌面拿掉，免得误点：\n\n" +
                        "• 推荐：把桌面改成「抽屉」布局（桌面空白处双指捏合 → 桌面设置 → 桌面布局），然后长按 Claude 图标选「移除」（不是「卸载」）。App 收进应用抽屉，通知照常。\n" +
                        "• 或者：把 Claude 图标拖进一个不常翻的文件夹。\n" +
                        "• 不建议用系统的「隐藏应用」：荣耀、华为等手机会把被隐藏 App 的通知一起藏掉，Claude 回复完你就收不到提醒了。\n" +
                        "• 不要把 Claude 放进「隐私空间」或「应用分身」：那里相当于另一个用户，主空间的代理管不到它，本 App 也打不开它。",
                )
                TextButton(onClick = onClaudeInfo) { Text("打开 Claude 的应用信息") }
                Tip(
                    "让检查结果准确",
                    "本 App 查到的是它自己的网络出口。代理软件如果用了「分应用代理」，请让 Claude 和 To Claude 都走代理（或者都选「全部应用」），两者的出口才一致。",
                )
                Tip(
                    "「IP 库所在地」和「实测最近」",
                    "IP 库所在地来自公开的查询服务（ipwho.is，查不到时用 ip-api.com），常按 IP 的登记信息填写，不一定是节点的真实位置。\n\n" +
                        "实测最近：经当前网络测到亚马逊云东京、首尔、香港、新加坡、美国、法兰克福、伦敦机房的往返时间，离哪个最近，节点多半就在那附近。两者不一致时以实测为准。",
                )
                Tip(
                    "隐私",
                    "本 App 不收集、不上传任何信息。检查时只访问上面提到的查询服务、亚马逊云的测速地址和 Anthropic 的服务器（只看连不连得上）。",
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("知道了") } },
    )
}

@Composable
private fun Tip(title: String, body: String) {
    Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    Text(body, style = MaterialTheme.typography.bodySmall)
}
