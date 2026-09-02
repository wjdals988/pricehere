package com.pricehere.app

data class OpenSourceEntry(
    val name: String,
    val owner: String,
    val license: String,
)

/**
 * 이 앱이 실제로 링크하는 라이브러리만 적는다.
 * 의존성을 최소로 유지한 덕에 목록이 짧고, 전부 Apache 2.0이다.
 */
val OPEN_SOURCE: List<OpenSourceEntry> = listOf(
    OpenSourceEntry("Jetpack Compose", "The Android Open Source Project", "Apache License 2.0"),
    OpenSourceEntry("Material Components for Android (Material 3)", "Google", "Apache License 2.0"),
    OpenSourceEntry("AndroidX Core / Activity / Lifecycle", "The Android Open Source Project", "Apache License 2.0"),
    OpenSourceEntry("Kotlin Standard Library", "JetBrains", "Apache License 2.0"),
    OpenSourceEntry("kotlinx.coroutines", "JetBrains", "Apache License 2.0"),
)

const val APP_LICENSE = "MIT License"
const val COPYRIGHT_HOLDER = "JM"
const val COPYRIGHT_YEAR = "2026"
const val DASHBOARD_URL = "https://coldbrewventi.vercel.app"
const val GITHUB_URL = "https://github.com/wjdals988"
