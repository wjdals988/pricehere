package com.pricehere.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxRefundMinimumTest {

    private val before = TaxRefund.japan(LocalDate.of(2026, 10, 31))
    private val after = TaxRefund.japan(LocalDate.of(2026, 11, 1))
    private val czech = Currency.CZK.taxRefundOn(LocalDate.of(2026, 10, 24))!!

    @Test fun 일본은_세금_뺀_가격으로_최소_금액을_본다() {
        // 세금 포함 5,400엔 = 세금 제외 약 4,909엔. 5,000엔에 못 미친다.
        assertTrue(before.belowMinimum(5_400.0))
        assertTrue(after.belowMinimum(5_400.0))
        assertEquals(4_909.0, before.priceForMinimum(5_400.0), 1.0)
    }

    @Test fun 일본_세금_포함_5500엔이면_통과() {
        assertFalse(before.belowMinimum(5_500.0))
        assertFalse(after.belowMinimum(5_500.0))
    }

    @Test fun 체코는_정가_그대로_비교() {
        assertTrue(czech.belowMinimum(2_000.0))
        assertFalse(czech.belowMinimum(2_001.0))
    }
}
