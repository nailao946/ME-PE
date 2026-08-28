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

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun init(context: Context) {
        dir = File(context.filesDir, "JsonData")
        if (!dir.exists()) dir.mkdirs()
    }

    private fun file(name: String) = File(dir, "$name.json")

    fun <T> loadList(name: String, loader: (File) -> List<T>): MutableList<T> {
        val f = file(name)
        if (!f.exists()) return mutableListOf()
        return try {
            loader(f).toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveText(name: String, text: String) {
        File(dir, "$name.json").writeText(text)
    }

    fun readText(name: String): String? {
        val f = file(name)
        return if (f.exists()) f.readText() else null
    }

    fun allFiles(): List<File> = dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name } ?: emptyList()
}

/** 全局数据版本号：仓库每次写入后 +1，UI 通过读取它触发刷新 */
object DataBus {
    var rev: Int by androidx.compose.runtime.mutableStateOf(0)
        private set

    fun bump() { rev++ }
}
