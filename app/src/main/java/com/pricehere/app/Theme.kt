package com.pricehere.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 다이내믹 컬러(월페이퍼 기반)를 일부러 쓰지 않는다.
 * 기기마다 색이 달라지면 "환율 신선도"를 색으로 알리는 신호가 흐려지기 때문이다.
 */
private val Light = lightColorScheme(
    primary = Color(0xFF0B6E5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7F1E5),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635C),
    background = Color(0xFFF6F8F8),
    onBackground = Color(0xFF141C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141C1A),
    surfaceVariant = Color(0xFFE6ECEA),
    onSurfaceVariant = Color(0xFF5B6B67),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F3F3),
    surfaceContainerHigh = Color(0xFFE9EEED),
    surfaceContainerHighest = Color(0xFFE2E9E7),
    outline = Color(0xFF8A9A96),
    outlineVariant = Color(0xFFDCE4E2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5FD9C0),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFC7F1E5),
    secondary = Color(0xFFB1CCC4),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF161E1C),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF2A3532),
    onSurfaceVariant = Color(0xFF9FB0AB),
    surfaceContainerLow = Color(0xFF161E1C),
    surfaceContainer = Color(0xFF1A2321),
    surfaceContainerHigh = Color(0xFF232D2A),
    surfaceContainerHighest = Color(0xFF2D3835),
    outline = Color(0xFF6A7A76),
    outlineVariant = Color(0xFF2E3936),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** 환율 신선도 표시용. 라이트/다크 모두에서 대비가 확보되는 값으로 고른다. */
object Freshness {
    val fresh = Color(0xFF12A150)
    val aging = Color(0xFFE08700)
    val stale = Color(0xFFDC3545)
}

/** 국내 금융 관행대로 상승은 빨강, 하락은 파랑으로 쓴다. */
object Trend {
    val rising = Color(0xFFE12D39)
    val falling = Color(0xFF1B64DA)
    val risingDark = Color(0xFFFF7A82)
    val fallingDark = Color(0xFF7FA9F5)
}

/** 결과 카드 그라디언트. 단색보다 깊이가 생겨 "답"이라는 인상이 강해진다. */
object Hero {
    val lightTop = Color(0xFFD8F5EC)
    val lightBottom = Color(0xFFAFE9D5)
    val darkTop = Color(0xFF0D5248)
    val darkBottom = Color(0xFF07332D)
}

/**
 * 지금 다크 테마인지. 색을 직접 골라야 하는 소수 지점에서만 쓴다.
 * 사용자가 테마를 고정했을 수 있으므로 시스템 설정을 직접 보지 않는다.
 */
private val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun isDark(): Boolean = LocalIsDark.current

@Composable
fun PriceHereTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            content = content,
        )
    }
}
