package com.pricehere.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 저장 항목 고치기 시트(v2.13 #3). 배경을 덮지 않아 위 목록이 그대로 보이고, 바꾼 값이 목록에 바로 미리 반영된다.

/**
 * 이름·금액·결제 수단만 고친다. 통화와 저장 당시 환율은 바꾸지 않는다(그 시점 기록이므로).
 * scrim을 투명하게 두어 본문이 어두워지지 않는다. 시트 바깥을 누르면 고치지 않고 닫는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSavedSheet(
    original: SavedItem,
    draft: SavedItem,
    onDraft: (SavedItem) -> Unit,
    onSave: (SavedItem) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** 초안을 지금 환율로 계산한 원화. 키보드가 올라와 목록이 가려져도 바뀐 결과를 볼 수 있게 시트에도 보여준다. */
    previewKrw: Double? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var memo by remember(original.id) { mutableStateOf(original.memo) }
    var amountText by remember(original.id) {
        mutableStateOf(java.math.BigDecimal(original.amount).stripTrailingZeros().toPlainString())
    }
    val amount = amountText.replace(",", "").toDoubleOrNull()
    val valid = amount != null && amount > 0.0
    val changed = draft != original

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Space.gutter)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "항목 고치기",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (previewKrw != null) {
                    Text(
                        text = "지금 환율 ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = format(previewKrw, 0) + "원",
                        style = Num.amount(18).copy(letterSpacing = 0.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            OutlinedTextField(
                value = memo,
                onValueChange = {
                    memo = it.take(40)
                    onDraft(draft.copy(memo = memo))
                },
                label = { Text("항목 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { raw ->
                    val clean = raw.filter { it.isDigit() || it == '.' }.take(12)
                    amountText = clean
                    clean.toDoubleOrNull()?.takeIf { it > 0 }?.let { onDraft(draft.copy(amount = it)) }
                },
                label = { Text("금액 · ${original.currencyCode}") },
                singleLine = true,
                isError = !valid,
                supportingText = if (!valid) ({ Text("0보다 큰 금액을 넣어 주세요") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = Num.amount(20),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PriceMode.entries.forEach { mode ->
                    val on = draft.mode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { onDraft(draft.copy(mode = mode)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.shortLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = "환율은 저장할 때 값을 그대로 씁니다. 지금 환율은 목록에서 따로 보여줍니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("삭제", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (valid && changed) TealGradient
                            else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        .clickable(enabled = valid && changed) { onSave(draft.copy(memo = memo)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "저장",
                        fontWeight = FontWeight.Bold,
                        color = if (valid && changed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
