package com.joe.mepe.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 折线图：平滑曲线 + 渐变填充 + 画布内坐标标签，随主题配色 */
@Composable
fun LineChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    overlay: List<Pair<List<Double>, Color>> = emptyList(),
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = onSurfaceVariant)
    val all = (values + overlay.flatMap { it.first })
    if (all.size < 2) {
        Text(
            if (all.isEmpty()) "暂无数据" else "数据不足（至少 2 个点）",
            modifier = modifier.padding(vertical = 24.dp),
            color = onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }
    Column(modifier = modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val leftPad = 34.dp.toPx()
            val bottomPad = 18.dp.toPx()
            val w = size.width - leftPad
            val h = size.height - bottomPad
            val minV = all.min(); val maxV = all.max()
            val span = (maxV - minV).takeIf { it > 1e-9 } ?: 1.0
            val topPad = 8.dp.toPx()
            fun y(v: Double): Float {
                val t = ((v - minV) / span).toFloat()
                return topPad + (1f - t) * (h - topPad)
            }
            fun x(i: Int) = leftPad + i * (w / (values.size - 1).coerceAtLeast(1))

            // 横向网格 + Y 轴刻度
            for (g in 0..3) {
                val gy = topPad + g * (h - topPad) / 3f
                drawLine(gridColor, Offset(leftPad, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
                val vLabel = maxV - (span * g / 3.0)
                val text = if (maxV <= 10 && span > 1) "%.1f".format(vLabel) else "%.0f".format(vLabel)
                drawText(
                    measurer, text, topLeft = Offset(0f, gy - 7.dp.toPx()),
                    style = labelStyle, maxLines = 1
                )
            }
            // X 轴标签（均匀抽稀）
            if (values.isNotEmpty()) {
                val step = ((values.size - 1) / 5).coerceAtLeast(1)
                val idxs = (values.indices step step).toList() + listOf(values.size - 1)
                idxs.distinct().forEach { i ->
                    if (labels.isNotEmpty()) {
                        val lb = labels.getOrNull(i) ?: return@forEach
                        drawText(
                            measurer, lb,
                            topLeft = Offset((x(i) - 12.dp.toPx()).coerceAtLeast(0f), size.height - 14.dp.toPx()),
                            style = labelStyle, maxLines = 1
                        )
                    }
                }
            }
            // 覆盖系列（虚线）
            overlay.forEach { (series, c) ->
                if (series.size >= 2) {
                    val p = Path()
                    p.moveTo(x(0), y(series[0]))
                    for (i in 1 until series.size) p.lineTo(x(i), y(series[i]))
                    drawPath(
                        p, c, style = Stroke(
                            3.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                    )
                }
            }
            // 主系列：平滑贝塞尔 + 渐变填充
            if (values.size >= 2) {
                val path = Path()
                path.moveTo(x(0), y(values[0]))
                for (i in 1 until values.size) {
                    val x0 = x(i - 1); val y0 = y(values[i - 1])
                    val x1 = x(i); val y1 = y(values[i])
                    val cx = (x0 + x1) / 2f
                    path.cubicTo(cx, y0, cx, y1, x1, y1)
                }
                val fill = Path().apply {
                    addPath(path)
                    lineTo(x(values.size - 1), h)
                    lineTo(x(0), h)
                    close()
                }
                drawPath(
                    fill,
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                        startY = topPad, endY = h
                    )
                )
                drawPath(path, color, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
                if (values.size <= 24) values.forEachIndexed { i, v ->
                    drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(x(i), y(v)))
                    drawCircle(color, radius = 5.5f, center = Offset(x(i), y(v)), style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}

/** 柱状图：圆角 + 最高值高亮 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = onSurfaceVariant)
    if (values.isEmpty() || values.all { it <= 0.0 }) {
        Text("暂无数据", modifier = modifier.padding(24.dp), color = onSurfaceVariant, textAlign = TextAlign.Center)
        return
    }
    Column(modifier = modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val bottomPad = 18.dp.toPx()
            val h = size.height - bottomPad
            val maxV = values.max().takeIf { it > 0 } ?: 1.0
            val maxIdx = values.indices.maxBy { values[it] }
            val gap = size.width * 0.025f
            val barW = (size.width - gap * (values.size + 1)) / values.size
            values.forEachIndexed { i, v ->
                val bh = (h * (v / maxV)).toFloat()
                val c = if (i == maxIdx && values.size > 1) color else color.copy(alpha = 0.55f)
                if (bh > 1f) drawRoundRect(
                    color = c,
                    topLeft = Offset(gap + i * (barW + gap), h - bh),
                    size = Size(barW, bh),
                    cornerRadius = CornerRadius(barW / 2.4f, barW / 2.4f)
                )
                if (labels.isNotEmpty() && values.size <= 10) {
                    val lb = labels.getOrNull(i) ?: return@forEachIndexed
                    drawText(
                        measurer, lb,
                        topLeft = Offset(gap + i * (barW + gap) + barW / 2 - 12.dp.toPx(), size.height - 14.dp.toPx()),
                        style = labelStyle, maxLines = 1
                    )
                }
            }
        }
        if (values.size > 10 && labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(labels.first(), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
                Text(labels.last(), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
            }
        }
    }
}

data class PieSlice(val label: String, val value: Double, val color: Color)

/** 环形图（时间分布等），支持中心内容槽 */
@Composable
fun DonutChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier,
    centerText: String = "",
    onSliceClick: (PieSlice) -> Unit = {},
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(170.dp).padding(10.dp)) {
                val strokeW = 30.dp.toPx()
                var start = -90f
                val gapDeg = if (slices.size > 1) 2.5f else 0f
                slices.forEach { s ->
                    val sweep = ((s.value / total * 360.0).toFloat() - gapDeg).coerceAtLeast(0.5f)
                    drawArc(
                        color = s.color,
                        startAngle = start + gapDeg / 2, sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                        topLeft = Offset(strokeW / 2, strokeW / 2),
                        size = Size(size.width - strokeW, size.height - strokeW)
                    )
                    start += sweep + gapDeg
                }
            }
            if (centerText.isNotEmpty()) {
                Text(centerText, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        slices.sortedByDescending { it.value }.take(8).forEach { s ->
            val pct = (s.value / total * 100)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(10.dp)) { drawCircle(s.color) }
                Spacer(Modifier.width(8.dp))
                Text(s.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    "${s.value.toInt()} 分钟 · ${"%.0f".format(pct)}%",
                    style = MaterialTheme.typography.bodySmall, color = onSurfaceVariant
                )
            }
        }
    }
}

/** 进度环（进度变化带动画） */
@Composable
fun ProgressRing(
    progress: Double,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    stroke: Float = 10f,
    sizeDp: Int = 56,
    centerContent: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val anim by animateFloatAsState(
        targetValue = progress.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = tween(600), label = "ring"
    )
    androidx.compose.foundation.layout.Box(
        modifier.then(Modifier.size(sizeDp.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(sizeDp.dp)) {
            drawArc(track, -90f, 360f, false, style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke))
            drawArc(color, -90f, anim * 360f, false, style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke))
        }
        centerContent()
    }
}
