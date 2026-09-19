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
        // 用于检测「云端比本地新」避免覆盖别人/别的设备的更新（旧版单云端字段，保留兼容）
        var fileShas: Map<String, String> = emptyMap(),
        // 多云同步：每个云端各自的文件版本标识 / 上次上传成功时间 / 分支（GitHub=main、Gitee=master 互不干扰）
        var providerShas: Map<String, Map<String, String>> = emptyMap(),
        var providerLastPush: Map<String, String> = emptyMap(),
        var branches: Map<String, String> = emptyMap(),
        // 每个文件上次同步后的本地内容 MD5，用于下载时识别本机未上传的修改
        var fileHashes: Map<String, String> = emptyMap(),
        // 待处理的同步冲突（格式：云端键|文件名）。上传时「双方都改过且无法自动合并」会记在这里，等用户决定用本机还是云端
        var pendingConflicts: List<String> = emptyList(),
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
        // 旧版单云端配置迁移到多云结构：旧版本标识/上传时间/分支都归到当时所选的云端名下
        var migrated = false
        if (c.providerShas.isEmpty() && c.fileShas.isNotEmpty()) { c.providerShas = mapOf(c.provider to c.fileShas); migrated = true }
        if (c.providerLastPush.isEmpty() && c.lastPushAt.isNotBlank()) { c.providerLastPush = mapOf(c.provider to c.lastPushAt); migrated = true }
        if (c.branches.isEmpty() && c.branch.isNotBlank()) { c.branches = mapOf(c.provider to c.branch); migrated = true }
        if (migrated) save(context, c)
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

/** 追加型数据文件：双端冲突时按条目合并（Uid 去重），而不是跳过/覆盖 */
private val appendOnlyFiles = setOf("time_records", "focus_sessions", "task_completions", "health_records", "water_containers")

private fun objOf(el: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonObject? =
    el as? kotlinx.serialization.json.JsonObject

/** 取对象字段（大小写不敏感，兼容旧数据），返回 null 表示缺失/非字符串 */
private fun strOf(el: kotlinx.serialization.json.JsonElement, name: String): String? {
    val o = objOf(el) ?: return null
    val v = o.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value ?: return null
    return (v as? kotlinx.serialization.json.JsonPrimitive)?.content
}

/** 无 Uid 时的兜底去重键：打卡按任务+日期、健康按类型+日期、容器按名称、其余按原始内容完全相同 */
private fun mergeKey(el: kotlinx.serialization.json.JsonElement, file: String): String = when (file) {
    "task_completions" -> {
        val t = strOf(el, "TaskId"); val d = strOf(el, "Date")
        if (t != null && d != null) "t|$t|$d" else el.toString()
    }
    "health_records" -> {
        val t = strOf(el, "Type"); val d = strOf(el, "Date")
        if (t != null && d != null) "h|$t|$d" else el.toString()
    }
    "water_containers" -> strOf(el, "Name")?.let { "w|$it" } ?: el.toString()
    else -> el.toString()
}

/** 给对象替换/新增一个字符串字段（保留其它字段原样） */
private fun setProp(el: kotlinx.serialization.json.JsonElement, name: String, value: String): kotlinx.serialization.json.JsonElement {
    val o = objOf(el) ?: return el
    return buildJsonObject {
        o.forEach { (k, v) -> if (!k.equals(name, ignoreCase = true)) put(k, v) }
        put(name, value)
    }
}

/**
 * 合并两个追加型 JSON 数组（按 Uid 去重，无 Uid 走兜底键；旧条目自动补 Uid）。
 * 返回 (合并后的文本, 新增条目数)；本地无需变化时返回原文本。
 */
private fun mergeJson(localText: String, remoteText: String, file: String): Pair<String, Int> {
    fun parse(t: String): List<kotlinx.serialization.json.JsonElement>? = try {
        (JsonStore.json.parseToJsonElement(t) as? kotlinx.serialization.json.JsonArray)?.toList()
    } catch (_: Exception) { null }
    val local = parse(localText) ?: return localText to 0
    val remote = parse(remoteText) ?: return localText to 0
    if (remote.isEmpty()) return localText to 0
    if (local.isEmpty()) return remoteText to remote.size

    var result = local.toMutableList()
    var uidMigrated = false
    result.indices.forEach { i ->
        if (strOf(result[i], "Uid").isNullOrBlank()) {
            result[i] = setProp(result[i], "Uid", java.util.UUID.randomUUID().toString().replace("-", ""))
            uidMigrated = true
        }
    }
    val seen = HashSet<String>()
    var maxId = 0
    result.forEach { el ->
        val uid = strOf(el, "Uid")
        seen.add(if (!uid.isNullOrBlank()) "u|$uid" else mergeKey(el, file))
        (objOf(el)?.get("Id")?.toString()?.trim('"')?.toIntOrNull())?.let { if (it > maxId) maxId = it }
    }
    var added = 0
    for (rel in remote) {
        val uid = strOf(rel, "Uid")
        val key = if (!uid.isNullOrBlank()) "u|$uid" else mergeKey(rel, file)
        if (seen.contains(key)) continue
        var clone = rel
        if (strOf(clone, "Uid").isNullOrBlank())
            clone = setProp(clone, "Uid", java.util.UUID.randomUUID().toString().replace("-", ""))
        // Id 仅本地展示用：与本地冲突时重新分配，避免重复
        val cid = (objOf(clone)?.get("Id")?.toString()?.trim('"')?.toIntOrNull())
        if (cid != null && cid <= maxId) {
            maxId++
            clone = setProp(clone, "Id", maxId.toString())
        } else if (cid != null) {
            maxId = cid
        }
        seen.add("u|" + (strOf(clone, "Uid") ?: ""))
        result.add(clone)
        added++
    }
    if (added == 0 && !uidMigrated) return localText to 0
    return JsonStore.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(
        kotlinx.serialization.json.JsonElement.serializer()
    ), result) to added
}

