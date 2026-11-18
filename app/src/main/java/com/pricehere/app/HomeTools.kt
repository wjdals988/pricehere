package com.pricehere.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.boundsInWindow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import kotlinx.coroutines.delay

// 홈 화면 도구 칸(추이·환급·팁)과 최근 저장. ConverterScreen에서 나눔(v2.13).

// ---------------------------------------------------------------- 도구

/** 도구 세 칸. 들어가 보기 전에 답(추이·환급액·팁 관행)이 먼저 보이게 값을 함께 적는다. */
@Composable
internal fun ToolTiles(
    state: UiState,
    onTrend: () -> Unit,
    onRefund: () -> Unit,
    onTip: () -> Unit,
) {
    val dark = isDark()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val points = state.history.map { it.rate }
    val summary = state.history.takeIf { it.size >= 2 }?.summarize()
    val up = (summary?.changePercent ?: 0.0) >= 0
    val trendColor = when {
        up && dark -> Trend.risingDark
        up -> Trend.rising
        dark -> Trend.fallingDark
        else -> Trend.falling
    }
    val trendValue = when {
        summary != null -> "${state.historyRange.label} ${if (up) "▲" else "▼"}${formatFixed(kotlin.math.abs(summary.changePercent), 2)}%"
        state.historyLoading -> "불러오는 중"
        else -> "그래프 보기"
    }

    val refund = state.selected.taxRefund
    val price = state.foreignAmount
    val refundValue = when {
        refund == null -> "제도 없음"
        price > 0 -> "예상 ${format(refund.estimatedRefund(price), state.selected.decimals)} ${state.selected.code}"
        else -> "부가세 ${format(refund.vatPercent, 0)}%"
    }

    val tip = state.selected.tip.suggested
    val tipValue = if (tip.firstOrNull() == 0) "팁 문화 없음" else "${tip.first()}~${tip.last()}%"

    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        ToolTile(
            label = "환율 추이",
            value = trendValue,
            valueColor = if (summary != null) trendColor else muted,
            onClick = onTrend,
            modifier = Modifier.weight(1f),
        ) {
            if (points.size >= 2) {
                Sparkline(points, trendColor, Modifier.fillMaxWidth().height(26.dp))
            } else {
                TrendLineIcon(ink)
            }
        }
        ToolTile(
            label = "세금 환급",
            value = refundValue,
            valueColor = if (refund != null) MaterialTheme.colorScheme.primary else muted,
            onClick = onRefund,
            enabled = refund != null,
            modifier = Modifier.weight(1f),
        ) { ReceiptIcon(ink, sizeDp = 26) }
        ToolTile(
            label = "팁",
            value = tipValue,
            valueColor = muted,
            onClick = onTip,
            modifier = Modifier.weight(1f),
        ) { CoinIcon(ink, sizeDp = 26) }
    }
}

@Composable
internal fun ToolTile(
    label: String,
    value: String,
    valueColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visual: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            // v2.13: 위아래 여백을 줄여 숫자를 넣어도 탭 바와 맞닿지 않게.
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) { visual() }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = Num.FEATURES),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TrendLineIcon(tint: Color) {
    Canvas(Modifier.size(26.dp)) {
        val k = size.minDimension / 24f
        val s = Stroke(width = 1.7f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(tint, Offset(4f * k, 19f * k), Offset(20f * k, 19f * k), 1.7f * k, StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(5f * k, 15f * k); lineTo(9.5f * k, 10.5f * k); lineTo(13f * k, 13.5f * k); lineTo(19f * k, 7f * k)
            },
            tint,
            style = s,
        )
    }
}

// ---------------------------------------------------------------- 최근 저장

@Composable
internal fun RecentSaved(state: UiState, onOpen: () -> Unit) {
    Column {
        Text(
            text = "최근 저장",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = Space.s),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            val recent = state.saved.take(2)
            recent.forEachIndexed { i, item ->
                val won = item.krwAt(state.snapshot?.rates?.get(item.currencyCode) ?: item.rateAtSave)
                Row(
                    modifier = Modifier
                        .sharedSaved("saved-row-${item.id}")
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.memo,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Space.m))
                    Text(
                        text = "${format(won, 0)}원",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = Num.FEATURES),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                if (i < recent.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}
