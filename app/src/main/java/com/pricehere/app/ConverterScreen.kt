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
    var showDcc by remember { mutableStateOf(false) }

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
                    // 카드 결제일 때만 한 줄. 원화 결제(DCC)를 고르면 손해라는 것만 짧게 알리고, 누르면 자세히.
                    AnimatedVisibility(visible = state.priceMode == PriceMode.CARD) {
                        DccHint(state = state, onOpen = { showDcc = true })
                    }
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
                // 입력하자마자 보내는 경우가 많아 키보드 위 버튼은 공유로 둔다. 저장은 가격표 안에 그대로 있다.
                onShare = {
                    focus.clearFocus()
                    showShare = true
                },
            )
        }
    }

    if (showDcc) {
        DccSheet(onDismiss = { showDcc = false })
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

// ---------------------------------------------------------------- 공유 / 복사

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

internal fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("환율 결과", text))
    // Android 13부터는 시스템이 복사 알림을 직접 띄우므로 토스트가 중복된다.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "복사했습니다", Toast.LENGTH_SHORT).show()
    }
}
