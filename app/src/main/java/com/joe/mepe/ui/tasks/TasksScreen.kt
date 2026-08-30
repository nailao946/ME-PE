package com.joe.mepe.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.outlined.Today
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.joe.mepe.ui.SyncBall
import com.joe.mepe.ui.SyncStatusBus
import com.joe.mepe.ui.SwipeReveal
import com.joe.mepe.ui.runFullSync
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

/** 任务页一次读取的数据包 */
private data class TasksData(
    val tasks: List<TaskItem>,
    val completions: List<com.joe.mepe.data.TaskCompletionRecord>,
    val goals: List<Goal>,
    val tags: List<GoalTag>,
    val timeTags: List<com.joe.mepe.data.TimeTag>,
)

/** 任务列表页：日期条 + 标签过滤 + 分区 + 长按拖动排序 + 左滑编辑/删除 + 点卡片看详情 + 下拉刷新云同步 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(nav: (String) -> Unit) {
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selectedTagId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TaskItem?>(null) }
    var detailTaskId by remember { mutableStateOf<Int?>(null) }
    var detailSubTaskId by remember { mutableStateOf<Int?>(null) }

    // ---- 云同步：下拉列表顶部露出空白、松手弹回（微博式，无加载圈圈），下拉超过阈值静默触发一次完整同步；
    //      同步过程看右上角状态球（呼吸闪烁），结果用 Toast 轻提示 ----
    val syncCtx = LocalContext.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val maxPull = with(density) { 128.dp.toPx() }      // 最大下拉位移（橡皮筋上限）
    val triggerPull = with(density) { 88.dp.toPx() }   // 松手触发同步的最小下拉距离
    var pullOffset by remember { mutableStateOf(0f) }
    // 下拉时 1:1 跟手（snap），松手归零后弹簧回弹（spring）
    val pullDisplay by animateFloatAsState(
        targetValue = pullOffset,
        animationSpec = if (pullOffset > 0f) snap()
        else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pullOffset"
    )
    val syncScope = rememberCoroutineScope()
    val pullConnection = remember(listState, maxPull, triggerPull) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                return when {
                    // 列表在顶部继续下拉：吃掉手势转成「露出空白」
                    dy > 0 && !listState.canScrollBackward -> {
                        pullOffset = (pullOffset + dy).coerceAtMost(maxPull)
                        Offset(0f, dy)
                    }
                    // 下拉后又往上推：先收回下拉距离，剩余才让列表滚动
                    dy < 0 && pullOffset > 0f -> {
                        val consumed = dy.coerceAtLeast(-pullOffset)
                        pullOffset += consumed
                        Offset(0f, consumed)
                    }
                    else -> Offset.Zero
                }
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // 一帧内滚动越过顶部的剩余下拉量
                if (available.y > 0 && !listState.canScrollBackward) {
                    pullOffset = (pullOffset + available.y).coerceAtMost(maxPull)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(velocity: Velocity): Velocity {
                val pulled = pullOffset
                pullOffset = 0f   // 无论是否触发同步，松手都弹回
                if (pulled >= triggerPull && SyncStatusBus.state != SyncStatusBus.State.RUNNING)
                    syncScope.launch { runFullSync(syncCtx, toast = true) }
                return Velocity.Zero
            }
        }
    }

    val rev = DataBus.rev
    val data = remember(selectedDate, selectedTagId, rev) {
        val all = Repos.tasks()
        val completions = Repos.completions()
        val goals = Repos.goals()
        val tags = Repos.tags()
        TasksData(all, completions, goals, tags, Repos.timeTags())
    }
    val (allTasks, completions, goals, tags, timeTags) = data

    // ---- 长按拖动排序状态 ----
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var dropTargetKey by remember { mutableStateOf<String?>(null) }
    var dropAfter by remember { mutableStateOf(false) }
    // key -> (分组key, 任务id)；分组："active" / "done" / "p<父任务id>"
    val rowGroups = remember { mutableMapOf<String, Pair<String, Int>>() }
    val haptic = LocalHapticFeedback.current

    fun groupParent(group: String): Int? = if (group.startsWith("p")) group.removePrefix("p").toIntOrNull() else null

    /** 各组当前界面显示顺序：active/done 由列表直接写入，子任务组由卡片注册 */
    val groupOrderIds = remember { mutableMapOf<String, List<Int>>() }

    /**
     * 拖动落位：按"界面显示顺序"计算新序列并持久化。
     * 旧实现对整组任务（含未显示的任务）重排，导致松手后位置对不上；现在
     * 只重排显示中的任务，其余任务按原相对位置嵌回，显示顺序与落点完全一致。
     */
    fun reorderGroup(group: String, draggedId: Int, targetId: Int, down: Boolean) {
        val all = Repos.tasks()
        val groupAll = all.filter { !it.isDeleted && it.parentTaskId == groupParent(group) }
            .sortedBy { it.sortOrder }
        // 界面显示顺序：主分组由列表写入；子任务组回退到存储顺序（即当前显示顺序）
        val displayed = groupOrderIds[group] ?: groupAll.map { it.id }
        val seq = displayed.toMutableList()
        val di = seq.indexOf(draggedId)
        val ti = seq.indexOf(targetId)
        if (di < 0 || ti < 0) return
        seq.removeAt(di)
        seq.add((seq.indexOf(targetId) + if (down) 1 else 0).coerceIn(0, seq.size), draggedId)
        if (seq == displayed) return

        val byId = groupAll.associateBy { it.id }
        val moved = seq.toSet()
        val queue = ArrayDeque(seq.mapNotNull { byId[it] })
        val newOrder = groupAll.map { g -> if (g.id in moved) queue.removeFirst() else g }
        newOrder.forEachIndexed { idx, t ->
            t.sortOrder = idx
            t.priority = newOrder.size - idx
        }
        Repos.reorderTasks(newOrder)
    }

    /** 拖动中只计算预计落点（虚影行），不实时换位——松手才落位，杜绝抖动 */
    fun processDrag() {
        val key = draggingKey ?: return
        val group = rowGroups[key]?.first ?: return
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val draggedCenter = info.offset + dragOffset + info.size / 2f
        val candidates = listState.layoutInfo.visibleItemsInfo
            .filter { it.key != key && rowGroups[it.key]?.first == group }
        if (candidates.isEmpty()) { dropTargetKey = null; return }
        // 与拖动中心重叠的行 = 预计落点；无重叠时取最近一行
        val target = candidates.firstOrNull { c ->
            draggedCenter >= c.offset && draggedCenter <= c.offset + c.size
        } ?: candidates.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - draggedCenter) }
            ?: run { dropTargetKey = null; return }
        dropTargetKey = target.key as? String
        dropAfter = draggedCenter > target.offset + target.size / 2f
    }

    /** 松手：按预计落点执行一次重排（单次保存、单次刷新） */
    fun finishDrag() {
        val key = draggingKey
        if (key != null) {
            val group = rowGroups[key]?.first
            val draggedId = rowGroups[key]?.second
            val targetKey = dropTargetKey
            if (group != null && draggedId != null && targetKey != null) {
                val targetId = rowGroups[targetKey]?.second
                if (targetId != null && targetId != draggedId) {
                    reorderGroup(group, draggedId, targetId, down = dropAfter)
                }
            }
        }
        draggingKey = null
        dragOffset = 0f
        dropTargetKey = null
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "任务",
            icon = Icons.Filled.Checklist,
            subtitle = selectedDate.let {
                if (it == LocalDate.now()) "今天 · " + listOf("一","二","三","四","五","六","日")[it.dayOfWeek.value-1] + " · 左滑卡片编辑/删除"
                else it.format(DateTimeFormatter.ofPattern("M月d日")) + " · 左滑卡片编辑/删除"
            },
            actions = { SyncBall(); QuickLinks(Routes.TASKS, nav) }
        )

        // 日期条（今天前后各一年，可横滑）+ 一键回今天（今天始终居中）
        val dayOffsets = (-365L..365L).toList()
        val todayIndex = 365 // offset=0（今天）在列表中的下标
        val dateListState = rememberLazyListState()
        val dateScope = rememberCoroutineScope()
        suspend fun centerToday(animate: Boolean) {
            dateListState.scrollToItem(todayIndex)
            val info = dateListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == todayIndex } ?: return
            val viewport = dateListState.layoutInfo.viewportEndOffset - dateListState.layoutInfo.viewportStartOffset
            val off = info.size / 2 - viewport / 2
            if (animate) dateListState.animateScrollToItem(todayIndex, off)
            else dateListState.scrollToItem(todayIndex, off)
        }
        LaunchedEffect(Unit) { centerToday(animate = false) }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f).padding(vertical = 4.dp), state = dateListState) {
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
                            .padding(start = 12.dp, end = 4.dp)
                            .size(54.dp)
                            .background(bg, CircleShape)
                            .clickable { selectedDate = d },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            when {
                                offset == 0L -> "今天"; offset == -1L -> "昨天"; offset == 1L -> "明天"
                                d.dayOfMonth == 1 -> "${d.monthValue}月" // 每月 1 号显示月份，方便远端定位
                                else -> listOf("一","二","三","四","五","六","日")[d.dayOfWeek.value - 1]
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${d.dayOfMonth}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (active || hasTask) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        if (hasTask && !active) {
                            Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        } else Spacer(Modifier.size(4.dp))
                    }
                }
            }
            // 回到今天：与日期圆球同尺寸同语言的圆形按钮，主题色淡底描边
            val todayColor = MaterialTheme.colorScheme.primary
            Column(
                Modifier
                    .padding(start = 8.dp, end = 12.dp)
                    .size(54.dp)
                    .background(todayColor.copy(alpha = 0.10f), CircleShape)
                    .border(1.2.dp, todayColor.copy(alpha = 0.4f), CircleShape)
                    .clickable {
                        selectedDate = LocalDate.now()
                        dateScope.launch { centerToday(animate = true) }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.Today, "回到今天",
                    tint = todayColor, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text("今天", style = MaterialTheme.typography.labelSmall, color = todayColor, fontWeight = FontWeight.Medium)
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
            // 与桌面端同序：优先级降序，再按 sortOrder（拖动排序两端互通）
            .sortedWith(compareByDescending<TaskItem> { it.priority }.thenBy { it.sortOrder })

        val activeTasks = visible.filter { !TaskLogic.isDoneOn(it, selectedDate, completions) }
        val doneTasks = visible.filter { TaskLogic.isDoneOn(it, selectedDate, completions) }
        groupOrderIds["active"] = activeTasks.map { it.id }
        groupOrderIds["done"] = doneTasks.map { it.id }

        // 拍平的行：主任务与其子任务都是独立列表项（子任务才能拥有自己的拖动定位）
        val subsByParent = allTasks.filter { it.parentTaskId != null }.groupBy { it.parentTaskId!! }
        fun rowsFor(list: List<TaskItem>): List<TaskRow> = buildList {
            list.forEach { t ->
                val subs = (subsByParent[t.id] ?: emptyList()).sortedBy { it.sortOrder }
                add(TaskRow.Main(t, subs))
                subs.forEach { add(TaskRow.Sub(t.id, it)) }
            }
        }
        val activeRows = rowsFor(activeTasks)
        val doneRows = rowsFor(doneTasks)

        // 下拉刷新：列表在顶部继续下拉，内容整体下滑露出空白，松手弹回（不再显示转圈指示器）
        Box(Modifier.weight(1f).nestedScroll(pullConnection)) {
        LazyColumn(Modifier.fillMaxSize().graphicsLayer { translationY = pullDisplay }, state = listState) {
            item(key = "head_empty") {
                if (activeTasks.isEmpty() && doneTasks.isEmpty()) EmptyHint("此日期没有任务", Icons.Filled.Checklist)
            }
            if (activeRows.isNotEmpty()) {
                item(key = "sec_active") { SectionLabel("进行中 (${activeTasks.size})") }
                items(activeRows, key = { it.key }) { row ->
                    TaskRowItem(
                        row = row, date = selectedDate,
                        completions = completions, goals = goals, tags = tags, timeTags = timeTags,
                        group = "active", isDone = false,
                        draggingKey = draggingKey, dragOffset = dragOffset, dropTargetKey = dropTargetKey,
                        onStartDrag = {
                            draggingKey = it; dragOffset = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { dy -> dragOffset += dy; processDrag() },
                        onEndDrag = { finishDrag() },
                        onEdit = { editingTask = it }, onDelete = { deleteTarget = it },
                        onOpen = { detailTaskId = it.id },
                        onRegister = { k, g, id -> rowGroups[k] = g to id },
                    )
                }
            }
            if (doneRows.isNotEmpty()) {
                item(key = "sec_done") { SectionLabel("今日已完成 (${doneTasks.size})") }
                items(doneRows, key = { it.key }) { row ->
                    TaskRowItem(
                        row = row, date = selectedDate,
                        completions = completions, goals = goals, tags = tags, timeTags = timeTags,
                        group = "done", isDone = true,
                        draggingKey = draggingKey, dragOffset = dragOffset, dropTargetKey = dropTargetKey,
                        onStartDrag = {
                            draggingKey = it; dragOffset = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { dy -> dragOffset += dy; processDrag() },
                        onEndDrag = { finishDrag() },
                        onEdit = { editingTask = it }, onDelete = { deleteTarget = it },
                        onOpen = { detailTaskId = it.id },
                        onRegister = { k, g, id -> rowGroups[k] = g to id },
                    )
                }
            }
            item(key = "tail") { Spacer(Modifier.height(96.dp)) }
        }
        }
    }

    // 悬浮新建按钮（小圆 FAB，纯 + 号）
    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp, pressedElevation = 12.dp, hoveredElevation = 8.dp
            )
        ) {
            Icon(Icons.Filled.Add, "新任务", Modifier.size(26.dp))
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
            goals = goals, tags = tags, timeTags = timeTags,
            onClose = { detailTaskId = null; detailSubTaskId = null },
            onEdit = { editingTask = it; detailTaskId = null; detailSubTaskId = null },
            onDelete = { deleteTarget = it; detailTaskId = null; detailSubTaskId = null },
            onOpenSub = { detailSubTaskId = it },
        )
    }
    // 子任务详情：叠在父任务详情之上（子任务也有自己的打卡图）
    detailSubTaskId?.let { sid ->
        TaskDetailSheet(
            taskId = sid, date = selectedDate,
            goals = goals, tags = tags, timeTags = timeTags,
            onClose = { detailSubTaskId = null },
            onEdit = { editingTask = it; detailSubTaskId = null },
            onDelete = { deleteTarget = it; detailSubTaskId = null },
        )
    }
}

