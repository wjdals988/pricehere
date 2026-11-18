package com.pricehere.app

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

private val AXIS_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M.d")
private val PICK_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")

// ---------------------------------------------------------------- 환율 추이

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendSheet(
    currency: Currency,
    range: HistoryRange,
    points: List<RatePoint>,
    loading: Boolean,
    onRangeChange: (HistoryRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "${currency.code} 환율 추이",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "${currency.quoteUnit} ${currency.code} 기준 원화",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.l))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryRange.entries.forEach { r ->
                    Pill(
                        text = r.label,
                        selected = r == range,
                        modifier = Modifier.weight(1f),
                    ) { onRangeChange(r) }
                }
            }

            Spacer(Modifier.height(Space.m))

            // 기간을 바꿀 때는 이미 그려진 차트가 남아 있어서, 뭔가 하고 있다는 걸
            // 따로 알려주지 않으면 눌린 건지 알 수 없다.
            Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                if (loading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(Radius.badgeShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            text = "추이를 불러오는 중",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.xs))

            when {
                loading && points.isEmpty() -> SheetPlaceholder(loading = true)
                points.size < 2 -> SheetPlaceholder(loading = false)
                else -> {
                    val unit = currency.quoteUnit
                    val summary = points.summarize()
                    var scrub by remember(points) { mutableStateOf<Int?>(null) }
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.alpha(if (loading) 0.3f else 1f)) {
                            LineChart(
                                points = points,
                                unit = unit,
                                accent = MaterialTheme.colorScheme.primary,
                                grid = MaterialTheme.colorScheme.outlineVariant,
                                selected = scrub,
                                onScrub = { scrub = it },
                            )
                        }
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.m))
                    val picked = scrub?.let { points.getOrNull(it) }
                    if (picked != null) {
                        // 훑는 동안에는 양 끝 날짜 대신 손가락이 가리키는 날의 값을 보여준다.
                        Text(
                            text = "${PICK_DATE.format(picked.date)} · ${formatFixed(picked.rate * unit, 2)}원",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = Num.FEATURES),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = AXIS_DATE.format(points.first().date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = AXIS_DATE.format(points.last().date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(Space.l))
                    Column(modifier = Modifier.alpha(if (loading) 0.4f else 1f)) {
                        DetailRow("최저", "${formatFixed(summary.low * unit, 2)}원")
                        DetailRow("최고", "${formatFixed(summary.high * unit, 2)}원")
                        DetailRow("평균", "${formatFixed(summary.average * unit, 2)}원")
                        DetailRow(
                            label = "${range.label} 변동",
                            value = "${if (summary.changePercent >= 0) "▲" else "▼"} " +
                                "${formatFixed(kotlin.math.abs(summary.changePercent), 2)}%",
                            emphasize = true,
                            valueColor = trendColor(summary.changePercent >= 0),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.l))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Space.m))
            Text(
                text = "추이는 유럽중앙은행(ECB) 기준환율이라 홈 화면의 하나은행 매매기준율과 " +
                    "값이 조금 다릅니다. ECB는 영업일만 고시하므로 주말과 공휴일은 점이 없습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun trendColor(rising: Boolean) = when {
    rising && isDark() -> Trend.risingDark
    rising -> Trend.rising
    isDark() -> Trend.fallingDark
    else -> Trend.falling
}

