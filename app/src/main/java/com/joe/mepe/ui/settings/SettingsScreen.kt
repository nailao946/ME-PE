package com.joe.mepe.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.joe.mepe.data.BackupManager
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.GitHubLogin
import com.joe.mepe.data.CloudSync
import com.joe.mepe.data.UpdateChecker
import com.joe.mepe.data.Repos
import com.joe.mepe.data.SyncConfig
import com.joe.mepe.ui.ColorPickerDialog
import com.joe.mepe.ui.ConfirmDialog
import com.joe.mepe.ui.LabeledField
import com.joe.mepe.ui.Routes
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.Segmented
import com.joe.mepe.ui.SyncStatusBus
import com.joe.mepe.ui.ToggleRow
import com.joe.mepe.ui.theme.Accents
import com.joe.mepe.ui.theme.IconColorChoices
import com.joe.mepe.ui.theme.colorToHex
import com.joe.mepe.ui.theme.parseHexColor
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** 设置：微信式分类入口（主页一行一个大类，点进去是该类的设置子页） */
@Composable
fun SettingsScreen(nav: (String) -> Unit) {
    var page by rememberSaveable { mutableStateOf("") }

    // 系统返回逐级退出：在分类子页（关于/外观/云同步…）先退回设置首页，再由外层退回主界面
    BackHandler(enabled = page.isNotBlank()) { page = "" }

    if (page.isBlank()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "设置",
                icon = Icons.Filled.Settings,
                subtitle = "个性化与数据管理",
                onBack = { nav(Routes.BACK) }
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
            ) {
                SectionCard(title = null) {
                    SettingRow(Icons.Filled.Palette, "外观", "主题模式 · 强调色 · 图标颜色", Color(0xFF7C5CE0)) { page = "appearance" }
                    SettingRow(Icons.Filled.Favorite, "健康目标", "每日喝水 · 起身活动", Color(0xFF4FC3F7)) { page = "goals" }
                    SettingRow(Icons.Filled.CloudSync, "云同步", "GitHub 私有仓库，PC ↔ 安卓互通", Color(0xFF2E9E5B)) { page = "sync" }
                    SettingRow(Icons.Filled.Backup, "备份与恢复", "导出 / 导入 zip，与桌面版互通", Color(0xFFE0A93C)) { page = "backup" }
                }
                SectionCard(title = null) {
                    SettingRow(Icons.Filled.SmartToy, "AI 分析", "OpenAI 兼容供应商配置", Color(0xFFE05C8A)) { page = "ai" }
                    SettingRow(Icons.Filled.Extension, "自定义模块", "可扩展记录块 · 使用教程", Color(0xFF4F6EF7)) { page = "modules" }
                    SettingRow(Icons.Filled.Info, "关于", "版本 · 反馈", Color(0xFF8A8F9E)) { page = "about" }
                }
            }
        }
    } else {
        when (page) {
            "appearance" -> AppearancePage { page = "" }
            "goals" -> GoalsPage { page = "" }
            "sync" -> SyncPage { page = "" }
            "backup" -> BackupPage { page = "" }
            "ai" -> AiPage { page = "" }
            "modules" -> ModulesPage(nav) { page = "" }
            "about" -> AboutPage { page = "" }
        }
    }
}

// ============ 通用组件 ============

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).background(tint.copy(alpha = 0.15f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
    }
}

// ============ 外观 ============