/** 拍平的任务行：主任务与子任务都是独立列表项（子任务才能拥有自己的拖动定位与落点） */
private sealed class TaskRow {
    abstract val key: String
    data class Main(val task: TaskItem, val subs: List<TaskItem>) : TaskRow() {
        override val key get() = "t${task.id}"
    }
    data class Sub(val parentId: Int, val task: TaskItem) : TaskRow() {
        override val key get() = "s${parentId}_${task.id}"
    }
}

/** 拍平后的任务行：独立列表项，可长按拖动（落点虚影+松手落位）、可左滑编辑/删除 */
@Composable
private fun TaskRowItem(
    row: TaskRow,
    date: LocalDate,
    completions: List<com.joe.mepe.data.TaskCompletionRecord>,
    goals: List<Goal>,
    tags: List<GoalTag>,
    timeTags: List<com.joe.mepe.data.TimeTag>,
    group: String,
    isDone: Boolean,
    draggingKey: String?,
    dragOffset: Float,
    dropTargetKey: String?,
    onStartDrag: (String) -> Unit,
    onDrag: (Float) -> Unit,
    onEndDrag: () -> Unit,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    onRegister: (String, String, Int) -> Unit,
) {
    val task: TaskItem = when (row) {
        is TaskRow.Main -> row.task
        is TaskRow.Sub -> row.task
    }
    val itemKey = row.key
    val parentId = (row as? TaskRow.Sub)?.parentId
    val indent = parentId != null
    val regGroup = if (parentId != null) "p$parentId" else group
    onRegister(itemKey, regGroup, task.id)
    val done = if (row is TaskRow.Sub) TaskLogic.isDoneOn(task, date, completions) else isDone

    Column(
        Modifier.padding(
            start = if (indent) 36.dp else 12.dp,
            end = 12.dp,
            top = 2.dp,
            bottom = 2.dp
        )
    ) {
        SwipeReveal(
            onEdit = { onEdit(task) }, onDelete = { onDelete(task) },
            locked = draggingKey != null,
        ) {
            DraggableCard(
                itemKey = itemKey,
                draggingKey = draggingKey, dragOffset = dragOffset, dropTargetKey = dropTargetKey,
                onStartDrag = onStartDrag, onDrag = onDrag, onEndDrag = onEndDrag,
            ) {
                TaskCard(
                    task, date, completions, goals, tags, timeTags,
                    subtasks = (row as? TaskRow.Main)?.subs ?: emptyList(),
                    onOpen = onOpen, draggable = true
                )
            }
        }
    }
}

