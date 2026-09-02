package com.pricehere.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 위젯이 쓰는 상태. 앱 본체와 분리해 두어야 위젯 조작이 앱 화면을 흔들지 않는다. */
object WidgetPrefs {
    private const val NAME = "pricehere_widget"
    private const val K_CURRENCY = "currency"
    private const val K_INPUT = "input"

    private fun prefs(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun currency(c: Context): Currency = Currency.of(prefs(c).getString(K_CURRENCY, null))

    fun setCurrency(c: Context, v: Currency) {
        prefs(c).edit().putString(K_CURRENCY, v.code).apply()
    }

    fun input(c: Context): String = prefs(c).getString(K_INPUT, "") ?: ""


    fun setInput(c: Context, v: String) {
        prefs(c).edit().putString(K_INPUT, v).apply()
    }

    /** 키패드 한 글자 입력을 현재 문자열에 반영한다. */
    fun applyKey(current: String, key: String): String = when (key) {
        "DEL" -> current.dropLast(1)
        "CLR" -> ""
        "." -> when {
            current.contains('.') -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        else -> when {
            current.length + key.length > MAX -> current
            current == "0" && key != "00" -> key
            current.isEmpty() && key == "00" -> current
            else -> current + key
        }
    }

    private const val MAX = 12
}

object WidgetHub {
    const val ACTION_REFRESH = "com.pricehere.app.W_REFRESH"
    const val ACTION_CURRENCY = "com.pricehere.app.W_CURRENCY"
    const val ACTION_AMOUNT = "com.pricehere.app.W_AMOUNT"
    const val ACTION_KEY = "com.pricehere.app.W_KEY"
    const val EXTRA_VALUE = "value"

    private val PROVIDERS: List<Class<out AppWidgetProvider>> = listOf(
        RateWidgetProvider::class.java,
        QuickWidgetProvider::class.java,
        KeypadWidgetProvider::class.java,
    )

    /**
     * 같은 액션이라도 값이 다르면 서로 다른 PendingIntent여야 한다.
     * extras는 PendingIntent 동일성 판정에 안 들어가므로 data URI로 구분한다.
     */
    fun broadcast(
        context: Context,
        provider: Class<out AppWidgetProvider>,
        action: String,
        value: String? = null,
    ): PendingIntent {
        val intent = Intent(context, provider).apply {
            this.action = action
            data = Uri.parse("pricehere://${provider.simpleName}/$action/${value ?: "-"}")
            if (value != null) putExtra(EXTRA_VALUE, value)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun render(context: Context, provider: Class<out AppWidgetProvider>): RemoteViews = when (provider) {
        RateWidgetProvider::class.java -> renderRateWidget(context)
        QuickWidgetProvider::class.java -> renderQuickWidget(context)
        else -> renderKeypadWidget(context)
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        PROVIDERS.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isNotEmpty()) {
                manager.updateAppWidget(ids, render(context, provider))
            }
        }
    }
}

/** 세 위젯이 공유하는 동작. 렌더링만 각자 다르다. */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetHub.updateAll(context.applicationContext)
        fetch(context.applicationContext, null)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext
        val value = intent.getStringExtra(WidgetHub.EXTRA_VALUE).orEmpty()
        when (intent.action) {
            WidgetHub.ACTION_CURRENCY -> {
                WidgetPrefs.setCurrency(app, Currency.of(value))
                WidgetHub.updateAll(app)
            }

            WidgetHub.ACTION_AMOUNT -> {
                WidgetPrefs.setInput(app, value)
                WidgetHub.updateAll(app)
            }

            WidgetHub.ACTION_KEY -> {
                WidgetPrefs.setInput(app, WidgetPrefs.applyKey(WidgetPrefs.input(app), value))
                WidgetHub.updateAll(app)
            }

            WidgetHub.ACTION_REFRESH -> fetch(app, goAsync())
        }
    }

    /** 네트워크는 느리므로 캐시로 먼저 그리고, 받아오면 다시 그린다. */
    private fun fetch(context: Context, pending: PendingResult?) {
        WidgetHub.updateAll(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RateRepository(context).refresh()
            } catch (e: Exception) {
                // 실패하면 직전 캐시를 그대로 보여준다.
            }
            withContext(Dispatchers.Main) { WidgetHub.updateAll(context) }
            pending?.finish()
        }
    }
}

class RateWidgetProvider : BaseWidgetProvider()
class QuickWidgetProvider : BaseWidgetProvider()
class KeypadWidgetProvider : BaseWidgetProvider()
