package com.pricehere.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * v2.7 개편에서 화면들이 함께 쓰는 조각.
 * 기억에 남길 요소는 가격표(점선과 양옆 홈) 하나로 두고, 나머지는 글래스 재질·은은한 광원·움직임으로
 * 결을 맞춘다. 글래스는 뒤 흐림 없이 반투명 면 + 밝은 테두리로 만든다. 뒤 흐림(RenderEffect)은
 * Android 12 이상에서만 되고 다른 Composable 뒤를 흐리게 하려면 라이브러리가 필요해서다.
 */

// ---------------------------------------------------------------- 앱 아이콘 (C안)

/**
 * 런처 아이콘과 같은 그라데이션 타일에 윗변 하이라이트와 ₩ 아래 그림자를 더한 헤더용 마크.
 * 좌표는 ic_launcher_foreground.xml 과 같은 108 기준이다. 헤더에서는 런처 여백이 필요 없어 1.11배로 채운다.
 */
@Composable
fun WonTile(sizeDp: Int = 40) {
    val shape = RoundedCornerShape((sizeDp * 30f / 108f).dp)
    Canvas(
        Modifier
            .size(sizeDp.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color(0xFF0B6E5E),
                spotColor = Color(0xFF0B6E5E),
            ),
    ) {
        val k = size.minDimension / 108f
        val corner = CornerRadius(30f * k)
        drawRoundRect(
            brush = Brush.linearGradient(
                0f to Color(0xFF17A08C),
                0.55f to Color(0xFF0B6E5E),
                1f to Color(0xFF05433A),
                start = Offset(6f * k, 0f),
                end = Offset(102f * k, 108f * k),
            ),
            cornerRadius = corner,
        )
        // 오른쪽 위 광택
        drawPath(
            Path().apply {
                moveTo(108f * k, 0f)
                lineTo(108f * k, 62f * k)
                cubicTo(78f * k, 44f * k, 58f * k, 20f * k, 54f * k, 0f)
                close()
            },
            color = Color.White.copy(alpha = 0.10f),
        )
        // 윗변 하이라이트: 위에서 빛을 받는 느낌으로 테두리 위쪽만 밝힌다.
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.55f),
                0.45f to Color.White.copy(alpha = 0f),
            ),
            topLeft = Offset(1.5f * k, 1.5f * k),
            size = Size(105f * k, 105f * k),
            cornerRadius = CornerRadius(28.5f * k),
            style = Stroke(width = 3f * k),
        )
        val zoom = 1.11f
        fun p(v: Float) = (54f + (v - 54f) * zoom) * k
        val w = Path().apply {
            moveTo(p(36.2f), p(37f))
            lineTo(p(45.1f), p(72.7f))
            lineTo(p(54f), p(44.6f))
            lineTo(p(62.9f), p(72.7f))
            lineTo(p(71.8f), p(37f))
        }
        val bar = 5.1f * zoom * k
        val stroke = Stroke(width = 7.6f * zoom * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // ₩ 아래 그림자
        val drop = 2.5f * k
        translate(top = drop) {
            drawPath(w, Color(0xFF05433A).copy(alpha = 0.4f), style = stroke)
            listOf(52.3f, 62.5f).forEach { y ->
                drawLine(Color(0xFF05433A).copy(alpha = 0.4f), Offset(p(32.8f), p(y)), Offset(p(75.2f), p(y)), bar, StrokeCap.Round)
            }
        }
        val ink = Brush.verticalGradient(
            0f to Color.White,
            1f to Color(0xFFD3F3EA),
            startY = p(30f),
            endY = p(80f),
        )
        drawPath(w, ink, style = stroke)
        listOf(52.3f, 62.5f).forEach { y ->
            drawLine(ink, Offset(p(32.8f), p(y)), Offset(p(75.2f), p(y)), bar, StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------- 재질

/** 글래스 면. 밝은 반투명 면 + 위쪽이 더 밝은 테두리. 다크에서는 흰 면 대신 옅은 흰 막. */
@Composable
fun Modifier.glass(shape: Shape): Modifier {
    val dark = isDark()
    return this
        .clip(shape)
        .background(if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.58f))
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                if (dark) {
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
                } else {
                    listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.55f))
                }
            ),
            shape = shape,
        )
}

