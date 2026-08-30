package com.joe.mepe.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.joe.mepe.data.CloudSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 云同步状态总线：任务页的状态球从这里读状态，任何入口（状态球点击/下拉刷新/设置页上传下载）
 * 的同步结果都写回这里，球的状态全端一致。
 */
object SyncStatusBus {
    enum class State { IDLE, RUNNING, SUCCESS, FAILED }

    var state by mutableStateOf(State.IDLE)
    var message by mutableStateOf("")

    fun setRunning(msg: String = "正在同步…") {
        state = State.RUNNING
        message = msg
    }

    /** 登记一次结果：✓ 开头 = 成功（绿），否则失败（红） */
    fun report(result: String) {
        message = result
        state = if (result.startsWith("✓")) State.SUCCESS else State.FAILED
    }
}

/**
 * 一次完整同步 = 先上传（防覆盖：云端较新的文件自动跳过）再下载（把云端较新的拉下来）。
 * 返回汇总消息（✓ 开头表示整体成功）；toast=true 时轻提示结果——Toast 不打断界面，
 * 同步过程由状态球呼吸显示。
 */
suspend fun runFullSync(context: Context, toast: Boolean): String = withContext(Dispatchers.Default) {
    if (SyncStatusBus.state == SyncStatusBus.State.RUNNING) return@withContext SyncStatusBus.message
    SyncStatusBus.setRunning()
    val pushMsg = try { CloudSync.push(context) } catch (e: Exception) { "✗ 上传失败：" + (e.message ?: "网络异常") }
    val pullMsg = try { CloudSync.pull(context) } catch (e: Exception) { "✗ 下载失败：" + (e.message ?: "网络异常") }
    fun ok(m: String) = m.startsWith("✓") || m.contains("没有可上传的数据") ||
            m.contains("没有可下载的数据") || m.contains("目录为空")
    val msg = when {
        pushMsg.contains("请先登录") && pullMsg.contains("请先登录") ||
                pushMsg.contains("请先填写") && pullMsg.contains("请先填写") ->
            "✗ 请先在「设置 → 云同步」配置好同步账号（GitHub / Gitee / WebDAV）后再同步"
        ok(pushMsg) && ok(pullMsg) -> {
            val extra = listOf(pushMsg, pullMsg).filter { it.contains("已跳过") || it.contains("比本地新") }
            if (extra.isEmpty()) "✓ 同步完成" else "✓ 同步完成：" + extra.joinToString("；")
        }
        else -> listOf(pushMsg, pullMsg).filter { !ok(it) }.joinToString("；").ifBlank { "✗ 同步失败" }
    }
    SyncStatusBus.report(msg)
    if (toast) {
        try {
            Toast.makeText(context, msg, if (msg.startsWith("✓")) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
        } catch (_: Exception) { }
    }
    msg
}

/**
 * 任务页顶栏的云同步状态球：绿 = 已同步，呼吸绿 = 同步中，红 = 同步失败，灰 = 未同步；
 * 点一下立即触发一次完整同步（结果 Toast 轻提示）。
 */
@Composable
fun SyncBall() {
    val st = SyncStatusBus.state
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val color by animateColorAsState(
        when (st) {
            SyncStatusBus.State.SUCCESS, SyncStatusBus.State.RUNNING -> Color(0xFF22C55E)
            SyncStatusBus.State.FAILED -> Color(0xFFEF4444)
            SyncStatusBus.State.IDLE -> Color(0xFF9CA3AF)
        },
        label = "syncBallColor"
    )
    // 同步中呼吸闪烁（透明度往复），其余状态保持不透明
    val breathe = rememberInfiniteTransition(label = "syncBreathe")
    val breatheAlpha by breathe.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "syncBreatheAlpha"
    )
    IconButton(
        onClick = {
            if (SyncStatusBus.state != SyncStatusBus.State.RUNNING)
                scope.launch { runFullSync(ctx, toast = true) }
        },
        modifier = Modifier.size(36.dp)
    ) {
        Box(
            Modifier
                .size(11.dp)
                .alpha(if (st == SyncStatusBus.State.RUNNING) breatheAlpha else 1f)
                .background(color, CircleShape)
        )
    }
}
