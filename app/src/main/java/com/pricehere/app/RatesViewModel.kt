package com.pricehere.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 새로고침을 눌렀을 때 실제로 무슨 일이 일어났는지.
 * "눌러도 값이 그대로인데 진짜 동작하는 건가"라는 의심을 없애려고 결과를 명시한다.
 */
data class RefreshOutcome(
    val before: Double?,
    val after: Double?,
    val throttled: Boolean = false,
) {
    val changed: Boolean get() = before != null && after != null && before != after
}

/** 어떤 값을 "실제로 낼 돈"으로 볼 것인지. */
enum class PriceMode(val label: String, val shortLabel: String) {
    BASE("매매기준율", "기준율"),
    CARD("카드 결제", "카드"),
    CASH("현찰 구입", "현찰"),
    ;

    /** 이 결제 수단의 수수료(%). 기준율이면 0. */
    fun feePercent(currency: Currency): Double = when (this) {
        BASE -> 0.0
        CARD -> currency.cardFeePercent
        CASH -> currency.cashSpreadPercent
    }

    /** 매매기준율에 이 결제 수단의 수수료를 씌운 환율. */
    fun apply(base: Double, currency: Currency): Double = base * (1 + feePercent(currency) / 100.0)

    companion object {
        fun of(name: String?): PriceMode = entries.firstOrNull { it.name == name } ?: BASE
    }
}

data class UiState(
    val selected: Currency = Currency.CZK,
    /** true면 외화 → 원화, false면 원화 → 외화. */
    val foreignToKrw: Boolean = true,
    /** 사용자가 실제로 입력한 원본 문자열. 콤마는 표시 단계에서만 붙인다. */
    val input: String = "",
    val snapshot: Snapshot? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val priceMode: PriceMode = PriceMode.BASE,
    val refundOpen: Boolean = false,
    val saved: List<SavedItem> = emptyList(),
    val outcome: RefreshOutcome? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val update: UpdateInfo? = null,
    val historyRange: HistoryRange = HistoryRange.MONTH,
    val history: List<RatePoint> = emptyList(),
    val historyLoading: Boolean = false,
    /** 복사·저장을 처음 몇 번만 안내한다. */
    val showHints: Boolean = false,
    /** 외부에서 금액이 들어올 때마다 1씩 오른다. 화면은 이 값이 바뀌면 홈 탭으로 간다. */
    val externalTick: Int = 0,
) {
    /** 설치된 버전보다 최신 릴리스가 있고, 사용자가 그 버전을 넘기지 않았을 때만 참. */
    val hasUpdate: Boolean get() = update != null
    /** 은행 고시 매매기준율 그대로. */
    val baseRate: Double? get() = snapshot?.rateOf(selected)

    val change: Change? get() = snapshot?.changeOf(selected)

    /** 선택한 결제 수단의 수수료까지 반영한 환율. */
    val rate: Double?
        get() {
            val base = baseRate ?: return null
            return priceMode.apply(base, selected)
        }

    val feePercent: Double get() = priceMode.feePercent(selected)

    /** 입력이 식이면 계산한 값. 0으로 나누면 null. */
    val amountOrNull: Double? get() = Expression.evaluate(input)
    val amount: Double get() = amountOrNull ?: 0.0
    val isExpression: Boolean get() = Expression.hasOperator(input)

    /** 변환 결과. 환율이 아직 없으면 null. */
    val result: Double?
        get() {
            val r = rate ?: return null
            val amount = amountOrNull ?: return null
            return if (foreignToKrw) amount * r else if (r == 0.0) null else amount / r
        }

    /** 방향과 무관하게 "외화가 얼마인지". 환급 계산의 기준이 된다. */
    val foreignAmount: Double get() = if (foreignToKrw) amount else (result ?: 0.0)

    val quickAmounts: List<Long>
        get() = if (foreignToKrw) {
            selected.quickAmounts
        } else {
            listOf(10_000, 50_000, 100_000, 500_000, 1_000_000)
        }

    /** 은행 고시 단위 기준으로 보여줄 환율 문구. 엔화는 100엔당으로 적는다. */
    val rateLabel: String?
        get() {
            val r = rate ?: return null
            val unit = selected.quoteUnit
            return "$unit ${selected.code} = ${formatFixed(r * unit, 2)}원"
        }

    val fromCurrencyCode: String get() = if (foreignToKrw) selected.code else "KRW"
    val toCurrencyCode: String get() = if (foreignToKrw) "KRW" else selected.code
    val fromLabel: String get() = if (foreignToKrw) selected.koreanName else "대한민국 원"
    val toLabel: String get() = if (foreignToKrw) "대한민국 원" else selected.koreanName
    val fromFlag: String get() = if (foreignToKrw) selected.flag else "🇰🇷"
    val toFlag: String get() = if (foreignToKrw) "🇰🇷" else selected.flag

    /** KRW는 소수점을 쓰지 않고, 외화는 통화별 자릿수를 따른다(엔화는 0자리). */
    val fromDecimals: Int get() = if (foreignToKrw) selected.decimals else 0
    val toDecimals: Int get() = if (foreignToKrw) 0 else selected.decimals
}