/** 카드 뒤에 깔리는 은은한 빛 번짐 두 곳. 화면 배경이 단색일 때보다 글래스 면이 살아난다. */
@Composable
fun Modifier.ambientGlow(): Modifier {
    val dark = isDark()
    val teal = Color(0xFF17A08C).copy(alpha = if (dark) 0.20f else 0.26f)
    val mint = Color(0xFF7FE0C3).copy(alpha = if (dark) 0.10f else 0.30f)
    return drawBehind {
        drawCircle(
            brush = Brush.radialGradient(listOf(teal, Color.Transparent), center = Offset(size.width * 0.25f, 300.dp.toPx()), radius = 230.dp.toPx()),
            radius = 230.dp.toPx(),
            center = Offset(size.width * 0.25f, 300.dp.toPx()),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(mint, Color.Transparent), center = Offset(size.width * 0.95f, 420.dp.toPx()), radius = 170.dp.toPx()),
            radius = 170.dp.toPx(),
            center = Offset(size.width * 0.95f, 420.dp.toPx()),
        )
    }
}

/** 주요 버튼(저장)의 틸 그라데이션. 위쪽 안쪽에 얇은 하이라이트를 넣어 살짝 볼록해 보이게 한다. */
val TealGradient = Brush.linearGradient(listOf(Color(0xFF17A08C), Color(0xFF0B6E5E)))

/** 스왑 버튼의 잉크 그라데이션. */
val InkGradient = Brush.linearGradient(listOf(Color(0xFF2A3935), Color(0xFF15201D)))

// ---------------------------------------------------------------- 가격표

/**
 * 가격표의 이음매 줄. 위·아래 칸 경계에 점선을 긋고, 양옆을 반원으로 파낸다.
 * 높이가 정해진 줄이라 위·아래 칸 높이가 글꼴 크기로 바뀌어도 홈 위치가 어긋나지 않는다.
 */
class SeamShape(private val notch: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { notch.toPx() }
        val cy = size.height / 2f
        val rect = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val holes = Path().apply {
            addOval(Rect(-r, cy - r, r, cy + r))
            addOval(Rect(size.width - r, cy - r, size.width + r, cy + r))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, rect, holes))
    }
}

/** 이음매 줄의 위 절반은 위 칸 색, 아래 절반은 아래 칸 색으로 칠하고 가운데 점선을 긋는다. */
fun Modifier.seam(top: Color, bottom: Brush, dash: Color, inset: Dp): Modifier = drawBehind {
    val half = size.height / 2f
    drawRect(top, size = Size(size.width, half))
    drawRect(bottom, topLeft = Offset(0f, half), size = Size(size.width, half))
    val x = inset.toPx()
    drawLine(
        color = dash,
        start = Offset(x, half),
        end = Offset(size.width - x, half),
        strokeWidth = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
    )
}

// ---------------------------------------------------------------- 숫자 롤링

/**
 * 바뀐 자리 숫자만 아래에서 올라온다. 자리는 오른쪽 끝에서부터 세서 맞춘다.
 * 왼쪽에서 세면 107,442 → 1,074,420 처럼 자릿수가 늘 때 모든 글자가 한 칸씩 밀려 전부 다시 움직인다.
 */
@Composable
fun RollingNumber(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Row(modifier) {
        val n = text.length
        text.forEachIndexed { i, ch ->
            val fromRight = n - 1 - i
            key(fromRight) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val delay = (fromRight * 22).coerceAtMost(200)
                        (
                            slideInVertically(tween(320, delayMillis = delay)) { it / 2 } +
                                fadeIn(tween(240, delayMillis = delay))
                            ) togetherWith (
                            slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(160))
                            )
                    },
                    label = "digit",
                ) { c ->
                    Text(text = c.toString(), style = style, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 슬라이딩 선택

/**
 * 고른 칸 뒤의 흰 알약이 살짝 튕기며 옮겨 가는 세그먼트. 통화 탭과 결제 수단이 같이 쓴다.
 * 칸 폭이 같아야 위치 계산이 맞으므로 칸은 weight(1f)로 채운다.
 */
@Composable
fun SlidingSegments(
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = isDark()
    val track = RoundedCornerShape(16.dp)
    BoxWithConstraints(modifier.fillMaxWidth().glass(track).padding(4.dp)) {
        val cell = maxWidth / count
        val x by animateDpAsState(
            targetValue = cell * selected,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        // 세로 스크롤 안이라 높이 제약이 무한이다. matchParentSize로 형제 Row 높이에 맞춘 상자 안에서 그린다.
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .offset(x = x)
                    .width(cell)
                    .fillMaxHeight()
                    .shadow(3.dp, RoundedCornerShape(12.dp), clip = false)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else Color.White),
            )
        }
        Row(Modifier.fillMaxWidth(), content = content)
    }
}


// ---------------------------------------------------------------- 실시간 점

/** 고시가 최신일 때만 맥박처럼 퍼지는 점. 오래됐거나 오프라인이면 멈춘 점으로 색만 보여준다. */
@Composable
fun LiveDot(color: Color, live: Boolean) {
    Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
        if (live) {
            val t = rememberInfiniteTransition(label = "live")
            val p by t.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
                label = "pulse",
            )
            Box(
                Modifier
                    .size(12.dp)
                    .scale(0.6f + p * 0.9f)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.45f * (1f - p))),
            )
        }
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    }
}

