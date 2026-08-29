package com.joe.mepe.ui.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.ExerciseItem
import com.joe.mepe.data.HealthRecord
import com.joe.mepe.data.HealthTypes
import com.joe.mepe.data.Repos
import com.joe.mepe.ui.BarChart
import com.joe.mepe.ui.EmptyHint
import com.joe.mepe.ui.LineChart
import com.joe.mepe.ui.QuickLinks
import com.joe.mepe.ui.Routes
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.Segmented
import com.joe.mepe.ui.StatRow
import com.joe.mepe.ui.Stepper
import com.joe.mepe.ui.TimeField
import com.joe.mepe.ui.rememberData
import com.joe.mepe.ui.theme.LocalIconColor
import com.joe.mepe.ui.theme.parseHexColor
import com.joe.mepe.notify.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 健康页：总览 + 子页签 + 对比 + AI 分析 */
private val healthTabs = listOf("总览", "睡眠", "身体", "喝水", "心情", "尿酸", "锻炼", "久坐", "用药", "对比")

private val healthTabIcons = listOf(
    Icons.Filled.Favorite, Icons.Filled.Bedtime, Icons.Filled.MonitorWeight, Icons.Filled.WaterDrop,
    Icons.Filled.Mood, Icons.Filled.Science, Icons.Filled.FitnessCenter, Icons.Filled.Chair,
    Icons.Filled.Medication, Icons.Filled.CompareArrows,
)

