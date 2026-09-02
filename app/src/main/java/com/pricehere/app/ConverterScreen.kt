package com.pricehere.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun ConverterScreen(viewModel: RatesViewModel, state: UiState) {
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    var askMemo by remember { mutableStateOf(false) }

    // 새로고침 결과 배너는 잠깐만 보여준다.
    LaunchedEffect(state.outcome) {
        if (state.outcome != null) {
            delay(4_000)
            viewModel.clearOutcome()
        }
    }

    // 신선도 문구를 30초마다 다시 계산한다.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
            .padding(horizontal = 20.dp),
    ) {
        Header(
            loading = state.loading,
            onRefresh = { viewModel.refresh(manual = true) },
            onShare = { shareResult(context, state) },
        )

        CurrencySegments(selected = state.selected, onSelect = viewModel::select)

        Spacer(Modifier.height(22.dp))

        AmountCard(
            flag = state.fromFlag,
            label = state.fromLabel,
            code = state.fromCurrencyCode,
            value = state.input,
            onValueChange = viewModel::onInputChange,
        )

        Spacer(Modifier.height(10.dp))
        QuickAmounts(state.quickAmounts, viewModel::setQuickAmount)

        SwapRow(onSwap = viewModel::swap)

        val resultText = state.result?.let { format(it, state.toDecimals) } ?: "—"
        ResultCard(
            flag = state.toFlag,
            label = state.toLabel,
            code = state.toCurrencyCode,
            text = resultText,
            onCopy = { copyToClipboard(context, "$resultText ${state.toCurrencyCode}") },
            onSave = { askMemo = true },
        )

        Spacer(Modifier.height(14.dp))
        PriceModeRow(state.priceMode, viewModel::setPriceMode)

        Spacer(Modifier.height(14.dp))
        RateInfoCard(state = state, now = now)

        if (state.selected.taxRefund != null) {
            Spacer(Modifier.height(12.dp))
            TaxRefundPanel(state = state, onToggle = viewModel::toggleRefund)
        }

        Spacer(Modifier.height(24.dp))
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
private fun Header(loading: Boolean, onRefresh: () -> Unit, onShare: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "여긴얼마",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = APP_VERSION,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )

        Spacer(Modifier.weight(1f))

        RoundAction(onClick = onShare) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "결과 공유",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        RoundAction(onClick = onRefresh, enabled = !loading) {
            if (loading) {
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

@Composable
private fun RoundAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
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
            .clip(RoundedCornerShape(16.dp))
            .background(track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Currency.entries.forEach { currency ->
            val on = currency == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (on && !dark) Modifier.shadow(2.dp, RoundedCornerShape(12.dp))
                        else Modifier
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) activeBg else Color.Transparent)
                    .clickable { onSelect(currency) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currency.flag, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = currency.code,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
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

// ---------------------------------------------------------------- 금액

@Composable
private fun AmountCard(
    flag: String,
    label: String,
    code: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(24.dp), clip = false)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        CardHeader(flag, label, code, MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = amountStyle(MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            visualTransformation = ThousandsTransformation,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                if (value.isEmpty()) {
                    Text(
                        text = "0",
                        style = amountStyle(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        ),
                    )
                }
                field()
            },
        )
    }
}

@Composable
private fun QuickAmounts(values: List<Long>, onPick: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        values.forEach { value ->
            Pill(text = format(value.toDouble(), 0), selected = false) { onPick(value) }
        }
    }
}

@Composable
private fun SwapRow(onSwap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onSwap),
            contentAlignment = Alignment.Center,
        ) {
            SwapIcon(MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun ResultCard(
    flag: String,
    label: String,
    code: String,
    text: String,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    val dark = isDark()
    val brush = Brush.verticalGradient(
        if (dark) listOf(Hero.darkTop, Hero.darkBottom) else listOf(Hero.lightTop, Hero.lightBottom)
    )
    val ink = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .clickable(onClick = onCopy)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        CardHeader(flag, label, code, ink.copy(alpha = 0.68f))
        Spacer(Modifier.height(10.dp))
        Text(
            text = text,
            style = amountStyle(ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "탭하면 복사됩니다",
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.5f),
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ink.copy(alpha = 0.10f))
                    .clickable(onClick = onSave)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "＋ 저장",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ink.copy(alpha = 0.85f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 결제 수단

@Composable
private fun PriceModeRow(selected: PriceMode, onSelect: (PriceMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PriceMode.entries.forEach { mode ->
            Pill(
                text = mode.label,
                selected = mode == selected,
                modifier = Modifier.weight(1f),
            ) { onSelect(mode) }
        }
    }
}

// ---------------------------------------------------------------- 환율 정보

@Composable
private fun RateInfoCard(state: UiState, now: Long) {
    val snapshot = state.snapshot
    val rate = state.rate
    val change = state.change

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (rate == null) {
                    "환율을 불러오는 중입니다"
                } else {
                    "1 ${state.selected.code} = ${formatFixed(rate, 2)}원"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (change != null && change.direction != 0 && state.priceMode == PriceMode.BASE) {
                Spacer(Modifier.weight(1f))
                TrendBadge(change)
            }
        }

        if (state.priceMode != PriceMode.BASE) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = "매매기준율 ${formatFixed(state.baseRate ?: 0.0, 2)}원에 " +
                    "${state.priceMode.label} 수수료 ${formatFixed(state.feePercent, 2)}%를 더한 추정치",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (snapshot != null) {
            val minutes = ((now - snapshot.quotedAtMillis) / 60_000L).coerceAtLeast(0)
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(dotColor(snapshot.source, minutes))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${snapshot.source.label} · " +
                        "${CLOCK.format(Instant.ofEpochMilli(snapshot.quotedAtMillis))} 고시 " +
                        "(${elapsed(minutes)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.outcome?.let { outcome ->
            Spacer(Modifier.height(10.dp))
            RefreshBanner(outcome)
        }

        if (state.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(13.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(13.dp))
        Text(
            text = when (state.priceMode) {
                PriceMode.BASE ->
                    "매매기준율입니다. 실제 환전 시에는 은행 수수료가 더해집니다."
                PriceMode.CARD ->
                    "국제브랜드와 국내 카드사 수수료를 합친 대표값 추정입니다. 카드사마다 다릅니다."
                PriceMode.CASH ->
                    "은행 현찰 스프레드 대표값 추정입니다. 은행과 통화에 따라 편차가 큽니다."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

/** 새로고침이 실제로 무엇을 했는지 알려준다. 값이 그대로여도 "동작했다"는 사실은 보여야 한다. */
@Composable
private fun RefreshBanner(outcome: RefreshOutcome) {
    val text = when {
        outcome.throttled ->
            "방금 확인했습니다. 하나은행 고시는 보통 1~2분마다 갱신됩니다."
        outcome.changed ->
            "환율이 갱신되었습니다 · " +
                "${formatFixed(outcome.before ?: 0.0, 2)} → ${formatFixed(outcome.after ?: 0.0, 2)}원"
        outcome.after != null ->
            "최신 상태입니다 · 직전 조회 이후 변동 없음"
        else -> return
    }
    val accent = if (outcome.changed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (outcome.changed) "↻" else "✓",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
        )
    }
}

@Composable
private fun TrendBadge(change: Change) {
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
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = if (dark) 0.16f else 0.10f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = if (rising) "▲" else "▼", fontSize = 9.sp, color = tint)
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${formatFixed(change.amount, 2)} (${formatFixed(change.ratio, 2)}%)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

// ---------------------------------------------------------------- 세금 환급

@Composable
private fun TaxRefundPanel(state: UiState, onToggle: () -> Unit) {
    val refund = state.selected.taxRefund ?: return
    val price = state.foreignAmount
    val base = state.baseRate ?: 0.0

    SectionCard(background = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(refund.countryFlag, fontSize = 15.sp)
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    text = "세금 환급 예상",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${refund.countryName} · 부가세 ${format(refund.vatPercent, 0)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Chevron(MaterialTheme.colorScheme.onSurfaceVariant, state.refundOpen)
        }

        if (!state.refundOpen) return@SectionCard

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(6.dp))

        if (price <= 0.0 || base <= 0.0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "금액을 입력하면 환급 예상액을 계산합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        val code = state.selected.code
        val vatPart = refund.vatIncludedIn(price)
        val net = refund.estimatedRefund(price)
        val after = price - net

        DetailRow(
            label = "구매 금액",
            value = "${format(price, 2)} $code",
            sub = "${format(price * base, 0)}원",
        )
        DetailRow(
            label = "정가에 포함된 부가세",
            value = "${format(vatPart, 2)} $code",
            sub = "${format(vatPart * base, 0)}원",
        )
        DetailRow(
            label = "실수령 추정 (대행 수수료 25% 차감)",
            value = "${format(net, 2)} $code",
            sub = "${format(net * base, 0)}원",
            emphasize = true,
            valueColor = MaterialTheme.colorScheme.primary,
        )
        DetailRow(
            label = "환급 후 실부담",
            value = "${format(after, 2)} $code",
            sub = "${format(after * base, 0)}원",
        )

        if (refund.minimumPurchase > 0 && price < refund.minimumPurchase) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "한 매장에서 하루 ${format(refund.minimumPurchase, 0)} $code 이상" +
                        " 사야 환급을 신청할 수 있습니다. 현재 ${format(price, 0)} $code.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Spacer(Modifier.height(11.dp))
        Text(
            text = "환급액은 대행사·수령 방식(현금/카드)에 따라 달라집니다. " +
                "실수령률은 대행 수수료를 약 25%로 잡은 추정치이며, 공항에서 세관 확인을 받아야 합니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
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
                Spacer(Modifier.height(14.dp))
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

private fun shareResult(context: Context, state: UiState) {
    val rate = state.rate
    val result = state.result
    val body = if (rate == null || result == null) {
        "여긴얼마 $APP_VERSION — 아직 환율을 불러오지 못했습니다."
    } else {
        buildString {
            appendLine(
                "${format(state.amount, state.fromDecimals)} ${state.fromCurrencyCode}" +
                    " = ${format(result, state.toDecimals)} ${state.toCurrencyCode}"
            )
            appendLine()
            appendLine("적용 환율  1 ${state.selected.code} = ${formatFixed(rate, 2)}원")
            if (state.priceMode != PriceMode.BASE) {
                appendLine("기준       ${state.priceMode.label} 수수료 ${formatFixed(state.feePercent, 2)}% 반영")
            }
            state.snapshot?.let {
                appendLine("고시       ${it.source.label} · ${STAMP.format(Instant.ofEpochMilli(it.quotedAtMillis))}")
            }
            appendLine()
            append("여긴얼마 · PriceHere $APP_VERSION")
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(send, "환율 결과 공유"))
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("환율 결과", text))
    // Android 13부터는 시스템이 복사 알림을 직접 띄우므로 토스트가 중복된다.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "복사했습니다", Toast.LENGTH_SHORT).show()
    }
}
