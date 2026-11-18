package com.pricehere.app

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 촉각 반응. performHapticFeedback만 써서 진동 권한이 필요 없고,
 * 사용자가 시스템 설정에서 터치 진동을 끄면 함께 꺼진다.
 */
object Haptic {
    /** 숫자 한 자리가 넘어가거나 그래프 점을 지날 때의 가장 약한 틱. */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** 통화·결제 수단처럼 칸을 고를 때. */
    fun segment(view: View) {
        val c = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        view.performHapticFeedback(c)
    }

    /** 저장처럼 일이 끝났음을 알릴 때. */
    fun confirm(view: View) {
        val c = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        view.performHapticFeedback(c)
    }
}

@Composable
fun rememberView(): View = LocalView.current

// ---------------------------------------------------------------- 저장 태그 날리기

/**
 * 저장하면 가격표의 저장 버튼에서 태그가 포물선을 그리며 하단 '저장' 탭으로 날아간다.
 * 좌표는 모두 창 기준(positionInRoot)이고, 오버레이가 자기 위치를 빼서 그린다.
 */
class FlyToSaved {
    /** 저장 탭 아이콘 중심. 탭 바가 숨어 있으면 null. */
    var target by mutableStateOf<Offset?>(null)
    internal val flights = mutableStateListOf<Pair<Long, Offset>>()
    private var seq = 0L

    /** 날릴 곳이 없으면(탭 바가 숨어 있으면) false. */
    fun launch(from: Offset): Boolean {
        if (target == null) return false
        flights += (seq++) to from
        return true
    }
}

val LocalFlyToSaved = staticCompositionLocalOf<FlyToSaved?> { null }

private val FlyEasing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)

@Composable
fun FlyOverlay(fly: FlyToSaved, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val view = rememberView()
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        fly.flights.toList().forEach { (id, from) ->
            key(id) {
                // 탭 바가 숨으면 목적지가 없다. 그때는 마지막으로 본 자리로 계속 날려 끝낸다.
                var lastTo by remember { mutableStateOf(fly.target ?: from) }
                fly.target?.let { lastTo = it }
                val to = lastTo
                val t = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    t.animateTo(1f, tween(560, easing = FlyEasing))
                    Haptic.confirm(view)
                    fly.flights.removeAll { it.first == id }
                }
                val lift = with(LocalDensity.current) { 140.dp.toPx() }
                val p = t.value
                // 2차 베지어: 출발점 → 위로 솟은 조절점 → 탭 아이콘
                val c = Offset(from.x + (to.x - from.x) * 0.35f, minOf(from.y, to.y) - lift)
                val u = 1f - p
                val x = u * u * from.x + 2 * u * p * c.x + p * p * to.x
                val y = u * u * from.y + 2 * u * p * c.y + p * p * to.y
                val half = with(LocalDensity.current) { 18.dp.toPx() }
                Box(
                    Modifier
                        .offset { IntOffset((x - origin.x - half).roundToInt(), (y - origin.y - half).roundToInt()) }
                        .graphicsLayer {
                            val s = 1f - 0.5f * p
                            scaleX = s
                            scaleY = s
                            rotationZ = -18f * p
                            alpha = if (p > 0.9f) (1f - p) * 10f else 1f
                        }
                        .size(36.dp)
                        .shadow(8.dp, CircleShape, clip = false, ambientColor = Color(0x660B3C32), spotColor = Color(0x660B3C32))
                        .clip(CircleShape)
                        .background(TealGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    TagIcon(Color.White, sizeDp = 16)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 시작 화면 → 헤더 로고 착지

/**
 * 시작 화면(Android 12+)의 ₩ 타일을 헤더 로고 자리로 날려 앉히기 위한 연결점.
 * 헤더가 자기 로고 위치(창 기준 px)를 적어 두고, MainActivity가 시작 화면을 닫을 때 그 자리로 날린다.
 * 착지하면 done이 true가 되고, 홈 화면의 나머지가 아래에서 떠오른다.
 */
object SplashGate {
    var headerTile: android.graphics.RectF? = null
    val done = mutableStateOf(true)
}

/** 시작 화면이 끝난 뒤 떠오르는 등장. delay로 위에서 아래 순서를 만든다. */
@Composable
fun Modifier.introRise(delayMillis: Int): Modifier {
    val done by SplashGate.done
    val p by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = tween(420, delayMillis = if (done) delayMillis else 0, easing = FlyEasing),
        label = "intro",
    )
    val lift = with(LocalDensity.current) { 14.dp.toPx() }
    return graphicsLayer {
        alpha = p
        translationY = (1f - p) * lift
    }
}
