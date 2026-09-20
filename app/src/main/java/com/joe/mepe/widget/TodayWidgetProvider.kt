package com.joe.mepe.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import com.joe.mepe.MainActivity
import com.joe.mepe.R
import com.joe.mepe.data.Repos
import com.joe.mepe.data.TaskItem
import com.joe.mepe.data.TaskLogic
import java.time.LocalDate

/**
 * 「今日任务」桌面小组件（Android AppWidget / RemoteViews 实现）。
 * 标准 Widget 协议，Android 原生桌面、小米澎湃 HyperOS、ColorOS、OriginOS 等均可添加。
 *
 * 稳定性约定（v2.4.50 重做）：
 * - build() 全程兜底，任何异常都渲染"打开应用查看"的最小布局，组件永不空白；
 * - 深浅色跟随系统：更新时按系统夜间模式选择配色与卡片底，深色桌面不再刺眼/不可见；
 * - 按组件实际尺寸决定行数（小尺寸 2 行，大尺寸最多 10 行），拖动改变大小立即重排；
 * - 进程启动时兜底刷新一次，避免开机/杀进程后组件停留在旧数据。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            manager.updateAppWidget(id, safeBuild(context, manager.getAppWidgetOptions(id)))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager,
        appWidgetId: Int, newOptions: Bundle,
    ) {
        manager.updateAppWidget(appWidgetId, safeBuild(context, newOptions))
    }

    companion object {
        /** 数据变化后刷新所有已添加到桌面的小组件 */
        fun updateAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
                if (ids.isEmpty()) return
                for (id in ids) {
                    manager.updateAppWidget(id, safeBuild(context, manager.getAppWidgetOptions(id)))
                }
            } catch (_: Exception) {
            }
        }

        // ---------- 配色（浅色 / 深色跟随系统） ----------
        private class Palette(
            val bgRes: Int, val title: Int, val count: Int,
            val doneMark: Int, val todoMark: Int, val rowTitle: Int, val rowDone: Int,
        )

        private fun palette(context: Context): Palette {
            val dark = (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            return if (dark) Palette(
                R.drawable.widget_bg_dark,
                0xFFE4E6ED.toInt(), 0xFF9AA0B0.toInt(),
                0xFF4CC276.toInt(), 0xFF7A8090.toInt(), 0xFFE4E6ED.toInt(), 0xFF7A8090.toInt(),
            ) else Palette(
                R.drawable.widget_bg,
                0xFF1B1D25.toInt(), 0xFF5B5F6E.toInt(),
                0xFF2E9E5B.toInt(), 0xFF8A8F9E.toInt(), 0xFF1B1D25.toInt(), 0xFF8A8F9E.toInt(),
            )
        }

        /** 按组件当前高度估算可显示行数（表头约 40dp + 上下留白约 24dp，每行约 27dp） */
        private fun maxRows(options: Bundle?): Int {
            val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
            if (h <= 0) return 6
            return ((h - 64) / 27).coerceIn(2, 10)
        }

        private fun safeBuild(context: Context, options: Bundle?): RemoteViews =
            try {
                build(context, options)
            } catch (_: Exception) {
                // 兜底：数据/逻辑异常也不能让组件空白
                val p = palette(context)
                val rv = RemoteViews(context.packageName, R.layout.widget_today)
                rv.setInt(R.id.widget_root, "setBackgroundResource", p.bgRes)
                rv.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
                rv.setTextColor(R.id.widget_title, p.title)
                rv.setTextViewText(R.id.widget_count, "…")
                rv.setTextColor(R.id.widget_count, p.count)
                rv.removeAllViews(R.id.widget_rows)
                rv.addView(R.id.widget_rows, row(context, "○", context.getString(R.string.widget_open_hint), p, done = false))
                setClick(context, rv)
                rv
            }

        private fun setClick(context: Context, rv: RemoteViews) {
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
        }

        private fun build(context: Context, options: Bundle?): RemoteViews {
            val p = palette(context)
            val rv = RemoteViews(context.packageName, R.layout.widget_today)
            rv.setInt(R.id.widget_root, "setBackgroundResource", p.bgRes)

            val tasks: List<TaskItem> = try { Repos.tasks() } catch (_: Exception) { emptyList() }
            val completions = try { Repos.completions() } catch (_: Exception) { emptyList() }
            val today = LocalDate.now()

            val due = tasks
                .filter { !it.isDeleted && it.parentTaskId == null && TaskLogic.occursOnDate(it, today) }
                .sortedWith(compareByDescending<TaskItem> { it.priority }.thenBy { it.sortOrder })
            val done = due.count { TaskLogic.isDoneReadonly(it, today, completions) }

            rv.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
            rv.setTextColor(R.id.widget_title, p.title)
            rv.setTextViewText(R.id.widget_count, if (due.isEmpty()) context.getString(R.string.widget_no_tasks) else "$done / ${due.size}")
            rv.setTextColor(R.id.widget_count, p.count)

            rv.removeAllViews(R.id.widget_rows)
            val limit = maxRows(options)
            if (due.isEmpty()) {
                rv.addView(R.id.widget_rows, row(context, "·", context.getString(R.string.widget_empty_today), p, done = true))
            } else {
                due.take(limit).forEach { t ->
                    val isDone = TaskLogic.isDoneReadonly(t, today, completions)
                    rv.addView(R.id.widget_rows, row(context, if (isDone) "✓" else "○", t.title, p, isDone))
                }
                if (due.size > limit) {
                    rv.addView(R.id.widget_rows, row(context, "…",
                        context.getString(R.string.widget_more, due.size - limit), p, done = false))
                }
            }

            setClick(context, rv)
            return rv
        }

        private fun row(context: Context, check: String, title: String, p: Palette, done: Boolean): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_row)
            rv.setTextViewText(R.id.row_check, check)
            rv.setTextColor(R.id.row_check, if (done) p.doneMark else p.todoMark)
            rv.setTextViewText(R.id.row_title, title)
            rv.setTextColor(R.id.row_title, if (done) p.rowDone else p.rowTitle)
            return rv
        }
    }
}
