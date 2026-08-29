package com.joe.mepe.ui.timetrack

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.FocusSession
import com.joe.mepe.data.Repos
import com.joe.mepe.data.TimeRecord
import com.joe.mepe.data.TimeTag
import com.joe.mepe.ui.BarChart
import com.joe.mepe.ui.ColorDot
import com.joe.mepe.ui.ColorPickerDialog
import com.joe.mepe.ui.DonutChart
import com.joe.mepe.ui.EmptyHint
import com.joe.mepe.ui.LabeledField
import com.joe.mepe.ui.PieSlice
import com.joe.mepe.ui.ProgressRing
import com.joe.mepe.ui.QuickLinks
import com.joe.mepe.ui.Routes
import com.joe.mepe.ui.RoundedProgressBar
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.Segmented
import com.joe.mepe.ui.StatRow
import com.joe.mepe.ui.Stepper
import com.joe.mepe.ui.ToggleRow
import com.joe.mepe.ui.rememberData
import com.joe.mepe.ui.theme.parseHexColor
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ============ 番茄钟引擎（页面切换不丢状态） ============

/** 番茄钟阶段：0=专注 1=短休 2=长休，参数键与桌面端一致 */
object PomodoroEngine {
    var running by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var phase by mutableStateOf(0)
    var startEpochMs by mutableStateOf(0L)
    var accumulatedMs by mutableStateOf(0L)
    var completedRounds by mutableStateOf(0)

    var workMinutes by mutableStateOf(25)
    var shortMinutes by mutableStateOf(5)
    var longMinutes by mutableStateOf(15)
    var beforeLong by mutableStateOf(4)
    var autoStartBreaks by mutableStateOf(true)
    var autoStartPomodoros by mutableStateOf(false)

    // 标签绑定：专注段时间记到工作标签，休息段记到休息标签（未绑定则不记）
    var workTagId by mutableStateOf<Int?>(null)
    var breakTagId by mutableStateOf<Int?>(null)

    fun loadSettings() {
        workMinutes = Repos.getSetting("PomodoroWorkMinutes", "25").toIntOrNull()?.coerceAtLeast(1) ?: 25
        shortMinutes = Repos.getSetting("PomodoroShortBreakMinutes", "5").toIntOrNull()?.coerceAtLeast(1) ?: 5
        longMinutes = Repos.getSetting("PomodoroLongBreakMinutes", "15").toIntOrNull()?.coerceAtLeast(1) ?: 15
        beforeLong = Repos.getSetting("PomodoroBeforeLongBreak", "4").toIntOrNull()?.coerceAtLeast(1) ?: 4
        autoStartBreaks = Repos.getSetting("PomodoroAutoStartBreaks", "1") == "1"
        autoStartPomodoros = Repos.getSetting("PomodoroAutoStartPomodoros", "0") == "1"
        workTagId = Repos.getSetting("pomodoro_work_tag", "").toIntOrNull()
        breakTagId = Repos.getSetting("pomodoro_break_tag", "").toIntOrNull()
    }

    fun saveSettings() {
        Repos.setSetting("PomodoroWorkMinutes", workMinutes.toString())
        Repos.setSetting("PomodoroShortBreakMinutes", shortMinutes.toString())
        Repos.setSetting("PomodoroLongBreakMinutes", longMinutes.toString())
        Repos.setSetting("PomodoroBeforeLongBreak", beforeLong.toString())
        Repos.setSetting("PomodoroAutoStartBreaks", if (autoStartBreaks) "1" else "0")
        Repos.setSetting("PomodoroAutoStartPomodoros", if (autoStartPomodoros) "1" else "0")
        Repos.setSetting("pomodoro_work_tag", workTagId?.toString() ?: "")
        Repos.setSetting("pomodoro_break_tag", breakTagId?.toString() ?: "")
    }

