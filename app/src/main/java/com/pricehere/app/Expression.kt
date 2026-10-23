package com.pricehere.app

import java.math.BigDecimal
import java.math.MathContext

/**
 * 위젯 계산기와 앱 입력칸이 함께 쓰는 사칙연산.
 *
 * 저장은 ASCII(`+ - * /`)로 하고 화면에는 `+ − × ÷`로 보여준다. 입력 문자열은
 * 앱 화면·빠른 계산 위젯과 공유하므로, 연산자가 없는 숫자도 그대로 평가돼야 한다.
 * `=` 키는 두지 않는다. 결과 원화가 입력하는 즉시 계속 갱신되기 때문이다.
 */
object Expression {
    const val OPS = "+-*/"

    private val PRETTY = mapOf('+' to "+", '-' to "−", '*' to "×", '/' to "÷")

    /** 키보드마다 연산자 문자가 다르다. 보이는 기호로 들어와도 ASCII로 바꿔 받는다. */
    private val ALIASES = mapOf('×' to '*', '÷' to '/', '−' to '-', '–' to '-', 'x' to '*', 'X' to '*')

    /**
     * 키보드로 친 문자열을 식 규칙에 맞게 다듬는다. 규칙은 위젯 키패드(WidgetPrefs.applyKey)와 같다.
     * - 맨 앞 연산자는 버리고, 연산자를 연달아 치면 마지막 것으로 바꾼다
     * - 숫자 하나에 소수점은 하나, 숫자 없이 찍은 소수점은 "0."
     * - 연산자 앞에 남은 소수점은 지운다
     */
    fun sanitize(raw: String, max: Int): String = buildString {
        var dotInNumber = false
        for (c in raw) {
            val ch = ALIASES[c] ?: c
            when {
                ch.isDigit() -> append(ch)
                ch == '.' -> {
                    if (dotInNumber) continue
                    if (isEmpty() || last() in OPS) append('0')
                    append('.')
                    dotInNumber = true
                }
                ch in OPS -> {
                    // "3.+" → "3+", "3+×" → "3×"
                    while (isNotEmpty() && (last() in OPS || last() == '.')) setLength(length - 1)
                    if (isEmpty()) continue
                    append(ch)
                    dotInNumber = false
                }
            }
            if (length >= max) break
        }
    }.take(max)

    /** 입력 끝의 연산자·소수점은 무시하고 평가한다. 0으로 나누면 null. */
    fun evaluate(input: String): Double? {
        val tokens = tokenize(input.trimEnd { it in OPS || it == '.' }) ?: return null
        if (tokens.isEmpty()) return 0.0

        // 1단계: × ÷ 를 먼저 접는다.
        val terms = mutableListOf(tokens[0] as BigDecimal)
        val signs = mutableListOf<Char>()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i] as Char
            val n = tokens[i + 1] as BigDecimal
            when (op) {
                '*' -> terms[terms.lastIndex] = terms.last().multiply(n)
                '/' -> {
                    if (n.signum() == 0) return null
                    terms[terms.lastIndex] = terms.last().divide(n, MathContext.DECIMAL64)
                }
                else -> { signs += op; terms += n }
            }
            i += 2
        }
        // 2단계: + − 를 왼쪽부터.
        var acc = terms[0]
        signs.forEachIndexed { k, op ->
            acc = if (op == '+') acc.add(terms[k + 1]) else acc.subtract(terms[k + 1])
        }
        return acc.toDouble()
    }

    fun hasOperator(input: String): Boolean = input.any { it in OPS }

    /** "1000+25.5*2" → "1,000 + 25.5 × 2". 입력 중인 소수점도 그대로 보여준다. */
    fun pretty(input: String): String = buildString {
        var number = StringBuilder()
        fun flush() {
            if (number.isEmpty()) return
            val raw = number.toString()
            val dot = raw.indexOf('.')
            val whole = if (dot < 0) raw else raw.substring(0, dot)
            append(if (whole.isEmpty()) "0" else format(whole.toDouble(), 0))
            if (dot >= 0) append(raw.substring(dot))
            number = StringBuilder()
        }
        for (ch in input) {
            if (ch in OPS) {
                flush()
                append(' ').append(PRETTY.getValue(ch)).append(' ')
            } else {
                number.append(ch)
            }
        }
        flush()
    }

    /**
     * 입력칸용 표시 변환. pretty()와 같은 모양을 만들면서, 커서 위치를 원본과 맞추는
     * 대응표도 함께 돌려준다. map[i]는 원본 i번째 칸이 화면에서 몇 번째 칸인지.
     */
    fun prettyWithMap(input: String): Pair<String, IntArray> {
        val out = StringBuilder()
        val map = IntArray(input.length + 1)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch in OPS) {
                map[i] = out.length
                out.append(' ').append(PRETTY.getValue(ch)).append(' ')
                i++
                continue
            }
            var end = i
            while (end < input.length && input[end] !in OPS) end++
            val raw = input.substring(i, end)
            val dot = raw.indexOf('.')
            val whole = if (dot < 0) raw else raw.substring(0, dot)
            for (k in raw.indices) {
                if (k < whole.length) {
                    // 앞에서부터 k자리 뒤에 붙은 쉼표 개수만큼 밀린다.
                    val commasBefore = (whole.length - 1) / 3 - (whole.length - 1 - k) / 3
                    map[i + k] = out.length + k + commasBefore
                } else {
                    map[i + k] = out.length + k + (whole.length - 1).coerceAtLeast(0) / 3
                }
            }
            whole.forEachIndexed { k, c ->
                if (k > 0 && (whole.length - k) % 3 == 0) out.append(',')
                out.append(c)
            }
            if (dot >= 0) out.append(raw.substring(dot))
            i = end
        }
        map[input.length] = out.length
        return out.toString() to map
    }

    /** 숫자와 연산자가 번갈아 나오는 목록. 형식이 깨졌으면 null. */
    private fun tokenize(s: String): List<Any>? {
        if (s.isEmpty()) return emptyList()
        val out = mutableListOf<Any>()
        val number = StringBuilder()
        for (ch in s) {
            if (ch in OPS) {
                out += number.toString().toBigDecimalOrNull() ?: return null
                number.clear()
                out += ch
            } else {
                number.append(ch)
            }
        }
        out += number.toString().toBigDecimalOrNull() ?: return null
        return out
    }
}