@Composable
private fun SheetPlaceholder(loading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(Radius.cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "추이를 불러오지 못했습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 차트 라이브러리를 붙이지 않고 직접 그린다. 점이 60개 남짓이라 충분하다. */
@Composable
private fun LineChart(
    points: List<RatePoint>,
    unit: Int,
    accent: androidx.compose.ui.graphics.Color,
    grid: androidx.compose.ui.graphics.Color,
    selected: Int?,
    onScrub: (Int?) -> Unit,
) {
    val values = points.map { it.rate * unit }
    val low = values.min()
    val high = values.max()
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0
    val view = rememberView()
    val surface = MaterialTheme.colorScheme.surface

    // 선이 왼쪽에서 오른쪽으로 그려지며 등장한다. 기간을 바꿔도 다시 그린다.
    val draw = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { draw.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(Radius.cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            // 손가락으로 훑으면 가장 가까운 날을 고르고, 날이 바뀔 때마다 약한 틱.
            .pointerInput(points) {
                var last = -1
                fun pick(x: Float) {
                    val i = ((x / size.width).coerceIn(0f, 1f) * (values.size - 1)).roundToInt()
                    if (i != last) {
                        last = i
                        Haptic.tick(view)
                        onScrub(i)
                    }
                }
                awaitEachGesture {
                    last = -1
                    val down = awaitFirstDown()
                    pick(down.position.x)
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        pick(change.position.x)
                        change.consume()
                    }
                    onScrub(null)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        fun x(i: Int) = if (values.size == 1) w / 2f else w * i / (values.size - 1).toFloat()
        fun y(v: Double) = (h - ((v - low) / span) * h).toFloat()

        // 최고·최저 기준선
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
        drawLine(grid, Offset(0f, y(high)), Offset(w, y(high)), 1.5f, pathEffect = dash)
        drawLine(grid, Offset(0f, y(low)), Offset(w, y(low)), 1.5f, pathEffect = dash)

        val line = Path().apply {
            values.forEachIndexed { i, v ->
                if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v))
            }
        }

        // 선 아래를 옅게 채워 추세가 눈에 들어오게 한다.
        val fill = Path().apply {
            addPath(line)
            lineTo(x(values.lastIndex), h)
            lineTo(x(0), h)
            close()
        }
        val p = draw.value
        clipRect(right = w * p) {
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.02f))
                ),
            )
        }
        val measure = PathMeasure().apply { setPath(line, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * p, partial, true)
        drawPath(
            partial,
            color = accent,
            style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (p >= 1f) {
            drawCircle(accent, radius = 4.5f, center = Offset(x(values.lastIndex), y(values.last())))
        }
        selected?.let { i ->
            val c = Offset(x(i), y(values[i]))
            drawLine(accent.copy(alpha = 0.5f), Offset(c.x, 0f), Offset(c.x, h), 1.5.dp.toPx())
            drawCircle(surface, radius = 7.dp.toPx(), center = c)
            drawCircle(accent, radius = 4.5.dp.toPx(), center = c)
        }
    }
}

// ---------------------------------------------------------------- 팁 계산

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipSheet(state: UiState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currency = state.selected
    val tip = currency.tip

    var percent by remember { mutableIntStateOf(tip.suggested.firstOrNull() ?: 10) }
    var people by remember { mutableIntStateOf(1) }

    val bill = state.foreignAmount
    val tipAmount = bill * percent / 100.0
    val total = bill + tipAmount
    val perPerson = if (people <= 0) total else total / people
    val base = state.baseRate ?: 0.0
    val decimals = currency.decimals

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "${currency.koreanName} 팁 계산",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "계산서 ${format(bill, decimals)} ${currency.code}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (bill <= 0.0 || base <= 0.0) {
                Spacer(Modifier.height(Space.l))
                Text(
                    text = "홈 화면에서 금액을 먼저 입력해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.m))
                TipNote(tip.note)
                return@Column
            }

            Spacer(Modifier.height(Space.l))
            Text(
                text = "팁 비율",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tip.suggested.forEach { p ->
                    Pill(text = "$p%", selected = p == percent, modifier = Modifier.weight(1f)) {
                        percent = p
                    }
                }
            }

            Spacer(Modifier.height(Space.l))
            Text(
                text = "나눠 낼 인원",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 4, 5, 6).forEach { n ->
                    Pill(text = "$n", selected = n == people, modifier = Modifier.weight(1f)) {
                        people = n
                    }
                }
            }

            Spacer(Modifier.height(Space.l))
            SectionCard(background = MaterialTheme.colorScheme.primaryContainer, corner = 18) {
                DetailRow(
                    label = "팁 $percent%",
                    value = "${format(tipAmount, decimals)} ${currency.code}",
                    sub = "${format(tipAmount * base, 0)}원",
                )
                DetailRow(
                    label = "합계",
                    value = "${format(total, decimals)} ${currency.code}",
                    sub = "${format(total * base, 0)}원",
                    emphasize = true,
                    valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (people > 1) {
                    DetailRow(
                        label = "1인당",
                        value = "${format(perPerson, decimals)} ${currency.code}",
                        sub = "${format(perPerson * base, 0)}원",
                        emphasize = true,
                        valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(Space.l))
            TipNote(tip.note)
        }
    }
}

@Composable
private fun TipNote(note: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.pillShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("ℹ", fontSize = Num.glyph, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.s))
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- 세금 환급

