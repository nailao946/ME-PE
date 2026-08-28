package com.joe.mepe.ui.modules

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
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
            val color = parseHexColor(m.colorHex, MaterialTheme.colorScheme.primary)
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(color.copy(alpha = 0.15f), MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            ModuleIconList.getOrElse(m.icon) { Icons.Filled.Extension },
                            null, tint = color, modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "字段：${m.fields.joinToString("、") { it.label + if (it.unit != null) "(${it.unit})" else "" }.ifBlank { "无" }} · ${m.records.size} 条记录",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { recording = m }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) { Text("记一笔") }
                    OutlinedButton(onClick = { historyOf = m }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) { Text("历史") }
                    OutlinedButton(onClick = { editing = m }, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Filled.Edit, "编辑", modifier = Modifier.size(15.dp))
                    }
                    OutlinedButton(
                        onClick = { deleteTarget = m },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.width(52.dp)
                    ) { Text("删", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Button(onClick = { creating = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("＋ 新建模块", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (creating || editing != null) {
        ModuleEditDialog(initial = editing, onClose = { creating = false; editing = null })
    }
    recording?.let { m -> ModuleRecordDialog(m, onClose = { recording = null }) }
    historyOf?.let { m -> ModuleHistoryDialog(m, onClose = { historyOf = null }, onEditRecord = { mod, rec -> }) }
    deleteTarget?.let { m ->
        ConfirmDialog("删除模块", "确定删除「${m.name}」及其全部 ${m.records.size} 条记录吗？", {
            Repos.deleteCustomModule(m.id)
            deleteTarget = null
        }, { deleteTarget = null })
    }
}

// ============ 模块编辑 ============

@Composable
fun ModuleEditDialog(initial: CustomModule?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: "#4F6EF7") }
    var iconIdx by remember { mutableStateOf(initial?.icon ?: 0) }
    var fields by remember {
        mutableStateOf(
            initial?.fields?.map { it.copy() }?.toMutableList()
                ?: mutableListOf(CustomModuleField(key = "value", label = "数值", type = "number", unit = ""))
        )
    }
    var pickingColor by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(if (initial == null) "新建模块" else "编辑模块", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                LabeledField("模块名称", name, { name = it }, placeholder = "如：跑步 / 日记")
                Spacer(Modifier.height(8.dp))
                // 图标
                Text("图标", style = MaterialTheme.typography.titleSmall)
                ModuleIconList.chunked(8).forEach { rowIcons ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowIcons.forEach { icon ->
                            val idx = ModuleIconList.indexOf(icon)
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .background(
                                        if (idx == iconIdx) colorSafe(colorHex).copy(alpha = 0.2f) else Color.Transparent,
                                        MaterialTheme.shapes.extraSmall
                                    )
                                    .clickable { iconIdx = idx },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = if (idx == iconIdx) colorSafe(colorHex) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).background(colorSafe(colorHex), CircleShape)
                            .clickable { pickingColor = true }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("点击色块自定义颜色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Text("字段定义", style = MaterialTheme.typography.titleSmall)
                fields.forEachIndexed { i, f ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("字段 ${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (fields.size > 1) {
                                TextButton(onClick = { fields.removeAt(i) }) { Text("移除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                        LabeledField("字段名", f.label, { fields[i] = f.copy(label = it, key = if (f.key.isBlank()) autoKey(it) else f.key) })
                        Spacer(Modifier.height(4.dp))
                        Segmented(fieldTypeNames, fieldTypes.indexOf(f.type).coerceAtLeast(0)) { ti ->
                            fields[i] = f.copy(type = fieldTypes[ti])
                        }
                        if (f.type == "number" || f.type == "text") {
                            LabeledField("单位（可选）", f.unit ?: "", { fields[i] = f.copy(unit = it.ifBlank { null }) })
                        }
                        if (f.type == "select") {
                            LabeledField("候选值（逗号分隔）", f.options ?: "", { fields[i] = f.copy(options = it) }, placeholder = "如：好,中,差")
                        }
                    }
                }
                TextButton(onClick = {
                    fields.add(CustomModuleField(key = "f${System.currentTimeMillis() % 100000}", label = "", type = "number"))
                }) { Text("＋ 添加字段") }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("取消") }
                    Button(
                        onClick = {
                            if (name.isBlank()) return@Button
                            val validFields = fields.filter { it.label.isNotBlank() }
                                .mapIndexed { i, f -> f.copy(key = if (f.key.isBlank()) "f${i + 1}" else f.key) }
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
            }
        }
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

private fun colorSafe(hex: String): Color = parseHexColor(hex, Color(0xFF4F6EF7))
private fun autoKey(label: String): String = "f_${label.hashCode().toString().take(6)}"

// ============ 记一笔 ============

@Composable
fun ModuleRecordDialog(m: CustomModule, onClose: () -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDatePick by remember { mutableStateOf(false) }
    val values = remember { mutableStateOf(m.fields.associate { it.key to "" }) }

    Dialog(onDismissRequest = onClose) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        ModuleIconList.getOrElse(m.icon) { Icons.Filled.Extension }, null,
                        tint = colorSafe(m.colorHex), modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("记录 · ${m.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(6.dp))
                }
                LabeledField("备注（可选）", values.value["__note"] ?: "", { s -> values.value = values.value + ("__note" to s) })
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("取消") }
                    Button(onClick = {
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
                    }) { Text("保存记录") }
                }
            }
        }
    }
    if (showDatePick) {
        DatePickerDialog(date, { date = it; showDatePick = false }, { showDatePick = false })
    }
}

// ============ 历史 ============

@Composable
fun ModuleHistoryDialog(m: CustomModule, onClose: () -> Unit, onEditRecord: (CustomModule, CustomModuleRecord) -> Unit) {
    val mod = rememberData { Repos.customModules().firstOrNull { it.id == m.id } } ?: m
    val records = mod.records.sortedBy { it.date }
    val numberField = mod.fields.firstOrNull { it.type == "number" }

    Dialog(onDismissRequest = onClose) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text("${mod.name} · 历史（${records.size} 条）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
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
                            TextButton(onClick = {
                                val all = Repos.customModules()
                                val mm = all.firstOrNull { it.id == mod.id }
                                if (mm != null) { mm.records.removeIf { it.id == r.id }; Repos.saveCustomModules(all) }
                            }) { Text("删", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("关闭") }
                }
            }
        }
    }
}