    /** 依据引擎状态对齐标签计时记录：专注→工作标签，休息→休息标签；暂停/停止→结束记录 */
    fun syncRecord() {
        val wantTag = when {
            !running || paused -> null
            phase == 0 -> workTagId
            else -> breakTagId
        }
        val current = Repos.runningRecord()
        when {
            wantTag == null -> if (current != null) Repos.stopTimer(current.tagId)
            current == null || current.tagId != wantTag -> Repos.startTimer(wantTag)
        }
    }

    fun segmentMinutes(): Int = when (phase) { 0 -> workMinutes; 1 -> shortMinutes; else -> longMinutes }

    fun elapsedMs(): Long = when {
        !running || paused -> accumulatedMs
        else -> accumulatedMs + (System.currentTimeMillis() - startEpochMs)
    }

    fun remainingMs(): Long = (segmentMinutes() * 60_000L - elapsedMs()).coerceAtLeast(0)

    fun beginSegment() {
        running = true; paused = false
        startEpochMs = System.currentTimeMillis()
        accumulatedMs = 0L
    }

    fun start() {
        if (!running) beginSegment()
        else if (paused) { paused = false; startEpochMs = System.currentTimeMillis() }
        syncRecord()
    }

    fun pause() {
        if (running && !paused) {
            accumulatedMs += System.currentTimeMillis() - startEpochMs
            paused = true
        }
        syncRecord()
    }

    /** 界面 tick 时检查段是否走完；返回刚完成的专注段分钟数（记一次专注） */
    fun onTick(): Int? {
        if (!running || paused) return null
        if (remainingMs() > 0L) return null
        val wasWork = phase == 0
        var savedMinutes: Int? = null
        if (wasWork) {
            completedRounds += 1
            savedMinutes = workMinutes
            Repos.addFocus(
                FocusSession(
                    mode = 1,
                    duration = java.time.Duration.ofMinutes(workMinutes.toLong()),
                    startTime = LocalDateTime.now().minusMinutes(workMinutes.toLong()),
                    endTime = LocalDateTime.now(),
                    isCompleted = true,
                    notes = "番茄钟"
                )
            )
            phase = if (completedRounds % beforeLong == 0) 2 else 1
            if (autoStartBreaks) beginSegment() else stop()
        } else {
            phase = 0
            if (autoStartPomodoros) beginSegment() else stop()
        }
        syncRecord()
        return savedMinutes
    }

    fun skip() {
        if (!running) return
        if (phase == 0) {
            completedRounds += 1
            phase = if (completedRounds % beforeLong == 0) 2 else 1
        } else phase = 0
        if ((phase == 0 && autoStartPomodoros) || (phase != 0 && autoStartBreaks)) beginSegment()
        else stop()
        syncRecord()
    }

    fun stop() { running = false; paused = false; accumulatedMs = 0L; syncRecord() }
}

fun fmtMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

fun fmtMinutes(min: Long): String = if (min >= 60) "${min / 60}小时${min % 60}分" else "${min}分钟"

// ============ 页面 ============

