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

    @Test fun `늘린 통화는 환급과 팁 자료가 없다`() {
        assertTrue(Currency.TWD.basicOnly)
        assertNull(Currency.TWD.taxRefund)
        assertTrue(!Currency.JPY.basicOnly)
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
