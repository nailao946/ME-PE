package com.joe.mepe.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * GitHub 免费云同步：把 JsonData 目录的 JSON 文件提交到用户自己的私有仓库 `data/` 目录。
 * 配置（PAT/仓库名）保存在 JsonData 之外，避免随数据一起被上传。
 */
object SyncConfig {
    @Serializable
    data class Conf(
        var pat: String = "",
        var repo: String = "",      // owner/name
        var branch: String = "main",
        var autoPush: Boolean = false,
        var lastPushAt: String = "",
        var lastPullAt: String = "",
        var account: String = "",   // 授权登录后显示的 GitHub 用户名
    )

    private const val FILE = "sync_config.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun file(context: Context): File = File(context.filesDir, FILE)

    fun load(context: Context): Conf = try {
        val f = file(context)
        if (f.exists()) json.decodeFromString(Conf.serializer(), f.readText()) else Conf()
    } catch (_: Exception) { Conf() }

    fun save(context: Context, conf: Conf) {
        file(context).writeText(json.encodeToString(conf))
    }
}

object GitHubSync {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val API = "https://api.github.com"

    private fun request(conf: SyncConfig.Conf, url: String, method: String, body: String?): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${conf.pat}")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ME-PE")
            .method(method, body?.toRequestBody("application/json".toMediaType()))
            .build()

    /** 发请求并返回原始响应体；非 2xx 抛异常 */
    private fun httpCall(conf: SyncConfig.Conf, url: String, method: String, body: String?): String {
        val resp = http.newCall(request(conf, url, method, body)).execute()
        resp.use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}：${text.take(300)}")
            return text
        }
    }

    private fun parseObj(text: String): JsonObject =
        JsonStore.json.parseToJsonElement(text) as JsonObject

    /** 上传 JsonData 全部文件到仓库 data/ 目录（逐文件 commit，已存在则带 sha 更新） */
    suspend fun push(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        require(conf.pat.isNotBlank() && conf.repo.isNotBlank()) { "请先填写 GitHub Token 和仓库名" }
        val files = JsonStore.allFiles()
        if (files.isEmpty()) return@withContext "没有可上传的数据"
        var okCount = 0
        var lastErr: String? = null
        for (f in files) {
            try {
                val content = Base64.getEncoder().encodeToString(f.readText().toByteArray(Charsets.UTF_8))
                // 查询已有文件 sha
                var sha: String? = null
                try {
                    val existing = parseObj(httpCall(conf, "$API/repos/${conf.repo}/contents/data/${f.name}?ref=${conf.branch}", "GET", null))
                    sha = (existing["sha"])?.toString()?.trim('"')
                } catch (_: Exception) { /* 不存在则新建 */ }

                val payload = buildJsonObject {
                    put("message", "ME 数据同步（Android）· ${java.time.LocalDateTime.now()}")
                    put("content", content)
                    put("branch", conf.branch)
                    if (sha != null) put("sha", sha)
                }.toString()
                parseObj(httpCall(conf, "$API/repos/${conf.repo}/contents/data/${f.name}", "PUT", payload))
                okCount++
            } catch (e: Exception) {
                lastErr = e.message
            }
        }
        return@withContext if (okCount == files.size) {
            conf.lastPushAt = java.time.LocalDateTime.now().toString()
            SyncConfig.save(context, conf)
            "✓ 已上传 $okCount 个文件"
        } else {
            "已上传 $okCount/${files.size} 个" + (lastErr?.let { "，错误：$it" } ?: "")
        }
    }

    /** 从仓库 data/ 目录下载并覆盖本地 JsonData（先做本地备份） */
    suspend fun pull(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        require(conf.pat.isNotBlank() && conf.repo.isNotBlank()) { "请先填写 GitHub Token 和仓库名" }
        val items: List<JsonObject> = try {
            val text = httpCall(conf, "$API/repos/${conf.repo}/contents/data?ref=${conf.branch}", "GET", null)
            val el = JsonStore.json.parseToJsonElement(text)
            (el as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        } catch (e: Exception) {
            if (e.message?.contains("404") == true) emptyList() else throw e
        }
        if (items.isEmpty()) return@withContext "仓库 data 目录为空，没有可下载的数据"

        // 本地备份
        val backupDir = File(context.filesDir, "JsonData_backup_${System.currentTimeMillis()}")
        backupDir.mkdirs()
        JsonStore.allFiles().forEach { it.copyTo(File(backupDir, it.name), overwrite = true) }

        var n = 0
        var lastErr: String? = null
        for (item in items) {
            val name = item["name"]?.toString()?.trim('"') ?: continue
            if (!name.endsWith(".json")) continue
            try {
                val detail = parseObj(httpCall(conf, "$API/repos/${conf.repo}/contents/data/$name?ref=${conf.branch}", "GET", null))
                val contentB64 = detail["content"]?.toString()?.trim('"') ?: continue
                val text = String(Base64.getMimeDecoder().decode(contentB64), Charsets.UTF_8)
                File(JsonStore.dir, name).writeText(text)
                n++
            } catch (e: Exception) {
                lastErr = e.message
            }
        }
        if (n > 0) {
            conf.lastPullAt = java.time.LocalDateTime.now().toString()
            SyncConfig.save(context, conf)
            DataBus.bump()
        }
        if (n == items.size) "✓ 已下载 $n 个文件（原数据已备份）"
        else "已下载 $n/${items.size} 个" + (lastErr?.let { "，错误：$it" } ?: "")
    }
}

