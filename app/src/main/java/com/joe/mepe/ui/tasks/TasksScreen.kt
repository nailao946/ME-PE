package com.joe.mepe.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.Goal
import com.joe.mepe.data.GoalTag
import com.joe.mepe.data.QuantModes
import com.joe.mepe.data.Repos
import com.joe.mepe.data.TaskItem
import com.joe.mepe.data.TaskLogic
import com.joe.mepe.data.TaskTypes
import com.joe.mepe.ui.ConfirmDialog
import com.joe.mepe.ui.EmptyHint
import com.joe.mepe.ui.QuickLinks
import com.joe.mepe.ui.Routes
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.SwipeReveal
import com.joe.mepe.ui.rememberData
import com.joe.mepe.ui.theme.LocalIconColor
import com.joe.mepe.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.roundToInt

/** 任务列表页：日期条 + 标签过滤 + 分区 + 长按拖动排序 + 左滑编辑/删除 + 点卡片看详情 */
@Composable
fun TasksScreen(nav: (String) -> Unit) {
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selectedTagId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TaskItem?>(null) }
    var detailTaskId by remember { mutableStateOf<Int?>(null) }

    val rev = DataBus.rev
    val data = remember(selectedDate, selectedTagId, rev) {
        val all = Repos.tasks()
        val completions = Repos.completions()
        val goals = Repos.goals()
        val tags = Repos.tags()
        Triple(all, completions, goals to tags)
    }
    val (allTasks, completions, goalTagPair) = data
    val (goals, tags) = goalTagPair

    // ---- 长按拖动排序状态 ----
    val listState = rememberLazyListState()
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    // key -> (分组key, 任务id)；分组："active" / "done" / "p<父任务id>"
    val rowGroups = remember { mutableMapOf<String, Pair<String, Int>>() }
    val haptic = LocalHapticFeedback.current

    fun groupParent(group: String): Int? = if (group.startsWith("p")) group.removePrefix("p").toIntOrNull() else null

    /** 把 draggedId 移到 targetId 前/后（同组内），并重排 Priority+SortOrder 持久化 */
    fun moveTo(draggedId: Int, targetId: Int, parentId: Int?, down: Boolean) {
        val list = Repos.tasks()
            .filter { !it.isDeleted && it.parentTaskId == parentId }
            .sortedWith(compareBy({ -it.priority }, { it.sortOrder }))
            .toMutableList()
        val di = list.indexOfFirst { it.id == draggedId }
        val ti = list.indexOfFirst { it.id == targetId }
        if (di < 0 || ti < 0) return
        val item = list.removeAt(di)
        val insertAt = (list.indexOfFirst { it.id == targetId } + if (down) 1 else 0).coerceIn(0, list.size)
        list.add(insertAt, item)
        list.forEachIndexed { idx, t ->
            t.priority = list.size - idx
            t.sortOrder = idx
            Repos.updateTask(t)
        }
        DataBus.bump()
    }

    /** 拖动经过相邻同组项时触发换位 */
    fun processDrag() {
        val key = draggingKey ?: return
        val group = rowGroups[key]?.first ?: return
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val draggedCenter = info.offset + dragOffset + info.size / 2f
        val candidates = listState.layoutInfo.visibleItemsInfo
            .filter { it.key != key && rowGroups[it.key]?.first == group }
        val target = candidates
            .filter {
                val c = it.offset + it.size / 2f
                if (dragOffset > 0) c < draggedCenter else c > draggedCenter
            }
            .maxByOrNull { if (dragOffset > 0) it.offset + it.size / 2f else -(it.offset + it.size / 2f) }
            ?: return
        val tCenter = target.offset + target.size / 2f
        val crossed = if (dragOffset > 0) draggedCenter > tCenter else draggedCenter < tCenter
        if (crossed) {
            val targetId = rowGroups[target.key]?.second ?: return
            val draggedId = rowGroups[key]?.second ?: return
            moveTo(draggedId, targetId, groupParent(group), down = dragOffset > 0)
            dragOffset += if (dragOffset > 0) -target.size.toFloat() else target.size.toFloat()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "任务",
            icon = Icons.Filled.Checklist,
            subtitle = selectedDate.let {
                if (it == LocalDate.now()) "今天 · " + listOf("一","二","三","四","五","六","日")[it.dayOfWeek.value-1] + " · 左滑卡片编辑/删除"
                else it.format(DateTimeFormatter.ofPattern("M月d日")) + " · 左滑卡片编辑/删除"
            },
            actions = { QuickLinks(Routes.TASKS, nav) }
        )

        // 日期条（今天 ±7 天，可横滑）
        val dayOffsets = (-7L..14L).toList()
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            items(dayOffsets) { offset ->
                val d = LocalDate.now().plusDays(offset)
                val active = d == selectedDate
                val hasTask = allTasks.any { TaskLogic.occursOnDate(it, d) }
                val bg by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    label = "dayBg"
                )
                Column(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(52.dp, 66.dp)
                        .background(bg, RoundedCornerShape(14.dp))
                        .clickable { selectedDate = d },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        when (offset) {
                            0L -> "今天"; -1L -> "昨天"; 1L -> "明天"
                            else -> listOf("一","二","三","四","五","六","日")[d.dayOfWeek.value - 1]
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${d.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (active || hasTask) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    if (hasTask && !active) {
                        Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    } else Spacer(Modifier.size(4.dp))
                }
            }
        }

        // 标签过滤
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            item {
                FilterChip2("全部", selectedTagId == null) { selectedTagId = null }
            }
            items(tags, key = { it.id }) { tag ->
                FilterChip2(tag.name, selectedTagId == tag.id, parseHexColor(tag.color, MaterialTheme.colorScheme.primary)) {
                    selectedTagId = if (selectedTagId == tag.id) null else tag.id
                }
            }
        }

        // 任务分区
        val visible = allTasks
            .filter { TaskLogic.occursOnDate(it, selectedDate) }
            .filter { t ->
                if (selectedTagId == null) true
                else t.goalId?.let { gid -> goals.find { g -> g.id == gid }?.tagId == selectedTagId } ?: false
            }
            .filter { it.parentTaskId == null }
            .sortedWith(compareBy({ -it.priority }, { it.sortOrder }))

        val activeTasks = visible.filter { !TaskLogic.isDoneOn(it, selectedDate, completions) }
        val doneTasks = visible.filter { TaskLogic.isDoneOn(it, selectedDate, completions) }

        LazyColumn(Modifier.fillMaxSize().weight(1f), state = listState) {
            item(key = "head_empty") {
                if (activeTasks.isEmpty() && doneTasks.isEmpty()) EmptyHint("此日期没有任务", Icons.Filled.Checklist)
            }
            if (activeTasks.isNotEmpty()) {
                item(key = "sec_active") { SectionLabel("进行中 (${activeTasks.size})") }
                items(activeTasks, key = { "t${it.id}" }) { t ->
                    rowGroups["t${t.id}"] = "active" to t.id
                    DraggableTaskGroup(
                        mainKey = "t${t.id}",
                        task = t, date = selectedDate,
                        completions = completions, goals = goals, tags = tags,
                        isDone = false,
                        draggingKey = draggingKey, dragOffset = dragOffset,
                        onStartDrag = {
                            draggingKey = it; dragOffset = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { dy -> dragOffset += dy; processDrag() },
                        onEndDrag = { draggingKey = null; dragOffset = 0f },
                        onEdit = { editingTask = it }, onDelete = { deleteTarget = it },
                        onOpen = { detailTaskId = it.id },
                        registerKey = { k, id -> rowGroups[k] = subGroupKey(k) to id },
                    )
                }
            }
            if (doneTasks.isNotEmpty()) {
                item(key = "sec_done") { SectionLabel("今日已完成 (${doneTasks.size})") }
                items(doneTasks, key = { "d${it.id}" }) { t ->
                    rowGroups["d${t.id}"] = "done" to t.id
                    DraggableTaskGroup(
                        mainKey = "d${t.id}",
                        task = t, date = selectedDate,
                        completions = completions, goals = goals, tags = tags,
                        isDone = true,
                        draggingKey = draggingKey, dragOffset = dragOffset,
                        onStartDrag = {
                            draggingKey = it; dragOffset = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { dy -> dragOffset += dy; processDrag() },
                        onEndDrag = { draggingKey = null; dragOffset = 0f },
                        onEdit = { editingTask = it }, onDelete = { deleteTarget = it },
                        onOpen = { detailTaskId = it.id },
                        registerKey = { k, id -> rowGroups[k] = subGroupKey(k) to id },
                    )
                }
            }
            item(key = "tail") { Spacer(Modifier.height(96.dp)) }
        }
    }

    // 悬浮新建按钮
    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("新任务", fontWeight = FontWeight.SemiBold)
        }
    }

    if (creating || editingTask != null) {
        TaskEditDialog(
            initial = editingTask,
            goals = goals,
            onClose = { creating = false; editingTask = null }
        )
    }
    deleteTarget?.let { t ->
        ConfirmDialog("删除任务", "确定删除「${t.title}」吗？可在回收站恢复。", {
            Repos.softDeleteTask(t.id)
            deleteTarget = null
        }, { deleteTarget = null })
    }
    detailTaskId?.let { tid ->
        TaskDetailSheet(
            taskId = tid, date = selectedDate,
            goals = goals, tags = tags,
            onClose = { detailTaskId = null },
            onEdit = { editingTask = it; detailTaskId = null },
            onDelete = { deleteTarget = it; detailTaskId = null },
        )
    }
}

