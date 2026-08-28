package com.joe.mepe.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.joe.mepe.MEApp
import com.joe.mepe.R
import com.joe.mepe.data.MedicationRecord
import com.joe.mepe.data.Repos
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar

/** 用药提醒：为每条开启提醒的用药，按时间点注册每日循环闹钟 */
object ReminderScheduler {

    private fun pendingIntent(context: Context, medId: Int, timeIdx: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.joe.mepe.MED_REMINDER"
            putExtra("medId", medId)
        }
        return PendingIntent.getBroadcast(
            context, medId * 100 + timeIdx, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val today = LocalDate.now()
        Repos.medications().filter { it.remind }.forEach { med ->
            med.timeList().forEachIndexed { idx, timeStr ->
                val parts = timeStr.split(':')
                val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val now = LocalDateTime.now()
                var next = LocalDateTime.of(today, LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59)))
                if (!next.isAfter(now)) next = next.plusDays(1)
                val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                am.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP, triggerAt, AlarmManager.INTERVAL_DAY,
                    pendingIntent(context, med.id, idx)
                )
            }
        }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        Repos.medications().forEach { med ->
            med.timeList().forEachIndexed { idx, _ ->
                am.cancel(pendingIntent(context, med.id, idx))
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.joe.mepe.MED_REMINDER") return
        val medId = intent.getIntExtra("medId", -1)
        val med: MedicationRecord = Repos.medications().firstOrNull { it.id == medId } ?: return

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val builder = NotificationCompat.Builder(context, MEApp.CHANNEL_MEDICATION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💊 到点吃药啦")
            .setContentText("${med.name} ${if (med.specValue > 0) "${med.specValue}${com.joe.mepe.data.medUnitName(med.unit)}" else ""}")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        try {
            NotificationManagerCompat.from(context).notify(medId * 100 + 1, builder.build())
        } catch (_: SecurityException) {
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleAll(context)
        }
    }
}
