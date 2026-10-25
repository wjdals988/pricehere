package com.pricehere.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * "이거 얼마였지?" 를 위해 남겨두는 항목.
 * 외화 금액을 저장해 두고 볼 때마다 최신 환율로 다시 계산한다.
 * rateAtSave 를 함께 남겨야 저장 시점 대비 얼마나 움직였는지 보여줄 수 있다.
 */
data class SavedItem(
    val id: Long,
    val memo: String,
    val amount: Double,
    val currencyCode: String,
    /** 저장 시점의 매매기준율. 수수료는 mode로 따로 씌운다. */
    val rateAtSave: Double,
    val savedAtMillis: Long,
    /** 저장할 때 고른 결제 수단. v2.5.4 이전 항목은 기준율로 읽는다. */
    val mode: PriceMode = PriceMode.BASE,
) {
    val currency: Currency get() = Currency.of(currencyCode)
    val krwAtSave: Double get() = krwAt(rateAtSave)

    /** 매매기준율 base에 이 항목의 결제 수단 수수료를 씌운 원화 금액. */
    fun krwAt(base: Double): Double = amount * mode.apply(base, currency)
}

/**
 * 한국 입국 여행자 휴대품 기본 면세 한도(1인, 미화). 관세청 기준이며 2022년 9월 600달러에서 올랐다.
 * 술(2L·400달러)·담배·향수는 이 한도와 따로 센다.
 */
const val DUTY_FREE_USD = 800.0

/**
 * 저장 항목의 물품 가격 합계를 달러로 바꾼다. 세관은 물품 가격을 보므로 카드·현찰 수수료는 빼고
 * 매매기준율로 계산한다. 지금 환율이 없는 통화는 저장 당시 환율을 쓴다. 달러 환율이 없으면 null.
 */
fun dutyFreeUsd(items: List<SavedItem>, rates: Map<String, Double>): Double? {
    val usd = rates[Currency.USD.code]?.takeIf { it > 0 } ?: return null
    return items.sumOf { it.amount * (rates[it.currencyCode] ?: it.rateAtSave) } / usd
}

/** Room을 쓸 만한 규모가 아니라 SharedPreferences에 JSON 배열로 둔다. */
class SavedStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<SavedItem> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                SavedItem(
                    id = o.getLong("id"),
                    memo = o.optString("memo"),
                    amount = o.getDouble("amount"),
                    currencyCode = o.getString("currency"),
                    rateAtSave = o.getDouble("rate"),
                    savedAtMillis = o.getLong("savedAt"),
                    mode = PriceMode.of(o.optString("mode", null)),
                )
            }.sortedByDescending { it.savedAtMillis }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(
        memo: String,
        amount: Double,
        currency: Currency,
        rate: Double,
        mode: PriceMode = PriceMode.BASE,
    ): List<SavedItem> {
        val item = SavedItem(
            id = nextId(),
            memo = memo.trim().ifBlank { "이름 없는 항목" },
            amount = amount,
            currencyCode = currency.code,
            rateAtSave = rate,
            savedAtMillis = System.currentTimeMillis(),
            mode = mode,
        )
        return persist(load() + item)
    }

    fun remove(id: Long): List<SavedItem> = persist(load().filterNot { it.id == id })

    /** 되돌리기. 지운 항목을 id·저장 시각 그대로 다시 넣는다. 이미 있으면 그대로 둔다. */
    fun restore(item: SavedItem): List<SavedItem> {
        val items = load()
        return if (items.any { it.id == item.id }) items else persist(items + item)
    }

    fun clear(): List<SavedItem> = persist(emptyList())

    private fun persist(items: List<SavedItem>): List<SavedItem> {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("memo", item.memo)
                    .put("amount", item.amount)
                    .put("currency", item.currencyCode)
                    .put("rate", item.rateAtSave)
                    .put("savedAt", item.savedAtMillis)
                    .put("mode", item.mode.name)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
        return items.sortedByDescending { it.savedAtMillis }
    }

    /** 같은 밀리초에 두 번 저장해도 id가 겹치지 않게 한다. */
    private fun nextId(): Long {
        val seq = prefs.getLong(KEY_SEQ, 0L) + 1L
        prefs.edit().putLong(KEY_SEQ, seq).apply()
        return System.currentTimeMillis() * 1000 + (seq % 1000)
    }

    private companion object {
        const val PREFS = "pricehere_saved"
        const val KEY = "items"
        const val KEY_SEQ = "seq"
    }
}
