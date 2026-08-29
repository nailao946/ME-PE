package com.joe.mepe.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.Goal
import com.joe.mepe.data.GoalColors
import com.joe.mepe.data.GoalTag
import com.joe.mepe.data.Repos
import com.joe.mepe.data.TaskLogic
import com.joe.mepe.data.TimeFrames
import com.joe.mepe.ui.ColorPickerDialog
import com.joe.mepe.ui.ColorDot
import com.joe.mepe.ui.ConfirmDialog
import com.joe.mepe.ui.DatePickerDialog
import com.joe.mepe.ui.EmptyHint
import com.joe.mepe.ui.FormDialog
import com.joe.mepe.ui.LabeledField
import com.joe.mepe.ui.NumberField
import com.joe.mepe.ui.ProgressRing
import com.joe.mepe.ui.QuickLinks
import com.joe.mepe.ui.Routes
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.Segmented
import com.joe.mepe.ui.StatRow
import com.joe.mepe.ui.ToggleRow
import com.joe.mepe.ui.colorForGoal
import com.joe.mepe.ui.rememberData
import com.joe.mepe.ui.theme.LocalIconColor
import com.joe.mepe.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToLong

private val frameNames = listOf("短期目标", "长期目标", "灵感目标")

/** 目标页一次读取的数据包 */
private data class GoalsData(
    val goals: List<Goal>,
    val tags: List<GoalTag>,
    val tasks: List<com.joe.mepe.data.TaskItem>,
    val timeTags: List<com.joe.mepe.data.TimeTag>,
)

