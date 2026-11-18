package com.pricehere.app

import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun SavedScreen(viewModel: RatesViewModel, state: UiState, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askClear by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<Long>() }
    val items = state.saved

    // 목록이 비면 선택 모드를 유지할 이유가 없다.
    LaunchedEffect(items.size) {
        if (items.isEmpty()) {
            picking = false
            picked.clear()
        }
    }

    // 걸으면서 한 손으로 누르다 잘못 지우기 쉽다. 확인 창 대신 되돌리기를 준다.
    fun delete(item: SavedItem) {
        val removed = viewModel.deleteSaved(item.id) ?: return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "‘${removed.memo}’ 삭제했습니다",
                actionLabel = "되돌리기",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreSaved(removed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ambientGlow()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.l),
    ) {
        SavedHeader(
            picking = picking,
            pickedCount = picked.size,
            totalCount = items.size,
            onStartPicking = { picking = true; picked.clear() },
            onCancelPicking = { picking = false; picked.clear() },
            onToggleAll = {
                if (picked.size == items.size) {
                    picked.clear()
                } else {
                    picked.clear()
                    picked.addAll(items.map { it.id })
                }
            },
        )

        if (items.isEmpty()) {
            EmptyState()
            return@Column
        }

        TotalCard(
            items = items,
            state = state,
            now = System.currentTimeMillis(),
            onRefresh = { viewModel.refresh(manual = true) },
        )

        Spacer(Modifier.height(Space.l))

        // 항목을 한 묶음 목록으로. 카드가 항목마다 떠 있으면 합계 카드와 무게가 같아 보인다.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            items.forEachIndexed { i, item ->
                key(item.id) {
                    SavedRow(
                        item = item,
                        currentRate = state.snapshot?.rates?.get(item.currencyCode),
                        picking = picking,
                        checked = item.id in picked,
                        onToggle = {
                            if (item.id in picked) picked.remove(item.id) else picked.add(item.id)
                        },
                        onDelete = { delete(item) },
                    )
                }
                if (i < items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
        }

        Spacer(Modifier.height(Space.s))

        if (picking) {
            Spacer(Modifier.height(Space.s))
            ShareSelectedButton(
                count = picked.size,
                onShare = {
                    shareText(
                        context,
                        buildSavedShareText(items.filter { it.id in picked }, state.snapshot),
                        "저장 목록 공유",
                    )
                    picking = false
                    picked.clear()
                },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "왼쪽으로 밀어서 삭제",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "전체 삭제",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(Radius.badgeShape)
                        .clickable { askClear = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(NavClearance))
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("전체 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("저장한 ${items.size}건을 모두 삭제합니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = { askClear = false; viewModel.clearSaved() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("취소") } },
        )
    }
}

// ---------------------------------------------------------------- 헤더

@Composable
private fun SavedHeader(
    picking: Boolean,
    pickedCount: Int,
    totalCount: Int,
    onStartPicking: () -> Unit,
    onCancelPicking: () -> Unit,
    onToggleAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (picking) "${pickedCount}건 선택" else "저장한 금액",
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))

        if (picking) {
            TextAction(
                text = if (pickedCount == totalCount) "선택 해제" else "전체 선택",
                onClick = onToggleAll,
            )
            Spacer(Modifier.width(Space.xs))
            TextAction(text = "취소", onClick = onCancelPicking)
        } else if (totalCount > 0) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .glass(RoundedCornerShape(16.dp))
                    .clickable(onClick = onStartPicking),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "선택 공유",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(Radius.badgeShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun ShareSelectedButton(count: Int, onShare: () -> Unit) {
    val enabled = count > 0
    Text(
        text = if (enabled) "${count}건 공유" else "공유할 항목 선택",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.pillShape)
            .background(if (enabled) TealGradient else Brush.linearGradient(List(2) { MaterialTheme.colorScheme.surfaceContainerHigh }))
            .clickable(enabled = enabled, onClick = onShare)
            .padding(vertical = 16.dp),
    )
}

// ---------------------------------------------------------------- 비었을 때

