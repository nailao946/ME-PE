package com.joe.mepe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.joe.mepe.data.Repos
import com.joe.mepe.notify.ReminderScheduler
import com.joe.mepe.ui.AppRoot
import com.joe.mepe.ui.rememberData
import com.joe.mepe.ui.theme.METheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 启动时重排用药提醒
        ReminderScheduler.scheduleAll(applicationContext)

        setContent {
            // 读取 DataBus.rev：设置里改主题/强调色/图标色后立即重组生效
            val (themeMode, accent, iconColor) = rememberData {
                Triple(
                    Repos.getSetting("theme_mode", "system"),
                    Repos.getSetting("accent_color", "蓝色").ifBlank { "蓝色" },
                    Repos.getSetting("icon_color", "auto").ifBlank { "auto" }
                )
            }
            METheme(themeMode, accent, iconColor) {
                AppRoot()
            }
        }
    }
}
