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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** 设置：外观 / 目标 / 备份 / 云同步 / AI / 自定义模块教程 / 关于 */
@Composable
fun SettingsScreen(nav: (String) -> Unit) {
    val ctx = LocalContext.current
    val rev = DataBus.rev
    var themeMode by remember(rev) { mutableStateOf(Repos.getSetting("theme_mode", "system")) }
    var accent by remember(rev) { mutableStateOf(Repos.getSetting("accent_color", "蓝色").ifBlank { "蓝色" }) }
    var iconColor by remember(rev) { mutableStateOf(Repos.getSetting("icon_color", "auto").ifBlank { "auto" }) }
    var waterGoal by remember(rev) { mutableStateOf(Repos.getSetting("water_goal", "2000")) }
    var sedentaryGoal by remember(rev) { mutableStateOf(Repos.getSetting("sedentary_goal", "8")) }

    val syncConf = remember(rev) { SyncConfig.load(ctx) }
    var syncPat by remember(syncConf.pat) { mutableStateOf(syncConf.pat) }
    var syncRepo by remember(syncConf.repo) { mutableStateOf(syncConf.repo) }
    var syncBranch by remember(syncConf.branch) { mutableStateOf(syncConf.branch) }
    var syncAuto by remember(syncConf.autoPush) { mutableStateOf(syncConf.autoPush) }
    var syncing by remember { mutableStateOf(false) }
    var loginMsg by remember { mutableStateOf("") }
    var loggingIn by remember { mutableStateOf(false) }
    var loginCode by remember { mutableStateOf("") }

    var aiName by remember { mutableStateOf("") }
    var aiKey by remember { mutableStateOf("") }
    var aiUrl by remember { mutableStateOf("https://api.deepseek.com") }
    var aiModel by remember { mutableStateOf("deepseek-chat") }
    var confirmClear by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var showAccentPicker by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "设置",
            icon = Icons.Filled.Settings,
            subtitle = "个性化与数据管理",
            onBack = { nav(Routes.BACK) }
        )

        // ===== 外观 =====
        SectionCard(title = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("外观", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Segmented(listOf("跟随系统", "浅色", "深色"), when (themeMode) { "light" -> 1; "dark" -> 2; else -> 0 }) { i ->
                themeMode = when (i) { 1 -> "light"; 2 -> "dark"; else -> "system" }
                Repos.setSetting("theme_mode", themeMode)
            }
            Spacer(Modifier.height(10.dp))
            Text("强调色", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Accents.forEach { (name, color) ->
                    val active = accent == name
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(30.dp)
                            .background(color, CircleShape)
                            .clickable { accent = name; Repos.setSetting("accent_color", name) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (active) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                if (accent.startsWith("#")) {
                    Box(Modifier.size(30.dp).background(parseHexColor(accent, MaterialTheme.colorScheme.primary), CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                OutlinedButton(onClick = { showAccentPicker = true }, shape = MaterialTheme.shapes.small) { Text("自定义…") }
            }
            Spacer(Modifier.height(10.dp))
            Text("图标颜色（全部页面的单色图标）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconColorChoices.forEach { (name, hex) ->
                    val active = iconColor == hex
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(30.dp)
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
                OutlinedButton(onClick = { showIconPicker = true }, shape = MaterialTheme.shapes.small) { Text("自定义…") }
            }
        }

        // ===== 目标设定 =====
        SectionCard(title = "目标设定") {
            LabeledField("每日喝水目标（ml）", waterGoal, { waterGoal = it })
            Spacer(Modifier.height(6.dp))
            LabeledField("每日起身活动目标（次）", sedentaryGoal, { sedentaryGoal = it })
            TextButton(onClick = {
                Repos.setSetting("water_goal", waterGoal)
                Repos.setSetting("sedentary_goal", sedentaryGoal)
                msg = "✓ 已保存"
            }) { Text("保存目标") }
        }

        // ===== 备份与恢复 =====
        SectionCard(title = "备份与恢复（与桌面版互通）") {
            Text(
                "导出为 zip / 导入桌面端备份 zip（me_backup_*.zip 或 JsonData 打包）",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                Button(onClick = { exportLauncher.launch(BackupManager.defaultFileName()) }, shape = MaterialTheme.shapes.small) { Text("导出备份") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, shape = MaterialTheme.shapes.small) { Text("导入备份") }
            }
        }

        // ===== GitHub 云同步 =====
        SectionCard(title = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudSync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("云同步（GitHub 免费私有仓库）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "把数据上传到自己的 GitHub 私有仓库实现 PC / 安卓免费云同步。Token 需要 repo（Contents 读写）权限。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LabeledField("仓库（owner/name）", syncRepo, { syncRepo = it }, placeholder = "如：nailao946/ME-Data")
            Spacer(Modifier.height(6.dp))
            LabeledField("分支", syncBranch, { syncBranch = it }, placeholder = "main")
            Spacer(Modifier.height(6.dp))
            LabeledField("GitHub Token（PAT）", syncPat, { syncPat = it }, placeholder = "ghp_xxxx")
            Spacer(Modifier.height(10.dp))
            // 账号授权登录（GitHub Device Flow）：跳网页登录输代码点允许，自动拿 Token
            Text(
                if (syncConf.pat.isNotBlank())
                    (if (syncConf.account.isNotBlank()) "已登录：${syncConf.account}" else "已登录 GitHub 账号")
                else "未登录 GitHub 账号",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        loggingIn = true
                        loginMsg = "正在请求授权码…"
                        loginCode = ""
                        scope.launch {
                            try {
                                val s = GitHubLogin.start()
                                loginCode = s.userCode
                                loginMsg = "浏览器即将打开，登录 GitHub 后输入代码 ${s.userCode}，点 Authorize 授权，然后等在这里…"
                                try {
                                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(s.verifyUrl)))
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
                                    loginMsg = "✓ 授权成功，Token 已自动保存"
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
            }
            if (loginMsg.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(loginMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow("自动上传", syncAuto, { syncAuto = it }, sub = "每次修改数据后自动推送到仓库")
            Spacer(Modifier.height(6.dp))
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
            if (syncConf.lastPushAt.isNotBlank()) Text("上次上传：${syncConf.lastPushAt.take(19).replace('T', ' ')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (syncConf.lastPullAt.isNotBlank()) Text("上次下载：${syncConf.lastPullAt.take(19).replace('T', ' ')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ===== 自定义模块 =====
        SectionCard(title = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Extension, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("自定义模块（可扩展记录块）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "像「健康」一样，创建属于自己的记录块：定义任意字段（数值 / 文本 / 时间 / 是否 / 单选）并每天记一笔，自动出历史和趋势图，PC 与安卓互通。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "教程：进入「管理模块」→ 点「＋ 新建模块」→ 填名称、选颜色和图标 → 添加字段（如「跑步」类型数值，单位 km）→ 保存。之后在模块卡片上点「记一笔」即可录入，点「历史」看趋势。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { nav(Routes.MODULES) }, shape = MaterialTheme.shapes.small) { Text("管理模块") }
        }

        // ===== AI 分析 =====
        SectionCard(title = "AI 分析（OpenAI 兼容）") {
            val providers = Repos.aiProviders()
            if (providers.isNotEmpty()) {
                providers.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.name}${if (p.isDefault) "（默认）" else ""}", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            Text("${p.model} · ${p.baseUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Spacer(Modifier.height(6.dp))
            LabeledField("供应商名称", aiName, { aiName = it }, placeholder = "如：DeepSeek")
            LabeledField("API Key", aiKey, { aiKey = it })
            LabeledField("Base URL", aiUrl, { aiUrl = it })
            LabeledField("模型", aiModel, { aiModel = it })
            TextButton(onClick = {
                if (aiName.isBlank() || aiKey.isBlank()) { msg = "✗ 名称和 Key 必填"; return@TextButton }
                Repos.addAiProvider(
                    com.joe.mepe.data.AiProvider(name = aiName.trim(), encryptedApiKey = aiKey.trim(),
                        baseUrl = aiUrl.trim(), model = aiModel.trim(), isDefault = providers.isEmpty())
                )
                aiName = ""; aiKey = ""
                msg = "✓ 已添加供应商"
            }) { Text("＋ 添加供应商") }
        }

        // ===== 危险操作 =====
        SectionCard(title = "危险操作") {
            TextButton(onClick = { confirmClear = true }) { Text("清空全部本地数据", color = MaterialTheme.colorScheme.error) }
        }

        // ===== 关于 =====
        SectionCard(title = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("关于", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text("目标地图 PE（ME PE）v2.0.0", fontWeight = FontWeight.SemiBold)
            Text(
                "个人目标管理 & 健康追踪 · 纯本地存储，可选 GitHub 云同步",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "数据与桌面版（WPF）互通：桌面端备份 zip 可直接导入；备份 zip 亦可作为迁移媒介。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (msg.isNotBlank()) {
            Text(msg, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
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
    if (confirmClear) {
        ConfirmDialog("清空数据", "确定删除本机全部 JSON 数据吗？此操作不可恢复！", {
            BackupManager.clearAll()
            confirmClear = false
            msg = "✓ 已清空"
        }, { confirmClear = false })
    }
}
