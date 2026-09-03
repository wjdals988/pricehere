package com.pricehere.app

import androidx.compose.foundation.Canvas
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "${currency.flag} ${currency.code} 환율 추이",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${currency.quoteUnit} ${currency.code} 기준 원화",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HistoryRange.entries.forEach { r ->
                    Pill(
                        text = r.label,
                        selected = r == range,
                        modifier = Modifier.weight(1f),
                    ) { onRangeChange(r) }
                }
            }

            Spacer(Modifier.height(18.dp))

            when {
                loading && points.isEmpty() -> SheetPlaceholder(loading = true)
                points.size < 2 -> SheetPlaceholder(loading = false)
                else -> {
                    val unit = currency.quoteUnit
                    val summary = points.summarize()
                    LineChart(
                        points = points,
                        unit = unit,
                        accent = MaterialTheme.colorScheme.primary,
                        grid = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
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

                    Spacer(Modifier.height(14.dp))
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

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
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
            .clip(RoundedCornerShape(16.dp))
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
) {
    val values = points.map { it.rate * unit }
    val low = values.min()
    val high = values.max()
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 16.dp)
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
        drawPath(
            fill,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.02f))
            ),
        )
        drawPath(
            line,
            color = accent,
            style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(accent, radius = 4.5f, center = Offset(x(values.lastIndex), y(values.last())))
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "${tip.flag} 팁 계산",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "계산서 ${format(bill, decimals)} ${currency.code}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (bill <= 0.0 || base <= 0.0) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "홈 화면에서 금액을 먼저 입력해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                TipNote(tip.note)
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "팁 비율",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tip.suggested.forEach { p ->
                    Pill(text = "$p%", selected = p == percent, modifier = Modifier.weight(1f)) {
                        percent = p
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "나눠 낼 인원",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 2, 3, 4, 5, 6).forEach { n ->
                    Pill(text = "$n", selected = n == people, modifier = Modifier.weight(1f)) {
                        people = n
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
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

            Spacer(Modifier.height(14.dp))
            TipNote(tip.note)
        }
    }
}

@Composable
private fun TipNote(note: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("ℹ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(9.dp))
        Text(
            text = note,
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
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(glyph, fontSize = 13.sp)
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
