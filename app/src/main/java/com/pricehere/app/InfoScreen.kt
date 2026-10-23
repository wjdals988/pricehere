package com.pricehere.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InfoScreen(
    viewModel: RatesViewModel,
    state: UiState,
    openVersion: Boolean = false,
    onVersionOpened: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter),
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

        VersionCard(
            state = state,
            forceOpen = openVersion,
            onOpened = onVersionOpened,
            onDismissUpdate = viewModel::dismissUpdate,
        )

        Spacer(Modifier.height(Space.m))

        ThemeCard(current = state.themeMode, onSelect = viewModel::setThemeMode)

        Spacer(Modifier.height(Space.m))

        WidgetCard()

        Spacer(Modifier.height(Space.m))

        SourceCard()

        Spacer(Modifier.height(Space.xl))
        InfoFooter()

        Spacer(Modifier.height(Space.xl))
    }
}

/** 정보에는 현재 버전만 두고, 탭하면 전체 변경 이력이 펼쳐진다. */
@Composable
private fun VersionCard(
    state: UiState,
    forceOpen: Boolean,
    onOpened: () -> Unit,
    onDismissUpdate: () -> Unit,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val update = state.update

    LaunchedEffect(forceOpen) {
        if (forceOpen) {
            open = true
            onOpened()
        }
    }

    SectionCard(background = MaterialTheme.colorScheme.surfaceContainer) {
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
                        Spacer(Modifier.width(Space.s))
                        Dot(Trend.rising, size = 7)
                    }
                }
                Text(
                    text = if (update != null) {
                        "새 버전 v${update.latestVersion} 이 나왔습니다"
                    } else {
                        CHANGELOG.firstOrNull()?.headline ?: "변경 이력"
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
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "다운로드",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(Radius.pillShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { open(context, update.downloadUrl ?: update.releaseUrl) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    text = "이 버전 넘기기",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(Radius.pillShape)
                        .clickable(onClick = onDismissUpdate)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (!open) return@SectionCard

        Spacer(Modifier.height(Space.l))
        CHANGELOG.forEachIndexed { index, release ->
            ReleaseCard(release = release, latest = index == 0)
            if (index != CHANGELOG.lastIndex) Spacer(Modifier.height(Space.s))
        }
    }
}

@Composable
private fun ThemeCard(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SectionCard(background = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text = "화면 테마",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "홈 화면 위젯은 항상 기기 설정을 따릅니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/**
 * 만든 사람·라이선스·고지는 기능이 아니므로 카드로 세우지 않고 맨 아래에 작고 흐리게 모은다.
 * 링크 글자는 작아도 누르는 영역은 48dp를 지킨다. 좁은 화면에서는 링크 줄이 알아서 접힌다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoFooter() {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            text = "여긴얼마? (PriceHere) · © $COPYRIGHT_YEAR $COPYRIGHT_HOLDER · $APP_LICENSE",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        // 링크의 누르는 영역이 왼쪽으로 8dp 나가 있으므로, 글자를 윗줄과 맞추려고 그만큼 당긴다.
        // 바깥으로 나가는 링크는 목적지의 아이콘이나 ↗, 앱 안에서 열리는 것은 ›로 구분한다.
        FlowRow(Modifier.offset(x = -Space.s)) {
            FooterLink("대시보드", R.drawable.ic_globe) { open(context, DASHBOARD_URL) }
            FooterLink("GitHub", R.drawable.ic_github) { open(context, GITHUB_URL) }
            FooterLink("문제 신고 ↗") { open(context, ISSUES_URL) }
            FooterLink("오픈소스 라이선스 ›") { showLicenses = true }
        }
        FooterNote(
            "개인정보를 수집하지 않습니다. 통신망 국가와 다른 앱에서 선택한 텍스트는 " +
                "환산에만 쓰고 기기 밖으로 보내지 않습니다. 환율과 새 버전을 확인할 때만 " +
                "네이버, Frankfurter, GitHub에 요청을 보냅니다."
        )
        Spacer(Modifier.height(Space.s))
        FooterNote(
            "환율은 참고용입니다. 하나은행 매매기준율은 네이버 마켓인덱스, 보조 환율은 " +
                "ECB 기준환율(Frankfurter API)에서 받아오며, 어느 곳과도 제휴 관계가 없습니다. " +
                "상표권은 각 권리자에게 있습니다. 실제 환전·결제 금액은 은행과 카드사의 " +
                "고시 환율과 수수료에 따라 달라집니다."
        )
    }

    if (showLicenses) LicenseSheet(onDismiss = { showLicenses = false })
}

@Composable
private fun FooterNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
    )
}

@Composable
private fun FooterLink(text: String, @DrawableRes icon: Int? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(Radius.badgeShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // 글자 높이에 맞춘 단색. 브랜드 색을 쓰지 않고 링크 색 하나로 통일한다.
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Space.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseSheet(onDismiss: () -> Unit) {
    val apache = OPEN_SOURCE.count { it.license == LICENSE_APACHE }
    val mit = OPEN_SOURCE.count { it.license == LICENSE_MIT }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "오픈소스 라이선스",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "${OPEN_SOURCE.size}개 · $LICENSE_APACHE ${apache}개, $LICENSE_MIT ${mit}개",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.m))
            OPEN_SOURCE.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${entry.owner} · ${entry.license}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Space.l))
            LicenseText(LICENSE_APACHE, R.raw.license_apache_2_0)
            Spacer(Modifier.height(Space.s))
            LicenseText("$LICENSE_MIT (Octicons)", R.raw.license_octicons_mit)
        }
    }
}

