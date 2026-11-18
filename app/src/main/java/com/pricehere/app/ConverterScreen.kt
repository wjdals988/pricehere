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

@Composable
fun ConverterScreen(
    viewModel: RatesViewModel,
    state: UiState,
    onOpenVersion: () -> Unit,
    onOpenSaved: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val amountFocus = remember { FocusRequester() }
    var askMemo by remember { mutableStateOf(false) }
    // 빠른 금액 버튼으로 바뀐 숫자에만 틱 진동을 준다. 키보드 입력이면 끈다.
    var rollTicks by remember { mutableStateOf(false) }
    // 저장 태그가 날아갈 출발점: 가격표의 저장 버튼 중심(창 기준).
    var saveAnchor by remember { mutableStateOf(Offset.Zero) }
    val fly = LocalFlyToSaved.current
    val scope = rememberCoroutineScope()
    val view = rememberView()
    var showUpdate by remember { mutableStateOf(false) }
    var showTrend by remember { mutableStateOf(false) }
    var showTip by remember { mutableStateOf(false) }
    var showRefund by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }

    // 신선도 문구를 30초마다 다시 계산한다.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    // 도구 칸의 미니 차트용. 통화를 바꾸면 select()가 추이를 비우므로 그때마다 다시 받는다.
    LaunchedEffect(state.selected) {
        if (state.history.isEmpty() && !state.historyLoading) viewModel.loadHistory()
    }

    val scroll = rememberScrollState()
    var inputFocused by remember { mutableStateOf(false) }

    // 키보드가 올라올 때 화면 전체를 압축하지 않는다. 아래쪽만 가려지게 두고,
    // 입력에 들어가면 위로 붙여 금액과 결과가 함께 보이도록 한다.
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(inputFocused) {
        if (inputFocused) scroll.animateScrollTo(0)
    }
    // 포커스만 보면 뒤로 가기로 키보드를 닫은 뒤에도 연산자가 남는다. 키보드가 실제로 떠 있을 때만.
    val typing = inputFocused && imeBottom > 0.dp

    Box(Modifier.fillMaxSize().ambientGlow()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
                .padding(horizontal = Space.l),
        ) {
            // 입력 중에는 헤더와 아래 도구를 접어 금액과 결과만 남긴다.
            AnimatedVisibility(visible = !typing) {
                Header(
                    hasUpdate = state.hasUpdate,
                    onShare = { showShare = true },
                    onVersionClick = onOpenVersion,
                )
            }
            if (typing) Spacer(Modifier.height(Space.m))

            if (state.snapshot?.source == RateSource.CACHE) {
                Box(Modifier.introRise(60)) { OfflineBanner() }
                Spacer(Modifier.height(Space.m))
            }

            Box(Modifier.introRise(60)) {
                CurrencySegments(selected = state.selected, onSelect = viewModel::select)
            }

            Spacer(Modifier.height(Space.m))

            val resultText = state.result?.let { format(it, state.toDecimals) } ?: "—"
            Box(Modifier.introRise(110)) {
            PriceTag(
                state = state,
                resultText = resultText,
                focusRequester = amountFocus,
                onFocusChange = { inputFocused = it },
                onValueChange = {
                    rollTicks = false
                    viewModel.onInputChange(it)
                },
                ticks = rollTicks,
                saveModifier = Modifier.onGloballyPositioned { saveAnchor = it.boundsInRoot().center },
                onSwap = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.swap()
                },
                onCopy = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    copyToClipboard(context, "$resultText ${state.toCurrencyCode}")
                },
                onSave = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    askMemo = true
                },
                onAdd = {
                    rollTicks = true
                    viewModel.addQuickAmount(it)
                },
                onClear = {
                    rollTicks = false
                    viewModel.clearInput()
                },
            )
            }

            // 입력 중에는 결제 수단 줄도 접는다. 키보드 위 연산자 줄과 겹치면 글자가 비쳐 지저분해진다.
            AnimatedVisibility(visible = !typing, modifier = Modifier.introRise(160)) {
                Column {
                    Spacer(Modifier.height(Space.m))
                    PaymentSegments(state = state, onPick = viewModel::setPriceMode)
                    Spacer(Modifier.height(Space.l))
                    RateRow(state = state, now = now, onRefresh = { viewModel.refresh(manual = true) })
                    Spacer(Modifier.height(Space.m))
                    ToolTiles(
                        state = state,
                        onTrend = {
                            showTrend = true
                            if (state.history.isEmpty()) viewModel.loadHistory()
                        },
                        onRefund = { showRefund = true },
                        onTip = { showTip = true },
                    )
                    if (state.saved.isNotEmpty()) {
                        Spacer(Modifier.height(Space.l))
                        RecentSaved(state = state, onOpen = onOpenSaved)
                    }
                }
            }

            Spacer(Modifier.height(NavClearance))
            Spacer(Modifier.height(imeBottom + if (typing) OPERATOR_BAR_HEIGHT else 0.dp))
        }
        if (typing) {
            OperatorBar(
                modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
                onOperator = {
                    rollTicks = false
                    viewModel.appendOperator(it)
                },
                onSave = { askMemo = true },
            )
        }
    }

    if (showShare) {
        ShareSheet(state = state, onDismiss = { showShare = false })
    }

    if (showTrend) {
        TrendSheet(
            currency = state.selected,
            range = state.historyRange,
            points = state.history,
            loading = state.historyLoading,
            onRangeChange = viewModel::loadHistory,
            onDismiss = { showTrend = false },
        )
    }

    if (showTip) {
        TipSheet(state = state, onDismiss = { showTip = false })
    }

    if (showRefund && state.selected.taxRefund != null) {
        RefundSheet(state = state, onDismiss = { showRefund = false })
    }

    if (showUpdate && state.update != null) {
        UpdateDialog(
            info = state.update,
            onDismiss = { showUpdate = false },
            onSkip = { showUpdate = false; viewModel.dismissUpdate() },
            onDownload = {
                showUpdate = false
                openUrl(context, state.update.downloadUrl ?: state.update.releaseUrl)
            },
        )
    }

    if (askMemo) {
        SaveDialog(
            state = state,
            onDismiss = { askMemo = false },
            onConfirm = { memo ->
                askMemo = false
                val ok = viewModel.saveCurrent(memo)
                if (!ok) {
                    Toast.makeText(context, "금액과 환율을 먼저 확인해 주세요", Toast.LENGTH_SHORT).show()
                } else {
                    // 키보드가 내려가고 탭 바가 다시 올라온 뒤에 날린다. 날릴 곳이 없으면 글로 알린다.
                    scope.launch {
                        delay(320)
                        if (fly?.launch(saveAnchor) != true) {
                            Haptic.confirm(view)
                            Toast.makeText(context, "저장했습니다", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        )
    }
}

// ---------------------------------------------------------------- 헤더

@Composable
private fun Header(
    hasUpdate: Boolean,
    onShare: () -> Unit,
    onVersionClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 런처 아이콘과 같은 마크(입체 C안). 시작 화면의 타일이 이 자리로 날아와 앉는다(SplashGate).
        DisposableEffect(Unit) { onDispose { SplashGate.headerTile = null } }
        Box(
            Modifier.onGloballyPositioned {
                val r = it.boundsInWindow()
                SplashGate.headerTile = android.graphics.RectF(r.left, r.top, r.right, r.bottom)
            },
        ) { WonTile(sizeDp = 40) }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f).introRise(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "여긴얼마?",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.7).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Space.s))
                Box {
                    Text(
                        text = APP_VERSION,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .glass(Radius.badgeShape)
                            .clickable(onClick = onVersionClick)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (hasUpdate) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-3).dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Trend.rising)
                        )
                    }
                }
            }
            Text(
                text = "지금 이 가격, 원화로",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(Modifier.introRise(0)) {
        GlassIconButton(description = "결과 공유", onClick = onShare) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
    }
}

