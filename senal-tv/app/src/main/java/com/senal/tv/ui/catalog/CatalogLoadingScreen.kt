package com.senal.tv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senal.tv.AppContainer
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.LoadingPulse
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary

/**
 * Puerta del catálogo:
 * - Si ya hay lista en disco → carga local y entra al home (rápido).
 * - Si es la primera vez → descarga UNA vez con mensaje
 *   «Cargando todos los canales…» y solo entonces deja usar la app.
 */
@Composable
fun CatalogLoadingScreen(
    container: AppContainer,
    onReady: () -> Unit
) {
    val hadCache = remember { container.playlistSync.hasLocalCache() }
    var message by remember {
        mutableStateOf(
            if (hadCache) "Abriendo lista guardada…" else "Cargando todos los canales…"
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        busy = true
        error = null
        val hasCache = container.playlistSync.hasLocalCache()
        message = if (hasCache) {
            "Abriendo lista guardada…"
        } else {
            "Cargando todos los canales…"
        }

        val result = if (hasCache) {
            container.playlistSync.loadLocalOnly()
        } else {
            container.playlistSync.downloadFirstTimeIfNeeded()
        }

        if (result.error != null || result.channels <= 0) {
            val fallback = container.playlistSync.loadLocalOnly()
            if (fallback.channels > 0 && fallback.error == null) {
                busy = false
                onReady()
                return@LaunchedEffect
            }
            busy = false
            error = result.error
                ?: "No hay canales. Revisa el servidor o pulsa Reintentar."
            message = "No se pudo cargar la lista"
            return@LaunchedEffect
        }

        message = "${result.channels} canales listos"
        busy = false
        onReady()
    }

    SenalBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .background(GraphiteCard.copy(alpha = 0.94f), RoundedCornerShape(28.dp))
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "SEÑAL",
                    color = BrandOrange,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (busy) {
                    LoadingPulse(message)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (hadCache || container.playlistSync.hasLocalCache()) {
                            "Usa la copia guardada en este dispositivo"
                        } else {
                            "Solo la primera vez. Luego queda guardada."
                        },
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                } else {
                    val err = error
                    if (err != null) {
                        Text(text = message, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = err, color = Color(0xFFFF8A80))
                        Spacer(modifier = Modifier.height(20.dp))
                        FocusableButton(
                            label = "Reintentar",
                            onClick = { attempt += 1 },
                            primary = true
                        )
                    }
                }
            }
        }
    }
}
