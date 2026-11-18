package com.pricehere.app

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi

/** 위젯 색. 앱 색(청록)이 기본이고, Android 12 이상에서는 배경화면 색(Material You)을 고를 수 있다. */
enum class WidgetPalette(val label: String) {
    APP("앱 색"),
    WALLPAPER("배경화면 색"),
    ;

    companion object {
        fun of(name: String?): WidgetPalette = entries.firstOrNull { it.name == name } ?: APP

        /** 배경화면 색은 시스템 동적 색(API 31+)이 있어야 한다. */
        val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}

/**
 * 배경화면 색 모드에서 위젯 레이아웃의 앱 색을 시스템 동적 색으로 덮어쓴다.
 *
 * 레이아웃을 두 벌 두지 않고, 그린 뒤 뷰마다 색 자원을 다시 지정한다. setColorStateList(…, @ColorRes)는
 * 색을 런처 쪽에서 풀기 때문에 기기의 다크 모드를 그대로 따른다(values-v31 / values-night-v31의 wy_*).
 * 등락(빨강·파랑)과 오래된 환율 경고색은 뜻이 있는 색이라 바꾸지 않는다.
 */
@RequiresApi(Build.VERSION_CODES.S)
object WidgetYou {

    fun enabled(context: Context): Boolean =
        WidgetPalette.supported && SettingsStore(context).widgetPalette() == WidgetPalette.WALLPAPER

    private fun bg(v: RemoteViews, id: Int, color: Int) = v.setColorStateList(id, "setBackgroundTintList", color)
    private fun text(v: RemoteViews, id: Int, color: Int) = v.setColorStateList(id, "setTextColor", color)
    private fun icon(v: RemoteViews, id: Int, color: Int) = v.setColorStateList(id, "setImageTintList", color)

    fun rates(v: RemoteViews, codeIds: IntArray, rateIds: IntArray, stale: Boolean) {
        bg(v, R.id.w_root, R.color.wy_bg)
        if (!stale) text(v, R.id.w_time, R.color.wy_muted)
        icon(v, R.id.w_refresh, R.color.wy_accent)
        codeIds.forEach { text(v, it, R.color.wy_muted) }
        rateIds.forEach { text(v, it, R.color.wy_text) }
    }

    /** 통화 칩 줄(빠른 계산·큰 계산기). 고른 칩만 진한 알약. */
    fun header(v: RemoteViews, chips: Map<Currency, Int>, selected: Currency) {
        chips.forEach { (currency, id) ->
            if (currency == selected) {
                bg(v, id, R.color.wy_ink)
                text(v, id, R.color.wy_on_ink)
            } else {
                text(v, id, R.color.wy_muted)
            }
        }
        icon(v, R.id.w_refresh, R.color.wy_accent)
    }

    fun hero(v: RemoteViews, stale: Boolean) {
        text(v, R.id.w_amount, R.color.wy_muted)
        text(v, R.id.w_result, R.color.wy_text)
        if (!stale) text(v, R.id.w_rate, R.color.wy_muted)
    }

    fun quick(v: RemoteViews, chipIds: IntArray) {
        bg(v, R.id.w_root, R.color.wy_bg)
        bg(v, R.id.w_hero_box, R.color.wy_hero)
        text(v, R.id.w_amount, R.color.wy_text)
        chipIds.forEach {
            bg(v, it, R.color.wy_key_sl)
            text(v, it, R.color.wy_text)
        }
    }

    fun keypad(v: RemoteViews, compact: Boolean) {
        bg(v, R.id.w_root, R.color.wy_bg)
        if (compact) text(v, R.id.w_cur_label, R.color.wy_text)
        DIGITS.forEach {
            bg(v, it, R.color.wy_key_sl)
            text(v, it, R.color.wy_text)
        }
        FUNCTIONS.forEach {
            bg(v, it, R.color.wy_key_fn)
            text(v, it, R.color.wy_muted)
        }
        if (compact) {
            bg(v, R.id.kcur, R.color.wy_key_fn)
            text(v, R.id.kcur, R.color.wy_muted)
        }
        OPERATORS.forEach {
            bg(v, it, R.color.wy_key_op_sl)
            text(v, it, R.color.wy_accent)
        }
    }

    private val DIGITS = intArrayOf(
        R.id.k0, R.id.k1, R.id.k2, R.id.k3, R.id.k4, R.id.k5, R.id.k6, R.id.k7, R.id.k8, R.id.k9, R.id.kdot,
    )
    private val FUNCTIONS = intArrayOf(R.id.kclr, R.id.kdel, R.id.k00)
    private val OPERATORS = intArrayOf(R.id.kadd, R.id.ksub, R.id.kmul, R.id.kdiv)
}