/**
 * GitHub 设备码授权登录（与 PC 端同一 OAuth App）：
 * 应用显示一个 8 位代码 → 打开浏览器 github.com/login/device → 登录输入代码点 Authorize → 自动拿到 Token。
 */
object GitHubLogin {
    private const val CLIENT_ID = "Ov23liBQpCTtMnMWyzsa"
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Session(
        val deviceCode: String,
        val userCode: String,
        val verifyUrl: String,
        var interval: Int = 5,
        val expiresAt: Long = System.currentTimeMillis() + 15 * 60 * 1000,
    )

    private fun postForm(url: String, body: FormBody): Request =
        Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "ME-PE")
            .post(body).build()

    private fun parseObj(text: String): JsonObject =
        JsonStore.json.parseToJsonElement(text) as JsonObject

    /** 第一步：请求 device code */
    suspend fun start(): Session = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", "repo")
            .build()
        http.newCall(postForm("https://github.com/login/device/code", form)).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}：${text.take(200)}")
            val o = parseObj(text)
            Session(
                deviceCode = o["device_code"]?.toString()?.trim('"') ?: "",
                userCode = o["user_code"]?.toString()?.trim('"') ?: "",
                verifyUrl = o["verification_uri"]?.toString()?.trim('"') ?: "https://github.com/login/device",
                interval = o["interval"]?.toString()?.trim('"')?.toIntOrNull() ?: 5,
                expiresAt = System.currentTimeMillis() + (o["expires_in"]?.toString()?.trim('"')?.toLongOrNull() ?: 900L) * 1000,
            )
        }
    }

    /** 第二步：轮询一次。返回 null=仍在等待；"!xxx"=错误；其他=token */
    suspend fun poll(s: Session): String? = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("device_code", s.deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        http.newCall(postForm("https://github.com/login/oauth/access_token", form)).execute().use { r ->
            val o = parseObj(r.body?.string() ?: "{}")
            val err = o["error"]?.toString()?.trim('"')
            if (err != null) {
                return@withContext when (err) {
                    "authorization_pending" -> null
                    "slow_down" -> { s.interval += 5; null }
                    "expired_token" -> "!授权码已过期，请重新开始"
                    else -> "!授权失败：$err"
                }
            }
            o["access_token"]?.toString()?.trim('"')
        }
    }

    /** 用 token 拉取 GitHub 用户名（失败返回空串） */
    suspend fun fetchAccountName(token: String): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "ME-PE")
                .build()
            http.newCall(req).execute().use { r ->
                val o = parseObj(r.body?.string() ?: "{}")
                o["login"]?.toString()?.trim('"') ?: ""
            }
        } catch (_: Exception) { "" }
    }
}