/**
 * handle: 시스템이 백그라운드에서 앱을 정리했다가 되살릴 때 입력값을 돌려받는 곳.
 * 카메라·지도를 오가는 사이 프로세스가 죽어도 쓰던 금액과 식이 남는다.
 * 사용자가 앱을 직접 닫으면 함께 비워지는 게 맞으므로 SharedPreferences에는 두지 않는다.
 */
class RatesViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repo = RateRepository(app)
    private val store = SavedStore(app)
    private val settings = SettingsStore(app)

    private val _state = MutableStateFlow(
        UiState(
            selected = repo.loadSelected(),
            snapshot = repo.loadCache(),
            saved = store.load(),
            themeMode = settings.themeMode(),
            priceMode = settings.priceMode(),
            showHints = settings.bumpLaunchCount() <= 3,
            input = handle[KEY_INPUT] ?: "",
            foreignToKrw = handle[KEY_DIRECTION] ?: true,
        )
    )
    val state = _state.asStateFlow()

    private var lastFetchAtMillis = 0L
    private var outcomeJob: Job? = null

    init {
        viewModelScope.launch {
            state.collect {
                handle[KEY_INPUT] = it.input
                handle[KEY_DIRECTION] = it.foreignToKrw
            }
        }
        refresh()
        checkForUpdate()
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    /** 최신 릴리스 확인. 실패하거나 이미 최신이면 아무것도 하지 않는다. */
    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = UpdateChecker.fetch() ?: return@launch
            if (!UpdateChecker.isNewerThan(info.latestVersion, BuildConfig.VERSION_NAME)) return@launch
            if (settings.dismissedVersion() == info.latestVersion) return@launch
            _state.update { it.copy(update = info) }
        }
    }

    /** 이 버전은 그만 알리기. */
    fun dismissUpdate() {
        val version = _state.value.update?.latestVersion ?: return
        settings.dismissVersion(version)
        _state.update { it.copy(update = null) }
    }

    /**
     * manual = 사용자가 새로고침 버튼을 누른 경우.
     * 비공식 API를 연타로 두들기지 않도록 최소 간격을 두고, 대신 왜 요청을 건너뛰었는지 알려준다.
     */
    fun refresh(manual: Boolean = false) {
        val current = _state.value
        if (current.loading) return

        val sinceLast = System.currentTimeMillis() - lastFetchAtMillis
        if (manual && current.snapshot != null && sinceLast < MIN_REFRESH_INTERVAL_MS) {
            showOutcome(RefreshOutcome(null, null, throttled = true))
            return
        }

        val before = current.snapshot?.rateOf(current.selected)
        _state.update { it.copy(loading = true, error = null, outcome = null) }
        viewModelScope.launch {
            try {
                val snapshot = repo.refresh()
                lastFetchAtMillis = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        snapshot = snapshot,
                        loading = false,
                        error = if (snapshot.source == RateSource.CACHE) {
                            "네트워크에 연결할 수 없어 마지막으로 받아둔 환율을 쓰고 있습니다."
                        } else null,
                    )
                }
                // 캐시로 떨어졌을 때 "변동 없음 · 최신"이라고 하면 거짓말이 된다. 오프라인은 버튼 색이 알린다.
                if (snapshot.source != RateSource.CACHE) {
                    showOutcome(RefreshOutcome(before, snapshot.rateOf(_state.value.selected)))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = "환율을 가져오지 못했습니다. 네트워크를 확인해 주세요.")
                }
            }
        }
    }

    /**
     * 새로고침 결과는 4초만 보여주고 출처·시각 줄로 돌아간다.
     * 화면이 아니라 여기서 지워야 환산·저장 화면 어디서 눌러도 똑같이 사라진다.
     */
    private fun showOutcome(outcome: RefreshOutcome) {
        outcomeJob?.cancel()
        _state.update { it.copy(outcome = outcome) }
        outcomeJob = viewModelScope.launch {
            delay(OUTCOME_SHOWN_MS)
            _state.update { it.copy(outcome = null) }
        }
    }

    /**
     * 환율 추이는 시트를 열 때만 받아온다. 홈 화면을 열자마자 부를 필요가 없고,
     * 통화나 기간이 바뀌는 사이에 응답이 도착하면 그 결과는 버린다.
     */
    fun loadHistory(range: HistoryRange = _state.value.historyRange) {
        val currency = _state.value.selected
        _state.update { it.copy(historyRange = range, historyLoading = true) }
        viewModelScope.launch {
            val points = RateHistory.fetch(currency, range)
            _state.update {
                if (it.selected != currency || it.historyRange != range) {
                    it
                } else {
                    it.copy(history = points.orEmpty(), historyLoading = false)
                }
            }
        }
    }

    /** 숫자와 소수점 1개만 남긴다. 콤마 삽입은 하지 않는다(표시 단계에서 처리). */
    fun onInputChange(raw: String) {
        _state.update { it.copy(input = Expression.sanitize(raw, MAX_INPUT)) }
    }

    /** 키보드 위 연산자 칩. 규칙은 키보드로 친 것과 같다. */
    fun appendOperator(op: Char) {
        _state.update { it.copy(input = Expression.sanitize(it.input + op, MAX_INPUT)) }
    }

    /**
     * 빠른 금액은 누를 때마다 더한다(10 → 10, 다시 10 → 20).
     * 소수점 입력과 섞여도 오차가 생기지 않게 BigDecimal로 더하고, 입력 길이 한도를 넘으면 무시한다.
     * 입력이 식이면 계산한 값에 더한다(20×3 에서 +10 → 70). 0으로 나눈 식이면 무시한다.
     */
    fun addQuickAmount(value: Long) {
        _state.update { s ->
            val current = s.input.toBigDecimalOrNull()
                ?: if (s.isExpression) {
                    s.amountOrNull?.toBigDecimal()?.setScale(s.fromDecimals, RoundingMode.HALF_UP)
                        ?: return@update s
                } else BigDecimal.ZERO
            val next = current.add(BigDecimal.valueOf(value)).stripTrailingZeros().toPlainString()
            if (next.length > MAX_INPUT) s else s.copy(input = next)
        }
    }

    fun clearInput() {
        _state.update { it.copy(input = "") }
    }

    /**
     * 해외 통신망에 처음 붙었을 때 그 나라 통화로 바꾼다. 바꿨으면 그 통화를 돌려준다.
     * 같은 나라 안에서는 다시 건드리지 않아, 사용자가 고른 통화가 유지된다.
     */
    fun syncLocalCurrency(country: String): Currency? {
        if (country.isBlank()) return null
        val target = LocalCurrency.decide(country, settings.lastNetworkCountry(), _state.value.selected)
        settings.setLastNetworkCountry(country)
        target ?: return null
        select(target)
        _state.update { it.copy(foreignToKrw = true) }
        return target
    }

    /**
     * 다른 앱의 텍스트 선택 메뉴에서 "앱에서 열기"로 넘어온 값.
     * fromKrw 면 amount 는 원화이고, 원 → 외화 방향으로 연다.
     */
    fun openExternal(currency: Currency, amount: Double, fromKrw: Boolean = false) {
        select(currency)
        val decimals = if (fromKrw) 0 else currency.decimals
        val input = BigDecimal(amount).setScale(decimals, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
        _state.update {
            it.copy(
                foreignToKrw = !fromKrw,
                input = Expression.sanitize(input, MAX_INPUT),
                externalTick = it.externalTick + 1,
            )
        }
    }

    /** 위젯 계산기에서 넘어올 때. 식(1280*3+500)을 계산하지 않고 그대로 이어받는다. */
    fun openInput(currency: Currency, input: String) {
        select(currency)
        _state.update {
            it.copy(
                foreignToKrw = true,
                input = Expression.sanitize(input, MAX_INPUT),
                externalTick = it.externalTick + 1,
            )
        }
    }

    fun select(currency: Currency) {
        if (currency == _state.value.selected) return
        repo.saveSelected(currency)
        // 통화가 바뀌면 이전 통화의 추이는 의미가 없다.
        _state.update { it.copy(selected = currency, history = emptyList()) }
    }

    /** "카드로 낼 거니까 카드"로 두고 쓰는 사람이 많아서, 앱을 다시 열어도 유지한다. */
    fun setPriceMode(mode: PriceMode) {
        settings.setPriceMode(mode)
        _state.update { it.copy(priceMode = mode) }
    }

    fun toggleRefund() {
        _state.update { it.copy(refundOpen = !it.refundOpen) }
    }

    /** 결과값을 입력값으로 옮기고 방향을 뒤집는다(왕복 일관성). */
    fun swap() {
        _state.update { s ->
            val carried = s.result
            val next = if (carried == null || carried == 0.0) {
                ""
            } else {
                BigDecimal(carried).setScale(s.toDecimals, RoundingMode.HALF_UP).toPlainString()
            }
            s.copy(foreignToKrw = !s.foreignToKrw, input = next)
        }
    }

    // ---------- 저장 목록 ----------

    /**
     * 환율은 매매기준율로 남기고, 그때 고른 결제 수단을 함께 남긴다.
     * 저장 화면은 최신 기준율에 그 결제 수단의 수수료를 다시 씌워 보여준다.
     */
    fun saveCurrent(memo: String): Boolean {
        val s = _state.value
        val base = s.baseRate ?: return false
        val foreign = s.foreignAmount
        if (foreign <= 0.0) return false
        val items = store.add(memo, foreign, s.selected, base, s.priceMode)
        _state.update { it.copy(saved = items) }
        return true
    }

    /** 지운 항목을 돌려준다. 화면이 "되돌리기"를 띄우는 동안 들고 있다가 restoreSaved 로 되살린다. */
    fun deleteSaved(id: Long): SavedItem? {
        val removed = _state.value.saved.firstOrNull { it.id == id }
        _state.update { it.copy(saved = store.remove(id)) }
        return removed
    }

    fun restoreSaved(item: SavedItem) {
        _state.update { it.copy(saved = store.restore(item)) }
    }

    fun clearSaved() {
        _state.update { it.copy(saved = store.clear()) }
    }

    private companion object {
        const val MAX_INPUT = 24

        /** 비공식 API를 보호하기 위한 수동 새로고침 최소 간격. */
        const val MIN_REFRESH_INTERVAL_MS = 2_000L

        const val OUTCOME_SHOWN_MS = 4_000L

        const val KEY_INPUT = "input"
        const val KEY_DIRECTION = "foreignToKrw"
    }
}
