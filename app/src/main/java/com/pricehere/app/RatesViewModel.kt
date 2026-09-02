package com.pricehere.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
            return when (priceMode) {
                PriceMode.BASE -> base
                PriceMode.CARD -> base * (1 + selected.cardFeePercent / 100.0)
                PriceMode.CASH -> base * (1 + selected.cashSpreadPercent / 100.0)
            }
        }

    val feePercent: Double
        get() = when (priceMode) {
            PriceMode.BASE -> 0.0
            PriceMode.CARD -> selected.cardFeePercent
            PriceMode.CASH -> selected.cashSpreadPercent
        }

    val amount: Double get() = input.toDoubleOrNull() ?: 0.0

    /** 변환 결과. 환율이 아직 없으면 null. */
    val result: Double?
        get() {
            val r = rate ?: return null
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

class RatesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RateRepository(app)
    private val store = SavedStore(app)
    private val settings = SettingsStore(app)

    private val _state = MutableStateFlow(
        UiState(
            selected = repo.loadSelected(),
            snapshot = repo.loadCache(),
            saved = store.load(),
            themeMode = settings.themeMode(),
        )
    )
    val state = _state.asStateFlow()

    private var lastFetchAtMillis = 0L

    init {
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
            _state.update { it.copy(outcome = RefreshOutcome(null, null, throttled = true)) }
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
                        outcome = RefreshOutcome(before, snapshot.rateOf(it.selected)),
                        error = if (snapshot.source == RateSource.CACHE) {
                            "네트워크에 연결할 수 없어 마지막으로 받아둔 환율을 쓰고 있습니다."
                        } else null,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = "환율을 가져오지 못했습니다. 네트워크를 확인해 주세요.")
                }
            }
        }
    }

    fun clearOutcome() {
        if (_state.value.outcome != null) _state.update { it.copy(outcome = null) }
    }

    /** 숫자와 소수점 1개만 남긴다. 콤마 삽입은 하지 않는다(표시 단계에서 처리). */
    fun onInputChange(raw: String) {
        var seenDot = false
        val cleaned = buildString {
            for (ch in raw) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && !seenDot -> {
                        seenDot = true
                        append('.')
                    }
                }
                if (length >= MAX_INPUT) break
            }
        }
        _state.update { it.copy(input = cleaned) }
    }

    fun setQuickAmount(value: Long) {
        _state.update { it.copy(input = value.toString()) }
    }

    fun select(currency: Currency) {
        if (currency == _state.value.selected) return
        repo.saveSelected(currency)
        _state.update { it.copy(selected = currency) }
    }

    fun setPriceMode(mode: PriceMode) {
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

    /** 저장은 항상 매매기준율 기준으로 남긴다. 수수료 모드는 화면에서 다시 씌운다. */
    fun saveCurrent(memo: String): Boolean {
        val s = _state.value
        val base = s.baseRate ?: return false
        val foreign = s.foreignAmount
        if (foreign <= 0.0) return false
        val items = store.add(memo, foreign, s.selected, base)
        _state.update { it.copy(saved = items) }
        return true
    }

    fun deleteSaved(id: Long) {
        _state.update { it.copy(saved = store.remove(id)) }
    }

    fun clearSaved() {
        _state.update { it.copy(saved = store.clear()) }
    }

    private companion object {
        const val MAX_INPUT = 15

        /** 비공식 API를 보호하기 위한 수동 새로고침 최소 간격. */
        const val MIN_REFRESH_INTERVAL_MS = 2_000L
    }
}
