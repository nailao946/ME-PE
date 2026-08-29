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
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
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
        var refreshToken: String = "",  // GitHub App 开启「令牌过期」时用于自动续期
        var tokenExpiresAt: Long = 0L,  // 令牌到期时间戳（毫秒）；0 = 令牌不过期
        var repo: String = "",      // owner/name
        var branch: String = "main",
        var autoPush: Boolean = false,
        var lastPushAt: String = "",
        var lastPullAt: String = "",
        var account: String = "",   // 授权登录后显示的 GitHub 用户名
        // 每个文件上次同步后的云端 sha，用于检测「云端比本地新」避免覆盖别人/别的设备的更新
        var fileShas: Map<String, String> = emptyMap(),
    )

    private const val FILE = "sync_config.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun file(context: Context): File = File(context.filesDir, FILE)

    fun load(context: Context): Conf = try {
        val f = file(context)
        val c = if (f.exists()) json.decodeFromString(Conf.serializer(), f.readText()) else Conf()
        // 数据仓库由 ME-OKR 更名为 ME-Data：旧配置自动迁移，避免与桌面端同步中断
        if (c.repo == "ME-OKR" || c.repo.endsWith("/ME-OKR")) {
            c.repo = if (c.repo.contains('/')) c.repo.substringBefore('/') + "/ME-Data" else "ME-Data"
            save(context, c)
        }
        c
    } catch (_: Exception) { Conf() }

    fun save(context: Context, conf: Conf) {
        file(context).writeText(json.encodeToString(conf))
    }
}

/**
 * DNS 解析兜底：先走系统 DNS，查不到时自动改用加密 DNS（DoH，阿里 223.5.5.5）再查一次。
 * 部分运营商网络解析 github.com 会返回空结果（报 unable to resolve host），浏览器因为自带
 * 加密 DNS 能打开网页，App 用系统 DNS 就会失败；这里给 App 补上同样的能力。
 */
object DnsFallback : Dns {
    private val doh: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build())
            .url("https://dns.alidns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("223.5.5.5"),
                InetAddress.getByName("223.6.6.6"),
            )
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val sys = try { Dns.SYSTEM.lookup(hostname) } catch (_: UnknownHostException) { emptyList() }
        if (sys.isNotEmpty()) return sys
        return try {
            doh.lookup(hostname)
        } catch (_: Exception) {
            throw UnknownHostException("无法解析 $hostname（系统 DNS 与加密 DNS 均失败），请检查网络")
        }
    }
}

object GitHubSync {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(DnsFallback)
        .build()

    private const val API = "https://api.github.com"

    /** 统一错误文案：401 = 令牌在 GitHub 侧已失效（被撤销或过期），引导重新授权 */
    private fun describeError(code: Int, text: String): String =
        if (code == 401) "GitHub 授权已失效（令牌被撤销或已过期），请重新授权登录一次即可恢复"
        else "HTTP $code：${text.take(300)}"

    private fun request(conf: SyncConfig.Conf, url: String, method: String, body: String?, accept: String = "application/vnd.github+json"): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${conf.pat}")
            .header("Accept", accept)
            .header("User-Agent", "ME-PE")
            .method(method, body?.toRequestBody("application/json".toMediaType()))
            .build()

