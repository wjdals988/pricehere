package com.pricehere.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
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

/** 그림자는 한 겹만. 나머지 깊이는 배경 명도 차로 만든다. */
object Elev {
    val card = 1.dp
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

    fun amount(size: Int) = TextStyle(
        fontSize = size.sp,
        lineHeight = (size + 6).sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.2).sp,
        fontFeatureSettings = FEATURES,
    )
}