/** 时间页：运行中计时条 + 番茄钟 + 标签一键计时（带颜色） + 今日记录 + 分布图，整体可滚动 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeTrackScreen(nav: (String) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var showTagManager by remember { mutableStateOf(false) }
    var showStatsSheet by remember { mutableStateOf(false) }
    var showPomoSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        PomodoroEngine.loadSettings()
        while (true) { delay(500); PomodoroEngine.onTick(); tick++ }
    }

    val rev = DataBus.rev
    val ctx = LocalContext.current
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val today = LocalDate.now()
    val tags = remember(rev) { Repos.timeTags() }
    val running = remember(rev, tick) { Repos.runningRecord() }
    val records = remember(rev) { Repos.timeRecords() }

    val todayRecords = records.filter { it.date == today.toString() && it.endTime != null }
    val todayTotalMin = todayRecords.sumOf { it.minutes() }
    val runningTag = running?.let { r -> tags.find { it.id == r.tagId } }

    // 计时开始/停止 与 通知栏前台服务 同步
    fun beginTimer(tagId: Int) {
        Repos.startTimer(tagId)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        com.joe.mepe.data.TimerNotificationService.start(ctx)
    }
    fun endTimer(tagId: Int) {
        Repos.stopTimer(tagId)
        com.joe.mepe.data.TimerNotificationService.stop(ctx)
    }

    val fallbackColor = MaterialTheme.colorScheme.primary
    val distSlices = remember(rev) {
        tags.map { t ->
            PieSlice(
                t.name, todayRecords.filter { it.tagId == t.id }.sumOf { it.minutes() }.toDouble(),
                parseHexColor(t.color, fallbackColor)
            )
        }.filter { it.value > 0 }
    }
    val weekDays = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekTotals = remember(rev) {
        weekDays.map { d -> records.filter { it.date == d.toString() && it.endTime != null }.sumOf { it.minutes() }.toDouble() }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "时间",
            icon = Icons.Filled.Timer,
            subtitle = if (running != null) "正在计时" else "点标签开始计时",
            actions = {
                IconButton(onClick = { showStatsSheet = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Insights, "时间统计", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
                }
                QuickLinks(Routes.TIME, nav)
            }
        )

        StatRow(listOf(
            Triple("今日计时", fmtMinutes(todayTotalMin), null),
            Triple("今日记录", "${todayRecords.size} 条", null),
            Triple("番茄钟", "${PomodoroEngine.completedRounds} 轮", null),
        ))

        LazyColumn(Modifier.fillMaxSize()) {
            // 运行中计时条
            if (running != null && runningTag != null) {
                item(key = "running") {
                    val elapsed = ChronoUnit.SECONDS.between(running.startTime, LocalDateTime.now())
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(parseHexColor(runningTag.color, MaterialTheme.colorScheme.primary), sizeDp = 14)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("正在计时 · ${runningTag.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "已计时 %02d:%02d:%02d".format(elapsed / 3600, elapsed % 3600 / 60, elapsed % 60),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = { endTimer(running.tagId) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Filled.Stop, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("停止")
                            }
                        }
                    }
                }
            }

            // 时间标签（按钮组：点一下开始/停止计时）
            item(key = "sec_tags") {
                SectionLabelRow("标签计时", "点标签开始/停止计时") {
                    TextButton(onClick = { showTagManager = true }) { Text("标签管理") }
                }
            }
            item(key = "tag_chips") {
                if (tags.isEmpty()) {
                    EmptyHint("暂无标签，点「标签管理」添加", Icons.Filled.Timer)
                } else {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { t ->
                            val isRunning = running?.tagId == t.id
                            TimeTagChip(t, isRunning) {
                                if (isRunning) endTimer(t.id) else beginTimer(t.id)
                            }
                        }
                    }
                }
            }

            // 番茄钟
            item(key = "pomodoro") { PomodoroCard(tick = tick, onOpenSettings = { showPomoSettings = true }) }

            // 今日记录
            item(key = "sec_records") { SectionLabelRow("今日记录", null, null) }
            if (todayRecords.isEmpty()) {
                item(key = "no_records") { EmptyHint("今天还没有计时记录") }
            }
            items(todayRecords.size, key = { "r${todayRecords[it].id}" }) { i ->
                val r = todayRecords[i]
                val t = tags.find { it.id == r.tagId }
                RecordRow(r, t?.name ?: "未知", parseHexColor(t?.color, MaterialTheme.colorScheme.primary)) {
                    Repos.deleteTimeRecord(r.id)
                }
            }

            // 分布图
            if (distSlices.isNotEmpty()) {
                item(key = "sec_dist") { SectionLabelRow("今日时间分布", null, null) }
                item(key = "chart_dist") {
                    SectionCard { DonutChart(slices = distSlices, centerText = fmtMinutes(todayTotalMin)) }
                }
            }
            if (weekTotals.any { it > 0 }) {
                item(key = "sec_week") { SectionLabelRow("近 7 日计时", null, null) }
                item(key = "chart_week") {
                    SectionCard {
                        BarChart(
                            values = weekTotals,
                            labels = weekDays.map { "${it.monthValue}/${it.dayOfMonth}" },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item(key = "tail") { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (showTagManager) {
        TimeTagManagerDialog(onClose = { showTagManager = false })
    }
    if (showStatsSheet) {
        TimeStatsSheet(onClose = { showStatsSheet = false })
    }
    if (showPomoSettings) PomodoroSettingsDialog(onClose = { showPomoSettings = false })
}

@Composable
private fun SectionLabelRow(text: String, sub: String?, action: (@Composable () -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        action?.invoke()
    }
}

/** 番茄钟圆形图标按钮 */
@Composable
private fun PomoCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    bg: Color,
    tint: Color,
    sizeDp: Int = 40,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** 番茄钟卡片：大进度环 + 阶段色 + 控制（tick 每 500ms 变化驱动剩余时间重算走字） */