@Composable
fun HealthScreen(nav: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "健康", icon = Icons.Filled.Favorite, subtitle = "记录与分析你的健康数据", actions = { QuickLinks(Routes.HEALTH, nav) })
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 12.dp,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            healthTabs.forEachIndexed { i, name ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(name, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                    icon = {
                        Icon(
                            healthTabIcons[i], null,
                            tint = if (tab == i) LocalIconColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                )
            }
        }

        AnimatedContent(targetState = tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "healthTab") { t ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (t) {
                    0 -> HealthOverview { tab = it }
                    1 -> SleepTab()
                    2 -> BodyTab()
                    3 -> WaterTab()
                    4 -> MoodTab()
                    5 -> UricTab()
                    6 -> ExerciseTab()
                    7 -> SedentaryTab()
                    8 -> MedicationTab()
                    9 -> CompareTab()
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

// ============ 工具 ============

fun lastNDays(n: Int): List<LocalDate> = (n - 1 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }

fun fmtDay(d: LocalDate): String = "${d.monthValue}/${d.dayOfMonth}"

fun healthRecords(type: String): List<HealthRecord> =
    Repos.health().filter { it.type == type }.sortedBy { it.date }

@Composable
fun TrendCard(title: String, values: List<Double>, days: List<LocalDate>, unit: String = "") {
    SectionCard(title = title) {
        if (values.filter { it > 0 }.isEmpty()) EmptyHint("暂无数据")
        else LineChart(
            values = values,
            labels = days.map { fmtDay(it) }
        )
    }
}

// ============ 总览 ============

private data class Metric(val icon: ImageVector, val label: String, val value: String, val color: Color, val tab: Int)

@Composable
fun HealthOverview(onSwitchTab: (Int) -> Unit) {
    val rev = DataBus.rev
    val today = LocalDate.now()
    val todayStr = today.toString()
    val records = remember(rev) { Repos.health() }

    val sleepRec = records.lastOrNull { it.type == HealthTypes.SLEEP && it.date == today.minusDays(1).toString() }
    val sleepText = sleepRec?.let { "${it.value.toInt()}小时${((it.value % 1) * 60).toInt()}分" } ?: "—"
    val weight = records.lastOrNull { it.type == HealthTypes.WEIGHT }?.value?.let { "${it}kg" } ?: "—"
    val waterTotal = records.filter { it.type == HealthTypes.WATER && it.date == todayStr }.sumOf { it.value }
    val waterGoal = Repos.getWaterGoal().toDoubleOrNull() ?: 2000.0
    val waterText = if (waterTotal > 0) "${waterTotal.toInt()}ml" else "—"
    val moodVal = records.lastOrNull { it.type == HealthTypes.MOOD && it.date == todayStr }?.value?.toInt()
    val moodText = moodVal?.let { listOf("😢", "😔", "😐", "😊", "😄")[(it - 1).coerceIn(0, 4)] } ?: "—"
    val uric = records.filter { it.type == HealthTypes.URIC_ACID }.maxByOrNull { it.date }?.let { "${it.value.toInt()}" } ?: "—"
    val sedCount = records.count { it.type == HealthTypes.SEDENTARY && it.date == todayStr }
    val medTotal = records.count { it.type == HealthTypes.MEDICATION && it.date == todayStr }

    val metrics = listOf(
        Metric(Icons.Filled.Bedtime, "昨晚睡眠", sleepText, Color(0xFF7C5CE0), 1),
        Metric(Icons.Filled.MonitorWeight, "最新体重", weight, Color(0xFF2BA8A8), 2),
        Metric(Icons.Filled.WaterDrop, "今日喝水", waterText, Color(0xFF4FC3F7), 3),
        Metric(Icons.Filled.Mood, "今日心情", moodText, Color(0xFFE0A93C), 4),
        Metric(Icons.Filled.Science, "尿酸 μmol/L", uric, Color(0xFFE05C8A), 5),
        Metric(Icons.Filled.Chair, "久坐活动", "$sedCount 次", Color(0xFF2E9E5B), 7),
    )

    Column {
        SectionCard(title = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今日概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${today.monthValue}月${today.dayOfMonth}日 · " + listOf("一","二","三","四","五","六","日")[today.dayOfWeek.value-1] + " · 服药 ${medTotal} 次",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                com.joe.mepe.ui.ProgressRing(
                    progress = (waterTotal / waterGoal).coerceIn(0.0, 1.0),
                    sizeDp = 64, stroke = 8f, color = Color(0xFF4FC3F7),
                    centerContent = {
                        Text("${(waterTotal / waterGoal * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                )
            }
        }
        // 指标卡片网格（两列对称）
        metrics.chunked(2).forEach { rowMetrics ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowMetrics.forEach { m ->
                    Card2(
                        Modifier.weight(1f).clickable { onSwitchTab(m.tab) },
                        icon = m.icon, iconBg = m.color.copy(alpha = 0.15f), iconTint = m.color,
                        title = m.label, value = m.value
                    )
                }
                repeat(2 - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // 记录入口
        val entries = listOf(
            Triple(Icons.Filled.Bedtime, "睡眠", 1), Triple(Icons.Filled.MonitorWeight, "体重", 2),
            Triple(Icons.Filled.WaterDrop, "喝水", 3), Triple(Icons.Filled.Mood, "心情", 4),
            Triple(Icons.Filled.Science, "尿酸", 5), Triple(Icons.Filled.FitnessCenter, "锻炼", 6),
            Triple(Icons.Filled.Chair, "久坐", 7), Triple(Icons.Filled.Medication, "用药", 8),
            Triple(Icons.Filled.CompareArrows, "对比", 9),
        )
        SectionCard(title = "快速记录") {
            entries.chunked(3).forEach { rowEntries ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowEntries.forEach { (icon, label, idx) ->
                        Column(
                            Modifier.weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), MaterialTheme.shapes.small)
                                .clickable { onSwitchTab(idx) }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(icon, null, tint = LocalIconColor.current, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    repeat(3 - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Card2(
    modifier: Modifier,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    value: String,
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(iconBg, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============ 睡眠 ============

@Composable
fun SleepTab() {
    val rev = DataBus.rev
    val saved = remember(rev) { healthRecords(HealthTypes.SLEEP).lastOrNull { it.date == LocalDate.now().minusDays(1).toString() } }
    var sleepH by remember { mutableStateOf(saved?.detail?.split('|')?.getOrNull(0)?.split(':')?.getOrNull(0)?.toIntOrNull() ?: 23) }
    var sleepM by remember { mutableStateOf(saved?.detail?.split('|')?.getOrNull(0)?.split(':')?.getOrNull(1)?.toIntOrNull() ?: 0) }
    var wakeH by remember { mutableStateOf(saved?.detail?.split('|')?.getOrNull(1)?.split(':')?.getOrNull(0)?.toIntOrNull() ?: 7) }
    var wakeM by remember { mutableStateOf(saved?.detail?.split('|')?.getOrNull(1)?.split(':')?.getOrNull(1)?.toIntOrNull() ?: 30) }

    val days = lastNDays(30)
    val records = remember(rev) { healthRecords(HealthTypes.SLEEP) }
    val durations = days.map { d -> records.lastOrNull { it.date == d.toString() }?.value ?: 0.0 }
    val avg7 = days.takeLast(7).mapNotNull { d -> records.lastOrNull { it.date == d.toString() }?.value }.filter { it > 0 }

    SectionCard(title = "记录昨晚睡眠") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("入睡", sleepH, sleepM, { h, m -> sleepH = h; sleepM = m }, Modifier.weight(1f))
            TimeField("起床", wakeH, wakeM, { h, m -> wakeH = h; wakeM = m }, Modifier.weight(1f))
        }
        var dur = java.time.Duration.between(
            LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(sleepH, sleepM)),
            LocalDateTime.of(LocalDate.now(), LocalTime.of(wakeH, wakeM))
        )
        if (dur.isNegative || dur.isZero) dur = dur.plus(java.time.Duration.ofDays(1))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("时长：${dur.toHours()}小时${dur.toMinutes() % 60}分", Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Button(onClick = {
                Repos.upsertHealth(HealthTypes.SLEEP, LocalDate.now().minusDays(1),
                    value = dur.toMinutes() / 60.0,
                    detail = "%02d:%02d|%02d:%02d".format(sleepH, sleepM, wakeH, wakeM))
            }) { Text("保存") }
        }
        if (avg7.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("近7天平均 ${"%.1f".format(avg7.average())} 小时", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    TrendCard("近30天睡眠时长（小时）", durations, days)
}

// ============ 身体测量 ============

@Composable
fun BodyTab() {
    val rev = DataBus.rev
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }

    val records = remember(rev) { healthRecords(HealthTypes.WEIGHT) }
    val latest = records.lastOrNull()
    val days = lastNDays(30)
    val values = days.map { d -> records.lastOrNull { it.date == d.toString() }?.value ?: 0.0 }

    SectionCard(title = "记录体重") {
        com.joe.mepe.ui.LabeledField("体重（kg）", weight, { weight = it })
        if (!weight.isNullOrBlank() && weight.toDoubleOrNull() != null) {
            val h = latest?.detail?.toDoubleOrNull() ?: 0.0
            if (h > 0) {
                val bmi = weight.toDouble() / (h / 100 * h / 100)
                val level = when {
                    bmi < 18.5 -> "偏瘦"; bmi < 24 -> "正常"; bmi < 28 -> "超重"; else -> "肥胖"
                }
                Text("BMI ${"%.1f".format(bmi)} · $level", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            } else {
                com.joe.mepe.ui.LabeledField("身高（cm，首次记录用）", height, { height = it })
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val w = weight.toDoubleOrNull() ?: return@Button
                val hCm = if (height.isNotBlank()) height.toDoubleOrNull() ?: return@Button else latest?.detail?.toDoubleOrNull() ?: return@Button
                Repos.upsertHealth(HealthTypes.WEIGHT, LocalDate.now(), w, detail = hCm.toString())
                weight = ""; height = ""
            },
            enabled = weight.toDoubleOrNull() != null && (latest != null || height.toDoubleOrNull() != null)
        ) { Text("保存") }
        latest?.let { last ->
            val hCm = last.detail?.toDoubleOrNull()
            val bmiText = if (hCm != null && hCm > 0) {
                val bmi = last.value / (hCm / 100 * hCm / 100)
                " · 身高${hCm}cm · BMI ${"%.1f".format(bmi)}"
            } else ""
            Text("最近记录：${last.date} · ${last.value}kg$bmiText",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    TrendCard("近30天体重（kg）", values, days)
}

// ============ 喝水 ============

@Composable
fun WaterTab() {
    val rev = DataBus.rev
    val today = remember(rev) { LocalDate.now() }
    val containers = remember(rev) { Repos.waterContainers() }
    val allWater = remember(rev) { healthRecords(HealthTypes.WATER) }
    val todayRecords = allWater.filter { it.date == today.toString() }.sortedBy { it.createdAt }
    val total = todayRecords.sumOf { it.value }
    var goal by remember(rev) { mutableStateOf(Repos.getWaterGoal().toDoubleOrNull() ?: 2000.0) }
    val days = lastNDays(14)
    val waterColor = Color(0xFF4FC3F7)
    var addingContainer by remember { mutableStateOf(false) }
    var editingContainer by remember { mutableStateOf<com.joe.mepe.data.WaterContainer?>(null) }
    var cName by remember { mutableStateOf("") }
    var cMl by remember { mutableStateOf("") }

    fun deleteRecord(id: Int) {
        val all = Repos.health().toMutableList()
        all.removeAll { it.id == id }
        Repos.saveHealth(all)
    }

    SectionCard(title = null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            com.joe.mepe.ui.ProgressRing(
                progress = (total / goal).coerceIn(0.0, 1.0),
                sizeDp = 158, stroke = 14f, color = waterColor,
                centerContent = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.WaterDrop, null, tint = waterColor, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${(goal - total).coerceAtLeast(0.0).toInt()}",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                        )
                        Text("还需 ml", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "今日 ${total.toInt()} / ${goal.toInt()} ml · ${(total / goal * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = waterColor
            )
            Spacer(Modifier.height(14.dp))
            // 快捷加量：三个等宽对称按钮
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 250, 500).forEach { ml ->
                    Button(
                        onClick = { Repos.addHealth(HealthTypes.WATER, today, ml.toDouble()) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = waterColor.copy(alpha = 0.15f), contentColor = waterColor)
                    ) { Text("+$ml", fontWeight = FontWeight.SemiBold) }
                }
            }
            // 自定义容器
            if (containers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    containers.take(3).forEach { c ->
                        OutlinedButton(
                            onClick = { Repos.addHealth(HealthTypes.WATER, today, c.capacityMl) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Filled.WaterDrop, null, Modifier.size(14.dp), tint = waterColor)
                            Spacer(Modifier.width(4.dp))
                            Text("${c.name} +${c.capacityMl.toInt()}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (containers.size > 3) {
                    Spacer(Modifier.height(6.dp))
                    Text("其余容器在下方「容器管理」中记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                com.joe.mepe.ui.Stepper(
                    "每日目标 ml", goal.toInt().toString(),
                    { goal = (goal - 100).coerceAtLeast(500.0); Repos.setWaterGoal(goal.toInt().toString()) },
                    { goal += 100; Repos.setWaterGoal(goal.toInt().toString()) },
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val all = Repos.health().toMutableList()
                    val last = all.lastOrNull { it.type == HealthTypes.WATER && it.date == today.toString() }
                    if (last != null) { all.remove(last); Repos.saveHealth(all) }
                }, enabled = total > 0) { Text("撤销一次") }
            }
        }
    }

    SectionCard(title = "今日记录（${todayRecords.size}）") {
        if (todayRecords.isEmpty()) {
            EmptyHint("今天还没喝水，点上面按钮记一笔", Icons.Filled.WaterDrop)
        } else {
            todayRecords.reversed().forEach { r ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.WaterDrop, null, tint = waterColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${r.value.toInt()} ml", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(r.createdAt.toString().take(16).replace('T', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { deleteRecord(r.id) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }

    SectionCard(title = "近14天喝水（ml）") {
        val vals = days.map { d -> allWater.filter { it.date == d.toString() }.sumOf { it.value } }
        BarChart(vals, days.map { fmtDay(it) })
    }

    // 容器管理：列出全部容器，可编辑/删除/新增（与桌面端互通，存 water_containers.json）
    SectionCard(title = "容器管理（${containers.size}）") {
        if (containers.isEmpty()) {
            EmptyHint("还没有容器，点下方新增（如：马克杯 500ml）", Icons.Filled.WaterDrop)
        } else {
            containers.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.WaterDrop, null, tint = waterColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${c.name}（${c.capacityMl.toInt()}ml）", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { Repos.addHealth(HealthTypes.WATER, today, c.capacityMl) }) { Text("记一笔") }
                    IconButton(
                        onClick = {
                            editingContainer = c
                            cName = c.name
                            cMl = if (c.capacityMl == c.capacityMl.toLong().toDouble()) c.capacityMl.toInt().toString() else c.capacityMl.toString()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Edit, "编辑容器", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        TextButton(onClick = { addingContainer = true }) { Text("＋ 新增容器") }
    }

    if (addingContainer || editingContainer != null) {
        val isEdit = editingContainer != null
        androidx.compose.ui.window.Dialog(onDismissRequest = { addingContainer = false; editingContainer = null }) {
            androidx.compose.material3.Card(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (isEdit) "编辑容器" else "新增容器",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    com.joe.mepe.ui.LabeledField("名称", cName, { cName = it }, placeholder = "如：马克杯")
                    com.joe.mepe.ui.NumberField("容量（ml）", cMl, { cMl = it })
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (isEdit) {
                            TextButton(onClick = {
                                Repos.deleteWaterContainer(editingContainer!!.id)
                                editingContainer = null; cName = ""; cMl = ""
                            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { addingContainer = false; editingContainer = null }) { Text("取消") }
                        Button(onClick = {
                            val ml = cMl.toDoubleOrNull()
                            if (cName.isNotBlank() && ml != null && ml > 0) {
                                if (isEdit) {
                                    Repos.updateWaterContainer(editingContainer!!.copy(name = cName.trim(), capacityMl = ml))
                                } else {
                                    Repos.addWaterContainer(com.joe.mepe.data.WaterContainer(name = cName.trim(), capacityMl = ml))
                                }
                                cName = ""; cMl = ""
                                addingContainer = false; editingContainer = null
                            }
                        }, enabled = cName.isNotBlank() && cMl.toDoubleOrNull() != null) { Text("保存") }
                    }
                }
            }
        }
    }
}

// ============ 心情 ============

@Composable
fun MoodTab() {
    val rev = DataBus.rev
    val today = remember(rev) { LocalDate.now() }
    val records = remember(rev) { healthRecords(HealthTypes.MOOD) }
    val emojis = listOf("😢", "😔", "😐", "😊", "😄")
    val moodLabels = listOf("很难过", "低落", "一般", "不错", "很开心")
    val todayMood = records.lastOrNull { it.date == today.toString() }?.value?.toInt()

    SectionCard(title = "今日心情") {
        if (todayMood != null) Text(
            "已记录：${emojis[(todayMood - 1).coerceIn(0, 4)]} ${moodLabels[(todayMood - 1).coerceIn(0, 4)]}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            emojis.forEachIndexed { i, e ->
                val selected = todayMood == i + 1
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(50.dp).background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            CircleShape
                        ).clickable { Repos.upsertHealth(HealthTypes.MOOD, today, (i + 1).toDouble()) },
                        contentAlignment = Alignment.Center
                    ) { Text(e, style = MaterialTheme.typography.headlineSmall) }
                    Text(moodLabels[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    SectionCard(title = "近30天分布") {
        val counts = IntArray(5)
        lastNDays(30).forEach { d ->
            records.lastOrNull { it.date == d.toString() }?.value?.toInt()?.let { v -> if (v in 1..5) counts[v - 1]++ }
        }
        Column {
            counts.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(emojis[i], Modifier.width(30.dp))
                    val max = counts.max().coerceAtLeast(1)
                    com.joe.mepe.ui.RoundedProgressBar(
                        progress = c.toFloat() / max,
                        modifier = Modifier.weight(1f),
                        heightDp = 12,
                        color = when (i) { 4 -> Color(0xFF2E9E5B); 3 -> Color(0xFF7CB342); 2 -> Color(0xFFE0A93C); 1 -> Color(0xFFE0603C); else -> Color(0xFFE5484D) }
                    )
                    Text("  $c 天", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ============ 尿酸 ============

@Composable
fun UricTab() {
    val rev = DataBus.rev
    var value by remember { mutableStateOf("") }
    var isMale by rememberSaveable { mutableStateOf(true) }

    val records = remember(rev) { healthRecords(HealthTypes.URIC_ACID) }
    val days = lastNDays(30)
    val values = days.map { d -> records.lastOrNull { it.date == d.toString() }?.value ?: 0.0 }

    SectionCard(title = "记录尿酸") {
        com.joe.mepe.ui.LabeledField("尿酸值（μmol/L）", value, { value = it })
        com.joe.mepe.ui.Segmented(listOf("男", "女"), if (isMale) 0 else 1) { isMale = it == 0 }
        val normalMin = if (isMale) 149.0 else 89.0
        val normalMax = if (isMale) 416.0 else 357.0
        val v = value.toDoubleOrNull()
        if (v != null) {
            val level = when {
                v < normalMin -> "偏低"; v <= normalMax -> "正常"; else -> "偏高"
            }
            Text("评估：$level（正常 ${normalMin.toInt()}~${normalMax.toInt()}）",
                color = if (level == "正常") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val d = value.toDoubleOrNull() ?: return@Button
            Repos.addHealth(HealthTypes.URIC_ACID, LocalDate.now(), d)
            value = ""
        }, enabled = value.toDoubleOrNull() != null) { Text("保存") }
    }
    StatRow(listOf(
        Triple("最新值", records.lastOrNull()?.value?.toInt()?.toString() ?: "—", null),
        Triple("正常范围", if (isMale) "149~416" else "89~357", null),
        Triple("记录次数", "${records.size}", null),
    ))
    SectionCard(title = "历史记录") {
        if (records.isEmpty()) EmptyHint("暂无记录")
        else records.takeLast(10).reversed().forEach { r ->
            val normalMax = if (isMale) 416.0 else 357.0
            val normalMin = if (isMale) 149.0 else 89.0
            val color = if (r.value in normalMin..normalMax) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(r.date, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("${r.value.toInt()}", color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
    TrendCard("近30天尿酸趋势", values, days)
}

// ============ 锻炼 ============

@Composable
fun ExerciseTab() {
    val rev = DataBus.rev
    val today = remember(rev) { LocalDate.now() }
    val items = remember(rev) { Repos.exercises() }
    var editing by remember { mutableStateOf<ExerciseItem?>(null) }
    var adding by remember { mutableStateOf(false) }

    SectionCard(title = "今日锻炼") {
        if (items.isEmpty()) EmptyHint("还没有锻炼项目，点下方添加", Icons.Filled.FitnessCenter)
        items.forEach { item ->
            val due = Repos.exerciseDueToday(item, today)
            val sum = Repos.exerciseSum(item.id, today)
            val pct = if (item.targetValue > 0) (sum / item.targetValue).coerceIn(0.0, 1.0) else 0.0
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.targetValue > 0) {
                    com.joe.mepe.ui.ProgressRing(
                        progress = pct, sizeDp = 42, stroke = 6f,
                        color = if (pct >= 1.0) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.primary,
                        centerContent = {
                            Text("${(pct * 100).toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, fontWeight = FontWeight.Medium)
                        if (!due) {
                            Spacer(Modifier.width(6.dp))
                            Text("今日休息", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("${sum.toInt()}/${item.targetValue.toInt()} ${item.unit} · ${
                        when (item.frequency) { "daily" -> "每日"; "everyOther" -> "隔日"; else -> "每周" + (item.weeklyDays ?: "") }
                    }", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "编辑", Modifier.clickable { editing = item }.padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = { Repos.addHealth(HealthTypes.EXERCISE, today, 1.0, detail = item.id.toString()) },
                    enabled = due, shape = MaterialTheme.shapes.small
                ) { Text("+1") }
            }
        }
        TextButton(onClick = { adding = true }) { Text("＋ 添加锻炼项目") }
    }

    SectionCard(title = "近7天锻炼量") {
        val days = lastNDays(7)
        val vals = days.map { d -> Repos.health().count { it.type == HealthTypes.EXERCISE && it.date == d.toString() }.toDouble() }
        BarChart(vals, days.map { fmtDay(it) })
    }

    if (adding) ExerciseEditDialog(null) { adding = false }
    editing?.let { e -> ExerciseEditDialog(e) { editing = null } }
}

@Composable
fun ExerciseEditDialog(initial: ExerciseItem?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var target by remember { mutableStateOf(if (initial != null && initial.targetValue > 0) initial.targetValue.toInt().toString() else "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "次") }
    var freq by remember { mutableStateOf(listOf("daily", "everyOther", "weekly").indexOf(initial?.frequency ?: "daily").coerceAtLeast(0)) }
    var weekDays by remember {
        mutableStateOf((initial?.weeklyDays ?: "1,3,5").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet())
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(if (initial == null) "新建锻炼项目" else "编辑锻炼项目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                com.joe.mepe.ui.LabeledField("名称", name, { name = it }, placeholder = "如：俯卧撑")
                com.joe.mepe.ui.NumberField("目标量（留空 = 只计数）", target, { target = it })
                com.joe.mepe.ui.LabeledField("单位", unit, { unit = it })
                Spacer(Modifier.height(6.dp))
                com.joe.mepe.ui.Segmented(listOf("每日", "隔日", "每周指定"), freq) { freq = it }
                if (freq == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("一","二","三","四","五","六","日").forEachIndexed { i, n ->
                            val d = i + 1
                            val on = d in weekDays
                            TextButton(onClick = { weekDays = if (on) weekDays - d else weekDays + d }) {
                                Text(n, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (initial != null) {
                        TextButton(onClick = { Repos.deleteExercise(initial.id); onClose() }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("取消") }
                    Button(onClick = {
                        if (name.isBlank()) return@Button
                        val e = ExerciseItem(
                            id = initial?.id ?: 0, name = name.trim(),
                            targetValue = target.toDoubleOrNull() ?: 0.0, unit = unit,
                            frequency = listOf("daily", "everyOther", "weekly")[freq],
                            weeklyDays = if (freq == 2) weekDays.sorted().joinToString(",") else null,
                            category = initial?.category, sortOrder = initial?.sortOrder ?: 0,
                            note = initial?.note, isDeleted = false, createdAt = initial?.createdAt ?: LocalDateTime.now(),
                        )
                        if (initial == null) Repos.addExercise(e) else Repos.updateExercise(e)
                        onClose()
                    }, enabled = name.isNotBlank()) { Text("保存") }
                }
            }
        }
    }
}

// ============ 久坐 ============

@Composable
fun SedentaryTab() {
    val rev = DataBus.rev
    val today = remember(rev) { LocalDate.now() }
    val records = remember(rev) { Repos.health() }
    val days = lastNDays(7)
    val todayCount = records.count { it.type == HealthTypes.SEDENTARY && it.date == today.toString() }

    SectionCard(title = "起身活动") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Chair, null, tint = LocalIconColor.current, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("今日已活动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$todayCount 次", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { Repos.addHealth(HealthTypes.SEDENTARY, today, 1.0) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
            Text("起身活动 +1", modifier = Modifier.padding(vertical = 6.dp))
        }
        if (todayCount > 0) {
            TextButton(onClick = {
                val all = records.toMutableList()
                val last = all.lastOrNull { it.type == HealthTypes.SEDENTARY && it.date == today.toString() }
                if (last != null) { all.remove(last); Repos.saveHealth(all) }
            }) { Text("撤销一次") }
        }
    }
    StatRow(listOf(
        Triple("今日", "$todayCount 次", null),
        Triple("近7天日均", "${"%.1f".format(days.map { d -> records.count { it.type == HealthTypes.SEDENTARY && it.date == d.toString() } }.average())} 次", null),
        Triple("近7天总计", "${days.sumOf { d -> records.count { it.type == HealthTypes.SEDENTARY && it.date == d.toString() } }} 次", null),
    ))
    SectionCard(title = "近7天活动次数") {
        BarChart(days.map { d -> records.count { it.type == HealthTypes.SEDENTARY && it.date == d.toString() }.toDouble() }, days.map { fmtDay(it) })
    }
}

// ============ 用药 ============

@Composable
fun MedicationTab() {
    val rev = DataBus.rev
    val today = remember(rev) { LocalDate.now() }
    val meds = remember(rev) { Repos.medications() }
    var editing by remember { mutableStateOf<com.joe.mepe.data.MedicationRecord?>(null) }
    var adding by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    SectionCard(title = "用药计划") {
        if (meds.isEmpty()) EmptyHint("还没有用药记录，点下方添加", Icons.Filled.Medication)
        meds.forEach { m ->
            val due = Repos.medicationDueToday(m, today)
            val taken = Repos.health().any {
                it.type == HealthTypes.MEDICATION && it.date == today.toString() && it.detail == m.id.toString()
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Medication, null, tint = LocalIconColor.current, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, fontWeight = FontWeight.Medium)
                    val spec = if (m.specValue > 0) " ${m.specValue}${com.joe.mepe.data.medUnitName(m.unit)}" else ""
                    Text(
                        "${com.joe.mepe.data.medTypeName(m.type)} ·$spec · ${com.joe.mepe.data.medFreqName(m.frequency)} · ${m.times}" + if (m.remind) " · 提醒" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "编辑", Modifier.clickable { editing = m }.padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = { Repos.addHealth(HealthTypes.MEDICATION, today, 1.0, detail = m.id.toString()) },
                    enabled = due && !taken,
                    shape = MaterialTheme.shapes.small,
                    colors = if (taken) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    else ButtonDefaults.buttonColors()
                ) {
                    if (taken) Icon(Icons.Filled.Check, null, Modifier.size(14.dp))
                    Text(if (taken) "已服" else "服药")
                }
            }
        }
        TextButton(onClick = { adding = true }) { Text("＋ 添加用药") }
    }

    if (adding) MedicationEditDialog(null) { adding = false }
    editing?.let { m -> MedicationEditDialog(m) {
        editing = null
        ReminderScheduler.scheduleAll(ctx)
    } }
}

@Composable
fun MedicationEditDialog(initial: com.joe.mepe.data.MedicationRecord?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: 1) }
    var spec by remember { mutableStateOf(if (initial != null && initial.specValue > 0) initial.specValue.toString() else "") }
    var unit by remember { mutableStateOf(initial?.unit ?: 1) }
    var freq by remember { mutableStateOf(initial?.frequency ?: 0) }
    var times by remember { mutableStateOf(initial?.times ?: "08:00") }
    var remind by remember { mutableStateOf(initial?.remind ?: true) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(if (initial == null) "添加用药" else "编辑用药", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                com.joe.mepe.ui.LabeledField("药名", name, { name = it })
                com.joe.mepe.ui.Segmented(listOf("药片", "胶囊", "液体", "外用"), when (type) {
                    1 -> 0; 0 -> 1; 2 -> 2; else -> 3
                }) { i -> type = when (i) { 0 -> 1; 1 -> 0; 2 -> 2; else -> 3 } }
                com.joe.mepe.ui.NumberField("规格（数值，可留空）", spec, { spec = it })
                com.joe.mepe.ui.Segmented(listOf("mg", "ml", "g"), when (unit) { 1 -> 0; 0 -> 1; 2 -> 2; else -> 0 }) { i -> unit = when (i) { 0 -> 1; 1 -> 0; else -> 2 } }
                com.joe.mepe.ui.Segmented(listOf("每天", "按需"), if (freq == 0) 0 else 1) { i -> freq = if (i == 0) 0 else 4 }
                com.joe.mepe.ui.LabeledField("服药时间（逗号分隔）", times, { times = it }, placeholder = "08:00,20:00")
                com.joe.mepe.ui.ToggleRow("到点提醒", remind, { remind = it }, sub = "开启后在服药时间发系统通知")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (initial != null) {
                        TextButton(onClick = { Repos.deleteMedication(initial.id); onClose() }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("取消") }
                    Button(onClick = {
                        if (name.isBlank()) return@Button
                        val m = com.joe.mepe.data.MedicationRecord(
                            id = initial?.id ?: 0, name = name.trim(), type = type,
                            specValue = spec.toDoubleOrNull() ?: 0.0, unit = unit, frequency = freq,
                            times = times, remind = remind,
                            startDate = initial?.startDate ?: LocalDateTime.now(),
                            createdAt = initial?.createdAt ?: LocalDateTime.now(),
                        )
                        if (initial == null) Repos.addMedication(m) else Repos.updateMedication(m)
                        onClose()
                    }, enabled = name.isNotBlank()) { Text("保存") }
                }
            }
        }
    }
}

// ============ 对比 ============

@Composable
fun CompareTab() {
    var selA by rememberSaveable { mutableStateOf(HealthTypes.WATER) }
    var selB by rememberSaveable { mutableStateOf(HealthTypes.SLEEP) }
    val days = lastNDays(30)
    val records = remember(DataBus.rev) { Repos.health() }

    fun series(type: String): List<Double> = days.map { d ->
        when (type) {
            HealthTypes.SLEEP -> records.lastOrNull { it.type == type && it.date == d.toString() }?.value ?: 0.0
            else -> records.filter { it.type == type && it.date == d.toString() }.sumOf { it.value }
        }
    }

    val options = listOf(
        HealthTypes.WATER to "喝水", HealthTypes.SLEEP to "睡眠", HealthTypes.WEIGHT to "体重",
        HealthTypes.URIC_ACID to "尿酸", HealthTypes.MOOD to "心情", HealthTypes.EXERCISE to "锻炼", HealthTypes.SEDENTARY to "久坐"
    )
    @Composable
    fun OptionRow(sel: String, accent: Color, onSelect: (String) -> Unit) {
        options.chunked(4).forEach { rowOpts ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                rowOpts.forEach { (key, label) ->
                    val active = sel == key
                    Box(
                        Modifier
                            .weight(1f)
                            .background(
                                if (active) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                MaterialTheme.shapes.extraSmall
                            )
                            .clickable { onSelect(key) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                repeat(4 - rowOpts.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    SectionCard(title = "选择两个参数叠加对比") {
        Text("参数 A", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OptionRow(selA, MaterialTheme.colorScheme.primary) { selA = it }
        Spacer(Modifier.height(6.dp))
        Text("参数 B", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OptionRow(selB, MaterialTheme.colorScheme.secondary) { selB = it }
    }
    SectionCard(title = "近30天叠加趋势") {
        LineChart(
            values = series(selA),
            overlay = listOf(series(selB) to MaterialTheme.colorScheme.secondary),
            labels = days.map { fmtDay(it) }
        )
    }
    SectionCard(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Science, null, tint = LocalIconColor.current, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("AI 分析", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text("把所选参数的近30天数据交给 AI 分析相关性", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var analyzing by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf("") }
        var err by remember { mutableStateOf("") }
        Button(onClick = {
            val provider = Repos.defaultAiProvider()
            if (provider == null) { err = "请先在「设置 → AI 分析」配置供应商"; return@Button }
            analyzing = true; err = ""; result = ""
            val namesA = options.find { it.first == selA }?.second ?: selA
            val namesB = options.find { it.first == selB }?.second ?: selB
            val prompt = buildString {
                appendLine("请分析以下两组健康数据的相关性并给出建议（近30天，按天）：")
                appendLine("【$namesA】")
                series(selA).forEachIndexed { i, v -> if (v > 0) appendLine("${days[i]}: ${"%.1f".format(v)}") }
                appendLine("【$namesB】")
                series(selB).forEachIndexed { i, v -> if (v > 0) appendLine("${days[i]}: ${"%.1f".format(v)}") }
            }
            scope.launch {
                val r = com.joe.mepe.ai.LlmService.chat(provider, prompt)
                analyzing = false
                r.onSuccess { result = it }.onFailure { err = "分析失败：${it.message}" }
            }
        }, enabled = !analyzing, shape = MaterialTheme.shapes.small) { Text(if (analyzing) "分析中…" else "开始 AI 分析") }
        if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    }
}
