package com.joe.mepe

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.joe.mepe.data.Repos
import com.joe.mepe.data.TimerNotificationService

/**
 * 长按图标快捷方式的落地页（透明无界面，点完即走）：
 * - 开始计时：用最近使用过的标签开始计时（Toast 轻提示 + 通知栏走秒）
 * - 停止计时：结束进行中的计时并移除通知
 * - 添加小部件：打开应用的添加引导（系统无公开 API 直接替用户添加小部件）
 */
class ShortcutTrampolineActivity : Activity() {

    companion object {
        const val ACTION_START_TIMER = "com.joe.mepe.action.SHORTCUT_START_TIMER"
        const val ACTION_STOP_TIMER = "com.joe.mepe.action.SHORTCUT_STOP_TIMER"
        const val ACTION_ADD_WIDGET = "com.joe.mepe.action.SHORTCUT_ADD_WIDGET"
        const val EXTRA_WIDGET_GUIDE = "show_widget_guide"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            when (intent?.action) {
                ACTION_START_TIMER -> startTimer()
                ACTION_STOP_TIMER -> stopTimer()
                ACTION_ADD_WIDGET -> startActivity(mainIntent().putExtra(EXTRA_WIDGET_GUIDE, true))
                else -> startActivity(mainIntent())
            }
        } catch (_: Exception) {
            Toast.makeText(this, R.string.shortcut_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun mainIntent() = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** 最近一次使用过的计时标签（无记录则取第一个标签） */
    private fun lastUsedTagId(): Int? {
        val tags = Repos.timeTags()
        if (tags.isEmpty()) return null
        val ids = tags.map { it.id }.toSet()
        val recent = Repos.timeRecords().filter { it.tagId in ids }.maxByOrNull { it.startTime }
        return recent?.tagId ?: tags.first().id
    }

    private fun startTimer() {
        val tagId = lastUsedTagId()
        if (tagId == null) {
            Toast.makeText(this, R.string.shortcut_no_tags, Toast.LENGTH_LONG).show()
            startActivity(mainIntent())
            return
        }
        val running = Repos.runningRecord()
        if (running != null && running.tagId == tagId) {
            Toast.makeText(this, getString(R.string.shortcut_already_running), Toast.LENGTH_SHORT).show()
            return
        }
        val tagName = Repos.timeTags().find { it.id == tagId }?.name ?: ""
        Repos.startTimer(tagId)
        TimerNotificationService.start(this)
        Toast.makeText(this, getString(R.string.shortcut_started, tagName), Toast.LENGTH_SHORT).show()
    }

    private fun stopTimer() {
        val running = Repos.runningRecord()
        if (running == null) {
            Toast.makeText(this, R.string.shortcut_not_running, Toast.LENGTH_SHORT).show()
            return
        }
        Repos.stopTimer(running.tagId)
        TimerNotificationService.stop(this)
        Toast.makeText(this, R.string.shortcut_stopped, Toast.LENGTH_SHORT).show()
    }
}
