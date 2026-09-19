package com.joe.mepe.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** 任务出现规则 / 完成状态判断 —— 与桌面端 TaskService 逻辑一致 */
object TaskLogic {

    fun taskTypeName(type: Int): String = when (type) {
        TaskTypes.ONE_TIME -> "一次性"
        TaskTypes.PERIODIC, TaskTypes.RECURRING -> "周期"
        TaskTypes.QUANTITATIVE -> "量化"
        else -> ""
    }

    fun patternName(t: TaskItem): String = when (t.recurringPattern) {
        RecPatterns.DAILY -> "每日"
        RecPatterns.WEEKDAY -> "工作日"
        RecPatterns.WEEKEND -> "周末"
        RecPatterns.WEEKLY -> "每周" + weekDaysName(t.recurringDaysOfWeek)
        RecPatterns.MONTHLY -> if (t.isLastDayOfMonth) "每月末" else "每月${t.recurringDayOfMonth ?: '?'}日"
        RecPatterns.INTERVAL -> "每${t.recurringInterval ?: '?'}天"
        RecPatterns.CUSTOM -> when {
            (t.recurringTimesPerWeek ?: 0) > 0 -> "每周${t.recurringTimesPerWeek}次"
            (t.recurringTimesPerDay ?: 0) > 0 -> "每天${t.recurringTimesPerDay}次"
            else -> "自定义"
        }
        else -> ""
    }

