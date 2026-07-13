package com.senal.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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
            SenalTheme {
                SenalRoot()
            }
        }
    }
}

private sealed interface AppRoute {
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
    var route by remember { mutableStateOf<AppRoute>(if (token.isNullOrBlank()) AppRoute.Login else AppRoute.Home) }

    // Keep route aligned with auth state when logout clears token.
    androidx.compose.runtime.LaunchedEffect(token) {
        if (token.isNullOrBlank() && route !is AppRoute.Login) {
            route = AppRoute.Login
        } else if (!token.isNullOrBlank() && route is AppRoute.Login) {
            route = AppRoute.Home
        }
    }

    when (val current = route) {
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
