package com.pricehere.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceTextTest {
    private fun p(text: String, fallback: Currency = Currency.USD) = PriceText.parse(text, fallback)

    @Test fun 엔화_기호() = assertEquals(PriceText.Parsed(Currency.JPY, 12800.0, false), p("¥12,800"))
    @Test fun 엔화_円_뒤표기() = assertEquals(12800.0, p("12,800円（税込）")!!.amount, 0.0)
    @Test fun 엔화_만() = assertEquals(12000.0, p("1.2万円")!!.amount, 0.0)
    @Test fun 달러_소수() = assertEquals(PriceText.Parsed(Currency.USD, 1299.99, false), p("$1,299.99"))
    @Test fun 달러_점하나는_소수() = assertEquals(1.299, p("$1.299")!!.amount, 0.0)
    @Test fun 유로_유럽식() = assertEquals(PriceText.Parsed(Currency.EUR, 1234.56, false), p("1.234,56 €"))
    @Test fun 유로_점하나는_천단위() = assertEquals(1299.0, p("€1.299")!!.amount, 0.0)
    @Test fun 유로_쉼표_소수() = assertEquals(12.5, p("12,50 €")!!.amount, 0.0)
    @Test fun 코루나_공백_천단위() = assertEquals(PriceText.Parsed(Currency.CZK, 1290.0, false), p("1 290 Kč"))
    @Test fun 통화_없으면_기본값() = assertEquals(PriceText.Parsed(Currency.EUR, 4500.0, true), p("4500", Currency.EUR))
    @Test fun 숫자_없으면_null() = assertNull(p("가격 문의"))
    @Test fun 영은_null() = assertNull(p("¥0"))
}

class LocalCurrencyTest {
    @Test fun 나라_매핑() {
        assertEquals(Currency.JPY, LocalCurrency.forCountry("JP"))
        assertEquals(Currency.EUR, LocalCurrency.forCountry("es"))
        assertEquals(Currency.CZK, LocalCurrency.forCountry("cz"))
        assertEquals(Currency.USD, LocalCurrency.forCountry("us"))
        assertNull(LocalCurrency.forCountry("kr"))
        assertNull(LocalCurrency.forCountry("gb"))
    }

    @Test fun 처음_도착하면_바꾼다() = assertEquals(Currency.JPY, LocalCurrency.decide("jp", "kr", Currency.USD))
    @Test fun 같은_나라면_건드리지_않는다() = assertNull(LocalCurrency.decide("jp", "jp", Currency.USD))
    @Test fun 이미_그_통화면_null() = assertNull(LocalCurrency.decide("jp", "kr", Currency.JPY))
    @Test fun 빈_값은_판단하지_않는다() = assertNull(LocalCurrency.decide("", "kr", Currency.USD))
    @Test fun 한국은_그대로() = assertNull(LocalCurrency.decide("kr", "jp", Currency.JPY))
}

class ApproxWonTest {
    @Test fun 만원_미만은_null() = assertNull(approxWon(9_999.0))
    @Test fun 만_천() = assertEquals("약 3만 7천원", approxWon(36_725.0))
    @Test fun 딱_만() = assertEquals("약 3만원", approxWon(30_200.0))
    @Test fun 십만_경계() = assertEquals("약 10만원", approxWon(99_700.0))
    @Test fun 만_단위() = assertEquals("약 123만원", approxWon(1_234_567.0))
    @Test fun 천만_단위() = assertEquals("약 1,235만원", approxWon(12_345_678.0))
    @Test fun 억() = assertEquals("약 1억 2천만원", approxWon(123_456_789.0))
    @Test fun 딱_억() = assertEquals("약 3억원", approxWon(300_000_000.0))
}
