package com.pricehere.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedItemModeTest {

    private fun item(mode: PriceMode) = SavedItem(
        id = 1, memo = "t", amount = 100.0, currencyCode = "USD",
        rateAtSave = 1_400.0, savedAtMillis = 0, mode = mode,
    )

    @Test fun 기준율_항목은_기준율_그대로() {
        assertEquals(140_000.0, item(PriceMode.BASE).krwAtSave, 0.001)
    }

    @Test fun 카드_항목은_카드_수수료를_씌운다() {
        val expected = 100.0 * 1_400.0 * (1 + Currency.USD.cardFeePercent / 100.0)
        assertEquals(expected, item(PriceMode.CARD).krwAtSave, 0.001)
        assertEquals(expected / 1_400.0 * 1_500.0, item(PriceMode.CARD).krwAt(1_500.0), 0.001)
    }

    @Test fun 모르는_이름은_기준율() {
        assertEquals(PriceMode.BASE, PriceMode.of(null))
        assertEquals(PriceMode.BASE, PriceMode.of("OLD"))
        assertEquals(PriceMode.CASH, PriceMode.of("CASH"))
    }
}
