package com.joe.mepe.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.joe.mepe.MainActivity

/**
 * 标签计时前台服务：状态栏实时显示计时（系统 chronometer 自动走秒，无需轮询），
 * 通知带「停止」按钮可直接结束计时，点通知打开应用。
 * 开始/停止计时必须与 Repos.startTimer/stopTimer 配对调用（见 TimeTrackScreen）。
 */
class TimerNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "me_timer_channel"
        const val NOTIF_ID = 4201
        const val ACTION_STOP = "com.joe.mepe.action.STOP_TIMER"

        var active = false
            private set

        /** 开始计时时调用：启动前台服务，通知栏显示计时 */
        fun start(context: Context) {
            ensureChannel(context)
            context.startForegroundService(Intent(context, TimerNotificationService::class.java))
        }

        /** 停止计时时调用：移除通知并结束服务 */
        fun stop(context: Context) {
            context.stopService(Intent(context, TimerNotificationService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "计时", NotificationManager.IMPORTANCE_LOW).apply {
                            setShowBadge(false)
                        }
                    )
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Repos.runningRecord()?.let { Repos.stopTimer(it.tagId) }
            active = false
            stopSelf()
            return START_NOT_STICKY
        }
        active = true
        startForeground(NOTIF_ID, buildNotification())
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val running = Repos.runningRecord()
        val tag = running?.let { r -> Repos.timeTags().find { it.id == r.tagId } }
        val tagName = tag?.name ?: "计时中"

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else
            @Suppress("DEPRECATION") Notification.Builder(this)

        notif.setContentTitle("正在计时 · $tagName")
            .setContentText("计时中，点这里查看详情")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)

        if (running != null) {
            notif.setUsesChronometer(true)
            notif.setWhen(running.startTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        }

        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, TimerNotificationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notif.addAction(Notification.Action.Builder(null, "停止计时", stopPi).build())

        return notif.build()
    }
}
