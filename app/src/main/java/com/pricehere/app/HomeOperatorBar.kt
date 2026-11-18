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

// 키보드 위 연산자 줄. ConverterScreen에서 나눔(v2.13).

// ---------------------------------------------------------------- 연산자 줄

internal val OPERATOR_CHIPS = listOf('+' to "+", '-' to "−", '*' to "×", '/' to "÷")

/** 연산자 줄 전체 높이(키 44 + 안쪽 여백 + 바깥 여백). 스크롤 끝 여백 계산에 쓴다. */
internal val OPERATOR_BAR_HEIGHT = 68.dp

/**
 * 시스템 숫자 키보드에는 연산자가 없다. 키보드가 떠 있는 동안 그 바로 위에 붙는 글래스 연산자 줄.
 * 끝에는 공유를 둔다. 계산하자마자 보내는 경우가 많고, 저장은 가격표 안에 그대로 있다(v2.11).
 */
@Composable
internal fun OperatorBar(modifier: Modifier, onOperator: (Char) -> Unit, onShare: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .glass(RoundedCornerShape(20.dp))
            .padding(6.dp)
            .height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OPERATOR_CHIPS.forEach { (op, label) ->
            OperatorKey(op = op, label = label, modifier = Modifier.weight(1f)) { onOperator(op) }
        }
        Row(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(TealGradient)
                .clickable(onClick = onShare),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = Color.White,
            )
            Spacer(Modifier.width(5.dp))
            Text("공유", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

/**
 * 연산자 키. 글꼴의 + − × ÷ 는 글리프마다 크기·높이가 달라(− 가 유독 짧고 낮다) 줄이 들쭉날쭉해진다.
 * 같은 굵기의 선으로 직접 그려 네 기호의 크기와 중심을 맞춘다.
 */
@Composable
internal fun OperatorKey(op: Char, label: String, modifier: Modifier, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = OPERATOR_NAMES.getValue(op) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = 2.dp.toPx()
            val half = 6.5.dp.toPx()
            val c = center
            fun line(dx: Float, dy: Float) = drawLine(
                color = ink,
                start = Offset(c.x - dx, c.y - dy),
                end = Offset(c.x + dx, c.y + dy),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            when (op) {
                '+' -> { line(half, 0f); line(0f, half) }
                '-' -> line(half, 0f)
                // 대각선은 같은 길이면 + 보다 커 보여서 0.8배로 줄인다.
                '*' -> { val d = half * 0.8f; line(d, d); line(d, -d) }
                '/' -> {
                    line(half, 0f)
                    val dot = 4.2.dp.toPx()
                    drawCircle(ink, radius = stroke * 0.65f, center = Offset(c.x, c.y - dot))
                    drawCircle(ink, radius = stroke * 0.65f, center = Offset(c.x, c.y + dot))
                }
            }
        }
    }
}

internal val OPERATOR_NAMES = mapOf('+' to "더하기", '-' to "빼기", '*' to "곱하기", '/' to "나누기")
