package com.pricehere.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// 자주 쓰는 통화 고르기 시트(v2.13 #2). 정보 탭 '자주 쓰는 통화'와 홈 통화 칸 길게 누르기에서 연다.

/**
 * 전체 통화를 체크로 고른다. 최대 4개, 체크한 순서대로 홈 칸에 놓인다(위 선택 줄이 그 순서).
 * 4개가 차면 나머지는 흐려지고, 누르면 안내만 한다. '완료'를 눌러야 반영되고, 닫으면 그대로다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSheet(current: List<Currency>, onDone: (List<Currency>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = rememberView()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picked = remember { mutableStateListOf<Currency>().apply { addAll(current) } }
    var query by remember { mutableStateOf("") }
    val full = picked.size >= Currency.MAX_FAVORITES

    val matches = Currency.entries.filter { c ->
        query.isBlank() || listOf(c.code, c.koreanName).any { it.contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "자주 쓰는 통화",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "최대 ${Currency.MAX_FAVORITES}개 · 고른 순서대로 홈 칸에 놓임",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "완료",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (picked.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = picked.isNotEmpty()) {
                            Haptic.confirm(view)
                            onDone(picked.toList())
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            // 고른 순서. 칩을 누르면 빠진다.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(Currency.MAX_FAVORITES) { i ->
                    val c = picked.getOrNull(i)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (c != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable(enabled = c != null) { c?.let { picked.remove(it) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = c?.let { "${it.flag} ${it.code} ✕" } ?: "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (c != null) FontWeight.Bold else FontWeight.Normal,
                            color = if (c != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(20) },
                placeholder = { Text("나라·통화 검색 (예: 대만, TWD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                var lastRegion: String? = null
                matches.forEach { c ->
                    if (query.isBlank() && c.region != lastRegion) {
                        val region = c.region
                        item(key = "h-$region") {
                            Text(
                                text = region,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            )
                        }
                        lastRegion = region
                    }
                    item(key = c.code) {
                        val on = c in picked
                        val locked = full && !on
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    when {
                                        on -> picked.remove(c)
                                        locked -> Toast.makeText(context, "최대 4개입니다. 하나를 빼고 고르세요", Toast.LENGTH_SHORT).show()
                                        else -> picked.add(c)
                                    }
                                }
                                .alpha(if (locked) 0.4f else 1f)
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
                            Text(c.flag, fontSize = Num.flag)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = c.code,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(44.dp),
                            )
                            Text(
                                text = c.koreanName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            if (c.basicOnly) {
                                Text(
                                    text = "기본 정보만",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "기본값으로 (USD · EUR · JPY · CZK)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        picked.clear()
                        picked.addAll(Currency.DEFAULT_FAVORITES)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/** 정보 탭의 자주 쓰는 통화 칸. 지금 4개를 보여주고 누르면 고르기 시트. */
@Composable
fun FavoritesCard(current: List<Currency>, onOpen: () -> Unit) {
    SectionCard(background = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "자주 쓰는 통화",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = current.joinToString("  ") { "${it.flag} ${it.code}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = "바꾸기 ›",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp),
            )
        }
    }
}
