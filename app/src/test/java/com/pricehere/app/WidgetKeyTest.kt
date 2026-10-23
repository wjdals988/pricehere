package com.pricehere.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetKeyTest {

    /** 키를 차례로 눌렀을 때의 입력 문자열. */
    private fun press(vararg keys: String) =
        keys.fold("") { acc, k -> WidgetPrefs.applyKey(acc, k) }

    @Test fun 숫자와_연산자를_번갈아() {
        assertEquals("20+15", press("2", "0", "+", "1", "5"))
    }

    @Test fun 맨_앞_연산자는_무시() {
        assertEquals("", press("+"))
        assertEquals("5", press("*", "5"))
    }

    @Test fun 연산자를_연달아_누르면_마지막_것으로_바뀐다() {
        assertEquals("5*", press("5", "+", "-", "*"))
    }

    @Test fun 소수점만_남은_채로_연산자를_누르면_소수점을_뗀다() {
        assertEquals("12+", press("1", "2", ".", "+"))
    }

    @Test fun 소수점은_숫자마다_하나() {
        assertEquals("1.5+2.5", press("1", ".", "5", ".", "+", "2", ".", "5"))
    }

    @Test fun 연산자_뒤_소수점은_0을_채운다() {
        assertEquals("3+0.5", press("3", "+", ".", "5"))
    }

    @Test fun 앞자리_0은_토큰마다_대체된다() {
        assertEquals("5+7", press("5", "+", "0", "7"))
        assertEquals("7", press("0", "7"))
    }

    @Test fun 비어_있는_토큰에서_00과_000은_무시() {
        assertEquals("", press("000"))
        assertEquals("5+", press("5", "+", "00"))
        assertEquals("0", press("0", "000"))
    }

    @Test fun 지우기와_초기화() {
        assertEquals("20+", press("2", "0", "+", "1", "DEL"))
        assertEquals("", press("2", "0", "+", "CLR"))
    }

    @Test fun 길이_한도_20자() {
        val long = press(*Array(25) { "9" })
        assertEquals(20, long.length)
    }
}
