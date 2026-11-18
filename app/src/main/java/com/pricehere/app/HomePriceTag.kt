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

// 홈 화면 가격표: 외화·원화 칸, 이음매, 빠른 금액. ConverterScreen에서 나눔(v2.13).

// ---------------------------------------------------------------- 가격표 (주인공)

/**
 * 입력 · 스왑 · 결과 · 빠른 금액을 가격표 하나로 묶는다.
 * 위 칸(입력)과 아래 칸(결과) 사이를 점선과 양옆 홈으로 끊어 가격표처럼 보이게 하고,
 * 빠른 금액은 계산하는 자리 바로 밑 칸에 붙인다.
 */
@Composable
internal fun PriceTag(
    state: UiState,
    resultText: String,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    ticks: Boolean,
    saveModifier: Modifier,
    onSwap: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onAdd: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val dark = isDark()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val mintTop = if (dark) Hero.darkTop else Color(0xFFDDF5EC)
    val mintBottom = if (dark) Hero.darkBottom else Color(0xFFCFEFE4)
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val top = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val bottom = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    var selectAllNext by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color(0x330B3C32),
                spotColor = Color(0x660B3C32),
            ),
    ) {
        // 위 칸은 늘 외화, 아래 칸은 늘 원화. 자리는 바뀌지 않고, 누른 칸이 입력 칸이 된다.
        // 입력하지 않는 칸은 환산값을 보여주고, 길게 누르면 그 값을 복사한다.
        val foreignActive = state.foreignToKrw
        var field by remember { mutableStateOf(TextFieldValue(state.input)) }
        if (field.text != state.input) {
            // 칸을 옮긴 직후에는 넘어온 값을 통째로 선택해 둔다. 바로 치면 새 금액으로 바뀐다.
            val range = if (selectAllNext) TextRange(0, state.input.length) else TextRange(state.input.length)
            field = TextFieldValue(state.input, range)
            selectAllNext = false
        }
        var focusAfterSwitch by remember { mutableStateOf(false) }
        LaunchedEffect(foreignActive) {
            if (focusAfterSwitch) {
                focusAfterSwitch = false
                focusRequester.requestFocus()
            }
        }
        val activate: (Boolean) -> Unit = { wantForeign ->
            if (wantForeign == foreignActive) {
                focusRequester.requestFocus()
            } else {
                selectAllNext = state.result != null && state.result != 0.0
                focusAfterSwitch = true
                onSwap()
            }
        }
        val onFieldChange: (TextFieldValue) -> Unit = {
            field = it
            onValueChange(it.text)
        }
        val calcText = state.amountOrNull?.let { format(it, state.fromDecimals) } ?: "—"
        val code = state.selected.code

        // 위 칸: 외화
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(top)
                .background(surface)
                .combinedClickable(
                    onClickLabel = "$code 입력",
                    onLongClickLabel = if (foreignActive) null else "결과 복사",
                    onLongClick = if (foreignActive) null else onCopy,
                    onClick = { activate(true) },
                )
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.selected.koreanName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = muted,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                // 식이면 계산값을 보여준다. 줄이 새로 생기지 않아 카드가 흔들리지 않는다.
                if (foreignActive && state.isExpression) {
                    Text(
                        text = "= $calcText $code",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = Num.FEATURES),
                        color = muted,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                if (foreignActive) {
                    AmountField(
                        value = field,
                        onValueChange = onFieldChange,
                        style = Num.amount(34).copy(color = MaterialTheme.colorScheme.onSurface),
                        placeholderColor = muted.copy(alpha = 0.3f),
                        focusRequester = focusRequester,
                        onFocusChange = onFocusChange,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(Modifier.weight(1f)) {
                        RollingNumber(
                            text = resultText,
                            style = Num.amount(34).copy(color = MaterialTheme.colorScheme.onSurface),
                            ticks = ticks,
                        )
                    }
                }
                if (!(foreignActive && state.isExpression)) {
                    Spacer(Modifier.width(Space.s))
                    Text(
                        text = code,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = muted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }

        // 이음매: 점선 + 양옆 홈. 오른쪽 버튼은 입력 칸을 위아래로 옮긴다. 화살표 방향이 지금 입력 방향.
        val swapTurn by animateFloatAsState(
            targetValue = if (state.foreignToKrw) 0f else 180f,
            animationSpec = tween(300),
            label = "swap",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(SeamShape(11.dp))
                .seam(top = surface, bottom = SolidColor(mintTop), dash = MaterialTheme.colorScheme.outlineVariant, inset = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (dark) TealGradient else InkGradient)
                    .clickable { activate(!foreignActive) }
                    // 화면 읽기에서 무엇이 바뀌는지 분명하게: 누르면 입력할 통화를 말한다.
                    .semantics { contentDescription = if (foreignActive) "원화로 입력" else "${code}로 입력" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.rotate(swapTurn)) { SwapIcon(Color.White, sizeDp = 17) }
            }
        }

        // 아래 칸: 원화
        val size = when {
            (if (foreignActive) resultText else state.input).length > 12 -> 32
            (if (foreignActive) resultText else state.input).length > 9 -> 38
            else -> 44
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(mintTop, mintBottom)))
                .combinedClickable(
                    onClickLabel = "원화 입력",
                    onLongClickLabel = if (foreignActive) "결과 복사" else null,
                    onLongClick = if (foreignActive) onCopy else null,
                    onClick = { activate(false) },
                )
                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "대한민국 원 · ${state.priceMode.label}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ink.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (!foreignActive && state.isExpression) {
                    Text(
                        text = "= ${calcText}원",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = Num.FEATURES),
                        color = ink.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Space.xs)) {
                if (foreignActive) {
                    RollingNumber(
                        text = resultText,
                        style = Num.amount(size).copy(color = ink, fontWeight = FontWeight.Bold),
                        ticks = ticks,
                    )
                } else {
                    AmountField(
                        value = field,
                        onValueChange = onFieldChange,
                        style = Num.amount(size).copy(color = ink, fontWeight = FontWeight.Bold),
                        placeholderColor = ink.copy(alpha = 0.3f),
                        focusRequester = focusRequester,
                        onFocusChange = onFocusChange,
                        // 글자 폭만큼만 차지해야 "원"이 숫자 바로 뒤에 붙는다.
                        modifier = Modifier.weight(1f, fill = false).width(IntrinsicSize.Min),
                    )
                }
                if (!(!foreignActive && state.isExpression)) {
                    Text(
                        text = "원",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        modifier = Modifier.padding(start = 2.dp, bottom = (size / 6).dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.s)) {
                // 원화는 "약 3만 7천원"으로 감을 준다. 만 원 미만이면 처음 몇 번만 사용법 안내.
                val won = if (foreignActive) state.result else state.amountOrNull
                val approx = won?.let(::approxWon)
                Text(
                    text = approx ?: if (state.showHints && foreignActive) "눌러서 원화 입력 · 길게 눌러 복사" else "",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (approx != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (approx != null) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.5f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = saveModifier
                        .height(36.dp)
                        .clip(Radius.pillShape)
                        .background(TealGradient)
                        .clickable(onClick = onSave)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TagIcon(Color.White, sizeDp = 15)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "저장",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }

        // 맨 아래 칸: 빠른 금액. 누를 때마다 더한다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(bottom)
                .background(surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val large = isLargeFont()
            state.quickAmounts.take(4).forEach { value ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onAdd(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        // 큰 글자에서는 "+1만"처럼 줄여 버튼 안에 들어가게.
                        text = "+" + if (large) compactWon(value) else format(value.toDouble(), 0),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = Num.FEATURES),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            val canClear = state.input.isNotEmpty()
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(enabled = canClear, onClick = onClear)
                    .semantics { contentDescription = "입력 지우기" },
                contentAlignment = Alignment.Center,
            ) {
                ClearIcon(muted.copy(alpha = if (canClear) 1f else 0.35f))
            }
        }
    }
}

/** 가격표의 금액 입력 칸. 위(외화)·아래(원화) 칸이 같은 입력 상태를 번갈아 쓴다. */
@Composable
internal fun AmountField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    style: androidx.compose.ui.text.TextStyle,
    placeholderColor: Color,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = ExpressionTransformation,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) },
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) Text("0", style = style.copy(color = placeholderColor))
                inner()
            }
        },
    )
}

@Composable
internal fun ClearIcon(tint: Color) {
    Canvas(Modifier.size(14.dp)) {
        val w = 1.8.dp.toPx()
        drawLine(tint, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
        drawLine(tint, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
    }
}

/** 큰 글자용 짧은 금액. 10,000 → 1만, 500,000 → 50만, 5,000 → 5천, 500 → 500. */
internal fun compactWon(value: Long): String = when {
    value >= 10_000 && value % 10_000 == 0L -> "${value / 10_000}만"
    value >= 1_000 && value % 1_000 == 0L -> "${value / 1_000}천"
    else -> format(value.toDouble(), 0)
}
