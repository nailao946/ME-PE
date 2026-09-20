package com.joe.mepe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.joe.mepe.ui.SyncStatusBus
import com.joe.mepe.data.Repos
import com.joe.mepe.notify.ReminderScheduler
import com.joe.mepe.ui.AppRoot
import com.joe.mepe.ui.LanguageService
import com.joe.mepe.ui.LocalLanguageContext
import com.joe.mepe.ui.rememberData
import com.joe.mepe.ui.theme.METheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        SyncStatusBus.scheduleAutoSync(applicationContext)
    }

    // 长按图标「添加小部件」快捷方式进入时显示引导
    private val showWidgetGuide = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(ShortcutTrampolineActivity.EXTRA_WIDGET_GUIDE, false)) {
            showWidgetGuide.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 启动时重排用药提醒
        ReminderScheduler.scheduleAll(applicationContext)

        if (intent?.getBooleanExtra(ShortcutTrampolineActivity.EXTRA_WIDGET_GUIDE, false) == true) {
            showWidgetGuide.value = true
        }

        setContent {
            var language by remember { mutableStateOf(LanguageService.getLanguage()) }
            val languageContext = remember(language) { LanguageService.localizedContext(this@MainActivity) }
            CompositionLocalProvider(LocalLanguageContext provides languageContext) {
                // 读取 DataBus.rev：设置里改主题/强调色/图标色后立即重组生效
                val (themeMode, accent, iconColor) = rememberData {
                    Triple(
                        Repos.getSetting("theme_mode", "system"),
                        Repos.getSetting("accent_color", "蓝色").ifBlank { "蓝色" },
                        Repos.getSetting("icon_color", "auto").ifBlank { "auto" }
                    )
                }
                METheme(themeMode, accent, iconColor) {
                    AppRoot(onLanguageChanged = { language = it })
                    if (showWidgetGuide.value) {
                        AlertDialog(
                            onDismissRequest = { showWidgetGuide.value = false },
                            title = { Text(languageContext.getString(R.string.widget_guide_title)) },
                            text = { Text(languageContext.getString(R.string.widget_guide_text)) },
                            confirmButton = {
                                TextButton(onClick = { showWidgetGuide.value = false }) {
                                    Text(languageContext.getString(R.string.widget_guide_ok))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
