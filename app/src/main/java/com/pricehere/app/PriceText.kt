package com.pricehere.app

import java.util.Locale
import kotlin.math.roundToLong

/**
 * 앱 밖에서 들어온 값을 이 앱의 통화·금액으로 바꾸는 규칙들.
 * 안드로이드 API에 기대지 않는 순수 함수만 둬서 단위 테스트로 고정한다.
 */
object LocalCurrency {
    /** 유로를 법정 통화로 쓰는 나라. 불가리아는 2026-01-01부터. */
    private val EURO = setOf(
        "at", "be", "bg", "cy", "de", "ee", "es", "fi", "fr", "gr", "hr", "ie", "it",
        "lt", "lu", "lv", "mt", "nl", "pt", "si", "sk",
        "ad", "mc", "me", "sm", "va", "xk",
    )
    private val DOLLAR = setOf("us", "pr", "gu", "vi", "as", "mp", "ec", "sv", "pa")

    fun forCountry(iso: String): Currency? = when (val c = iso.lowercase(Locale.ROOT)) {
        "jp" -> Currency.JPY
        "cz" -> Currency.CZK
        in EURO -> Currency.EUR
        in DOLLAR -> Currency.USD
        else -> null
    }

    /**
     * 통신망 국가가 "바뀌었을 때만" 통화를 고른다. 같은 나라에 머무는 동안
     * 사용자가 직접 바꾼 통화를 앱을 열 때마다 되돌리면 안 되기 때문이다.
     * 빈 값(와이파이 전용, 비행기 모드)은 판단하지 않는다.
     *
     * @return 새로 고를 통화. 바꿀 것이 없으면 null.
     */
    fun decide(country: String, lastCountry: String?, selected: Currency): Currency? {
        if (country.isBlank() || country.equals(lastCountry, ignoreCase = true)) return null
        val target = forCountry(country) ?: return null
        return target.takeIf { it != selected }
    }
}

/** 다른 앱에서 선택한 "¥12,800" 같은 글자에서 통화와 금액을 뽑는다. */
object PriceText {
    /**
     * fromKrw 가 true면 amount 는 원화이고, currency 는 그 원화를 바꿔 볼 외화(마지막에 고른 통화)다.
     * currencyGuessed 는 기호가 없어 통화를 추측했다는 뜻이다. 화면에서 확인을 요청한다.
     */
    data class Parsed(
        val currency: Currency,
        val amount: Double,
        val currencyGuessed: Boolean,
        val fromKrw: Boolean = false,
    )

    private val MARKERS: List<Pair<Regex, Currency>> = listOf(
        Regex("""US\$|USD|\$|달러|dollars?""", RegexOption.IGNORE_CASE) to Currency.USD,
        Regex("""€|EUR|유로|euros?""", RegexOption.IGNORE_CASE) to Currency.EUR,
        // ¥는 위안도 같은 기호지만 이 앱은 위안을 다루지 않는다.
        Regex("""¥|￥|円|JPY|엔|yen""", RegexOption.IGNORE_CASE) to Currency.JPY,
        Regex("""Kč|CZK|코루나|korun""", RegexOption.IGNORE_CASE) to Currency.CZK,
    )

    /** 원화. "회원가"처럼 단어 속 "원"은 통화가 아니므로 숫자 바로 뒤의 "원"만 본다. 외화 기호가 있으면 그쪽이 먼저다. */
    private val KRW = Regex("""₩|￦|KRW|(?<=\d)\s?원""", RegexOption.IGNORE_CASE)

    /**
     * 공백은 뒤에 정확히 세 자리가 올 때만 천 단위 구분자로 본다(1 290 Kč, 12 800 円).
     * 아무 공백이나 숫자로 삼키면 "¥1,280 2個"가 "1,280 2"로 붙어 1.2802가 된다.
     */
    private val NUMBER = Regex("""\d{1,3}(?:[ \u00A0\u202F\u2009']\d{3})+(?:[.,]\d+)?|\d[\d.,']*""")