    fun weekDaysName(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        return s.split(',').mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }.joinToString("") { names[it - 1] }
    }

    private fun inRange(t: TaskItem, date: LocalDate): Boolean {
        val start = t.startDate?.toLocalDate()
        val end = t.endDate?.toLocalDate()
        if (start != null && date.isBefore(start)) return false
        if (end != null && date.isAfter(end)) return false
        return true
    }

    /** 任务在指定日期是否出现 */
    fun occursOnDate(t: TaskItem, date: LocalDate): Boolean {
        if (t.isDeleted) return false
        return when (t.type) {
            TaskTypes.ONE_TIME -> {
                val start = t.startDate?.toLocalDate() ?: return date == LocalDate.now()
                val end = t.endDate?.toLocalDate() ?: start
                !date.isBefore(start) && !date.isAfter(end)
            }
            TaskTypes.PERIODIC, TaskTypes.RECURRING -> {
                if (!inRange(t, date)) return false
                val created = t.createdAt.toLocalDate()
                if (date.isBefore(created)) return false
                when (t.recurringPattern) {
                    RecPatterns.DAILY -> true
                    RecPatterns.WEEKDAY -> date.dayOfWeek.value in 1..5
                    RecPatterns.WEEKEND -> date.dayOfWeek.value in 6..7
                    RecPatterns.WEEKLY -> (t.recurringDaysOfWeek ?: "")
                        .split(',').mapNotNull { it.trim().toIntOrNull() }.contains(date.dayOfWeek.value)
                    RecPatterns.MONTHLY -> {
                        val dom = t.recurringDayOfMonth
                        val isLast = date.plusDays(1).month != date.month
                        if (t.isLastDayOfMonth) isLast else dom == date.dayOfMonth
                    }
                    RecPatterns.INTERVAL -> {
                        val start = t.startDate?.toLocalDate() ?: created
                        val interval = (t.recurringInterval ?: 1).coerceAtLeast(1).toLong()
                        ChronoUnit.DAYS.between(start, date) % interval == 0L
                    }
                    else -> true // Custom：每日出现，按次数判断完成
                }
            }
            else -> inRange(t, date) && !date.isBefore(t.createdAt.toLocalDate().let { c -> if (c.year <= 1) LocalDate.now() else c })
        }
    }

    /** 指定日期的完成次数（打卡记录） */
    fun doneCountOn(taskId: Int, date: LocalDate, completions: List<TaskCompletionRecord>): Int =
        completions.count { it.taskId == taskId && it.date == date.toString() }

    /** 任务"永久完成"的日期（一次性=完成当天；量化=达标当天；循环类按天打卡无永久完成日，返回 null） */
    fun completedOn(t: TaskItem): LocalDate? {
        if (t.type == TaskTypes.RECURRING) return null
        if (t.type == TaskTypes.QUANTITATIVE) {
            val target = t.quantitativeTarget
            val reached = target != null && target > 0 && (t.quantitativeCurrent ?: 0.0) >= target
            return if (reached) t.completedAt?.toLocalDate() else null
        }
        // 完成时间缺失（旧数据）时退回到起始日/创建日，保证「过去完成」分组与桌面端一致
        return if (t.isCompleted)
            (t.completedAt ?: t.lastCompletedDate ?: t.startDate ?: t.createdAt).toLocalDate()
        else null
    }

    /**
     * 确保量化任务已落"今日基线"快照（跨日首次访问时以当前值滚动，漏几天也只落当天一次）。
     * 当日完成口径：Accumulate = 当前值 - 今日基线 >= 每日目标；Update = 当前值 >= 每日目标。
     */
    fun ensureQuantBaseline(t: TaskItem) {
        if (t.type != TaskTypes.QUANTITATIVE) return
        val today = LocalDate.now()
        if (t.quantSnapDate?.toLocalDate() == today) return
        t.quantSnapDate = LocalDateTime.now()
        t.quantSnapValue = t.quantitativeCurrent ?: t.quantitativeStart ?: 0.0
        Repos.updateTask(t)
    }

    /**
     * 量化任务每日目标的"当日完成"判定：今天按基线口径实时重算并写/删当日记录，历史日期直接查记录。
     */
    fun evalQuantDaily(t: TaskItem, date: LocalDate): Boolean {
        val dailyMin = t.quantitativeDailyMin ?: return false
        if (dailyMin <= 0) return false
        if (date != LocalDate.now())
            return Repos.completions().any { it.taskId == t.id && it.date == date.toString() }
        ensureQuantBaseline(t)
        val cur = t.quantitativeCurrent ?: 0.0
        val base = t.quantSnapValue ?: cur
        val dayMet = if (t.quantitativeMode == QuantModes.UPDATE) cur >= dailyMin else cur - base >= dailyMin
        val completions = Repos.completions()
        if (dayMet) {
            if (completions.none { it.taskId == t.id && it.date == date.toString() })
                Repos.addCompletion(t.id, date)
        } else {
            if (completions.any { it.taskId == t.id && it.date == date.toString() })
                Repos.removeCompletion(t.id, date)
        }
        return dayMet
    }

    /**
     * 只读版「当日已完成」判定：与 isDoneOn 同口径，但**不写/删当日记录**。
     * 列表 / 日历等渲染期调用这个，避免组合期间反复触发 IO 造成卡顿；
     * 真正落记录仍由打卡/编辑等用户动作触发。
     */
    fun isDoneReadonly(t: TaskItem, d: LocalDate, completions: List<TaskCompletionRecord>): Boolean {
        if (t.type == TaskTypes.QUANTITATIVE) {
            val target = t.quantitativeTarget
            if (target != null && target > 0 && (t.quantitativeCurrent ?: 0.0) >= target) return true
            val dailyMin = t.quantitativeDailyMin ?: return false
            if (dailyMin <= 0.0) return false
            if (d == LocalDate.now()) {
                val cur = t.quantitativeCurrent ?: 0.0
                val base = if (t.quantSnapDate?.toLocalDate() == d) (t.quantSnapValue ?: cur) else cur
                return if (t.quantitativeMode == QuantModes.UPDATE) cur >= dailyMin else cur - base >= dailyMin
            }
            return completions.any { it.taskId == t.id && it.date == d.toString() }
        }
        if (t.type == TaskTypes.ONE_TIME && t.isCompleted) return completedOn(t) == d
        val need = when (t.recurringPattern) {
            RecPatterns.CUSTOM -> t.recurringTimesPerDay?.takeIf { it > 0 }
                ?: t.recurringTimesPerWeek?.takeIf { it > 0 }?.let { 1 }
            else -> null
        }
        val done = doneCountOn(t.id, d, completions)
        val byRecords = if (need != null) done >= need else done > 0
        return byRecords || (t.isCompleted && t.completedAt?.toLocalDate() == d)
    }

    /** 展示用的"当日已完成"（任务列表/日历按日判定） */
    fun isDoneOn(t: TaskItem, date: LocalDate, completions: List<TaskCompletionRecord>): Boolean {
        if (t.type == TaskTypes.QUANTITATIVE) {
            val target = t.quantitativeTarget
            // 总目标达成后是永久完成；完成日期只用于历史分组。
            if (target != null && target > 0 && (t.quantitativeCurrent ?: 0.0) >= target)
                return true
            // 未达标：每日目标口径（今天按基线实时判定，历史查当日记录）
            return (t.quantitativeDailyMin ?: 0.0) > 0 && (
                if (date == LocalDate.now()) evalQuantDaily(t, date)
                else completions.any { it.taskId == t.id && it.date == date.toString() }
                )
        }
        if (t.type == TaskTypes.ONE_TIME && t.isCompleted) return completedOn(t) == date
        val need = when (t.recurringPattern) {
            RecPatterns.CUSTOM -> t.recurringTimesPerDay?.takeIf { it > 0 }
                ?: t.recurringTimesPerWeek?.takeIf { it > 0 }?.let { 1 }
            else -> null
        }
        val done = doneCountOn(t.id, date, completions)
        val byRecords = if (need != null) done >= need else done > 0
        return byRecords || (t.isCompleted && t.completedAt?.toLocalDate() == date)
    }

    // ============ 统计口径（定期盘点：完成任务 / 总任务数，按天计） ============

    /** 是否计入"完成任务/总任务数"：子任务不计；未设每日目标的量化任务不计 */
    fun countedForStats(t: TaskItem): Boolean =
        !t.isDeleted && t.parentTaskId == null &&
            !(t.type == TaskTypes.QUANTITATIVE && (t.quantitativeDailyMin ?: 0.0) <= 0.0)

    /** 某日是否计入总任务数（该日应做的任务；已达总目标的量化任务只算到达标当天） */
    fun dueOnDate(t: TaskItem, d: LocalDate): Boolean {
        if (!countedForStats(t)) return false
        if (t.type == TaskTypes.QUANTITATIVE) {
            val target = t.quantitativeTarget
            if (target != null && target > 0 && (t.quantitativeCurrent ?: 0.0) >= target) {
                val doneDay = t.completedAt?.toLocalDate() ?: return false
                if (d.isAfter(doneDay)) return false
            }
        }
        return occursOnDate(t, d)
    }

    /** 某日是否算"完成"：总目标量化达成后永久完成；其他类型按当日打卡规则 */
    fun doneOnDate(t: TaskItem, d: LocalDate, completions: List<TaskCompletionRecord>): Boolean {
        if (!countedForStats(t)) return false
        return when (t.type) {
            TaskTypes.ONE_TIME -> t.completedAt?.toLocalDate() == d
            TaskTypes.QUANTITATIVE ->
                completions.any { it.taskId == t.id && it.date == d.toString() } ||
                    t.completedAt?.toLocalDate() == d
            else -> isDoneOn(t, d, completions)
        }
    }

    /** 点击打卡 / 取消打卡（一次性/循环类）；量化任务点击始终增加一步，避免达成每日目标后误删当日记录 */
    fun toggleDone(t: TaskItem, date: LocalDate) {
        if (t.type == TaskTypes.QUANTITATIVE) {
            ensureQuantBaseline(t)
            val step = quantStep(t)
            t.quantitativeCurrent = (t.quantitativeCurrent ?: t.quantitativeStart ?: 0.0) + step
            val target = t.quantitativeTarget
            if (target != null && target > 0 && t.quantitativeCurrent!! >= target) {
                t.isCompleted = true; t.completedAt = LocalDateTime.now()
                t.lastCompletedDate = LocalDateTime.now()
            }
            Repos.updateTask(t)
            if (target == null || target <= 0 || t.quantitativeCurrent!! < target) evalQuantDaily(t, date)
            return
        }

        val completions = Repos.completions()
        if (isDoneOn(t, date, completions)) {
            // 取消当天所有打卡
            Repos.saveCompletions(completions.filterNot { it.taskId == t.id && it.date == date.toString() })
            if (t.type == TaskTypes.ONE_TIME) {
                t.isCompleted = false; t.completedAt = null; Repos.updateTask(t)
            }
        } else {
            Repos.addCompletion(t.id, date)
            if (t.type == TaskTypes.ONE_TIME) {
                t.isCompleted = true; t.completedAt = LocalDateTime.now(); Repos.updateTask(t)
            }
        }
    }

    /** 量化任务：手动加/减进度（到达目标自动标记完成；退回则取消完成；未达标按每日目标重算当日完成） */
    fun adjustQuantitative(t: TaskItem, delta: Double) {
        val start = t.quantitativeStart ?: 0.0
        val cur = t.quantitativeCurrent ?: start
        // 先落今日基线，再应用本次变更，确保本次增量计入今天而不是基线
        ensureQuantBaseline(t)
        t.quantitativeCurrent = (cur + delta).coerceAtLeast(start)
        val target = t.quantitativeTarget
        if (target != null && target > 0) {
            if (t.quantitativeCurrent!! >= target) {
                if (!t.isCompleted) {
                    t.isCompleted = true
                    t.completedAt = LocalDateTime.now()
                    t.lastCompletedDate = LocalDateTime.now()
                }
            } else if (t.isCompleted) {
                t.isCompleted = false
                t.completedAt = null
                t.lastCompletedDate = null
            }
        }
        Repos.updateTask(t)
        // 未达标且设了每日目标：按"当前值-今日基线"重算当日完成记录
        if (target == null || target <= 0 || t.quantitativeCurrent!! < target) {
            if ((t.quantitativeDailyMin ?: 0.0) > 0) evalQuantDaily(t, LocalDate.now())
        }
    }

    /** 量化任务点击打卡圈的步长（每日最低量，默认 1） */
    fun quantStep(t: TaskItem): Double = t.quantitativeDailyMin?.takeIf { it > 0 } ?: 1.0

    /** 目标进度（量化目标按数值，否则按子任务完成度） */
    fun goalProgress(g: Goal, allTasks: List<TaskItem>, date: LocalDate): Double {
        if (g.quantitativeTarget != null) {
            val start = g.quantitativeStart ?: 0.0
            val cur = g.quantitativeCurrent ?: 0.0
            if (g.quantitativeTarget!! <= start) return g.progress
            return ((cur - start) / (g.quantitativeTarget!! - start)).coerceIn(0.0, 1.0)
        }
        val children = allTasks.filter { it.goalId == g.id && it.parentTaskId == null && !it.isDeleted }
        if (children.isEmpty()) return g.progress.coerceIn(0.0, 1.0)
        val completions = Repos.completions()
        return children.map { c ->
            if (c.type == TaskTypes.QUANTITATIVE && c.quantitativeTarget != null && c.quantitativeTarget!! > 0)
                ((c.quantitativeCurrent ?: 0.0) / c.quantitativeTarget!!).coerceIn(0.0, 1.0)
            else if (isDoneOn(c, date, completions)) 1.0 else 0.0
        }.average().coerceIn(0.0, 1.0)
    }

    /** 更新目标首次达到/回退到 100% 的时间，调用方负责持久化。 */
    fun refreshGoalCompletion(g: Goal, allTasks: List<TaskItem>, date: LocalDate = LocalDate.now()): Double {
        val progress = goalProgress(g, allTasks, date)
        if (progress >= 0.999) {
            if (g.goalCompletedAt == null) g.goalCompletedAt = date.atStartOfDay()
        } else {
            g.goalCompletedAt = null
        }
        return progress
    }
}
