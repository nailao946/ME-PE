package com.joe.mepe.data

import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份：把 JsonData 目录打包为 zip（文件名与桌面端备份目录规则一致：me_backup_时间）。
 * 桌面端备份是目录拷贝（*.db 目录），把该目录 zip 后即可在手机端导入；反之亦然。
 */
object BackupManager {

    fun exportTo(output: File): Boolean {
        return try {
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                JsonStore.allFiles().forEach { f ->
                    zip.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun defaultFileName(): String =
        "me_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip"

    /** 从 zip 导入（支持 zip 根或一级子目录内的 *.json） */
    fun importFrom(input: InputStream): Int {
        var count = 0
        try {
            ZipInputStream(input.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && name.endsWith(".json") && name.length > 5) {
                        val target = File(JsonStore.dir, name)
                        target.outputStream().use { zip.copyTo(it) }
                        count++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            DataBus.bump()
        } catch (_: Exception) {
            return count
        }
        return count
    }

    /** 清空本地全部 JSON 数据（导入前可选） */
    fun clearAll() {
        JsonStore.allFiles().forEach { it.delete() }
        DataBus.bump()
    }
}
