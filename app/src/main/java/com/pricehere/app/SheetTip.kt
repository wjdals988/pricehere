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

// 팁 계산 시트. Sheets.kt에서 나눔(v2.13).

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
internal fun TipNote(note: String) {
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
