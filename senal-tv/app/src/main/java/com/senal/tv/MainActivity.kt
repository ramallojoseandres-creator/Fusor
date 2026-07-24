package com.senal.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.home.HomeScreen
import com.senal.tv.ui.login.LoginScreen
import com.senal.tv.ui.player.PlayerScreen
import com.senal.tv.ui.splash.SplashScreen
import com.senal.tv.ui.theme.SenalTheme
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
    var route by remember { mutableStateOf<AppRoute>(AppRoute.Splash) }
    var splashDone by remember { mutableStateOf(false) }
    var sessionChecked by remember { mutableStateOf(false) }

    LaunchedEffect(splashDone) {
        if (!splashDone || sessionChecked) return@LaunchedEffect
        if (!token.isNullOrBlank()) {
            container.authRepository.validateSession()
        }
        sessionChecked = true
        route = when {
            container.tokenStore.cachedToken.isNullOrBlank() -> AppRoute.Login
            else -> AppRoute.Home
        }
    }

    LaunchedEffect(token, splashDone, sessionChecked) {
        if (!splashDone || !sessionChecked) return@LaunchedEffect
        route = when {
            token.isNullOrBlank() -> AppRoute.Login
            route is AppRoute.Player -> route
            route is AppRoute.Login && !token.isNullOrBlank() -> AppRoute.Home
            else -> route
        }
    }

    when (val current = route) {
        AppRoute.Splash -> SplashScreen(
            onFinished = {
                splashDone = true
            }
        )
        AppRoute.Login -> LoginScreen(
            authRepository = container.authRepository,
            onLoggedIn = { route = AppRoute.Home }
        )
        AppRoute.Home -> HomeScreen(
            container = container,
            onPlay = { item, start, neighbors ->
                route = AppRoute.Player(
                    item = item,
                    startPositionMs = start,
                    neighbors = neighbors.ifEmpty { listOf(item) }
                )
            },
            onLogout = { route = AppRoute.Login }
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
