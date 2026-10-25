package com.pricehere.app

import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.util.SizeF
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
        snapshot?.let { "${it.source.short} · ${stamp(it)}" } ?: "환율을 불러오는 중",
    )
    views.setTextColor(R.id.w_time, context.getColor(staleColor(snapshot, R.color.w_muted)))

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

// ---------------------------------------------------------------- 4×2 빠른 계산

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

// ---------------------------------------------------------------- 키패드 계산기

private val DIGIT_KEYS = mapOf(
    R.id.k0 to "0", R.id.k1 to "1", R.id.k2 to "2", R.id.k3 to "3", R.id.k4 to "4",
    R.id.k5 to "5", R.id.k6 to "6", R.id.k7 to "7", R.id.k8 to "8", R.id.k9 to "9",
    R.id.k00 to "00", R.id.kdot to ".",
    R.id.kdel to "DEL", R.id.kclr to "CLR",
    R.id.kadd to "+", R.id.ksub to "-", R.id.kmul to "*", R.id.kdiv to "/",
)

/**
 * 위젯 높이가 이 값 이상이면 표시 영역이 큰 레이아웃을 쓴다. 3칸(약 301dp)에서 키가 48dp인데,
 * 큰 표시 영역(약 +33dp)을 넣으면 키가 약 40dp로 줄어 손가락보다 작아진다. 4칸(약 417dp)부터 여유가 생긴다.
 */
private const val KEYPAD_TALL_MIN_HEIGHT = 380f

fun renderKeypadWidget(context: Context): RemoteViews {
    val compact = buildKeypad(context, R.layout.widget_keypad)
    // 크기별 레이아웃은 Android 12(API 31)부터. 그 아래는 늘 한 줄 표시.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return compact
    return RemoteViews(
        mapOf(
            SizeF(250f, 180f) to compact,
            SizeF(250f, KEYPAD_TALL_MIN_HEIGHT) to buildKeypad(context, R.layout.widget_keypad_tall),
        )
    )
}

private fun buildKeypad(context: Context, layout: Int): RemoteViews {
    val views = RemoteViews(context.packageName, layout)
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
    // 열기 키 대신 표시 영역 전체(식·결과·환율 줄과 그 여백)를 누르면 앱이 열린다.
    // 입력하던 식과 통화를 그대로 들고 간다.
    views.setOnClickPendingIntent(R.id.w_display, WidgetHub.openAppWith(context, state.currency, state.input))
    return views
}

// ---------------------------------------------------------------- 공통

/**
 * 위젯은 30분마다(Doze면 더 늦게) 또는 눌렀을 때만 다시 그려진다. "2분 전" 같은 상대 시각은
 * 다음 갱신까지 그대로 남아 거짓이 되므로, 시간이 지나도 틀리지 않는 날짜 붙은 고시 시각을 쓴다.
 */
private fun stamp(snapshot: Snapshot): String = DAY_STAMP.format(Instant.ofEpochMilli(snapshot.quotedAtMillis))

/**
 * 색도 그린 시점 기준이라, 시간이 지나도 틀려지지 않는 경우에만 경고색을 쓴다.
 * "오프라인 캐시"와 "고시 후 24시간 넘음"은 한번 참이면 계속 참이다. 앱의 dotColor와 같은 기준이다.
 */
private fun staleColor(snapshot: Snapshot?, normal: Int): Int {
    snapshot ?: return normal
    val minutes = (System.currentTimeMillis() - snapshot.quotedAtMillis) / 60_000L
    val stale = dotColor(snapshot.source, minutes) == Freshness.stale
    return if (stale) R.color.w_stale else normal
}

/**
 * 위젯은 방향을 바꾸지 않는다. 현지 통화를 넣으면 원화가 나오는 한 방향만 지원한다.
 * 홈 화면에서 방향이 뒤집힌 채로 남아 있으면 "왜 이상한 값이 뜨지"가 되기 때문이다.
 */
private class WidgetSnapshot(
    val snapshot: Snapshot?,
    val currency: Currency,
    val input: String,
    /** 앱에서 고른 결제 수단. 앱으로 넘어갔을 때 금액이 달라지지 않도록 위젯도 같은 기준으로 계산한다. */
    val mode: PriceMode,
) {
    val rate: Double? get() = snapshot?.rateOf(currency)?.let { mode.apply(it, currency) }
    /** 계산기 위젯은 식(20+15)을 넣을 수 있다. 0으로 나누면 null. */
    val amount: Double? get() = Expression.evaluate(input)
    val result: Double? get() = rate?.let { r -> amount?.let { it * r } }
}

private fun readState(context: Context) = WidgetSnapshot(
    snapshot = RateRepository(context).lastKnown(),
    currency = WidgetPrefs.currency(context),
    input = WidgetPrefs.input(context),
    mode = SettingsStore(context).priceMode(),
)

private fun bindHeader(
    context: Context,
    views: RemoteViews,
    provider: Class<out AppWidgetProvider>,
    state: WidgetSnapshot,
) {
    CURRENCY_IDS.forEach { (currency, id) ->
        val on = currency == state.currency
        // 앱의 통화 선택과 같은 "국기 + 코드". 코드만 있으면 네 칸이 같은 모양이라 한눈에 안 갈린다.
        views.setTextViewText(id, "${currency.flag} ${currency.code}")
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
    // 식이면 입력한 그대로(1,000 + 500), 숫자 하나면 통화 자릿수에 맞춰 보여준다.
    val amountText = if (Expression.hasOperator(state.input)) {
        Expression.pretty(state.input)
    } else {
        format(state.amount ?: 0.0, currency.decimals)
    }
    views.setTextViewText(R.id.w_amount, "$amountText ${currency.code}")
    views.setTextViewText(
        R.id.w_result,
        state.result?.let { "${format(it, 0)}원" } ?: "—",
    )
    val snapshot = state.snapshot
    views.setTextViewText(
        R.id.w_rate,
        state.rate?.let { rate ->
            // 기준율이 아니면 무엇이 더해진 금액인지 밝힌다. "100 JPY = 862.77원 · 카드 · 10/24 12:01"
            "$unit ${currency.code} = ${formatFixed(rate * unit, 2)}원" +
                (if (state.mode != PriceMode.BASE) " · ${state.mode.shortLabel}" else "") +
                (snapshot?.let { " · ${stamp(it)}" } ?: "")
        } ?: "환율을 불러오는 중",
    )
    views.setTextColor(R.id.w_rate, context.getColor(staleColor(snapshot, R.color.w_muted)))
    val open = WidgetHub.openAppWith(context, currency, state.input)
    views.setOnClickPendingIntent(R.id.w_result, open)
    views.setOnClickPendingIntent(R.id.w_amount, open)
}
