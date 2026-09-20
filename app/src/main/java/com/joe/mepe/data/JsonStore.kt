package com.joe.mepe.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 本地 JSON 文件存储 —— 与桌面端 %LocalAppData%\ME\JsonData 完全相同的文件名与格式，
 * 备份 zip 可在两端互通。
 */
object JsonStore {
    lateinit var dir: File
        private set

    /** 应用级 Context，供小组件刷新等无 Compose 场景使用 */
    var appContext: Context? = null
        private set

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        dir = File(context.filesDir, "JsonData")
        if (!dir.exists()) dir.mkdirs()
    }

    private fun file(name: String) = File(dir, "$name.json")

    /**
     * 解析结果内存缓存：Tab 切换/页面重建时同一文件会被反复读取，
     * 直接复用上次解析结果（按 lastModified + length 校验，文件被云同步等
     * 外部直接改写后自动失效重读），把主线程上的重复 IO+JSON 解析降到近零。
     */
    private class CacheEntry(val mod: Long, val len: Long, val data: List<*>)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    @Suppress("UNCHECKED_CAST")
    fun <T> loadList(name: String, loader: (File) -> List<T>): MutableList<T> {
        val f = file(name)
        if (!f.exists()) { cache.remove(name); return mutableListOf() }
        val mod = f.lastModified()
        val len = f.length()
        val hit = cache[name]
        if (hit != null && hit.mod == mod && hit.len == len) {
            return (hit.data as List<T>).toMutableList()
        }
        return try {
            val list = loader(f)
            cache[name] = CacheEntry(mod, len, list)
            list.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveText(name: String, text: String) {
        File(dir, "$name.json").writeText(text)
        cache.remove(name) // 写入后让缓存失效，下次读取以文件为准
    }

    fun readText(name: String): String? {
        val f = file(name)
        return if (f.exists()) f.readText() else null
    }

    fun allFiles(): List<File> = dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name } ?: emptyList()
}

/** 全局数据版本号：仓库每次写入后 +1，UI 通过读取它触发刷新；同时刷新桌面小组件 */
object DataBus {
    var rev: Int by androidx.compose.runtime.mutableStateOf(0)
        private set

    fun bump() {
        rev++
        // 桌面小组件跟随数据变化刷新（无小组件时静默跳过）
        JsonStore.appContext?.let { ctx ->
            try { com.joe.mepe.widget.TodayWidgetProvider.updateAll(ctx) } catch (_: Exception) { }
        }
    }
}