// 生成子任务行的分组 key（p<父id>）
private fun subGroupKey(k: String): String {
    // k 形如 s<父id>_<子id>
    val parent = k.removePrefix("s").substringBefore('_')
    return "p$parent"
}

/** 主任务卡 + 子任务树（每张卡都可长按拖动，同组内排序；主卡可左滑编辑/删除） */
@Composable
private fun DraggableTaskGroup(
    mainKey: String,
    task: TaskItem,
    date: LocalDate,
    completions: List<com.joe.mepe.data.TaskCompletionRecord>,
    goals: List<Goal>,
    tags: List<GoalTag>,
    isDone: Boolean,
    draggingKey: String?,
    dragOffset: Float,
    onStartDrag: (String) -> Unit,
    onDrag: (Float) -> Unit,
    onEndDrag: () -> Unit,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    registerKey: (String, Int) -> Unit,
) {
    val subtasks = rememberData(key = task.id to date) {
        Repos.tasks().filter { it.parentTaskId == task.id && !it.isDeleted }
            .sortedWith(compareBy({ -it.priority }, { it.sortOrder }))
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        SwipeReveal(onEdit = { onEdit(task) }, onDelete = { onDelete(task) }, locked = draggingKey != null) {
            DraggableCard(
                itemKey = mainKey,
                draggingKey = draggingKey, dragOffset = dragOffset,
                onStartDrag = onStartDrag, onDrag = onDrag, onEndDrag = onEndDrag,
            ) {
                TaskCard(task, date, completions, goals, tags, subtasks, onOpen = onOpen, draggable = true)
            }
        }
        subtasks.forEach { sub ->
            val key = "s${task.id}_${sub.id}"
            registerKey(key, sub.id)
            Box(Modifier.padding(start = 24.dp, top = 4.dp)) {
                SwipeReveal(onEdit = { onEdit(sub) }, onDelete = { onDelete(sub) }, locked = draggingKey != null) {
                    DraggableCard(
                        itemKey = key,
                        draggingKey = draggingKey, dragOffset = dragOffset,
                        onStartDrag = onStartDrag, onDrag = onDrag, onEndDrag = onEndDrag,
                    ) {
                        TaskCard(sub, date, completions, goals, tags, emptyList(), onOpen = onOpen, draggable = true)
                    }
                }
            }
        }
    }
}

