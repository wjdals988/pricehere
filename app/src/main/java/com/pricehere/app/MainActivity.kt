package com.pricehere.app

import android.content.Intent
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

enum class Tab(val label: String) { HOME("홈"), SAVED("저장"), INFO("정보") }

class MainActivity : ComponentActivity() {

    private val viewModel: RatesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleExternal(intent)
        setContent {
            val state by viewModel.state.collectAsState()
            PriceHereTheme(state.themeMode) {
                AppShell(viewModel, state)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExternal(intent)
    }

    override fun onResume() {
        super.onResume()
        // 첫 실행에 오프라인이었다면 init의 새로고침은 이미 실패했다. 돌아올 때마다 한 번 더 시도한다.
        // 네트워크 콜백은 ACCESS_NETWORK_STATE 권한이 필요해서 쓰지 않는다.
        if (viewModel.state.value.snapshot == null) viewModel.refresh()
        // 여행지에 도착해 처음 열면 그 나라 통화로. 위치 권한 없이 통신망 국가만 본다.
        val country = getSystemService(TelephonyManager::class.java)?.networkCountryIso.orEmpty()
        viewModel.syncLocalCurrency(country)?.let { currency ->
            val name = Locale("", country).getDisplayCountry(Locale.KOREAN)
            Toast.makeText(this, "$name 통신망이라 ${currency.code}로 바꿨습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleExternal(intent: Intent?) {
        val code = intent?.getStringExtra(EXTRA_CURRENCY) ?: return
        val input = intent.getStringExtra(EXTRA_INPUT)
        if (input != null) {
            viewModel.openInput(Currency.of(code), input)
        } else {
            val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0).takeIf { it > 0 } ?: return
            viewModel.openExternal(Currency.of(code), amount, intent.getBooleanExtra(EXTRA_FROM_KRW, false))
        }
        intent.removeExtra(EXTRA_CURRENCY)
    }

    companion object {
        const val EXTRA_CURRENCY = "com.pricehere.app.CURRENCY"
        const val EXTRA_AMOUNT = "com.pricehere.app.AMOUNT"
        /** true면 EXTRA_AMOUNT 는 원화다. 원 → 외화 방향으로 연다. */
        const val EXTRA_FROM_KRW = "com.pricehere.app.FROM_KRW"
        /** 위젯 계산기의 입력 그대로(식 포함, ASCII 연산자). 있으면 EXTRA_AMOUNT 대신 쓴다. */
        const val EXTRA_INPUT = "com.pricehere.app.INPUT"
    }
}

@Composable
private fun AppShell(viewModel: RatesViewModel, state: UiState) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val snackbar = remember { SnackbarHostState() }
    // 홈에서 버전 칩을 누르면 정보 탭의 버전 카드를 펼친 상태로 열어준다.
    var openVersion by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.externalTick) {
        if (state.externalTick > 0) tab = Tab.HOME
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomTabs(tab, state.saved.size, state.hasUpdate) { tab = it } },
    ) { inset ->
        // consume 해 두면 홈 화면의 imePadding 이 하단 탭 높이를 빼고 키보드 위에 딱 붙는다.
        Box(Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset)) {
            when (tab) {
                Tab.HOME -> ConverterScreen(
                    viewModel = viewModel,
                    state = state,
                    onOpenVersion = {
                        openVersion = true
                        tab = Tab.INFO
                    },
                )

                Tab.SAVED -> SavedScreen(viewModel, state, snackbar)
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

                        Tab.SAVED -> TagIcon(
                            if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        Tab.INFO -> Box {
                            InfoIcon(
                                if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
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
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