@Composable
private fun PomodoroCard(tick: Int, onOpenSettings: () -> Unit) {
    val _tick = tick // 读取 tick：LazyColumn item 依赖它重组，时间才会走
    val running = PomodoroEngine.running
    val paused = PomodoroEngine.paused
    val phase = PomodoroEngine.phase
    val remaining = PomodoroEngine.remainingMs()
    val total = (PomodoroEngine.segmentMinutes() * 60_000L).coerceAtLeast(1)
    val progress = 1.0 - remaining.toDouble() / total
    val phaseName = when (phase) { 0 -> "专注"; 1 -> "短休息"; else -> "长休息" }
    val phaseColor = when (phase) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val timeTags = remember { Repos.timeTags() }
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("番茄钟", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text(
                "第 ${PomodoroEngine.completedRounds + if (phase == 0 && running) 1 else 0} 轮",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Settings, "番茄钟设置", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        val boundLine = listOfNotNull(
            PomodoroEngine.workTagId?.let { id -> timeTags.find { it.id == id }?.name }?.let { "专注→$it" },
            PomodoroEngine.breakTagId?.let { id -> timeTags.find { it.id == id }?.name }?.let { "休息→$it" }
        ).joinToString(" · ")
        if (boundLine.isNotBlank()) {
            Text(
                boundLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = progress, sizeDp = 118, stroke = 10f, color = phaseColor,
                centerContent = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (!running) "未开始" else if (paused) "已暂停" else fmtMs(remaining),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (running && !paused) phaseColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(phaseName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
            Spacer(Modifier.weight(1f))
            // 图标圆钮列：主控（相位色）+ 跳过 + 重置
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PomoCircleButton(
                    icon = if (!running || paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    desc = if (!running || paused) "开始" else "暂停",
                    bg = if (running && !paused) phaseColor else MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    sizeDp = 46
                ) {
                    if (!running || paused) PomodoroEngine.start() else PomodoroEngine.pause()
                }
                PomoCircleButton(
                    icon = Icons.Filled.SkipNext,
                    desc = "跳过",
                    bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = running
                )
                {
                    PomodoroEngine.skip()
                }
                PomoCircleButton(
                    icon = Icons.Filled.Replay,
                    desc = "重置",
                    bg = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    tint = MaterialTheme.colorScheme.error,
                    enabled = running
                ) {
                    PomodoroEngine.stop()
                }
            }
        }
    }
}

/** 时间标签按钮（只有文字，文字完全静止）：点=开始/停止计时；开始时颜色填充，计时中仅底色明暗呼吸 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeTagChip(tag: TimeTag, isRunning: Boolean, onClick: () -> Unit) {
    val color = parseHexColor(tag.color, MaterialTheme.colorScheme.primary)

    // 计时中：底色在 75%~100% 透明度间缓慢呼吸（只动背景，不动文字/形状）
    val breath = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            breath.snapTo(1f)
            while (true) {
                breath.animateTo(0.75f, tween(850))
                breath.animateTo(1f, tween(850))
            }
        } else {
            breath.snapTo(1f)
        }
    }

    val bg = if (isRunning) color.copy(alpha = breath.value) else color.copy(alpha = 0.13f)
    val border by animateColorAsState(if (isRunning) color else color.copy(alpha = 0.55f), tween(250), label = "border")
    val content by animateColorAsState(if (isRunning) Color.White else color, tween(250), label = "content")

    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            tag.name, color = content,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** 时间标签管理：列表（编辑/删除）+ 新增，编辑弹窗用全色调色盘选色 */
@Composable
private fun TimeTagManagerDialog(onClose: () -> Unit) {
    val rev = DataBus.rev
    val tags = remember(rev) { Repos.timeTags() }
    var editingTag by remember { mutableStateOf<TimeTag?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleteTag by remember { mutableStateOf<TimeTag?>(null) }

    Dialog(onDismissRequest = onClose) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("时间标签管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                if (tags.isEmpty()) EmptyHint("暂无标签")
                LazyColumn(Modifier.height(320.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorDot(parseHexColor(tag.color, MaterialTheme.colorScheme.primary), sizeDp = 14)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                                if (tag.isPreset)
                                    Text("预置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { editingTag = tag }) { Text("编辑") }
                            if (!tag.isPreset)
                                TextButton(onClick = { deleteTag = tag }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { adding = true }) { Text("＋ 新标签") }
                    TextButton(onClick = onClose) { Text("完成") }
                }
            }
        }
    }

    if (adding || editingTag != null) {
        TimeTagEditDialog(initial = editingTag, onClose = { adding = false; editingTag = null })
    }
    deleteTag?.let { t ->
        com.joe.mepe.ui.ConfirmDialog("删除标签", "确定删除「${t.name}」吗？（已有的计时记录会保留）", {
            Repos.deleteTimeTag(t.id)
            deleteTag = null
        }, { deleteTag = null })
    }
}

/** 单条计时记录行 */
@Composable
private fun RecordRow(r: TimeRecord, tagName: String, color: Color, onDelete: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color, sizeDp = 12)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tagName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${r.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - " +
                        (r.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "进行中"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(fmtMinutes(r.minutes()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** 时间标签编辑：名称 + 自定义颜色 + 备注 */
@Composable
private fun TimeTagEditDialog(initial: TimeTag?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var colorHex by remember { mutableStateOf(initial?.color ?: "#4F6EF7") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var picking by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(if (initial == null) "新时间标签" else "编辑标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                LabeledField("标签名称", name, { name = it })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp)
                            .background(parseHexColor(colorHex, MaterialTheme.colorScheme.primary), CircleShape)
                            .clickable { picking = true }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("点击色块选择颜色（可自定义）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                LabeledField("备注（可选）", notes, { notes = it })
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("取消") }
                    Button(
                        onClick = {
                            if (name.isBlank()) return@Button
                            if (initial == null) Repos.addTimeTag(TimeTag(name = name.trim(), color = colorHex, notes = notes.ifBlank { null }))
                            else Repos.updateTimeTag(initial.apply { this.name = name.trim(); this.color = colorHex; this.notes = notes.ifBlank { null } })
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

/** 番茄钟参数（与桌面端设置键互通） */
@Composable
private fun PomodoroSettingsDialog(onClose: () -> Unit) {
    val eng = PomodoroEngine
    val timeTags = remember { Repos.timeTags() }
    var pickFor by remember { mutableStateOf<Int?>(null) } // 0=工作标签 1=休息标签
    Dialog(onDismissRequest = onClose) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text("番茄钟设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Stepper("专注时长（分钟）", eng.workMinutes.toString(), { eng.workMinutes = (eng.workMinutes - 1).coerceAtLeast(1) }, { eng.workMinutes++ })
                Stepper("短休息（分钟）", eng.shortMinutes.toString(), { eng.shortMinutes = (eng.shortMinutes - 1).coerceAtLeast(1) }, { eng.shortMinutes++ })
                Stepper("长休息（分钟）", eng.longMinutes.toString(), { eng.longMinutes = (eng.longMinutes - 1).coerceAtLeast(1) }, { eng.longMinutes++ })
                Stepper("几轮后长休息", eng.beforeLong.toString(), { eng.beforeLong = (eng.beforeLong - 1).coerceAtLeast(1) }, { eng.beforeLong++ })
                ToggleRow("专注后自动开始休息", eng.autoStartBreaks, { eng.autoStartBreaks = it })
                ToggleRow("休息后自动开始专注", eng.autoStartPomodoros, { eng.autoStartPomodoros = it })
                Spacer(Modifier.height(10.dp))
                // 标签绑定：专注段时间记到工作标签，休息段记到休息标签
                TagBindRow("工作标签（专注计时归入）", eng.workTagId, timeTags) { pickFor = 0 }
                Spacer(Modifier.height(6.dp))
                TagBindRow("休息标签（休息计时归入）", eng.breakTagId, timeTags) { pickFor = 1 }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { eng.saveSettings(); onClose() }) { Text("保存") }
                }
            }
        }
    }

    pickFor?.let { which ->
        Dialog(onDismissRequest = { pickFor = null }) {
            Card(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (which == 0) "选择工作标签" else "选择休息标签",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.height(300.dp)) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (which == 0) eng.workTagId = null else eng.breakTagId = null
                                    pickFor = null
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "不绑定（番茄钟不自动计时）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(timeTags, key = { it.id }) { t ->
                            val selected = if (which == 0) eng.workTagId == t.id else eng.breakTagId == t.id
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (which == 0) eng.workTagId = t.id else eng.breakTagId = t.id
                                    pickFor = null
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ColorDot(parseHexColor(t.color, MaterialTheme.colorScheme.primary), sizeDp = 14)
                                Spacer(Modifier.width(10.dp))
                                Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 标签绑定行：显示当前绑定的标签名，点击打开选择 */
@Composable
private fun TagBindRow(label: String, tagId: Int?, tags: List<TimeTag>, onClick: () -> Unit) {
    val name = tagId?.let { id -> tags.find { it.id == id }?.name }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            name ?: "未绑定",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (name != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (name != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier
            .padding(start = 6.dp).size(16.dp))
    }
}

/**
 * 时间统计底部弹窗（从下往上浮出）：周期切换 + 总览统计 + 扇形分布 + 各标签时长 + 当日甘特时间轴 + 近14天趋势。
 * span: 0=今日 1=本周 2=本月 3=全部
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeStatsSheet(onClose: () -> Unit) {
    val rev = DataBus.rev
    var span by remember { mutableStateOf(0) }
    val tags = remember(rev) { Repos.timeTags() }
    val records = remember(rev) { Repos.timeRecords().filter { it.endTime != null } }
    val today = LocalDate.now()

    val (s, e) = when (span) {
        0 -> today to today
        1 -> today.with(DayOfWeek.MONDAY) to today
        2 -> today.withDayOfMonth(1) to today
        else -> null to null
    }
    val inRange = records.filter { r ->
        val d = try { LocalDate.parse(r.date) } catch (_: Exception) { return@filter false }
        (s == null || !d.isBefore(s)) && (e == null || !d.isAfter(e))
    }
    val perTag = tags.map { t ->
        Triple(t, inRange.filter { it.tagId == t.id }.sumOf { it.minutes() },
            parseHexColor(t.color, MaterialTheme.colorScheme.primary))
    }.filter { it.second > 0 }.sortedByDescending { it.second }
    val total = perTag.sumOf { it.second }

    // 日均：按周期覆盖天数
    val spanDays = when (span) {
        0 -> 1L
        1 -> today.dayOfWeek.value.toLong()
        2 -> today.dayOfMonth.toLong()
        else -> records.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .minOrNull()?.let { ChronoUnit.DAYS.between(it, today) + 1 } ?: 1L
    }.coerceAtLeast(1)

    val slices = perTag.map { (t, min, c) -> PieSlice(t.name, min.toDouble(), c) }
    val todayRecords = records.filter { it.date == today.toString() }
    val days14 = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val daily = days14.map { d -> records.filter { it.date == d.toString() }.sumOf { it.minutes() }.toDouble() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text("时间统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("今日", "本周", "本月", "全部"), span) { span = it }
            Spacer(Modifier.height(12.dp))

            StatRow(listOf(
                Triple("总计时长", fmtMinutes(total), null),
                Triple("记录", "${inRange.size} 条", null),
                Triple("日均", fmtMinutes(total / spanDays), null),
            ))
            Spacer(Modifier.height(4.dp))

            SectionCard(title = "时间分布（扇形图）") {
                if (perTag.isEmpty()) EmptyHint("此范围没有计时记录")
                else DonutChart(slices = slices, centerText = fmtMinutes(total))
            }

            SectionCard(title = "各标签时长") {
                if (perTag.isEmpty()) EmptyHint("此范围没有计时记录")
                else perTag.forEach { (t, min, c) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorDot(c, sizeDp = 10)
                        Spacer(Modifier.width(8.dp))
                        Text(t.name, Modifier.width(78.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            fmtMinutes(min), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = c
                        )
                        Spacer(Modifier.width(10.dp))
                        RoundedProgressBar(
                            progress = if (total > 0) min.toFloat() / total else 0f,
                            modifier = Modifier.weight(1f),
                            heightDp = 10,
                            color = c
                        )
                        Text(
                            if (total > 0) " ${(min * 100 / total)}%" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionCard(title = "今日时间轴（甘特图）") {
                DayGanttChart(todayRecords, tags)
            }

            if (daily.any { it > 0 }) {
                SectionCard(title = "近 14 天每日时长") {
                    BarChart(daily, days14.map { "${it.monthValue}/${it.dayOfMonth}" })
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 当日 24 小时甘特时间轴：每标签一行，色块为该标签的计时区间，竖线每 3 小时一条 */
@Composable
private fun DayGanttChart(records: List<TimeRecord>, tags: List<TimeTag>) {
    val groups = tags.map { t -> t to records.filter { it.tagId == t.id }.sortedBy { it.startTime } }
        .filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) {
        EmptyHint("今天还没有计时记录")
        return
    }
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

    Column {
        // 小时刻度头
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(62.dp))
            Row(Modifier.weight(1f)) {
                for (h in 0 until 24 step 3) {
                    Text(
                        "%02d".format(h),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.height(4.dp))
        groups.forEach { (tag, recs) ->
            val color = parseHexColor(tag.color, MaterialTheme.colorScheme.primary)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tag.name, Modifier.width(62.dp),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Box(
                    Modifier.weight(1f).height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        for (i in 0..8) {
                            drawLine(grid, Offset(w * i / 8f, 0f), Offset(w * i / 8f, h), strokeWidth = 1f)
                        }
                        recs.forEach { r ->
                            val startF = r.startTime.toLocalTime().toSecondOfDay() / 86400f
                            val endF = (r.endTime?.toLocalTime() ?: LocalTime.now()).toSecondOfDay() / 86400f
                            val left = (startF.coerceIn(0f, 1f)) * w
                            val barW = (((endF - startF).coerceIn(0f, 1f)) * w).coerceAtLeast(3f)
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(left, 2.dp.toPx()),
                                size = Size(barW, h - 4.dp.toPx()),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "横轴为 0-24 时（竖线每 3 小时），色块 = 该标签的计时区间",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
