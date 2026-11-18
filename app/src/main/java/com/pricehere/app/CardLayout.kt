package com.pricehere.app

/**
 * 공유 카드의 배치 계산. 그림(Canvas)과 떼어 두어 휴대폰 없이 단위 테스트로 확인한다(v2.13 #10).
 * 글자 폭은 measure 함수로 받는다. 실제 그릴 때는 Paint.measureText를, 테스트에서는 가짜 측정을 넘긴다.
 */
object CardLayout {
    /** 저장 목록 카드 한 장에 넣는 항목 수. 넘치면 마지막 줄을 "외 N건"으로 쓴다. */
    const val SAVED_ROWS = 7
    const val MIN_HEIGHT = 1080
    const val ROW_H = 112f
    const val LIST_TOP = 250f

    data class Saved(
        /** 그리는 항목 수. */
        val shown: Int,
        /** "외 N건"의 N. 0이면 그 줄이 없다. */
        val rest: Int,
        val listBottom: Float,
        val totalTop: Float,
        val totalBottom: Float,
        val height: Int,
    )

    /** hasDutyFree: 달러 환율이 있어 면세 막대를 그리는지. 없으면 합계 카드가 낮아진다. */
    fun saved(count: Int, hasDutyFree: Boolean): Saved {
        require(count > 0)
        val shown = if (count > SAVED_ROWS) SAVED_ROWS - 1 else count
        val rest = count - shown
        val rows = shown + if (rest > 0) 1 else 0
        val listBottom = LIST_TOP + 28f + rows * ROW_H + 28f
        val totalTop = listBottom + 40f
        val totalBottom = totalTop + if (hasDutyFree) 400f else 240f
        val height = maxOf(MIN_HEIGHT, (totalBottom + 240f).toInt())
        return Saved(shown, rest, listBottom, totalTop, totalBottom, height)
    }

    /** 폭을 넘으면 뒤를 "…"로 줄인다. 메모처럼 글자 크기를 바꾸면 안 되는 글에 쓴다. */
    fun ellipsize(text: String, maxWidth: Float, measure: (String) -> Float): String {
        if (measure(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && measure(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    /** 폭을 넘으면 글자 크기를 6%씩 줄여 한 줄에 맞춘다. 20보다 작게는 줄이지 않는다. */
    fun fitSize(text: String, start: Float, maxWidth: Float, measure: (String, Float) -> Float): Float {
        var size = start
        while (measure(text, size) > maxWidth && size > 20f) size *= 0.94f
        return size
    }
}

/** 원화 결제(DCC) 때 더 나가는 원화 범위. 현지 통화 카드 결제액의 3~8%(금융감독원 안내 범위). */
fun dccExtraRange(cardWon: Double): Pair<Double, Double> = cardWon * 0.03 to cardWon * 0.08
