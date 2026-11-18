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

    private const val BG = 0xFFEDF1F0.toInt()
    private const val INK = 0xFF15201D.toInt()
    private const val MUTED = 0xFF5E6B67.toInt()
    private const val DEEP = 0xFF0B3C32.toInt()
    private const val TEAL = 0xFF0B6E5E.toInt()
    private const val TEAL_HI = 0xFF17A08C.toInt()
    private const val TEAL_DEEP = 0xFF05433A.toInt()
    private const val MINT_TOP = 0xFFDDF5EC.toInt()
    private const val MINT_BOTTOM = 0xFFCFEFE4.toInt()
    private const val DASH = 0xFFC9D6D1.toInt()

    /** 결과가 없으면(환율 없음·입력 없음) null. */
    fun render(context: Context, state: UiState): Bitmap? {
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
        c.drawColor(BG)
        glow(c, 260f, 380f, 620f, 0x4217A08C)
        glow(c, 1010f, 760f, 460f, 0x4D7FE0C3)

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
            color = BG
            setShadowLayer(56f, 0f, 28f, 0x400B3C32)
        }
        c.drawRoundRect(RectF(left, top, right, bottom), radius, radius, shadow)

        // 위 칸(흰색)과 아래 칸(민트)을 둥근 사각형 하나로 자르고, 이음매에서 색을 나눈다.
        c.save()
        val clip = Path().apply { addRoundRect(RectF(left, top, right, bottom), radius, radius, Path.Direction.CW) }
        c.clipPath(clip)
        val seamY = top + topH + seamH / 2f
        c.drawRect(left, top, right, seamY, Paint().apply { color = 0xFFFFFFFF.toInt() })
        c.drawRect(
            left, seamY, right, bottom,
            Paint().apply {
                shader = LinearGradient(0f, seamY, 0f, bottom, MINT_TOP, MINT_BOTTOM, Shader.TileMode.CLAMP)
            },
        )
        c.restore()
        // 이음매: 양옆 홈 + 점선
        val notch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG }
        c.drawCircle(left, seamY, 26f, notch)
        c.drawCircle(right, seamY, 26f, notch)
        c.drawLine(
            left + 56f, seamY, right - 56f, seamY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DASH
                strokeWidth = 4f
                pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
            },
        )

        val pad = 64f
        val textW = right - left - pad * 2

        // 위 칸: 외화
        val label = korean(40f, MUTED, bold = true)
        c.drawText(state.selected.koreanName, left + pad, top + 96f, label)
        if (state.foreignToKrw && state.isExpression) {
            val expr = number(geist, 40f, MUTED, 500)
            fitText(Expression.pretty(state.input), expr, textW * 0.62f)
            expr.textAlign = Paint.Align.RIGHT
            c.drawText(Expression.pretty(state.input), right - pad, top + 96f, expr)
        }
        val codePaint = number(geist, 52f, MUTED, 600).apply { textAlign = Paint.Align.RIGHT }
        c.drawText(code, right - pad, top + 262f, codePaint)
        val foreignText = format(foreign, state.selected.decimals)
        val big1 = number(geist, 128f, INK, 600)
        fitText(foreignText, big1, textW - codePaint.measureText(code) - 40f)
        c.drawText(foreignText, left + pad, top + 268f, big1)

        // 아래 칸: 원화
        val bTop = seamY
        val modeLabel = "대한민국 원 · ${state.priceMode.label}"
        c.drawText(modeLabel, left + pad, bTop + 110f, korean(40f, 0xB30B3C32.toInt(), bold = true))
        val wonText = format(won, 0)
        val big2 = number(geist, 168f, DEEP, 700)
        val unit = korean(64f, DEEP, bold = true)
        fitText(wonText, big2, textW - unit.measureText("원") - 16f)
        val wonY = bTop + 290f
        c.drawText(wonText, left + pad, wonY, big2)
        c.drawText("원", left + pad + big2.measureText(wonText) + 12f, wonY, unit)
        approxWon(won)?.let { c.drawText(it, left + pad, bTop + 370f, korean(44f, TEAL, bold = true)) }

        // ---------------- 환율과 출처
        val infoY = bottom + 104f
        state.rateLabel?.let { c.drawText(it, left + 8f, infoY, number(geist, 46f, INK, 600)) }
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
        val sourcePaint = korean(34f, MUTED, bold = false)
        fitText(source, sourcePaint, right - left - 16f)
        c.drawText(source, left + 8f, infoY + 58f, sourcePaint)

        // ---------------- 서명(앱 로고 + 이름 + 받는 곳)
        val footY = H - 120f
        val tile = 96f
        wonTile(c, left + 8f, footY - tile / 2f, tile)
        val nameX = left + 8f + tile + 28f
        c.drawText("여긴얼마?", nameX, footY - 6f, korean(48f, INK, bold = true, heavy = true))
        c.drawText("지금 이 가격, 원화로", nameX, footY + 42f, korean(32f, MUTED, bold = false))
        val get = korean(30f, MUTED, bold = false).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("Android 앱 받기", right - 8f, footY - 6f, korean(32f, TEAL, bold = true).apply { textAlign = Paint.Align.RIGHT })
        c.drawText("github.com/wjdals988/pricehere", right - 8f, footY + 40f, get)

        return bmp
    }

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

    /** 폭을 넘으면 글자 크기를 줄여 한 줄에 맞춘다. */
    private fun fitText(text: String, paint: Paint, maxWidth: Float) {
        while (paint.measureText(text) > maxWidth && paint.textSize > 20f) {
            paint.textSize *= 0.94f
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
