package com.joe.mepe.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 版本检测：对比本机 versionName 与 GitHub Releases 上发布的最新版本（设置-关于页用）。
 * 仓库公开，匿名访问即可；从每个 Release 的 tag、标题、资产文件名里提取版本号取最大值
 * （发布资产常直接带版本，如 ME-PE-v2.4.32.apk），预发布 Release 也参与比较。
 */
object UpdateChecker {
    data class Result(
        val hasUpdate: Boolean,
        val current: String,
        val latest: String,     // 检测到的最新版本，仓库没发布过时为 ""
        val releaseUrl: String, // 最新版本所在发布页（浏览器打开后下载新 APK）
        val error: String? = null,
    )

    private const val API = "https://api.github.com/repos/nailao946/ME-PE/releases?per_page=20"
    private const val PAGE = "https://github.com/nailao946/ME-PE/releases"
    private val VER = Regex("""v?(\d+)\.(\d+)(?:\.(\d+))?""")

    /** 提取第一个版本号为 [主, 次, 修] 列表，取不出返回 null */
    private fun parseVer(s: String): List<Int>? {
        val m = VER.find(s) ?: return null
        val a = m.groupValues[1].toIntOrNull() ?: return null
        val b = m.groupValues[2].toIntOrNull() ?: return null
        val c = m.groupValues[3].ifEmpty { "0" }.toIntOrNull() ?: return null
        return listOf(a, b, c)
    }

    /** 逐位比较：a 是否比 b 新（主 → 次 → 修） */
    private fun isNewer(a: List<Int>, b: List<Int>): Boolean {
        for (i in 0..2) { if (a[i] != b[i]) return a[i] > b[i] }
        return false
    }

    /** 移动网络到 GitHub 的链路不稳，失败自动重试一次（与云同步一致） */
    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        var lastError: String? = null
        var result: Result? = null
        repeat(2) {
            if (result == null) {
                try { result = fetch(currentVersion) } catch (e: Exception) { lastError = e.message }
            }
        }
        result ?: Result(false, currentVersion, "", PAGE, lastError ?: "检查失败，请检查网络后重试")
    }

    private fun fetch(currentVersion: String): Result {
        val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dns(DnsFallback)
            .build()
        val req = Request.Builder().url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ME-PE")
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}：${text.take(200)}")
            val arr = Json.parseToJsonElement(text).jsonArray
            val cur = parseVer(currentVersion) ?: listOf(0, 0, 0)
            var best: List<Int>? = null
            var bestStr = ""
            var url = PAGE
            for (rel in arr) {
                val obj = rel.jsonObject
                val relUrl = obj["html_url"]?.jsonPrimitive?.content ?: PAGE
                val candidates = buildList {
                    add(obj["tag_name"]?.jsonPrimitive?.content ?: "")
                    add(obj["name"]?.jsonPrimitive?.content ?: "")
                    obj["assets"]?.jsonArray?.forEach { add(it.jsonObject["name"]?.jsonPrimitive?.content ?: "") }
                }
                for (s in candidates) {
                    val v = parseVer(s) ?: continue
                    val b = best
                    if (b == null || isNewer(v, b)) { best = v; bestStr = "${v[0]}.${v[1]}.${v[2]}"; url = relUrl }
                }
            }
            val b = best ?: return Result(false, currentVersion, "", PAGE, "仓库还没有发布过版本（发布 Release 后才能检测更新）")
            return Result(isNewer(b, cur), currentVersion, bestStr, url, null)
        }
    }
}
