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

    /** 마지막으로 본 통신망 국가. 나라가 바뀌었을 때만 통화를 자동으로 고르려고 기억한다. */
    fun lastNetworkCountry(): String? = prefs.getString(KEY_NETWORK_COUNTRY, null)

    fun setLastNetworkCountry(iso: String) {
        prefs.edit().putString(KEY_NETWORK_COUNTRY, iso.lowercase()).apply()
    }

    /** 마지막으로 고른 결제 수단(기준율·카드·현찰). */
    fun priceMode(): PriceMode = PriceMode.of(prefs.getString(KEY_PRICE_MODE, null))

    fun setPriceMode(mode: PriceMode) {
        prefs.edit().putString(KEY_PRICE_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_PRICE_MODE = "priceMode"
        const val KEY_NETWORK_COUNTRY = "networkCountry"
        const val KEY_LAUNCHES = "launches"
        const val KEY_THEME = "themeMode"
        const val KEY_DISMISSED = "dismissedVersion"
    }
}
