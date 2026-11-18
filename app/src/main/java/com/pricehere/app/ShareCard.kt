package com.pricehere.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.time.Instant

/**
 * 환산 결과를 앱의 가격표 모양 그대로 그린 공유 이미지(1080×1350, 4:5).
 * 카카오톡·인스타 피드에서 잘리지 않는 비율이고, 받는 사람이 앱을 몰라도 한눈에 읽히도록
 * 위 칸은 외화, 아래 칸은 원화, 그 아래 환율과 출처를 둔다.
 * 이미지 안의 주소는 누를 수 없으므로 같은 내용의 글(앱 받기 링크 포함)을 함께 넘긴다.
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1350

    // 로고 타일 색. 밝기 모드와 상관없이 같다.
    private const val TEAL = 0xFF0B6E5E.toInt()
    private const val TEAL_HI = 0xFF17A08C.toInt()
    private const val TEAL_DEEP = 0xFF05433A.toInt()

    /** 카드 색. 앱이 다크 모드면 어두운 카드로 그린다(v2.14 #10). */
    private class Palette(
        val bg: Int, val surface: Int, val ink: Int, val muted: Int,
        val deep: Int, val deepSoft: Int, val accent: Int,
        val mintTop: Int, val mintBottom: Int, val dash: Int, val divider: Int, val track: Int,
        val glow1: Int, val glow2: Int, val shadow: Int, val alert: Int,
    )

    private val LIGHT = Palette(
        bg = 0xFFEDF1F0.toInt(), surface = 0xFFFFFFFF.toInt(), ink = 0xFF15201D.toInt(), muted = 0xFF5E6B67.toInt(),
        deep = 0xFF0B3C32.toInt(), deepSoft = 0xB30B3C32.toInt(), accent = 0xFF0B6E5E.toInt(),
        mintTop = 0xFFDDF5EC.toInt(), mintBottom = 0xFFCFEFE4.toInt(), dash = 0xFFC9D6D1.toInt(),
        divider = 0xFFE6ECEA.toInt(), track = 0x1F0B3C32,
        glow1 = 0x4217A08C, glow2 = 0x4D7FE0C3, shadow = 0x400B3C32, alert = 0xFFE12D39.toInt(),
    )

    private val DARK = Palette(
        bg = 0xFF0A0F0F.toInt(), surface = 0xFF161E1C.toInt(), ink = 0xFFDDE4E1.toInt(), muted = 0xFF93A19C.toInt(),
        deep = 0xFFD3F3EA.toInt(), deepSoft = 0xB3BFE3D8.toInt(), accent = 0xFF3FC7AE.toInt(),
        mintTop = 0xFF12332C.toInt(), mintBottom = 0xFF0E2A24.toInt(), dash = 0xFF2A3532.toInt(),
        divider = 0xFF26302D.toInt(), track = 0x26FFFFFF,
        glow1 = 0x3317A08C, glow2 = 0x1F7FE0C3, shadow = 0x66000000, alert = 0xFFFF7A82.toInt(),
    )

    /** 지금 그리는 카드의 팔레트. render/renderSaved 시작에서 정한다(UI 스레드에서만 부른다). */
    private var pal = LIGHT

    /** 결과가 없으면(환율 없음·입력 없음) null. */
    fun render(context: Context, state: UiState, dark: Boolean = false): Bitmap? {
        pal = if (dark) DARK else LIGHT
        val result = state.result ?: return null
        val amount = state.amountOrNull?.takeIf { it > 0 } ?: return null
        state.rate ?: return null

        val foreign = if (state.foreignToKrw) amount else result
        val won = if (state.foreignToKrw) result else amount
        val code = state.selected.code

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val geist = ResourcesCompat.getFont(context, R.font.geist) ?: Typeface.DEFAULT

        // 바탕과 은은한 빛 번짐(앱 홈 화면과 같은 느낌)
        c.drawColor(pal.bg)
        glow(c, 260f, 380f, 620f, pal.glow1)
        glow(c, 1010f, 760f, 460f, pal.glow2)

        // ---------------- 가격표
        val left = 72f
        val right = W - 72f
        val top = 110f
        val topH = 330f
        val seamH = 72f
        val bottomH = 380f
        val radius = 64f
        val bottom = top + topH + seamH + bottomH

        // 그림자
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.bg
            setShadowLayer(56f, 0f, 28f, pal.shadow)
        }
        c.drawRoundRect(RectF(left, top, right, bottom), radius, radius, shadow)

        // 위 칸(흰색)과 아래 칸(민트)을 둥근 사각형 하나로 자르고, 이음매에서 색을 나눈다.
        c.save()
        val clip = Path().apply { addRoundRect(RectF(left, top, right, bottom), radius, radius, Path.Direction.CW) }
        c.clipPath(clip)
        val seamY = top + topH + seamH / 2f
        c.drawRect(left, top, right, seamY, Paint().apply { color = pal.surface })
        c.drawRect(
            left, seamY, right, bottom,
            Paint().apply {
                shader = LinearGradient(0f, seamY, 0f, bottom, pal.mintTop, pal.mintBottom, Shader.TileMode.CLAMP)
            },
        )
        c.restore()
        // 이음매: 양옆 홈 + 점선
        val notch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.bg }
        c.drawCircle(left, seamY, 26f, notch)
        c.drawCircle(right, seamY, 26f, notch)
        c.drawLine(
            left + 56f, seamY, right - 56f, seamY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.dash
                strokeWidth = 4f
                pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
            },
        )

        val pad = 64f
        val textW = right - left - pad * 2

        // 위 칸: 외화
        val label = korean(40f, pal.muted, bold = true)
        c.drawText(state.selected.koreanName, left + pad, top + 96f, label)
        if (state.foreignToKrw && state.isExpression) {
            val expr = number(geist, 40f, pal.muted, 500)
            fitText(Expression.pretty(state.input), expr, textW * 0.62f)
            expr.textAlign = Paint.Align.RIGHT
            c.drawText(Expression.pretty(state.input), right - pad, top + 96f, expr)
        }
        val codePaint = number(geist, 52f, pal.muted, 600).apply { textAlign = Paint.Align.RIGHT }
        c.drawText(code, right - pad, top + 262f, codePaint)
        val foreignText = format(foreign, state.selected.decimals)
        val big1 = number(geist, 128f, pal.ink, 600)
        fitText(foreignText, big1, textW - codePaint.measureText(code) - 40f)
        c.drawText(foreignText, left + pad, top + 268f, big1)

        // 아래 칸: 원화
        val bTop = seamY
        val modeLabel = "대한민국 원 · ${state.priceMode.label}"
        c.drawText(modeLabel, left + pad, bTop + 110f, korean(40f, pal.deepSoft, bold = true))
        val wonText = format(won, 0)
        val big2 = number(geist, 168f, pal.deep, 700)
        val unit = korean(64f, pal.deep, bold = true)
        fitText(wonText, big2, textW - unit.measureText("원") - 16f)
        val wonY = bTop + 290f
        c.drawText(wonText, left + pad, wonY, big2)
        c.drawText("원", left + pad + big2.measureText(wonText) + 12f, wonY, unit)
        approxWon(won)?.let { c.drawText(it, left + pad, bTop + 370f, korean(44f, pal.accent, bold = true)) }

        // ---------------- 환율과 출처
        val infoY = bottom + 104f
        state.rateLabel?.let { c.drawText(it, left + 8f, infoY, number(geist, 46f, pal.ink, 600)) }
        val source = buildString {
            state.snapshot?.let {
                append(it.source.label)
                append(" · ")
                append(DAY_STAMP.format(Instant.ofEpochMilli(it.quotedAtMillis)))
                append(" 고시")
            }
            if (state.priceMode != PriceMode.BASE) {
                append(" · ${state.priceMode.shortLabel} 수수료 ${formatFixed(state.feePercent, 2)}% 반영")
            }
        }
        val sourcePaint = korean(34f, pal.muted, bold = false)
        fitText(source, sourcePaint, right - left - 16f)
        c.drawText(source, left + 8f, infoY + 58f, sourcePaint)

        signature(c, left, right, H - 120f)
        return bmp
    }

    /** 서명: 앱 로고 + 이름 + 받는 곳. footY는 로고 세로 중심. */
    private fun signature(c: Canvas, left: Float, right: Float, footY: Float) {
        val tile = 96f
        wonTile(c, left + 8f, footY - tile / 2f, tile)
        val nameX = left + 8f + tile + 28f
        c.drawText("여긴얼마?", nameX, footY - 6f, korean(48f, pal.ink, bold = true, heavy = true))
        c.drawText("지금 이 가격, 원화로", nameX, footY + 42f, korean(32f, pal.muted, bold = false))
        val get = korean(30f, pal.muted, bold = false).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("Android 앱 받기", right - 8f, footY - 6f, korean(32f, pal.accent, bold = true).apply { textAlign = Paint.Align.RIGHT })
        c.drawText("github.com/wjdals988/pricehere", right - 8f, footY + 40f, get)
    }

    /**
     * 저장 목록 카드. 폭 1080에 높이는 항목 수에 맞춰 1080~1700 사이.
     * 위에 제목, 흰 목록 카드, 민트 합계 카드(입국 면세 한도 막대 포함), 맨 아래 서명.
     */
    fun renderSaved(context: Context, items: List<SavedItem>, snapshot: Snapshot?, dark: Boolean = false): Bitmap? {
        pal = if (dark) DARK else LIGHT
        if (items.isEmpty()) return null
        val rates = snapshot?.rates.orEmpty()
        fun rateOf(item: SavedItem) = rates[item.currencyCode] ?: item.rateAtSave
        val total = items.sumOf { it.krwAt(rateOf(it)) }
        val usd = dutyFreeUsd(items, rates)
        // 배치 계산은 CardLayout(단위 테스트 대상)에서.
        val layout = CardLayout.saved(items.size, hasDutyFree = usd != null)
        val shown = items.take(layout.shown)
        val rest = layout.rest
        val rowH = CardLayout.ROW_H
        val listRows = shown.size + if (rest > 0) 1 else 0
        val left = 72f
        val right = W - 72f
        val listTop = CardLayout.LIST_TOP
        val listBottom = layout.listBottom
        val totalTop = layout.totalTop
        val totalBottom = layout.totalBottom
        val height = layout.height

        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val geist = ResourcesCompat.getFont(context, R.font.geist) ?: Typeface.DEFAULT
        c.drawColor(pal.bg)
        glow(c, 260f, 380f, 620f, pal.glow1)
        glow(c, 1010f, height * 0.62f, 460f, pal.glow2)

        // 제목
        c.drawText("저장한 금액", left + 8f, 150f, korean(60f, pal.ink, bold = true, heavy = true))
        val sub = "${items.size}건 · 현재 환율 기준" +
            (snapshot?.let { " · ${DAY_STAMP.format(Instant.ofEpochMilli(it.quotedAtMillis))} 고시" } ?: "")
        c.drawText(sub, left + 8f, 206f, korean(34f, pal.muted, bold = false))

        // 목록 카드
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.surface; setShadowLayer(40f, 0f, 18f, pal.shadow) }
        c.drawRoundRect(RectF(left, listTop, right, listBottom), 56f, 56f, shadow)
        val pad = 56f
        val divider = Paint().apply { color = pal.divider; strokeWidth = 2f }
        var y = listTop + 28f
        shown.forEachIndexed { i, item ->
            val krw = item.krwAt(rateOf(item))
            val wonText = format(krw, 0) + "원"
            val wonPaint = number(geist, 46f, pal.ink, 600).apply { textAlign = Paint.Align.RIGHT }
            val wonW = wonPaint.measureText(wonText)
            val memo = korean(40f, pal.ink, bold = true)
            val memoText = ellipsize(item.memo, memo, right - left - pad * 2 - wonW - 32f)
            c.drawText(memoText, left + pad, y + 52f, memo)
            val detail = "${format(item.amount, item.currency.decimals)} ${item.currencyCode}" +
                (if (item.mode != PriceMode.BASE) " · ${item.mode.shortLabel}" else "")
            c.drawText(detail, left + pad, y + 94f, number(geist, 30f, pal.muted, 500))
            c.drawText(wonText, right - pad, y + 70f, wonPaint)
            if (i < listRows - 1) c.drawLine(left + pad, y + rowH, right - pad, y + rowH, divider)
            y += rowH
        }
        if (rest > 0) {
            c.drawText("외 ${rest}건", left + pad, y + 66f, korean(36f, pal.muted, bold = true))
        }

        // 합계 카드
        val totalRect = RectF(left, totalTop, right, totalBottom)
        c.drawRoundRect(totalRect, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, totalTop, 0f, totalBottom, pal.mintTop, pal.mintBottom, Shader.TileMode.CLAMP)
            setShadowLayer(40f, 0f, 18f, pal.shadow)
        })
        c.drawText("합계", left + pad, totalTop + 84f, korean(38f, pal.deepSoft, bold = true))
        val totalText = format(total, 0)
        val big = number(geist, 120f, pal.deep, 700)
        fitText(totalText, big, right - left - pad * 2 - 90f)
        c.drawText(totalText, left + pad, totalTop + 200f, big)
        c.drawText("원", left + pad + big.measureText(totalText) + 12f, totalTop + 200f, korean(52f, pal.deep, bold = true))
        if (usd != null) {
            val over = usd > DUTY_FREE_USD
            val alert = pal.alert
            val barTop = totalTop + 296f
            val barL = left + pad
            val barR = right - pad
            val label = korean(30f, pal.deepSoft, bold = true)
            c.drawText("입국 면세 한도", barL, barTop - 14f, label)
            val usdText = "$" + format(usd, 0) + " / $" + format(DUTY_FREE_USD, 0)
            c.drawText(usdText, barR, barTop - 14f, number(geist, 32f, if (over) alert else pal.deep, 700).apply { textAlign = Paint.Align.RIGHT })
            val track = RectF(barL, barTop + 8f, barR, barTop + 30f)
            c.drawRoundRect(track, 11f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.track })
            val frac = (usd / DUTY_FREE_USD).toFloat().coerceIn(0f, 1f)
            if (frac > 0f) {
                val fill = RectF(barL, barTop + 8f, barL + (barR - barL) * frac, barTop + 30f)
                c.drawRoundRect(fill, 11f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    if (over) color = alert else shader = LinearGradient(fill.left, 0f, fill.right, 0f, TEAL_HI, TEAL, Shader.TileMode.CLAMP)
                })
            }
            val note = if (over) "${format(usd - DUTY_FREE_USD, 0)}달러 초과 · 1인 기준" else "${format(DUTY_FREE_USD - usd, 0)}달러 남음 · 1인 기준"
            c.drawText(note, barL, barTop + 70f, korean(28f, if (over) alert else pal.deepSoft, bold = false))
        }

        signature(c, left, right, height - 120f)
        return bmp
    }

    /** 사진 앱(Pictures/여긴얼마)에 PNG로 저장한다. Android 10 미만은 권한이 필요해 지원하지 않는다. */
    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "pricehere_${System.currentTimeMillis()}.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/여긴얼마")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("no stream")
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
    }

    val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= 29

    /** 이미지와 글(앱 받기 링크 포함)을 함께 공유한다. 글을 버리는 앱은 이미지만 보낸다. */
    fun share(context: Context, bitmap: Bitmap, text: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "pricehere.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("여긴얼마", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(send, "이미지로 공유")) }
    }

    // ---------------------------------------------------------------- 그리기 도구

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
    }

    private fun number(face: Typeface, size: Float, color: Int, weight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
        fontFeatureSettings = "tnum"
        fontVariationSettings = "'wght' $weight"
        letterSpacing = -0.02f
    }

    private fun korean(size: Float, color: Int, bold: Boolean, heavy: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = when {
            Build.VERSION.SDK_INT >= 28 -> Typeface.create(Typeface.DEFAULT, if (heavy) 800 else if (bold) 700 else 400, false)
            bold || heavy -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
        textSize = size
        this.color = color
        letterSpacing = if (heavy) -0.02f else 0f
    }

    /** 폭을 넘으면 뒤를 "…"로 줄인다(메모처럼 크기를 줄이면 안 되는 글). */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String =
        CardLayout.ellipsize(text, maxWidth) { paint.measureText(it) }

    /** 폭을 넘으면 글자 크기를 줄여 한 줄에 맞춘다. */
    private fun fitText(text: String, paint: Paint, maxWidth: Float) {
        val probe = Paint(paint)
        paint.textSize = CardLayout.fitSize(text, paint.textSize, maxWidth) { t, size ->
            probe.textSize = size
            probe.measureText(t)
        }
    }

    /** 런처 아이콘과 같은 ₩ 타일. splash_icon.xml과 같은 비율로 그린다. */
    private fun wonTile(c: Canvas, x: Float, y: Float, size: Float) {
        val r = size * 14f / 48f
        val rect = RectF(x, y, x + size, y + size)
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                x, y, x + size, y + size,
                intArrayOf(TEAL_HI, TEAL, TEAL_DEEP), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        })
        val u = size / 48f * 0.667f
        val cx = x + size / 2f
        val cy = y + size / 2f
        fun px(v: Float) = cx + (v - 54f) * u
        fun py(v: Float) = cy + (v - 54f) * u
        val wPath = Path().apply {
            moveTo(px(36.2f), py(37f)); lineTo(px(45.1f), py(72.7f)); lineTo(px(54f), py(44.6f))
            lineTo(px(62.9f), py(72.7f)); lineTo(px(71.8f), py(37f))
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = 0xFFFFFFFF.toInt()
            strokeWidth = 7.6f * u
        }
        c.drawPath(wPath, stroke)
        stroke.strokeWidth = 5.1f * u
        stroke.color = 0xFFE6F8F2.toInt()
        c.drawLine(px(32.8f), py(52.3f), px(75.2f), py(52.3f), stroke)
        c.drawLine(px(32.8f), py(62.5f), px(75.2f), py(62.5f), stroke)
    }
}
