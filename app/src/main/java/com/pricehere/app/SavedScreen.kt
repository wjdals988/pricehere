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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant

@Composable
fun SavedScreen(viewModel: RatesViewModel, state: UiState) {
    val context = LocalContext.current
    var askClear by remember { mutableStateOf(false) }
    val items = state.saved

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
                text = "저장한 금액",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                RoundAction(onClick = {
                    shareText(context, buildSavedShareText(items, state.snapshot), "저장 목록 공유")
                }) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "저장 목록 공유",
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                RoundAction(
                    onClick = { viewModel.refresh(manual = true) },
                    enabled = !state.loading,
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "환율 새로고침",
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (items.isEmpty()) {
            EmptyState()
            return@Column
        }

        TotalCard(items = items, snapshot = state.snapshot, now = System.currentTimeMillis())

        Spacer(Modifier.height(14.dp))

        items.forEach { item ->
            SavedRow(
                item = item,
                currentRate = state.snapshot?.rates?.get(item.currencyCode),
                onDelete = { viewModel.deleteSaved(item.id) },
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "전체 삭제",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { askClear = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
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

@Composable
private fun EmptyState() {
    Spacer(Modifier.height(60.dp))
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
        Spacer(Modifier.height(18.dp))
        Text(
            text = "저장한 금액이 없습니다",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "환산 화면에서 금액을 넣고 결과 카드의 ＋ 저장을 누르면\n" +
                "여기에 쌓입니다. 나중에 최신 환율로 다시 계산해 보여줍니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TotalCard(items: List<SavedItem>, snapshot: Snapshot?, now: Long) {
    val total = items.sumOf { item ->
        val rate = snapshot?.rates?.get(item.currencyCode) ?: item.rateAtSave
        item.amount * rate
    }
    val totalAtSave = items.sumOf { it.krwAtSave }
    val diff = total - totalAtSave

    SectionCard(background = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text = "저장한 ${items.size}개 합계",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${format(total, 0)}원",
            style = amountStyle(MaterialTheme.colorScheme.onSurface, size = 28),
        )
        if (kotlin.math.abs(diff) >= 1.0) {
            Spacer(Modifier.height(6.dp))
            DeltaText(diff)
        }
        if (snapshot != null) {
            val minutes = ((now - snapshot.quotedAtMillis) / 60_000L).coerceAtLeast(0)
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(dotColor(snapshot.source, minutes))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${snapshot.source.short} · " +
                        "${CLOCK.format(Instant.ofEpochMilli(snapshot.quotedAtMillis))} 고시 기준",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SavedRow(item: SavedItem, currentRate: Double?, onDelete: () -> Unit) {
    val rate = currentRate ?: item.rateAtSave
    val nowKrw = item.amount * rate
    val diff = nowKrw - item.krwAtSave

    SectionCard(background = MaterialTheme.colorScheme.surface, corner = 18) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.currency.flag, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.memo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(32.dp)
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

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${format(nowKrw, 0)}원",
                style = amountStyle(MaterialTheme.colorScheme.onSurface, size = 26),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "${format(item.amount, 2)} ${item.currencyCode}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(9.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${DAY_STAMP.format(Instant.ofEpochMilli(item.savedAtMillis))} 저장 · " +
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
