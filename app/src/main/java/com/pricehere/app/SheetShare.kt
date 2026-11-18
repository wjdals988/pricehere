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

// 공유 시트(환산·저장 목록 카드, 사진 저장, 글 공유). Sheets.kt에서 나눔(v2.13).

// ---------------------------------------------------------------- 공유

/** 환산 결과 공유 시트. 실제로 보낼 가격표 카드를 미리 보여준다. */
@Composable
fun ShareSheet(state: UiState, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val text = remember(state) { buildConversionShareText(state) }
    val dark = isDark()
    val card = remember(state.result, state.input, state.priceMode, state.selected, state.foreignToKrw, state.snapshot, dark) {
        ShareCard.render(context, state, dark)
    }
    ImageShareSheet(title = "환산 결과 공유", card = card, text = text, onDismiss = onDismiss)
}

/** 저장 목록 공유 시트. 고른 항목과 합계·면세 한도를 카드 한 장에 담는다. */
@Composable
fun SavedShareSheet(items: List<SavedItem>, snapshot: Snapshot?, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val text = remember(items, snapshot) { buildSavedShareText(items, snapshot) }
    val dark = isDark()
    val card = remember(items, snapshot, dark) { ShareCard.renderSaved(context, items, snapshot, dark) }
    ImageShareSheet(title = "저장 목록 공유", card = card, text = text, onDismiss = onDismiss)
}

/**
 * 공유 방법을 고르는 시트. 위에 실제 이미지를 미리 보여주고,
 * 이미지 공유(글 함께) · 사진에 저장 · 글만 공유 중에 고른다. 이미지가 없으면 글 공유만 남긴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageShareSheet(title: String, card: android.graphics.Bitmap?, text: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = rememberView()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saved by remember { mutableStateOf(false) }
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
                text = title,
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
                // 긴 저장 목록 카드도 시트 안에 들어오도록 높이를 제한한다.
                androidx.compose.foundation.Image(
                    bitmap = card.asImageBitmap(),
                    contentDescription = "공유할 이미지 미리보기",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .heightIn(max = 420.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(18.dp)),
                )
                Spacer(Modifier.height(Space.l))
                ShareAction(label = "이미지로 공유", primary = true, modifier = Modifier.fillMaxWidth()) {
                    onDismiss()
                    ShareCard.share(context, card, text)
                }
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    if (ShareCard.canSaveToGallery) {
                        ShareAction(
                            label = if (saved) "사진에 저장됨" else "사진에 저장",
                            primary = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (!saved) {
                                saved = ShareCard.saveToGallery(context, card)
                                if (saved) {
                                    Haptic.confirm(view)
                                } else {
                                    android.widget.Toast.makeText(context, "사진에 저장하지 못했습니다", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    ShareAction(label = "글로 공유", primary = false, modifier = Modifier.weight(1f)) {
                        onDismiss()
                        shareText(context, text, title)
                    }
                }
            } else {
                ShareAction(label = "글로 공유", primary = true, modifier = Modifier.fillMaxWidth()) {
                    onDismiss()
                    shareText(context, text, title)
                }
            }
        }
    }
}

@Composable
internal fun ShareAction(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
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
