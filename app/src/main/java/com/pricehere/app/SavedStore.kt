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
    val rateAtSave: Double,
    val savedAtMillis: Long,
) {
    val currency: Currency get() = Currency.of(currencyCode)
    val krwAtSave: Double get() = amount * rateAtSave
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
                )
            }.sortedByDescending { it.savedAtMillis }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(memo: String, amount: Double, currency: Currency, rate: Double): List<SavedItem> {
        val item = SavedItem(
            id = nextId(),
            memo = memo.trim().ifBlank { "이름 없는 항목" },
            amount = amount,
            currencyCode = currency.code,
            rateAtSave = rate,
            savedAtMillis = System.currentTimeMillis(),
        )
        return persist(load() + item)
    }

    fun remove(id: Long): List<SavedItem> = persist(load().filterNot { it.id == id })

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
