package com.pricehere.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.time.Instant

/**
 * 다른 앱에서 "¥12,800"을 선택하면 텍스트 선택 메뉴에 "여긴얼마?"가 뜬다(ACTION_PROCESS_TEXT).
 * 누르면 그 앱 위에 반쯤 덮이는 카드로 원화를 보여주고, 닫으면 원래 화면으로 돌아간다.
 * 앱을 오가며 숫자를 옮겨 칠 필요가 없게 하는 것이 목적이라, 화면 전환 없이 끝낸다.
 */
class PriceTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        val repo = RateRepository(this)
        val parsed = PriceText.parse(text, repo.loadSelected())
        val theme = SettingsStore(this).themeMode()

        setContent {
            PriceHereTheme(theme) {
                var snapshot by remember { mutableStateOf(repo.lastKnown()) }
                LaunchedEffect(Unit) {
                    // 캐시가 충분히 새것이면 비공식 API를 다시 부르지 않는다.
                    val age = System.currentTimeMillis() - (snapshot?.fetchedAtMillis ?: 0L)
                    if (age > FRESH_MS) runCatching { repo.refresh() }.onSuccess { snapshot = it }
                }
                PriceSheet(
                    text = text,
                    parsed = parsed,
                    snapshot = snapshot,
                    onOpen = { currency, amount ->
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra(MainActivity.EXTRA_CURRENCY, currency.code)
                                .putExtra(MainActivity.EXTRA_AMOUNT, amount)
                        )
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    private companion object {
        const val FRESH_MS = 10 * 60 * 1000L
    }
}

@Composable
private fun PriceSheet(
    text: String,
    parsed: PriceText.Parsed?,
    snapshot: Snapshot?,
    onOpen: (Currency, Double) -> Unit,
    onClose: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val noRipple = remember { MutableInteractionSource() }
    var currency by remember { mutableStateOf(parsed?.currency ?: Currency.USD) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = noRipple, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radius.card, topEnd = Radius.card))
                .background(MaterialTheme.colorScheme.surface)
                // 카드 안을 눌러도 닫히지 않게 터치를 여기서 받는다.
                .clickable(interactionSource = noRipple, indication = null) {}
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter, vertical = Space.xl),
        ) {
            Text(
                text = "“${text.trim()}”",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.m))

            if (parsed == null) {
                Text(
                    text = "선택한 글자에서 금액을 찾지 못했습니다",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = "숫자가 들어 있는 부분만 선택해 보세요. 예: ¥12,800",
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            } else {
                // 기호로 통화를 못 정했거나 틀렸을 수 있어 항상 바꿀 수 있게 둔다.
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Currency.entries.forEach { c ->
                        Pill(text = "${c.flag} ${c.code}", selected = c == currency) { currency = c }
                    }
                }
                Spacer(Modifier.height(Space.l))

                val rate = snapshot?.rateOf(currency)
                val won = rate?.let { parsed.amount * it }
                Text(
                    text = won?.let { format(it, 0) + "원" } ?: "환율 없음",
                    style = Num.amount(34).copy(color = MaterialTheme.colorScheme.onSurface),
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = listOfNotNull(
                        "${format(parsed.amount, currency.decimals)} ${currency.code}",
                        won?.let(::approxWon),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (snapshot != null && rate != null) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = "${currency.quoteUnit} ${currency.code} = ${formatFixed(rate * currency.quoteUnit, 2)}원 · " +
                            "${snapshot.source.short} ${CLOCK.format(Instant.ofEpochMilli(snapshot.quotedAtMillis))} 고시",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                }
            }

            Spacer(Modifier.height(Space.xl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (parsed != null) {
                    Text(
                        text = "앱에서 열기",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(Radius.pillShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onOpen(currency, parsed.amount) }
                            .padding(horizontal = Space.l, vertical = Space.m),
                    )
                    Spacer(Modifier.width(Space.s))
                }
                Text(
                    text = "닫기",
                    style = MaterialTheme.typography.labelLarge,
                    color = muted,
                    modifier = Modifier
                        .clip(Radius.pillShape)
                        .clickable(onClick = onClose)
                        .padding(horizontal = Space.l, vertical = Space.m),
                )
            }
        }
    }
}