@Composable
private fun AppearancePage(onBack: () -> Unit) {
    val rev = DataBus.rev
    var themeMode by remember(rev) { mutableStateOf(Repos.getSetting("theme_mode", "system")) }
    var accent by remember(rev) { mutableStateOf(Repos.getSetting("accent_color", "蓝色").ifBlank { "蓝色" }) }
    var iconColor by remember(rev) { mutableStateOf(Repos.getSetting("icon_color", "auto").ifBlank { "auto" }) }
    var showAccentPicker by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "外观", icon = Icons.Filled.Palette, subtitle = "主题与配色", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionCard(title = "主题模式") {
                Segmented(listOf("跟随系统", "浅色", "深色"), when (themeMode) { "light" -> 1; "dark" -> 2; else -> 0 }) { i ->
                    themeMode = when (i) { 1 -> "light"; 2 -> "dark"; else -> "system" }
                    Repos.setSetting("theme_mode", themeMode)
                }
            }
            SectionCard(title = "强调色") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Accents.forEach { (name, color) ->
                        val active = accent == name
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(color, CircleShape)
                                .clickable { accent = name; Repos.setSetting("accent_color", name) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (active) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (accent.startsWith("#")) {
                        Box(
                            Modifier.size(34.dp)
                                .background(parseHexColor(accent, MaterialTheme.colorScheme.primary), CircleShape)
                                .clickable { }
                        )
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
                            .clickable { showAccentPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, "添加", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
            SectionCard(title = "图标颜色（全部页面的单色图标）") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconColorChoices.forEach { (name, hex) ->
                        val active = iconColor == hex
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(
                                    if (hex == "auto") MaterialTheme.colorScheme.primary else parseHexColor(hex, MaterialTheme.colorScheme.primary),
                                    CircleShape
                                )
                                .clickable { iconColor = hex; Repos.setSetting("icon_color", hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (active) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            if (hex == "auto") Text("A", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
                            .clickable { showIconPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, "添加", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAccentPicker) {
        ColorPickerDialog(
            title = "自定义强调色",
            initial = parseHexColor(accent, MaterialTheme.colorScheme.primary),
            onPick = { accent = colorToHex(it); Repos.setSetting("accent_color", accent); showAccentPicker = false },
            onDismiss = { showAccentPicker = false }
        )
    }
    if (showIconPicker) {
        ColorPickerDialog(
            title = "自定义图标颜色",
            initial = parseHexColor(iconColor, MaterialTheme.colorScheme.primary),
            onPick = { iconColor = colorToHex(it); Repos.setSetting("icon_color", iconColor); showIconPicker = false },
            onDismiss = { showIconPicker = false }
        )
    }
}

// ============ 健康目标 ============

@Composable
private fun GoalsPage(onBack: () -> Unit) {
    val rev = DataBus.rev
    var waterGoal by remember(rev) { mutableStateOf(Repos.getWaterGoal()) }
    var sedentaryGoal by remember(rev) { mutableStateOf(Repos.getSetting("sedentary_goal", "8")) }
    var msg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "健康目标", icon = Icons.Filled.Favorite, subtitle = "每日目标设定", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionCard(title = "每日目标") {
                LabeledField("每日喝水目标（ml）", waterGoal, { waterGoal = it })
                Spacer(Modifier.height(8.dp))
                LabeledField("每日起身活动目标（次）", sedentaryGoal, { sedentaryGoal = it })
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    Repos.setWaterGoal(waterGoal)
                    Repos.setSetting("sedentary_goal", sedentaryGoal)
                    msg = "✓ 已保存"
                }, shape = MaterialTheme.shapes.small) { Text("保存目标") }
                if (msg.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============ 云同步（GitHub） ============

private fun copyText(ctx: android.content.Context, text: String) {
    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("ME", text))
}

@Composable
private fun SyncPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rev = DataBus.rev
    val syncConf = remember(rev) { SyncConfig.load(ctx) }
    var syncProvider by remember(syncConf.provider) { mutableStateOf(syncConf.provider.ifBlank { "github" }) }
    var syncPat by remember(syncConf.pat) { mutableStateOf(syncConf.pat) }
    var syncGiteePat by remember(syncConf.giteePat) { mutableStateOf(syncConf.giteePat) }
    var syncWebDavUrl by remember(syncConf.webdavUrl) { mutableStateOf(syncConf.webdavUrl) }
    var syncWebDavUser by remember(syncConf.webdavUser) { mutableStateOf(syncConf.webdavUser) }
    var syncWebDavPass by remember(syncConf.webdavPass) { mutableStateOf(syncConf.webdavPass) }
    var syncRepo by remember(syncConf.repo) { mutableStateOf(syncConf.repo.ifBlank { "ME-Data" }) }
    var syncBranch by remember(syncConf.branch) { mutableStateOf(syncConf.branch.ifBlank { "main" }) }
    var syncAuto by remember(syncConf.autoPush) { mutableStateOf(syncConf.autoPush) }
    var syncing by remember { mutableStateOf(false) }
    var loginMsg by remember { mutableStateOf("") }
    var loggingIn by remember { mutableStateOf(false) }
    var pendingCode by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    /** 把页面上的输入写进配置：登录/上传/下载前都要先调用，云端操作读取的是落盘配置 */
    fun saveConf() {
        val c = SyncConfig.load(ctx).also {
            it.provider = syncProvider
            it.pat = syncPat.trim()
            it.giteePat = syncGiteePat.trim()
            it.webdavUrl = syncWebDavUrl.trim()
            it.webdavUser = syncWebDavUser.trim()
            it.webdavPass = syncWebDavPass.trim()
            it.repo = syncRepo.trim()
            it.branch = syncBranch.trim().ifBlank { if (syncProvider == "gitee") "master" else "main" }
            it.autoPush = syncAuto
        }
        SyncConfig.save(ctx, c)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "云同步", icon = Icons.Filled.CloudSync, subtitle = "GitHub / Gitee / WebDAV · PC ↔ 安卓互通", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 同步方式
            SectionCard(title = "同步方式") {
                Segmented(listOf("GitHub", "Gitee", "WebDAV"), when (syncProvider) { "gitee" -> 1; "webdav" -> 2; else -> 0 }) { idx ->
                    syncProvider = when (idx) { 1 -> "gitee"; 2 -> "webdav"; else -> "github" }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when (syncProvider) {
                        "gitee" -> "Gitee（国内直连快）：到 gitee.com → 设置 → 安全设置 → 私人令牌 生成令牌（勾选 projects 与 user_info），粘贴到下方即可。"
                        "webdav" -> "WebDAV（坚果云 / Nextcloud / 群晖等）：坚果云在网页版「账户信息 → 安全选项 → 添加应用密码」生成密码，注意不能用登录密码。"
                        else -> "GitHub（推荐）：跳转 GitHub 网页点一次授权即可，自动创建私有仓库 ME-Data，一次登录长期有效。"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 账号配置（按所选同步方式显示对应表单）
            when (syncProvider) {
                "gitee" -> SectionCard(title = "Gitee 账号") {
                    if (syncConf.giteeAccount.isNotBlank())
                        Text("已配置：${syncConf.giteeAccount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    LabeledField("私人令牌", syncGiteePat, { syncGiteePat = it }, placeholder = "gitee.com → 设置 → 私人令牌")
                }
                "webdav" -> SectionCard(title = "WebDAV 账号") {
                    LabeledField("服务器地址", syncWebDavUrl, { syncWebDavUrl = it }, placeholder = "https://dav.jianguoyun.com/dav/")
                    Spacer(Modifier.height(8.dp))
                    LabeledField("账号", syncWebDavUser, { syncWebDavUser = it }, placeholder = "坚果云为注册手机号/邮箱")
                    Spacer(Modifier.height(8.dp))
                    LabeledField("密码 / 应用密码", syncWebDavPass, { syncWebDavPass = it })
                }
                else -> SectionCard(title = "GitHub 账号") {
                    Text(
                        if (syncConf.pat.isNotBlank())
                            (if (syncConf.account.isNotBlank()) "已登录：${syncConf.account}" else "已登录 GitHub 账号")
                        else "未登录 GitHub 账号",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "跳转 GitHub 网页登录并点允许即可，自动获取 Token 并创建私有仓库 ME-Data，一次登录长期有效。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            // 先把当前页面配置落盘，保证授权成功后自动建仓走的是 GitHub 方式
                            saveConf()
                            loggingIn = true
                            pendingCode = ""
                            loginMsg = "正在请求授权码…"
                            scope.launch {
                                try {
                                    val s = GitHubLogin.start()
                                    pendingCode = s.userCode
                                    try {
                                        copyText(ctx, s.userCode)
                                        Toast.makeText(ctx, "授权码 ${s.userCode} 已复制到剪贴板", Toast.LENGTH_LONG).show()
                                    } catch (_: Exception) { }
                                    loginMsg = "浏览器即将打开，登录 GitHub 后粘贴授权码 ${s.userCode}，点 Authorize 授权…"
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(s.verifyUrl)))
                                    } catch (_: Exception) { }
                                    var result: String? = null
                                    var failures = 0
                                    while (isActive) {
                                        // 比要求的最小间隔多留 2 秒余量（网络往返有抖动，掐得太准会被 GitHub 判定轮询过快触发 slow_down 限流）；
                                        // 网络异常后额外多等 3 秒再重试，给网络恢复留时间
                                        kotlinx.coroutines.delay(s.interval * 1000L + 2000L + (if (failures > 0) 3000L else 0L))
                                        result = try {
                                            val r = GitHubLogin.poll(s)
                                            if (failures > 0) loginMsg = "网络已恢复，继续等待授权…"
                                            failures = 0
                                            r
                                        } catch (_: Exception) {
                                            // 网络抖动/切后台导致的解析失败不打死流程，静默重试直到授权码过期
                                            failures++
                                            if (failures == 3) loginMsg = "网络不稳定（域名解析失败），正在自动重试，请保持网络畅通…"
                                            null
                                        }
                                        if (result != null) break
                                        if (failures >= 30) { result = "!无法连接 GitHub：域名持续解析失败，请检查网络或换一个网络（如手机热点）后重试"; break }
                                        if (System.currentTimeMillis() > s.expiresAt) { result = "!授权超时，请重试"; break }
                                    }
                                    val r = result ?: "!授权超时"
                                    if (r.startsWith("!")) {
                                        loginMsg = r
                                    } else {
                                        // 先立即落盘 token——拉取用户名（api.github.com）可能很慢甚至超时，不能拖住登录完成
                                        val fresh = SyncConfig.load(ctx)
                                        fresh.pat = r
                                        fresh.refreshToken = s.refreshToken
                                        fresh.tokenExpiresAt = s.tokenExpiresAt
                                        SyncConfig.save(ctx, fresh)
                                        syncPat = r
                                        loginMsg = "✓ 授权成功，正在自动创建同步仓库 ME-Data…"
                                        loginMsg = try {
                                            val repo = CloudSync.ensureRepo(ctx)
                                            "✓ 授权成功，已配置仓库 $repo，可直接上传/下载"
                                        } catch (e: Exception) {
                                            "✓ 授权成功，但自动建仓失败：${e.message}（可手动填仓库名）"
                                        }
                                        // 账号名仅用于显示，后台补拉，失败不影响登录
                                        val acc = GitHubLogin.fetchAccountName(r)
                                        if (acc.isNotBlank()) {
                                            val f2 = SyncConfig.load(ctx)
                                            f2.account = acc
                                            SyncConfig.save(ctx, f2)
                                            DataBus.bump()   // 刷新页面上的「已登录」显示
                                        }
                                    }
                                } catch (e: java.net.UnknownHostException) {
                                    loginMsg = "✗ 无法连接 GitHub：域名解析失败。请检查网络，或换一个网络（如手机热点）后重试"
                                } catch (e: Exception) {
                                    loginMsg = "✗ ${e.message}"
                                }
                                loggingIn = false
                            }
                        },
                        enabled = !loggingIn,
                        shape = MaterialTheme.shapes.small
                    ) { Text(if (loggingIn) "等待授权…" else "账号授权登录") }
                    if (loggingIn && pendingCode.isNotBlank()) {
                        TextButton(onClick = {
                            try {
                                copyText(ctx, pendingCode)
                                Toast.makeText(ctx, "授权码 $pendingCode 已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) { }
                        }) { Text("再次复制授权码 $pendingCode") }
                    }
                    if (loginMsg.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(loginMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 同步设置
            SectionCard(title = "同步设置") {
                LabeledField(if (syncProvider == "webdav") "文件夹名" else "仓库名", syncRepo, { syncRepo = it }, placeholder = "ME-Data")
                Spacer(Modifier.height(4.dp))
                Text(
                    when (syncProvider) {
                        "webdav" -> "云盘里存放数据的文件夹名，默认 ME-Data，一般不用改"
                        "gitee" -> "只填仓库名即可：自动创建私有仓库并挂到你的账号下，无需填 owner/"
                        else -> "只填仓库名即可：自动创建私有仓库并挂到你的账号下，无需填 owner/"
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (syncProvider != "webdav") {
                    Spacer(Modifier.height(8.dp))
                    LabeledField("分支", syncBranch, { syncBranch = it }, placeholder = if (syncProvider == "gitee") "master" else "main")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "GitHub 默认 main，Gitee 默认 master；切换同步方式后如上传报分支错误，改成对应默认分支即可",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (syncProvider == "github") {
                    Spacer(Modifier.height(8.dp))
                    LabeledField("GitHub Token（PAT，可选）", syncPat, { syncPat = it }, placeholder = "已登录可留空")
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow("自动上传", syncAuto, { syncAuto = it }, sub = "每次修改数据后自动推送到仓库")
                if (syncConf.lastPushAt.isNotBlank())
                    Text("上次上传：${syncConf.lastPushAt.take(19).replace('T', ' ')}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (syncConf.lastPullAt.isNotBlank())
                    Text("上次下载：${syncConf.lastPullAt.take(19).replace('T', ' ')}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 上传 / 下载
            SectionCard(title = "上传 / 下载") {
                Text(
                    "上传会覆盖云端；若云端有比本地更新的文件会自动跳过并提示（先下载即可拿到最新）。下载会先备份本机数据。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            syncing = true
                            scope.launch {
                                saveConf()
                                SyncStatusBus.setRunning("正在上传…")
                                msg = try { CloudSync.push(ctx) } catch (e: Exception) { "✗ 上传失败：" + (e.message ?: "网络异常") }
                                SyncStatusBus.report(msg)
                                syncing = false
                            }
                        },
                        enabled = !syncing,
                        shape = MaterialTheme.shapes.small
                    ) { Text(if (syncing) "上传中…" else "上传数据") }
                    OutlinedButton(
                        onClick = {
                            syncing = true
                            scope.launch {
                                saveConf()
                                SyncStatusBus.setRunning("正在下载…")
                                msg = try { CloudSync.pull(ctx) } catch (e: Exception) { "✗ 下载失败：" + (e.message ?: "网络异常") }
                                SyncStatusBus.report(msg)
                                syncing = false
                            }
                        },
                        enabled = !syncing,
                        shape = MaterialTheme.shapes.small
                    ) { Text(if (syncing) "下载中…" else "下载数据") }
                }
                if (msg.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============ 备份与恢复 ============

@Composable
private fun BackupPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val ok = ctx.contentResolver.openOutputStream(uri)?.use { out ->
                val tmp = File(ctx.cacheDir, BackupManager.defaultFileName())
                if (BackupManager.exportTo(tmp)) { tmp.inputStream().use { it.copyTo(out) }; tmp.delete(); true } else false
            } ?: false
            msg = if (ok) "✓ 备份已导出" else "✗ 导出失败"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val n = ctx.contentResolver.openInputStream(uri)?.use { BackupManager.importFrom(it) } ?: 0
            msg = if (n > 0) "✓ 导入 $n 个数据文件" else "✗ 导入失败（需要桌面端备份 zip）"
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "备份与恢复", icon = Icons.Filled.Backup, subtitle = "本地备份 · 与桌面版互通", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionCard(title = "导出 / 导入") {
                Text(
                    "导出为 zip / 导入桌面端备份 zip（me_backup_*.zip 或 JsonData 打包）。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch(BackupManager.defaultFileName()) }, shape = MaterialTheme.shapes.small) { Text("导出备份") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, shape = MaterialTheme.shapes.small) { Text("导入备份") }
                }
                if (msg.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            SectionCard(title = "危险操作") {
                TextButton(onClick = { confirmClear = true }) { Text("清空全部本地数据", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClear) {
        ConfirmDialog("清空数据", "确定删除本机全部 JSON 数据吗？此操作不可恢复！", {
            BackupManager.clearAll()
            confirmClear = false
            msg = "✓ 已清空"
        }, { confirmClear = false })
    }
}

// ============ AI 分析 ============

@Composable
private fun AiPage(onBack: () -> Unit) {
    val rev = DataBus.rev
    // 在 AiPage 作用域按 rev 缓存供应商列表：rev 变化（增删/设默认）时拿到新列表实例，
    // 下面的 SectionCard 内容 lambda 因捕获了它必然重组——之前把读取写在 SectionCard 内部，
    // lambda 没有任何状态捕获被复用，删除后界面不刷新，要退出页面重进才能看到
    val providers = remember(rev) { Repos.aiProviders() }
    var deleteTarget by remember { mutableStateOf<com.joe.mepe.data.AiProvider?>(null) }
    var aiName by remember { mutableStateOf("") }
    var aiKey by remember { mutableStateOf("") }
    var aiUrl by remember { mutableStateOf("https://api.deepseek.com") }
    var aiModel by remember { mutableStateOf("deepseek-chat") }
    var aiFormat by remember { mutableStateOf(0) } // 0=OpenAI 兼容 1=Anthropic
    var msg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "AI 分析", icon = Icons.Filled.SmartToy, subtitle = "OpenAI / Anthropic 供应商", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionCard(title = "供应商") {
                if (providers.isEmpty()) {
                    Text("还没有供应商，添加一个即可在健康 → 对比 使用 AI 分析",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    providers.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${p.name}${if (p.isDefault) "（默认）" else ""}", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${if (p.apiFormat == 1) "Anthropic" else "OpenAI 兼容"} · ${p.model} · ${p.baseUrl}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!p.isDefault) TextButton(onClick = {
                                Repos.saveAiProviders(providers.map { it.copy(isDefault = it.id == p.id) })
                            }) { Text("设为默认") }
                            TextButton(onClick = { deleteTarget = p }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            SectionCard(title = "添加供应商") {
                LabeledField("供应商名称", aiName, { aiName = it }, placeholder = "如：DeepSeek / Claude")
                Spacer(Modifier.height(8.dp))
                Text("API 格式", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Segmented(listOf("OpenAI 兼容", "Anthropic"), aiFormat) { aiFormat = it }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (aiFormat == 1) "Anthropic：请求 地址/v1/messages，如 https://api.anthropic.com，模型如 claude-sonnet-4-5"
                    else "OpenAI 兼容：请求 地址/chat/completions，如 https://api.deepseek.com",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LabeledField("API Key", aiKey, { aiKey = it })
                Spacer(Modifier.height(6.dp))
                LabeledField("Base URL", aiUrl, { aiUrl = it })
                Spacer(Modifier.height(6.dp))
                LabeledField("模型", aiModel, { aiModel = it })
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (aiName.isBlank() || aiKey.isBlank()) { msg = "✗ 名称和 Key 必填"; return@Button }
                    Repos.addAiProvider(
                        com.joe.mepe.data.AiProvider(name = aiName.trim(), encryptedApiKey = aiKey.trim(),
                            baseUrl = aiUrl.trim(), model = aiModel.trim(), apiFormat = aiFormat,
                            isDefault = Repos.aiProviders().isEmpty())
                    )
                    aiName = ""; aiKey = ""
                    msg = "✓ 已添加供应商"
                }, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加供应商")
                }
                if (msg.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 删除供应商二次确认（防误触）
    deleteTarget?.let { target ->
        ConfirmDialog(
            "删除供应商",
            "确定删除「${target.name}」吗？删除后使用它的 AI 分析将不可用。",
            {
                Repos.saveAiProviders(providers.filterNot { it.id == target.id })
                deleteTarget = null
            },
            { deleteTarget = null }
        )
    }
}

// ============ 自定义模块 ============

@Composable
private fun ModulesPage(nav: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "自定义模块", icon = Icons.Filled.Extension, subtitle = "可扩展记录块", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionCard(title = "这是什么") {
                Text(
                    "像「健康」一样，创建属于自己的记录块：定义任意字段（数值 / 文本 / 时间 / 是否 / 单选）并每天记一笔，自动出历史和趋势图，PC 与安卓互通。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionCard(title = "使用教程") {
                Text(
                    "进入「管理模块」→ 点「＋ 新建模块」→ 填名称、选颜色和图标 → 添加字段（如「跑步」类型数值，单位 km）→ 保存。之后在模块卡片上点「记一笔」即可录入，点「历史」看趋势。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = { nav(Routes.MODULES) }, shape = MaterialTheme.shapes.small) { Text("管理模块") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============ 关于 ============

@Composable
private fun AboutPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var showFeedback by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun openUrl(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) { }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "关于", icon = Icons.Filled.Info, subtitle = "ME", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionCard(title = null) {
                Text("ME（个人管理系统）v${com.joe.mepe.BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "个人目标管理 & 健康追踪 · 纯本地存储，可选 GitHub 云同步",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "数据与桌面版（WPF）互通：桌面端备份 zip 可直接导入；GitHub 云同步可直接在两端互传。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionCard(title = "检查更新") {
                var checking by remember { mutableStateOf(false) }
                var result by remember { mutableStateOf<UpdateChecker.Result?>(null) }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !checking) {
                        checking = true
                        scope.launch {
                            result = UpdateChecker.check(com.joe.mepe.BuildConfig.VERSION_NAME)
                            checking = false
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (checking) "正在检查…" else "点击检查是否有新版本",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium
                    )
                    if (!checking) Text(
                        when {
                            result?.error != null -> "检查失败"
                            result?.hasUpdate == true -> "有新版本"
                            result != null -> "已是最新"
                            else -> "v" + com.joe.mepe.BuildConfig.VERSION_NAME
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                    )
                }
                result?.let { r ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            r.error != null -> r.error
                            r.hasUpdate -> "发现新版本 v${r.latest}（当前 v${r.current}），点此前往下载"
                            else -> "已是最新版本 v${r.latest}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (r.hasUpdate) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = if (r.hasUpdate) Modifier.clickable { openUrl(r.releaseUrl) } else Modifier
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "对比 GitHub Releases 发布的最新版本（预发布也算，版本号从 tag/标题/附件文件名提取）",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionCard(title = "提意见 / 反馈 Bug") {
                Row(
                    Modifier.fillMaxWidth().clickable { showFeedback = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✍ 写下建议或问题", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "提交到 GitHub Issues",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "使用「云同步」已绑定的 GitHub 账号，反馈将作为 Issue 提交到项目仓库",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionCard(title = "项目主页") {
                Row(
                    Modifier.fillMaxWidth().clickable { openUrl("https://github.com/nailao946/ME-PE") },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⭐ GitHub（安卓端）", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "github.com/nailao946/ME-PE",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clickable { openUrl("https://github.com/nailao946/ME") },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💻 GitHub（桌面端）", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "github.com/nailao946/ME",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "点击跳转浏览器打开，欢迎 Star 与反馈建议",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showFeedback) {
        var text by remember { mutableStateOf("") }
        var submitting by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!submitting) showFeedback = false }) {
            androidx.compose.material3.Card {
                Column(Modifier.padding(16.dp)) {
                    Text("提意见 / 反馈 Bug", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "将作为 Issue 提交到 github.com/nailao946/ME-PE（用「云同步」绑定的账号）；第一行会自动作为标题。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                        placeholder = { Text("写下你的建议或遇到的问题…") },
                        enabled = !submitting,
                        maxLines = 10,
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(
                            onClick = { showFeedback = false },
                            enabled = !submitting
                        ) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                submitting = true; error = null
                                scope.launch {
                                    try {
                                        val n = CloudSync.submitFeedback(ctx, text)
                                        showFeedback = false
                                        Toast.makeText(ctx, "已提交 Issue #$n，感谢反馈！", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        error = e.message ?: "提交失败，请检查网络后重试"
                                    }
                                    submitting = false
                                }
                            },
                            enabled = !submitting && text.isNotBlank()
                        ) { Text(if (submitting) "提交中…" else "提交") }
                    }
                }
            }
        }
    }
}
