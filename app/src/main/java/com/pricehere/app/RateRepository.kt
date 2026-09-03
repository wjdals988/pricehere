package com.pricehere.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.abs

/** 나라별 팁 관행. 비율은 현지 통념이며 의무가 아니다. */
data class TipGuide(
    val flag: String,
    val suggested: List<Int>,
    val note: String,
)

/** 세금을 돌려받는 방식. 매장에서 바로 빼주느냐, 나중에 환급받느냐가 다르다. */
enum class RefundMode { IMMEDIATE, REFUND }

/**
 * 여행자 부가세 환급 정보. 미국처럼 제도가 없는 곳은 null로 둔다.
 * minimumPurchase 는 한 매장에서 하루에 사야 하는 최소 금액(해당 통화 기준), 0이면 하한 없음.
 * netFactor 는 대행사 수수료를 뺀 실수령 비율이다. 즉시 면세는 수수료가 없어 1.0이다.
 */
data class TaxRefund(
    val countryFlag: String,
    val countryName: String,
    val vatPercent: Double,
    val minimumPurchase: Double,
    val mode: RefundMode = RefundMode.REFUND,
    val netFactor: Double = AGENCY_NET_FACTOR,
    val note: String? = null,
) {
    /** 정가에 이미 포함된 부가세액. 정가 × VAT/(100+VAT). */
    fun vatIncludedIn(price: Double): Double = price * vatPercent / (100.0 + vatPercent)

    /** 수수료를 뺀 실수령 추정액. */
    fun estimatedRefund(price: Double): Double = vatIncludedIn(price) * netFactor

    val benefitLabel: String
        get() = when (mode) {
            RefundMode.IMMEDIATE -> "매장에서 바로 차감"
            RefundMode.REFUND -> "실수령 추정 (대행 수수료 ${(100 - netFactor * 100).toInt()}% 차감)"
        }

    companion object {
        /** Global Blue 등 환급 대행사가 약 1/4을 수수료로 가져간다는 통설을 반영한 값. */
        const val AGENCY_NET_FACTOR = 0.75

        /** 일본은 2026-11-01부터 매장 즉시 면세가 공항 환급으로 바뀐다. */
        private val JAPAN_REFORM: LocalDate = LocalDate.of(2026, 11, 1)

        fun japan(today: LocalDate = LocalDate.now(SEOUL_ZONE)): TaxRefund =
            if (today.isBefore(JAPAN_REFORM)) {
                TaxRefund(
                    countryFlag = "🇯🇵",
                    countryName = "일본",
                    vatPercent = 10.0,
                    minimumPurchase = 5_000.0,
                    mode = RefundMode.IMMEDIATE,
                    netFactor = 1.0,
                    note = "면세 매장에서 여권을 보여주면 계산할 때 소비세가 바로 빠집니다. " +
                        "2026년 11월 1일부터는 정가를 내고 출국 공항에서 환급받는 방식으로 바뀝니다.",
                )
            } else {
                TaxRefund(
                    countryFlag = "🇯🇵",
                    countryName = "일본",
                    vatPercent = 10.0,
                    minimumPurchase = 5_000.0,
                    mode = RefundMode.REFUND,
                    netFactor = 1.0,
                    note = "2026년 11월 개편된 제도입니다. 정가를 내고 출국 공항의 면세 단말기에서 " +
                        "여권과 물품을 제시해 환급받습니다. 짐을 부치기 전에 처리해야 합니다.",
                )
            }

        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

/**
 * 지원 통화. 통화를 늘리려면 이 enum에 한 줄만 추가하면 된다.
 * naverCode 는 네이버 마켓인덱스의 reutersCode 규칙(FX_{코드}KRW)을 따른다.
 *
 * cardFeePercent / cashSpreadPercent 는 모두 대표값 추정이다.
 * 카드는 국제브랜드(약 1.0%) + 국내 카드사 해외서비스수수료(약 0.2%)를 합쳐 잡았고,
 * 현찰은 은행 고시 스프레드 기준이라 통화별 편차가 매우 크다.
 */
enum class Currency(
    val code: String,
    val koreanName: String,
    val flag: String,
    /** 은행이 몇 단위로 고시하는지. 엔화는 100엔 단위라 100이다. */
    val quoteUnit: Int,
    /** 금액을 몇 자리까지 보여줄지. 엔화는 소수점을 쓰지 않는다. */
    val decimals: Int,
    val quickAmounts: List<Long>,
    val cardFeePercent: Double,
    val cashSpreadPercent: Double,
    val taxRefund: TaxRefund?,
    val tip: TipGuide,
) {
    USD(
        "USD", "미국 달러", "🇺🇸", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.75, null,
        TipGuide(
            "🇺🇸", listOf(15, 18, 20, 25),
            "미국은 서비스 팁이 사실상 필수입니다. 식당은 세전 금액의 15~20%가 일반적이고, " +
                "카페나 테이크아웃은 잔돈 정도면 충분합니다.",
        ),
    ),
    EUR(
        "EUR", "유로", "🇪🇺", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.97,
        TaxRefund("🇪🇸", "스페인", 21.0, 0.0),
        TipGuide(
            "🇪🇸", listOf(5, 10, 15),
            "스페인은 팁이 의무가 아닙니다. 잔돈을 남기거나 5~10% 정도면 충분하고, " +
                "계산서에 서비스료가 이미 포함된 경우도 있습니다.",
        ),
    ),
    JPY(
        "JPY", "일본 엔", "🇯🇵", 100, 0, listOf(500, 1_000, 5_000, 10_000, 50_000), 1.2, 1.75,
        TaxRefund.japan(),
        TipGuide(
            "🇯🇵", listOf(0, 5, 10),
            "일본은 팁 문화가 없습니다. 두고 나오면 오히려 돌려주려 하거나 당황하게 만들 수 있어서 " +
                "0%가 정답입니다. 참고용으로만 계산해 보세요.",
        ),
    ),
    CZK(
        "CZK", "체코 코루나", "🇨🇿", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 8.0,
        TaxRefund("🇨🇿", "체코", 21.0, 2001.0),
        TipGuide(
            "🇨🇿", listOf(5, 10, 15),
            "체코는 5~10%가 일반적입니다. 카드로 낼 때 팁을 따로 못 올리는 곳이 많아 " +
                "현금 잔돈을 남기는 방식이 흔합니다.",
        ),
    );

    val naverCode: String get() = "FX_${code}KRW"

    companion object {
        fun of(code: String?): Currency = entries.firstOrNull { it.code == code } ?: CZK
    }
}

enum class RateSource(val label: String, val short: String) {
    NAVER("하나은행 매매기준율", "하나은행"),
    ECB("ECB 기준환율", "ECB"),
    CACHE("오프라인 캐시", "오프라인"),
    ;

    companion object {
        fun of(name: String?): RateSource = entries.firstOrNull { it.name == name } ?: CACHE
    }
}

/** 전일 대비 등락. direction: 1 상승, -1 하락, 0 보합. */
data class Change(val amount: Double, val ratio: Double, val direction: Int)

/** rates: 해당 통화 1단위당 KRW. quotedAtMillis: 환율이 고시된 시각. */
data class Snapshot(
    val rates: Map<String, Double>,
    val source: RateSource,
    val quotedAtMillis: Long,
    val fetchedAtMillis: Long,
    val changes: Map<String, Change> = emptyMap(),
) {
    fun rateOf(currency: Currency): Double? = rates[currency.code]
    fun changeOf(currency: Currency): Change? = changes[currency.code]
}

class NoRatesException : Exception("환율을 가져오지 못했습니다")

class RateRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 1차 네이버(하나은행 매매기준율) → 2차 Frankfurter(ECB) → 3차 마지막 성공 캐시.
     *
     * 네이버 엔드포인트는 공개 문서가 없는 비공식 API다. Play 스토어 배포처럼
     * 약관 리스크를 피해야 하는 상황이 되면 아래 fetchNaver() 호출만 지우면 된다.
     */
    suspend fun refresh(): Snapshot = withContext(Dispatchers.IO) {
        val fresh = attempt { fetchNaver() } ?: attempt { fetchFrankfurter() }
        if (fresh != null) {
            save(fresh)
            return@withContext fresh
        }
        loadCache() ?: throw NoRatesException()
    }

    private inline fun attempt(block: () -> Snapshot): Snapshot? =
        try {
            block()
        } catch (e: Exception) {
            null
        }

    // ---------- 1차: 네이버 (하나은행 고시회차, delayTime 0) ----------

    private suspend fun fetchNaver(): Snapshot = coroutineScope {
        val results = Currency.entries
            .map { currency ->
                async(Dispatchers.IO) {
                    val body = httpGet("$NAVER_BASE/${currency.naverCode}")
                    currency to JSONObject(body).getJSONObject("exchangeInfo")
                }
            }
            .awaitAll() // 통화 하나라도 실패하면 기준 시각이 어긋나므로 전체를 실패 처리한다.

        val rates = HashMap<String, Double>(results.size)
        val changes = HashMap<String, Change>(results.size)
        var quotedAt = 0L
        for ((currency, info) in results) {
            // 네이버는 엔화를 100엔 단위로 준다. 내부에서는 항상 1단위당 원화로 맞춘다.
            val unit = currency.quoteUnit.toDouble()
            rates[currency.code] = info.getString("closePrice").replace(",", "").toDouble() / unit
            quotedAt = maxOf(quotedAt, parseNaverTime(info.optString("localTradedAt")))
            parseChange(info, unit)?.let { changes[currency.code] = it }
        }
        Snapshot(rates, RateSource.NAVER, quotedAt, System.currentTimeMillis(), changes)
    }

    /** 네이버는 전일 대비 등락폭/등락률을 함께 준다. 없으면 조용히 건너뛴다. */
    private fun parseChange(info: JSONObject, unit: Double): Change? {
        val raw = info.optString("fluctuations").replace(",", "").toDoubleOrNull() ?: return null
        val amount = raw / unit
        val ratio = info.optString("fluctuationsRatio").replace(",", "").toDoubleOrNull() ?: return null
        val direction = when (info.optJSONObject("fluctuationsType")?.optString("name")) {
            "RISING" -> 1
            "FALLING" -> -1
            else -> 0
        }
        return Change(abs(amount), abs(ratio), direction)
    }

    /** "2026-09-02T23:47:33+09:00" 형태. 날짜만 오는 변형에도 견디게 한다. */
    private fun parseNaverTime(raw: String): Long = try {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            LocalDate.parse(raw.take(10)).atTime(9, 0).atZone(SEOUL).toInstant().toEpochMilli()
        } catch (e2: Exception) {
            System.currentTimeMillis()
        }
    }

    // ---------- 2차: Frankfurter (ECB 공식, 무키) ----------

    private fun fetchFrankfurter(): Snapshot {
        val symbols = (Currency.entries.map { it.code } + "KRW").joinToString(",")
        val json = JSONObject(httpGet("$FRANKFURTER_BASE?base=EUR&symbols=$symbols"))
        val quoted = json.getJSONObject("rates")
        val krwPerEur = quoted.getDouble("KRW")

        // ECB는 EUR 기준만 주므로 KRW 교차환율로 환산한다.
        val rates = Currency.entries.associate { currency ->
            val perEur = if (currency.code == "EUR") 1.0 else quoted.getDouble(currency.code)
            currency.code to krwPerEur / perEur
        }

        // ECB 고시는 해당 일자 16:00 CET.
        val quotedAt = LocalDate.parse(json.getString("date"))
            .atTime(16, 0).atZone(FRANKFURT).toInstant().toEpochMilli()

        return Snapshot(rates, RateSource.ECB, quotedAt, System.currentTimeMillis())
    }

    // ---------- 3차: 로컬 캐시 ----------

    /** 앱의 3차 폴백. 오래된 값임을 분명히 하려고 출처를 CACHE로 덮는다. */
    fun loadCache(): Snapshot? = lastKnown()?.copy(source = RateSource.CACHE)

    /** 위젯용. 마지막으로 받아온 값을 원래 출처 그대로 돌려준다. */
    fun lastKnown(): Snapshot? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val ratesJson = json.getJSONObject("rates")
            val rates = ratesJson.keys().asSequence().associateWith { ratesJson.getDouble(it) }
            val changesJson = json.optJSONObject("changes")
            val changes = changesJson?.keys()?.asSequence()?.mapNotNull { code ->
                val c = changesJson.optJSONObject(code) ?: return@mapNotNull null
                code to Change(c.getDouble("a"), c.getDouble("r"), c.getInt("d"))
            }?.toMap().orEmpty()
            Snapshot(
                rates = rates,
                source = RateSource.of(json.optString("source")),
                quotedAtMillis = json.getLong("quotedAt"),
                fetchedAtMillis = json.getLong("fetchedAt"),
                changes = changes,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun save(snapshot: Snapshot) {
        val ratesJson = JSONObject()
        snapshot.rates.forEach { (code, rate) -> ratesJson.put(code, rate) }
        val changesJson = JSONObject()
        snapshot.changes.forEach { (code, c) ->
            changesJson.put(
                code,
                JSONObject().put("a", c.amount).put("r", c.ratio).put("d", c.direction),
            )
        }
        val json = JSONObject()
            .put("rates", ratesJson)
            .put("changes", changesJson)
            .put("source", snapshot.source.name)
            .put("quotedAt", snapshot.quotedAtMillis)
            .put("fetchedAt", snapshot.fetchedAtMillis)
        prefs.edit().putString(KEY_SNAPSHOT, json.toString()).apply()
    }

    // ---------- 마지막 선택 통화 ----------

    fun loadSelected(): Currency = Currency.of(prefs.getString(KEY_SELECTED, null))

    fun saveSelected(currency: Currency) {
        prefs.edit().putString(KEY_SELECTED, currency.code).apply()
    }

    // ---------- HTTP ----------

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val PREFS = "pricehere"
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_SELECTED = "selected"
        const val TIMEOUT_MS = 4_000
        const val NAVER_BASE = "https://api.stock.naver.com/marketindex/exchange"
        const val FRANKFURTER_BASE = "https://api.frankfurter.dev/v1/latest"
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        val FRANKFURT: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