/** 라이선스 전문. 길어서 접어 두고, 펼칠 때만 res/raw에서 읽는다. */
@Composable
private fun LicenseText(title: String, @RawRes res: Int) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    SectionCard(background = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$title 전문",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Chevron(MaterialTheme.colorScheme.onSurfaceVariant, open)
        }
        if (open) {
            // 원문은 80자 폭으로 줄이 꺾여 있어 휴대폰 폭에서는 들쭉날쭉 끊긴다.
            // 빈 줄로 나뉜 문단은 그대로 두고, 문단 안의 줄바꿈과 들여쓰기만 풀어 다시 흐르게 한다.
            val text = remember(res) {
                context.resources.openRawResource(res).bufferedReader().use { it.readText() }
                    .trim()
                    .split(Regex("\\n\\s*\\n"))
                    .joinToString("\n\n") { it.trim().replace(Regex("\\s+"), " ") }
            }
            Spacer(Modifier.height(Space.m))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    SectionCard(background = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text = "홈 화면 위젯",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "길게 눌러 직접 배치해도 되고, 아래에서 바로 추가할 수 있습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.m))

        WidgetRow("환율 보기", "4×2 · 네 통화 환율과 등락") {
            pinWidget(context, RateWidgetProvider::class.java)
        }
        WidgetRow("빠른 계산", "4×2 · 통화와 금액을 골라 환산") {
            pinWidget(context, QuickWidgetProvider::class.java)
        }
        WidgetRow("환율 계산기", "4×3 · 위젯 안에서 사칙연산으로 계산") {
            pinWidget(context, KeypadWidgetProvider::class.java)
        }
    }
}

@Composable
private fun WidgetRow(title: String, detail: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
            // 위젯 추가는 부차 기능이다. solid 딥틸 버튼 3개가 세로로 서 있으면
            // 화면에서 가장 강한 요소가 되어 위계가 뒤집힌다.
            text = "추가",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(Radius.pillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onAdd)
                .padding(horizontal = Space.l, vertical = Space.s),
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
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "앞의 것이 실패하면 자동으로 다음 순서로 넘어갑니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.m))

        // 우선순위는 순서가 이미 말해 준다. 색까지 쓰면 3번이 "오류"처럼 보인다.
        SourceRow(1, "하나은행 매매기준율", "네이버 마켓인덱스 경유 · 고시회차마다 갱신")
        SourceRow(2, "ECB 기준환율", "Frankfurter API · 평일 16:00 CET 고시")
        SourceRow(3, "오프라인 캐시", "마지막으로 성공한 환율을 기기에 보관")
        // 수수료 설명은 홈 환율 카드가 결제 수단마다 이미 해 준다.
    }
}

@Composable
private fun SourceRow(order: Int, title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$order",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Space.m))
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
    // 최신 버전만 순백으로 한 단 올려 구분한다. 색을 바꾸지 않고 명도만 쓴다.
    SectionCard(
        background = if (latest) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "v${release.version}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (latest) {
                Spacer(Modifier.width(Space.s))
                Text(
                    text = "현재 버전",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(Radius.badgeShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = release.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Space.xs))
        Text(
            text = release.headline,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.m))
        release.notes.forEach { note ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.padding(top = 8.dp)) {
                    Dot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), size = 4)
                }
                Spacer(Modifier.width(Space.s))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }
        }
    }
}