/** 长按拖动手势 + 拖动视觉（抬起、微放大）+ 落点淡蓝虚影 */
@Composable
private fun DraggableCard(
    itemKey: String,
    draggingKey: String?,
    dragOffset: Float,
    dropTargetKey: String?,
    onStartDrag: (String) -> Unit,
    onDrag: (Float) -> Unit,
    onEndDrag: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dragging = draggingKey == itemKey
    val isDropTarget = dropTargetKey == itemKey && draggingKey != null && !dragging
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
    ) {
        Box {
            content()
            // 预计落点：淡蓝色虚影覆盖在目标行上
            if (isDropTarget) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                )
            }
        }
    }
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

/** 任务卡：勾选圈=完成/取消（量化任务=进度+步长）；点卡片=详情；左滑=编辑/删除 */
@Composable
private fun TaskCard(
    task: TaskItem,
    date: LocalDate,
    completions: List<com.joe.mepe.data.TaskCompletionRecord>,
    goals: List<Goal>,
    tags: List<GoalTag>,
    timeTags: List<com.joe.mepe.data.TimeTag>,
    subtasks: List<TaskItem>,
    onOpen: (TaskItem) -> Unit,
    draggable: Boolean = false,
) {
    val done = TaskLogic.isDoneOn(task, date, completions)
    val goal = task.goalId?.let { gid -> goals.find { it.id == gid } }
    val tagColor = goal?.tagId?.let { tid -> tags.find { t -> t.id == tid }?.color }
    // 关联的时间标签：卡片颜色风格（边框/色条/小字/进度条/完成圈）全部跟随标签
    val timeTag = task.timeTagId?.let { id -> timeTags.find { it.id == id } }
    val accent = timeTag?.let { parseHexColor(it.color, MaterialTheme.colorScheme.primary) }
    val doneColor = accent ?: MaterialTheme.colorScheme.primary
    val progress = if (task.type == TaskTypes.QUANTITATIVE && task.quantitativeTarget != null && task.quantitativeTarget!! > 0)
        ((task.quantitativeCurrent ?: 0.0) / task.quantitativeTarget!!).coerceIn(0.0, 1.0) else null

    Card(
        Modifier.fillMaxWidth().clickable { onOpen(task) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            // 已完成也保持不透明（左滑动作层在卡片下层，半透明会透出按钮）
            containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (accent != null) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 时间标签色条（卡片颜色风格随标签）
            if (accent != null) {
                Box(Modifier.width(4.dp).height(34.dp).background(accent, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(9.dp))
            }
            // 打卡圈（完成入口；量化任务点击=进度+步长）
            Box(
                Modifier.size(26.dp)
                    .border(
                        2.dp,
                        if (done) doneColor
                        else accent?.copy(alpha = 0.8f) ?: MaterialTheme.colorScheme.outline,
                        CircleShape
                    )
                    .background(if (done) doneColor else Color.Transparent, CircleShape)
                    .clickable {
                        if (task.type == TaskTypes.QUANTITATIVE) {
                            TaskLogic.adjustQuantitative(task, TaskLogic.quantStep(task))
                            DataBus.bump()
                        } else {
                            TaskLogic.toggleDone(task, date)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (done) Icon(Icons.Filled.Check, "完成", tint = Color.White, modifier = Modifier.size(16.dp))
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
                    if (timeTag != null) append("⏱ ${timeTag.name} · ")
                    if (task.type != TaskTypes.ONE_TIME) append(TaskLogic.patternName(task))
                    if (progress != null) append(" · ${(progress * 100).toInt()}%")
                    if (subtasks.isNotEmpty()) append(" · 子任务 ${subtasks.size}")
                    if (need > 0) append(" · ${completions.count { it.taskId == task.id && it.date == date.toString() }}/$need")
                }
                if (meta.isNotBlank())
                    Text(
                        meta, style = MaterialTheme.typography.bodySmall,
                        color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                if (progress != null) {
                    com.joe.mepe.ui.RoundedProgressBar(
                        progress = progress.toFloat(),
                        modifier = Modifier.padding(top = 6.dp),
                        heightDp = 8,
                        color = doneColor
                    )
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
    timeTags: List<com.joe.mepe.data.TimeTag>,
    onClose: () -> Unit,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
    onOpenSub: (Int) -> Unit = {},
) {
    val rev = DataBus.rev
    val task = rememberData(key = rev) { Repos.tasks().find { it.id == taskId } } ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val completions = remember(rev) { Repos.completions() }
    val subtasks = remember(rev, task.id) {
        Repos.tasks().filter { it.parentTaskId == task.id && !it.isDeleted }
            .sortedBy { it.sortOrder }
    }
    var newSub by remember { mutableStateOf("") }
    var addAmount by remember { mutableStateOf("") }
    var deleteSubTarget by remember { mutableStateOf<TaskItem?>(null) }

    val done = TaskLogic.isDoneOn(task, date, completions)
    val goal = task.goalId?.let { gid -> goals.find { it.id == gid } }
    val tagColor = goal?.tagId?.let { tid -> tags.find { t -> t.id == tid }?.color }
    val timeTag = task.timeTagId?.let { id -> timeTags.find { it.id == id } }
    val accent = timeTag?.let { parseHexColor(it.color, MaterialTheme.colorScheme.primary) }
    val doneColor = accent ?: MaterialTheme.colorScheme.primary

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp)
                        .border(
                            2.dp,
                            if (done) doneColor
                            else accent?.copy(alpha = 0.8f) ?: MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                        .background(if (done) doneColor else Color.Transparent, CircleShape)
                        .clickable {
                            if (task.type == TaskTypes.QUANTITATIVE) {
                                TaskLogic.adjustQuantitative(task, TaskLogic.quantStep(task))
                                DataBus.bump()
                            } else {
                                TaskLogic.toggleDone(task, date)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (done) Icon(Icons.Filled.Check, "完成", tint = Color.White, modifier = Modifier.size(18.dp))
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
                if (timeTag != null) add("时间标签：${timeTag.name}")
                if (task.type != TaskTypes.ONE_TIME) add(TaskLogic.patternName(task))
                task.endDate?.let { add("截止：${it.toLocalDate()}") }
            }
            metaLines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    when {
                        line.startsWith("时间标签") && accent != null -> {
                            com.joe.mepe.ui.ColorDot(accent)
                            Spacer(Modifier.width(8.dp))
                        }
                        line.startsWith("目标") && tagColor != null -> {
                            com.joe.mepe.ui.ColorDot(parseHexColor(tagColor, MaterialTheme.colorScheme.primary))
                            Spacer(Modifier.width(8.dp))
                        }
                        else -> Spacer(Modifier.width(14.dp))
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 打卡图：近 15 周每天完成情况（亮色=当天完成，点击圆圈补卡需回列表对应日期）
            CheckinChart(task, completions, accent ?: MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))

            // 统计（按任务类型展示）：习惯型=打卡率，量化=进度/剩余，一次性=状态/完成时间
            val isHabit = task.type == TaskTypes.PERIODIC || task.type == TaskTypes.RECURRING
            when {
                isHabit -> {
                    val days30 = (29 downTo 0).map { date.minusDays(it.toLong()) }
                    val dueDays = days30.filter { TaskLogic.occursOnDate(task, it) }
                    if (dueDays.isNotEmpty()) {
                        val done30 = dueDays.count { TaskLogic.isDoneOn(task, it, completions) }
                        var streak = 0
                        run {
                            var d = date
                            var guard = 0
                            while (guard++ < 400 && dueDays.contains(d) && TaskLogic.isDoneOn(task, d, completions)) {
                                streak++
                                d = d.minusDays(1)
                            }
                        }
                        com.joe.mepe.ui.StatRow(listOf(
                            Triple("近30天完成", "$done30/${dueDays.size} 天", null),
                            Triple("打卡率", "${done30 * 100 / dueDays.size}%", null),
                            Triple("连续打卡", "$streak 天", null),
                        ))
                        Spacer(Modifier.height(10.dp))
                    }
                }
                task.type == TaskTypes.QUANTITATIVE && task.quantitativeTarget != null && task.quantitativeTarget!! > 0 -> {
                    val qStart = task.quantitativeStart ?: 0.0
                    val qTarget = task.quantitativeTarget!!
                    val qCur = (task.quantitativeCurrent ?: qStart)
                    val qUnit = task.quantitativeUnit ?: ""
                    val pct = if (qTarget > qStart) (((qCur - qStart) / (qTarget - qStart)) * 100).toInt().coerceIn(0, 100) else 0
                    val remain = (qTarget - qCur).coerceAtLeast(0.0)
                    val left = if (qTarget > qStart) qCur - qStart else 0.0
                    com.joe.mepe.ui.StatRow(listOf(
                        Triple("当前进度", "${fmtNum(qCur)}/${fmtNum(qTarget)}${if (qUnit.isNotBlank()) " $qUnit" else ""}", null),
                        Triple("完成度", "$pct%", null),
                        Triple("剩余", "${fmtNum(remain)}${if (qUnit.isNotBlank()) " $qUnit" else ""}", null),
                    ))
                    Spacer(Modifier.height(10.dp))
                }
                task.type == TaskTypes.ONE_TIME -> {
                    val doneDate = task.completedAt?.toLocalDate()?.toString()
                    com.joe.mepe.ui.StatRow(listOf(
                        Triple("状态", if (done) "已完成 ✓" else "进行中", null),
                        Triple("完成于", doneDate ?: "—", null),
                        Triple("截止", task.endDate?.toLocalDate()?.toString() ?: "不限", null),
                    ))
                    Spacer(Modifier.height(10.dp))
                }
            }

            // 量化任务：进度控制（圆圈/滑杆/数值加减，参考 PC：点打卡圈=进度+步长）
            if (task.type == TaskTypes.QUANTITATIVE && task.quantitativeTarget != null && task.quantitativeTarget!! > 0) {
                val start = task.quantitativeStart ?: 0.0
                val target = task.quantitativeTarget!!
                val cur = (task.quantitativeCurrent ?: start)
                val unit = task.quantitativeUnit?.let { " $it" } ?: ""
                val isUpdate = task.quantitativeMode == QuantModes.UPDATE
                Text(
                    "进度 ${fmtNum(cur)} / ${fmtNum(target)}$unit · ${TaskLogic.patternName(task)}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                com.joe.mepe.ui.RoundedProgressBar(
                    progress = ((cur - start) / (target - start)).coerceIn(0.0, 1.0).toFloat(),
                    heightDp = 12
                )
                Spacer(Modifier.height(10.dp))
                // 数值加减行：−1 / 输入数值 / 加N（更新模式=设为N）
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            TaskLogic.adjustQuantitative(task, -1.0)
                            DataBus.bump()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(46.dp, 40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("−1", fontWeight = FontWeight.SemiBold) }
                    OutlinedTextField(
                        value = addAmount,
                        onValueChange = { s -> addAmount = s.filter { it.isDigit() || it == '.' }.take(8) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入数值（默认每次 +1）", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    androidx.compose.material3.Button(
                        onClick = {
                            val n = addAmount.toDoubleOrNull() ?: 1.0
                            if (isUpdate) {
                                // 更新模式：直接设为该值（如体重打卡）
                                TaskLogic.adjustQuantitative(task, n - (task.quantitativeCurrent ?: start))
                            } else {
                                TaskLogic.adjustQuantitative(task, n)
                            }
                            addAmount = ""
                            DataBus.bump()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isUpdate) Text("设为", fontWeight = FontWeight.SemiBold)
                        else Icon(Icons.Filled.Add, "加", modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = ((cur - start) / (target - start)).coerceIn(0.0, 1.0).toFloat(),
                    onValueChange = { f ->
                        task.quantitativeCurrent = start + (target - start) * f.toDouble()
                        Repos.updateTask(task)
                    },
                    onValueChangeFinished = { DataBus.bump() },
                    enabled = target > start
                )
                Text(
                    "提示：点左上角打卡圈 = 进度 +${fmtNum(TaskLogic.quantStep(task))}$unit",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { onOpenSub(sub.id) },
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
                                timeTagId = task.timeTagId,
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

// ============ 打卡图（近 15 周热力格） ============

/** 打卡图的单日完成判定（与列表展示口径一致）：一次性/周期=完成当天；循环=当日打卡记录；量化=当日打卡记录或达标当天 */
private fun doneOnDay(t: TaskItem, d: LocalDate, completions: List<com.joe.mepe.data.TaskCompletionRecord>): Boolean =
    when (t.type) {
        TaskTypes.ONE_TIME, TaskTypes.PERIODIC -> t.completedAt?.toLocalDate() == d
        TaskTypes.QUANTITATIVE ->
            completions.any { it.taskId == t.id && it.date == d.toString() } ||
                t.completedAt?.toLocalDate() == d
        else -> TaskLogic.isDoneOn(t, d, completions)
    }

/** 近 15 周每日完成情况：亮色=当天完成，灰=当天该做未做，淡=无安排/未开始，最后一格为今天 */
@Composable
private fun CheckinChart(
    task: TaskItem,
    completions: List<com.joe.mepe.data.TaskCompletionRecord>,
    accent: Color,
) {
    val today = LocalDate.now()
    val weeks = 15
    val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val start = thisMonday.minusDays(((weeks - 1) * 7).toLong())

    Column {
        Text(
            "打卡图 · 近${weeks}周",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Row {
            // 星期标签列（一 / 三 / 五）
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("一", "", "三", "", "五", "", "日").forEach { lb ->
                    Box(Modifier.size(13.dp), contentAlignment = Alignment.Center) {
                        if (lb.isNotEmpty()) Text(
                            lb, fontSize = 8.sp, lineHeight = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(3.dp))
            repeat(weeks) { w ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(7) { dow ->
                        val d = start.plusDays((w * 7 + dow).toLong())
                        val state = when {
                            d > today -> 0
                            doneOnDay(task, d, completions) -> 2
                            TaskLogic.occursOnDate(task, d) -> 1
                            else -> 0
                        }
                        val bg = when (state) {
                            2 -> accent
                            1 -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = if (d > today) 0.15f else 0.35f
                            )
                        }
                        var cell = Modifier.size(13.dp).background(bg, RoundedCornerShape(3.dp))
                        if (d == today) cell = cell.border(1.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                        Box(cell)
                    }
                }
                Spacer(Modifier.width(2.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChartLegend(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), "无安排")
            Spacer(Modifier.width(10.dp))
            ChartLegend(MaterialTheme.colorScheme.surfaceVariant, "未完成")
            Spacer(Modifier.width(10.dp))
            ChartLegend(accent, "已完成")
        }
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
