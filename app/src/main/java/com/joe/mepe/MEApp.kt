package com.joe.mepe

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.joe.mepe.data.JsonStore

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

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MEDICATION, "用药提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "到点服药通知提醒"
            }
        )
    }
}
