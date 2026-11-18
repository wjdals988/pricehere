package com.pricehere.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 공유 카드 배치 계산과 DCC 범위(v2.13 #10). 글자 폭은 "글자 수 × 크기 × 0.5"로 흉내 낸다. */
class CardLayoutTest {
    private val measure: (String) -> Float = { it.length * 40f * 0.5f }
    private val measureSized: (String, Float) -> Float = { t, size -> t.length * size * 0.5f }

    @Test fun `1건에 면세 막대가 없으면 최소 높이 1080`() {
        val l = CardLayout.saved(1, hasDutyFree = false)
        assertEquals(1, l.shown)
        assertEquals(0, l.rest)
        assertEquals(CardLayout.MIN_HEIGHT, l.height)
    }

    @Test fun `1건에 면세 막대가 있으면 내용 높이를 따른다`() {
        // 250 + 28 + 112 + 28 + 40 + 400 + 240(서명) = 1098
        assertEquals(1098, CardLayout.saved(1, hasDutyFree = true).height)
    }

    @Test fun `7건까지는 모두 그리고 외 N건 줄이 없다`() {
        val l = CardLayout.saved(7, hasDutyFree = true)
        assertEquals(7, l.shown)
        assertEquals(0, l.rest)
    }

    @Test fun `8건이면 6건 그리고 외 2건`() {
        val l = CardLayout.saved(8, hasDutyFree = true)
        assertEquals(6, l.shown)
        assertEquals(2, l.rest)
    }

    @Test fun `8건과 7건은 줄 수가 같아 높이도 같다`() {
        assertEquals(CardLayout.saved(7, true).height, CardLayout.saved(8, true).height)
    }

    @Test fun `달러 환율이 없으면 면세 막대만큼 합계 카드가 낮다`() {
        val with = CardLayout.saved(7, hasDutyFree = true)
        val without = CardLayout.saved(7, hasDutyFree = false)
        assertEquals(160f, with.totalBottom - without.totalBottom, 0.01f)
    }

    @Test fun `합계 카드는 목록 카드 아래 40 띄워 시작하고 서명 자리가 남는다`() {
        val l = CardLayout.saved(5, hasDutyFree = true)
        assertEquals(l.listBottom + 40f, l.totalTop, 0.01f)
        assertTrue(l.height - l.totalBottom >= 240f)
    }

    @Test fun `짧은 메모는 그대로`() {
        assertEquals("라멘", CardLayout.ellipsize("라멘", 300f, measure))
    }

    @Test fun `긴 메모는 폭 안에서 말줄임`() {
        val memo = "면세점에서 산 선물용 향수 세트와 포장 상자 두 개 그리고 쇼핑백까지 전부"
        val out = CardLayout.ellipsize(memo, 400f, measure)
        assertTrue(out.endsWith("…"))
        assertTrue(measure(out) <= 400f)
    }

    @Test fun `폭에 들어가는 합계는 글자 크기를 줄이지 않는다`() {
        assertEquals(120f, CardLayout.fitSize("382,586", 120f, 900f, measureSized), 0.01f)
    }

    @Test fun `아주 큰 합계는 한 줄에 들어가게 줄인다`() {
        val text = "9,999,999,999"
        val size = CardLayout.fitSize(text, 120f, 700f, measureSized)
        assertTrue(size < 120f)
        assertTrue(measureSized(text, size) <= 700f)
    }

    @Test fun `DCC 범위는 카드 금액의 3~8퍼센트`() {
        val (low, high) = dccExtraRange(93_011.0)
        assertEquals("2,790", format(low, 0))
        assertEquals("7,441", format(high, 0))
    }
}
