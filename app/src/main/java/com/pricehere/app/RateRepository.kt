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
    /** 홈 도구 칸에 "팁 문화 없음"으로 보일지. 0만 제안하는 나라는 자동으로 참. */
    val noCulture: Boolean = suggested == listOf(0),
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
 * (v2.14 재확인, 2026-11-18) 2025~2026년 Visa·Mastercard 인상 1차 출처는 없음. 실제 합계는 카드사·상품에 따라
 * 약 1.18~1.40%(Visa 일부 상품 1.1%, 해외서비스수수료 0.18~0.30%)라 1.2%는 범위의 아래쪽이다.
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
        "EUR", "유로", "🇪🇺", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.99,
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
            "🇯🇵", listOf(0, 5, 10), noCulture = true, note =
            "일본은 팁 문화가 없습니다. 두고 나오면 오히려 돌려주려 하거나 당황하게 만들 수 있어서 " +
                "0%가 정답입니다. 참고용으로만 계산해 보세요.",
        ),
    ),
    CZK(
        "CZK", "체코 코루나", "🇨🇿", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 8.5,
        { TaxRefund("🇨🇿", "체코", 21.0, 2001.0) },
        TipGuide(
            "🇨🇿", listOf(5, 10, 15),
            "체코는 5~10%가 일반적입니다. 카드로 낼 때 팁을 따로 못 올리는 곳이 많아 " +
                "현금 잔돈을 남기는 방식이 흔합니다.",
        ),
    ),

    // v2.13: 자주 쓰는 통화 고르기용. 네이버(하나은행) 고시 확인 완료(2026-11-18).
    // 환급·팁(v2.14): 각국 정부·세관 안내와 여행 자료로 2026-11-18 조사. 홍콩·말레이시아·뉴질랜드·캐나다·영국은
    // 관광객 환급 제도가 없거나(말레이시아는 2018년 종료로 추정) 폐지돼 null. 수수료를 정할 수 없는 곳은 netFactor 1.0 + 문구.
    // VND·IDR은 엔화처럼 100단위 고시.
    // 현찰 스프레드(v2.14): 하나은행 2026-11-18 19:13 고시(459회차) "현찰 사실 때 ÷ 매매기준율 − 1".
    // TRY는 하나은행이 현찰을 팔지 않아 고시가 없다(9%는 추정치로 남김).
    // ECB(2차 출처)에는 TWD·VND가 없어 네이버가 실패하면 마지막 저장 환율을 쓴다.
    CNY("CNY", "중국 위안", "🇨🇳", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 5.0, { TaxRefund("🇨🇳", "중국", 13.0, 200.0, netFactor = 0.78, note = "같은 환급 매장에서 같은 날 200위안 이상 사면 대상입니다. 출국할 때 세관 확인을 받고 공항 환급 창구에서 받습니다. 환급률 11%에서 수수료 2%를 빼면 실수령은 결제 금액의 약 9%입니다.") },
        TipGuide("🇨🇳", listOf(0), "중국은 식당에서 팁을 주는 문화가 거의 없습니다. 일부 고급 호텔이나 외국인 대상 업소만 서비스료를 붙입니다."), "아시아"),
    HKD("HKD", "홍콩 달러", "🇭🇰", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.97, { null },
        TipGuide("🇭🇰", listOf(0, 10), "홍콩은 식당 대부분이 계산서에 10% 서비스료를 붙여 추가 팁은 보통 필요 없습니다. 서비스료가 없으면 잔돈을 남기는 정도입니다."), "아시아"),
    TWD("TWD", "대만 달러", "🇹🇼", 1, 0, listOf(50, 100, 500, 1_000, 5_000), 1.2, 13.1, { TaxRefund("🇹🇼", "대만", 5.0, 2_000.0, netFactor = 1.0, note = "같은 매장에서 같은 날 2,000대만달러 이상 사면 대상입니다. 공항 환급 창구에서 받거나, 48,000대만달러 이하면 매장에서 바로 받는 소액 현장환급도 있습니다. 수수료가 차감될 수 있습니다.") },
        TipGuide("🇹🇼", listOf(0, 10), "대만은 일반 식당에서 팁을 주지 않습니다. 호텔이나 고급 식당은 10% 서비스료를 계산서에 넣는 경우가 많습니다."), "아시아"),
    THB("THB", "태국 바트", "🇹🇭", 1, 2, listOf(20, 100, 500, 1_000, 5_000), 1.2, 5.0, { TaxRefund("🇹🇭", "태국", 7.0, 2_000.0, netFactor = 1.0, note = "한 매장에서 하루 2,000바트 이상, 출국 때 합계 5,000바트 이상이어야 합니다. 매장에서 P.P.10 서류를 받고 체크인 전 세관 확인을 받은 뒤 출국장 환급 창구에서 받습니다. 구매 후 60일 안에 반출해야 하고, 수수료가 차감될 수 있습니다.") },
        TipGuide("🇹🇭", listOf(0, 5, 10), "태국은 팁이 의무가 아니지만 잔돈을 남기거나 소액을 주는 일이 흔합니다. 서비스료 10%가 붙은 식당이면 추가 팁은 필요 없습니다."), "아시아"),
    VND("VND", "베트남 동", "🇻🇳", 100, 0, listOf(10_000, 50_000, 100_000, 500_000, 1_000_000), 1.2, 11.8, { date -> TaxRefund("🇻🇳", "베트남", if (date.isBefore(LocalDate.of(2027, 1, 1))) 8.0 else 10.0, 2_000_000.0, netFactor = 0.85, note = "같은 매장에서 같은 날 2,000,000동 이상 사면 대상입니다. 출국할 때 세관 확인 후 공항 환급 창구에서 받습니다. 낸 부가세의 85%를 돌려받습니다(15%는 수수료). 부가세는 2026년 말까지 대부분 품목 8%, 이후 10%입니다.") },
        TipGuide("🇻🇳", listOf(0, 5, 10), "베트남은 팁이 의무가 아니지만 관광지와 대도시에서는 고마움의 표시로 주는 경우가 늘었습니다. 서비스료가 포함됐는지 계산서를 확인하세요."), "아시아"),
    PHP("PHP", "필리핀 페소", "🇵🇭", 1, 2, listOf(50, 100, 500, 1_000, 5_000), 1.2, 10.0, { TaxRefund("🇵🇭", "필리핀", 12.0, 3_000.0, netFactor = 1.0, note = "법(RA 12079)으로는 인증 매장 3,000페소 이상 구매에 환급이 정해져 있지만, 2026년 11월 기준 공항 환급 창구가 실제로 운영되는지는 확인되지 않았습니다.") },
        TipGuide("🇵🇭", listOf(0, 5, 10), "필리핀은 식당에 10% 서비스료가 붙는 경우가 많고, 없으면 잔돈이나 소액을 남기는 편입니다."), "아시아"),
    SGD("SGD", "싱가포르 달러", "🇸🇬", 1, 2, listOf(5, 10, 50, 100, 500), 1.2, 1.99, { TaxRefund("🇸🇬", "싱가포르", 9.0, 100.0, netFactor = 1.0, note = "같은 매장 영수증 최대 3장을 합쳐 100싱가포르달러 이상이면 대상입니다. 매장에서 eTRS 등록 후 공항 eTRS 키오스크에서 신청합니다. 운영사 수수료가 차감될 수 있습니다.") },
        TipGuide("🇸🇬", listOf(0), "싱가포르는 팁 문화가 거의 없습니다. 식당 대부분이 10% 서비스료를 계산서에 넣습니다."), "아시아"),
    MYR("MYR", "말레이시아 링깃", "🇲🇾", 1, 2, listOf(5, 10, 50, 100, 500), 1.2, 7.4, { null },
        TipGuide("🇲🇾", listOf(0), "말레이시아는 팁 문화가 거의 없습니다. 식당에는 서비스료가 붙는 경우가 많아 추가 팁은 보통 필요 없습니다."), "아시아"),
    IDR("IDR", "인도네시아 루피아", "🇮🇩", 100, 0, listOf(10_000, 50_000, 100_000, 500_000, 1_000_000), 1.2, 10.0, { TaxRefund("🇮🇩", "인도네시아", 11.0, 505_000.0, netFactor = 1.0, note = "영수증 한 장의 부가세가 50,000루피아 이상(구매 약 505,000루피아 이상)이고, 환급할 부가세 합계가 500,000루피아 이상이어야 합니다. 출국 당일 지정 공항 환급 창구에서만 받습니다.") },
        TipGuide("🇮🇩", listOf(0, 5, 10), "인도네시아는 팁이 의무가 아니지만 관광지에서는 5~10% 정도를 주는 일이 있습니다. 서비스료가 포함된 식당이 많습니다."), "아시아"),
    AUD("AUD", "호주 달러", "🇦🇺", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.97, { TaxRefund("🇦🇺", "호주", 10.0, 300.0, netFactor = 1.0, note = "같은 사업자에서 합계 300호주달러 이상, 출국 전 60일 안에 산 물건이 대상입니다. 공항 출국장 TRS 창구에서 영수증 원본과 물건을 보여 주고 카드나 계좌로 받습니다.") },
        TipGuide("🇦🇺", listOf(0, 10), "호주는 팁이 의무가 아닙니다. 서비스가 좋았을 때 금액을 올리거나 10% 정도를 남기는 경우가 있습니다."), "오세아니아"),
    NZD("NZD", "뉴질랜드 달러", "🇳🇿", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.97, { null },
        TipGuide("🇳🇿", listOf(0, 10), "뉴질랜드는 팁 문화가 거의 없습니다. 서비스가 특별히 좋았을 때 10% 정도를 남기기도 합니다."), "오세아니아"),
    CAD("CAD", "캐나다 달러", "🇨🇦", 1, 2, listOf(10, 50, 100, 500, 1_000), 1.2, 1.97, { null },
        TipGuide("🇨🇦", listOf(15, 18, 20), "캐나다는 식당 팁이 사실상 기본입니다. 세전 금액의 15~20%가 일반적입니다."), "아메리카"),
    GBP("GBP", "영국 파운드", "🇬🇧", 1, 2, listOf(5, 10, 50, 100, 500), 1.2, 1.97, { null },
        TipGuide("🇬🇧", listOf(0, 10, 15), "영국은 계산서에 10~12.5% 서비스료가 붙는 식당이 많고, 이때 추가 팁은 필요 없습니다. 서비스료가 없으면 10~15%가 일반적입니다."), "유럽"),
    CHF("CHF", "스위스 프랑", "🇨🇭", 1, 2, listOf(5, 10, 50, 100, 500), 1.2, 1.97, { TaxRefund("🇨🇭", "스위스", 8.1, 300.0, netFactor = TaxRefund.AGENCY_NET_FACTOR, note = "300스위스프랑을 넘게 사면 대상입니다. 매장에서 Tax Free 서류를 받아 출국할 때 세관 도장을 받고 환급 사무소에서 받습니다. 구매 후 30일 안에 반출해야 합니다.") },
        TipGuide("🇨🇭", listOf(0, 5, 10), "스위스는 서비스료가 가격에 포함돼 팁이 의무가 아닙니다. 금액을 올려 내거나 5~10% 정도를 남기기도 합니다."), "유럽"),
    HUF("HUF", "헝가리 포린트", "🇭🇺", 1, 0, listOf(500, 1_000, 5_000, 10_000, 50_000), 1.2, 9.3, { TaxRefund("🇭🇺", "헝가리", 27.0, 68_000.0, netFactor = TaxRefund.AGENCY_NET_FACTOR, note = "68,000포린트(약 175유로)를 넘게 사면 대상입니다. 90일 안에 EU 밖으로 반출하면서 세관 확인을 받은 뒤 환급됩니다.") },
        TipGuide("🇭🇺", listOf(0, 10, 15), "헝가리는 계산서에 10~15% 서비스료가 붙는 식당이 많고, 이때 추가 팁은 필요 없습니다. 없으면 10~15%가 일반적입니다."), "유럽"),
    TRY("TRY", "튀르키예 리라", "🇹🇷", 1, 2, listOf(50, 100, 500, 1_000, 5_000), 1.2, 9.0, { TaxRefund("🇹🇷", "튀르키예", 20.0, 1_000.0, netFactor = TaxRefund.AGENCY_NET_FACTOR, note = "Tax Free 매장에서 서류를 받아 3개월 안에 출국하면서 공항 세관 확인을 받고 환급 창구에서 받습니다. 최소 금액 1,000리라는 공식 고시로 확인되지 않았습니다.") },
        TipGuide("🇹🇷", listOf(5, 10, 15), "튀르키예는 식당에서 5~10% 정도를 주는 것이 일반적이고, 서비스가 좋으면 더 주기도 합니다."), "유럽");

    val naverCode: String get() = "FX_${code}KRW"

    /** 오늘(서울 기준)의 환급 제도. 읽을 때마다 날짜를 다시 본다. */
    val taxRefund: TaxRefund? get() = refundOn(LocalDate.now(TaxRefund.SEOUL_ZONE))

    fun taxRefundOn(date: LocalDate): TaxRefund? = refundOn(date)

    /** 추이 그래프 출처(ECB)에 있는 통화인지. TWD·VND는 없어 추이 칸을 '자료 없음'으로 막는다. */
    val hasHistory: Boolean get() = code !in NO_ECB

    /** 환급·팁 자료가 없는 통화. 도구 칸에 '자료 없음'으로 보인다. v2.14부터 모든 통화에 자료가 있다. */
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
