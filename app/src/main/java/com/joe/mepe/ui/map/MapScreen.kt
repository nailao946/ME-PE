package com.joe.mepe.ui.map

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
import androidx.compose.material.icons.filled.Map as MapIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.Repos
import com.joe.mepe.data.TaskLogic
import com.joe.mepe.ui.ProgressRing
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.colorForGoal
import com.joe.mepe.ui.rememberData
import java.time.LocalDate

/** 目标地图：所有根目标 + 子目标树形铺开，进度环 + 完成度概览 */
@Composable
fun MapScreen(nav: (String) -> Unit) {
    val rev = DataBus.rev
    val goals = remember(rev) { Repos.goals() }
    val tasks = remember(rev) { Repos.tasks() }
    val today = LocalDate.now()
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }

    val roots = goals.filter { it.parentId == null && !it.isDeleted }
    val avgProgress = if (roots.isEmpty()) 0.0 else roots.sumOf { TaskLogic.goalProgress(it, tasks, today) } / roots.size

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "目标地图",
            icon = Icons.Filled.MapIcon,
            subtitle = "全部目标一图总览",
            onBack = { nav(com.joe.mepe.ui.Routes.BACK) },
            actions = { com.joe.mepe.ui.QuickLinks(com.joe.mepe.ui.Routes.MAP, nav) }
        )

        // 全局概览
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = avgProgress, sizeDp = 72, stroke = 10f,
                    color = MaterialTheme.colorScheme.primary,
                    centerContent = { Text("${(avgProgress * 100).toInt()}%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("整体进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${roots.size} 个根目标 · ${tasks.size} 个任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val done = tasks.count { it.type != com.joe.mepe.data.TaskTypes.QUANTITATIVE && it.isCompleted }
                    Text("已完成任务 $done 个", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 树形目标
        roots.forEach { root ->
            MapNode(root, goals, tasks, today, 0, selected) { selected = it }
        }
        if (roots.isEmpty()) {
            Text(
                "还没有目标，去「目标」页创建吧",
                Modifier.fillMaxWidth().padding(32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MapNode(
    goal: com.joe.mepe.data.Goal,
    goals: List<com.joe.mepe.data.Goal>,
    tasks: List<com.joe.mepe.data.TaskItem>,
    today: LocalDate,
    depth: Int,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val children = goals.filter { it.parentId == goal.id && !it.isDeleted }
    val progress = TaskLogic.goalProgress(goal, tasks, today)
    val color = com.joe.mepe.ui.goals.goalDisplayColor(goal, MaterialTheme.colorScheme.primary)
    val isSel = selected == goal.id

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = (16 + depth * 24).dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                .background(
                    if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(12.dp)
                )
                .clickable { onSelect(if (isSel) null else goal.id) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressRing(progress = progress, sizeDp = if (depth == 0) 44 else 34, stroke = if (depth == 0) 7f else 5f, color = color,
                centerContent = {
                    Text("${(progress * 100).toInt()}", style = if (depth == 0) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)), fontWeight = FontWeight.Bold)
                }
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.name, fontWeight = if (depth == 0) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                val tasksHere = tasks.filter { it.goalId == goal.id && !it.isDeleted }
                val meta = mutableListOf<String>()
                if (children.isNotEmpty()) meta.add("子目标 ${children.size}")
                if (tasksHere.isNotEmpty()) meta.add("任务 ${tasksHere.size}")
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (children.isNotEmpty()) Text(if (isSel) "▾" else "▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isSel) children.forEach { c -> MapNode(c, goals, tasks, today, depth + 1, selected, onSelect) }
    }
}
