package com.senal.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.catalog.CatalogLoadingScreen
import com.senal.tv.ui.components.ReconnectBanner
import com.senal.tv.ui.home.HomeScreen
import com.senal.tv.ui.login.LoginScreen
import com.senal.tv.ui.player.PlayerScreen
import com.senal.tv.ui.splash.SplashScreen
import com.senal.tv.ui.theme.SenalTheme
import com.senal.tv.util.DeviceUi
import com.senal.tv.util.rememberAppContainer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            SenalTheme { SenalRoot() }
        }
    }
}

private sealed interface AppRoute {
    data object Splash : AppRoute
    data object Login : AppRoute
    data object CatalogGate : AppRoute
    data object Home : AppRoute
    data class Player(
        val item: CatalogItem,
        val startPositionMs: Long,
        val neighbors: List<CatalogItem>
    ) : AppRoute
}

@Composable
private fun SenalRoot() {
    val container = rememberAppContainer()
    val token by container.authRepository.tokenFlow.collectAsState(initial = container.tokenStore.cachedToken)
    val netState by container.networkMonitor.state.collectAsState()
    var route by remember { mutableStateOf<AppRoute>(AppRoute.Splash) }
    var splashDone by remember { mutableStateOf(false) }
    var lastChannelAutoPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(token, splashDone) {
        if (!splashDone) return@LaunchedEffect
        route = when {
            token.isNullOrBlank() -> AppRoute.Login
            route is AppRoute.Player -> route
            route is AppRoute.Home -> route
            route is AppRoute.CatalogGate -> route
            else -> AppRoute.CatalogGate
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Transiciones sin flash negro entre Home ↔ Player / Login.
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                (fadeIn(tween(220)) togetherWith fadeOut(tween(160)))
            },
            label = "senalRootNav",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                AppRoute.Splash -> SplashScreen(
                    onFinished = {
                        splashDone = true
                        route = if (token.isNullOrBlank()) AppRoute.Login else AppRoute.CatalogGate
                    }
                )
                AppRoute.Login -> LoginScreen(
                    authRepository = container.authRepository,
                    onLoggedIn = {
                        lastChannelAutoPlayed = false
                        route = AppRoute.CatalogGate
                    }
                )
                AppRoute.CatalogGate -> CatalogLoadingScreen(
                    container = container,
                    onReady = { route = AppRoute.Home }
                )
                AppRoute.Home -> HomeScreen(
                    container = container,
                    autoPlayLastChannel = DeviceUi.autoPlayLastChannelDefault && !lastChannelAutoPlayed,
                    onPlay = { item, start, neighbors ->
                        lastChannelAutoPlayed = true
                        route = AppRoute.Player(
                            item = item,
                            startPositionMs = start,
                            neighbors = neighbors.ifEmpty { listOf(item) }
                        )
                    },
                    onLogout = {
                        lastChannelAutoPlayed = false
                        route = AppRoute.Login
                    }
                )
                is AppRoute.Player -> PlayerScreen(
                    container = container,
                    item = current.item,
                    startPositionMs = current.startPositionMs,
                    neighbors = current.neighbors,
                    onBack = { route = AppRoute.Home }
                )
            }
        }

        ReconnectBanner(
            state = netState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
