package com.joe.mepe.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.joe.mepe.ui.calendar.CalendarScreen
import com.joe.mepe.ui.goals.GoalsScreen
import com.joe.mepe.ui.health.HealthScreen
import com.joe.mepe.ui.map.MapScreen
import com.joe.mepe.ui.review.ReviewScreen
import com.joe.mepe.ui.settings.SettingsScreen
import com.joe.mepe.ui.tasks.TasksScreen
import com.joe.mepe.ui.theme.LocalIconColor
import com.joe.mepe.ui.timetrack.TimeTrackScreen

object Routes {
    const val TASKS = "tasks"
    const val GOALS = "goals"
    const val CALENDAR = "calendar"
    const val TIME = "time"
    const val HEALTH = "health"
    const val MAP = "map"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
    const val MODULES = "modules"
    const val BACK = "__back"

    val mainTabs = listOf(TASKS, GOALS, CALENDAR, TIME, HEALTH)
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val activeIcon: ImageVector,
)

private val tabs = listOf(
    TabItem(Routes.TASKS, "任务", Icons.Outlined.Checklist, Icons.Filled.Checklist),
    TabItem(Routes.GOALS, "目标", Icons.Outlined.Flag, Icons.Filled.Flag),
    TabItem(Routes.CALENDAR, "日历", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    TabItem(Routes.TIME, "时间", Icons.Outlined.Timer, Icons.Filled.Timer),
    TabItem(Routes.HEALTH, "健康", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
)

/** 主页右上角快速入口（地图 / 盘点 / 设置），单色图标 */
@Composable
fun QuickLinks(current: String, nav: (String) -> Unit) {
    val links = listOf(
        Icons.Filled.Map to Routes.MAP,
        Icons.Filled.RateReview to Routes.REVIEW,
        Icons.Filled.Settings to Routes.SETTINGS,
    )
    androidx.compose.foundation.layout.Row {
        links.forEach { (icon, route) ->
            IconButton(onClick = { nav(route) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    icon, null,
                    tint = if (current == route) LocalIconColor.current
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
fun AppRoot() {
    var route by rememberSaveable { mutableStateOf(Routes.TASKS) }
    var mainTab by rememberSaveable { mutableStateOf(Routes.TASKS) }

    fun navigate(target: String) {
        if (target == Routes.BACK) {
            route = mainTab
            return
        }
        if (target in Routes.mainTabs) mainTab = target
        route = target
    }

    val isMain = route in Routes.mainTabs
    androidx.activity.compose.BackHandler(enabled = !isMain) {
        route = mainTab
    }

    Scaffold(
        bottomBar = {
            if (isMain) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    tabs.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigate(tab.route) },
                            icon = {
                                Icon(
                                    if (selected) tab.activeIcon else tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) LocalIconColor.current
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                                    else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val nav: (String) -> Unit = { navigate(it) }
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val goingDown = targetState == Routes.BACK
                    if (goingDown)
                        (slideInVerticallyDown() togetherWith fadeOut(tween(140)))
                    else
                        (fadeIn(tween(160)) + slideInHorizontallyRight()) togetherWith fadeOut(tween(120))
                },
                label = "route"
            ) { r ->
                when (r) {
                    Routes.TASKS -> TasksScreen(nav)
                    Routes.GOALS -> GoalsScreen(nav)
                    Routes.CALENDAR -> CalendarScreen(nav)
                    Routes.TIME -> TimeTrackScreen(nav)
                    Routes.HEALTH -> HealthScreen(nav)
                    Routes.MAP -> MapScreen(nav)
                    Routes.REVIEW -> ReviewScreen(nav)
                    Routes.SETTINGS -> SettingsScreen(nav)
                    Routes.MODULES -> com.joe.mepe.ui.modules.ModulesScreen(nav)
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<String>.slideInVerticallyDown() =
    androidx.compose.animation.slideInVertically(tween(200)) { it / 12 } + fadeIn(tween(200))

private fun AnimatedContentTransitionScope<String>.slideInHorizontallyRight() =
    androidx.compose.animation.slideInHorizontally(tween(200)) { it / 16 }