@Composable
private fun EmptyState() {
    Spacer(Modifier.height(48.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .glass(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TagIcon(MaterialTheme.colorScheme.primary, sizeDp = 26)
        }
        Spacer(Modifier.height(Space.l))
        Text(
            text = "저장한 금액 없음",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = "홈에서 금액을 넣고 저장을 누르면 여기에 쌓입니다.\n" +
                "다시 열 때마다 최신 환율로 계산합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- 합계

/** 위 칸은 합계, 아래 민트 칸은 입국 면세 한도. 홈의 가격표와 같은 구성이다. */
@Composable
private fun TotalCard(items: List<SavedItem>, state: UiState, now: Long, onRefresh: () -> Unit) {
    val dark = isDark()
    val snapshot = state.snapshot
    val total = items.sumOf { item ->
        item.krwAt(snapshot?.rates?.get(item.currencyCode) ?: item.rateAtSave)
    }
    val totalAtSave = items.sumOf { it.krwAtSave }
    val diff = total - totalAtSave
    val mintTop = if (dark) Hero.darkTop else Color(0xFFDDF5EC)
    val mintBottom = if (dark) Hero.darkBottom else Color(0xFFCFEFE4)

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x330B3C32), spotColor = Color(0x660B3C32))
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        ) {
            Text(
                text = "${items.size}건 합계 · 현재 환율 기준",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xs)) {
                Text(
                    text = format(total, 0),
                    style = Num.amount(36).copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "원",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp),
                )
                if (kotlin.math.abs(diff) >= 1.0) {
                    Spacer(Modifier.width(Space.s))
                    DeltaPill(diff)
                }
            }
            if (snapshot != null) {
                val minutes = ((now - snapshot.quotedAtMillis) / 60_000L).coerceAtLeast(0)
                Spacer(Modifier.height(Space.xs))
                StatusLine(
                    snapshot = snapshot,
                    minutes = minutes,
                    unit = state.selected.quoteUnit,
                    code = state.selected.code,
                    outcome = state.outcome,
                    loading = state.loading,
                    onRefresh = onRefresh,
                )
            }
        }
        snapshot?.let { dutyFreeUsd(items, it.rates) }?.let { usd ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(mintTop, mintBottom)))
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
            ) {
                DutyFreeGauge(usd)
            }
        }
    }
}

/**
 * 입국 면세 한도까지 얼마나 찼는지. 쇼핑 중에 "이제 신고 대상인가"를 바로 보게 한다.
 * 1인 기준이고 주류·담배·향수는 따로라서, 넘었다고 단정하지 않고 참고 문구를 붙인다.
 */
