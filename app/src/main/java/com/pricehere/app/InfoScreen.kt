package com.pricehere.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InfoScreen(viewModel: RatesViewModel, state: UiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "정보",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        VersionCard(state = state, onDismissUpdate = viewModel::dismissUpdate)

        Spacer(Modifier.height(12.dp))

        ThemeCard(current = state.themeMode, onSelect = viewModel::setThemeMode)

        Spacer(Modifier.height(12.dp))

        WidgetCard()

        Spacer(Modifier.height(12.dp))

        SourceCard()

        Spacer(Modifier.height(12.dp))

        MakerCard()

        Spacer(Modifier.height(12.dp))

        LicenseCard()

        Spacer(Modifier.height(16.dp))
        Text(
            text = "이 앱은 환율 정보를 참고용으로만 제공합니다. " +
                "실제 환전·결제 금액은 은행과 카드사의 고시 환율 및 수수료에 따라 달라집니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(28.dp))
    }
}

/** 정보에는 현재 버전만 두고, 탭하면 전체 변경 이력이 펼쳐진다. */
@Composable
private fun VersionCard(state: UiState, onDismissUpdate: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val update = state.update

    SectionCard(background = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "현재 버전 $APP_VERSION",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (update != null) {
                        Spacer(Modifier.width(7.dp))
                        Dot(Trend.rising, size = 7)
                    }
                }
                Text(
                    text = if (update != null) {
                        "새 버전 v${update.latestVersion} 이 나왔습니다"
                    } else {
                        "탭하면 그동안 무엇이 바뀌었는지 볼 수 있습니다"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (update != null) {
                        Trend.rising
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Chevron(MaterialTheme.colorScheme.onSurfaceVariant, open)
        }

        if (update != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "다운로드",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { open(context, update.downloadUrl ?: update.releaseUrl) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Text(
                    text = "이 버전 넘기기",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismissUpdate)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (!open) return@SectionCard

        Spacer(Modifier.height(14.dp))
        CHANGELOG.forEachIndexed { index, release ->
            ReleaseCard(release = release, latest = index == 0)
            if (index != CHANGELOG.lastIndex) Spacer(Modifier.height(9.dp))
        }
    }
}

@Composable
private fun ThemeCard(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SectionCard(background = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(
            text = "화면 테마",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "홈 화면 위젯은 항상 기기 설정을 따릅니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ThemeMode.entries.forEach { mode ->
                Pill(
                    text = mode.label,
                    selected = mode == current,
                    modifier = Modifier.weight(1f),
                ) { onSelect(mode) }
            }
        }
    }
}

@Composable
private fun MakerCard() {
    val context = LocalContext.current
    SectionCard(background = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text = "만든 사람",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$COPYRIGHT_HOLDER 이 직접 설계하고 만들었습니다. " +
                "다른 프로젝트도 대시보드에 정리해 두었습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        LinkRow("프로젝트 대시보드", DASHBOARD_URL) { open(context, DASHBOARD_URL) }
        LinkRow("GitHub", GITHUB_URL) { open(context, GITHUB_URL) }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "© $COPYRIGHT_YEAR $COPYRIGHT_HOLDER · 여긴얼마(PriceHere) · $APP_LICENSE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun LinkRow(title: String, url: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = url.removePrefix("https://"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "↗",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LicenseCard() {
    var open by remember { mutableStateOf(false) }
    SectionCard(background = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "오픈소스 라이선스",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${OPEN_SOURCE.size}개 라이브러리 · 모두 Apache License 2.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Chevron(MaterialTheme.colorScheme.onSurfaceVariant, open)
        }

        if (!open) return@SectionCard

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))

        OPEN_SOURCE.forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${entry.owner} · ${entry.license}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "환율 데이터 고지",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "매매기준율은 하나은행 고시 환율이며 네이버 마켓인덱스를 통해 조회합니다. " +
                "보조 환율은 유럽중앙은행(ECB) 기준환율을 Frankfurter API로 받아옵니다. " +
                "두 출처 모두 이 앱과 제휴 관계가 없으며, 상표권은 각 권리자에게 있습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
    }
}

private fun open(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun WidgetCard() {
    val context = LocalContext.current
    SectionCard(background = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text = "홈 화면 위젯",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "길게 눌러 직접 배치해도 되고, 아래에서 바로 추가할 수 있습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        WidgetRow("환율 보기", "4×2 · 네 통화 환율과 등락") {
            pinWidget(context, RateWidgetProvider::class.java)
        }
        WidgetRow("빠른 계산", "4×2 · 통화와 금액을 골라 환산") {
            pinWidget(context, QuickWidgetProvider::class.java)
        }
        WidgetRow("환율 계산기", "4×5 · 위젯 안에서 금액 입력") {
            pinWidget(context, KeypadWidgetProvider::class.java)
        }
    }
}

@Composable
private fun WidgetRow(title: String, detail: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "추가",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** 런처가 지원하면 시스템 배치 다이얼로그를 띄운다. */
private fun pinWidget(context: Context, provider: Class<*>) {
    val manager = AppWidgetManager.getInstance(context)
    val ok = if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, provider), null, null)
    } else {
        false
    }
    if (!ok) {
        Toast.makeText(
            context,
            "홈 화면을 길게 눌러 위젯에서 직접 추가해 주세요.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

@Composable
private fun SourceCard() {
    SectionCard {
        Text(
            text = "환율 데이터 출처",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "앞의 것이 실패하면 자동으로 다음 순서로 넘어갑니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        SourceRow(1, "하나은행 매매기준율", "네이버 마켓인덱스 경유 · 고시회차마다 갱신", Freshness.fresh)
        SourceRow(2, "ECB 기준환율", "Frankfurter API · 평일 16:00 CET 고시", Freshness.aging)
        SourceRow(3, "오프라인 캐시", "마지막으로 성공한 환율을 기기에 보관", Freshness.stale)

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "매매기준율은 은행이 고시하는 기준 환율입니다. " +
                "카드 결제나 현찰 환전에는 여기에 수수료가 더 붙습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun SourceRow(order: Int, title: String, detail: String, accent: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$order",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReleaseCard(release: Release, latest: Boolean) {
    SectionCard(
        background = if (latest) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        corner = 18,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "v${release.version}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (latest) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "현재 버전",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = release.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(3.dp))
        Text(
            text = release.headline,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(11.dp))
        release.notes.forEach { note ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.padding(top = 7.dp)) {
                    Dot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), size = 4)
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }
        }
    }
}