/** 长按拖动手势 + 拖动视觉（抬起、微放大）+ 落点淡蓝虚影 */
@Composable
private fun DragLift(
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
            .androidx_goalGraphics(dragging, dragOffset)
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

private fun Modifier.androidx_goalGraphics(dragging: Boolean, dragOffset: Float): Modifier =
    this.then(
        Modifier.graphicsLayer {
            translationY = if (dragging) dragOffset else 0f
            scaleX = if (dragging) 1.02f else 1f
            scaleY = if (dragging) 1.02f else 1f
            shadowElevation = if (dragging) 24f else 0f
        }
    )

/** 目标展示色：优先自定义 ColorHex，其次枚举 */
fun goalDisplayColor(g: Goal, fallback: Color): Color =
    if (!g.colorHex.isNullOrBlank()) parseHexColor(g.colorHex, fallback)
    else colorForGoal(g.color, fallback)

/** 目标页：三个时间框架分区 + 标签过滤 + 树形层级 + 进度环 + 完整编辑 */
@Composable
fun GoalsScreen(nav: (String) -> Unit) {
    var selectedTagId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Goal?>(null) }
    var editingParentForSub by remember { mutableStateOf<Int?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Goal?>(null) }
    var quantGoal by remember { mutableStateOf<Goal?>(null) }
    var detailGoal by remember { mutableStateOf<Goal?>(null) }
    var expandedIds by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var showTagManager by remember { mutableStateOf(false) }

    val rev = DataBus.rev
    val data = remember(selectedTagId, rev) {
        GoalsData(Repos.goals(), Repos.tags(), Repos.tasks(), Repos.timeTags())
    }
    val (goals, tags, tasks, timeTags) = data
    val today = LocalDate.now()

    // ---- 长按拖动排序状态（同时间框架内的顶级目标） ----
    val listState = rememberLazyListState()
    var draggingGoalKey by remember { mutableStateOf<String?>(null) }
    var goalDragOffset by remember { mutableStateOf(0f) }
    var goalDropTargetKey by remember { mutableStateOf<String?>(null) }
    var goalDropAfter by remember { mutableStateOf(false) }
    val goalRowGroups = remember { mutableMapOf<String, Pair<Int, Int>>() } // key -> (timeFrame, 目标id)
    val haptic = LocalHapticFeedback.current

    /** 把 draggedId 移到 targetId 前/后（同框架内顶级目标），按 sortOrder 持久化 */
    fun moveGoalTo(draggedId: Int, targetId: Int, down: Boolean) {
        val all = Repos.goals(includeDeleted = true)
        val dragged = all.find { it.id == draggedId } ?: return
        val roots = all.filter { it.timeFrame == dragged.timeFrame && it.parentId == null && !it.isDeleted }
            .sortedBy { it.sortOrder }.toMutableList()
        val di = roots.indexOfFirst { it.id == draggedId }
        val ti = roots.indexOfFirst { it.id == targetId }
        if (di < 0 || ti < 0) return
        val item = roots.removeAt(di)
        val insertAt = (roots.indexOfFirst { it.id == targetId } + if (down) 1 else 0).coerceIn(0, roots.size)
        roots.add(insertAt, item)
        roots.forEachIndexed { idx, g -> g.sortOrder = idx; g.updatedAt = java.time.LocalDateTime.now() }
        Repos.saveGoals(all.map { g -> roots.find { it.id == g.id } ?: g })
    }

    /** 拖动中只计算预计落点（虚影行），不实时换位——松手才落位，杜绝抖动 */
    fun processGoalDrag() {
        val key = draggingGoalKey ?: return
        val frame = goalRowGroups[key]?.first ?: return
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val draggedCenter = info.offset + goalDragOffset + info.size / 2f
        val candidates = listState.layoutInfo.visibleItemsInfo
            .filter { it.key != key && goalRowGroups[it.key]?.first == frame }
        if (candidates.isEmpty()) { goalDropTargetKey = null; return }
        val target = candidates.firstOrNull { c ->
            draggedCenter >= c.offset && draggedCenter <= c.offset + c.size
        } ?: candidates.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - draggedCenter) }
            ?: run { goalDropTargetKey = null; return }
        goalDropTargetKey = target.key as? String
        goalDropAfter = draggedCenter > target.offset + target.size / 2f
    }

    /** 松手：按预计落点执行一次重排 */
    fun finishGoalDrag() {
        val key = draggingGoalKey
        if (key != null) {
            val draggedId = goalRowGroups[key]?.second
            val tKey = goalDropTargetKey
            if (draggedId != null && tKey != null) {
                val targetId = goalRowGroups[tKey]?.second
                if (targetId != null && targetId != draggedId) {
                    moveGoalTo(draggedId, targetId, down = goalDropAfter)
                }
            }
        }
        draggingGoalKey = null
        goalDragOffset = 0f
        goalDropTargetKey = null
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "目标",
            icon = Icons.Filled.Flag,
            subtitle = "目标拆解与进度追踪",
            actions = {
                IconButton(onClick = { showTagManager = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Label, "管理标签", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
                }
                QuickLinks(Routes.GOALS, nav)
            }
        )

        // 顶部统计
        StatRow(listOf(
            Triple("个目标", "${goals.count { !it.isArchived }}", null),
            Triple("平均进度", "${if (goals.isNotEmpty()) (goals.map { TaskLogic.goalProgress(it, tasks, today) }.average() * 100).toInt() else 0}%", null),
            Triple("今日完成任务", "${tasks.count { TaskLogic.isDoneOn(it, today, Repos.completions()) }}", null),
        ))

        // 标签过滤
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            item { TagChip("全部", null, selectedTagId == null) { selectedTagId = null } }
            items(tags, key = { it.id }) { tag ->
                TagChip(tag.name, tag.color, selectedTagId == tag.id) {
                    selectedTagId = if (selectedTagId == tag.id) null else tag.id
                }
            }
        }

        val visible = goals.filter { g ->
            g.parentId == null && (selectedTagId == null || g.tagId == selectedTagId)
        }
        // 与桌面端同序：sortOrder 升序，创建时间新的在前
            .sortedWith(compareBy<com.joe.mepe.data.Goal> { it.sortOrder }.thenByDescending { it.createdAt })

        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            if (visible.isEmpty()) {
                item { EmptyHint("还没有目标，点右下角新建", Icons.Filled.Flag) }
            }
            (TimeFrames.SHORT..TimeFrames.INSPIRATION).forEach { frame ->
                val inFrame = visible.filter { it.timeFrame == frame }
                if (inFrame.isNotEmpty()) {
                    item(key = "f$frame") {
                        Text(
                            frameNames[frame],
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(inFrame, key = { "g${it.id}" }) { g ->
                        goalRowGroups["g${g.id}"] = frame to g.id
                        DragLift(
                            itemKey = "g${g.id}",
                            draggingKey = draggingGoalKey, dragOffset = goalDragOffset, dropTargetKey = goalDropTargetKey,
                            onStartDrag = {
                                draggingGoalKey = it; goalDragOffset = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { dy -> goalDragOffset += dy; processGoalDrag() },
                            onEndDrag = { finishGoalDrag() },
                        ) {
                            GoalNode(
                                goal = g, goals = goals, tags = tags, tasks = tasks, timeTags = timeTags, today = today,
                                depth = 0, expandedIds = expandedIds,
                                onToggleExpand = { id ->
                                    expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
                                },
                                onShowDetail = { detailGoal = it },
                                onEdit = { editing = it },
                                onDelete = { deleteTarget = it },
                                onAddSub = { editingParentForSub = it },
                                onQuant = { quantGoal = it },
                            )
                        }
                    }
                }
            }
            item(key = "tail") { Spacer(Modifier.height(96.dp)) }
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
            Icon(Icons.Filled.Add, "新目标", Modifier.size(26.dp))
        }
    }

    if (creating || editing != null || editingParentForSub != null) {
        GoalEditDialog(
            initial = editing,
            parentId = editingParentForSub,
            onClose = { creating = false; editing = null; editingParentForSub = null }
        )
    }
    deleteTarget?.let { g ->
        ConfirmDialog("删除目标", "确定删除「${g.name}」及其子目标吗？", {
            Repos.deleteGoal(g.id)
            deleteTarget = null
        }, { deleteTarget = null })
    }
    if (showTagManager) TagManagerDialog(onClose = { showTagManager = false })
    quantGoal?.let { g -> SubGoalQuantDialog(g, onClose = { quantGoal = null }) }
    detailGoal?.let { g ->
        GoalDetailSheet(
            goal = g, goals = goals, tags = tags, tasks = tasks, timeTags = timeTags, today = today,
            onClose = { detailGoal = null },
            onEdit = { detailGoal = null; editing = g },
            onAddSub = { detailGoal = null; editingParentForSub = g.id },
        )
    }
}

