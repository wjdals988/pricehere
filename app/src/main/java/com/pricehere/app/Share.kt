package com.pricehere.app

import android.content.Context
import android.content.Intent
import java.time.Instant

private const val RULE = "─────────────────"

/**
 * 카카오톡·메시지에 붙여도 읽히도록 일반 텍스트로만 꾸민다.
 * 마크다운이나 표는 대부분의 메신저에서 깨지므로 쓰지 않는다.
 */
fun buildConversionShareText(state: UiState): String {
    val result = state.result
    val rate = state.rate
    if (result == null || rate == null) {
        return "여긴얼마? $APP_VERSION — 아직 환율을 불러오지 못했습니다."
    }

    val fromFlag = state.fromFlag
    val toFlag = state.toFlag
    return buildString {
        appendLine("$fromFlag ${format(state.amount, state.fromDecimals)} ${state.fromCurrencyCode}")
        appendLine("   ↓")
        appendLine("$toFlag ${format(result, state.toDecimals)} ${state.toCurrencyCode}")
        appendLine()
        appendLine(RULE)
        appendLine("환율   ${state.rateLabel}")
        state.snapshot?.let {
            appendLine(
                "기준   ${it.source.label}" +
                    " (${CLOCK.format(Instant.ofEpochMilli(it.quotedAtMillis))} 고시)"
            )
        }
        if (state.priceMode != PriceMode.BASE) {
            appendLine("반영   ${state.priceMode.label} 수수료 ${formatFixed(state.feePercent, 2)}%")
        }
        appendLine(RULE)
        append("여긴얼마? $APP_VERSION · $DASHBOARD_HOST")
    }
}

fun buildSavedShareText(items: List<SavedItem>, snapshot: Snapshot?): String {
    if (items.isEmpty()) return "여긴얼마? $APP_VERSION — 저장한 금액이 없습니다."

    fun rateOf(item: SavedItem) = snapshot?.rates?.get(item.currencyCode) ?: item.rateAtSave

    val total = items.sumOf { it.amount * rateOf(it) }
    val totalAtSave = items.sumOf { it.krwAtSave }
    val diff = total - totalAtSave

    return buildString {
        appendLine("🏷 저장한 금액 ${items.size}개")
        appendLine()
        items.forEach { item ->
            val krw = item.amount * rateOf(item)
            appendLine(
                "· ${item.memo}" +
                    "\n  ${item.currency.flag} ${format(item.amount, item.currency.decimals)} " +
                    "${item.currencyCode} → ${format(krw, 0)}원"
            )
        }
        appendLine(RULE)
        appendLine("합계   ${format(total, 0)}원")
        if (kotlin.math.abs(diff) >= 1.0) {
            val arrow = if (diff > 0) "▲" else "▼"
            appendLine("변동   $arrow ${format(kotlin.math.abs(diff), 0)}원 (저장 시점 대비)")
        }
        snapshot?.let {
            appendLine(
                "기준   ${it.source.label}" +
                    " (${CLOCK.format(Instant.ofEpochMilli(it.quotedAtMillis))} 고시)"
            )
        }
        appendLine(RULE)
        append("여긴얼마? $APP_VERSION · $DASHBOARD_HOST")
    }
}

fun shareText(context: Context, body: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
}
