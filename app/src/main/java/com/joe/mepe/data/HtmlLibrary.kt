package com.joe.mepe.data

import com.joe.mepe.ai.LlmService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 模块资料库页面：一段可离线打开的 HTML + 配套 CSV。
 * 存于 html_library.json，与桌面端同格式，随云同步在 PC / 安卓互通。
 * 桌面端用 System.Text.Json 序列化（PascalCase 字段名），这里用 @SerialName 对齐。
 */
@Serializable
data class HtmlLibraryPage(
    @SerialName("Id") var id: Int = 0,
    @SerialName("ModuleId") var moduleId: Int = 0,
    @SerialName("Title") var title: String = "",
    @SerialName("Html") var html: String = "",
    @SerialName("Csv") var csv: String = "",
    /** manual = 手写 / 导入，ai = AI 生成 */
    @SerialName("Source") var source: String = "manual",
    @SerialName("CreatedAt") var createdAt: String = "",
    @SerialName("UpdatedAt") var updatedAt: String = "",
    @SerialName("IsDeleted") var isDeleted: Boolean = false,
)

/** html_library.json 读写（独立仓库，避免改动 Repos.kt 的大文件） */
object HtmlLibraryRepository {
    private val listK = ListSerializer(HtmlLibraryPage.serializer())
    private const val FILE = "html_library"

    fun all(): MutableList<HtmlLibraryPage> =
        JsonStore.loadList(FILE) { f -> JsonStore.json.decodeFromString(listK, f.readText()) }

    /** 某个模块的资料页（按更新时间倒序） */
    fun forModule(moduleId: Int): List<HtmlLibraryPage> =
        all().filter { !it.isDeleted && it.moduleId == moduleId }.sortedByDescending { it.updatedAt }

    fun get(id: Int): HtmlLibraryPage? = all().firstOrNull { it.id == id && !it.isDeleted }

    fun add(page: HtmlLibraryPage): HtmlLibraryPage {
        val allList = all()
        page.id = (allList.maxOfOrNull { it.id } ?: 0) + 1
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        page.createdAt = now
        page.updatedAt = now
        allList.add(page)
        save(allList)
        return page
    }

    fun update(page: HtmlLibraryPage) {
        val allList = all()
        val idx = allList.indexOfFirst { it.id == page.id }
        if (idx < 0) { add(page); return }
        page.updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        allList[idx] = page
        save(allList)
    }

    fun delete(id: Int) {
        val allList = all()
        val idx = allList.indexOfFirst { it.id == id }
        if (idx < 0) return
        allList[idx].isDeleted = true
        save(allList)
    }

    private fun save(list: List<HtmlLibraryPage>) {
        JsonStore.saveText(FILE, JsonStore.json.encodeToString(listK, list))
        DataBus.bump()
    }

    /** 把模块记录导出为 CSV（与桌面端 HtmlLibraryService.BuildDefaultCsv 同结构） */
    fun buildDefaultCsv(m: CustomModule): String {
        fun esc(s: String?): String {
            val v = s ?: ""
            return if (v.contains(',') || v.contains('"') || v.contains('\n'))
                "\"" + v.replace("\"", "\"\"") + "\"" else v
        }
        val sb = StringBuilder()
        val cols = mutableListOf("date", "time")
        cols.addAll(m.fields.map { it.key })
        cols.add("note")
        sb.appendLine(cols.joinToString(",") { esc(it) })
        m.records.sortedWith(compareBy({ it.date }, { it.time })).forEach { r ->
            val row = mutableListOf(esc(r.date), esc(r.time))
            m.fields.forEach { f -> row.add(esc(r.values[f.key] ?: "")) }
            row.add(esc(r.note ?: ""))
            sb.appendLine(row.joinToString(","))
        }
        return sb.toString()
    }

    /**
     * 让 AI 根据模块字段与记录生成资料页（HTML + CSV）。
     * 提示词与桌面端 HtmlLibraryService 保持同口径，保证两端产物一致。
     */
    suspend fun generateWithAi(m: CustomModule, userHint: String, provider: AiProvider?): Result<HtmlLibraryPage> {
        if (provider == null)
            return Result.failure(IllegalStateException("未配置 AI 供应商，请到 设置 → AI 分析 添加并填写 API Key"))

        fun esc(s: String?): String {
            val v = s ?: ""
            return if (v.contains(',') || v.contains('"') || v.contains('\n'))
                "\"" + v.replace("\"", "\"\"") + "\"" else v
        }

        val sb = StringBuilder()
        sb.appendLine(
            "你是 ME 个人管理系统的资料页生成器。请输出一个可直接在浏览器打开的单文件 HTML 页面：" +
                "内嵌全部 CSS 与 JS（不引用外部资源），风格类似个人资料库/知识库（标题层级、卡片分区、表格、" +
                "可搜索、可视化用原生 SVG/Canvas）。要求：1) 用 <html-lib-csv>...</html-lib-csv> 包裹一段与页面数据一致的 CSV；" +
                "2) 页面内嵌同样的数据，保证离线打开也能看到全部内容；3) 不要输出任何解释文字。"
        )
        sb.appendLine()
        sb.appendLine("模块名称：${m.name}")
        sb.appendLine("字段定义：")
        m.fields.forEach { f ->
            sb.appendLine("- ${f.label}（类型 ${f.type}${f.unit?.takeIf { it.isNotBlank() }?.let { "，单位 $it" } ?: ""}）")
        }
        sb.appendLine()
        sb.appendLine("CSV 数据（date,time,各字段,note）：")
        sb.appendLine(buildDefaultCsv(m))
        sb.appendLine()
        sb.appendLine(
            if (userHint.isBlank()) "请基于以上数据生成一个个人资料库风格的 HTML 页面。"
            else "用户额外要求：${userHint.trim()}"
        )

        return LlmService.chat(provider, sb.toString()).map { reply ->
            val html = extractFenced(reply, "html") ?: extractFenced(reply, null)
            if (html.isNullOrBlank())
                throw IllegalStateException("AI 没有返回可用的 HTML，请重试或换一个模型")
            val csv = extractTagged(reply, "html-lib-csv") ?: extractFenced(reply, "csv") ?: buildDefaultCsv(m)
            add(
                HtmlLibraryPage(
                    moduleId = m.id,
                    title = userHint.lineSequence().firstOrNull()?.take(40)?.trim().takeUnless { it.isNullOrBlank() }
                        ?: (m.name + " · 资料页"),
                    html = html.trim(),
                    csv = csv.trim(),
                    source = "ai",
                )
            )
        }
    }

    private fun extractFenced(text: String, lang: String?): String? {
        val lines = text.replace("\r\n", "\n").split('\n')
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (!line.startsWith("```")) continue
            if (lang != null && !line.removePrefix("```").trim().equals(lang, ignoreCase = true)) continue
            val sb = StringBuilder()
            for (j in i + 1 until lines.size) {
                if (lines[j].trim().startsWith("```")) return sb.toString()
                sb.appendLine(lines[j])
            }
        }
        return null
    }

    private fun extractTagged(text: String, tag: String): String? {
        val open = "<$tag>"
        val close = "</$tag>"
        val a = text.indexOf(open, ignoreCase = true)
        if (a < 0) return null
        val b = text.indexOf(close, a, ignoreCase = true)
        if (b < 0) return null
        return text.substring(a + open.length, b).trim()
    }
}
