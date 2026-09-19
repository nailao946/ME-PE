package com.joe.mepe.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.joe.mepe.MainActivity
import com.joe.mepe.R
import com.joe.mepe.data.Repos
import com.joe.mepe.data.TaskLogic
import com.joe.mepe.data.TaskTypes
import java.time.LocalDate

/**
 * 「今日任务」桌面小组件（Android AppWidget / RemoteViews 实现）。
 * 标准.Widget 协议，Android 原生桌面、小米澎湃 HyperOS、ColorOS、OriginOS 等均可添加。
 * 展示当日应做任务与完成数，点击任意位置打开应用；数据变更时由 DataBus.bump() 触发刷新。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager,
        appWidgetId: Int, newOptions: android.os.Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, build(context))
    }

    companion object {
        /** 数据变化后刷新所有已添加到桌面的小组件 */
        fun updateAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val views = build(context)
                for (id in ids) manager.updateAppWidget(id, views)
            } catch (_: Exception) {
            }
        }

        private fun build(context: Context): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_today)

            val tasks = try { Repos.tasks() } catch (_: Exception) { emptyList() }
            val completions = try { Repos.completions() } catch (_: Exception) { emptyList() }
            val today = LocalDate.now()

            val due = tasks
                .filter { !it.isDeleted && it.parentTaskId == null && TaskLogic.occursOnDate(it, today) }
                .sortedWith(compareByDescending<com.joe.mepe.data.TaskItem> { it.priority }.thenBy { it.sortOrder })
            val done = due.count { TaskLogic.isDoneReadonly(it, today, completions) }

            rv.setTextViewText(R.id.widget_title, "今日任务")
            rv.setTextViewText(R.id.widget_count, if (due.isEmpty()) "无任务" else "$done / ${due.size}")

            rv.removeAllViews(R.id.widget_rows)
            if (due.isEmpty()) {
                rv.addView(R.id.widget_rows, row(context, "·", "今天没有安排任务", done = true))
            } else {
                due.take(6).forEach { t ->
                    val isDone = TaskLogic.isDoneReadonly(t, today, completions)
                    rv.addView(R.id.widget_rows, row(context, if (isDone) "✓" else "○", t.title, isDone))
                }
                if (due.size > 6) {
                    rv.addView(R.id.widget_rows, row(context, "…", "还有 ${due.size - 6} 项，点开查看", done = false))
                }
            }

            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            return rv
        }

        private fun row(context: Context, check: String, title: String, done: Boolean): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_row)
            rv.setTextViewText(R.id.row_check, check)
            rv.setTextColor(R.id.row_check, if (done) 0xFF2E9E5B.toInt() else 0xFF8A8F9E.toInt())
            rv.setTextViewText(R.id.row_title, title)
            rv.setTextColor(R.id.row_title, if (done) 0xFF8A8F9E.toInt() else 0xFF1B1D25.toInt())
            return rv
        }
    }
}
