package com.joe.mepe.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.joe.mepe.data.Goal
import com.joe.mepe.data.Repos
import com.joe.mepe.data.RecPatterns
import com.joe.mepe.data.QuantModes
import com.joe.mepe.data.TaskItem
import com.joe.mepe.data.TaskTypes
import com.joe.mepe.ui.CheckRow
import com.joe.mepe.ui.DatePickerDialog
import com.joe.mepe.ui.FormDialog
import com.joe.mepe.ui.LabeledField
import com.joe.mepe.ui.NumberField
import com.joe.mepe.ui.OptionItem
import com.joe.mepe.ui.OptionPickerDialog
import com.joe.mepe.ui.SelectorField
import com.joe.mepe.ui.Segmented
import com.joe.mepe.ui.WheelPicker
import java.time.LocalDate
import java.time.LocalDateTime

/** 新建/编辑任务对话框：标题/描述/类型/所属目标/日期/周期/量化 */
@Composable
fun TaskEditDialog(initial: TaskItem?, goals: List<Goal>, onClose: () -> Unit) {
    val isNew = initial == null
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: TaskTypes.ONE_TIME) }
    var goalId by remember { mutableStateOf(initial?.goalId) }
    var timeTagId by remember { mutableStateOf(initial?.timeTagId) }
    val timeTags = remember { Repos.timeTags() }

    var startDate by remember { mutableStateOf(initial?.startDate?.toLocalDate() ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(initial?.endDate?.toLocalDate()) }

    var pattern by remember { mutableStateOf(initial?.recurringPattern ?: RecPatterns.DAILY) }
    var intervalDays by remember { mutableStateOf((initial?.recurringInterval ?: 2).toString()) }
    var weekDays by remember {
        mutableStateOf(
            (initial?.recurringDaysOfWeek ?: "1,2,3,4,5").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        )
    }
    var dayOfMonth by remember { mutableStateOf((initial?.recurringDayOfMonth ?: 1).toString()) }
    var lastDay by remember { mutableStateOf(initial?.isLastDayOfMonth ?: false) }
    var timesPerDay by remember { mutableStateOf((initial?.recurringTimesPerDay ?: 0).toString()) }

    var quantMode by remember { mutableStateOf(initial?.quantitativeMode ?: QuantModes.ACCUMULATE) }
    var quantTarget by remember { mutableStateOf(if (initial?.quantitativeTarget != null) trimNum(initial.quantitativeTarget!!) else "") }
    var quantUnit by remember { mutableStateOf(initial?.quantitativeUnit ?: "") }
    var quantDailyMin by remember { mutableStateOf(if (initial?.quantitativeDailyMin != null) trimNum(initial.quantitativeDailyMin!!) else "") }

    var showStartPick by remember { mutableStateOf(false) }
    var showEndPick by remember { mutableStateOf(false) }
    var showPatternPick by remember { mutableStateOf(false) }

    FormDialog(title = if (isNew) "新建任务" else "编辑任务", onClose = onClose) {

                LabeledField("任务标题", title, { title = it }, placeholder = "要做什么？")
                Spacer(Modifier.height(8.dp))
                LabeledField("描述（可选）", desc, { desc = it }, singleLine = false)
                Spacer(Modifier.height(10.dp))

                Segmented(listOf("一次性", "周期", "量化"), when (type) {
                    TaskTypes.ONE_TIME -> 0
                    TaskTypes.PERIODIC, TaskTypes.RECURRING -> 1
                    else -> 2
                }) { i ->
                    type = when (i) { 0 -> TaskTypes.ONE_TIME; 1 -> TaskTypes.PERIODIC; else -> TaskTypes.QUANTITATIVE }
                }
                Spacer(Modifier.height(10.dp))

                // 所属目标（横滑芯片，放得下不换行）
                Text("所属目标", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        GoalChip("无目标", goalId == null, null) { goalId = null }
                    }
                    items(goals.filter { it.parentId == null }, key = { it.id }) { g ->
                        GoalChip(g.name, goalId == g.id, null) {
                            goalId = g.id
                            // 目标绑定了时间标签 → 任务默认继承该标签
                            if (g.timeTagId != null) timeTagId = g.timeTagId
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // 关联时间标签（与桌面端一致：计时/专注时归到该标签）
                if (timeTags.isNotEmpty()) {
                    Text("关联时间标签", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            GoalChip("无", timeTagId == null, null) { timeTagId = null }
                        }
                        items(timeTags, key = { it.id }) { t ->
                            GoalChip(
                                t.name, timeTagId == t.id,
                                com.joe.mepe.ui.theme.parseHexColor(
                                    t.color, MaterialTheme.colorScheme.primary
                                )
                            ) { timeTagId = if (timeTagId == t.id) null else t.id }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                when (type) {
                    TaskTypes.ONE_TIME -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showStartPick = true }) {
                                Text("开始 $startDate")
                            }
                            OutlinedButton(onClick = { showEndPick = true }) {
                                Text(endDate?.let { "截止 $it" } ?: "截止日期")
                            }
                        }
                        if (endDate != null) {
                            TextButton(onClick = { endDate = null }) { Text("清除截止日期") }
                        }
                    }
                    TaskTypes.PERIODIC -> {
                        // 重复频率：单行选择器，点开选项弹窗（替代原来一排芯片）
                        SelectorField("重复频率", patternLabel(pattern, intervalDays)) { showPatternPick = true }
                        Spacer(Modifier.height(8.dp))
                        when (pattern) {
                            RecPatterns.WEEKLY -> {
                                if (weekDays.size > 1) {
                                    // 老数据勾了多个星期几：保留芯片多选，改用滚轮会丢数据
                                    val names = listOf("一", "二", "三", "四", "五", "六", "日")
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        names.forEachIndexed { i, n ->
                                            val d = i + 1
                                            val on = d in weekDays
                                            OutlinedButton(onClick = {
                                                weekDays = if (on) weekDays - d else weekDays + d
                                            }) {
                                                Text(n, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                } else {
                                    Text("选周几（滚动到中间即选中）", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(2.dp))
                                    WheelPicker(
                                        items = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                                        selectedIndex = (weekDays.firstOrNull() ?: 1) - 1
                                    ) { i -> weekDays = setOf(i + 1) }
                                }
                            }
                            RecPatterns.MONTHLY -> {
                                Text("每月几号（滚动到中间即选中）", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(2.dp))
                                WheelPicker(
                                    items = (1..31).map { "$it 号" },
                                    selectedIndex = (dayOfMonth.toIntOrNull() ?: 1).coerceIn(1, 31) - 1
                                ) { i -> dayOfMonth = (i + 1).toString() }
                                Spacer(Modifier.height(6.dp))
                                CheckRow("每月最后一天", lastDay, { lastDay = it })
                            }
                            RecPatterns.INTERVAL -> NumberField("每隔几天", intervalDays, { intervalDays = it })
                            else -> {}
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showStartPick = true }) { Text("开始 $startDate") }
                            OutlinedButton(onClick = { showEndPick = true }) {
                                Text(endDate?.let { "截止 $it" } ?: "截止日期")
                            }
                        }
                        if (endDate != null) {
                            TextButton(onClick = { endDate = null }) { Text("清除截止日期") }
                        }
                    }
                    TaskTypes.QUANTITATIVE -> {
                        Segmented(listOf("累加", "更新"), if (quantMode == QuantModes.ACCUMULATE) 0 else 1) {
                            quantMode = if (it == 0) QuantModes.ACCUMULATE else QuantModes.UPDATE
                        }
                        Spacer(Modifier.height(8.dp))
                        NumberField("目标值", quantTarget, { quantTarget = it })
                        LabeledField("单位", quantUnit, { quantUnit = it }, placeholder = "如：次 / km / 页")
                        NumberField("每次打卡量（默认 1）", quantDailyMin, { quantDailyMin = it })
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    onClick = {
                            if (title.isBlank()) return@Button
                            val t = (initial ?: TaskItem()).apply {
                                this.title = title.trim()
                                this.description = desc.ifBlank { null }
                                this.type = type
                                this.goalId = goalId
                                this.timeTagId = timeTagId
                                this.startDate = startDate.atStartOfDay()
                                this.endDate = endDate?.atTime(23, 59, 59)
                                this.recurringPattern = if (type == TaskTypes.PERIODIC) pattern else null
                                this.recurringInterval = if (pattern == RecPatterns.INTERVAL) intervalDays.toIntOrNull() else null
                                this.recurringDaysOfWeek = if (pattern == RecPatterns.WEEKLY) weekDays.sorted().joinToString(",") else null
                                this.recurringDayOfMonth = if (pattern == RecPatterns.MONTHLY) dayOfMonth.toIntOrNull() else null
                                this.isLastDayOfMonth = pattern == RecPatterns.MONTHLY && lastDay
                                this.recurringTimesPerDay = timesPerDay.toIntOrNull()?.takeIf { it > 0 }
                                this.quantitativeMode = if (type == TaskTypes.QUANTITATIVE) quantMode else null
                                this.quantitativeTarget = if (type == TaskTypes.QUANTITATIVE) quantTarget.toDoubleOrNull() else null
                                this.quantitativeUnit = if (type == TaskTypes.QUANTITATIVE) quantUnit.ifBlank { null } else null
                                this.quantitativeDailyMin = if (type == TaskTypes.QUANTITATIVE) (quantDailyMin.toDoubleOrNull() ?: 1.0) else null
                                if (isNew) { this.isCompleted = false; this.createdAt = LocalDateTime.now() }
                                this.updatedAt = LocalDateTime.now()
                            }
                    if (isNew) Repos.addTask(t) else Repos.updateTask(t)
                    onClose()
                },
                enabled = title.isNotBlank()
            ) { Text("保存") }
    }

    if (showStartPick) DatePickerDialog(startDate, { startDate = it; showStartPick = false }, { showStartPick = false })
    if (showEndPick) DatePickerDialog(endDate ?: startDate, { endDate = it; showEndPick = false }, { showEndPick = false })
    if (showPatternPick) {
        OptionPickerDialog(
            title = "重复频率",
            options = listOf(
                OptionItem(RecPatterns.DAILY.toString(), "每日", "每天都打卡"),
                OptionItem(RecPatterns.WEEKDAY.toString(), "工作日", "周一至周五"),
                OptionItem(RecPatterns.WEEKEND.toString(), "周末", "周六、周日"),
                OptionItem(RecPatterns.WEEKLY.toString(), "每周", "自选星期几"),
                OptionItem(RecPatterns.MONTHLY.toString(), "每月", "指定每月几号"),
                OptionItem(RecPatterns.INTERVAL.toString(), "自定义间隔", "每隔几天执行一次"),
            ),
            selectedKey = pattern.toString(),
            onPick = { pattern = it.toInt(); showPatternPick = false },
            onDismiss = { showPatternPick = false }
        )
    }
}

/** 频率选择器当前值文案 */
private fun patternLabel(pattern: Int, intervalDays: String): String = when (pattern) {
    RecPatterns.DAILY -> "每日"
    RecPatterns.WEEKDAY -> "工作日"
    RecPatterns.WEEKEND -> "周末"
    RecPatterns.WEEKLY -> "每周"
    RecPatterns.MONTHLY -> "每月"
    else -> "每隔${intervalDays.toIntOrNull() ?: 2}天"
}

/** 选择芯片（横滑，圆角小胶囊；dot 颜色可空） */
@Composable
private fun GoalChip(label: String, active: Boolean, dot: androidx.compose.ui.graphics.Color?, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (dot != null) {
                com.joe.mepe.ui.ColorDot(dot)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun trimNum(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
