package com.pricehere.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedContent
import android.content.Intent
import android.os.Bundle
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.view.View
import android.os.SystemClock
import android.os.Build
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.runtime.CompositionLocalProvider
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
        if (savedInstanceState == null) holdSplash()
        if (savedInstanceState == null) handleExternal(intent)
        setContent {
            val state by viewModel.state.collectAsState()
            PriceHereTheme(state.themeMode) {
                AppShell(viewModel, state)
            }
        }
    }

    /**
     * v2.9 B안 "로고 착지": 가운데 ₩ 타일이 잠깐(약 0.4초) 머문 뒤 헤더 로고 자리로 줄어들며 날아가 앉고,
     * 앉는 순간 홈 화면의 나머지가 아래에서 떠오른다. Android 12 미만은 시작 화면 API가 없어 그대로 둔다.
     */
    private fun holdSplash() {
        if (Build.VERSION.SDK_INT < 31) return
        val start = SystemClock.uptimeMillis()
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (SystemClock.uptimeMillis() - start < 420) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        SplashGate.done.value = false
        // 시작 화면이 어떤 이유로든 안 뜨면 홈이 투명한 채 남지 않도록 1.5초 뒤 강제로 연다.
        content.postDelayed({ SplashGate.done.value = true }, 1500)
        splashScreen.setOnExitAnimationListener { splash ->
            val icon = splash.iconView
            val target = SplashGate.headerTile
            if (icon == null || target == null || icon.width == 0) {
                SplashGate.done.value = true
                splash.animate().alpha(0f).setDuration(200).withEndAction { splash.remove() }.start()
                return@setOnExitAnimationListener
            }
            // 아이콘 뷰 안에서 타일이 차지하는 크기(splash_icon.xml의 48/108).
            val loc = IntArray(2).also { icon.getLocationInWindow(it) }
            val tile = icon.width * SPLASH_TILE_FRACTION
            val cx = loc[0] + icon.width / 2f
            val cy = loc[1] + icon.height / 2f
            val scale = target.width() / tile
            icon.animate()
                .translationX(target.centerX() - cx)
                .translationY(target.centerY() - cy)
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(520)
                .setInterpolator(PathInterpolator(0.65f, 0f, 0.35f, 1f))
                .withEndAction {
                    // 헤더 로고와 정확히 겹친 순간 나머지를 띄우고, 시작 화면은 짧게 걷어낸다.
                    SplashGate.done.value = true
                    splash.animate().alpha(0f).setDuration(160).withEndAction { splash.remove() }.start()
                }
                .start()
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
        /** splash_icon.xml에서 타일이 캔버스에서 차지하는 비율(48/108). */
        private const val SPLASH_TILE_FRACTION = 48f / 108f

        const val EXTRA_CURRENCY = "com.pricehere.app.CURRENCY"
        const val EXTRA_AMOUNT = "com.pricehere.app.AMOUNT"
        /** true면 EXTRA_AMOUNT 는 원화다. 원 → 외화 방향으로 연다. */
        const val EXTRA_FROM_KRW = "com.pricehere.app.FROM_KRW"
        /** 위젯 계산기의 입력 그대로(식 포함, ASCII 연산자). 있으면 EXTRA_AMOUNT 대신 쓴다. */
        const val EXTRA_INPUT = "com.pricehere.app.INPUT"
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppShell(viewModel: RatesViewModel, state: UiState) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val snackbar = remember { SnackbarHostState() }
    // 홈에서 버전 칩을 누르면 정보 탭의 버전 카드를 펼친 상태로 열어준다.
    var openVersion by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.externalTick) {
        if (state.externalTick > 0) tab = Tab.HOME
    }

    // 키보드가 떠 있으면 탭 바를 숨긴다. 연산자 줄이 그 자리에 붙는다.
    val imeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    // 저장 태그가 날아갈 곳(저장 탭)과, 탭 바 뒤로 흐리게 비칠 화면 내용.
    val fly = remember { FlyToSaved() }
    LaunchedEffect(imeVisible) { if (imeVisible) fly.target = null }
    val backdropLayer = rememberGraphicsLayer()
    val backdrop = remember(backdropLayer) { Backdrop(backdropLayer) }

    CompositionLocalProvider(LocalFlyToSaved provides fly) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = NavClearance - 16.dp)) },
    ) { inset ->
        // consume 해 두면 홈 화면의 imePadding 이 시스템 내비게이션 높이를 빼고 키보드 위에 딱 붙는다.
        Box(Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset)) {
            Box(Modifier.fillMaxSize().recordBackdrop(backdrop)) {
            SharedTransitionLayout {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "tab",
            ) { current ->
            CompositionLocalProvider(
                LocalSharedScope provides this@SharedTransitionLayout,
                LocalTabScope provides this,
            ) {
            when (current) {
                Tab.HOME -> ConverterScreen(
                    viewModel = viewModel,
                    state = state,
                    onOpenVersion = {
                        openVersion = true
                        tab = Tab.INFO
                    },
                    onOpenSaved = { tab = Tab.SAVED },
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
            }
            // 떠 있는 글래스 탭 바. 내용이 그 뒤로 비쳐 지나간다.
            AnimatedVisibility(
                visible = !imeVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                GlassTabBar(
                    current = tab,
                    // 날아가는 중인 태그는 아직 세지 않는다. 도착하는 순간 숫자가 올라가며 배지가 튄다.
                    savedCount = (state.saved.size - fly.flights.size).coerceAtLeast(0),
                    hasUpdate = state.hasUpdate,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    backdrop = backdrop,
                    onSavedAnchor = { if (!imeVisible) fly.target = it },
                )
            }
            FlyOverlay(fly)
        }
    }
    }
}
