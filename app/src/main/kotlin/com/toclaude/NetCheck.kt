package com.toclaude

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 打开 Claude 前的几项网络检查。本 App 自己的流量和其他 App 一样走手机当前的网络（开着 VPN 就经过 VPN），
 * 所以查到的就是 Claude 将要用的出口——前提是代理软件的「分应用」设置对 Claude 和本 App 一样（见使用提示）。
 */
object NetCheck {
    /** Claude 不提供服务的地区（出口在这些地方时提醒） */
    val UNSUPPORTED = setOf("CN", "HK", "MO", "RU", "BY", "IR", "KP", "SY", "CU")

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS)
        .build()

    // ---------- 1. 有没有走代理 ----------
    data class Route(val vpn: Boolean, val proxy: String?)

    fun route(ctx: Context): Route {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val lp = cm?.activeNetwork?.let { cm.getLinkProperties(it) }
        val proxy = lp?.httpProxy?.takeIf { !it.host.isNullOrBlank() }?.let { "${it.host}:${it.port}" }
        return Route(vpn, proxy)
    }

    // ---------- 2. 出口 IP（查询服务的 IP 库） ----------
    data class Exit(val ip: String, val place: String, val countryCode: String, val isp: String, val source: String)

    private fun getJson(url: String): JSONObject =
        http.newCall(Request.Builder().url(url).header("User-Agent", "ToClaude").build()).execute().use {
            if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
            JSONObject(it.body?.string() ?: "{}")
        }

    private fun join(vararg p: String) = p.filter { it.isNotBlank() }.distinct().joinToString(" · ")

    /**
     * 先 ipwho.is，查不到再 ip-api.com。2026-11 实测：一个实际在日本的节点，ip-api.com 按 IP 的登记信息报成香港，
     * ipwho.is 报的是东京，和延迟实测一致，所以 ipwho.is 放在前面。各家 IP 库本来就会有出入，所以下面还有延迟实测。
     */
    suspend fun exit(): Exit = withContext(Dispatchers.IO) {
        runCatching {
            val o = getJson("https://ipwho.is/?lang=zh-CN")
            if (!o.optBoolean("success")) throw IllegalStateException(o.optString("message"))
            val conn = o.optJSONObject("connection")
            Exit(
                o.optString("ip"), join(o.optString("country"), o.optString("region"), o.optString("city")),
                o.optString("country_code").uppercase(),
                join(conn?.optString("isp").orEmpty(), conn?.optInt("asn")?.takeIf { it > 0 }?.let { "AS$it" }.orEmpty()),
                "ipwho.is",
            )
        }.getOrElse {
            val o = getJson("http://ip-api.com/json/?lang=zh-CN&fields=status,message,country,countryCode,regionName,city,isp,org,as,query")
            if (o.optString("status") != "success") throw IllegalStateException(o.optString("message"))
            Exit(
                o.optString("query"), join(o.optString("country"), o.optString("regionName"), o.optString("city")),
                o.optString("countryCode").uppercase(),
                join(o.optString("isp").ifBlank { o.optString("org") }, o.optString("as").substringBefore(' ')),
                "ip-api.com",
            )
        }
    }

    // ---------- 3. 延迟实测：出口离哪个机房最近 ----------
    data class Dc(val name: String, val countryCode: String, val region: String)

    /** 亚马逊云各地机房（用 DynamoDB 的 ping 地址，专门给测延迟用，返回很小） */
    private val DCS = listOf(
        Dc("东京", "JP", "ap-northeast-1"),
        Dc("首尔", "KR", "ap-northeast-2"),
        Dc("香港", "HK", "ap-east-1"),
        Dc("新加坡", "SG", "ap-southeast-1"),
        Dc("美国西部", "US", "us-west-1"),
        Dc("美国东部", "US", "us-east-1"),
        Dc("法兰克福", "DE", "eu-central-1"),
        Dc("伦敦", "GB", "eu-west-2"),
    )

    data class Nearest(val dc: Dc, val ms: Long, val second: Pair<Dc, Long>?)

    /**
     * 经当前网络（开着 VPN 就经过代理节点）测到各地机房的往返时间。第一次请求要建连接，不算；
     * 再测两次取最小值，只剩「节点 ↔ 机房」的距离差。离哪个机房最近，出口大概率就在那附近——
     * 这是按真实线路测的，不依赖 IP 库的登记信息。
     */
    suspend fun nearest(): Nearest? = coroutineScope {
        val res = DCS.map { dc ->
            async(Dispatchers.IO) {
                val url = "https://dynamodb.${dc.region}.amazonaws.com/ping"
                runCatching {
                    fun once(): Long {
                        val t0 = System.nanoTime()
                        http.newCall(Request.Builder().url(url).header("User-Agent", "ToClaude").build()).execute().use { it.body?.bytes() }
                        return (System.nanoTime() - t0) / 1_000_000
                    }
                    once()   // 建连接（DNS、TCP、TLS），不计
                    minOf(once(), once())
                }.getOrNull()?.let { dc to it }
            }
        }.awaitAll().filterNotNull().sortedBy { it.second }
        val first = res.firstOrNull() ?: return@coroutineScope null
        Nearest(first.first, first.second, res.getOrNull(1))
    }

    // ---------- 4. Claude 的服务器连不连得上 ----------
    /** 返回耗时（毫秒），连不上抛异常。服务器回什么状态码都算连上了 */
    suspend fun claudeReach(): Long = withContext(Dispatchers.IO) {
        val t0 = System.nanoTime()
        http.newCall(Request.Builder().url("https://api.anthropic.com/").header("User-Agent", "ToClaude").build()).execute().use { it.body?.bytes() }
        (System.nanoTime() - t0) / 1_000_000
    }
}
