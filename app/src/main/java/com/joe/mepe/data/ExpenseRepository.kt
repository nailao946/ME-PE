package com.joe.mepe.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 记账数据仓库（单例），读写 expenses.json，与桌面端互通 */
object ExpenseRepository {
    private val k = ListSerializer(ExpenseRecord.serializer())

    fun all(): MutableList<ExpenseRecord> =
        JsonStore.loadList("expenses") { f -> JsonStore.json.decodeFromString(k, f.readText()) }

    fun save(list: List<ExpenseRecord>) {
        JsonStore.saveText("expenses", JsonStore.json.encodeToString(k, list))
        DataBus.bump()
    }

    fun add(r: ExpenseRecord): Int {
        val all = all()
        r.id = (all.maxOfOrNull { it.id } ?: 0) + 1
        r.uid = Repos.newUid()
        r.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        all.add(r)
        save(all)
        return r.id
    }

    fun update(r: ExpenseRecord) {
        val all = all()
        val i = all.indexOfFirst { it.id == r.id }
        if (i >= 0) { all[i] = r; save(all) }
    }

    fun delete(id: Int) = save(all().filterNot { it.id == id })

    /** 指定月份（yyyy-MM）内的记录 */
    fun inMonth(yearMonth: String): List<ExpenseRecord> =
        all().filter { it.date.startsWith(yearMonth) }
}
