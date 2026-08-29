package com.joe.mepe.ui.settings

import android.content.Intent
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
import com.joe.mepe.data.GitHubSync
import com.joe.mepe.data.Repos
import com.joe.mepe.data.SyncConfig
import com.joe.mepe.ui.ColorPickerDialog
import com.joe.mepe.ui.ConfirmDialog
import com.joe.mepe.ui.LabeledField
import com.joe.mepe.ui.Routes
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.Segmented
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
                    ) { Text("＋", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    ) { Text("＋", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    var waterGoal by remember(rev) { mutableStateOf(Repos.getSetting("water_goal", "2000")) }
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
                    Repos.setSetting("water_goal", waterGoal)
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

@Composable
private fun SyncPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rev = DataBus.rev
    val syncConf = remember(rev) { SyncConfig.load(ctx) }
    var syncPat by remember(syncConf.pat) { mutableStateOf(syncConf.pat) }
    var syncRepo by remember(syncConf.repo) { mutableStateOf(syncConf.repo.ifBlank { "ME-Data" }) }
    var syncBranch by remember(syncConf.branch) { mutableStateOf(syncConf.branch.ifBlank { "main" }) }
    var syncAuto by remember(syncConf.autoPush) { mutableStateOf(syncConf.autoPush) }
    var syncing by remember { mutableStateOf(false) }
    var loginMsg by remember { mutableStateOf("") }
    var loggingIn by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "云同步", icon = Icons.Filled.CloudSync, subtitle = "GitHub 私有仓库 · PC ↔ 安卓互通", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 账号授权登录
            SectionCard(title = "GitHub 账号") {
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
                        loggingIn = true
                        loginMsg = "正在请求授权码…"
                        scope.launch {
                            try {
                                val s = GitHubLogin.start()
                                loginMsg = "浏览器即将打开，登录 GitHub 后输入代码 ${s.userCode}，点 Authorize 授权…"
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(s.verifyUrl)))
                                } catch (_: Exception) { }
                                var result: String? = null
                                while (isActive) {
                                    kotlinx.coroutines.delay(s.interval * 1000L)
                                    result = GitHubLogin.poll(s)
                                    if (result != null) break
                                    if (System.currentTimeMillis() > s.expiresAt) { result = "!授权超时，请重试"; break }
                                }
                                val r = result ?: "!授权超时"
                                if (r.startsWith("!")) {
                                    loginMsg = r
                                } else {
                                    val fresh = SyncConfig.load(ctx)
                                    fresh.pat = r
                                    fresh.account = GitHubLogin.fetchAccountName(r)
                                    SyncConfig.save(ctx, fresh)
                                    syncPat = r
                                    loginMsg = "✓ 授权成功，正在自动创建同步仓库 ME-Data…"
                                    loginMsg = try {
                                        val repo = GitHubSync.ensureRepo(ctx)
                                        "✓ 授权成功，已配置仓库 $repo，可直接上传/下载"
                                    } catch (e: Exception) {
                                        "✓ 授权成功，但自动建仓失败：${e.message}（可手动填仓库名）"
                                    }
                                }
                            } catch (e: Exception) {
                                loginMsg = "✗ ${e.message}"
                            }
                            loggingIn = false
                        }
                    },
                    enabled = !loggingIn,
                    shape = MaterialTheme.shapes.small
                ) { Text(if (loggingIn) "等待授权…" else "账号授权登录") }
                if (loginMsg.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(loginMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 同步设置
            SectionCard(title = "同步设置") {
                LabeledField("仓库名", syncRepo, { syncRepo = it }, placeholder = "ME-Data")
                Spacer(Modifier.height(4.dp))
                Text(
                    "只填仓库名即可：自动创建私有仓库并挂到你的账号下，无需填 owner/",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LabeledField("分支", syncBranch, { syncBranch = it }, placeholder = "main")
                Spacer(Modifier.height(8.dp))
                LabeledField("GitHub Token（PAT，可选）", syncPat, { syncPat = it }, placeholder = "已登录可留空")
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
                                val c = SyncConfig.load(ctx).also {
                                    it.pat = syncPat.trim(); it.repo = syncRepo.trim()
                                    it.branch = syncBranch.trim().ifBlank { "main" }; it.autoPush = syncAuto
                                }
                                SyncConfig.save(ctx, c)
                                msg = GitHubSync.push(ctx)
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
                                val c = SyncConfig.load(ctx).also {
                                    it.pat = syncPat.trim(); it.repo = syncRepo.trim()
                                    it.branch = syncBranch.trim().ifBlank { "main" }; it.autoPush = syncAuto
                                }
                                SyncConfig.save(ctx, c)
                                msg = GitHubSync.pull(ctx)
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
                val providers = Repos.aiProviders()
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
                            TextButton(onClick = { Repos.saveAiProviders(providers.filterNot { it.id == p.id }) }) {
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
                }, shape = MaterialTheme.shapes.small) { Text("＋ 添加供应商") }
                if (msg.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
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
}
