package com.pricehere.app

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter),
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

        items.forEach { item ->
            SavedRow(
                item = item,
                currentRate = state.snapshot?.rates?.get(item.currencyCode),
                picking = picking,
                checked = item.id in picked,
                onToggle = {
                    if (item.id in picked) picked.remove(item.id) else picked.add(item.id)
                },
                onShare = {
                    shareText(
                        context,
                        buildSavedShareText(listOf(item), state.snapshot),
                        "${item.memo} 공유",
                    )
                },
                // 걸으면서 한 손으로 누르다 잘못 지우기 쉽다. 확인 창 대신 되돌리기를 준다.
                onDelete = {
                    val removed = viewModel.deleteSaved(item.id) ?: return@SavedRow
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(
                            message = "‘${removed.memo}’ 삭제했습니다",
                            actionLabel = "되돌리기",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restoreSaved(removed)
                    }
                },
            )
            Spacer(Modifier.height(Space.m))
        }

        Spacer(Modifier.height(Space.s))

        if (picking) {
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "전체 삭제",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(Radius.badgeShape)
                        .clickable { askClear = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(Space.xl))
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("전체 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("저장한 ${items.size}개를 모두 지웁니다. 되돌릴 수 없습니다.") },
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
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (picking) "${pickedCount}개 선택" else "저장한 금액",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
            color = MaterialTheme.colorScheme.onBackground,
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
            RoundAction(onClick = onStartPicking) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "골라서 공유",
                    modifier = Modifier.size(17.dp),
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
        text = if (enabled) "${count}개 공유하기" else "공유할 항목을 골라 주세요",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.pillShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(enabled = enabled, onClick = onShare)
            .padding(vertical = 16.dp),
    )
}

// ---------------------------------------------------------------- 목록

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
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text("🏷", fontSize = 26.sp)
        }
        Spacer(Modifier.height(Space.l))
        Text(
            text = "저장한 금액이 없습니다",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = "홈 화면에서 금액을 넣고 결과 카드의 ＋ 저장을 누르면\n" +
                "여기에 쌓입니다. 나중에 최신 환율로 다시 계산해 보여줍니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TotalCard(items: List<SavedItem>, state: UiState, now: Long, onRefresh: () -> Unit) {
    val snapshot = state.snapshot
    val total = items.sumOf { item ->
        item.krwAt(snapshot?.rates?.get(item.currencyCode) ?: item.rateAtSave)
    }
    val totalAtSave = items.sumOf { it.krwAtSave }
    val diff = total - totalAtSave

    SectionCard(background = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text = "저장한 ${items.size}개 합계",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = "${format(total, 0)}원",
            style = amountStyle(MaterialTheme.colorScheme.onSurface, size = 28),
        )
        if (kotlin.math.abs(diff) >= 1.0) {
            Spacer(Modifier.height(Space.s))
            DeltaText(diff)
        }
        snapshot?.let { dutyFreeUsd(items, it.rates) }?.let { usd ->
            Spacer(Modifier.height(Space.m))
            DutyFreeGauge(usd)
        }
        if (snapshot != null) {
            val minutes = ((now - snapshot.quotedAtMillis) / 60_000L).coerceAtLeast(0)
            Spacer(Modifier.height(Space.s))
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
}

/**
 * 입국 면세 한도까지 얼마나 찼는지. 쇼핑 중에 "이제 신고 대상인가"를 바로 보게 한다.
 * 1인 기준이고 술·담배·향수는 따로라서, 넘었다고 단정하지 않고 참고 문구를 붙인다.
 */
@Composable
private fun DutyFreeGauge(usd: Double) {
    val over = usd > DUTY_FREE_USD
    val dark = isDark()
    val tint = when {
        over && dark -> Trend.risingDark
        over -> Trend.rising
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        text = if (over) {
            "입국 면세 한도를 ${format(usd - DUTY_FREE_USD, 0)}달러 넘었습니다"
        } else {
            "입국 면세 한도 ${format(DUTY_FREE_USD, 0)}달러 중 ${format(usd, 0)}달러"
        },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (over) tint else MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(Space.xs))
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth((usd / DUTY_FREE_USD).toFloat().coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tint),
        )
    }
    Spacer(Modifier.height(Space.xs))
    Text(
        text = "1인 기준 참고값 · 술·담배·향수는 따로 셉니다",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SavedRow(
    item: SavedItem,
    currentRate: Double?,
    picking: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val nowKrw = item.krwAt(currentRate ?: item.rateAtSave)
    val diff = nowKrw - item.krwAtSave

    SectionCard(
        modifier = if (picking) Modifier.clickable(onClick = onToggle) else Modifier,
        background = if (picking && checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        corner = 18,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (picking) {
                Checkbox(checked = checked, onCheckedChange = { onToggle() })
                Spacer(Modifier.width(Space.xs))
            }
            Text(item.currency.flag, fontSize = Num.flag)
            Spacer(Modifier.width(Space.s))
            Text(
                text = item.memo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (!picking) {
                // 공유·삭제가 맞붙어 있어 Compose의 터치 영역 자동 확장(48dp)이 서로 겹친다.
                // 보이는 크기 자체를 40dp로 키워 이웃 버튼을 잘못 누를 여지를 줄인다.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onShare),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "이 항목만 공유",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "삭제",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.m))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${format(nowKrw, 0)}원",
                style = amountStyle(MaterialTheme.colorScheme.onSurface, size = 26),
            )
            Spacer(Modifier.width(Space.s))
            Text(
                text = "${format(item.amount, item.currency.decimals)} ${item.currencyCode}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Spacer(Modifier.height(Space.m))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Space.s))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${DAY_STAMP.format(Instant.ofEpochMilli(item.savedAtMillis))} 저장 · " +
                    (if (item.mode != PriceMode.BASE) "${item.mode.shortLabel} · " else "") +
                    "${format(item.krwAtSave, 0)}원",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DeltaText(diff)
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
    val dark = isDark()
    val tint = when {
        rising && dark -> Trend.risingDark
        rising -> Trend.rising
        dark -> Trend.fallingDark
        else -> Trend.falling
    }
    Text(
        text = "${if (rising) "▲" else "▼"} ${format(kotlin.math.abs(diff), 0)}원",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = tint,
    )
}
