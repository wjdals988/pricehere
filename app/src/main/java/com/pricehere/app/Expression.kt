package com.pricehere.app

import java.math.BigDecimal
import java.math.MathContext

/**
 * 위젯 계산기의 사칙연산.
 *
 * 저장은 ASCII(`+ - * /`)로 하고 화면에는 `+ − × ÷`로 보여준다. 입력 문자열은
 * 앱 화면·빠른 계산 위젯과 공유하므로, 연산자가 없는 숫자도 그대로 평가돼야 한다.
 * `=` 키는 두지 않는다. 결과 원화가 입력하는 즉시 계속 갱신되기 때문이다.
 */
object Expression {
    const val OPS = "+-*/"

    private val PRETTY = mapOf('+' to "+", '-' to "−", '*' to "×", '/' to "÷")

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
