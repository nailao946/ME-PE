package com.joe.mepe.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RateReview as RateReviewIcon
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.Review
import com.joe.mepe.data.Repos
import com.joe.mepe.data.TaskLogic
import com.joe.mepe.ui.BarChart
import com.joe.mepe.ui.EmptyHint
import com.joe.mepe.ui.LabeledField
import com.joe.mepe.ui.LineChart
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.StatChip
import com.joe.mepe.ui.rememberData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 定期盘点：今日/周/月统计 + 完成率趋势 + 历史盘点记录 */
@Composable
fun ReviewScreen(nav: (String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(0) } // 0=今日 1=周 2=月
    var writing by remember { mutableStateOf(false) }

    val rev = DataBus.rev
    val tasks = remember(rev) { Repos.tasks() }
    val goals = remember(rev) { Repos.goals() }
    val completions = remember(rev) { Repos.completions() }
    val reviews = remember(rev) { Repos.reviews() }
    val today = LocalDate.now()

    val (start, end) = when (mode) {
        0 -> today to today
        1 -> today.with(java.time.DayOfWeek.MONDAY) to today
        else -> today.withDayOfMonth(1) to today
    }

    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    // 统计口径（与桌面端一致）：总任务数=当日应做的任务（子任务、未设每日目标的量化、非当日循环任务不计）；
    // 完成=当日完成（量化=当日打卡/达标，一次性=完成当天，周期=当日打卡记录）
    val duePerDay = days.map { d -> tasks.count { TaskLogic.dueOnDate(it, d) } }
    val donePerDay = days.map { d -> tasks.count { TaskLogic.doneOnDate(it, d, completions) } }
    val totalDue = duePerDay.sum()
    val doneTotal = donePerDay.sum()
    val validRates = days.indices.mapNotNull { i -> if (duePerDay[i] > 0) donePerDay[i].toDouble() / duePerDay[i] else null }
    val avgRate = if (validRates.isEmpty()) null else validRates.average()

    // 较上期：今日比昨天，周盘点比上周，月盘点比上个月
    val prevStart = when (mode) { 0 -> today.minusDays(1); 1 -> start.minusDays(7); else -> start.minusMonths(1) }
    val prevEnd = if (mode == 0) prevStart else start.minusDays(1)
    val prevDays = generateSequence(prevStart) { it.plusDays(1) }.takeWhile { !it.isAfter(prevEnd) }.toList()
    val prevTotal = prevDays.sumOf { d -> tasks.count { TaskLogic.dueOnDate(it, d) } }
    val prevDone = prevDays.sumOf { d -> tasks.count { TaskLogic.doneOnDate(it, d, completions) } }
    val prevRate = if (prevTotal > 0) prevDone.toDouble() / prevTotal else null
    val cmpLabel = if (mode == 0) "较昨日" else "较上期"
    val rateTrend = if (avgRate != null && prevRate != null) {
        val diff = Math.round((avgRate - prevRate) * 100)
        "$cmpLabel${if (diff >= 0) "+" else ""}$diff%" to (diff >= 0)
    } else null
    val doneTrend = if (prevTotal > 0 || totalDue > 0) {
        val diff = doneTotal - prevDone
        "$cmpLabel${if (diff >= 0) "+" else ""}$diff" to (diff >= 0)
    } else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "盘点",
            icon = Icons.Filled.RateReviewIcon,
            subtitle = when (mode) {
                0 -> "今日 ${start.monthValue}/${start.dayOfMonth}"
                1 -> "本周 ${start.monthValue}/${start.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
                else -> "${today.year}年${today.monthValue}月"
            },
            onBack = { nav(com.joe.mepe.ui.Routes.BACK) },
            actions = { com.joe.mepe.ui.QuickLinks(com.joe.mepe.ui.Routes.REVIEW, nav) }
        )
        com.joe.mepe.ui.Segmented(listOf("今日", "周盘点", "月盘点"), mode, Modifier.padding(horizontal = 16.dp)) { mode = it }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("完成率", avgRate?.let { "${(it * 100).toInt()}%" } ?: "—", Modifier.weight(1f),
                trend = rateTrend?.first, trendUp = rateTrend?.second ?: true)
            StatChip("完成任务", "$doneTotal / $totalDue 个", Modifier.weight(1f),
                trend = doneTrend?.first, trendUp = doneTrend?.second ?: true)
            if (mode == 0) StatChip("待完成任务", "${(totalDue - doneTotal).coerceAtLeast(0)} 个", Modifier.weight(1f))
            else StatChip("活跃天数", "${validRates.size} 天", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))

        SectionCard(title = if (mode == 0) "近7天每日完成率" else "每日完成率趋势") {
            if (mode == 0) {
                // 今日页：当天没有趋势可画，附看近 7 天完成率
                val days7 = (6 downTo 0).map { today.minusDays(it.toLong()) }
                val due7 = days7.map { d -> tasks.count { TaskLogic.dueOnDate(it, d) } }
                val done7 = days7.map { d -> tasks.count { TaskLogic.doneOnDate(it, d, completions) } }
                if (due7.all { it == 0 }) EmptyHint("近7天没有任务")
                else BarChart(
                    days7.indices.map { i -> if (due7[i] == 0) 0.0 else done7[i] * 100.0 / due7[i] },
                    days7.map { "${it.monthValue}/${it.dayOfMonth}" }
                )
            } else if (validRates.isEmpty()) EmptyHint("此范围没有任务")
            else BarChart(
                days.indices.map { i -> if (duePerDay[i] == 0) 0.0 else donePerDay[i] * 100.0 / duePerDay[i] },
                days.map { "${it.dayOfMonth}" }
            )
        }

        SectionCard(title = "目标进度") {
            val rootGoals = goals.filter { it.parentId == null && !it.isDeleted }
            if (rootGoals.isEmpty()) EmptyHint("暂无目标")
            else rootGoals.forEach { g ->
                val p = TaskLogic.goalProgress(g, tasks, today)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${(p * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        TimeStatsCard(mode = mode, start = start, end = end)

        // 写盘点与历史盘点只属于周/月，今日页不放
        if (mode > 0) {
            val reviewType = mode - 1 // 页面 mode：1=周 2=月 → 盘点记录 type：0=周 1=月
            SectionCard(title = "历史盘点") {
                val list = reviews.filter { it.type == reviewType }.sortedByDescending { it.reviewDate }
                if (list.isEmpty()) EmptyHint("还没有写过盘点")
                else list.take(10).forEach { r ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${r.reviewDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))} · 完成率 ${(r.completionRate * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
                            )
                            r.personalNotes?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        androidx.compose.material3.TextButton(onClick = { Repos.saveReviews(Repos.reviews().filterNot { it.id == r.id }) }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            OutlinedButton(
                onClick = { writing = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(if (mode == 1) "写本周盘点" else "写本月盘点") }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (writing) {
        var notes by remember { mutableStateOf("") }
        var success by remember { mutableStateOf("") }
        var failure by remember { mutableStateOf("") }
        androidx.compose.ui.window.Dialog(onDismissRequest = { writing = false }) {
            androidx.compose.material3.Card {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text(if (mode == 1) "本周盘点" else "本月盘点", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    if (avgRate != null) Text("本期完成率：${(avgRate * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LabeledField("做得好的", success, { success = it }, singleLine = false)
                    LabeledField("待改进", failure, { failure = it }, singleLine = false)
                    LabeledField("个人笔记", notes, { notes = it }, singleLine = false)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = { writing = false }) { Text("取消") }
                        Button(onClick = {
                            Repos.addReview(Review(
                                type = mode - 1, reviewDate = java.time.LocalDateTime.now(),
                                completionRate = avgRate ?: 0.0,
                                successReasons = success.ifBlank { null },
                                failureReasons = failure.ifBlank { null },
                                personalNotes = notes.ifBlank { null },
                            ))
                            writing = false
                        }) { Text("保存") }
                    }
                }
            }
        }
    }
}

/**
 * 时间统计卡（参考桌面端统计）：总计 / 各标签时长占比 / 每日趋势。
 * span: 0=今日 1=本周 2=本月 3=全部
 */
@Composable
private fun TimeStatsCard(mode: Int, start: LocalDate, end: LocalDate) {
    val rev = DataBus.rev
    // 初始周期跟随上方所选的盘点页（今日/周/月）；切页时重置
    var span by rememberSaveable(mode) { mutableStateOf(mode.coerceIn(0, 2)) }
    val tags = remember(rev) { Repos.timeTags() }
    val records = remember(rev) { Repos.timeRecords() }
    val today = LocalDate.now()

    val (s, e) = when (span) {
        0 -> today to today
        1 -> today.with(java.time.DayOfWeek.MONDAY) to today
        2 -> today.withDayOfMonth(1) to today
        else -> null to null
    }
    val inRange = records.filter { r ->
        val d = try { LocalDate.parse(r.date) } catch (_: Exception) { return@filter false }
        (s == null || !d.isBefore(s)) && (e == null || !d.isAfter(e))
    }
    // 与桌面端复盘一致：默认标签（闲时）不参与统计；运行中记录计入到当前时刻
    val perTag = tags.filter { !it.isDefault }.map { t -> t to inRange.filter { it.tagId == t.id }.sumOf { it.minutes() } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    val total = perTag.sumOf { it.second }

    // 较上期：今日比昨天、本周比上周、本月比上月（全部不比）
    val prevRange: Pair<LocalDate, LocalDate>? = when (span) {
        0 -> today.minusDays(1) to today.minusDays(1)
        1 -> today.with(java.time.DayOfWeek.MONDAY).minusDays(7) to today.with(java.time.DayOfWeek.MONDAY).minusDays(1)
        2 -> today.withDayOfMonth(1).minusMonths(1) to today.withDayOfMonth(1).minusDays(1)
        else -> null
    }
    val prevTotalMin = prevRange?.let { (ps, pe) ->
        records.filter { r ->
            val d = try { LocalDate.parse(r.date) } catch (_: Exception) { return@filter false }
            !d.isBefore(ps) && !d.isAfter(pe)
        }.sumOf { it.minutes() }
    } ?: 0
    val timeTrend = if (prevRange != null && (prevTotalMin > 0 || total > 0)) {
        val diff = total - prevTotalMin
        val cmp = if (span == 0) "较昨日" else "较上期"
        "$cmp${if (diff >= 0) "+" else "-"}${com.joe.mepe.ui.timetrack.fmtMinutes(kotlin.math.abs(diff))}" to (diff >= 0)
    } else null

    SectionCard(title = "时间统计") {
        com.joe.mepe.ui.Segmented(listOf("今日", "本周", "本月", "全部"), span) { span = it }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.joe.mepe.ui.StatChip("总计时长", com.joe.mepe.ui.timetrack.fmtMinutes(total), Modifier.weight(1f),
                trend = timeTrend?.first, trendUp = timeTrend?.second ?: true)
            com.joe.mepe.ui.StatChip("计时记录", "${inRange.size} 条", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        if (perTag.isEmpty()) {
            EmptyHint("此范围还没有计时记录")
        } else {
            perTag.forEach { (t, min) ->
                val color = com.joe.mepe.ui.theme.parseHexColor(t.color, MaterialTheme.colorScheme.primary)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    com.joe.mepe.ui.ColorDot(color, sizeDp = 10)
                    Spacer(Modifier.width(8.dp))
                    Text(t.name, Modifier.width(84.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        com.joe.mepe.ui.timetrack.fmtMinutes(min),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    com.joe.mepe.ui.RoundedProgressBar(
                        progress = (if (total > 0) min.toDouble() / total else 0.0).toFloat(),
                        modifier = Modifier.weight(1f),
                        heightDp = 10,
                        color = color
                    )
                    Text(
                        if (total > 0) " ${(min * 100 / total)}%" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        // 折线图展示上方所选周期的数据（点「今日/本周/本月/全部」即切换；近14天/近12月也一并改为折线图）
        if (span == 3) {
            // 全部：近 12 个月每月时长（按天太密看不清）
            val months = (11 downTo 0).map { today.minusMonths(it.toLong()).withDayOfMonth(1) }
            val monthly = months.map { m ->
                val key = m.year.toString() + "-" + m.monthValue.toString().padStart(2, '0')
                records.filter { it.date.take(7) == key }.sumOf { it.minutes() }.toDouble()
            }
            if (monthly.any { it > 0 }) {
                Text("近 12 个月每月时长", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LineChart(monthly, months.map { "${it.year.toString().takeLast(2)}/${it.monthValue.toString().padStart(2, '0')}" })
            }
        } else {
            var title = when (span) { 0 -> "近 14 天每日时长"; 1 -> "本周每日时长"; else -> "本月每日时长" }
            var periodDays = when (span) {
                0 -> (13 downTo 0).map { today.minusDays(it.toLong()) } // 今日：当天画不了折线，附看近 14 天走势
                1 -> generateSequence(today.with(java.time.DayOfWeek.MONDAY)) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
                else -> (1..today.dayOfMonth).map { today.withDayOfMonth(it) }
            }
            if (periodDays.size < 2) { // 周一/1号只有一个点，退回近 7 天
                title = "近 7 天每日时长"
                periodDays = (6 downTo 0).map { today.minusDays(it.toLong()) }
            }
            val daily = periodDays.map { d -> records.filter { it.date == d.toString() }.sumOf { it.minutes() }.toDouble() }
            if (daily.any { it > 0 }) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LineChart(daily, periodDays.map { "${it.monthValue}/${it.dayOfMonth}" })
            }
        }
    }
}
