package com.pricehere.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressionInputTest {
    private fun s(raw: String) = Expression.sanitize(raw, 24)

    @Test fun 숫자만_있으면_그대로() = assertEquals("1234.5", s("1234.5"))
    @Test fun 보이는_기호도_받는다() = assertEquals("20*3/2-1", s("20×3÷2−1"))
    @Test fun 맨_앞_연산자는_버린다() = assertEquals("5", s("+5"))
    @Test fun 연달아_친_연산자는_마지막_것() = assertEquals("3*", s("3+-*"))
    @Test fun 연산자_앞_소수점은_지운다() = assertEquals("3+", s("3.+"))
    @Test fun 숫자마다_소수점_하나() = assertEquals("1.5+2.25", s("1.5.+2.2.5"))
    @Test fun 숫자_없는_소수점은_0점() = assertEquals("0.5+0.1", s(".5+.1"))
    @Test fun 글자와_쉼표는_버린다() = assertEquals("1000", s("1,0a00"))
    @Test fun 길이_한도() = assertEquals(4, Expression.sanitize("123456", 4).length)

    @Test fun 표시_변환() {
        val (out, map) = Expression.prettyWithMap("1234+5.5")
        assertEquals("1,234 + 5.5", out)
        // 1 2 3 4 + 5 . 5 (끝)
        assertArrayEquals(intArrayOf(0, 2, 3, 4, 5, 8, 9, 10, 11), map)
    }

    @Test fun 표시_변환은_pretty와_같다() {
        listOf("0.5", "1000000*3", "12/4-", "7.").forEach {
            assertEquals(Expression.pretty(it), Expression.prettyWithMap(it).first)
        }
    }
}