/**
 * 접이식 패널이던 것을 시트로 옮겼다.
 * 홈 화면에 카드가 여섯 개나 쌓여 주인공이 사라진 게 문제였고,
 * 환급은 상시 기능이 아니라 필요할 때만 열면 된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefundSheet(state: UiState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val refund = state.selected.taxRefund ?: return
    val currency = state.selected
    val price = state.foreignAmount
    val base = state.baseRate ?: 0.0
    val decimals = currency.decimals
    val code = currency.code

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.gutter),
        ) {
            Text(
                text = "세금 환급 예상",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "${refund.countryName} · 부가세 ${format(refund.vatPercent, 0)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (price <= 0.0 || base <= 0.0) {
                Spacer(Modifier.height(Space.l))
                Text(
                    text = "홈 화면에서 금액을 먼저 입력해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.m))
                RefundNote(refund)
                return@Column
            }

            val vatPart = refund.vatIncludedIn(price)
            val net = refund.estimatedRefund(price)
            val after = price - net

            Spacer(Modifier.height(Space.l))
            SectionCard(background = MaterialTheme.colorScheme.surfaceContainer) {
                DetailRow(
                    label = "구매 금액",
                    value = "${format(price, decimals)} $code",
                    sub = "${format(price * base, 0)}원",
                )
                DetailRow(
                    label = "정가에 포함된 부가세",
                    value = "${format(vatPart, decimals)} $code",
                    sub = "${format(vatPart * base, 0)}원",
                )
                DetailRow(
                    label = refund.benefitLabel,
                    value = "${format(net, decimals)} $code",
                    sub = "${format(net * base, 0)}원",
                    emphasize = true,
                    valueColor = MaterialTheme.colorScheme.primary,
                )
                DetailRow(
                    label = "환급 후 실부담",
                    value = "${format(after, decimals)} $code",
                    sub = "${format(after * base, 0)}원",
                )
            }

            if (refund.belowMinimum(price)) {
                Spacer(Modifier.height(Space.m))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.pillShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = Space.m, vertical = Space.m)
                ) {
                    Text(
                        text = if (refund.minimumIsPreTax) {
                            "한 매장에서 하루 세금 제외 ${format(refund.minimumPurchase, 0)} $code 이상" +
                                " 사야 면세받을 수 있습니다." +
                                " 현재 세금 제외 ${format(refund.priceForMinimum(price), 0)} $code."
                        } else {
                            "한 매장에서 하루 ${format(refund.minimumPurchase, 0)} $code 이상" +
                                " 사야 환급을 신청할 수 있습니다. 현재 ${format(price, 0)} $code."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(Modifier.height(Space.m))
            RefundNote(refund)
        }
    }
}

@Composable
private fun RefundNote(refund: TaxRefund) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.pillShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Space.m, vertical = Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Text("ℹ", fontSize = Num.glyph, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.s))
        Text(
            text = refund.note
                ?: ("환급액은 대행사·수령 방식(현금/카드)에 따라 달라집니다. " +
                    "실수령률은 대행 수수료를 약 25%로 잡은 추정치이며, 공항에서 세관 확인을 받아야 합니다."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- 진입 칩

@Composable
fun ToolChip(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(Radius.pillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(glyph, fontSize = Num.glyph)
        Spacer(Modifier.width(Space.s))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------- 공유

/**
 * 공유 방법을 고르는 시트. 위에 실제로 보낼 이미지 카드를 미리 보여주고,
 * 이미지(글 함께) 또는 글만 보낼 수 있다. 결과가 없으면 글 공유만 남긴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(state: UiState, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val text = remember(state) { buildConversionShareText(state) }
    val card = remember(state.result, state.input, state.priceMode, state.selected, state.foreignToKrw, state.snapshot) {
        ShareCard.render(context, state)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "환산 결과 공유",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "이미지에는 앱 받기 링크가 담긴 글이 함께 붙습니다",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.l))
            if (card != null) {
                androidx.compose.foundation.Image(
                    bitmap = card.asImageBitmap(),
                    contentDescription = "공유할 이미지 미리보기",
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(18.dp)),
                )
                Spacer(Modifier.height(Space.l))
                ShareAction(
                    label = "이미지로 공유",
                    primary = true,
                ) {
                    onDismiss()
                    ShareCard.share(context, card, text)
                }
                Spacer(Modifier.height(Space.s))
            }
            ShareAction(label = "글로 공유", primary = card == null) {
                onDismiss()
                shareText(context, text, "환산 결과 공유")
            }
        }
    }
}

@Composable
private fun ShareAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .then(
                if (primary) {
                    Modifier.background(TealGradient)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (primary) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}
