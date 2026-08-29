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

    /** 展示用的"当日已完成" */
    fun isDoneOn(t: TaskItem, date: LocalDate, completions: List<TaskCompletionRecord>): Boolean {
        if (t.type == TaskTypes.QUANTITATIVE) {
            val target = t.quantitativeTarget ?: return false
            return (t.quantitativeCurrent ?: 0.0) >= target && t.lastCompletedDate?.toLocalDate() == date
        }
        if (t.type == TaskTypes.ONE_TIME && t.isCompleted) return true
        val need = when (t.recurringPattern) {
            RecPatterns.CUSTOM -> t.recurringTimesPerDay?.takeIf { it > 0 }
                ?: t.recurringTimesPerWeek?.takeIf { it > 0 }?.let { 1 }
            else -> null
        }
        val done = doneCountOn(t.id, date, completions)
        return if (need != null) done >= need else done > 0
    }

    /** 点击打卡 / 取消打卡 */
    fun toggleDone(t: TaskItem, date: LocalDate) {
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
            if (t.type == TaskTypes.QUANTITATIVE) {
                val step = t.quantitativeDailyMin ?: 1.0
                t.quantitativeCurrent = (t.quantitativeCurrent ?: t.quantitativeStart ?: 0.0) + step
                if (t.quantitativeTarget != null && t.quantitativeCurrent!! >= t.quantitativeTarget!!) {
                    t.isCompleted = true; t.completedAt = LocalDateTime.now()
                    t.lastCompletedDate = LocalDateTime.now()
                }
                Repos.updateTask(t)
            }
        }
    }

    /** 量化任务：手动加/减进度（到达目标自动标记完成；退回则取消完成） */
    fun adjustQuantitative(t: TaskItem, delta: Double) {
        val start = t.quantitativeStart ?: 0.0
        val cur = t.quantitativeCurrent ?: start
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
}
