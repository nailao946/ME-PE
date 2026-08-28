package com.joe.mepe.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.Repos
import com.joe.mepe.data.TaskItem
import com.joe.mepe.data.TaskLogic
import com.joe.mepe.data.TaskTypes
import com.joe.mepe.ui.EmptyHint
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.StatChip
import com.joe.mepe.ui.colorForGoal
import com.joe.mepe.ui.rememberData
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** 日历页：月视图（完成率色块）+ 当日任务详情 + 月度统计（打卡率/剩余天数/连续打卡） */
@Composable
fun CalendarScreen(nav: (String) -> Unit) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }

    val rev = DataBus.rev
    val tasks = remember(rev) { Repos.tasks() }
    val goals = remember(rev) { Repos.goals() }
    val completions = remember(rev) { Repos.completions() }
    val today = LocalDate.now()

    fun dueOn(date: LocalDate): List<TaskItem> = tasks.filter { TaskLogic.occursOnDate(it, date) }

    fun dayRate(date: LocalDate): Double? {
        val list = dueOn(date)
        if (list.isEmpty()) return null
        return list.count { TaskLogic.isDoneOn(it, date, completions) }.toDouble() / list.size
    }

    // 月度统计
    val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val activeDays = monthDays.filter { dueOn(it).isNotEmpty() }
    val monthRate = if (activeDays.isEmpty()) null
    else activeDays.sumOf { d -> d.let { dayRate(it) ?: 0.0 } } / activeDays.size
    val monthRemaining = monthDays.count { it >= today && dueOn(it).any { t -> !TaskLogic.isDoneOn(t, it, completions) } }

    // 连续打卡（今天往前数，全部完成或有任务且完成率>0 的天数）
    var streak = 0
    var cursor = today
    repeat(90) {
        val d = dueOn(cursor)
        if (d.isEmpty()) { cursor = cursor.minusDays(1); return@repeat }
        if (d.all { TaskLogic.isDoneOn(it, cursor, completions) }) { streak++; cursor = cursor.minusDays(1) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        com.joe.mepe.ui.ScreenHeader(
            title = "日历",
            icon = Icons.Filled.Event,
            subtitle = "打卡进度一目了然",
            actions = { com.joe.mepe.ui.QuickLinks(com.joe.mepe.ui.Routes.CALENDAR, nav) }
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(0.3f))
            androidx.compose.material3.IconButton(onClick = { month = month.minusMonths(1) }) {
                androidx.compose.material3.Icon(
                    Icons.Filled.ChevronLeft, "上月",
                    tint = com.joe.mepe.ui.theme.LocalIconColor.current
                )
            }
            Text("${month.year}年${month.monthValue}月", Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            androidx.compose.material3.IconButton(onClick = { month = month.plusMonths(1) }) {
                androidx.compose.material3.Icon(
                    Icons.Filled.ChevronRight, "下月",
                    tint = com.joe.mepe.ui.theme.LocalIconColor.current
                )
            }
            Spacer(Modifier.weight(0.3f))
        }

        // 星期表头
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 日期网格：前置空白 + 日期，按 7 个一行
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(leadingBlanks) { null } +
                (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1.1f).padding(2.dp)) {
                        if (date != null) {
                            val rate = dayRate(date)
                            val isToday = date == today
                            val isSelected = date == selectedDate
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            rate != null -> MaterialTheme.colorScheme.primary.copy(alpha = (0.12 + rate * 0.5).toFloat())
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        },
                                        RoundedCornerShape(10.dp)
                                    )
                                    .then(
                                        if (isToday && !isSelected)
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                                    .clickable { selectedDate = date },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
                // 补齐末尾
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // 统计
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("月度打卡率", monthRate?.let { "${(it * 100).toInt()}%" } ?: "—", Modifier.weight(1f))
            StatChip("待完成任务日", "$monthRemaining 天", Modifier.weight(1f))
            StatChip("连续全勤", "$streak 天", Modifier.weight(1f))
        }

        // 当日详情
        SectionCard(title = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 任务详情") {
            val list = dueOn(selectedDate)
            if (list.isEmpty()) EmptyHint("这一天没有任务")
            else list.forEach { t ->
                val done = TaskLogic.isDoneOn(t, selectedDate, completions)
                val goal = t.goalId?.let { gid -> goals.find { it.id == gid } }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(4.dp).height(34.dp)
                            .background(
                                goal?.let { colorForGoal(it.color, MaterialTheme.colorScheme.primary) }
                                    ?: MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        if (goal != null) Text(goal.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (t.type == TaskTypes.QUANTITATIVE && t.quantitativeTarget != null && t.quantitativeTarget!! > 0) {
                        Text("${t.quantitativeCurrent?.toInt() ?: 0}/${t.quantitativeTarget!!.toInt()}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(if (done) "✓ 完成" else "未完成", style = MaterialTheme.typography.labelMedium,
                            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
