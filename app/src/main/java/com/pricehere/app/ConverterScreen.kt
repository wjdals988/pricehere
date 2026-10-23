package com.pricehere.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
) {
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val amountFocus = remember { FocusRequester() }
    var askMemo by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showTrend by remember { mutableStateOf(false) }
    var showTip by remember { mutableStateOf(false) }
    var showRefund by remember { mutableStateOf(false) }

    // 신선도 문구를 30초마다 다시 계산한다.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val scroll = rememberScrollState()
    var inputFocused by remember { mutableStateOf(false) }

    // 키보드가 올라올 때 화면 전체를 압축하지 않는다. 아래쪽만 가려지게 두고,
    // 입력에 들어가면 위로 붙여 금액과 결과가 함께 보이도록 한다.
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(inputFocused) {
        if (inputFocused) scroll.animateScrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
            .padding(horizontal = Space.gutter),
    ) {
        Header(
            hasUpdate = state.hasUpdate,
            onShare = { shareText(context, buildConversionShareText(state), "환산 결과 공유") },
            onVersionClick = onOpenVersion,
        )

        if (state.snapshot?.source == RateSource.CACHE) {
            OfflineBanner()
            Spacer(Modifier.height(Space.m))
        }

        CurrencySegments(selected = state.selected, onSelect = viewModel::select)

        Spacer(Modifier.height(Space.xl))

        val resultText = state.result?.let { format(it, state.toDecimals) } ?: "—"
        ConvertCard(
            state = state,
            resultText = resultText,
            focusRequester = amountFocus,
            onFocusChange = { inputFocused = it },
            onValueChange = viewModel::onInputChange,
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
        )

        Spacer(Modifier.height(Space.m))
        QuickAmounts(
            values = state.quickAmounts,
            // 포커스만 보면 뒤로 가기로 키보드를 닫은 뒤에도 연산자가 남는다. 키보드가 실제로 떠 있을 때만.
            operators = inputFocused && imeBottom > 0.dp,
            canClear = state.input.isNotEmpty(),
            onAdd = viewModel::addQuickAmount,
            onOperator = viewModel::appendOperator,
            onClear = viewModel::clearInput,
        )

        Spacer(Modifier.height(Space.l))
        RateCard(
            state = state,
            now = now,
            onRefresh = { viewModel.refresh(manual = true) },
            onPriceMode = viewModel::setPriceMode,
        )

        Spacer(Modifier.height(Space.m))
        ToolRow(
            refund = state.selected.taxRefund,
            onTrend = {
                showTrend = true
                if (state.history.isEmpty()) viewModel.loadHistory()
            },
            onTip = { showTip = true },
            onRefund = { showRefund = true },
        )

        Spacer(Modifier.height(Space.xl))
        Spacer(Modifier.height(imeBottom))
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
                Toast.makeText(
                    context,
                    if (ok) "저장했습니다" else "금액과 환율을 먼저 확인해 주세요",
                    Toast.LENGTH_SHORT,
                ).show()
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
    val dark = isDark()
    val brand = Brush.linearGradient(
        if (dark) {
            listOf(Color(0xFF7BE6D0), Color(0xFF17A08C))
        } else {
            listOf(Color(0xFF17A08C), Color(0xFF05433A))
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth().height(74.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 아이콘과 같은 마크를 헤더에도 둔다. 앱을 열 때마다 브랜드가 먼저 눈에 들어온다.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(Radius.pillShape)
                .background(brand),
            contentAlignment = Alignment.Center,
        ) {
            WonMark(Color.White, sizeDp = 21)
        }

        Spacer(Modifier.width(Space.m))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "여긴얼마?",
                    style = TextStyle(
                        brush = brand,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.9).sp,
                    ),
                )
                Spacer(Modifier.width(Space.s))
                Box {
                    Text(
                        text = APP_VERSION,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(Radius.badgeShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = onVersionClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                letterSpacing = 0.1.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        RoundAction(onClick = onShare) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "결과 공유",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- 통화 선택

@Composable
private fun CurrencySegments(selected: Currency, onSelect: (Currency) -> Unit) {
    // 선택된 칸은 항상 트랙보다 "떠 보여야" 한다. 다크에서는 밝은 쪽이 위로 온다.
    val dark = isDark()
    val track = if (dark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val activeBg = if (dark) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.cardShape)
            .background(track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Currency.entries.forEach { currency ->
            val on = currency == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(Radius.pillShape)
                    .background(if (on) activeBg else Color.Transparent)
                    .clickable { onSelect(currency) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currency.flag, fontSize = Num.flag)
                Spacer(Modifier.width(Space.s))
                Text(
                    text = currency.code,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 환산 (주인공)

/**
 * 입력 · 스왑 · 결과를 한 카드로 묶는다.
 * 이전에는 카드 세 개가 따로 떠 있어서 화면의 주인공이 없었다.
 * 그림자는 이 카드에만 준다 — 나머지 깊이는 배경 명도 차로 만든다.
 */
@Composable
private fun ConvertCard(
    state: UiState,
    resultText: String,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onSwap: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.cardShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // 입력
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { focusRequester.requestFocus() }
                .padding(horizontal = Space.gutter, vertical = Space.l),
        ) {
            // 식이면 오른쪽 통화 코드 자리에 계산값을 보여준다. 줄이 새로 생기지 않아 카드가 흔들리지 않는다.
            val headerRight = if (state.isExpression) {
                "= " + (state.amountOrNull?.let { format(it, state.fromDecimals) } ?: "—") +
                    " " + state.fromCurrencyCode
            } else {
                state.fromCurrencyCode
            }
            CardHeader(state.fromFlag, state.fromLabel, headerRight, muted)
            Spacer(Modifier.height(Space.s))
            // 빠른 금액·연산자 칩으로 값이 바뀌면 커서를 끝으로 보낸다.
            // 문자열만 넘기면 커서가 옛 위치에 남아 다음 입력이 중간에 끼어든다.
            var field by remember { mutableStateOf(TextFieldValue(state.input)) }
            if (field.text != state.input) {
                field = TextFieldValue(state.input, TextRange(state.input.length))
            }
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    onValueChange(it.text)
                },
                singleLine = true,
                textStyle = Num.amount(34).copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = ExpressionTransformation,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                decorationBox = { field ->
                    if (state.input.isEmpty()) {
                        Text("0", style = Num.amount(34).copy(color = muted.copy(alpha = 0.3f)))
                    }
                    field()
                },
            )
        }

        // 스왑 — 구분선이 버튼까지 이어져 두 영역의 경계 역할을 한다.
        // 높이를 44dp로 확보해야 버튼이 측정 제약에 눌리지 않는다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.width(Space.l))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(Elev.floating, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onSwap),
                contentAlignment = Alignment.Center,
            ) {
                SwapIcon(MaterialTheme.colorScheme.onPrimary, sizeDp = 17)
            }
        }

        // 결과
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onCopy)
                .padding(horizontal = Space.gutter, vertical = Space.l),
        ) {
            val ink = MaterialTheme.colorScheme.onPrimaryContainer
            // 원화 결과는 코드(KRW) 대신 "약 3만 7천원"으로 감을 준다. 만 원 미만은 코드 그대로.
            val toRight = state.result
                ?.takeIf { state.toCurrencyCode == "KRW" }
                ?.let(::approxWon)
                ?: state.toCurrencyCode
            CardHeader(state.toFlag, state.toLabel, toRight, ink.copy(alpha = 0.66f))
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = resultText,
                    style = Num.amount(34).copy(color = ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 가중치를 숫자 쪽에만 준다. Spacer에도 weight를 주면 남은 폭이
                    // 반씩 나뉘어 저장 버튼이 자릿수에 따라 좌우로 흔들린다.
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.m))
                Box(
                    modifier = Modifier
                        .clip(Radius.pillShape)
                        .background(ink.copy(alpha = 0.1f))
                        .clickable(onClick = onSave)
                        .padding(horizontal = Space.m, vertical = Space.s),
                ) {
                    Text(
                        text = "＋ 저장",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ink.copy(alpha = 0.88f),
                    )
                }
            }
            if (state.showHints) {
                Spacer(Modifier.height(Space.s))
                Text(
                    text = "숫자를 탭하면 복사됩니다",
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun QuickAmounts(
    values: List<Long>,
    operators: Boolean,
    canClear: Boolean,
    onAdd: (Long) -> Unit,
    onOperator: (Char) -> Unit,
    onClear: () -> Unit,
) {
    // 초기화는 스크롤 영역 밖 오른쪽에 고정한다. 칩 줄 끝에 붙이면 통화마다
    // 칩 개수·폭이 달라 위치가 바뀌고, 화면 밖으로 밀려나기도 한다.
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val chips = rememberScrollState()
        Row(
            modifier = Modifier
                .weight(1f)
                .edgeFade(chips)
                .horizontalScroll(chips),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (operators) {
                // 시스템 숫자 키보드에는 연산자가 없다. 입력 중에는 이 줄이 연산자 키가 된다.
                OPERATOR_CHIPS.forEach { (op, label) ->
                    // 기호는 글자보다 작아 보여서 한 단계 크고 굵게 둔다. 칩 높이는 빠른 금액과 같다.
                    Pill(
                        text = label,
                        selected = false,
                        modifier = Modifier.width(52.dp),
                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 17.sp),
                        weight = FontWeight.SemiBold,
                    ) { onOperator(op) }
                }
            } else {
                values.forEach { value ->
                    // "+"를 붙여 누를 때마다 더해진다는 것을 보여준다.
                    Pill(text = "+" + format(value.toDouble(), 0), selected = false) { onAdd(value) }
                }
            }
        }
        Spacer(Modifier.width(Space.s))
        Text(
            text = "초기화",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (canClear) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier
                .clip(Radius.pillShape)
                .clickable(enabled = canClear, onClick = onClear)
                .padding(horizontal = Space.m, vertical = Space.s),
        )
    }
}

private val OPERATOR_CHIPS = listOf('+' to "+", '-' to "−", '*' to "×", '/' to "÷")

/**
 * 가로 스크롤 줄의 잘린 쪽 끝을 흐리게 한다. 칩이 반쯤 잘려 보이는 대신
 * "옆으로 더 있다"는 신호가 된다. 끝까지 밀면 그쪽 페이드는 사라진다.
 */
private fun Modifier.edgeFade(state: ScrollState, width: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx()
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                topLeft = Offset(size.width - w, 0f),
                size = Size(w, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
                size = Size(w, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

// ---------------------------------------------------------------- 환율 (조연)

/**
 * 환율·등락·출처·결제 수단·고지를 한 카드에 담는다.
 * 수수료 pill이 카드 밖에 떠 있으면 무엇을 조작하는 컨트롤인지 알 수 없다.
 */
@Composable
private fun RateCard(
    state: UiState,
    now: Long,
    onRefresh: () -> Unit,
    onPriceMode: (PriceMode) -> Unit,
) {
    val snapshot = state.snapshot
    val change = state.change

    SectionCard(background = MaterialTheme.colorScheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.rateLabel ?: "환율 없음",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (change != null && change.direction != 0 && state.priceMode == PriceMode.BASE) {
                Spacer(Modifier.weight(1f))
                TrendBadge(change, state.selected.quoteUnit)
            }
        }

        if (snapshot == null) {
            // 처음 설치한 뒤 오프라인이면 캐시도 없다. 출처 줄이 없으니 다시 시도할 길을 여기서 연다.
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.loading) {
                        "환율을 받아오고 있습니다"
                    } else {
                        state.error ?: "환율을 받아오지 못했습니다. 네트워크를 확인해 주세요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.loading) MaterialTheme.colorScheme.onSurfaceVariant else Freshness.stale,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.s))
                StatusRefreshButton(
                    color = Freshness.stale,
                    loading = state.loading,
                    confirmed = false,
                    description = "환율 다시 받기",
                    onClick = onRefresh,
                )
            }
        }

        if (snapshot != null) {
            val minutes = ((now - snapshot.quotedAtMillis) / 60_000L).coerceAtLeast(0)
            // 등락 배지와 새로고침 버튼이 오른쪽 끝에 위아래로 붙지 않도록 한 칸 띄운다.
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

        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PriceMode.entries.forEach { mode ->
                Pill(
                    text = mode.label,
                    selected = mode == state.priceMode,
                    modifier = Modifier.weight(1f),
                ) { onPriceMode(mode) }
            }
        }

        Spacer(Modifier.height(Space.m))
        Text(
            text = when (state.priceMode) {
                PriceMode.BASE ->
                    "매매기준율입니다. 실제 환전 시에는 은행 수수료가 더해집니다."
                PriceMode.CARD ->
                    "매매기준율 ${formatFixed((state.baseRate ?: 0.0) * state.selected.quoteUnit, 2)}원에 " +
                        "국제브랜드와 국내 카드사 수수료 ${formatFixed(state.feePercent, 2)}%를 더한 추정입니다."
                PriceMode.CASH ->
                    "매매기준율 ${formatFixed((state.baseRate ?: 0.0) * state.selected.quoteUnit, 2)}원에 " +
                        "은행 현찰 스프레드 ${formatFixed(state.feePercent, 2)}%를 더한 추정입니다."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        )
    }
}

// ---------------------------------------------------------------- 더 보기

/** 자주 쓰지 않는 것은 화면 맨 아래 한 줄로 모아 시트로 연다. */
@Composable
private fun ToolRow(
    refund: TaxRefund?,
    onTrend: () -> Unit,
    onTip: () -> Unit,
    onRefund: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        ToolChip("📈", "환율 추이", onTrend, Modifier.weight(1f))
        ToolChip("💵", "팁 계산", onTip, Modifier.weight(1f))
        if (refund != null) {
            ToolChip(refund.countryFlag, "세금 환급", onRefund, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrendBadge(change: Change, unit: Int) {
    val rising = change.direction > 0
    val dark = isDark()
    val tint = when {
        rising && dark -> Trend.risingDark
        rising -> Trend.rising
        dark -> Trend.fallingDark
        else -> Trend.falling
    }
    Row(
        modifier = Modifier
            .clip(Radius.badgeShape)
            .background(tint.copy(alpha = if (dark) 0.16f else 0.10f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = if (rising) "▲" else "▼", fontSize = 9.sp, color = tint)
        Spacer(Modifier.width(Space.xs))
        Text(
            text = "${formatFixed(change.amount * unit, 2)} (${formatFixed(change.ratio, 2)}%)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

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
            text = "오프라인입니다. 마지막으로 받아둔 환율로 계산하고 있습니다.",
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
                    label = { Text("무엇인가요?") },
                    placeholder = { Text("예: 가죽가방") },
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
