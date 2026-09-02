package com.pricehere.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val APP_VERSION = "v${BuildConfig.VERSION_NAME}"

val STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"))

/** 고시 시각을 초까지 보여줘서 네이버·은행 화면과 직접 대조할 수 있게 한다. */
val CLOCK: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"))

val DAY_STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 HH:mm").withZone(ZoneId.of("Asia/Seoul"))

// ---------------------------------------------------------------- 숫자

/** 소수점 이하가 없으면 생략한다. 금액 표시용. */
fun format(value: Double, decimals: Int): String =
    NumberFormat.getNumberInstance(Locale.KOREA).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = decimals
    }.format(value)

/** 환율처럼 자릿수가 흔들리면 안 되는 값에 쓴다. */
fun formatFixed(value: Double, decimals: Int): String =
    NumberFormat.getNumberInstance(Locale.KOREA).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(value)

fun elapsed(minutes: Long): String = when {
    minutes < 1 -> "방금 전 기준"
    minutes < 60 -> "${minutes}분 전 기준"
    minutes < 60 * 24 -> "${minutes / 60}시간 전 기준"
    else -> "${minutes / (60 * 24)}일 전 기준"
}

fun dotColor(source: RateSource, minutes: Long): Color = when {
    source == RateSource.CACHE -> Freshness.stale
    minutes < 30 -> Freshness.fresh
    minutes < 60 * 24 -> Freshness.aging
    else -> Freshness.stale
}

// ---------------------------------------------------------------- 공통 조각

@Composable
fun amountStyle(color: Color, size: Int = 36) = TextStyle(
    fontSize = size.sp,
    lineHeight = (size + 6).sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-1.2).sp,
    color = color,
)

/** 화면 전체에서 쓰는 기본 카드. 배경만 다르고 모양은 같아야 리듬이 잡힌다. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
    corner: Int = 20,
    horizontal: Int = 18,
    vertical: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner.dp))
            .background(background)
            .padding(horizontal = horizontal.dp, vertical = vertical.dp),
        content = content,
    )
}

@Composable
fun CardHeader(flag: String, label: String, code: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(flag, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = code,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = tint,
        )
    }
}

/** 라벨 하나짜리 알약 버튼. 선택 상태를 색으로만 구분한다. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
        )
    }
}

/** 좌우로 라벨과 값을 벌려 놓는 한 줄. 환급 · 수수료 내역에서 반복해 쓴다. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    sub: String? = null,
    emphasize: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = if (emphasize) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
                color = valueColor,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
fun Dot(color: Color, size: Int = 7) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/** 아이콘 라이브러리를 더 붙이지 않으려고 ⇅ 를 직접 그린다. */
@Composable
fun SwapIcon(tint: Color, sizeDp: Int = 18) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.12f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val xL = w * 0.28f
        val xR = w * 0.72f
        val top = h * 0.11f
        val bottom = h * 0.89f
        val head = w * 0.17f

        drawLine(tint, Offset(xL, bottom), Offset(xL, top), sw, StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(xL - head, top + head)
                lineTo(xL, top)
                lineTo(xL + head, top + head)
            },
            color = tint,
            style = stroke,
        )

        drawLine(tint, Offset(xR, top), Offset(xR, bottom), sw, StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(xR - head, bottom - head)
                lineTo(xR, bottom)
                lineTo(xR + head, bottom - head)
            },
            color = tint,
            style = stroke,
        )
    }
}

/** 펼침/접힘을 나타내는 갈매기. 역시 아이콘 의존성을 피하려고 직접 그린다. */
@Composable
fun Chevron(tint: Color, expanded: Boolean, sizeDp: Int = 14) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = w * 0.14f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val path = Path().apply {
            if (expanded) {
                moveTo(w * 0.15f, h * 0.66f)
                lineTo(w * 0.5f, h * 0.32f)
                lineTo(w * 0.85f, h * 0.66f)
            } else {
                moveTo(w * 0.15f, h * 0.34f)
                lineTo(w * 0.5f, h * 0.68f)
                lineTo(w * 0.85f, h * 0.34f)
            }
        }
        drawPath(path, color = tint, style = stroke)
    }
}

// ---------------------------------------------------------------- 입력 표시

/**
 * 입력창에 천단위 콤마를 "보여주기만" 한다. 실제 상태 문자열은 콤마 없는 원본이라
 * 파싱이 단순하고, OffsetMapping으로 커서 위치도 정확히 유지된다.
 */
object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val dot = raw.indexOf('.')
        val intPart = if (dot >= 0) raw.substring(0, dot) else raw
        val tail = if (dot >= 0) raw.substring(dot) else ""

        val grouped = buildString {
            intPart.forEachIndexed { i, c ->
                if (i > 0 && (intPart.length - i) % 3 == 0) append(',')
                append(c)
            }
        }
        val out = grouped + tail
        val commas = grouped.length - intPart.length

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, raw.length)
                if (o > intPart.length) return (o + commas).coerceIn(0, out.length)
                val added = if (o == 0) 0 else commas - (intPart.length - o) / 3
                return (o + added).coerceIn(0, out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val o = offset.coerceIn(0, out.length)
                val commasBefore = out.take(o).count { it == ',' }
                return (o - commasBefore).coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
