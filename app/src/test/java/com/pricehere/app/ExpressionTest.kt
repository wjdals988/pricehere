package com.pricehere.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpressionTest {

    private fun eval(s: String) = Expression.evaluate(s)

    // ---------- 평가 ----------

    @Test fun 숫자_하나는_그대로() {
        assertEquals(0.0, eval("")!!, 0.0)
        assertEquals(1000.0, eval("1000")!!, 0.0)
        assertEquals(12.5, eval("12.5")!!, 0.0)
    }

    @Test fun 곱셈_나눗셈이_먼저() {
        assertEquals(14.0, eval("2+3*4")!!, 0.0)
        assertEquals(10.0, eval("20-30/3")!!, 0.0)
        assertEquals(7.0, eval("1+2*3")!!, 0.0)
    }

    @Test fun 같은_우선순위는_왼쪽부터() {
        assertEquals(5.0, eval("10-3-2")!!, 0.0)
        assertEquals(2.5, eval("20/4/2")!!, 0.0)
    }

    @Test fun 끝에_남은_연산자와_소수점은_무시() {
        assertEquals(20.0, eval("20+")!!, 0.0)
        assertEquals(12.0, eval("12.")!!, 0.0)
        assertEquals(35.0, eval("20+15*")!!, 0.0)
    }

    @Test fun 영으로_나누면_null() {
        assertNull(eval("10/0"))
        assertNull(eval("5+10/0"))
    }

    @Test fun 소수_덧셈에_부동소수점_오차가_없다() {
        // Double로 더하면 0.30000000000000004 가 된다.
        assertEquals(0.3, eval("0.1+0.2")!!, 0.0)
    }

    @Test fun 음수_결과도_계산한다() {
        assertEquals(-10.0, eval("10-20")!!, 0.0)
    }

    // ---------- 표시 ----------

    @Test fun 표시는_천단위_콤마와_수학_기호() {
        assertEquals("1,000 + 500", Expression.pretty("1000+500"))
        assertEquals("20 × 3 − 5 ÷ 2", Expression.pretty("20*3-5/2"))
    }

    @Test fun 입력_중인_소수점과_끝_연산자를_보여준다() {
        assertEquals("1,234.", Expression.pretty("1234."))
        assertEquals("1,234.5", Expression.pretty("1234.5"))
        assertEquals("20 + ", Expression.pretty("20+"))
    }
}
