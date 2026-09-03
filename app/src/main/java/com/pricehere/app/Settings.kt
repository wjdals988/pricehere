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

    /**
     * 실행 횟수. 복사·저장 같은 기능 안내를 처음 몇 번만 보여주려고 센다.
     * 안내가 영구히 떠 있으면 그건 안내가 아니라 노이즈다.
     */
    fun bumpLaunchCount(): Int {
        val next = prefs.getInt(KEY_LAUNCHES, 0) + 1
        prefs.edit().putInt(KEY_LAUNCHES, next).apply()
        return next
    }

    private companion object {
        const val KEY_LAUNCHES = "launches"
        const val KEY_THEME = "themeMode"
        const val KEY_DISMISSED = "dismissedVersion"
    }
}
