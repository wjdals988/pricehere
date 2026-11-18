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

/**
 * 여행자 부가세 환급 정보. 미국처럼 제도가 없는 곳은 null로 둔다.
 * minimumPurchase 는 한 매장에서 하루에 사야 하는 최소 금액(해당 통화 기준), 0이면 하한 없음.
 * minimumIsPreTax 가 참이면 그 최소 금액을 세금 뺀 가격과 비교한다(일본 "5,000엔 이상, 세금 제외").
 * netFactor 는 대행사 수수료를 뺀 실수령 비율이다. 수수료를 따로 정할 수 없으면 1.0으로 두고 문구로 알린다.
 */
data class TaxRefund(
    val countryFlag: String,
    val countryName: String,
    val vatPercent: Double,
    val minimumPurchase: Double,
    val netFactor: Double = AGENCY_NET_FACTOR,
    val note: String? = null,
    val minimumIsPreTax: Boolean = false,
) {
    /** 정가에 이미 포함된 부가세액. 정가 × VAT/(100+VAT). */
    fun vatIncludedIn(price: Double): Double = price * vatPercent / (100.0 + vatPercent)

    /** 최소 구매 금액과 비교할 가격. 정가(세금 포함) price에서 필요하면 세금을 뺀다. */
    fun priceForMinimum(price: Double): Double =
        if (minimumIsPreTax) price - vatIncludedIn(price) else price

    /** 최소 구매 금액을 못 채웠으면 참. 하한이 없으면 늘 거짓. */
    fun belowMinimum(price: Double): Boolean =
        minimumPurchase > 0 && priceForMinimum(price) < minimumPurchase

    /** 수수료를 뺀 실수령 추정액. */
    fun estimatedRefund(price: Double): Double = vatIncludedIn(price) * netFactor

    val benefitLabel: String
        get() = if (netFactor >= 1.0) {
            // 일본처럼 수수료가 매장마다 달라 정할 수 없으면 "수수료 0% 차감"이라고 쓰지 않는다.
            "반출 확인 후 환급 예상"
        } else {
            "실수령 추정 (대행 수수료 ${(100 - netFactor * 100).toInt()}% 차감)"
        }

    companion object {
        /** Global Blue 등 환급 대행사가 약 1/4을 수수료로 가져간다는 통설을 반영한 값. */
        const val AGENCY_NET_FACTOR = 0.75

        /**
         * 일본 리펀드 방식(2026-11-01 시행). 공항 단말기는 반출만 확인하고, 돈은 구매한 매장이나
         * 환급 대행사가 나중에 돌려준다. 수수료는 매장마다 달라 정할 수 없어 netFactor 1.0으로 두고 문구로 알린다.
         * 근거: 일본 세관 Tax Exemption (Refund) Program 안내(2026-11-17 확인).
         */
        val JAPAN = TaxRefund(
            countryFlag = "🇯🇵",
            countryName = "일본",
            vatPercent = 10.0,
            minimumPurchase = 5_000.0,
            minimumIsPreTax = true,
            netFactor = 1.0,
            note = "정가로 결제한 뒤 출국 공항 단말기에서 반출 확인을 받습니다. " +
                "확인이 끝나면 구매한 매장이나 환급 대행사가 소비세를 돌려줍니다. " +
                "구매일부터 90일 안에 반출 확인을 받아야 합니다. 매장에 따라 수수료가 있을 수 있습니다.",
        )

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
    /**
     * 날짜를 받아 그날의 환급 제도를 돌려준다. 일본처럼 제도가 바뀌는 날이 있어서
     * 값으로 두면 enum이 처음 만들어질 때 한 번만 계산되어, 앱이 자정을 넘겨 살아 있으면 옛 제도가 남는다.
     */
    private val refundOn: (LocalDate) -> TaxRefund?,
    /** 팁 안내. v2.13에 늘린 통화처럼 자료가 없으면 null(환급도 '자료 없음'으로 표시). */
    val tip: TipGuide?,
    /** 통화 고르기 목록의 묶음 이름. */
    val region: String = "자주 쓰는 통화",
) {
    USD(
        "USD", "미국 달러", "🇺🇸", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.75, { null },
        TipGuide(
            "🇺🇸", listOf(15, 18, 20, 25),
            "미국은 서비스 팁이 사실상 필수입니다. 식당은 세전 금액의 15~20%가 일반적이고, " +
                "카페나 테이크아웃은 잔돈 정도면 충분합니다.",
        ),
    ),
    EUR(
        "EUR", "유로", "🇪🇺", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.97,
        { TaxRefund("🇪🇸", "스페인", 21.0, 0.0) },
        TipGuide(
            "🇪🇸", listOf(5, 10, 15),
            "스페인은 팁이 의무가 아닙니다. 잔돈을 남기거나 5~10% 정도면 충분하고, " +
                "계산서에 서비스료가 이미 포함된 경우도 있습니다.",
        ),
    ),
    JPY(
        "JPY", "일본 엔", "🇯🇵", 100, 0, listOf(500, 1_000, 5_000, 10_000, 50_000), 1.2, 1.75,
        { TaxRefund.JAPAN },
        TipGuide(
            "🇯🇵", listOf(0, 5, 10),
            "일본은 팁 문화가 없습니다. 두고 나오면 오히려 돌려주려 하거나 당황하게 만들 수 있어서 " +
                "0%가 정답입니다. 참고용으로만 계산해 보세요.",
        ),
    ),
    CZK(
        "CZK", "체코 코루나", "🇨🇿", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 8.0,
        { TaxRefund("🇨🇿", "체코", 21.0, 2001.0) },
        TipGuide(
            "🇨🇿", listOf(5, 10, 15),
            "체코는 5~10%가 일반적입니다. 카드로 낼 때 팁을 따로 못 올리는 곳이 많아 " +
                "현금 잔돈을 남기는 방식이 흔합니다.",
        ),
    ),

    // v2.13: 자주 쓰는 통화 고르기용. 네이버(하나은행) 고시 확인 완료(2026-11-18).
    // VND·IDR은 엔화처럼 100단위 고시. 현찰 스프레드는 대표 추정치이고, 환급·팁 자료는 없다.
    // ECB(2차 출처)에는 TWD·VND가 없어 네이버가 실패하면 마지막 저장 환율을 쓴다.
    CNY("CNY", "중국 위안", "🇨🇳", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 5.0, { null }, null, "아시아"),
    HKD("HKD", "홍콩 달러", "🇭🇰", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.97, { null }, null, "아시아"),
    TWD("TWD", "대만 달러", "🇹🇼", 1, 0, listOf(50, 100, 500, 1_000, 5_000), 1.2, 10.0, { null }, null, "아시아"),
    THB("THB", "태국 바트", "🇹🇭", 1, 2, listOf(20, 100, 500, 1_000, 5_000), 1.2, 6.0, { null }, null, "아시아"),
    VND("VND", "베트남 동", "🇻🇳", 100, 0, listOf(10_000, 50_000, 100_000, 500_000, 1_000_000), 1.2, 12.0, { null }, null, "아시아"),
    PHP("PHP", "필리핀 페소", "🇵🇭", 1, 2, listOf(50, 100, 500, 1_000, 5_000), 1.2, 8.0, { null }, null, "아시아"),
    SGD("SGD", "싱가포르 달러", "🇸🇬", 1, 2, listOf(5, 10, 50, 100, 500), 1.2, 1.99, { null }, null, "아시아"),
    MYR("MYR", "말레이시아 링깃", "🇲🇾", 1, 2, listOf(5, 10, 50, 100, 500), 1.2, 6.0, { null }, null, "아시아"),
    IDR("IDR", "인도네시아 루피아", "🇮🇩", 100, 0, listOf(10_000, 50_000, 100_000, 500_000, 1_000_000), 1.2, 12.0, { null }, null, "아시아"),
    AUD("AUD", "호주 달러", "🇦🇺", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.97, { null }, null, "오세아니아"),
    NZD("NZD", "뉴질랜드 달러", "🇳🇿", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.97, { null }, null, "오세아니아"),
    CAD("CAD", "캐나다 달러", "🇨🇦", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.97, { null }, null, "아메리카"),
    GBP("GBP", "영국 파운드", "🇬🇧", 1, 2, listOf(5, 10, 50, 100, 500), 1.2, 1.97, { null }, null, "유럽"),
    CHF("CHF", "스위스 프랑", "🇨🇭", 1, 2, listOf(5, 10, 50, 100, 500), 1.2, 1.97, { null }, null, "유럽"),
    HUF("HUF", "헝가리 포린트", "🇭🇺", 1, 0, listOf(500, 1_000, 5_000, 10_000, 50_000), 1.2, 8.0, { null }, null, "유럽"),
    TRY("TRY", "튀르키예 리라", "🇹🇷", 1, 2, listOf(50, 100, 500, 1_000, 5_000), 1.2, 9.0, { null }, null, "유럽");

    val naverCode: String get() = "FX_${code}KRW"

    /** 오늘(서울 기준)의 환급 제도. 읽을 때마다 날짜를 다시 본다. */
    val taxRefund: TaxRefund? get() = refundOn(LocalDate.now(TaxRefund.SEOUL_ZONE))

    fun taxRefundOn(date: LocalDate): TaxRefund? = refundOn(date)

    /** 추이 그래프 출처(ECB)에 있는 통화인지. TWD·VND는 없어 추이 칸을 '자료 없음'으로 막는다. */
    val hasHistory: Boolean get() = code !in NO_ECB

    /** 환급·팁 자료가 없는 통화. 도구 칸에 '자료 없음'으로 보인다. */
    val basicOnly: Boolean get() = tip == null

    companion object {
        fun of(code: String?): Currency = entries.firstOrNull { it.code == code } ?: CZK

        /** 홈 통화 칸 기본값. */
        val DEFAULT_FAVORITES: List<Currency> = listOf(USD, EUR, JPY, CZK)
        const val MAX_FAVORITES = 4
        private val NO_ECB = setOf("TWD", "VND")
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

class RateRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 받아올 통화: 홈 통화 칸 4개 + 지금 고른 통화 + 저장 항목의 통화 + USD(입국 면세 한도 계산).
     * 통화가 20종으로 늘어 모두 받으면 새로고침마다 요청이 20번이라, 쓰는 것만 받는다(v2.13).
     */
    private fun neededCurrencies(): List<Currency> =
        (SettingsStore(context).favorites() + loadSelected() + Currency.USD +
            SavedStore(context).load().map { it.currency }).distinct()

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
        val results = neededCurrencies()
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
        val needed = neededCurrencies()
        val symbols = (needed.map { it.code } + "KRW").joinToString(",")
        val json = JSONObject(httpGet("$FRANKFURTER_BASE?base=EUR&symbols=$symbols"))
        val quoted = json.getJSONObject("rates")
        val krwPerEur = quoted.getDouble("KRW")

        // ECB는 EUR 기준만 주므로 KRW 교차환율로 환산한다.
        // ECB에 없는 통화(TWD·VND)는 건너뛴다. 그 통화는 화면에서 마지막 저장 환율을 쓴다.
        val rates = needed.mapNotNull { currency ->
            val perEur = if (currency.code == "EUR") 1.0 else quoted.optDouble(currency.code, Double.NaN)
            if (perEur.isNaN() || perEur <= 0) null else currency.code to krwPerEur / perEur
        }.toMap()

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