/** 长按拖动手势 + 拖动视觉（抬起、微放大） */
@Composable
private fun DraggableCard(
    itemKey: String,
    draggingKey: String?,
    dragOffset: Float,
    onStartDrag: (String) -> Unit,
    onDrag: (Float) -> Unit,
    onEndDrag: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dragging = draggingKey == itemKey
    Column(
        Modifier
            .graphicsLayer {
                translationY = if (dragging) dragOffset else 0f
                scaleX = if (dragging) 1.02f else 1f
                scaleY = if (dragging) 1.02f else 1f
                shadowElevation = if (dragging) 24f else 0f
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                clip = false
            }
            .pointerInput(itemKey) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onStartDrag(itemKey) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                    onDragEnd = { onEndDrag() },
                    onDragCancel = { onEndDrag() }
                )
            }
    ) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FilterChip2(label: String, active: Boolean, dotColor: Color? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dotColor != null) { com.joe.mepe.ui.ColorDot(dotColor); Spacer(Modifier.width(6.dp)) }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 任务卡：勾选圈=完成/取消；点卡片=详情；左滑=编辑/删除 */
@Composable
private fun TaskCard(
    task: TaskItem,
    date: LocalDate,
    completions: List<com.joe.mepe.data.TaskCompletionRecord>,
    goals: List<Goal>,
    tags: List<GoalTag>,
    subtasks: List<TaskItem>,
    onOpen: (TaskItem) -> Unit,
    draggable: Boolean = false,
) {
    val done = TaskLogic.isDoneOn(task, date, completions)
    val goal = task.goalId?.let { gid -> goals.find { it.id == gid } }
    val tagColor = goal?.tagId?.let { tid -> tags.find { t -> t.id == tid }?.color }
    val progress = if (task.type == TaskTypes.QUANTITATIVE && task.quantitativeTarget != null && task.quantitativeTarget!! > 0)
        ((task.quantitativeCurrent ?: 0.0) / task.quantitativeTarget!!).coerceIn(0.0, 1.0) else null
    val checkColor by animateColorAsState(
        if (done) MaterialTheme.colorScheme.primary else Color.Transparent, label = "check"
    )

    Card(
        Modifier.fillMaxWidth().clickable { onOpen(task) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            // 已完成也保持不透明（左滑动作层在卡片下层，半透明会透出按钮）
            containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 打卡圈（唯一的完成入口）
            Box(
                Modifier.size(26.dp)
                    .border(2.dp, if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .background(checkColor, CircleShape)
                    .clickable { TaskLogic.toggleDone(task, date) },
                contentAlignment = Alignment.Center
            ) {
                if (done) Icon(Icons.Filled.Check, "完成", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tagColor != null) { com.joe.mepe.ui.ColorDot(parseHexColor(tagColor, MaterialTheme.colorScheme.primary)); Spacer(Modifier.width(6.dp)) }
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
                val need = task.recurringTimesPerDay ?: 0
                val meta = buildString {
                    if (task.type != TaskTypes.ONE_TIME) append(TaskLogic.patternName(task))
                    if (progress != null) append(" · ${(progress * 100).toInt()}%")
                    if (subtasks.isNotEmpty()) append(" · 子任务 ${subtasks.size}")
                    if (need > 0) append(" · ${completions.count { it.taskId == task.id && it.date == date.toString() }}/$need")
                }
                if (meta.isNotBlank())
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress != null) {
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxWidth(progress.toFloat()).height(4.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                    }
                }
            }
            if (draggable) {
                Icon(
                    Icons.Filled.DragHandle, "长按拖动排序",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 2.dp).size(18.dp)
                )
            }
        }
    }
}

