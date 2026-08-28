package com.joe.mepe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 可选强调色（与桌面端窗口边框色一致） */
val Accents = listOf(
    "蓝色" to Color(0xFF4F6EF7),
    "绿色" to Color(0xFF2E9E5B),
    "紫色" to Color(0xFF7C5CE0),
    "粉色" to Color(0xFFE05C8A),
    "橙色" to Color(0xFFE0883C),
    "青色" to Color(0xFF2BA8A8),
)

/** 可选图标色；"auto" 表示跟随强调色 */
val IconColorChoices = listOf(
    "自动（跟随强调色）" to "auto",
    "蓝" to "#4F6EF7",
    "绿" to "#2E9E5B",
    "紫" to "#7C5CE0",
    "粉" to "#E05C8A",
    "橙" to "#E0883C",
    "青" to "#2BA8A8",
    "黑" to "#1C1E26",
    "白" to "#F2F3F8",
    "灰" to "#8A8F9E",
)

/** 图标统一着色（全局单色图标，可在设置中更改） */
val LocalIconColor = staticCompositionLocalOf { Color.Unspecified }

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.14f),
    onPrimaryContainer = accent.darken(0.45f),
    inversePrimary = accent.lighten(0.3f),
    secondary = accent.darken(0.15f),
    onSecondary = Color.White,
    secondaryContainer = accent.copy(alpha = 0.10f),
    onSecondaryContainer = accent.darken(0.4f),
    tertiary = accent.copy(blue = accent.blue * 0.7f + 0.2f),
    onTertiary = Color.White,
    tertiaryContainer = accent.copy(alpha = 0.08f),
    onTertiaryContainer = accent.darken(0.35f),
    background = Color(0xFFF4F5FA),
    onBackground = Color(0xFF1B1D25),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1D25),
    surfaceVariant = Color(0xFFEAECF3),
    onSurfaceVariant = Color(0xFF5B5F6E),
    surfaceTint = accent,
    inverseSurface = Color(0xFF2C2E38),
    inverseOnSurface = Color(0xFFF2F2F7),
    error = Color(0xFFDC3644),
    onError = Color.White,
    errorContainer = Color(0xFFFFE2E4),
    onErrorContainer = Color(0xFF93121F),
    outline = Color(0xFFC8CCD8),
    outlineVariant = Color(0xFFE1E3EC),
    scrim = Color(0xFF000000),
)

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent.lighten(0.22f),
    onPrimary = Color(0xFF0F1220),
    primaryContainer = accent.copy(alpha = 0.30f),
    onPrimaryContainer = accent.lighten(0.55f),
    inversePrimary = accent.darken(0.1f),
    secondary = accent.lighten(0.12f),
    onSecondary = Color(0xFF0F1220),
    secondaryContainer = accent.copy(alpha = 0.22f),
    onSecondaryContainer = accent.lighten(0.45f),
    tertiary = accent.copy(blue = accent.blue * 0.7f + 0.2f).lighten(0.1f),
    onTertiary = Color(0xFF0F1220),
    tertiaryContainer = accent.copy(alpha = 0.18f),
    onTertiaryContainer = accent.lighten(0.4f),
    background = Color(0xFF131419),
    onBackground = Color(0xFFE4E6ED),
    surface = Color(0xFF1B1D24),
    onSurface = Color(0xFFE4E6ED),
    surfaceVariant = Color(0xFF262933),
    onSurfaceVariant = Color(0xFFA9ADBC),
    surfaceTint = accent,
    inverseSurface = Color(0xFFE4E6ED),
    inverseOnSurface = Color(0xFF1B1D24),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF37060D),
    errorContainer = Color(0xFF5C1120),
    onErrorContainer = Color(0xFFFFD9DC),
    outline = Color(0xFF484C5A),
    outlineVariant = Color(0xFF2E313C),
    scrim = Color(0xFF000000),
)

private fun Color.darken(f: Float) = Color(
    red = red * (1 - f), green = green * (1 - f), blue = blue * (1 - f), alpha = alpha
)

private fun Color.lighten(f: Float) = Color(
    red = red + (1 - red) * f, green = green + (1 - green) * f, blue = blue + (1 - blue) * f, alpha = alpha
)

private fun appTypography() = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

private val appShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

fun resolveAccent(accentName: String): Color {
    if (accentName.startsWith("#")) return parseHexColor(accentName, Accents.first().second)
    return Accents.firstOrNull { it.first == accentName }?.second ?: Accents.first().second
}

@Composable
fun METheme(
    themeMode: String, // light / dark / system
    accentName: String,
    iconColorHex: String = "auto",
    content: @Composable () -> Unit
) {
    val accent = resolveAccent(accentName)
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) darkScheme(accent) else lightScheme(accent)
    val iconColor = if (iconColorHex == "auto" || iconColorHex.isBlank())
        scheme.primary
    else parseHexColor(iconColorHex, scheme.primary)

    MaterialTheme(
        colorScheme = scheme,
        typography = appTypography(),
        shapes = appShapes,
    ) {
        CompositionLocalProvider(LocalIconColor provides iconColor, content = content)
    }
}

/** 解析 #RRGGBB(AA) 颜色串，失败回退 */
fun parseHexColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val h = hex.removePrefix("#").trim()
        val argb = when (h.length) {
            6 -> android.graphics.Color.parseColor("#$h")
            8 -> android.graphics.Color.parseColor("#$h")
            3 -> android.graphics.Color.parseColor("#$h")
            else -> return fallback
        }
        Color(argb)
    } catch (_: Exception) {
        fallback
    }
}

/** 把 Color 转成 #RRGGBB（持久化用） */
fun colorToHex(c: Color): String {
    fun part(v: Float) = "%02X".format((v * 255).toInt().coerceIn(0, 255))
    return "#${part(c.red)}${part(c.green)}${part(c.blue)}"
}
