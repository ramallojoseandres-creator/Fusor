package com.senal.tv.network

import com.senal.tv.ServerConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Monitor reactivo del panel SEÑAL ([ServerConfig] health).
 * Emite [UiState] para una barra "Reconectando..." sin tumbar la UI.
 */
class NetworkMonitor(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    data class UiState(
        val online: Boolean = true,
        val reconnecting: Boolean = false,
        val attempt: Int = 0,
        val lastError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var loopJob: Job? = null

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            var failStreak = 0
            while (isActive) {
                val ok = pingHealth()
                if (ok) {
                    failStreak = 0
                    _state.value = UiState(online = true, reconnecting = false, attempt = 0)
                    delay(12_000)
                } else {
                    failStreak++
                    val backoff = (1_000L * (1L shl (failStreak - 1).coerceAtMost(5)))
                        .coerceAtMost(20_000L)
                    _state.value = UiState(
                        online = false,
                        reconnecting = true,
                        attempt = failStreak,
                        lastError = "sin respuesta de ${ServerConfig.SERVER_IP}",
                    )
                    delay(backoff)
                }
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    private fun pingHealth(): Boolean = runCatching {
        val req = Request.Builder()
            .url(ServerConfig.healthUrl())
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