    fun parse(text: String, fallback: Currency): Parsed? {
        val src = text.take(200)
        val hit = MARKERS.firstNotNullOfOrNull { (re, c) -> re.find(src)?.let { it to c } }
        val krw = if (hit == null) KRW.find(src) else null
        val marker = hit?.first ?: krw

        val numbers = NUMBER.findAll(src).toList()
        if (numbers.isEmpty()) return null
        // 기호가 있으면 그 기호에 가장 가까운 숫자를 고른다. "2개 ¥1,000"에서 2가 아니라 1,000.
        val number = marker?.let { m -> numbers.minBy { gap(it.range, m.range) } } ?: numbers.first()

        val currency = hit?.second ?: fallback
        // 원화는 소수점을 쓰지 않으니 점 하나도 천 단위다(12.000원).
        val dotIsGrouping = krw != null || currency == Currency.EUR || currency == Currency.CZK
        var amount = toNumber(number.value.trim().trimEnd('.', ',', '\''), dotIsGrouping) ?: return null
        // 일본 가격표의 "1.2万円" 같은 표기.
        if (src.substring(number.range.last + 1).trimStart().startsWith("万")) amount *= 10_000
        if (amount <= 0.0) return null
        return Parsed(
            currency = currency,
            amount = amount,
            currencyGuessed = marker == null,
            fromKrw = krw != null,
        )
    }

    /** 두 구간 사이의 글자 수. 겹치면 0. */
    private fun gap(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }

    /**
     * 나라마다 천 단위·소수 구분자가 반대다(1,234.56 / 1.234,56 / 1 234,56).
     * 둘 다 있으면 마지막에 나온 것이 소수점이다. 하나만 있으면 뒤 자릿수로 판단한다.
     */
    private fun toNumber(raw: String, dotIsGrouping: Boolean): Double? {
        val s = raw.replace(Regex("""[\s\u00A0\u202F\u2009']"""), "")
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimal = if (lastDot > lastComma) '.' else ','
                val group = if (decimal == '.') ',' else '.'
                s.replace(group.toString(), "").replace(decimal, '.')
            }
            lastComma >= 0 -> if (isGrouping(s, ',')) s.replace(",", "") else s.replace(',', '.')
            lastDot >= 0 -> {
                // "1.299"는 미국식이면 1.299달러, 유럽식이면 1,299유로다. 통화로 가른다.
                if (isGrouping(s, '.') && (dotIsGrouping || s.count { it == '.' } > 1)) s.replace(".", "") else s
            }
            else -> s
        }
        return normalized.toDoubleOrNull()
    }

    /** 구분자 뒤가 모두 정확히 세 자리면 천 단위 구분자로 본다. */
    private fun isGrouping(s: String, sep: Char): Boolean =
        s.split(sep).drop(1).all { it.length == 3 }
}

/**
 * 원화 금액을 한국어로 어림한다. 36,725 → "약 3만 7천원".
 * 만 원 미만은 숫자 그대로가 더 읽기 쉬워서 null.
 */
fun approxWon(won: Double): String? {
    if (won.isNaN() || won < 10_000) return null
    return when {
        won < 100_000 -> {
            val k = (won / 1_000).roundToLong()
            val man = k / 10
            val cheon = k % 10
            if (cheon == 0L) "약 ${man}만원" else "약 ${man}만 ${cheon}천원"
        }
        won < 100_000_000 -> {
            val man = (won / 10_000).roundToLong()
            if (man >= 10_000) "약 1억원" else "약 ${format(man.toDouble(), 0)}만원"
        }
        else -> {
            val t = (won / 10_000_000).roundToLong()
            val eok = t / 10
            val cheonman = t % 10
            val head = "약 ${format(eok.toDouble(), 0)}억"
            if (cheonman == 0L) "${head}원" else "$head ${cheonman}천만원"
        }
    }
}
