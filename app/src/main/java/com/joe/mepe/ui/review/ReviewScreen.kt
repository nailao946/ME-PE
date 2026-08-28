package com.joe.mepe.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.StatChip
import com.joe.mepe.ui.rememberData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 定期盘点：周/月统计 + 完成率趋势 + 历史盘点记录 */
@Composable
fun ReviewScreen(nav: (String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(0) } // 0=周 1=月
    var writing by remember { mutableStateOf(false) }

    val rev = DataBus.rev
    val tasks = remember(rev) { Repos.tasks() }
    val goals = remember(rev) { Repos.goals() }
    val completions = remember(rev) { Repos.completions() }
    val reviews = remember(rev) { Repos.reviews() }
    val today = LocalDate.now()

    val (start, end) = if (mode == 0)
        today.with(java.time.DayOfWeek.MONDAY) to today
    else today.withDayOfMonth(1) to today

    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    val rates = days.map { d ->
        val due = tasks.filter { TaskLogic.occursOnDate(it, d) }
        if (due.isEmpty()) -1.0
        else due.count { TaskLogic.isDoneOn(it, d, completions) }.toDouble() / due.size
    }
    val validRates = rates.filter { it >= 0 }
    val avgRate = if (validRates.isEmpty()) null else validRates.average()
    val doneCount = days.sumOf { d -> tasks.count { TaskLogic.isDoneOn(it, d, completions) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "盘点",
            icon = Icons.Filled.RateReviewIcon,
            subtitle = if (mode == 0) "本周 ${start.monthValue}/${start.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
                       else "${today.year}年${today.monthValue}月",
            onBack = { nav(com.joe.mepe.ui.Routes.BACK) },
            actions = { com.joe.mepe.ui.QuickLinks(com.joe.mepe.ui.Routes.REVIEW, nav) }
        )
        com.joe.mepe.ui.Segmented(listOf("周盘点", "月盘点"), mode, Modifier.padding(horizontal = 16.dp)) { mode = it }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("完成率", avgRate?.let { "${(it * 100).toInt()}%" } ?: "—", Modifier.weight(1f))
            StatChip("完成任务", "$doneCount 个", Modifier.weight(1f))
            StatChip("活跃天数", "${validRates.size} 天", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))

        SectionCard(title = "每日完成率趋势") {
            if (validRates.isEmpty()) EmptyHint("此范围没有任务")
            else BarChart(rates.map { if (it < 0) 0.0 else it * 100 }, days.map { "${it.dayOfMonth}" })
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

        SectionCard(title = "历史盘点") {
            val list = reviews.filter { it.type == mode }.sortedByDescending { it.reviewDate }
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
        ) { Text(if (mode == 0) "写本周盘点" else "写本月盘点") }

        Spacer(Modifier.height(24.dp))
    }

    if (writing) {
        var notes by remember { mutableStateOf("") }
        var success by remember { mutableStateOf("") }
        var failure by remember { mutableStateOf("") }
        androidx.compose.ui.window.Dialog(onDismissRequest = { writing = false }) {
            androidx.compose.material3.Card {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text(if (mode == 0) "本周盘点" else "本月盘点", style = MaterialTheme.typography.titleLarge)
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
                                type = mode, reviewDate = java.time.LocalDateTime.now(),
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
