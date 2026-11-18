package com.pricehere.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 이 앱의 모든 모서리·여백·그림자는 여기서만 나온다.
 *
 * 토큰화 전에는 반경 13종(2~24dp), 세로 여백 18종(2~28dp)이 섞여 있었다.
 * 값이 많으면 규칙이 없는 것과 같아서, 눈이 리듬을 잡지 못한다.
 */
object Radius {
    /** 카드는 전부 이 값 하나를 쓴다. */
    val card = 20.dp
    /** 칩·세그먼트·버튼처럼 눌리는 것. */
    val pill = 14.dp
    /** 배지·진행 바처럼 아주 작은 것. */
    val badge = 8.dp

    val cardShape = RoundedCornerShape(card)
    val pillShape = RoundedCornerShape(pill)
    val badgeShape = RoundedCornerShape(badge)
}

/** 4의 배수만 쓴다. 이 일곱 값 밖으로 나가면 리듬이 깨진다. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    /** 화면 좌우 거터. 스크린 패딩은 전부 이 값. */
    val gutter = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/**
 * 그림자는 카드 위에 떠 있는 버튼(스왑) 하나만 쓴다.
 * 카드는 배경(#EDF1F0)과 흰 면의 명도 차로 구분한다. Wise·Revolut와 같은 방식.
 */
object Elev {
    val floating = 3.dp
}

/**
 * 금액·환율처럼 자릿수가 맞아야 하는 숫자 전용 스타일.
 * tnum(tabular numerals)을 켜면 숫자 폭이 같아져서 새로고침할 때 값이 흔들리지 않는다.
 */
object Num {
    const val FEATURES = "tnum"

    /** 국기 이모지. 기기 폰트마다 크기감이 달라 한 값으로 묶어 둔다. */
    val flag = 15.sp
    /** ▲▼ ℹ 같은 작은 글리프. */
    val glyph = 13.sp

    /**
     * 금액 숫자 글꼴 Geist(OFL 1.1, res/raw/license_geist_ofl.txt).
     * v2.8의 Archivo는 폭이 넓고 1의 깃발이 커서 투박했다. Geist는 기하학적이면서 0·6·9 속공간이
     * 넓어 작은 크기에서도 또렷하고, 파일도 165KB로 가볍다. 가변 글꼴의 굵기 축만 골라 쓰고,
     * 없는 글자(×÷ 등)는 시스템 글꼴로 넘어간다.
     */
    @OptIn(ExperimentalTextApi::class)
    val family = FontFamily(
        listOf(500, 600, 700, 800).map { w ->
            Font(
                R.font.geist,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(FontVariation.weight(w)),
            )
        },
    )

    fun amount(size: Int) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = (size + 6).sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-size * 0.02).sp,
        fontFeatureSettings = FEATURES,
    )
}

/**
 * 기기 글자 크기가 이 배율 이상이면 좁은 칸에서 보조 표시(국기·수수료율)를 숨기고 금액을 줄여 쓴다(v2.13 #6).
 * 에뮬레이터 실측: 130%에서는 모두 들어가고, 200%에서 통화 칸·빠른 금액·결제 수단 이름이 넘쳤다.
 */
const val LARGE_FONT_SCALE = 1.5f

@androidx.compose.runtime.Composable
fun isLargeFont(): Boolean = androidx.compose.ui.platform.LocalDensity.current.fontScale >= LARGE_FONT_SCALE
