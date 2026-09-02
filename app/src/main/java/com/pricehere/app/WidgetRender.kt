package com.pricehere.app

import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import java.time.Instant

private val CODE_IDS = intArrayOf(R.id.w_code_0, R.id.w_code_1, R.id.w_code_2, R.id.w_code_3)
private val RATE_IDS = intArrayOf(R.id.w_rate_0, R.id.w_rate_1, R.id.w_rate_2, R.id.w_rate_3)
private val CHG_IDS = intArrayOf(R.id.w_chg_0, R.id.w_chg_1, R.id.w_chg_2, R.id.w_chg_3)
private val CHIP_IDS = intArrayOf(
    R.id.w_chip_0, R.id.w_chip_1, R.id.w_chip_2,
    R.id.w_chip_3, R.id.w_chip_4, R.id.w_chip_5,
)
private val CURRENCY_IDS = mapOf(
    Currency.USD to R.id.w_cur_usd,
    Currency.EUR to R.id.w_cur_eur,
    Currency.JPY to R.id.w_cur_jpy,
    Currency.CZK to R.id.w_cur_czk,
)

// ---------------------------------------------------------------- 4×2 환율 보기

fun renderRateWidget(context: Context): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_rates)
    val snapshot = RateRepository(context).lastKnown()

    views.setTextViewText(
        R.id.w_time,
        snapshot?.let {
            "${it.source.short} · ${CLOCK.format(Instant.ofEpochMilli(it.quotedAtMillis))}"
        } ?: "환율을 불러오는 중",
    )

    Currency.entries.forEachIndexed { i, currency ->
        val unit = currency.quoteUnit
        views.setTextViewText(
            CODE_IDS[i],
            if (unit == 1) "${currency.flag} ${currency.code}" else "${currency.flag} ${unit}${currency.code}",
        )
        views.setTextViewText(
            RATE_IDS[i],
            snapshot?.rateOf(currency)?.let { formatFixed(it * unit, 2) } ?: "—",
        )
        bindChange(context, views, CHG_IDS[i], snapshot?.changeOf(currency), unit)
    }

    views.setOnClickPendingIntent(
        R.id.w_refresh,
        WidgetHub.broadcast(context, RateWidgetProvider::class.java, WidgetHub.ACTION_REFRESH),
    )
    views.setOnClickPendingIntent(R.id.w_root, WidgetHub.openApp(context))
    return views
}

private fun bindChange(
    context: Context,
    views: RemoteViews,
    id: Int,
    change: Change?,
    unit: Int,
) {
    if (change == null || change.direction == 0) {
        views.setTextViewText(id, "보합")
        views.setTextColor(id, context.getColor(R.color.w_muted))
        return
    }
    val rising = change.direction > 0
    views.setTextViewText(
        id,
        "${if (rising) "▲" else "▼"} ${formatFixed(change.amount * unit, 2)}",
    )
    views.setTextColor(id, context.getColor(if (rising) R.color.w_up else R.color.w_down))
}

// ---------------------------------------------------------------- 4×3 빠른 계산

fun renderQuickWidget(context: Context): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_quick)
    val provider = QuickWidgetProvider::class.java
    val state = readState(context)

    bindHeader(context, views, provider, state)
    bindHero(context, views, state)

    state.currency.quickAmounts.forEachIndexed { i, value ->
        views.setTextViewText(CHIP_IDS[i], compact(value))
        views.setOnClickPendingIntent(
            CHIP_IDS[i],
            WidgetHub.broadcast(context, provider, WidgetHub.ACTION_AMOUNT, value.toString()),
        )
    }
    views.setTextViewText(CHIP_IDS[5], "C")
    views.setOnClickPendingIntent(
        CHIP_IDS[5],
        WidgetHub.broadcast(context, provider, WidgetHub.ACTION_AMOUNT, ""),
    )
    return views
}

