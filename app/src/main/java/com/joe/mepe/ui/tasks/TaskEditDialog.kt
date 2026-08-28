package com.joe.mepe.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.joe.mepe.ui.LabeledField
import com.joe.mepe.ui.NumberField
import com.joe.mepe.ui.Segmented
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

    Dialog(onDismissRequest = onClose) {
        Card {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(if (isNew) "新建任务" else "编辑任务", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

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

                // 所属目标
                Text("所属目标", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { goalId = null }) {
                        Text("无", color = if (goalId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                goals.filter { it.parentId == null }.forEach { g ->
                    OutlinedButton(
                        onClick = { goalId = g.id },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(
                            g.name,
                            color = if (goalId == g.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

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
                        Segmented(listOf("每日", "工作日", "周末", "每周…", "每月…", "间隔"),
                            when (pattern) {
                                RecPatterns.DAILY -> 0; RecPatterns.WEEKDAY -> 1; RecPatterns.WEEKEND -> 2
                                RecPatterns.WEEKLY -> 3; RecPatterns.MONTHLY -> 4; else -> 5
                            }) { i ->
                            pattern = when (i) {
                                0 -> RecPatterns.DAILY; 1 -> RecPatterns.WEEKDAY; 2 -> RecPatterns.WEEKEND
                                3 -> RecPatterns.WEEKLY; 4 -> RecPatterns.MONTHLY; else -> RecPatterns.INTERVAL
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        when (pattern) {
                            RecPatterns.WEEKLY -> {
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
                            }
                            RecPatterns.MONTHLY -> {
                                NumberField("每月几号（1-31）", dayOfMonth, { dayOfMonth = it })
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

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("取消") }
                    Spacer(Modifier.height(0.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) return@Button
                            val t = (initial ?: TaskItem()).apply {
                                this.title = title.trim()
                                this.description = desc.ifBlank { null }
                                this.type = type
                                this.goalId = goalId
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
                                if (isNew) this.isCompleted = false
                            }
                            if (isNew) Repos.addTask(t) else Repos.updateTask(t)
                            onClose()
                        },
                        enabled = title.isNotBlank()
                    ) { Text("保存") }
                }
            }
        }
    }

    if (showStartPick) DatePickerDialog(startDate, { startDate = it; showStartPick = false }, { showStartPick = false })
    if (showEndPick) DatePickerDialog(endDate ?: startDate, { endDate = it; showEndPick = false }, { showEndPick = false })
}

private fun trimNum(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