object CloudSync {
    fun isAutoSyncEnabled(context: Context): Boolean = SyncConfig.load(context).autoPush

    /** 反馈提交目标仓库（项目 Issues，非用户的同步数据仓库）。反馈始终走 GitHub，与云同步方式无关 */
    private const val FEEDBACK_REPO = "nailao946/ME-PE"

    private fun backendFor(conf: SyncConfig.Conf, provider: String = conf.provider): CloudBackend = when (provider) {
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

    /** 已配置凭据、可参与同步的云端（多云同步：填好哪个的凭据就启用哪个，全部同时上传互为备份） */
    fun configuredProviders(conf: SyncConfig.Conf): List<String> = listOfNotNull(
        "github".takeIf { conf.pat.isNotBlank() },
        "gitee".takeIf { conf.giteePat.isNotBlank() },
        "webdav".takeIf { conf.webdavUser.isNotBlank() && conf.webdavPass.isNotBlank() },
    )

    private fun providerLabel(p: String) = when (p) { "gitee" -> "Gitee"; "webdav" -> "WebDAV"; else -> "GitHub" }

    /** 单云端同步结果：ok=该云端成功（含部分成功），text=给用户的明细 */
    private data class Outcome(val ok: Boolean, val text: String)

    /**
     * 上传 JsonData 全部文件到云端。多云同步：所有已配置的云端逐个上传，任一成功即有备份，
     * 某个失败不影响其他（下次上传会自动把失败的补齐）。
     * 防覆盖：按云端分别记录版本标识，并在缺少基线时用「内容哈希」比对云端与本地，
     * 全程不依赖文件时间；某文件在某个云端被别的设备改过时，该云端跳过此文件并提示先下载。
     */
    suspend fun push(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        val files = JsonStore.allFiles()
        if (files.isEmpty()) return@withContext "没有可上传的数据"
        val targets = configuredProviders(conf)
        if (targets.isEmpty()) return@withContext "✗ 请先在「设置 → 云同步」配置好同步账号（GitHub / Gitee / WebDAV）后再上传"
        val lines = mutableListOf<String>()
        val okNames = mutableListOf<String>()
        val badNames = mutableListOf<String>()
        for (p in targets) {
            val o = try {
                pushProvider(context, conf, p, files)
            } catch (e: Exception) {
                Outcome(false, "✗ ${e.message ?: "网络异常"}")
            }
            lines += "${providerLabel(p)}：${o.text}"
            if (o.ok) okNames += providerLabel(p) else badNames += providerLabel(p)
        }
        if (okNames.isNotEmpty()) {
            conf.lastPushAt = java.time.LocalDateTime.now().toString()
            SyncConfig.save(context, conf)
        }
        val head = when {
            okNames.isEmpty() -> "✗ 上传失败：${targets.size} 个云端均未成功"
            badNames.isEmpty() -> "✓ 上传完成：已上传到 ${okNames.joinToString("、")}"
            else -> "✓ 上传完成（部分云端未成功）：成功 ${okNames.joinToString("、")}；未成功 ${badNames.joinToString("、")}"
        }
        (listOf(head) + lines).joinToString("\n")
    }

    /** 上传到单个云端 */
    private suspend fun pushProvider(context: Context, conf: SyncConfig.Conf, provider: String, files: List<File>): Outcome {
        if (provider == "github") GitHubLogin.maybeRefresh(context, conf)
        val backend = backendFor(conf, provider)
        backend.ensureReady(context)
        val known = conf.providerShas[provider].orEmpty().toMutableMap()
        val newHashes = conf.fileHashes.toMutableMap()
        var okCount = 0
        var skipped = 0
        var mergedCount = 0
        var localMerged = false
        var lastErr: String? = null
        for (f in files) {
            try {
                val baseName = f.name.removeSuffix(".json")
                val localText = f.readText()
                val localHash = md5(localText)
                val rev = backend.revOf(f.name)
                // 「最新版本」判定：不依赖文件时间，用本云端基线 + 内容哈希双向比较
                val baseline = known[f.name]
                var remoteNewer = baseline != null && rev != null && baseline != rev
                if (baseline == null && rev != null) {
                    // 本机没有该云端的基线（首次上传到这个云端）：读回云端内容比对哈希，
                    // 内容不同说明云端有别的设备留下、本机没下载过的数据 → 不能盲写覆盖
                    val probe = backend.read(f.name)?.first
                    if (probe != null && md5(probe) != localHash) remoteNewer = true
                } else if (remoteNewer) {
                    // 版本标识对不上也可能是「记账不准」（云端返回的 sha 缺失/格式差异），
                    // 用内容哈希复核一次：内容其实一致就正常上传并刷新基线，避免永远卡在「云端较新」
                    val probe = backend.read(f.name)?.first
                    if (probe != null && md5(probe) == localHash) remoteNewer = false
                }
                if (remoteNewer) {
                    if (appendOnlyFiles.contains(baseName)) {
                        val remoteText = backend.read(f.name)?.first
                        if (remoteText != null) {
                            val merged = mergeJson(localText, remoteText, baseName)
                            if (merged.first != localText) { f.writeText(merged.first); localMerged = true }
                            known[f.name] = backend.write(f.name, merged.first, rev)
                            mergedCount += merged.second
                            okCount++
                            newHashes[f.name] = md5(f.readText())
                        }
                    } else {
                        skipped++
                        val entry = "$provider|${f.name}"
                        if (entry !in conf.pendingConflicts) conf.pendingConflicts = conf.pendingConflicts + entry
                        continue
                    }
                } else {
                    known[f.name] = backend.write(f.name, localText, rev)
                    newHashes[f.name] = localHash
                    okCount++
                    conf.pendingConflicts = conf.pendingConflicts.filterNot { it == "$provider|${f.name}" }
                }
                // Git 两家 API 有频率限制：文件之间留最小间隔，降低触发限流概率
                if (provider == "github" || provider == "gitee") Thread.sleep(250)
            } catch (e: Exception) { lastErr = e.message }
        }
        if (okCount > 0 || skipped > 0) {
            conf.providerShas = conf.providerShas + (provider to known.toMap())
            conf.providerLastPush = conf.providerLastPush + (provider to java.time.LocalDateTime.now().toString())
            conf.fileHashes = newHashes.toMap()
            SyncConfig.save(context, conf)
        }
        if (localMerged) DataBus.bump()
        val detail = buildString {
            if (mergedCount > 0) append("；合并 $mergedCount 条")
            if (skipped > 0) append("；$skipped 个云端较新已跳过（请先下载）")
            lastErr?.let { append("；错误：$it") }
        }
        return when {
            okCount == 0 && skipped > 0 -> Outcome(false, "✗ 云端有 $skipped 个文件比本地新，已全部跳过（请先「下载数据」再上传）$detail")
            okCount == 0 -> Outcome(false, "✗ ${lastErr ?: "上传失败"}")
            else -> Outcome(true, "✓ 已上传 $okCount/${files.size} 个$detail")
        }
    }

    /**
     * 从所有已配置云端下载（追加型文件自动合并，其余文件以"最近上传成功"的云端优先、其余云端仅补漏），
     * 每个云端独立汇报成功/失败原因。写盘前校验是有效 JSON，损坏内容不会写进本地数据。
     * 版本判定不使用文件时间：以「云端内容哈希 vs 本地内容哈希 vs 上次同步基线」三向比较决定谁更新。
     */
    suspend fun pull(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        val targets = configuredProviders(conf)
        if (targets.isEmpty()) return@withContext "✗ 请先在「设置 → 云同步」配置好同步账号（GitHub / Gitee / WebDAV）后再下载"
        val ordered = targets.sortedByDescending { conf.providerLastPush[it] ?: "" }
        val alreadyDownloaded = mutableSetOf<String>()
        val lines = mutableListOf<String>()
        val okNames = mutableListOf<String>()
        val badNames = mutableListOf<String>()
        var anyData = false
        for (p in ordered) {
            val o = try {
                pullProvider(context, conf, p, alreadyDownloaded)
            } catch (e: Exception) {
                Outcome(false, "✗ ${e.message ?: "网络异常"}")
            }
            lines += "${providerLabel(p)}：${o.text}"
            if (o.ok) {
                okNames += providerLabel(p)
                if (o.text.contains("下载") || o.text.contains("合并") || o.text.contains("保留")) anyData = true
            } else badNames += providerLabel(p)
        }
        if (anyData) {
            conf.lastPullAt = java.time.LocalDateTime.now().toString()
            SyncConfig.save(context, conf)
            DataBus.bump()
        }
        val head = when {
            okNames.isEmpty() -> "✗ 下载失败：${ordered.size} 个云端均未成功"
            badNames.isEmpty() -> "✓ 下载完成：已从 ${okNames.joinToString("、")} 取到数据"
            else -> "✓ 下载完成（部分云端未成功）：成功 ${okNames.joinToString("、")}；未成功 ${badNames.joinToString("、")}"
        }
        (listOf(head) + lines).joinToString("\n")
    }

    private suspend fun pullProvider(context: Context, conf: SyncConfig.Conf, provider: String, alreadyDownloaded: MutableSet<String>): Outcome {
        if (provider == "github") GitHubLogin.maybeRefresh(context, conf)
        val backend = backendFor(conf, provider)
        backend.ensureReady(context)

        // 目录清单：一次拿到每个文件的名字、大小与版本标识
        val items: List<RemoteFile> = try {
            backend.list()
        } catch (e: Exception) {
            if (e.message?.contains("404") == true) emptyList() else throw e
        }
        if (items.isEmpty()) return Outcome(true, "✓ 无数据（云端目录为空）")

        // 本地备份（全云端共用一次）
        val backupDir = File(context.filesDir, "JsonData_backup_${System.currentTimeMillis()}")
        backupDir.mkdirs()
        JsonStore.allFiles().forEach { it.copyTo(File(backupDir, it.name), overwrite = true) }

        var n = 0
        var mergedCount = 0
        var keptCount = 0
        var sameCount = 0
        var lastErr: String? = null
        val failed = mutableListOf<String>()
        val newShas = conf.providerShas[provider].orEmpty().toMutableMap()
        val currentLocalHashes = conf.fileHashes.toMutableMap()
        for (item in items) {
            try {
                val fileName = item.name.removeSuffix(".json")
                // 追加型文件：每个云端都合并；其余文件：只从第一个（最近上传成功的）云端下载，后续云端不重复覆盖
                if (!appendOnlyFiles.contains(fileName) && item.name in alreadyDownloaded) continue
                if (!item.name.endsWith(".json")) continue
                val localFile = File(JsonStore.dir, item.name)
                val localText = if (localFile.exists()) localFile.readText() else null
                val localHash = localText?.let { md5(it) }

                val r = backend.read(item.name)
                val t = r?.first
                if (t == null) { failed.add(item.name); lastErr = "文件内容为空"; continue }
                // 校验是有效 JSON 再写入，防止把传输损坏的内容存成本地数据
                try { JsonStore.json.parseToJsonElement(t) } catch (_: Exception) {
                    failed.add(item.name); lastErr = "下载内容不是有效 JSON"; continue
                }
                val remoteHash = md5(t)

                if (appendOnlyFiles.contains(fileName) && localText != null) {
                    // 追加型：始终按条目合并，任何一端都不会丢数据
                    val merged = mergeJson(localText, t, fileName)
                    if (merged.first != localText) { localFile.writeText(merged.first); mergedCount += merged.second }
                    n++
                    alreadyDownloaded.add(item.name)
                    newShas[item.name] = item.rev.ifBlank { r.second ?: remoteHash }
                    currentLocalHashes[item.name] = md5(localFile.readText())
                    if (provider == "github" || provider == "gitee") Thread.sleep(250)
                    continue
                }

                // 其余文件：三向比较（云端哈希 / 本地哈希 / 上次同步基线），不依赖文件时间
                if (localText != null && localHash == remoteHash) {
                    sameCount++
                    alreadyDownloaded.add(item.name)
                    newShas[item.name] = item.rev.ifBlank { r.second ?: remoteHash }
                    currentLocalHashes[item.name] = localHash!!
                    continue
                }
                // 本地自上次同步后改过（有未上传的修改）而云端内容不同 → 保留本地，避免未上传数据被覆盖
                val baselineHash = conf.fileHashes[item.name]
                if (localText != null && baselineHash != null && baselineHash != localHash) {
                    keptCount++
                    continue
                }

                localFile.writeText(t)
                n++
                alreadyDownloaded.add(item.name)
                newShas[item.name] = item.rev.ifBlank { r.second ?: remoteHash }
                currentLocalHashes[item.name] = md5(localFile.readText())
                if (provider == "github" || provider == "gitee") Thread.sleep(250)
            } catch (e: Exception) {
                failed.add(item.name)
                lastErr = e.message
            }
        }
        if (n > 0 || mergedCount > 0 || sameCount > 0) {
            conf.providerShas = conf.providerShas + (provider to newShas.toMap())
            conf.fileHashes = currentLocalHashes.toMap()
            SyncConfig.save(context, conf)
        }
        val extra = buildString {
            if (mergedCount > 0) append("；合并 $mergedCount 条")
            if (keptCount > 0) append("；保留本地未上传 $keptCount 个（先上传再下载即可同步）")
            if (sameCount > 0) append("；$sameCount 个内容一致无需下载")
        }
        val failText = if (failed.isNotEmpty())
            "；未成功：${failed.take(4).joinToString("、")}${if (failed.size > 4) "等${failed.size}个文件" else ""}" +
                (lastErr?.let { "（$it）" } ?: "")
        else lastErr?.let { "（$it）" } ?: ""
        val summary = "下载 $n/${items.size} 个$extra$failText"
        return if (failed.isEmpty()) Outcome(true, "✓ $summary（原数据已备份）")
        else Outcome(n > 0, "⚠ $summary")
    }

    /**
     * 连接诊断：逐个云端走「连接 → 列目录 → 读第一个文件」，给出每步状态与耗时。
     * 上传/下载失败时先跑一遍，能把「令牌失效 / 仓库不存在 / 限流 / 网络」区分开。
     */
    suspend fun diagnose(context: Context): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        val lines = mutableListOf("诊断结果：")
        for (key in listOf("github", "gitee", "webdav")) {
            if (key !in configuredProviders(conf)) { lines += "• ${providerLabel(key)}：未配置，跳过"; continue }
            val start = System.currentTimeMillis()
            try {
                val backend = backendFor(conf, key)
                val target = backend.ensureReady(context)
                val t1 = System.currentTimeMillis() - start
                val items = try { backend.list() } catch (e: Exception) {
                    if (e.message?.contains("404") == true) emptyList() else throw e
                }
                val t2 = System.currentTimeMillis() - start
                if (items.isEmpty()) {
                    lines += "✓ ${providerLabel(key)}：$target｜连接 ${t1}ms｜列目录 ${t2 - t1}ms｜云端还没有数据目录"
                } else {
                    val first = items.first()
                    val text = backend.read(first.name)?.first
                    val t3 = System.currentTimeMillis() - start
                    lines += "✓ ${providerLabel(key)}：$target｜连接 ${t1}ms｜列目录 ${t2 - t1}ms（${items.size} 个文件）｜读 ${first.name} ${t3 - t2}ms（${text?.length ?: 0} 字符）"
                }
            } catch (e: Exception) {
                lines += "✗ ${providerLabel(key)}：${e.message ?: "网络异常"}（耗时 ${System.currentTimeMillis() - start}ms）"
            }
        }
        if (conf.pendingConflicts.isNotEmpty())
            lines += "⚠ 有 ${conf.pendingConflicts.size} 个冲突等待处理（点「处理冲突」选择用本机还是云端）"
        lines.joinToString("\n")
    }

