package com.pricehere.app

import android.content.Context

enum class ThemeMode(val label: String) {
    SYSTEM("시스템 설정"),
    LIGHT("밝게"),
    DARK("어둡게"),
    ;

    companion object {
        fun of(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("pricehere_settings", Context.MODE_PRIVATE)

    fun themeMode(): ThemeMode = ThemeMode.of(prefs.getString(KEY_THEME, null))

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    /** 사용자가 "이 버전은 그만 알림" 한 버전. */
    fun dismissedVersion(): String? = prefs.getString(KEY_DISMISSED, null)

    fun dismissVersion(version: String) {
        prefs.edit().putString(KEY_DISMISSED, version).apply()
    }

    private companion object {
        const val KEY_THEME = "themeMode"
        const val KEY_DISMISSED = "dismissedVersion"
    }
}
