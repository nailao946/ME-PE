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
import okhttp3.Credentials
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
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * 云同步：把 JsonData 目录的 JSON 文件备份到用户自己的云端（GitHub / Gitee 私有仓库 data/ 目录，
 * 或任意 WebDAV 服务的 ME-Data 文件夹，如坚果云）。三种方式 PC ↔ 安卓互通，文件布局完全一致。
 * 配置（令牌/账号密码）保存在 JsonData 之外，避免随数据一起被上传。
 */
object SyncConfig {
    @Serializable
    data class Conf(
        var provider: String = "github",  // github | gitee | webdav
        var pat: String = "",             // GitHub token
        var refreshToken: String = "",    // GitHub App 开启「令牌过期」时用于自动续期
        var tokenExpiresAt: Long = 0L,    // 令牌到期时间戳（毫秒）；0 = 令牌不过期
        var repo: String = "",            // Git 供应商=owner/name 中的 name；WebDAV=文件夹名
        var branch: String = "main",      // 仅 Git 供应商使用（GitHub 默认 main，Gitee 默认 master）
        var autoPush: Boolean = false,
        var lastPushAt: String = "",
        var lastPullAt: String = "",
        var account: String = "",         // GitHub 登录用户名（显示用）
        var giteePat: String = "",        // Gitee 私人令牌（gitee.com → 设置 → 私人令牌）
        var giteeAccount: String = "",    // Gitee 用户名（显示用）
        var webdavUrl: String = "",       // WebDAV 地址，留空 = 坚果云 https://dav.jianguoyun.com/dav/
        var webdavUser: String = "",      // WebDAV 账号（坚果云为注册手机号/邮箱）
        var webdavPass: String = "",      // WebDAV 密码（坚果云用「安全选项」里生成的应用密码）
        // 每个文件上次同步后的云端版本标识（Git=文件 sha，WebDAV=内容 md5），
        // 用于检测「云端比本地新」避免覆盖别人/别的设备的更新
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

/** 云端存储的统一抽象：push/pull 只认这份接口，三种同步方式各自实现 */
private interface CloudBackend {
    /** 确保云端仓库/目录存在（不存在就创建），返回用于展示的目标名 */
    fun ensureReady(context: Context): String

    /** 列出同步目录下的文件（目录不存在返回空列表，其它网络错误抛异常） */
    fun list(): List<RemoteFile>

    /** 读取文件内容，返回 (内容, 版本标识)；文件不存在返回 null */
    fun read(name: String): Pair<String, String?>?

    /** 当前云端版本标识（Git=文件 sha，WebDAV=内容 md5）；文件不存在返回 null */
    fun revOf(name: String): String?

    /** 写入文件，返回新的云端版本标识 */
    fun write(name: String, content: String, prevRev: String?): String
}

private data class RemoteFile(val name: String, val size: Long, val rev: String)

private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .dns(DnsFallback)
    .build()

private const val GITHUB_API = "https://api.github.com"
private const val GITEE_API = "https://gitee.com/api/v5"

private fun parseObj(text: String): JsonObject =
    JsonStore.json.parseToJsonElement(text) as JsonObject

private fun md5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

object CloudSync {
    /** 反馈提交目标仓库（项目 Issues，非用户的同步数据仓库）。反馈始终走 GitHub，与云同步方式无关 */
    private const val FEEDBACK_REPO = "nailao946/ME-PE"

    private fun backendFor(conf: SyncConfig.Conf): CloudBackend = when (conf.provider) {
        "gitee" -> GitBackend(conf, GITEE_API, "Gitee", true)
        "webdav" -> WebDavBackend(conf)
        else -> GitBackend(conf, GITHUB_API, "GitHub", false)
    }

    /**
     * 提交用户反馈到项目仓库 Issues。任何 GitHub 账号都能在公开仓库提 issue，无需仓库写权限；
     * 首行作为标题（过长截断），正文自动附上版本与平台信息便于定位问题。返回 issue 编号。
     */
    suspend fun submitFeedback(context: Context, content: String): Int = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        if (conf.pat.isBlank())
            throw RuntimeException("提交反馈需要 GitHub 授权（与云同步方式无关）：请在「设置 → 云同步」选择 GitHub 并登录后再提交")
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

        val req = Request.Builder().url("$GITHUB_API/repos/$FEEDBACK_REPO/issues")
            .header("Authorization", "Bearer ${conf.pat}")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ME-PE")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val text2 = r.body?.string() ?: ""
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}：${text2.take(300)}")
            (parseObj(text2)["number"]?.toString()?.trim('"'))?.toIntOrNull() ?: 0
        }
    }

    /** 确保同步目标存在（登录后与上传/下载前调用），返回用于展示的目标名 */
    suspend fun ensureRepo(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        backendFor(conf).ensureReady(context)
    }

    /** 上传 JsonData 全部文件到云端（逐文件提交）。
     *  防覆盖：若某文件云端版本与上次同步记录不一致（别的设备改过），跳过该文件并提示先下载。 */
    suspend fun push(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        val backend = backendFor(conf)
        if (conf.provider != "webdav" && conf.provider != "gitee") GitHubLogin.maybeRefresh(context, conf)
        val files = JsonStore.allFiles()
        if (files.isEmpty()) return@withContext "没有可上传的数据"
        backend.ensureReady(context)
        var okCount = 0
        var skipped = 0
        var lastErr: String? = null
        val newShas = conf.fileShas.toMutableMap()
        for (f in files) {
            try {
                val rev = backend.revOf(f.name)
                // 云端被其它设备更新过而本地没有先下载 → 跳过，避免覆盖
                val known = conf.fileShas[f.name]
                if (known != null && rev != null && known != rev) {
                    skipped++
                    continue
                }
                val content = f.readText()
                newShas[f.name] = backend.write(f.name, content, rev)
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

    /** 从云端下载并覆盖本地 JsonData（先做本地备份）。
     *  写盘前校验是有效 JSON，损坏内容不会写进本地数据；单个文件失败不影响其它文件，
     *  失败的文件名与原因会列在结果里。 */
    suspend fun pull(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        val backend = backendFor(conf)
        if (conf.provider != "webdav" && conf.provider != "gitee") GitHubLogin.maybeRefresh(context, conf)
        backend.ensureReady(context)

        // 目录清单：一次拿到每个文件的名字、大小与版本标识
        val items: List<RemoteFile> = try {
            backend.list()
        } catch (e: Exception) {
            if (e.message?.contains("404") == true) emptyList() else throw e
        }
        if (items.isEmpty()) return@withContext "同步目录为空，没有可下载的数据"

        // 本地备份
        val backupDir = File(context.filesDir, "JsonData_backup_${System.currentTimeMillis()}")
        backupDir.mkdirs()
        JsonStore.allFiles().forEach { it.copyTo(File(backupDir, it.name), overwrite = true) }

        var n = 0
        var lastErr: String? = null
        val failed = mutableListOf<String>()
        val newShas = conf.fileShas.toMutableMap()
        for (item in items) {
            try {
                val r = backend.read(item.name)
                val t = r?.first
                if (t == null) {
                    failed.add(item.name); lastErr = "文件内容为空"; continue
                }
                // 校验是有效 JSON 再写入，防止把传输损坏的内容存成本地数据
                try { JsonStore.json.parseToJsonElement(t) } catch (_: Exception) {
                    failed.add(item.name); lastErr = "下载内容不是有效 JSON"; continue
                }
                File(JsonStore.dir, item.name).writeText(t)
                newShas[item.name] = item.rev.ifBlank { r.second ?: md5(t) }
                n++
            } catch (e: Exception) {
                failed.add(item.name)
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
 * GitHub / Gitee 的 Contents API 实现（两家接口结构一致，差别在域名、鉴权方式与错误文案）。
 * 文件放在仓库 data/ 目录；版本标识 = 文件 blob sha。
 */
private class GitBackend(
    private val conf: SyncConfig.Conf,
    private val api: String,
    private val label: String,
    private val isGitee: Boolean,
) : CloudBackend {

    init {
        if (isGitee) require(conf.giteePat.isNotBlank()) { "请先填写 Gitee 私人令牌" }
        else require(conf.pat.isNotBlank()) { "请先登录 GitHub 账号或填写 Token" }
    }

    private val token = if (isGitee) conf.giteePat.trim() else conf.pat.trim()
    private val branch get() = conf.branch.ifBlank { if (isGitee) "master" else "main" }

    /** 统一错误文案：401 = 令牌在云端侧已失效（被撤销或过期），引导重新配置 */
    private fun describeError(code: Int, text: String): String = when {
        code == 401 && isGitee -> "Gitee 令牌已失效（被撤销或已过期），请在「设置 → 云同步」重新填写私人令牌"
        code == 401 -> "GitHub 授权已失效（令牌被撤销或已过期），请重新授权登录一次即可恢复"
        else -> "HTTP $code：${text.take(300)}"
    }

    private fun request(url: String, method: String, body: String?, accept: String = "application/vnd.github+json"): Request {
        val b = Request.Builder()
            .header("User-Agent", "ME-PE")
            .header("Accept", accept)
            .method(method, body?.toRequestBody("application/json".toMediaType()))
        if (isGitee) b.url(if (url.contains('?')) "$url&access_token=${enc(token)}" else "$url?access_token=${enc(token)}")
        else b.url(url).header("Authorization", "Bearer $token")
        return b.build()
    }

    /** 发请求并返回原始响应体；非 2xx 抛异常 */
    private fun call(url: String, method: String, body: String?, accept: String = "application/vnd.github+json"): String {
        http.newCall(request(url, method, body, accept)).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw RuntimeException(describeError(r.code, text))
            return text
        }
    }

    /** GET（可指定 Accept），失败自动重试一次：移动网络链路不稳，偶发响应不完整 */
    private fun get(url: String, accept: String): String {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                http.newCall(request(url, "GET", null, accept)).execute().use { r ->
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

    /** 纯仓库名（用户只填 name 时自动挂到自己账号下，配置里不存 owner/） */
    private val repoName get() = conf.repo.substringAfter('/').ifBlank { "ME-Data" }
    /** owner：用户填了 owner/name 就用填的，否则用当前登录账号 */
    private val owner get() = if (conf.repo.contains('/')) conf.repo.substringBefore('/') else conf.account
    private fun dataUrl(path: String) = "$api/repos/${enc(owner)}/$repoName/contents/$path"

    override fun ensureReady(context: Context): String {
        // 用户名：GitHub 用已缓存的 account，Gitee 每次登录后缓存到 giteeAccount
        var login = if (isGitee) conf.giteeAccount else conf.account
        if (login.isBlank()) {
            login = if (isGitee) {
                val o = parseObj(get("$GITEE_API/user", "application/json"))
                (o["login"] ?: o["name"])?.toString()?.trim('"') ?: ""
            } else {
                // 与 GitHubLogin.fetchAccountName 相同的请求（此处非挂起上下文，直接发请求）
                try {
                    val req = Request.Builder().url("$api/user")
                        .header("Authorization", "Bearer $token")
                        .header("User-Agent", "ME-PE")
                        .build()
                    parseObj(http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) "" else r.body?.string() ?: "{}"
                    })["login"]?.toString()?.trim('"') ?: ""
                } catch (_: Exception) { "" }
            }
            if (login.isBlank()) throw RuntimeException("无法获取 $label 用户名，请检查令牌权限")
            if (isGitee) conf.giteeAccount = login else conf.account = login
        }

        if (conf.repo.isBlank()) conf.repo = "ME-Data"
        val name = repoName

        // 创建私有仓库（已存在则直接使用：GitHub 422；Gitee 400 且提示已存在）
        val payload = buildJsonObject {
            put("name", name)
            put("private", true)
            // Gitee 空仓库无法用 contents API 写入，auto_init 先生成一个提交（多一个 README 无影响）
            put("auto_init", isGitee)
        }.toString()
        try {
            call("$api/user/repos", "POST", payload, "application/json")
        } catch (e: RuntimeException) {
            val m = e.message.orEmpty()
            val exists = m.contains("422") || m.contains("已存在") || m.contains("同名") ||
                    m.contains("exist", ignoreCase = true) || m.contains("already")
            if (!exists) throw e
        }

        if (isGitee) {
            // Gitee 新仓库默认分支是 master
            if (conf.branch.isBlank() || conf.branch.equals("main", ignoreCase = true)) conf.branch = "master"
        } else if (conf.branch.isBlank()) {
            conf.branch = "main"
        }
        SyncConfig.save(context, conf)
        return "$login/$name"
    }

    override fun list(): List<RemoteFile> {
        val text = get(dataUrl("data?ref=${enc(branch)}"), "application/vnd.github+json")
        val el = JsonStore.json.parseToJsonElement(text)
        return (el as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }?.map { o ->
            RemoteFile(
                o["name"]?.toString()?.trim('"') ?: "",
                o["size"]?.toString()?.trim('"')?.toLongOrNull() ?: 0L,
                o["sha"]?.toString()?.trim('"') ?: "",
            )
        } ?: emptyList()
    }

    override fun read(name: String): Pair<String, String?>? {
        // GitHub 首选 raw 方式：响应体就是文件内容本身，不经 Base64（移动网络下更不易损坏）；
        // Gitee 的 contents 接口不支持 raw Accept（原样返回 JSON），直接走 JSON 接口 + Base64
        var text: String? = if (!isGitee) try {
            get(dataUrl("data/${enc(name)}?ref=${enc(branch)}"), "application/vnd.github.raw")
        } catch (_: Exception) { null } else null
        // 兜底（Gitee 的唯一路径）：JSON 接口 + Base64 解码
        var rev: String? = null
        if (text == null) {
            val detail = parseObj(call(dataUrl("data/${enc(name)}?ref=${enc(branch)}"), "GET", null))
            rev = detail["sha"]?.toString()?.trim('"')
            val content = (detail["content"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it.isString }?.content ?: ""
            if (content.isNotBlank()) {
                text = String(Base64.getMimeDecoder().decode(content.replace("\n", "").replace("\r", "")), Charsets.UTF_8)
            }
        }
        return text?.let { it to rev }
    }

    override fun revOf(name: String): String? = try {
        parseObj(call(dataUrl("data/${enc(name)}?ref=${enc(branch)}"), "GET", null))["sha"]?.toString()?.trim('"')
    } catch (_: Exception) { null } // 不存在则新建

    override fun write(name: String, content: String, prevRev: String?): String {
        fun body(sha: String?): String = buildJsonObject {
            put("message", "ME 数据同步（Android）· ${java.time.LocalDateTime.now()}")
            put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
            put("branch", branch)
            if (sha != null) put("sha", sha)
        }.toString()
        val path = dataUrl("data/${enc(name)}")
        // Gitee 与 GitHub 不同：PUT 是纯「更新」接口，不带 sha 一律 400 sha is missing（即使文件不存在），
        // 新建文件必须走 POST；撞上已存在（本地版本记录缺失）时取最新 sha 转更新。GitHub 的 PUT 兼容新建+更新，维持原行为
        val resp: JsonObject = if (prevRev != null || !isGitee) {
            parseObj(call(path, "PUT", body(prevRev)))
        } else try {
            parseObj(call(path, "POST", body(null)))
        } catch (e: RuntimeException) {
            val m = e.message.orEmpty()
            if (!(m.contains("存在") || m.contains("exist", ignoreCase = true))) throw e
            val fresh = revOf(name) ?: throw e
            parseObj(call(path, "PUT", body(fresh)))
        }
        return (resp["content"] as? JsonObject)?.get("sha")?.toString()?.trim('"') ?: ""
    }
}

/**
 * WebDAV 实现（坚果云 / Nextcloud / 群晖等任意 WebDAV 服务）。
 * 没有 sha 概念，用文件内容的 md5 指纹当版本标识：上传前 GET 对比指纹即可发现
 * 「云端被别的设备改过」，避免覆盖；目录用 PROPFIND 列举。
 */
private class WebDavBackend(private val conf: SyncConfig.Conf) : CloudBackend {

    init {
        require(conf.webdavUser.isNotBlank() && conf.webdavPass.isNotBlank()) { "请先填写 WebDAV 账号和密码" }
    }

    private val base = conf.webdavUrl.trim().ifBlank { "https://dav.jianguoyun.com/dav/" }.let {
        if (it.endsWith("/")) it else "$it/"
    }
    private val folder get() = base + enc(conf.repo.ifBlank { "ME-Data" }) + "/"
    private val authHeader = Credentials.basic(conf.webdavUser.trim(), conf.webdavPass.trim(), Charsets.UTF_8)

    private fun describeError(code: Int, text: String): String = when {
        code == 401 || code == 403 -> "WebDAV 账号或密码不正确（坚果云请用网页版「安全选项 → 添加应用密码」生成的密码，不能用登录密码）"
        else -> "HTTP $code：${text.take(300)}"
    }

    private fun call(method: String, url: String, body: ByteArray?, contentType: String? = null): Pair<Int, String> {
        val req = Request.Builder().url(url)
            .header("Authorization", authHeader)
            .header("User-Agent", "ME-PE")
            .method(method, (body ?: ByteArray(0)).toRequestBody(contentType?.toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string() ?: ""
            return r.code to text
        }
    }

    override fun ensureReady(context: Context): String {
        if (conf.repo.isBlank()) conf.repo = "ME-Data"
        // MKCOL 建目录：201 = 已创建，405/301 = 已存在，均可继续
        val (code, text) = call("MKCOL", folder, null)
        if (code != 201 && code != 405 && code != 301 && code != 200 && code != 409)
            throw RuntimeException("创建 WebDAV 目录失败：" + describeError(code, text))
        SyncConfig.save(context, conf)
        return folder
    }

    override fun list(): List<RemoteFile> {
        val (code, body) = call("PROPFIND", folder, "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:getcontentlength/></d:prop></d:propfind>".toByteArray(), "application/xml")
        if (code == 404) return emptyList()
        if (code !in 200..299 && code != 207) throw RuntimeException(describeError(code, body))

        // 解析 multistatus XML：每个 <response> 里的 <href> 与 <getcontentlength>
        val out = mutableListOf<RemoteFile>()
        val parser = android.util.Xml.newPullParser()
        parser.setInput(java.io.StringReader(body))
        var curHref: String? = null
        var curSize = 0L
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "response" -> { curHref = null; curSize = 0L }
                    "href" -> { parser.next(); curHref = parser.text }
                    "getcontentlength" -> try { parser.next(); curSize = parser.text?.toLongOrNull() ?: 0L } catch (_: Exception) { }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "response" && curHref != null) {
                    val href = curHref!!
                    // 跳过目录本身（以 / 结尾）与子目录，只留 .json 文件
                    if (!href.endsWith("/") && href.endsWith(".json")) {
                        val name = URLDecoder.decode(href.substringAfterLast('/'), "UTF-8")
                        out.add(RemoteFile(name, curSize, ""))
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    override fun read(name: String): Pair<String, String?>? {
        val (code, body) = call("GET", folder + enc(name), null)
        if (code == 404) return null
        if (code !in 200..299) throw RuntimeException(describeError(code, body))
        return body to md5(body)
    }

    override fun revOf(name: String): String? {
        val r = read(name) ?: return null
        return r.second
    }

    override fun write(name: String, content: String, prevRev: String?): String {
        val (code, text) = call("PUT", folder + enc(name), content.toByteArray(Charsets.UTF_8), "application/json;charset=utf-8")
        if (code !in 200..299 && code != 204) throw RuntimeException(describeError(code, text))
        return md5(content)
    }
}

/**
 * GitHub 设备码授权登录（与 PC 端同一 OAuth App）：
 * 应用显示一个 8 位代码 → 打开浏览器 github.com/login/device → 登录输入代码点 Authorize → 自动拿到 Token。
 * 仅 GitHub 方式需要；Gitee 直接在设置里粘贴私人令牌，WebDAV 填账号密码。
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
