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
import com.joe.mepe.ui.RoundedProgressBar
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.StatChip
import com.joe.mepe.ui.colorForGoal
import com.joe.mepe.ui.rememberData
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** 日历页：月视图（完成率色块）+ 当日任务详情 + 今日任务/连续打卡统计 */
@Composable
fun CalendarScreen(nav: (String) -> Unit) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }

    val rev = DataBus.rev
    val tasks = remember(rev) { Repos.tasks() }
    val goals = remember(rev) { Repos.goals() }
    val completions = remember(rev) { Repos.completions() }
    val today = LocalDate.now()

    // 日历统计口径：只统计该日期存在的任务；量化任务须设置了每日目标，且已达标的量化任务只算到达标当天；
    // 非循环类的永久完成项（单次任务 / 纯量化达标）只算完成当天，之后不再出现在日历统计里
    fun occursOnCalendar(t: TaskItem, date: LocalDate): Boolean {
        val isCycle = t.type == TaskTypes.RECURRING ||
            (t.type == TaskTypes.QUANTITATIVE && t.recurringPattern != null)
        if (t.type == TaskTypes.QUANTITATIVE) {
            if ((t.quantitativeDailyMin ?: 0.0) <= 0.0) return false
            val target = t.quantitativeTarget
            if (target != null && target > 0 && (t.quantitativeCurrent ?: 0.0) >= target) {
                val doneDay = t.completedAt?.toLocalDate()
                if (doneDay != null && date.isAfter(doneDay)) return false
            }
        }
        if (!isCycle && t.isCompleted) {
            val cd = TaskLogic.completedOn(t) ?: t.startDate?.toLocalDate() ?: t.createdAt.toLocalDate()
            if (cd != date) return false
        }
        return TaskLogic.occursOnDate(t, date)
    }

    // 当日任务列表：与任务页同序（优先级降序，再按 sortOrder，两端拖动排序互通）
    fun dueOn(date: LocalDate): List<TaskItem> = tasks.filter { occursOnCalendar(it, date) }
        .sortedWith(compareByDescending<TaskItem> { it.priority }.thenBy { it.sortOrder })

    /** 当日完成情况（不再计算打卡率）：0=无任务，1=有完成，2=全部完成，3=有任务未完成 */
    fun dayStatus(date: LocalDate): Int {
        val list = dueOn(date)
        if (list.isEmpty()) return 0
        val done = list.count { TaskLogic.isDoneReadonly(it, date, completions) }
        return if (done >= list.size) 2 else if (done > 0) 1 else 3
    }

    // —— 性能关键：整月状态一次性预算（进页/切月只算一次），渲染期不再逐格做磁盘 IO ——
    // 之前每个日期格都调用 isDoneOn → 量化任务会触发基线落盘，42 格 × 多任务导致进日历页卡顿
    val monthDays = remember(month) { (1..month.lengthOfMonth()).map { month.atDay(it) } }
    val monthStatus = remember(month, rev) {
        val map = HashMap<LocalDate, Int>(monthDays.size)
        for (d in monthDays) map[d] = dayStatus(d)
        map
    }

    // 月度统计：本月内还有未完成任务的「待完成任务日」
    val monthRemaining = monthDays.count { (monthStatus[it] ?: 0) == 1 || (monthStatus[it] ?: 0) == 3 }

    // 今日任务：不再展示打卡率，只给「已完成 / 未完成」（当天任务全部完成才算已完成）
    val todayDue = dueOn(today)
    val todayDone = todayDue.count { TaskLogic.isDoneReadonly(it, today, completions) }
    val todayStatusText = when {
        todayDue.isEmpty() -> "无任务"
        todayDone >= todayDue.size -> "已完成"
        else -> "未完成"
    }

    // 连续打卡：从今天往回逐日，当日完成 ≥1 个任务即打卡成功；无任务日跳过不断签；
    // 今天尚未完成时不计入今天也不中断（从昨天开始数）
    fun dayDone(d: LocalDate): Boolean = dueOn(d).any { TaskLogic.isDoneReadonly(it, d, completions) }
    var streak = 0
    var cursor = today
    if (!dayDone(today)) cursor = cursor.minusDays(1)
    var scanned = 0
    var scanning = true
    while (scanning && scanned < 365) {
        if (dayDone(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        } else if (dueOn(cursor).isEmpty()) {
            cursor = cursor.minusDays(1) // 无任务日跳过不断签
        } else {
            scanning = false
        }
        scanned++
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
                            val status = monthStatus[date] ?: 0 // 0=无任务 1=部分完成 2=全部完成 3=有任务未完成
                            val isToday = date == today
                            val isSelected = date == selectedDate
                            val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            status == 2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
                                            status == 1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                            status == 3 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        },
                                        RoundedCornerShape(10.dp)
                                    )
                                    .then(
                                        if (isToday && !isSelected)
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                                    // iOS 风水波纹：按压从中心扩散的涟漪，替代无反馈的裸点击
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = androidx.compose.material.ripple.rememberRipple(bounded = true),
                                    ) { selectedDate = date },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        status == 2 -> MaterialTheme.colorScheme.onPrimary
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
            StatChip("今日任务", todayStatusText, Modifier.weight(1f))
            StatChip("待完成任务日", "$monthRemaining 天", Modifier.weight(1f))
            StatChip("连续打卡", "$streak 天", Modifier.weight(1f))
        }

        // 当日详情
        SectionCard(title = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 任务详情") {
            // 当日任务量（与盘点同口径）：选到哪天就显示那天的 完成量 / 当日总任务量
            val dayDue = tasks.count { TaskLogic.dueOnDate(it, selectedDate) }
            val dayDone = tasks.count { TaskLogic.doneOnDate(it, selectedDate, completions) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "完成 $dayDone / $dayDue",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                RoundedProgressBar(
                    progress = if (dayDue > 0) dayDone.toFloat() / dayDue else 0f,
                    modifier = Modifier.weight(1f),
                    heightDp = 8
                )
            }
            Spacer(Modifier.height(8.dp))
            val list = dueOn(selectedDate)
            if (list.isEmpty()) EmptyHint("这一天没有任务")
            else list.forEach { t ->
                val done = TaskLogic.isDoneReadonly(t, selectedDate, completions)
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
