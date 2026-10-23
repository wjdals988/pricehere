package com.pricehere.app

const val LICENSE_APACHE = "Apache License 2.0"
const val LICENSE_MIT = "MIT License"

data class OpenSourceEntry(
    val name: String,
    val owner: String,
    val license: String,
)

/**
 * APK에 실제로 들어가는 라이브러리와 리소스만 적는다(releaseRuntimeClasspath 기준).
 * 의존성을 최소로 유지한 덕에 목록이 짧다. Octicons 아이콘 두 개만 MIT이고 나머지는 Apache 2.0이다.
 * 두 라이선스 모두 배포할 때 전문을 함께 줘야 하므로 res/raw에 원문을 넣어 시트에서 펼쳐 보인다.
 */
val OPEN_SOURCE: List<OpenSourceEntry> = listOf(
    OpenSourceEntry("Jetpack Compose", "The Android Open Source Project", LICENSE_APACHE),
    OpenSourceEntry("Material Components for Android (Material 3)", "Google", LICENSE_APACHE),
    OpenSourceEntry("AndroidX Core / Activity / Lifecycle", "The Android Open Source Project", LICENSE_APACHE),
    OpenSourceEntry("Kotlin Standard Library", "JetBrains", LICENSE_APACHE),
    OpenSourceEntry("kotlinx.coroutines", "JetBrains", LICENSE_APACHE),
    OpenSourceEntry("JetBrains Java Annotations", "JetBrains", LICENSE_APACHE),
    OpenSourceEntry("Guava ListenableFuture", "Google", LICENSE_APACHE),
    OpenSourceEntry("JSpecify", "The JSpecify Authors", LICENSE_APACHE),
    OpenSourceEntry("Octicons (GitHub 마크 · 지구 아이콘)", "GitHub Inc.", LICENSE_MIT),
)

const val APP_LICENSE = "MIT License"
const val COPYRIGHT_HOLDER = "JM"
const val COPYRIGHT_YEAR = "2026"
const val DASHBOARD_URL = "https://coldbrewventi.vercel.app"
const val DASHBOARD_HOST = "coldbrewventi.vercel.app"
const val GITHUB_URL = "https://github.com/wjdals988"
const val ISSUES_URL = "https://github.com/wjdals988/pricehere/issues"