/** 헤더·환율 줄 오른쪽에 두는 44dp 글래스 버튼. */
@Composable
private fun GlassIconButton(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .glass(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------------------------------------------------------- 통화 선택

@Composable
private fun CurrencySegments(selected: Currency, onSelect: (Currency) -> Unit) {
    val view = rememberView()
    SlidingSegments(count = Currency.entries.size, selected = Currency.entries.indexOf(selected)) {
        Currency.entries.forEach { currency ->
            val on = currency == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (!on) Haptic.segment(view)
                        onSelect(currency)
                    }
                    .semantics { contentDescription = currency.koreanName },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 국기는 고르지 않은 칸에서 살짝 흐리게 둔다. 고른 칸이 먼저 눈에 들어오게.
                    Text(
                        text = currency.flag,
                        fontSize = Num.flag,
                        modifier = Modifier.alpha(if (on) 1f else 0.75f),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = currency.code,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 가격표 (주인공)

/**
 * 입력 · 스왑 · 결과 · 빠른 금액을 가격표 하나로 묶는다.
 * 위 칸(입력)과 아래 칸(결과) 사이를 점선과 양옆 홈으로 끊어 가격표처럼 보이게 하고,
 * 빠른 금액은 계산하는 자리 바로 밑 칸에 붙인다.
 */
@Composable
private fun PriceTag(
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
                    .semantics { contentDescription = if (foreignActive) "원화 입력" else "$code 입력" },
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
                        text = "+" + format(value.toDouble(), 0),
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
private fun AmountField(
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
private fun ClearIcon(tint: Color) {
    Canvas(Modifier.size(14.dp)) {
        val w = 1.8.dp.toPx()
        drawLine(tint, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
        drawLine(tint, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- 결제 수단

/** 결제 수단 버튼 안에 그 수단으로 낼 원화를 같이 보여준다. 하나씩 눌러 보지 않아도 비교된다. */
@Composable
private fun PaymentSegments(state: UiState, onPick: (PriceMode) -> Unit) {
    val view = rememberView()
    SlidingSegments(count = PriceMode.entries.size, selected = PriceMode.entries.indexOf(state.priceMode)) {
        PriceMode.entries.forEach { mode ->
            val on = mode == state.priceMode
            val fee = mode.feePercent(state.selected)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (!on) Haptic.segment(view)
                        onPick(mode)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (fee > 0) "${mode.shortLabel} +${format(fee, 2)}%" else mode.shortLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
                state.krwFor(mode)?.let { won ->
                    Text(
                        text = format(won, 0),
                        style = Num.amount(16).copy(letterSpacing = 0.sp),
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 환율 줄

@Composable
private fun RateRow(state: UiState, now: Long, onRefresh: () -> Unit) {
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
private fun ChangePill(change: Change, unit: Int) {
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

// ---------------------------------------------------------------- 도구

/** 도구 세 칸. 들어가 보기 전에 답(추이·환급액·팁 관행)이 먼저 보이게 값을 함께 적는다. */
@Composable
private fun ToolTiles(
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
private fun ToolTile(
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.height(26.dp), contentAlignment = Alignment.CenterStart) { visual() }
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
private fun TrendLineIcon(tint: Color) {
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
private fun RecentSaved(state: UiState, onOpen: () -> Unit) {
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
                        .fillMaxWidth()
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

// ---------------------------------------------------------------- 연산자 줄

private val OPERATOR_CHIPS = listOf('+' to "+", '-' to "−", '*' to "×", '/' to "÷")

/** 연산자 줄 전체 높이(키 44 + 안쪽 여백 + 바깥 여백). 스크롤 끝 여백 계산에 쓴다. */
private val OPERATOR_BAR_HEIGHT = 68.dp

/**
 * 시스템 숫자 키보드에는 연산자가 없다. 키보드가 떠 있는 동안 그 바로 위에 붙는 글래스 연산자 줄.
 * 끝에 저장을 두어 계산을 마치면 키보드를 닫지 않고 바로 저장한다.
 */
@Composable
private fun OperatorBar(modifier: Modifier, onOperator: (Char) -> Unit, onSave: () -> Unit) {
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
                .clickable(onClick = onSave),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TagIcon(Color.White, sizeDp = 15)
            Spacer(Modifier.width(5.dp))
            Text("저장", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

/**
 * 연산자 키. 글꼴의 + − × ÷ 는 글리프마다 크기·높이가 달라(− 가 유독 짧고 낮다) 줄이 들쭉날쭉해진다.
 * 같은 굵기의 선으로 직접 그려 네 기호의 크기와 중심을 맞춘다.
 */
@Composable
private fun OperatorKey(op: Char, label: String, modifier: Modifier, onClick: () -> Unit) {
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

private val OPERATOR_NAMES = mapOf('+' to "더하기", '-' to "빼기", '*' to "곱하기", '/' to "나누기")

// ---------------------------------------------------------------- 오프라인 / 업데이트

/** 오프라인 상태는 아래쪽 카드에만 두면 놓치기 쉬워서 맨 위에도 알린다. */
@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.pillShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(MaterialTheme.colorScheme.error, size = 7)
        Spacer(Modifier.width(Space.s))
        Text(
            text = "오프라인 · 마지막으로 받은 환율로 계산 중",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 버전 v${info.latestVersion}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "지금 쓰는 버전은 $APP_VERSION 입니다. " +
                        "GitHub 릴리스에서 새 버전을 받을 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.m))
                Text(
                    text = "Play 스토어를 거치지 않는 파일이라 설치할 때 " +
                        "\"출처를 알 수 없는 앱\" 허용이 필요합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        },
        confirmButton = { Button(onClick = onDownload) { Text("다운로드") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("이 버전 넘기기") } },
    )
}

// ---------------------------------------------------------------- 저장 다이얼로그

@Composable
private fun SaveDialog(state: UiState, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var memo by remember { mutableStateOf("") }
    val price = state.foreignAmount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 금액 저장", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "${format(price, 2)} ${state.selected.code}" +
                        " · ${format(price * (state.baseRate ?: 0.0), 0)}원",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.l))
                OutlinedTextField(
                    value = memo,
                    onValueChange = { if (it.length <= 30) memo = it },
                    singleLine = true,
                    label = { Text("항목 이름") },
                    placeholder = { Text("예: 가죽 가방") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(memo) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

// ---------------------------------------------------------------- 공유 / 복사

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("환율 결과", text))
    // Android 13부터는 시스템이 복사 알림을 직접 띄우므로 토스트가 중복된다.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "복사했습니다", Toast.LENGTH_SHORT).show()
    }
}