// ============ 任务详情底部弹窗 ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailSheet(
    taskId: Int,
    date: LocalDate,
    goals: List<Goal>,
    tags: List<GoalTag>,
    onClose: () -> Unit,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
) {
    val rev = DataBus.rev
    val task = rememberData(key = rev) { Repos.tasks().find { it.id == taskId } } ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val completions = remember(rev) { Repos.completions() }
    val subtasks = remember(rev, task.id) {
        Repos.tasks().filter { it.parentTaskId == task.id && !it.isDeleted }
            .sortedWith(compareBy({ -it.priority }, { it.sortOrder }))
    }
    var newSub by remember { mutableStateOf("") }
    var deleteSubTarget by remember { mutableStateOf<TaskItem?>(null) }

    val done = TaskLogic.isDoneOn(task, date, completions)
    val goal = task.goalId?.let { gid -> goals.find { it.id == gid } }
    val tagColor = goal?.tagId?.let { tid -> tags.find { t -> t.id == tid }?.color }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp)
                        .border(2.dp, if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .background(if (done) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                        .clickable { TaskLogic.toggleDone(task, date) },
                    contentAlignment = Alignment.Center
                ) {
                    if (done) Icon(Icons.Filled.Check, "完成", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    task.title, Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { onDelete(task) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onEdit(task) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // 元信息
            val metaLines = buildList {
                goal?.let { add("目标：${it.name}") }
                if (task.type != TaskTypes.ONE_TIME) add(TaskLogic.patternName(task))
                task.endDate?.let { add("截止：${it.toLocalDate()}") }
            }
            metaLines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    if (line.startsWith("目标") && tagColor != null) {
                        com.joe.mepe.ui.ColorDot(parseHexColor(tagColor, MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 打卡统计：近 30 天完成次数 / 打卡率 / 连续打卡天数
            val days30 = (29 downTo 0).map { date.minusDays(it.toLong()) }
            val due30 = days30.count { d -> TaskLogic.occursOnDate(task, d) }
            val done30 = days30.count { d -> TaskLogic.isDoneOn(task, d, completions) }
            var streak = 0
            run {
                var d = date
                var guard = 0
                while (guard++ < 400 && TaskLogic.occursOnDate(task, d) && TaskLogic.isDoneOn(task, d, completions)) {
                    streak++
                    d = d.minusDays(1)
                }
            }
            if (due30 > 0) {
                com.joe.mepe.ui.StatRow(listOf(
                    Triple("近30天完成", "$done30 次", null),
                    Triple("打卡率", "${done30 * 100 / due30}%", null),
                    Triple("连续打卡", "$streak 天", null),
                ))
                Spacer(Modifier.height(10.dp))
            }

            // 量化任务：进度控制
            if (task.type == TaskTypes.QUANTITATIVE && task.quantitativeTarget != null && task.quantitativeTarget!! > 0) {
                val start = task.quantitativeStart ?: 0.0
                val target = task.quantitativeTarget!!
                val cur = (task.quantitativeCurrent ?: start)
                val unit = " · ${TaskLogic.patternName(task)}"
                Text(
                    "进度 ${fmtNum(cur)} / ${fmtNum(target)}$unit",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(((cur - start) / (target - start)).coerceIn(0.0, 1.0).toFloat()).height(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { TaskLogic.adjustQuantitative(task, -1.0) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(44.dp, 38.dp)
                    ) { Icon(Icons.Filled.Remove, null, Modifier.size(16.dp)) }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { TaskLogic.adjustQuantitative(task, 1.0) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(44.dp, 38.dp)
                    ) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) }
                    androidx.compose.material3.Button(
                        onClick = {
                            if (task.quantitativeMode == QuantModes.UPDATE) {
                                task.quantitativeCurrent = target; Repos.updateTask(task); DataBus.bump()
                            } else {
                                TaskLogic.adjustQuantitative(task, 5.0)
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(if (task.quantitativeMode == QuantModes.UPDATE) "完成" else "+5", fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(10.dp))
                Slider(
                    value = ((cur - start) / (target - start)).coerceIn(0.0, 1.0).toFloat(),
                    onValueChange = { f ->
                        task.quantitativeCurrent = start + (target - start) * f.toDouble()
                        Repos.updateTask(task)
                    },
                    onValueChangeFinished = { DataBus.bump() },
                    enabled = target > start
                )
            }

            // 循环次数任务：+1 打卡
            val need = task.recurringTimesPerDay ?: 0
            if (need > 0) {
                val cnt = completions.count { it.taskId == task.id && it.date == date.toString() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "今日 $cnt/$need 次", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                    )
                    androidx.compose.material3.Button(
                        onClick = { Repos.addCompletion(task.id, date) },
                        enabled = cnt < need,
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("+1", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 普通任务打卡按钮
            if (task.type == TaskTypes.ONE_TIME || (task.type == TaskTypes.RECURRING && need == 0)) {
                androidx.compose.material3.Button(
                    onClick = { TaskLogic.toggleDone(task, date) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = if (done) androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) else androidx.compose.material3.ButtonDefaults.buttonColors()
                ) { Text(if (done) "✓ 已完成（点击取消）" else "完成打卡", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
            }

            // 子任务
            if (subtasks.isNotEmpty()) {
                Text("子任务 ${subtasks.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                subtasks.forEach { sub ->
                    val subDone = TaskLogic.isDoneOn(sub, date, completions)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(22.dp)
                                .border(2.dp, if (subDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .background(if (subDone) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .clickable { TaskLogic.toggleDone(sub, date) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (subDone) Icon(Icons.Filled.Check, "完成", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            sub.title, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (subDone) TextDecoration.LineThrough else null,
                            color = if (subDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { deleteSubTarget = sub }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Delete, "删除子任务", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // 添加子任务
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSub,
                    onValueChange = { newSub = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("添加子任务…", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        if (newSub.isNotBlank()) {
                            Repos.addTask(TaskItem(
                                title = newSub.trim(),
                                parentTaskId = task.id,
                                goalId = task.goalId,
                                type = TaskTypes.ONE_TIME,
                                createdAt = LocalDateTime.now(),
                                updatedAt = LocalDateTime.now(),
                            ))
                            newSub = ""
                        }
                    },
                    enabled = newSub.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    deleteSubTarget?.let { sub ->
        ConfirmDialog("删除子任务", "确定删除「${sub.title}」吗？", {
            Repos.softDeleteTask(sub.id)
            deleteSubTarget = null
        }, { deleteSubTarget = null })
    }
}

private fun fmtNum(d: Double): String =
    if (abs(d - d.roundToLong()) < 0.001) d.roundToLong().toString() else "%.1f".format(d)
