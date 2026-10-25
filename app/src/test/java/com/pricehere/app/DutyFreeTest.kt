package com.pricehere.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DutyFreeTest {

    private fun item(amount: Double, code: String, rateAtSave: Double, mode: PriceMode = PriceMode.BASE) =
        SavedItem(
            id = 1, memo = "t", amount = amount, currencyCode = code,
            rateAtSave = rateAtSave, savedAtMillis = 0, mode = mode,
        )

    private val rates = mapOf("USD" to 1_400.0, "JPY" to 9.0, "EUR" to 1_500.0)

    @Test fun 여러_통화를_달러로_합친다() {
        // 70,000엔 = 630,000원 = 450달러, 200유로 = 300,000원 = 약 214.29달러
        val usd = dutyFreeUsd(listOf(item(70_000.0, "JPY", 9.0), item(200.0, "EUR", 1_500.0)), rates)!!
        assertEquals(450.0 + 300_000.0 / 1_400.0, usd, 0.001)
    }

    @Test fun 카드_수수료는_물품_가격이_아니라_빼고_계산한다() {
        val usd = dutyFreeUsd(listOf(item(100.0, "USD", 1_400.0, PriceMode.CARD)), rates)!!
        assertEquals(100.0, usd, 0.001)
    }

    @Test fun 지금_환율이_없으면_저장_당시_환율을_쓴다() {
        val usd = dutyFreeUsd(listOf(item(1_000.0, "CZK", 70.0)), rates)!!
        assertEquals(70_000.0 / 1_400.0, usd, 0.001)
    }

    @Test fun 달러_환율이_없으면_계산하지_않는다() {
        assertNull(dutyFreeUsd(listOf(item(100.0, "JPY", 9.0)), mapOf("JPY" to 9.0)))
    }
}
