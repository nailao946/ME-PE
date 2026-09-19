package com.joe.mepe.ui.modules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.joe.mepe.data.HtmlLibraryRepository
import com.joe.mepe.data.CustomModule
import com.joe.mepe.data.CustomModuleField
import com.joe.mepe.data.CustomModuleRecord
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.Repos
import com.joe.mepe.ui.ColorDot
import com.joe.mepe.ui.ColorPickerDialog
import com.joe.mepe.ui.ConfirmDialog
import com.joe.mepe.ui.DatePickerDialog
import com.joe.mepe.ui.EmptyHint
import com.joe.mepe.ui.FormDialog
import com.joe.mepe.ui.LabeledField
import com.joe.mepe.ui.LineChart
import com.joe.mepe.ui.QuickLinks
import com.joe.mepe.ui.Routes
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.Segmented
import com.joe.mepe.ui.Stepper
import com.joe.mepe.ui.TimeField
import com.joe.mepe.ui.ToggleRow
import com.joe.mepe.ui.rememberData
import com.joe.mepe.ui.theme.LocalIconColor
import com.joe.mepe.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 模块图标集（与桌面端约定同一顺序，Icon 字段存索引） */
val ModuleIconList: List<ImageVector> = listOf(
    Icons.Filled.Favorite, Icons.Filled.FitnessCenter, Icons.Filled.DirectionsRun, Icons.Filled.WaterDrop,
    Icons.Filled.Bedtime, Icons.Filled.Mood, Icons.Filled.MenuBook, Icons.Filled.School,
    Icons.Filled.Work, Icons.Filled.Home, Icons.Filled.ShoppingCart, Icons.Filled.LocalCafe,
    Icons.Filled.SelfImprovement, Icons.Filled.MusicNote, Icons.Filled.Pets, Icons.Filled.Book,
)

private val fieldTypes = listOf("number", "text", "time", "bool", "select")
private val fieldTypeNames = listOf("数值", "文本", "时间", "是否", "单选")