    /** 当前待处理的冲突条目（provider|文件名） */
    fun pendingConflicts(context: Context): List<String> = SyncConfig.load(context).pendingConflicts

    /**
     * 处理同步冲突：preferCloud=true 用云端覆盖本机，false 用本机覆盖云端。
     * file/provider 都为空时处理全部；处理完刷新基线并从待处理清单移除，避免下次同步再报。
     */
    suspend fun resolveConflicts(context: Context, preferCloud: Boolean, provider: String? = null, file: String? = null): String = withContext(Dispatchers.IO) {
        val conf = SyncConfig.load(context)
        val targets = conf.pendingConflicts.filter { entry ->
            val p = entry.substringBefore('|')
            val f = entry.substringAfter('|')
            (provider == null || p == provider) && (file == null || f == file)
        }
        if (targets.isEmpty()) return@withContext "没有待处理的冲突"

        var ok = 0
        val errors = mutableListOf<String>()
        val files = JsonStore.allFiles()
        for (entry in targets) {
            val p = entry.substringBefore('|')
            val name = entry.substringAfter('|')
            try {
                val backend = backendFor(conf, p)
                backend.ensureReady(context)
                val localFile = File(JsonStore.dir, name)
                if (preferCloud) {
                    val r = backend.read(name) ?: throw RuntimeException("云端已没有这个文件")
                    try { JsonStore.json.parseToJsonElement(r.first) } catch (_: Exception) {
                        throw RuntimeException("云端内容不是有效 JSON")
                    }
                    localFile.writeText(r.first)
                    val known = conf.providerShas[p].orEmpty().toMutableMap()
                    known[name] = r.second ?: md5(r.first)
                    conf.providerShas = conf.providerShas + (p to known)
                    conf.fileHashes = conf.fileHashes + (name to md5(r.first))
                } else {
                    val local = files.firstOrNull { it.name == name } ?: throw RuntimeException("本机已没有这个文件")
                    val text = local.readText()
                    val rev = backend.revOf(name)
                    val known = conf.providerShas[p].orEmpty().toMutableMap()
                    known[name] = backend.write(name, text, rev).ifBlank { md5(text) }
                    conf.providerShas = conf.providerShas + (p to known)
                    conf.fileHashes = conf.fileHashes + (name to md5(text))
                }
                conf.pendingConflicts = conf.pendingConflicts - entry
                ok++
            } catch (e: Exception) {
                errors += "$name（${providerLabel(p)}）：${e.message ?: "失败"}"
            }
        }
        if (ok > 0) SyncConfig.save(context, conf)
        DataBus.bump()
        val head = "已处理 $ok/${targets.size} 个冲突（${if (preferCloud) "采用云端" else "采用本机"}）"
        if (errors.isEmpty()) head else "$head；失败：${errors.joinToString("；")}"
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
    private val providerKey = if (isGitee) "gitee" else "github"
    // 分支按云端独立记忆（多云同步时 GitHub=main 与 Gitee=master 互不干扰）
    private val branch get() = conf.branches[providerKey]?.takeUnless { it.isBlank() }
        ?: (if (isGitee) "master" else "main")
    private var ctx: Context? = null

    /**
     * 在已配置分支上执行请求；若该分支在云端不存在（404）自动换另一个常用分支（main ↔ master）重试一次，
     * 成功后记住可用的分支。Gitee 与 GitHub 新仓库的默认分支并不一致，缺了这层兜底会让所有文件请求 404。
     */
    private fun <T> onBranch(action: (String) -> T): T = try {
        action(branch)
    } catch (e: Exception) {
        if (e.message?.contains("404") != true) throw e
        val alt = if (branch == "master") "main" else "master"
        val r = action(alt)
        conf.branches = conf.branches + (providerKey to alt)
        conf.branch = alt
        ctx?.let { SyncConfig.save(it, conf) }
        r
    }

    /** 统一错误文案：401 = 令牌在云端侧已失效（被撤销或过期），引导重新配置 */
    private fun describeError(code: Int, text: String): String = when {
        code == 401 && isGitee -> "Gitee 令牌已失效（被撤销或已过期），请在「设置 → 云同步」重新填写私人令牌"
        code == 401 -> "GitHub 授权已失效（令牌被撤销或已过期），请重新授权登录一次即可恢复"
        code == 403 && isGitee -> "Gitee 请求过于频繁被限流（HTTP 403），已自动重试一次仍失败，请稍等几分钟再试"
        code == 404 && isGitee -> "Gitee 找不到 $owner/$repoName 的 data 目录（HTTP 404）：请确认私人令牌勾选了 projects 权限，且该仓库确实存在"
        code == 404 -> "GitHub 找不到 $owner/$repoName 的 data 目录（HTTP 404）"
        code == 400 && isGitee -> "Gitee 拒绝了请求（HTTP 400）：${text.take(200)}"
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

    /** 发请求并返回原始响应体；非 2xx 抛异常（Gitee 限流 403 自动退避重试一次） */
    private fun call(url: String, method: String, body: String?, accept: String = "application/vnd.github+json"): String {
        var last: RuntimeException? = null
        repeat(if (isGitee && method != "GET") 2 else 1) { attempt ->
            try {
                http.newCall(request(url, method, body, accept)).execute().use { r ->
                    val text = r.body?.string() ?: ""
                    if (!r.isSuccessful) throw RuntimeException(describeError(r.code, text))
                    return text
                }
            } catch (e: RuntimeException) {
                if (attempt == 0 && isGitee && e.message?.contains("403") == true) {
                    last = e
                    try { Thread.sleep(3000) } catch (_: InterruptedException) { }
                } else throw e
            }
        }
        throw last ?: RuntimeException("请求失败")
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
    /** owner：用户填了 owner/name 就用填的，否则用当前对应云端的登录账号 */
    private val owner get() = if (conf.repo.contains('/')) conf.repo.substringBefore('/')
        else if (isGitee) conf.giteeAccount else conf.account
    private fun dataUrl(path: String) = "$api/repos/${enc(owner)}/$repoName/contents/$path"

    override fun ensureReady(context: Context): String {
        ctx = context
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

        // 分支按云端独立记忆：首次用到该云端时记下默认分支（Gitee 新仓库默认 master）
        if (conf.branches[providerKey].isNullOrBlank()) conf.branches = conf.branches + (providerKey to branch)
        conf.branch = branch // 兼容旧字段展示
        SyncConfig.save(context, conf)
        return "$login/$name"
    }

    override fun list(): List<RemoteFile> {
        val text = onBranch { b -> get(dataUrl("data?ref=${enc(b)}"), "application/vnd.github+json") }
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
            onBranch { b -> get(dataUrl("data/${enc(name)}?ref=${enc(b)}"), "application/vnd.github.raw") }
        } catch (_: Exception) { null } else null
        // 兜底（Gitee 的唯一路径）：JSON 接口 + Base64 解码
        var rev: String? = null
        if (text == null) {
            val detail = onBranch { b -> parseObj(call(dataUrl("data/${enc(name)}?ref=${enc(b)}"), "GET", null)) }
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
        onBranch { b -> parseObj(call(dataUrl("data/${enc(name)}?ref=${enc(b)}"), "GET", null))["sha"]?.toString()?.trim('"') }
    } catch (e: Exception) {
        // 只有「文件确实不存在」才当作新建。401/403/限流等错误必须向上抛：
        // 否则会被误判成「云端没有这个文件」而反复走新建接口，最终报「文件已存在」
        if (e.message?.contains("404") == true) null else throw e
    }

    override fun write(name: String, content: String, prevRev: String?): String {
        fun body(sha: String?, br: String): String = buildJsonObject {
            put("message", "ME 数据同步（Android）· ${java.time.LocalDateTime.now()}")
            put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
            put("branch", br)
            if (sha != null) put("sha", sha)
        }.toString()
        val path = dataUrl("data/${enc(name)}")
        // Gitee 与 GitHub 不同：PUT 是纯「更新」接口，不带 sha 一律 400 sha is missing（即使文件不存在），
        // 新建文件必须走 POST；撞上已存在（本地版本记录缺失）时取最新 sha 转更新。GitHub 的 PUT 兼容新建+更新，维持原行为
        val resp: JsonObject = if (prevRev != null || !isGitee) {
            onBranch { b -> parseObj(call(path, "PUT", body(prevRev, b))) }
        } else try {
            onBranch { b -> parseObj(call(path, "POST", body(null, b))) }
        } catch (e: RuntimeException) {
            val m = e.message.orEmpty()
            // Gitee 新建接口在文件已存在时可能返回 400 / 409，取最新 sha 转为更新
            if (!(m.contains("存在") || m.contains("exist", ignoreCase = true) ||
                    m.contains("400") || m.contains("409"))) throw e
            val fresh = revOf(name)
            if (fresh.isNullOrBlank()) throw e
            onBranch { b -> parseObj(call(path, "PUT", body(fresh, b))) }
        }
        // 版本标识：优先取响应里的 content.sha，缺失时回读一次云端 sha。
        // 绝不能返回空串——空串会让下次上传永远判定「云端较新」而卡住
        val sha = (resp["content"] as? JsonObject)?.get("sha")?.toString()?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?: resp["sha"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
        if (!sha.isNullOrBlank()) return sha
        return revOf(name)?.takeIf { it.isNotBlank() } ?: md5(content)
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
        code == 409 -> "HTTP 409：目标文件夹在云端无法就位（自动创建未生效或请求过于频繁——坚果云免费版每 30 分钟限约 600 个请求），请稍后重试，或在坚果云客户端手动建好目标文件夹"
        else -> "HTTP $code：${text.take(300)}"
    }

    private fun call(method: String, url: String, body: ByteArray?, contentType: String? = null): Pair<Int, String> {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                val req = Request.Builder().url(url)
                    .header("Authorization", authHeader)
                    .header("User-Agent", "ME-PE")
                    .method(method, (body ?: ByteArray(0)).toRequestBody(contentType?.toMediaType()))
                    .build()
                http.newCall(req).execute().use { r ->
                    val text = r.body?.string() ?: ""
                    return r.code to text
                }
            } catch (e: Exception) {
                // 移动网络链路不稳/超时：重试一次
                last = e
                if (attempt == 0) try { Thread.sleep(1000) } catch (_: InterruptedException) { }
            }
        }
        throw last ?: RuntimeException("请求失败")
    }

    override fun ensureReady(context: Context): String {
        if (conf.repo.isBlank()) conf.repo = "ME-Data"
        ensureDirs()
        SyncConfig.save(context, conf)
        return folder
    }

    /**
     * 逐级创建同步目录。坚果云等 WebDAV 服务不会隐式建父目录：父级缺失时 MKCOL/PUT
     * 一律 409（实测坚果云返回 <s:exception>AncestorsNotFound</s:exception>）。
     * 旧版把 MKCOL 的 409 当「可继续」，目录没建成照样上传 → 每个文件都 409 失败。
     */
    private fun ensureDirs() {
        val root = Regex("^(https?://[^/]+)", RegexOption.IGNORE_CASE).find(base)?.groupValues?.get(1)
            ?: throw RuntimeException("WebDAV 服务器地址无效，请检查（坚果云为 https://dav.jianguoyun.com/dav/）")
        var path = base.removePrefix(root).trimEnd('/')
        // folder = base + 各级目录（enc 过），从 base 之后逐级 MKCOL
        for (seg in folder.removePrefix(base).split('/').filter { it.isNotBlank() }) {
            path += "/" + seg
            var r = call("MKCOL", root + path, null)
            if (r.first == 409) { // 坚果云最终一致：刚建好的上级目录偶发立刻查不到，稍等重试一次
                Thread.sleep(800)
                r = call("MKCOL", root + path, null)
            }
            // 201 = 已创建；405/301/200 = 目录已存在，均可继续
            if (r.first != 201 && r.first != 405 && r.first != 301 && r.first != 200)
                throw RuntimeException("创建 WebDAV 目录失败：" + describeError(r.first, r.second))
        }
    }

    override fun list(): List<RemoteFile> {
        val (code, body) = call("PROPFIND", folder, "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:getcontentlength/></d:prop></d:propfind>".toByteArray(), "application/xml")
        if (code == 404) return emptyList()
        if (code !in 200..299 && code != 207) throw RuntimeException(describeError(code, body))

        // 解析 multistatus XML：每个 <response> 里的 <href> 与 <getcontentlength>。
        // 不同服务端返回的前缀不同（d:/D:/无前缀），统一取去掉前缀后的本地名再比对
        val out = mutableListOf<RemoteFile>()
        val parser = android.util.Xml.newPullParser()
        parser.setInput(java.io.StringReader(body))
        val local = { n: String -> n.substringAfter(':') }
        var curHref: String? = null
        var curSize = 0L
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (local(parser.name ?: "")) {
                    "response" -> { curHref = null; curSize = 0L }
                    "href" -> try { curHref = parser.nextText() } catch (_: Exception) { }
                    "getcontentlength" -> try { curSize = parser.nextText()?.trim()?.toLongOrNull() ?: 0L } catch (_: Exception) { }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> if (local(parser.name ?: "") == "response" && curHref != null) {
                    val href = curHref!!
                    // 跳过目录本身（以 / 结尾）与子目录，只留 .json 文件
                    if (!href.endsWith("/") && href.endsWith(".json")) {
                        val raw = href.substringAfterLast('/')
                        val name = try { URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
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
        val body = content.toByteArray(Charsets.UTF_8)
        val url = folder + enc(name)
        var (code, text) = call("PUT", url, body, "application/json;charset=utf-8")
        if (code == 409) {
            // 目标目录在云端缺失（坚果云 AncestorsNotFound）或最终一致延迟：重建目录后重试一次
            ensureDirs()
            val r = call("PUT", url, body, "application/json;charset=utf-8")
            code = r.first; text = r.second
        }
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
