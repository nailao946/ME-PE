package com.joe.mepe.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joe.mepe.data.DataBus
import com.joe.mepe.data.ExpenseRecord
import com.joe.mepe.data.ExpenseRepository
import com.joe.mepe.ui.ConfirmDialog
import com.joe.mepe.ui.DateField
import com.joe.mepe.ui.EmptyHint
import com.joe.mepe.ui.LabeledField
import com.joe.mepe.ui.NumberField
import com.joe.mepe.ui.QuickLinks
import com.joe.mepe.ui.Routes
import com.joe.mepe.ui.ScreenHeader
import com.joe.mepe.ui.SectionCard
import com.joe.mepe.ui.Segmented
import com.joe.mepe.ui.StatRow
import com.joe.mepe.ui.theme.LocalIconColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val EXPENSE_CATS = listOf("餐饮", "交通", "购物", "日用", "居住", "娱乐", "医疗", "人情", "其他")
private val INCOME_CATS = listOf("工资", "理财", "红包", "其他")
private val CAT_EMOJI = mapOf(
    "餐饮" to "🍜", "交通" to "🚌", "购物" to "🛒", "日用" to "🧴", "居住" to "🏠",
    "娱乐" to "🎮", "医疗" to "💊", "人情" to "🎁", "其他" to "📦",
    "工资" to "💰", "理财" to "📈", "红包" to "🧧",
)
private fun catEmoji(cat: String): String = CAT_EMOJI[cat] ?: "📦"
private fun money(v: Double) = "%.2f".format(v)
private val WEEK_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpensesScreen(nav: (String) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val rev = DataBus.rev
    val records = remember(rev, month) { ExpenseRepository.inMonth(month.toString()) }
    val incomeTotal = records.filter { it.isIncome }.sumOf { it.amount }
    val expenseTotal = records.filter { !it.isIncome }.sumOf { it.amount }
    val balance = incomeTotal - expenseTotal

    val expenseByCat = EXPENSE_CATS
        .map { c -> c to records.filter { !it.isIncome && it.category == c }.sumOf { it.amount } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    val maxCat = expenseByCat.maxOfOrNull { it.second } ?: 1.0

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<ExpenseRecord?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "记账",
                icon = Icons.Filled.AccountBalanceWallet,
                subtitle = "收支记录与分析",
                actions = { QuickLinks(Routes.EXPENSES, nav) }
            )
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // 月份切换
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.Filled.ChevronLeft, "上月", tint = LocalIconColor.current)
                    }
                    Text(
                        "${month.year}-${"%02d".format(month.monthValue)}",
                        Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.Filled.ChevronRight, "下月", tint = LocalIconColor.current)
                    }
                }
                // 三张统计卡
                StatRow(listOf(
                    Triple("收入合计", money(incomeTotal), Color(0xFF2E9E5B)),
                    Triple("支出合计", money(expenseTotal), Color(0xFFE5484D)),
                    Triple("结余", money(balance), if (balance >= 0) Color(0xFF2E9E5B) else Color(0xFFE5484D)),
                ))
                // 分类汇总条
                SectionCard(title = "本月支出分类（${expenseByCat.size}）") {
                    if (expenseByCat.isEmpty()) EmptyHint("本月暂无支出记录")
                    else expenseByCat.forEach { (cat, amt) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${catEmoji(cat)} $cat", Modifier.width(86.dp), style = MaterialTheme.typography.bodyMedium)
                            Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(Modifier.fillMaxWidth((amt / maxCat).toFloat()).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.primary))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(money(amt), Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                // 记录列表（按日期倒序分组）
                SectionCard(title = "本月记录（${records.size}）") {
                    if (records.isEmpty()) EmptyHint("还没有记账，点右下角 + 记一笔", Icons.Filled.AccountBalanceWallet)
                    else {
                        records.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (dateStr, recs) ->
                            val d = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                            val week = d?.let { WEEK_NAMES[it.dayOfWeek.value - 1] } ?: ""
                            Text(
                                if (d != null) "$dateStr 周$week" else dateStr,
                                Modifier.padding(top = 8.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            recs.sortedByDescending { it.createdAt }.forEach { r ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { editing = r },
                                            onLongClick = { deleteTarget = r }
                                        )
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(catEmoji(r.category), Modifier.width(30.dp), style = MaterialTheme.typography.titleMedium)
                                    Column(Modifier.weight(1f)) {
                                        Text(r.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        val noteText = r.note
                                        if (!noteText.isNullOrBlank())
                                            Text(noteText, style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    val amtText = if (r.isIncome) "+${money(r.amount)}" else "-${money(r.amount)}"
                                    Text(amtText, color = if (r.isIncome) Color(0xFF2E9E5B) else Color(0xFFE5484D),
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(96.dp))
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 12.dp)
        ) { Icon(Icons.Filled.Add, "记一笔", Modifier.size(26.dp)) }
    }

    if (adding || editing != null) {
        ExpenseEditDialog(initial = editing, onClose = { adding = false; editing = null })
    }
    deleteTarget?.let { r ->
        ConfirmDialog("删除记录", "确定删除「${catEmoji(r.category)} ${r.category} ${money(r.amount)}」吗？", {
            ExpenseRepository.delete(r.id)
            deleteTarget = null
        }, { deleteTarget = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseEditDialog(initial: ExpenseRecord?, onClose: () -> Unit) {
    val isNew = initial == null
    var amount by remember { mutableStateOf(initial?.amount?.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() } ?: "") }
    var isIncome by remember { mutableStateOf(initial?.isIncome ?: false) }
    var category by remember { mutableStateOf(initial?.category ?: (if (isIncome) INCOME_CATS.first() else EXPENSE_CATS.first())) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableStateOf(initial?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()) }

    val cats = if (isIncome) INCOME_CATS else EXPENSE_CATS

    com.joe.mepe.ui.FormDialog(title = if (isNew) "记一笔" else "编辑记录", onClose = onClose) {
        NumberField("金额", amount, { amount = it }, suffix = "元")
        Spacer(Modifier.height(10.dp))
        Segmented(listOf("支出", "收入"), if (isIncome) 1 else 0) {
            isIncome = it == 1
            if (category !in (if (isIncome) INCOME_CATS else EXPENSE_CATS))
                category = if (isIncome) INCOME_CATS.first() else EXPENSE_CATS.first()
        }
        Spacer(Modifier.height(10.dp))
        Text("分类", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { c ->
                val active = c == category
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { category = c }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .wrapContentWidth()
                ) {
                    Text("${catEmoji(c)} $c",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LabeledField("备注（可选）", note, { note = it })
        Spacer(Modifier.height(10.dp))
        DateField("日期", date) { date = it }
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Button(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            onClick = {
                val amt = amount.toDoubleOrNull() ?: return@Button
                if (amt <= 0 || category.isBlank()) return@Button
                val rec = (initial ?: ExpenseRecord()).apply {
                    this.amount = amt
                    this.isIncome = isIncome
                    this.category = category
                    this.note = note.ifBlank { null }
                    this.date = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                }
                if (isNew) ExpenseRepository.add(rec) else ExpenseRepository.update(rec)
                onClose()
            },
            enabled = amount.toDoubleOrNull() != null && category.isNotBlank()
        ) { Text("保存") }
    }
}