    /** 发请求并返回原始响应体；非 2xx 抛异常 */
    private fun httpCall(conf: SyncConfig.Conf, url: String, method: String, body: String?): String {
        val resp = http.newCall(request(conf, url, method, body)).execute()
        resp.use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw RuntimeException(describeError(r.code, text))
            return text
        }
    }

    /** GET 请求（可指定 Accept），失败自动重试一次：移动网络到 GitHub 的链路不稳，偶发响应不完整 */
    private fun httpGet(conf: SyncConfig.Conf, url: String, accept: String): String {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                val resp = http.newCall(request(conf, url, "GET", null, accept)).execute()
                resp.use { r ->
                    val text = r.body?.string() ?: ""
                    if (!r.isSuccessful) throw RuntimeException(describeError(r.code, text))
                    return text
                }
            } catch (e: Exception) {
                last = e
                if (attempt == 0) try { Thread.sleep(1200) } catch (_: InterruptedException) { }
            }
        }
        throw last ?: RuntimeException("请求失败")
    }

    private fun parseObj(text: String): JsonObject =
        JsonStore.json.parseToJsonElement(text) as JsonObject

    /** 反馈提交目标仓库（项目 Issues，非用户的同步数据仓库） */
    private const val FEEDBACK_REPO = "nailao946/ME-PE"

    /**
     * 提交用户反馈到项目仓库 Issues。任何 GitHub 账号都能在公开仓库提 issue，无需仓库写权限；
     * 首行作为标题（过长截断），正文自动附上版本与平台信息便于定位问题。返回 issue 编号。
     */
    suspend fun submitFeedback(context: Context, content: String): Int = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        if (conf.pat.isBlank()) throw RuntimeException("尚未绑定 GitHub，请先在「设置 → 云同步」中登录后再提交反馈")
        GitHubLogin.maybeRefresh(context, conf)
        val text = content.trim()
        if (text.isEmpty()) throw RuntimeException("请先填写反馈内容")

        val firstLine = text.lineSequence().first().trim()
        val title = if (firstLine.length > 40) firstLine.take(40) + "…" else firstLine
        val body = text + "\n\n---\n来自 ME 安卓版 v${com.joe.mepe.BuildConfig.VERSION_NAME} · Android"
        val payload = buildJsonObject {
            put("title", title)
            put("body", body)
        }.toString()

        val resp = httpCall(conf, "$API/repos/$FEEDBACK_REPO/issues", "POST", payload)
        (parseObj(resp)["number"]?.toString()?.trim('"'))?.toIntOrNull() ?: 0
    }

    /**
     * 确保同步仓库存在：默认 ME-Data（私有），用户只填仓库名时自动挂到当前账号下（已存在则直接用）。
     * 登录后与上传/下载前调用，用户无需手填 owner/ 前缀。配置里只保存仓库名。
     */
    suspend fun ensureRepo(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        require(conf.pat.isNotBlank()) { "尚未登录 GitHub 账号" }
        GitHubLogin.maybeRefresh(context, conf)

        var login = conf.account
        if (login.isBlank()) {
            login = GitHubLogin.fetchAccountName(conf.pat)
            conf.account = login
        }
        if (login.isBlank()) throw RuntimeException("无法获取 GitHub 用户名")

        if (conf.repo.isBlank()) conf.repo = "ME-Data"
        val name = conf.repo.substringAfter('/').ifBlank { "ME-Data" }

        val payload = buildJsonObject {
            put("name", name)
            put("private", true)
            put("auto_init", false)
        }.toString()
        try {
            val req = Request.Builder().url("$API/user/repos")
                .header("Authorization", "Bearer ${conf.pat}")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ME-PE")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { r ->
                // 422 = 仓库已存在，直接使用
                if (!r.isSuccessful && r.code != 422) {
                    throw RuntimeException("创建仓库失败：" + describeError(r.code, r.body?.string() ?: ""))
                }
            }
        } catch (e: RuntimeException) {
            if (!e.message.orEmpty().contains("422")) throw e
        }

        if (conf.branch.isBlank()) conf.branch = "main"
        SyncConfig.save(context, conf)
        "$login/$name"
    }

    /** 把用户填的仓库名解析成 owner/name：只填 ME-Data 时自动补当前账号前缀 */
    private suspend fun resolveRepo(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        if (conf.repo.isBlank()) {
            ensureRepo(context)
            return@withContext resolveRepo(context)
        }
        if (conf.repo.contains('/')) return@withContext conf.repo
        var login = conf.account
        if (login.isBlank()) {
            login = GitHubLogin.fetchAccountName(conf.pat)
            conf.account = login
            SyncConfig.save(context, conf)
        }
        "$login/${conf.repo}"
    }

    /** 上传 JsonData 全部文件到仓库 data/ 目录（逐文件 commit，已存在则带 sha 更新）。
     *  防覆盖：若某文件云端 sha 与上次同步记录不一致（别的设备改过），跳过该文件并提示先下载。 */
    suspend fun push(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        require(conf.pat.isNotBlank()) { "请先登录 GitHub 账号或填写 Token" }
        GitHubLogin.maybeRefresh(context, conf)
        val repo = resolveRepo(context)
        val files = JsonStore.allFiles()
        if (files.isEmpty()) return@withContext "没有可上传的数据"
        var okCount = 0
        var skipped = 0
        var lastErr: String? = null
        val newShas = conf.fileShas.toMutableMap()
        for (f in files) {
            try {
                val content = Base64.getEncoder().encodeToString(f.readText().toByteArray(Charsets.UTF_8))
                // 查询已有文件 sha
                var sha: String? = null
                try {
                    val existing = parseObj(httpCall(conf, "$API/repos/$repo/contents/data/${f.name}?ref=${conf.branch}", "GET", null))
                    sha = (existing["sha"])?.toString()?.trim('"')
                } catch (_: Exception) { /* 不存在则新建 */ }

                // 云端被其它设备更新过而本地没有先下载 → 跳过，避免覆盖
                val known = conf.fileShas[f.name]
                if (known != null && sha != null && known != sha) {
                    skipped++
                    continue
                }

                val payload = buildJsonObject {
                    put("message", "ME 数据同步（Android）· ${java.time.LocalDateTime.now()}")
                    put("content", content)
                    put("branch", conf.branch)
                    if (sha != null) put("sha", sha)
                }.toString()
                val resp = parseObj(httpCall(conf, "$API/repos/$repo/contents/data/${f.name}", "PUT", payload))
                (resp["content"] as? JsonObject)?.get("sha")?.toString()?.trim('"')?.let { newShas[f.name] = it }
                okCount++
            } catch (e: Exception) {
                lastErr = e.message
            }
        }
        if (okCount > 0) {
            conf.fileShas = newShas
            conf.lastPushAt = java.time.LocalDateTime.now().toString()
            SyncConfig.save(context, conf)
        }
        val base = if (okCount == files.size) "✓ 已上传 $okCount 个文件"
        else "已上传 $okCount/${files.size} 个" + (lastErr?.let { "，错误：$it" } ?: "")
        return@withContext if (skipped > 0)
            "$base；云端有 $skipped 个文件比本地新，已跳过（请先「下载数据」再上传）"
        else base
    }

    /** 从仓库 data/ 目录下载并覆盖本地 JsonData（先做本地备份）。
     *  每个文件优先用 raw 接口直接取文件原文（响应体即文件内容，不经 Base64 编解码，
     *  规避移动网络下 Base64 传输损坏导致的 "Last unit does not have enough valid bits"）；
     *  写盘前校验是有效 JSON，损坏内容不会写进本地数据；单个文件失败不影响其它文件，
     *  失败的文件名与原因会列在结果里。 */
    suspend fun pull(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        require(conf.pat.isNotBlank()) { "请先登录 GitHub 账号或填写 Token" }
        GitHubLogin.maybeRefresh(context, conf)
        val repo = resolveRepo(context)

        // 目录清单：一次拿到每个文件的名字、大小与 sha
        val items: List<JsonObject> = try {
            val text = httpGet(conf, "$API/repos/$repo/contents/data?ref=${conf.branch}", "application/vnd.github+json")
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
        val failed = mutableListOf<String>()
        val newShas = conf.fileShas.toMutableMap()
        for (item in items) {
            val name = item["name"]?.toString()?.trim('"') ?: continue
            if (!name.endsWith(".json")) continue
            val size = item["size"]?.toString()?.trim('"')?.toLongOrNull() ?: 0L
            val sha = item["sha"]?.toString()?.trim('"')
            try {
                // 首选 raw 方式：响应体就是文件内容本身，不经 Base64
                var text: String? = try {
                    val raw = httpGet(conf, "$API/repos/$repo/contents/data/$name?ref=${conf.branch}", "application/vnd.github.raw")
                    if (raw.isNotEmpty() || size == 0L) raw else null
                } catch (_: Exception) { null }

                // 兜底：raw 拿不到时退回 JSON 接口 + Base64 解码
                if (text == null) {
                    val detail = parseObj(httpCall(conf, "$API/repos/$repo/contents/data/$name?ref=${conf.branch}", "GET", null))
                    val content = (detail["content"] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.takeIf { it.isString }?.content ?: ""
                    if (content.isNotBlank()) {
                        text = String(Base64.getMimeDecoder().decode(content.replace("\n", "").replace("\r", "")), Charsets.UTF_8)
                    }
                }

                val t = text
                if (t == null) {
                    failed.add(name); lastErr = "文件内容为空"; continue
                }
                // 校验是有效 JSON 再写入，防止把传输损坏的内容存成本地数据
                try { JsonStore.json.parseToJsonElement(t) } catch (_: Exception) {
                    failed.add(name); lastErr = "下载内容不是有效 JSON"; continue
                }
                File(JsonStore.dir, name).writeText(t)
                sha?.let { newShas[name] = it }
                n++
            } catch (e: Exception) {
                failed.add(name)
                lastErr = e.message
            }
        }
        if (n > 0) {
            conf.fileShas = newShas
            conf.lastPullAt = java.time.LocalDateTime.now().toString()
            SyncConfig.save(context, conf)
            DataBus.bump()
        }
        if (failed.isEmpty()) return@withContext "✓ 已下载 $n 个文件（原数据已备份）"
        val names = failed.take(4).joinToString("、") + if (failed.size > 4) "等${failed.size}个文件" else ""
        "已下载 $n/${items.size} 个，失败：$names" + (lastErr?.let { "（$it）" } ?: "")
    }
}

/**
 * GitHub 设备码授权登录（与 PC 端同一 OAuth App）：
 * 应用显示一个 8 位代码 → 打开浏览器 github.com/login/device → 登录输入代码点 Authorize → 自动拿到 Token。
 */
object GitHubLogin {
    const val CLIENT_ID = "Ov23liBQpCTtMnMWyzsa"
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(DnsFallback)
        .build()

    data class Session(
        val deviceCode: String,
        val userCode: String,
        val verifyUrl: String,
        var interval: Int = 5,
        val expiresAt: Long = System.currentTimeMillis() + 15 * 60 * 1000,
        // 授权成功时一并返回的续期信息（应用开启「令牌过期」才有值，否则为空/0）
        var refreshToken: String = "",
        var tokenExpiresAt: Long = 0L,
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
            o["access_token"]?.toString()?.trim('"')?.also {
                s.refreshToken = o["refresh_token"]?.toString()?.trim('"') ?: ""
                val exp = o["expires_in"]?.toString()?.trim('"')?.toLongOrNull() ?: 0L
                s.tokenExpiresAt = if (exp > 0) System.currentTimeMillis() + exp * 1000 else 0L
            }
        }
    }

    /**
     * GitHub App 开启「令牌过期」后用户令牌 8 小时失效：到期前 10 分钟内自动用 refresh_token 换新，
     * 用户无需反复重新授权。未存过期时间（应用关闭过期或旧版本登录的）时什么都不做；
     * 换新失败不打断，让后续请求自然收到 401 并提示重新授权。
     */
    suspend fun maybeRefresh(context: Context, conf: SyncConfig.Conf) = withContext(Dispatchers.IO) {
        if (conf.tokenExpiresAt <= 0L || conf.refreshToken.isBlank()) return@withContext
        if (conf.tokenExpiresAt - System.currentTimeMillis() > 10 * 60 * 1000L) return@withContext
        try {
            val form = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", conf.refreshToken)
                .build()
            http.newCall(postForm("https://github.com/login/oauth/access_token", form)).execute().use { r ->
                val o = parseObj(r.body?.string() ?: "{}")
                val token = o["access_token"]?.toString()?.trim('"')
                if (token.isNullOrBlank()) return@use
                conf.pat = token
                o["refresh_token"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }?.let { conf.refreshToken = it }
                val exp = o["expires_in"]?.toString()?.trim('"')?.toLongOrNull() ?: 0L
                if (exp > 0) conf.tokenExpiresAt = System.currentTimeMillis() + exp * 1000
                SyncConfig.save(context, conf)
            }
        } catch (_: Exception) { }
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
