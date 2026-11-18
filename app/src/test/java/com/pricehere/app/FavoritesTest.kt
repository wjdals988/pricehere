package com.pricehere.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 자주 쓰는 통화(v2.13 #2): 통화 목록, 통신망 국가 자동 선택 규칙. */
class FavoritesTest {
    @Test fun `기본 통화 칸은 USD EUR JPY CZK`() {
        assertEquals(listOf("USD", "EUR", "JPY", "CZK"), Currency.DEFAULT_FAVORITES.map { it.code })
    }

    @Test fun `통화는 20종이고 코드가 겹치지 않는다`() {
        assertEquals(20, Currency.entries.size)
        assertEquals(20, Currency.entries.map { it.code }.toSet().size)
    }

    @Test fun `베트남 동과 인도네시아 루피아는 100단위 고시`() {
        assertEquals(100, Currency.VND.quoteUnit)
        assertEquals(100, Currency.IDR.quoteUnit)
    }

    @Test fun `v2_14부터 모든 통화에 팁 자료가 있다`() {
        assertTrue(Currency.entries.none { it.basicOnly })
    }

    @Test fun `환급 제도가 없는 나라는 null`() {
        listOf(Currency.HKD, Currency.MYR, Currency.NZD, Currency.CAD, Currency.GBP, Currency.USD)
            .forEach { assertNull(it.code, it.taxRefund) }
    }

    @Test fun `대만 환급은 5퍼센트에 2000대만달러 이상`() {
        val r = Currency.TWD.taxRefund!!
        assertEquals(5.0, r.vatPercent, 0.0)
        assertTrue(r.belowMinimum(1_999.0))
        assertTrue(!r.belowMinimum(2_000.0))
    }

    @Test fun `베트남 부가세는 2026년 말까지 8퍼센트, 이후 10퍼센트`() {
        assertEquals(8.0, Currency.VND.taxRefundOn(java.time.LocalDate.of(2026, 12, 31))!!.vatPercent, 0.0)
        assertEquals(10.0, Currency.VND.taxRefundOn(java.time.LocalDate.of(2027, 1, 1))!!.vatPercent, 0.0)
    }

    @Test fun `중국 실수령은 결제 금액의 약 9퍼센트`() {
        val r = Currency.CNY.taxRefund!!
        assertEquals(9.0, r.estimatedRefund(1_000.0) / 10.0, 0.1)
    }

    @Test fun `팁 문화 없음 표시 규칙`() {
        assertTrue(Currency.JPY.tip!!.noCulture)
        assertTrue(Currency.SGD.tip!!.noCulture)
        assertTrue(!Currency.HKD.tip!!.noCulture)
        assertEquals(listOf(15, 18, 20), Currency.CAD.tip!!.suggested)
    }

    @Test fun `통신망 국가가 통화 칸에 있으면 그 통화로`() {
        val fav = listOf(Currency.USD, Currency.TWD)
        assertEquals(Currency.TWD, LocalCurrency.decide("tw", "kr", Currency.USD, fav))
    }

    @Test fun `통신망 국가 통화가 칸에 없으면 바꾸지 않는다`() {
        assertNull(LocalCurrency.decide("tw", "kr", Currency.USD, Currency.DEFAULT_FAVORITES))
    }

    @Test fun `같은 나라에 머무는 동안은 다시 바꾸지 않는다`() {
        assertNull(LocalCurrency.decide("jp", "jp", Currency.USD, Currency.DEFAULT_FAVORITES))
    }
}
