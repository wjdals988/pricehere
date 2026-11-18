package com.pricehere.app

const val LICENSE_APACHE = "Apache License 2.0"
const val LICENSE_MIT = "MIT License"
const val LICENSE_OFL = "SIL Open Font License 1.1"

data class OpenSourceEntry(
    val name: String,
    val owner: String,
    val license: String,
)

/**
 * APK에 실제로 들어가는 라이브러리와 리소스만 적는다(releaseRuntimeClasspath 기준).
 * 의존성을 최소로 유지한 덕에 목록이 짧다. Octicons 아이콘 두 개는 MIT, 금액 글꼴 Geist는 OFL, 나머지는 Apache 2.0이다.
 * 세 라이선스 모두 배포할 때 전문을 함께 줘야 하므로 res/raw에 원문을 넣어 시트에서 펼쳐 보인다.
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
    OpenSourceEntry("Geist (금액 숫자 글꼴)", "The Geist Project Authors", LICENSE_OFL),
)

const val APP_LICENSE = "MIT License"
const val COPYRIGHT_HOLDER = "JM"
const val COPYRIGHT_YEAR = "2026"
const val DASHBOARD_URL = "https://coldbrewventi.vercel.app"
const val DASHBOARD_HOST = "coldbrewventi.vercel.app"
const val GITHUB_URL = "https://github.com/wjdals988"
/** 공유 문구에 넣는 앱 받기 주소. 릴리스마다 같은 이름의 사본(pricehere.apk)을 올려 늘 최신 APK를 가리킨다. */
const val DOWNLOAD_URL = "https://github.com/wjdals988/pricehere/releases/latest/download/pricehere.apk"
const val ISSUES_URL = "https://github.com/wjdals988/pricehere/issues"