@Composable
private fun DutyFreeGauge(usd: Double) {
    val over = usd > DUTY_FREE_USD
    val dark = isDark()
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val alert = if (dark) Trend.risingDark else Trend.rising
    val view = rememberView()

    // 화면에 들어오면 0에서 차오르고, 금액이 바뀌면 그 자리에서 이어서 움직인다.
    val target = (usd / DUTY_FREE_USD).toFloat().coerceIn(0f, 1f)
    val fill = remember { Animatable(0f) }
    LaunchedEffect(target) {
        // 처음에는 탭 전환(약 0.2초)이 끝난 뒤 차오르게 한다. 그 전에 차면 보이지 않는다.
        if (fill.value == 0f) delay(260)
        fill.animateTo(target, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow))
    }
    // 채움이 멈출 즈음 빛 한 줄이 왼쪽에서 오른쪽으로 한 번 지나간다.
    val sheen = remember { Animatable(-0.3f) }
    LaunchedEffect(target) {
        sheen.snapTo(-0.3f)
        delay(640)
        sheen.animateTo(1.3f, tween(900, easing = FastOutSlowInEasing))
    }
    // 한도를 넘는 순간에만 경고 진동 한 번(처음 그릴 때는 생략).
    var wasOver by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(over) {
        if (wasOver == false && over) Haptic.reject(view)
        wasOver = over
    }
    val barColor by animateColorAsState(if (over) alert else Color(0xFF0B6E5E), tween(360), label = "gauge")
    val textColor by animateColorAsState(if (over) alert else ink, tween(360), label = "gaugeText")
    val dollar = "$"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "입국 면세 한도",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            modifier = Modifier.weight(1f),
        )
        RollingNumber(
            text = dollar + format(usd, 0),
            style = Num.amount(16).copy(color = textColor, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        )
        Text(
            text = " / " + dollar + format(DUTY_FREE_USD, 0),
            style = Num.amount(16).copy(color = ink.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(Space.s))
    val track = ink.copy(alpha = 0.12f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp)),
    ) {
        drawRect(track)
        val w = size.width * fill.value
        if (w > 0f) {
            val r = CornerRadius(size.height / 2f)
            if (over) {
                drawRoundRect(barColor, size = Size(w, size.height), cornerRadius = r)
            } else {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF17A08C), barColor), endX = w.coerceAtLeast(1f)),
                    size = Size(w, size.height),
                    cornerRadius = r,
                )
            }
            // 빛 줄: 채워진 부분 안에서만 보인다.
            val x = size.width * sheen.value
            clipRect(right = w) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                        startX = x - 60.dp.toPx(),
                        endX = x + 60.dp.toPx(),
                    ),
                )
            }
        }
    }
    Spacer(Modifier.height(Space.s))
    Crossfade(targetState = over, label = "gaugeNote") { isOver ->
        Text(
            text = if (isOver) {
                "${format(usd - DUTY_FREE_USD, 0)}달러 초과 · 1인 기준, 주류·담배·향수 별도"
            } else {
                "${format(DUTY_FREE_USD - usd, 0)}달러 남음 · 1인 기준, 주류·담배·향수 별도"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isOver) alert else ink.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- 목록

private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d").withZone(ZoneId.of("Asia/Seoul"))

@Composable
private fun SavedRow(
    item: SavedItem,
    currentRate: Double?,
    picking: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val nowKrw = item.krwAt(currentRate ?: item.rateAtSave)
    val diff = nowKrw - item.krwAtSave
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !picking,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .sharedSaved("saved-row-${item.id}")
                .fillMaxWidth()
                .background(
                    if (picking && checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
                .clickable(enabled = picking, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (picking) {
                Checkbox(checked = checked, onCheckedChange = { onToggle() })
                Spacer(Modifier.width(Space.xs))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.memo,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${format(item.amount, item.currency.decimals)} ${item.currencyCode}" +
                        (if (item.mode != PriceMode.BASE) " · ${item.mode.shortLabel}" else "") +
                        " · ${SHORT_DAY.format(Instant.ofEpochMilli(item.savedAtMillis))}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = Num.FEATURES),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Space.m))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${format(nowKrw, 0)}원",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = Num.FEATURES),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                DeltaText(diff)
            }
        }
    }
}

/** 저장 시점 대비 원화 환산액이 얼마나 움직였는지. */
@Composable
private fun DeltaText(diff: Double) {
    if (kotlin.math.abs(diff) < 1.0) {
        Text(
            text = "변동 없음",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val rising = diff > 0
    val tint = trendTint(rising)
    Text(
        text = "${if (rising) "▲" else "▼"} ${format(kotlin.math.abs(diff), 0)}",
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = Num.FEATURES),
        fontWeight = FontWeight.Bold,
        color = tint,
        maxLines = 1,
    )
}

@Composable
private fun DeltaPill(diff: Double) {
    val rising = diff > 0
    val tint = trendTint(rising)
    Text(
        text = "${if (rising) "▲" else "▼"} ${format(kotlin.math.abs(diff), 0)}",
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = Num.FEATURES),
        fontWeight = FontWeight.Bold,
        color = tint,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(Radius.pillShape)
            .background(tint.copy(alpha = if (isDark()) 0.16f else 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun trendTint(rising: Boolean): Color {
    val dark = isDark()
    return when {
        rising && dark -> Trend.risingDark
        rising -> Trend.rising
        dark -> Trend.fallingDark
        else -> Trend.falling
    }
}
