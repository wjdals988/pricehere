package com.pricehere.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class RatePoint(val date: LocalDate, val rate: Double)

enum class HistoryRange(val days: Long, val label: String) {
    WEEK(7, "7일"),
    MONTH(30, "30일"),
    QUARTER(90, "90일"),
}

/**
 * 환율 추이는 ECB 기준환율(Frankfurter)에서 받아온다.
 * 하나은행은 시계열을 공개하지 않으므로, 화면에서 출처가 다르다는 점을 반드시 밝힌다.
 * ECB는 영업일만 고시하므로 주말·공휴일 포인트는 아예 없다.
 */
object RateHistory {

    private const val BASE = "https://api.frankfurter.dev/v1"

    suspend fun fetch(currency: Currency, range: HistoryRange): List<RatePoint>? =
        withContext(Dispatchers.IO) {
            try {
                val end = LocalDate.now(TaxRefund.SEOUL_ZONE)
                val start = end.minusDays(range.days)
                val url = "$BASE/$start..$end?base=${currency.code}&symbols=KRW"

                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    setRequestProperty("Accept", "application/json")
                }
                val body = try {
                    if (conn.responseCode !in 200..299) return@withContext null
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }

                val rates = JSONObject(body).getJSONObject("rates")
                rates.keys().asSequence()
                    .mapNotNull { key ->
                        val krw = rates.optJSONObject(key)?.optDouble("KRW") ?: return@mapNotNull null
                        if (krw.isNaN()) null else RatePoint(LocalDate.parse(key), krw)
                    }
                    .sortedBy { it.date }
                    .toList()
                    .takeIf { it.size >= 2 }
            } catch (e: Exception) {
                null
            }
        }
}

/** 차트 아래에 함께 보여줄 요약값. */
data class HistorySummary(
    val low: Double,
    val high: Double,
    val average: Double,
    val changePercent: Double,
)

fun List<RatePoint>.summarize(): HistorySummary {
    val values = map { it.rate }
    val first = values.first()
    val last = values.last()
    return HistorySummary(
        low = values.min(),
        high = values.max(),
        average = values.average(),
        changePercent = if (first == 0.0) 0.0 else (last - first) / first * 100.0,
    )
}