// ---------------------------------------------------------------- 선 아이콘

/** 1.8 선 두께로 맞춘 도구 아이콘. 아이콘 라이브러리를 늘리지 않으려고 직접 그린다. */
@Composable
fun ReceiptIcon(tint: Color, sizeDp: Int = 24) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val k = size.minDimension / 24f
        val s = Stroke(width = 1.7f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(
            Path().apply {
                moveTo(6f * k, 3.5f * k); lineTo(18f * k, 3.5f * k); lineTo(18f * k, 20.5f * k)
                lineTo(15.5f * k, 18.9f * k); lineTo(13.5f * k, 20.5f * k); lineTo(12f * k, 19.3f * k)
                lineTo(10.5f * k, 20.5f * k); lineTo(8.5f * k, 18.9f * k); lineTo(6f * k, 20.5f * k); close()
            },
            tint,
            style = s,
        )
        drawLine(tint, Offset(9.5f * k, 9f * k), Offset(14.5f * k, 9f * k), 1.7f * k, StrokeCap.Round)
        drawLine(tint, Offset(9.5f * k, 12.5f * k), Offset(14.5f * k, 12.5f * k), 1.7f * k, StrokeCap.Round)
    }
}

@Composable
fun CoinIcon(tint: Color, sizeDp: Int = 24) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val k = size.minDimension / 24f
        val s = Stroke(width = 1.7f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(tint, radius = 8.5f * k, center = Offset(12f * k, 12f * k), style = s)
        drawPath(
            Path().apply {
                moveTo(14.8f * k, 8.8f * k)
                cubicTo(14.2f * k, 8f * k, 13.2f * k, 7.5f * k, 12f * k, 7.5f * k)
                cubicTo(10.3f * k, 7.5f * k, 9f * k, 8.3f * k, 9f * k, 9.5f * k)
                cubicTo(9f * k, 12.3f * k, 15f * k, 11f * k, 15f * k, 13.8f * k)
                cubicTo(15f * k, 15.1f * k, 13.7f * k, 16f * k, 12f * k, 16f * k)
                cubicTo(10.7f * k, 16f * k, 9.6f * k, 15.4f * k, 9f * k, 14.5f * k)
            },
            tint,
            style = s,
        )
    }
}

/** 30일 환율을 줄 그래프로. 값이 없으면 아무것도 그리지 않는다. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val pad = 2.dp.toPx()
        fun y(v: Double) = (pad + (1 - (v - lo) / span) * (size.height - pad * 2)).toFloat()
        val line = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * stepX, y(v)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------- 하단 탭

/** 떠 있는 탭 바(64) + 아래 여백(16) + 숨 쉴 틈(16). 스크롤 화면 끝에 이만큼 비워 마지막 항목이 가리지 않게 한다. */
val NavClearance = 96.dp

/** 떠 있는 글래스 탭 바. 아이콘 위·글자 아래, 칸마다 한 단어라 줄바꿈이 생기지 않는다. */
@Composable
fun GlassTabBar(
    current: Tab,
    savedCount: Int,
    hasUpdate: Boolean,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    val dark = isDark()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = Color(0x4015201D), spotColor = Color(0x4015201D), clip = false)
            // 그림자가 반투명 면 너머로 비쳐 띠처럼 보이지 않도록 탭 바는 글래스보다 진하게 채운다.
            .clip(shape)
            .background(if (dark) Color(0xEB1B2422) else Color(0xE6FFFFFF))
            .glass(shape)
            .padding(6.dp),
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == current
            val tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelect(tab) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    when (tab) {
                        Tab.HOME -> SwapIcon(tint, sizeDp = 18)
                        Tab.SAVED -> TagIcon(tint, sizeDp = 19)
                        Tab.INFO -> InfoIcon(tint, sizeDp = 19)
                    }
                    if (tab == Tab.SAVED && savedCount > 0) {
                        CountBadge(savedCount, Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = (-2).dp))
                    }
                    if (tab == Tab.INFO && hasUpdate) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-12).dp, y = 3.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Trend.rising),
                        )
                    }
                }
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    color = tint,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** 저장 개수 배지. 숫자가 바뀔 때마다 작게 튀어 올라 방금 저장됐다는 걸 보여준다. */
@Composable
private fun CountBadge(count: Int, modifier: Modifier) {
    val bump = remember { Animatable(1f) }
    LaunchedEffect(count) {
        bump.snapTo(0.5f)
        bump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
    }
    Box(
        modifier = modifier
            .scale(bump.value)
            .size(width = (if (count >= 10) 22 else 16).dp, height = 16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
    }
}
