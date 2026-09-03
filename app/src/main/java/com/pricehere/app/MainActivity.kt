package com.pricehere.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class Tab(val label: String) { HOME("홈"), SAVED("저장"), INFO("정보") }

class MainActivity : ComponentActivity() {

    private val viewModel: RatesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()
            PriceHereTheme(state.themeMode) {
                AppShell(viewModel, state)
            }
        }
    }
}

@Composable
private fun AppShell(viewModel: RatesViewModel, state: UiState) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // 홈에서 버전 칩을 누르면 정보 탭의 버전 카드를 펼친 상태로 열어준다.
    var openVersion by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomTabs(tab, state.saved.size, state.hasUpdate) { tab = it } },
    ) { inset ->
        Box(Modifier.fillMaxSize().padding(inset)) {
            when (tab) {
                Tab.HOME -> ConverterScreen(
                    viewModel = viewModel,
                    state = state,
                    onOpenVersion = {
                        openVersion = true
                        tab = Tab.INFO
                    },
                )

                Tab.SAVED -> SavedScreen(viewModel, state)
                Tab.INFO -> InfoScreen(
                    viewModel = viewModel,
                    state = state,
                    openVersion = openVersion,
                    onVersionOpened = { openVersion = false },
                )
            }
        }
    }
}

@Composable
private fun BottomTabs(
    current: Tab,
    savedCount: Int,
    hasUpdate: Boolean,
    onSelect: (Tab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    when (tab) {
                        Tab.HOME -> SwapIcon(
                            if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            sizeDp = 19,
                        )

                        Tab.SAVED -> Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )

                        Tab.INFO -> Box {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            if (hasUpdate) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-2).dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Trend.rising)
                                )
                            }
                        }
                    }
                },
                label = {
                    Text(
                        text = if (tab == Tab.SAVED && savedCount > 0) {
                            "${tab.label} $savedCount"
                        } else {
                            tab.label
                        },
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
