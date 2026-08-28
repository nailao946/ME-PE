package com.joe.mepe.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.joe.mepe.data.DataBus
import com.joe.mepe.ui.theme.LocalIconColor
import com.joe.mepe.ui.theme.colorToHex
import com.joe.mepe.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.YearMonth

/** 页面标题栏：可带返回键、单色图标 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    icon: ImageVector? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "返回", tint = LocalIconColor.current)
            }
        }
        if (icon != null) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(LocalIconColor.current.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = LocalIconColor.current, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank())
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** 分组卡片：描边 + 主题面，内容高度变化带动画 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(Modifier.padding(14.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

/** 带图标的分组标题（卡片内用） */
@Composable
fun SectionTitle(icon: ImageVector, text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalIconColor.current, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyHint(text: String, icon: ImageVector? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** 统计卡（一排若干个，等宽对称） */
@Composable
fun StatRow(items: List<Triple<String, String, Color?>>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, value, color) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color ?: MaterialTheme.colorScheme.primary
                    )
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 单行统计（兼容保留） */
@Composable
fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 分段选择器（trailing lambda 传 onSelect） */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.then(Modifier.fillMaxWidth())
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, opt ->
            val active = i == selected
            Box(
                Modifier.weight(1f)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                        MaterialTheme.shapes.extraSmall
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    opt,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** 圆形小按钮（对称的 +/- 步进用） */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalIconColor.current,
) {
    Surface(
        modifier = modifier.size(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else Color.Transparent,
        border = if (enabled) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

/** +/- 步进器 */
@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        RoundIconButton(androidx.compose.material.icons.Icons.Filled.Remove, "减少", onMinus)
        Text(value, Modifier.width(72.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyLarge)
        RoundIconButton(androidx.compose.material.icons.Icons.Filled.Add, "增加", onPlus)
    }
}

/** 带 +/- 的数字输入行 */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onValueChange(s.filter { it.isDigit() || it == '.' }.take(8)) },
        label = { Text(if (suffix != null) "$label（$suffix）" else label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.small
    )
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        shape = MaterialTheme.shapes.small
    )
}

/** 开关行 */
@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 勾选行 */
@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 彩色圆点（标签色） */
@Composable
fun ColorDot(color: Color, sizeDp: Int = 10) {
    Box(Modifier.size(sizeDp.dp).background(color, CircleShape))
}

// ============ 时间选择 ============

@Composable
fun TimePickerDialog(initialHour: Int, initialMinute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var hour by remember { mutableStateOf(initialHour.coerceIn(0, 23)) }
    var minute by remember { mutableStateOf((initialMinute / 5 * 5).coerceIn(0, 59)) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text("选择时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(200.dp)) {
                    LazyColumn(Modifier.weight(1f)) {
                        items(24) { h ->
                            val active = h == hour
                            Text(
                                "%02d".format(h),
                                Modifier.fillMaxWidth()
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                        MaterialTheme.shapes.extraSmall
                                    )
                                    .clickable { hour = h }
                                    .padding(vertical = 6.dp),
                                textAlign = TextAlign.Center,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    Text("时", Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        items(12) { i ->
                            val m = i * 5
                            val active = m == minute
                            Text(
                                "%02d".format(m),
                                Modifier.fillMaxWidth()
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                        MaterialTheme.shapes.extraSmall
                                    )
                                    .clickable { minute = m }
                                    .padding(vertical = 6.dp),
                                textAlign = TextAlign.Center,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    Text("分", Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onConfirm(hour, minute) }) { Text("确定") }
                }
            }
        }
    }
}

/** HH:mm 展示 + 点击弹时间选择 */
@Composable
fun TimeField(label: String, hour: Int, minute: Int, onPick: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text("$label %02d:%02d".format(hour, minute))
    }
    if (show) TimePickerDialog(hour, minute, onPick) { show = false }
}

// ============ 日期选择 ============

@Composable
fun DatePickerDialog(initial: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var picked by remember { mutableStateOf(initial) }
    val today = LocalDate.now()
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(androidx.compose.material.icons.Icons.Filled.ChevronLeft, "上月", tint = LocalIconColor.current)
                    }
                    Text("${month.year}年${month.monthValue}月", Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(androidx.compose.material.icons.Icons.Filled.ChevronRight, "下月", tint = LocalIconColor.current)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val firstDay = month.atDay(1).dayOfWeek.value // 1=周一
                val daysInMonth = month.lengthOfMonth()
                val cells = (1..daysInMonth).map { month.atDay(it) }
                Column(Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState())) {
                    var dayIdx = 1 - firstDay + 1 // 周一为第一列
                    while (dayIdx <= daysInMonth) {
                        Row(Modifier.fillMaxWidth()) {
                            for (dow in 0 until 7) {
                                val d = dayIdx + dow
                                Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                    if (d in 1..daysInMonth) {
                                        val date = cells[d - 1]
                                        val isToday = date == today
                                        val isPicked = date == picked
                                        Box(
                                            Modifier.size(38.dp)
                                                .background(
                                                    when {
                                                        isPicked -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        else -> Color.Transparent
                                                    },
                                                    CircleShape
                                                )
                                                .clickable { picked = date },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "${date.dayOfMonth}",
                                                color = when {
                                                    isPicked -> MaterialTheme.colorScheme.onPrimary
                                                    isToday -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                fontWeight = if (isPicked || isToday) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        dayIdx += 7
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { picked = today }) { Text("今天") }
                    TextButton(onClick = { onConfirm(picked) }) { Text("确定") }
                }
            }
        }
    }
}

@Composable
fun DateField(label: String, date: LocalDate?, onPick: (LocalDate) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, shape = MaterialTheme.shapes.small) {
        Text(if (date != null) "$label ${date}" else "$label 选择日期")
    }
    if (show) DatePickerDialog(date ?: LocalDate.now(), onPick) { show = false }
}

// ============ 确认对话框 ============

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 读取一次数据并在 DataBus.rev 变化时重新计算 */
@Composable
fun <T> rememberData(key: Any? = null, calc: () -> T): T {
    val rev = DataBus.rev
    return androidx.compose.runtime.remember(rev, key) { calc() }
}

/** 目标颜色（与桌面端 GoalColor 枚举顺序一致：红绿蓝粉灰黄） */
fun colorForGoal(colorIdx: Int, fallback: Color): Color = when (colorIdx) {
    0 -> Color(0xFFE5484D)   // Red
    1 -> Color(0xFF2E9E5B)   // Green
    2 -> Color(0xFF4F6EF7)   // Blue
    3 -> Color(0xFFE05C8A)   // Pink
    4 -> Color(0xFF8A8F9E)   // Gray
    5 -> Color(0xFFE0A93C)   // Yellow
    else -> fallback
}

/** 预设调色板（颜色选择器用） */
val ColorPresets = listOf(
    0xFFE5484D, 0xFFE0603C, 0xFFE0A93C, 0xFFD9B23C,
    0xFF7CB342, 0xFF2E9E5B, 0xFF2BA8A8, 0xFF4FC3F7,
    0xFF4F6EF7, 0xFF7C5CE0, 0xFFE05C8A, 0xFF8A8F9E,
)

/** 颜色选择器：预设色板 + 自定义 #RRGGBB 输入 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Color,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    var hex by remember { mutableStateOf(colorToHex(initial)) }
    val current = parseHexColor(hex, initial)
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                ColorPresets.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { c ->
                            val col = Color(c)
                            val selected = colorToHex(col) == colorToHex(current)
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .background(col, CircleShape)
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                                    .clickable { hex = colorToHex(col) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Icon(
                                    Icons.Filled.Check, null,
                                    tint = Color.White, modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(current, MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small))
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { s -> hex = s.take(7) },
                        label = { Text("自定义 #RRGGBB") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onPick(current) }) { Text("确定") }
                }
            }
        }
    }
}
