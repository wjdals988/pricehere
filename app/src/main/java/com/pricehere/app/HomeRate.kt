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

// 홈 화면 환율 줄: 환율·등락·고시 상태·새로고침. ConverterScreen에서 나눔(v2.13).

// ---------------------------------------------------------------- 환율 줄

@Composable
internal fun RateRow(state: UiState, now: Long, onRefresh: () -> Unit) {
    val snapshot = state.snapshot
    val change = state.change
    val minutes = snapshot?.let { ((now - it.quotedAtMillis) / 60_000L).coerceAtLeast(0) } ?: 0L
    val freshness = snapshot?.let { dotColor(it.source, minutes) } ?: Freshness.stale

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.rateLabel ?: "환율 없음",
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = Num.FEATURES),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
                if (change != null && change.direction != 0 && state.priceMode == PriceMode.BASE) {
                    Spacer(Modifier.width(Space.s))
                    ChangePill(change, state.selected.quoteUnit)
                }
            }
            Spacer(Modifier.height(3.dp))
            if (snapshot == null) {
                // 처음 설치한 뒤 오프라인이면 캐시도 없다.
                Text(
                    text = if (state.loading) "환율 받는 중" else "환율 수신 실패 · 네트워크 확인 필요",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.loading) MaterialTheme.colorScheme.onSurfaceVariant else Freshness.stale,
                    maxLines = 1,
                )
            } else {
                val (text, color) = statusText(
                    snapshot = snapshot,
                    minutes = minutes,
                    unit = state.selected.quoteUnit,
                    code = state.selected.code,
                    outcome = state.outcome,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(freshness, live = freshness == Freshness.fresh)
                    Spacer(Modifier.width(6.dp))
                    Crossfade(targetState = text to color, label = "status") { (t, c) ->
                        Text(
                            text = t,
                            style = MaterialTheme.typography.bodySmall,
                            color = c,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(Space.s))
        val confirmed = state.outcome?.let { !it.throttled && it.after != null && !it.changed } == true
        GlassIconButton(description = "환율 새로고침", onClick = onRefresh, enabled = !state.loading) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = freshness)
            } else {
                Icon(
                    imageVector = if (confirmed) Icons.Filled.Check else Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = if (snapshot == null || freshness != Freshness.fresh) freshness else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun ChangePill(change: Change, unit: Int) {
    val rising = change.direction > 0
    val dark = isDark()
    val tint = when {
        rising && dark -> Trend.risingDark
        rising -> Trend.rising
        dark -> Trend.fallingDark
        else -> Trend.falling
    }
    Text(
        text = "${if (rising) "▲" else "▼"} ${formatFixed(change.amount * unit, 2)}",
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = Num.FEATURES),
        fontWeight = FontWeight.Bold,
        color = tint,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(Radius.pillShape)
            .background(tint.copy(alpha = if (dark) 0.16f else 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