/** 自定义模块管理页：创建 / 编辑 / 记一笔 / 历史 */
@Composable
fun ModulesScreen(nav: (String) -> Unit) {
    var editing by remember { mutableStateOf<CustomModule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<CustomModule?>(null) }
    var historyOf by remember { mutableStateOf<CustomModule?>(null) }
    var libraryOf by remember { mutableStateOf<CustomModule?>(null) }
    var deleteTarget by remember { mutableStateOf<CustomModule?>(null) }

    val modules = rememberData { Repos.customModules().filter { !it.isDeleted } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "自定义模块",
            icon = Icons.Filled.Extension,
            subtitle = "创建你自己的记录块（PC / 安卓互通）",
            onBack = { nav(Routes.BACK) },
            actions = { QuickLinks(Routes.SETTINGS, nav) }
        )

        if (modules.isEmpty()) {
            EmptyHint("还没有模块。像「健康」一样，你可以创建任意记录块：\n例如「跑步」记数值 km、「日记」记文本、「喝咖啡」记杯数。", Icons.Filled.Extension)
        }
        modules.forEach { m ->
            ModuleFeishuCard(
                m = m,
                onRecord = { recording = m },
                onHistory = { historyOf = m },
                onLibrary = { libraryOf = m },
                onEdit = { editing = m },
                onDelete = { deleteTarget = m },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Button(onClick = { creating = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建模块", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (creating || editing != null) {
        ModuleEditDialog(initial = editing, onClose = { creating = false; editing = null })
    }
    recording?.let { m -> ModuleRecordDialog(m, onClose = { recording = null }) }
    historyOf?.let { m -> ModuleHistoryDialog(m, onClose = { historyOf = null }, onEditRecord = { mod, rec -> }) }
    libraryOf?.let { m -> HtmlLibraryDialog(m, onClose = { libraryOf = null }) }
    deleteTarget?.let { m ->
        ConfirmDialog("删除模块", "确定删除「${m.name}」及其全部 ${m.records.size} 条记录吗？", {
            Repos.deleteCustomModule(m.id)
            deleteTarget = null
        }, { deleteTarget = null })
    }
}

/**
 * 模块卡片（飞书卡片风格）：16dp 大圆角白卡 + 1dp 细描边；
 * 头部 = 主题色圆角图标块 + 标题 + 副信息 + 记录数徽标；
 * 中间 = 最近一条记录摘要；底部细分割线 + 图标文字按钮操作区，右侧放次要操作。
 */
@Composable
private fun ModuleFeishuCard(
    m: CustomModule,
    onRecord: () -> Unit,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = parseHexColor(m.colorHex, MaterialTheme.colorScheme.primary)
    val shape = RoundedCornerShape(16.dp)
    val last = m.records.maxWithOrNull(
        compareBy<CustomModuleRecord> { it.date }.thenBy { it.time }.thenBy { it.id }
    )
    val fieldSummary = m.fields.joinToString(" · ") { f ->
        f.label + (f.unit?.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "")
    }.ifBlank { "暂无字段" }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            // ---- 头部：图标块 + 标题/副信息 + 记录数 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        ModuleIconList.getOrElse(m.icon) { Icons.Filled.Extension },
                        null, tint = color, modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(fieldSummary, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.12f), contentColor = color) {
                    Text(
                        "${m.records.size} 条",
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
                    )
                }
            }

            // ---- 最近一条记录摘要 ----
            if (last != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("最近记录 · ${last.date}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            last.values.entries.joinToString(" · ") { e ->
                                val f = m.fields.find { it.key == e.key }
                                "${f?.label ?: e.key} ${e.value}${f?.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}"
                            }.ifBlank { "（无字段值）" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // ---- 细分割线 ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            Spacer(Modifier.height(8.dp))

            // ---- 操作区：主操作在左，次要操作靠右 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                FeishuTextAction(Icons.Filled.Add, "记一笔", color, onRecord)
                Spacer(Modifier.width(4.dp))
                FeishuTextAction(Icons.Filled.Extension, "资料库", MaterialTheme.colorScheme.onSurfaceVariant, onLibrary)
                Spacer(Modifier.width(4.dp))
                FeishuTextAction(Icons.Filled.MenuBook, "历史", MaterialTheme.colorScheme.onSurfaceVariant, onHistory)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** 飞书卡片底部动作：小图标 + 文字，整块可点 */
@Composable
private fun FeishuTextAction(
    icon: ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .background(Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

// ============ 模块编辑 ============

/** 快速模板：一键填好常用模块（图标 / 颜色 / 字段），与桌面端 ModulePresets 同一套 */
private data class ModulePreset(
    val name: String, val icon: Int, val color: String,
    val fields: List<Triple<String, String, String?>>,
)

private val modulePresets = listOf(
    ModulePreset("跑步", 2, "#FF6B6B", listOf(Triple("距离", "number", "km"), Triple("路线", "text", null), Triple("体感", "select", null))),
    ModulePreset("喝水", 3, "#5AC8FA", listOf(Triple("杯数", "number", "杯"))),
    ModulePreset("体重", 1, "#8E8E93", listOf(Triple("体重", "number", "kg"))),
    ModulePreset("阅读", 6, "#AF52DE", listOf(Triple("页数", "number", "页"), Triple("状态", "select", null))),
    ModulePreset("日记", 15, "#4F6EF7", listOf(Triple("标题", "text", null), Triple("心情", "select", null))),
    ModulePreset("睡眠", 4, "#2E9E5B", listOf(Triple("时长", "number", "小时"), Triple("入睡", "time", null))),
)

/** 预设色盘（与桌面端 ColorPresets 一致） */
private val moduleColorPresets = listOf(
    "#4F6EF7", "#2E9E5B", "#7C5CE0", "#E05C8A", "#E0883C", "#2BA8A8",
    "#FF6B6B", "#5AC8FA", "#AF52DE", "#E0A93C", "#8E8E93", "#1C1C1E",
)
private fun colorSafe(hex: String): Color = parseHexColor(hex, Color(0xFF4F6EF7))
private fun autoKey(label: String): String = "f_${label.hashCode().toString().take(6)}"

@Composable
fun ModuleEditDialog(initial: CustomModule?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: "#4F6EF7") }
    var iconIdx by remember { mutableStateOf(initial?.icon ?: 0) }
    // 字段必须用不可变列表整体替换：原地改元素（fields[i]=…）Compose 检测不到变化，
    // 会整屏"冻结"——打字/点按钮都没反应，直到别的状态变化才一次性刷出来
    var fields by remember {
        mutableStateOf(
            initial?.fields?.map { it.copy() }
                ?: listOf(CustomModuleField(key = "value", label = "数值", type = "number", unit = ""))
        )
    }
    fun updateField(i: Int, f: CustomModuleField) { fields = fields.toMutableList().also { it[i] = f } }
    fun removeField(i: Int) { fields = fields.toMutableList().also { it.removeAt(i) } }
    fun moveField(i: Int, delta: Int) {
        val target = i + delta
        if (target < 0 || target >= fields.size) return
        val list = fields.toMutableList()
        val f = list.removeAt(i)
        list.add(target, f)
        fields = list
    }
    fun addField() {
        fields = fields + CustomModuleField(key = "f${System.currentTimeMillis() % 100000}", label = "", type = "number")
    }
    var pickingColor by remember { mutableStateOf(false) }
    val accent = colorSafe(colorHex)

    FormDialog(title = if (initial == null) "新建模块" else "编辑模块", onClose = onClose) {
        // —— 实时预览：飞书卡片头部，随名称 / 图标 / 颜色即时变化 ——
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        ModuleIconList.getOrElse(iconIdx) { Icons.Filled.Extension },
                        null, tint = accent, modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (name.isBlank()) "模块名称" else name,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("记录会显示在这里", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.12f), contentColor = accent) {
                    Text("0 条", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        LabeledField("模块名称", name, { name = it }, placeholder = "如：跑步 / 日记")
        Spacer(Modifier.height(10.dp))

        // —— 快速模板：一键套用图标 / 颜色 / 字段 ——
        Text("快速模板", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            modulePresets.forEach { p ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        Modifier.clickable {
                            val presetFields = p.fields.mapIndexed { idx, f ->
                                CustomModuleField(key = "f${idx + 1}", label = f.first, type = f.second, unit = f.third)
                            }
                            name = p.name; iconIdx = p.icon; colorHex = p.color
                            fields = if (presetFields.isEmpty())
                                listOf(CustomModuleField(key = "value", label = "数值", type = "number", unit = ""))
                            else presetFields
                        }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            ModuleIconList.getOrElse(p.icon) { Icons.Filled.Extension }, null,
                            tint = parseHexColor(p.color, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(p.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // —— 图标：选中态 = 主题色描边 + 淡底 + 右上角 ✓ ——
        Text("图标（点选后带 ✓ 选中标记）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(5.dp))
        ModuleIconList.chunked(8).forEach { rowIcons ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                rowIcons.forEach { icon ->
                    val idx = ModuleIconList.indexOf(icon)
                    val selected = idx == iconIdx
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(
                                if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
                                RoundedCornerShape(11.dp)
                            )
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(11.dp)
                            )
                            .clickable { iconIdx = idx },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon, null,
                            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        if (selected) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                                    .background(accent, CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "✓", color = Color.White,
                                    fontSize = 8.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // —— 颜色：预设色球（选中带环）+ 自定义 ——
        Text("颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            moduleColorPresets.forEach { hex ->
                val selected = hex.equals(colorHex, ignoreCase = true)
                Box(
                    Modifier
                        .size(if (selected) 27.dp else 24.dp)
                        .background(parseHexColor(hex, accent), CircleShape)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                        .clickable { colorHex = hex }
                )
            }
            Box(
                Modifier
                    .size(24.dp).background(accent, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { pickingColor = true },
                contentAlignment = Alignment.Center
            ) {
                Text("…", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))

        // —— 字段定义：可排序，↑↓ 调整记录时的填写顺序 ——
        Text("字段定义（↑↓ 调整记录时的填写顺序）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        fields.forEachIndexed { i, f ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "字段 ${i + 1}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { moveField(i, -1) }, enabled = i > 0, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.Remove, "上移",
                            tint = if (i > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = { moveField(i, 1) }, enabled = i < fields.size - 1, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.Add, "下移",
                            tint = if (i < fields.size - 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = { if (fields.size > 1) removeField(i) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Delete, "移除字段", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
                LabeledField("字段名", f.label, { updateField(i, f.copy(label = it, key = if (f.key.isBlank()) autoKey(it) else f.key)) })
                Spacer(Modifier.height(4.dp))
                Segmented(fieldTypeNames, fieldTypes.indexOf(f.type).coerceAtLeast(0)) { ti ->
                    updateField(i, f.copy(type = fieldTypes[ti]))
                }
                if (f.type == "number" || f.type == "text") {
                    LabeledField("单位（可选）", f.unit ?: "", { updateField(i, f.copy(unit = it.ifBlank { null })) })
                }
                if (f.type == "number") {
                    LabeledField("最小值（可留空）", f.min?.toString() ?: "", { s -> updateField(i, f.copy(min = s.toDoubleOrNull())) })
                    LabeledField("最大值（可留空）", f.max?.toString() ?: "", { s -> updateField(i, f.copy(max = s.toDoubleOrNull())) })
                    LabeledField("步长（可留空）", f.step?.toString() ?: "", { s -> updateField(i, f.copy(step = s.toDoubleOrNull())) })
                }
                if (f.type == "select") {
                    LabeledField("候选值（逗号分隔）", f.options ?: "", { updateField(i, f.copy(options = it)) }, placeholder = "如：好,中,差")
                }
            }
        }
        OutlinedButton(onClick = { addField() }, shape = MaterialTheme.shapes.small) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("添加字段")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            onClick = {
                if (name.isBlank()) return@Button
                val validFields = fields.filter { it.label.isNotBlank() }
                    .mapIndexed { idx, f -> f.copy(key = if (f.key.isBlank()) "f${idx + 1}" else f.key) }
                val m = (initial ?: CustomModule()).apply {
                    this.name = name.trim()
                    this.colorHex = colorHex
                    this.icon = iconIdx
                    this.fields = validFields
                }
                if (initial == null) Repos.addCustomModule(m) else Repos.updateCustomModule(m)
                onClose()
            },
            enabled = name.isNotBlank() && fields.any { it.label.isNotBlank() }
        ) { Text("保存") }
    }
    if (pickingColor) {
        ColorPickerDialog(
            title = "模块颜色",
            initial = colorSafe(colorHex),
            onPick = { colorHex = com.joe.mepe.ui.theme.colorToHex(it); pickingColor = false },
            onDismiss = { pickingColor = false }
        )
    }
}
/** 记一笔前的数值校验：范围 + 步长（字段定义里配了才校验，返回错误提示或 null */
private fun validateModuleRecord(m: CustomModule, values: Map<String, String>): String? {
    m.fields.forEach { f ->
        if (f.type != "number") return@forEach
        val raw = values[f.key] ?: return@forEach
        if (raw.isBlank()) return@forEach
        val num = raw.toDoubleOrNull() ?: return "「${f.label}」需要填数字"
        f.min?.let { if (num < it) return "「${f.label}」不能小于 $it" }
        f.max?.let { if (num > it) return "「${f.label}」不能大于 $it" }
        f.step?.takeIf { it > 0 }?.let { st ->
            val steps = num / st
            if (kotlin.math.abs(steps - kotlin.math.round(steps)) > 0.0001)
                return "「${f.label}」需要是 $st 的整数倍"
        }
    }
    return null
}

// ============ 记一笔 ============

@Composable
fun ModuleRecordDialog(m: CustomModule, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDatePick by remember { mutableStateOf(false) }
    val values = remember { mutableStateOf(m.fields.associate { it.key to "" }) }

    FormDialog(title = "记录 · ${m.name}", onClose = onClose) {
        OutlinedButton(onClick = { showDatePick = true }, shape = MaterialTheme.shapes.small) {
            Text("日期：$date" + if (date == LocalDate.now()) "（今天）" else "")
        }
        Spacer(Modifier.height(6.dp))
        m.fields.forEach { f ->
            val v = values.value[f.key] ?: ""
            when (f.type) {
                "number" -> com.joe.mepe.ui.NumberField(
                    f.label, v,
                    { s -> values.value = values.value + (f.key to s) },
                    suffix = f.unit
                )
                "text" -> LabeledField(f.label, v, { s -> values.value = values.value + (f.key to s) })
                "time" -> {
                    val parts = v.split(':')
                    var hh = parts.getOrNull(0)?.toIntOrNull() ?: 8
                    var mm = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(f.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TimeField("时间", hh, mm, { h, mi ->
                            hh = h; mm = mi
                            values.value = values.value + (f.key to "%02d:%02d".format(h, mi))
                        })
                    }
                }
                "bool" -> ToggleRow(f.label, v == "true", { c -> values.value = values.value + (f.key to c.toString()) })
                "select" -> {
                    Text(f.label, style = MaterialTheme.typography.titleSmall)
                    val opts = (f.options ?: "").split(',').map { it.trim() }.filter { it.isNotBlank() }
                    Segmented(opts.ifEmpty { listOf("选项1", "选项2") }, opts.indexOf(v).coerceAtLeast(0)) { i ->
                        values.value = values.value + (f.key to opts.getOrElse(i) { "" })
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        LabeledField("备注（可选）", values.value["__note"] ?: "", { s -> values.value = values.value + ("__note" to s) })
        Spacer(Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            onClick = {
                val invalid = validateModuleRecord(m, values.value)
                if (invalid == null) {
                    val saved = values.value.filterKeys { it != "__note" }
                        .filterValues { it.isNotBlank() }
                    Repos.addModuleRecord(
                        m.id,
                        CustomModuleRecord(
                            date = date.toString(),
                            time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                            values = saved,
                            note = values.value["__note"]?.ifBlank { null },
                        )
                    )
                    onClose()
                } else {
                    android.widget.Toast.makeText(ctx, invalid, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        ) { Text("保存记录") }
    }
    if (showDatePick) {
        DatePickerDialog(date, { date = it; showDatePick = false }, { showDatePick = false })
    }
}

// ============ 历史 ============

@Composable
fun ModuleHistoryDialog(m: CustomModule, onClose: () -> Unit, onEditRecord: (CustomModule, CustomModuleRecord) -> Unit) {
    val mod = rememberData { Repos.customModules().firstOrNull { it.id == m.id } } ?: m
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(HtmlLibraryRepository.buildDefaultCsv(mod).toByteArray(Charsets.UTF_8))
                }
                android.widget.Toast.makeText(ctx, "已导出 CSV", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(ctx, "导出失败：" + (e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val records = mod.records.sortedWith(
        compareBy<CustomModuleRecord> { it.date }.thenBy { it.time }.thenBy { it.id }
    )
    val numberField = mod.fields.firstOrNull { it.type == "number" }

    FormDialog(title = "${mod.name} · 历史", onClose = onClose) {
        Text(
            "共 ${records.size} 条记录",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        if (records.isEmpty()) EmptyHint("还没有记录")
        Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
            numberField?.let { f ->
                SectionCard(title = "${f.label} 趋势") {
                    val vals = records.map { it.values[f.key]?.toDoubleOrNull() ?: 0.0 }
                    if (vals.count { it > 0 } >= 2) {
                        LineChart(vals, records.map { it.date.take(5) })
                    } else Text("数据不足", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            records.reversed().forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.date} ${r.time}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            r.values.entries.joinToString(" · ") { e ->
                                val f = mod.fields.find { it.key == e.key }
                                "${f?.label ?: e.key}: ${e.value}${f?.unit?.let { " $it" } ?: ""}"
                            }.ifBlank { "（无字段值）" } + (r.note?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = {
                        val all = Repos.customModules()
                        val mm = all.firstOrNull { it.id == mod.id }
                        if (mm != null) {
                            mm.records.removeIf { it.id == r.id }
                            Repos.saveCustomModules(all)
                            DataBus.bump()
                        }
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Delete, "删除记录", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch(mod.name + ".csv") },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small
            ) { Text("导出 CSV") }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("关闭") }
        }
    }
}
