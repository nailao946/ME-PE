package com.joe.mepe

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.joe.mepe.data.JsonStore
import com.joe.mepe.ui.LanguageService

class MEApp : Application() {
    companion object {
        const val CHANNEL_MEDICATION = "medication_reminders"

        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
        JsonStore.init(this)
        LanguageService.init(this)

        // 进程启动时兜底刷新桌面小组件（开机/被杀后重进，数据可能已变化）
        try { com.joe.mepe.widget.TodayWidgetProvider.updateAll(this) } catch (_: Exception) { }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MEDICATION, "用药提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "到点服药通知提醒"
            }
        )
    }
}