@Composable
private fun TagChip(label: String, color: String?, active: Boolean, onClick: () -> Unit) {
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
            if (color != null) { ColorDot(parseHexColor(color, MaterialTheme.colorScheme.primary)); Spacer(Modifier.width(6.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 目标节点（含子目标递归） */
@Composable
private fun GoalNode(
    goal: Goal,
    goals: List<Goal>,
    tags: List<GoalTag>,
    tasks: List<com.joe.mepe.data.TaskItem>,
    timeTags: List<com.joe.mepe.data.TimeTag>,
    today: LocalDate,
    depth: Int,
    expandedIds: Set<Int>,
    onToggleExpand: (Int) -> Unit,
    onShowDetail: (Goal) -> Unit,
    onEdit: (Goal) -> Unit,
    onDelete: (Goal) -> Unit,
    onAddSub: (Int) -> Unit,
    onQuant: (Goal) -> Unit,
) {
    val children = goals.filter { it.parentId == goal.id && !it.isDeleted }
        .sortedBy { it.createdAt } // 与桌面端一致：子目标按创建先后
    val subTasks = tasks.filter { it.goalId == goal.id && it.parentTaskId == null && !it.isDeleted }
        .sortedWith(compareByDescending<com.joe.mepe.data.TaskItem> { it.priority }.thenBy { it.sortOrder })
    val progress = TaskLogic.goalProgress(goal, tasks, today)
    val goalColor = goalDisplayColor(goal, MaterialTheme.colorScheme.primary)
    val tag = goal.tagId?.let { tid -> tags.find { it.id == tid } }
    val boundTimeTag = goal.timeTagId?.let { id -> timeTags.find { it.id == id } }
    // 绑定了时间标签 → 目标卡颜色（进度环/圆点/小字/进度条）跟随标签色
    val color = boundTimeTag?.let { parseHexColor(it.color, MaterialTheme.colorScheme.primary) } ?: goalColor
    val expanded = goal.id !in expandedIds // 默认展开，点按折叠

    Column(Modifier.padding(horizontal = if (depth == 0) 12.dp else 0.dp, vertical = 4.dp)) {
        com.joe.mepe.ui.SwipeReveal(onEdit = { onEdit(goal) }, onDelete = { onDelete(goal) }) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(start = (depth * 14).dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(Modifier.padding(12.dp)) {
                // 点击整卡 → 目标详情弹窗（展开/折叠走右侧箭头）
                Row(
                    Modifier.fillMaxWidth().clickable { onShowDetail(goal) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProgressRing(
                        progress = progress, sizeDp = 46, stroke = 7f, color = color,
                        centerContent = {
                            Text("${(progress * 100).toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(color, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(goal.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (goal.isArchived) {
                                Spacer(Modifier.width(6.dp))
                                Text("已归档", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val meta = buildString {
                            if (children.isNotEmpty()) append("子目标 ${children.size} · ")
                            if (subTasks.isNotEmpty()) append("任务 ${subTasks.size} · ")
                            if (boundTimeTag != null) append("⏱ ${boundTimeTag.name} · ")
                            goal.endDate?.let { append("截止 ${it.toLocalDate()} · ") }
                            if (goal.quantitativeTarget != null && goal.quantitativeTarget!! > 0)
                                append("量化 ${(goal.quantitativeCurrent ?: 0.0)}/${goal.quantitativeTarget}${goal.quantitativeUnit?.let { " $it" } ?: ""}")
                            else if (tag != null) append("标签 ${tag.name}")
                        }
                        if (meta.isNotBlank()) Text(
                            meta.removeSuffix(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = color
                        )
                    }
                    if (children.isNotEmpty()) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            "展开/折叠",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onToggleExpand(goal.id) }
                        )
                    }
                    if (children.isEmpty() && subTasks.isEmpty()) {
                        IconButton(onClick = { onAddSub(goal.id) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Add, "添加子目标", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        }
        // 子目标以任务行的形式展示：可勾选完成、点击弹出量化/进度窗口
        if (children.isNotEmpty() && expanded) {
            children.forEach { child ->
                SubGoalRow(child, goals, tags, tasks, timeTags, today, depth + 1, expandedIds, onToggleExpand, onEdit, onDelete, onAddSub, onQuant)
            }
        }
    }
}

/** 子目标任务行：左侧勾选完成；点击行弹出量化/进度窗口；右侧笔=编辑 */
@Composable
private fun SubGoalRow(
    goal: Goal,
    goals: List<Goal>,
    tags: List<GoalTag>,
    tasks: List<com.joe.mepe.data.TaskItem>,
    timeTags: List<com.joe.mepe.data.TimeTag>,
    today: LocalDate,
    depth: Int,
    expandedIds: Set<Int>,
    onToggleExpand: (Int) -> Unit,
    onEdit: (Goal) -> Unit,
    onDelete: (Goal) -> Unit,
    onAddSub: (Int) -> Unit,
    onQuant: (Goal) -> Unit,
) {
    // 绑定了时间标签 → 行颜色（勾选圈/小字/进度条）跟随标签色
    val color = goal.timeTagId?.let { id -> timeTags.find { it.id == id } }
        ?.let { parseHexColor(it.color, MaterialTheme.colorScheme.primary) }
        ?: goalDisplayColor(goal, MaterialTheme.colorScheme.primary)
    val progress = TaskLogic.goalProgress(goal, tasks, today)
    val done = progress >= 0.999
    val children = goals.filter { it.parentId == goal.id && !it.isDeleted }
        .sortedBy { it.createdAt } // 与桌面端一致：子目标按创建先后
    val quant = goal.quantitativeTarget != null && goal.quantitativeTarget!! > 0

    Column(Modifier.padding(start = (14 + depth * 14).dp, end = 2.dp, top = 2.dp, bottom = 2.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable { onQuant(goal) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 完成勾选圈
            Box(
                Modifier
                    .size(22.dp)
                    .border(2.dp, if (done) color else MaterialTheme.colorScheme.outline, CircleShape)
                    .background(if (done) color else Color.Transparent, CircleShape)
                    .clickable {
                        if (quant) {
                            goal.quantitativeCurrent =
                                if (done) (goal.quantitativeStart ?: 0.0) else goal.quantitativeTarget
                        } else {
                            goal.progress = if (done) 0.0 else 1.0
                        }
                        Repos.updateGoal(goal)
                        DataBus.bump()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (done) Icon(Icons.Filled.Check, "完成", tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                val meta = buildString {
                    if (quant) append("量化 ${(goal.quantitativeCurrent ?: 0.0)}/${goal.quantitativeTarget}${goal.quantitativeUnit?.let { " $it" } ?: ""}")
                    else append("进度 ${(progress * 100).toInt()}%")
                    if (children.isNotEmpty()) append(" · 子目标 ${children.size}")
                }
                Text(meta, style = MaterialTheme.typography.labelSmall, color = color)
                Spacer(Modifier.height(4.dp))
                // 进度条（量化=数值进度，普通=百分比进度）
                com.joe.mepe.ui.RoundedProgressBar(
                    progress = progress.toFloat(),
                    heightDp = 8,
                    color = color
                )
            }
            Box(
                Modifier.size(28.dp).clickable { onEdit(goal) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
        }
        if (children.isNotEmpty() && goal.id !in expandedIds) {
            children.forEach { c ->
                SubGoalRow(c, goals, tags, tasks, timeTags, today, depth + 1, expandedIds, onToggleExpand, onEdit, onDelete, onAddSub, onQuant)
            }
        }
    }
}

/** 子目标量化/进度窗口：量化目标加减数值，普通目标调进度百分比 */
@Composable
private fun SubGoalQuantDialog(g0: Goal, onClose: () -> Unit) {
    val rev = DataBus.rev
    val g = remember(rev) { Repos.goals().find { it.id == g0.id } } ?: return
    val quant = g.quantitativeTarget != null && g.quantitativeTarget!! > 0
    val start = g.quantitativeStart ?: 0.0
    val target = g.quantitativeTarget ?: 1.0
    val cur = g.quantitativeCurrent ?: start
    val step = 1.0

    Dialog(onDismissRequest = onClose) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                if (quant) {
                    Text(
                        "${fmtG(cur)} / ${fmtG(target)}${g.quantitativeUnit?.let { " $it" } ?: ""}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.Slider(
                        value = ((cur - start) / (target - start)).coerceIn(0.0, 1.0).toFloat(),
                        onValueChange = { f ->
                            g.quantitativeCurrent = start + (target - start) * f.toDouble()
                            Repos.updateGoal(g)
                        },
                        onValueChangeFinished = { DataBus.bump() },
                        enabled = target > start
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { g.quantitativeCurrent = (g.quantitativeCurrent ?: start) - step; Repos.updateGoal(g); DataBus.bump() },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("−1") }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { g.quantitativeCurrent = (g.quantitativeCurrent ?: start) + step; Repos.updateGoal(g); DataBus.bump() },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("+1") }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { g.quantitativeCurrent = (g.quantitativeCurrent ?: start) + 5; Repos.updateGoal(g); DataBus.bump() },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("+5") }
                        androidx.compose.material3.Button(
                            onClick = { g.quantitativeCurrent = target; Repos.updateGoal(g); DataBus.bump() },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("完成") }
                    }
                } else {
                    Text(
                        "进度 ${(g.progress.coerceIn(0.0, 1.0) * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.Slider(
                        value = g.progress.coerceIn(0.0, 1.0).toFloat(),
                        onValueChange = { f -> g.progress = f.toDouble(); Repos.updateGoal(g) },
                        onValueChangeFinished = { DataBus.bump() }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.0, 0.25, 0.5, 0.75, 1.0).forEach { p ->
                            androidx.compose.material3.OutlinedButton(
                                onClick = { g.progress = p; Repos.updateGoal(g); DataBus.bump() },
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("${(p * 100).toInt()}%") }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("关闭") }
                }
            }
        }
    }
}

private fun fmtG(d: Double): String =
    if (kotlin.math.abs(d - d.roundToLong()) < 0.001) d.roundToLong().toString() else "%.1f".format(d)

/** 标签管理（增删改 + 自定义颜色） */
@Composable
fun TagManagerDialog(onClose: () -> Unit) {
    val tags = rememberData { Repos.tags().toList() }
    var adding by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<GoalTag?>(null) }
    var deleteTag by remember { mutableStateOf<GoalTag?>(null) }

    Dialog(onDismissRequest = onClose) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Label, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("管理标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                if (tags.isEmpty()) EmptyHint("暂无标签")
                LazyColumn(Modifier.height(300.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorDot(parseHexColor(tag.color, MaterialTheme.colorScheme.primary), sizeDp = 14)
                            Spacer(Modifier.width(10.dp))
                            Text(tag.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { editingTag = tag }) { Text("编辑") }
                            TextButton(onClick = { deleteTag = tag }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { adding = true }) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新标签")
                    }
                    TextButton(onClick = onClose) { Text("完成") }
                }
            }
        }
    }

    if (adding || editingTag != null) {
        TagEditDialog(initial = editingTag, onClose = { adding = false; editingTag = null })
    }
    deleteTag?.let { t ->
        ConfirmDialog("删除标签", "确定删除标签「${t.name}」吗？", {
            Repos.deleteTag(t.id)
            deleteTag = null
        }, { deleteTag = null })
    }
}

@Composable
private fun TagEditDialog(initial: GoalTag?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var colorHex by remember { mutableStateOf(initial?.color ?: "#4F6EF7") }
    var picking by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(if (initial == null) "新标签" else "编辑标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                LabeledField("标签名称", name, { name = it })
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(parseHexColor(colorHex, MaterialTheme.colorScheme.primary), CircleShape)
                            .clickable { picking = true }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("点击色块选择颜色（可自定义）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("取消") }
                    Button(
                        onClick = {
                            if (name.isBlank()) return@Button
                            if (initial == null) Repos.addTag(GoalTag(name = name.trim(), color = colorHex))
                            else Repos.updateTag(initial.apply { this.name = name.trim(); this.color = colorHex })
                            onClose()
                        },
                        enabled = name.isNotBlank()
                    ) { Text("保存") }
                }
            }
        }
    }
    if (picking) {
        ColorPickerDialog(
            title = "标签颜色",
            initial = parseHexColor(colorHex, MaterialTheme.colorScheme.primary),
            onPick = { colorHex = com.joe.mepe.ui.theme.colorToHex(it); picking = false },
            onDismiss = { picking = false }
        )
    }
}

/** 目标编辑对话框：完整字段 + 自定义颜色 */
@Composable
fun GoalEditDialog(initial: Goal?, parentId: Int? = null, onClose: () -> Unit) {
    val isNew = initial == null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var frame by remember { mutableStateOf(initial?.timeFrame ?: TimeFrames.SHORT) }
    var colorIdx by remember { mutableStateOf(initial?.color ?: GoalColors.BLUE) }
    var colorHex by remember { mutableStateOf(initial?.colorHex) }
    var tagId by remember { mutableStateOf(initial?.tagId) }
    var goalTimeTagId by remember { mutableStateOf(initial?.timeTagId) }
    var parentGoalId by remember { mutableStateOf(initial?.parentId ?: parentId) }
    var startDate by remember { mutableStateOf(initial?.startDate?.toLocalDate()) }
    var endDate by remember { mutableStateOf(initial?.endDate?.toLocalDate()) }
    var useQuant by remember { mutableStateOf(initial?.quantitativeTarget != null) }
    var quantTarget by remember { mutableStateOf(initial?.quantitativeTarget?.let { it.toString() } ?: "") }
    var quantUnit by remember { mutableStateOf(initial?.quantitativeUnit ?: "") }
    var archived by remember { mutableStateOf(initial?.isArchived ?: false) }
    var showDatePicker by remember { mutableStateOf<String?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }

    val tags = rememberData { Repos.tags().toList() }
    val timeTags = rememberData { Repos.timeTags().toList() }
    val parentChoices = rememberData { Repos.goals().filter { it.parentId == null } }

    FormDialog(title = if (isNew) "新建目标" else "编辑目标", onClose = onClose) {
                LabeledField("目标名称", name, { name = it })
                Spacer(Modifier.height(8.dp))
                LabeledField("描述（可选）", desc, { desc = it }, singleLine = false)
                Spacer(Modifier.height(10.dp))
                Segmented(frameNames, frame) { frame = it }
                Spacer(Modifier.height(10.dp))
                Text("颜色", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (0..5).forEach { i ->
                        val c = colorForGoal(i, MaterialTheme.colorScheme.primary)
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(30.dp)
                                .background(c, CircleShape)
                                .clickable { colorIdx = i; colorHex = null },
                            contentAlignment = Alignment.Center
                        ) {
                            if (i == colorIdx && colorHex == null)
                                Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    // 自定义颜色：图标圆球（窄屏放不下文字按钮，用颜料盘图标代替）
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { showColorPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Palette, "自定义颜色",
                            tint = LocalIconColor.current, modifier = Modifier.size(16.dp)
                        )
                    }
                    if (colorHex != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(30.dp).background(parseHexColor(colorHex, MaterialTheme.colorScheme.primary), CircleShape))
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 标签
                if (tags.isNotEmpty()) {
                    Text("标签", style = MaterialTheme.typography.titleSmall)
                    LazyRow(Modifier.padding(vertical = 4.dp)) {
                        item { TagChip("无", null, tagId == null) { tagId = null } }
                        items(tags, key = { it.id }) { t ->
                            TagChip(t.name, t.color, tagId == t.id) { tagId = if (tagId == t.id) null else t.id }
                        }
                    }
                }
                // 绑定时间标签（该目标下的任务默认继承）
                Text("绑定时间标签（任务默认继承）", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                LazyRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { TagChip("无", null, goalTimeTagId == null) { goalTimeTagId = null } }
                    items(timeTags, key = { it.id }) { t ->
                        TagChip(t.name, t.color, goalTimeTagId == t.id) {
                            goalTimeTagId = if (goalTimeTagId == t.id) null else t.id
                        }
                    }
                }
                // 父目标
                if (parentChoices.isNotEmpty() && parentChoices.any { it.id != initial?.id }) {
                    Text("父目标（可空）", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    var parentMenu by remember { mutableStateOf(false) }
                    androidx.compose.material3.OutlinedButton(onClick = { parentMenu = true }, shape = MaterialTheme.shapes.small) {
                        Text(parentGoalId?.let { pid -> parentChoices.find { it.id == pid }?.name } ?: "无（顶级目标）")
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = parentMenu, onDismissRequest = { parentMenu = false }) {
                        DropdownMenuItem(text = { Text("无（顶级目标）") }, onClick = { parentGoalId = null; parentMenu = false })
                        parentChoices.filter { it.id != initial?.id }.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { parentGoalId = p.id; parentMenu = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showDatePicker = "start" },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (startDate != null) "开始 $startDate" else "开始日期") }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showDatePicker = "end" },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (endDate != null) "截止 $endDate" else "截止日期") }
                }
                Spacer(Modifier.height(4.dp))
                ToggleRow("量化目标", useQuant, { useQuant = it }, sub = "用数值追踪进度")
                if (useQuant) {
                    NumberField("目标值", quantTarget, { quantTarget = it })
                    LabeledField("单位", quantUnit, { quantUnit = it }, placeholder = "如：本书 / km")
                }
                if (!isNew) ToggleRow("归档（不再展示于进行中）", archived, { archived = it })
                Spacer(Modifier.height(16.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    onClick = {
                        if (name.isBlank()) return@Button
                        val g = (initial ?: Goal()).apply {
                            this.name = name.trim()
                            this.description = desc.ifBlank { null }
                            this.timeFrame = frame
                            this.color = colorIdx
                            this.colorHex = colorHex
                            this.tagId = tagId
                            this.timeTagId = goalTimeTagId
                            this.parentId = parentGoalId
                            this.startDate = startDate?.atStartOfDay()
                            this.endDate = endDate?.atTime(23, 59)
                            this.isArchived = archived
                            if (isNew) this.createdAt = LocalDateTime.now()
                            this.updatedAt = LocalDateTime.now()
                            this.quantitativeTarget = if (useQuant) quantTarget.toDoubleOrNull() else null
                            this.quantitativeUnit = if (useQuant) quantUnit.ifBlank { null } else null
                            this.quantitativeStart = if (useQuant) (this.quantitativeStart ?: 0.0) else null
                        }
                        if (isNew) Repos.addGoal(g) else Repos.updateGoal(g)
                        onClose()
                    },
                    enabled = name.isNotBlank()
                ) { Text("保存") }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            title = "目标颜色",
            initial = if (colorHex != null) parseHexColor(colorHex, MaterialTheme.colorScheme.primary)
            else colorForGoal(colorIdx, MaterialTheme.colorScheme.primary),
            onPick = { colorHex = com.joe.mepe.ui.theme.colorToHex(it); showColorPicker = false },
            onDismiss = { showColorPicker = false }
        )
    }
    showDatePicker?.let { which ->
        DatePickerDialog(
            initial = if (which == "start") (startDate ?: LocalDate.now()) else (endDate ?: LocalDate.now()),
            onConfirm = { d ->
                if (which == "start") startDate = d else endDate = d
                showDatePicker = null
            },
            onDismiss = { showDatePicker = null }
        )
    }
}

/** 对话框内容超出时可竖向滚动 */
@Composable
private fun Modifier.androidx_verticalScroll(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

/** 目标详情信息行 */
@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(
            label, Modifier.width(92.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value, Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
        )
    }
}

/** 目标详情底部弹窗：点击目标卡弹出，汇总展示目标全部信息 + 快捷编辑/添加子目标 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDetailSheet(
    goal: Goal,
    goals: List<Goal>,
    tags: List<GoalTag>,
    tasks: List<com.joe.mepe.data.TaskItem>,
    timeTags: List<com.joe.mepe.data.TimeTag>,
    today: LocalDate,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onAddSub: () -> Unit,
) {
    val rev = DataBus.rev
    val children = remember(rev) {
        goals.filter { it.parentId == goal.id && !it.isDeleted }.sortedBy { it.createdAt }
    }
    val subTasks = remember(rev) {
        tasks.filter { it.goalId == goal.id && it.parentTaskId == null && !it.isDeleted }
            .sortedWith(compareByDescending<com.joe.mepe.data.TaskItem> { it.priority }.thenBy { it.sortOrder })
    }
    val progress = TaskLogic.goalProgress(goal, tasks, today)
    val boundTimeTag = goal.timeTagId?.let { id -> timeTags.find { it.id == id } }
    val color = boundTimeTag?.let { parseHexColor(it.color, MaterialTheme.colorScheme.primary) }
        ?: goalDisplayColor(goal, MaterialTheme.colorScheme.primary)
    val tag = goal.tagId?.let { tid -> tags.find { it.id == tid } }
    val quant = goal.quantitativeTarget != null && goal.quantitativeTarget!! > 0
    val doneCount = subTasks.count { TaskLogic.isDoneOn(it, today, Repos.completions()) }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = false)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = progress, sizeDp = 54, stroke = 8f, color = color,
                    centerContent = {
                        Text("${(progress * 100).toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        frameNames[goal.timeFrame] + if (goal.isArchived) " · 已归档" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            goal.description?.takeIf { it.isNotBlank() }?.let { DetailInfoRow("描述", it) }
            if (tag != null) DetailInfoRow("标签", tag.name)
            if (boundTimeTag != null) DetailInfoRow("绑定时间标签", boundTimeTag.name)
            goal.startDate?.let { DetailInfoRow("开始", it.toLocalDate().toString()) }
            goal.endDate?.let { DetailInfoRow("截止", it.toLocalDate().toString()) }
            if (quant) DetailInfoRow(
                "量化进度",
                "${fmtG(goal.quantitativeCurrent ?: 0.0)} / ${fmtG(goal.quantitativeTarget!!)}${goal.quantitativeUnit?.let { " $it" } ?: ""}"
            )
            if (children.isNotEmpty()) DetailInfoRow("子目标", "${children.size} 个：" + children.joinToString("、") { it.name })
            if (subTasks.isNotEmpty()) DetailInfoRow("任务", "$doneCount/${subTasks.size} 已完成")
            DetailInfoRow("创建于", goal.createdAt.toLocalDate().toString())
            Spacer(Modifier.height(10.dp))
            com.joe.mepe.ui.RoundedProgressBar(
                progress = progress.toFloat(), heightDp = 10, color = color
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onAddSub, modifier = Modifier.weight(1f)) {
                    Text("添加子目标")
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("编辑") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
