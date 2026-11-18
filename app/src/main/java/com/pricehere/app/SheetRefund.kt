package com.pricehere.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
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

// 세금 환급 시트와 도구 진입 칩. Sheets.kt에서 나눔(v2.13).

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
internal fun RefundNote(refund: TaxRefund) {
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
