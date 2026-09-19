package com.joe.mepe.ui.modules

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.joe.mepe.data.CustomModule
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.HtmlLibraryPage
import com.joe.mepe.data.HtmlLibraryRepository
import com.joe.mepe.data.Repos
import com.joe.mepe.ui.ConfirmDialog
import com.joe.mepe.ui.EmptyHint
import com.joe.mepe.ui.FormDialog
import com.joe.mepe.ui.LabeledField
import kotlinx.coroutines.launch

/**
 * 模块资料库：每个自定义模块可以挂若干「个人资料库风格」的 HTML 资料页 + 配套 CSV。
 * 桌面端可让 AI 生成（同 html_library.json），安卓端可直接在 WebView 里打开查看，
 * 数据随云同步在两端互通。
 */
@Composable
fun HtmlLibraryDialog(m: CustomModule, onClose: () -> Unit) {
    val rev = DataBus.rev
    var viewing by remember(rev) { mutableStateOf<HtmlLibraryPage?>(null) }
    var generating by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<HtmlLibraryPage?>(null) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (viewing != null) {
        HtmlPageViewer(viewing!!, onClose = { viewing = null })
        return
    }

    FormDialog(title = "资料库 · ${m.name}", onClose = onClose) {
        Text(
            "把模块做成个人资料库：可让 AI 根据记录生成 HTML 资料页与配套 CSV（离线可开，随云同步互通），也可导入本地 HTML。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        val pages = HtmlLibraryRepository.forModule(m.id)
        if (pages.isEmpty()) {
            EmptyHint("还没有资料页。点下方「AI 生成」让模型基于本模块记录做一页，或导入本地 HTML。")
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            pages.forEach { p ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (p.source == "ai") "AI 生成" else "本地") + " · " + p.updatedAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { viewing = p }, shape = MaterialTheme.shapes.small) {
                            Text("打开")
                        }
                        TextButtonSmall("删除") { deleteTarget = p }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showGenerate = true },
                enabled = !generating,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f)
            ) {
                if (generating) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("生成中…")
                } else {
                    Text("✨ AI 生成资料页")
                }
            }
            OutlinedButton(
                onClick = {
                    // 导入：直接读取一份空的占位页，用户可在桌面端完善；安卓端以 AI 生成为主
                    HtmlLibraryRepository.add(
                        HtmlLibraryPage(
                            moduleId = m.id,
                            title = m.name + " · 手写页",
                            html = emptyTemplate(m),
                            csv = HtmlLibraryRepository.buildDefaultCsv(m),
                            source = "manual",
                        )
                    )
                },
                enabled = !generating,
                shape = MaterialTheme.shapes.small
            ) { Text("新建手写页") }
        }
        if (msg.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showGenerate) {
        GenerateDialog(
            onCancel = { showGenerate = false },
            onGenerate = { hint ->
                showGenerate = false
                generating = true
                msg = "正在生成…（大模型可能需要十几秒）"
                scope.launch {
                    val result = HtmlLibraryRepository.generateWithAi(m, hint, Repos.defaultAiProvider())
                    generating = false
                    msg = result.fold(
                        onSuccess = { "✓ 已生成并保存，可在列表里打开（会随云同步同步到桌面端）。" },
                        onFailure = { "✗ 生成失败：" + (it.message ?: "未知错误") }
                    )
                }
            }
        )
    }

    deleteTarget?.let { p ->
        ConfirmDialog("删除资料页", "确定删除「${p.title}」吗？", {
            HtmlLibraryRepository.delete(p.id)
            deleteTarget = null
        }, { deleteTarget = null })
    }
}

@Composable
private fun TextButtonSmall(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text) }
}

@Composable
private fun GenerateDialog(onCancel: () -> Unit, onGenerate: (String) -> Unit) {
    var hint by remember { mutableStateOf("") }
    FormDialog(title = "AI 生成资料页", onClose = onCancel) {
        Text(
            "描述你想要的页面，AI 会基于本模块的全部记录生成一个可离线打开的 HTML 资料页与配套 CSV。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LabeledField("想做成什么样？（可留空）", hint, { hint = it }, placeholder = "如：做一页可搜索的跑步记录看板")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(onClick = { onGenerate(hint) }, modifier = Modifier.weight(1f)) { Text("开始生成") }
        }
    }
}

/** 用内置 WebView 打开资料页（离线加载，不访问网络） */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlPageViewer(page: HtmlLibraryPage, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Extension, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(page.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = onClose) { Text("关闭") }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp, max = 560.dp)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                loadDataWithBaseURL(null, page.html, "text/html", "utf-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun emptyTemplate(m: CustomModule): String {
    val rows = StringBuilder()
    m.fields.forEach { f -> rows.append("<th>").append(f.label).append(f.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "").append("</th>") }
    val data = StringBuilder()
    m.records.take(50).forEach { r ->
        data.append("<tr><td>").append(r.date).append(" ").append(r.time).append("</td>")
        m.fields.forEach { f -> data.append("<td>").append(r.values[f.key] ?: "").append("</td>") }
        data.append("</tr>")
    }
    return """
<!doctype html><html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${m.name}</title>
<style>
body{font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;background:#f4f5fa;color:#1b1d25;margin:0;padding:24px}
h1{font-size:22px;margin:0 0 4px}.sub{color:#5b5f6e;font-size:13px;margin-bottom:16px}
.card{background:#fff;border:1px solid #e6e8f0;border-radius:16px;padding:16px;box-shadow:0 1px 2px rgba(0,0,0,.04)}
table{width:100%;border-collapse:collapse;font-size:13px}
th,td{padding:8px 10px;border-bottom:1px solid #eef0f5;text-align:left}
th{background:#f7f8fc;color:#5b5f6e;font-weight:600}
tr:last-child td{border-bottom:none}
</style></head><body>
<h1>${m.name} · 资料页</h1><div class="sub">共 ${m.records.size} 条记录 · 由 ME 生成</div>
<div class="card"><table><thead><tr><th>时间</th>$rows</tr></thead><tbody>$data</tbody></table></div>
</body></html>
"""
}