/** 1,000,000처럼 긴 숫자는 위젯 칩에 안 들어간다. */
private fun compact(value: Long): String = when {
    value >= 10_000 -> "${value / 10_000}만"
    else -> format(value.toDouble(), 0)
}

// ---------------------------------------------------------------- 4×5 키패드

private val DIGIT_KEYS = mapOf(
    R.id.k0 to "0", R.id.k1 to "1", R.id.k2 to "2", R.id.k3 to "3", R.id.k4 to "4",
    R.id.k5 to "5", R.id.k6 to "6", R.id.k7 to "7", R.id.k8 to "8", R.id.k9 to "9",
    R.id.k00 to "00", R.id.k000 to "000", R.id.kdot to ".",
    R.id.kdel to "DEL", R.id.kclr to "CLR",
)

fun renderKeypadWidget(context: Context): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_keypad)
    val provider = KeypadWidgetProvider::class.java
    val state = readState(context)

    bindHeader(context, views, provider, state)
    bindHero(context, views, state)

    DIGIT_KEYS.forEach { (id, key) ->
        views.setOnClickPendingIntent(
            id,
            WidgetHub.broadcast(context, provider, WidgetHub.ACTION_KEY, key),
        )
    }
    views.setOnClickPendingIntent(R.id.kopen, WidgetHub.openApp(context))
    return views
}

// ---------------------------------------------------------------- 공통

/**
 * 위젯은 방향을 바꾸지 않는다. 현지 통화를 넣으면 원화가 나오는 한 방향만 지원한다.
 * 홈 화면에서 방향이 뒤집힌 채로 남아 있으면 "왜 이상한 값이 뜨지"가 되기 때문이다.
 */
private class WidgetSnapshot(
    val snapshot: Snapshot?,
    val currency: Currency,
    val input: String,
) {
    val rate: Double? get() = snapshot?.rateOf(currency)
    val amount: Double get() = input.toDoubleOrNull() ?: 0.0
    val result: Double? get() = rate?.let { amount * it }
}

private fun readState(context: Context) = WidgetSnapshot(
    snapshot = RateRepository(context).lastKnown(),
    currency = WidgetPrefs.currency(context),
    input = WidgetPrefs.input(context),
)

private fun bindHeader(
    context: Context,
    views: RemoteViews,
    provider: Class<out AppWidgetProvider>,
    state: WidgetSnapshot,
) {
    CURRENCY_IDS.forEach { (currency, id) ->
        val on = currency == state.currency
        views.setTextViewText(id, currency.code)
        views.setInt(
            id,
            "setBackgroundResource",
            if (on) R.drawable.w_chip_on else R.drawable.w_chip,
        )
        views.setTextColor(
            id,
            context.getColor(if (on) R.color.w_on_accent else R.color.w_muted),
        )
        views.setOnClickPendingIntent(
            id,
            WidgetHub.broadcast(context, provider, WidgetHub.ACTION_CURRENCY, currency.code),
        )
    }
    views.setOnClickPendingIntent(
        R.id.w_refresh,
        WidgetHub.broadcast(context, provider, WidgetHub.ACTION_REFRESH),
    )
}

private fun bindHero(context: Context, views: RemoteViews, state: WidgetSnapshot) {
    val currency = state.currency
    val unit = currency.quoteUnit
    views.setTextViewText(
        R.id.w_amount,
        "${format(state.amount, currency.decimals)} ${currency.code}",
    )
    views.setTextViewText(
        R.id.w_result,
        state.result?.let { "${format(it, 0)}원" } ?: "—",
    )
    views.setTextViewText(
        R.id.w_rate,
        state.rate?.let { "$unit ${currency.code} = ${formatFixed(it * unit, 2)}원" }
            ?: "환율을 불러오는 중",
    )
    views.setOnClickPendingIntent(R.id.w_result, WidgetHub.openApp(context))
    views.setOnClickPendingIntent(R.id.w_amount, WidgetHub.openApp(context))
}
