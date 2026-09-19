package com.joe.mepe.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** Local language preference shared by Compose root and settings. */
object LanguageService {
    private const val PREFS = "me_preferences"
    private const val KEY_LANGUAGE = "language"
    const val SYSTEM = "system"
    const val ZH = "zh"
    const val EN = "en"

    private lateinit var prefs: SharedPreferences
    fun init(context: Context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    fun getLanguage(): String = if (::prefs.isInitialized) prefs.getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM else SYSTEM
    fun setLanguage(value: String) { if (::prefs.isInitialized) prefs.edit().putString(KEY_LANGUAGE, value).apply() }
    fun localeTag(): String = when (getLanguage()) { EN -> "en"; ZH -> "zh"; else -> Locale.getDefault().language }
    fun localizedContext(context: Context): Context {
        val locale = Locale.forLanguageTag(localeTag())
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) config.setLocales(android.os.LocaleList(locale)) else @Suppress("DEPRECATION") config.locale = locale
        return context.createConfigurationContext(config)
    }
}

val LocalLanguageContext = staticCompositionLocalOf<Context> { error("Language context not provided") }
