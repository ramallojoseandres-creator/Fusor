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
 * Tras el login siempre intenta sincronizar del VPS (con fallback a caché/disco).
 * Así no te quedas sin canales el día del partido.
 */
@Composable
fun CatalogLoadingScreen(
    container: AppContainer,
    onReady: () -> Unit
) {
    var message by remember { mutableStateOf("Sincronizando canales SEÑAL…") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        busy = true
        error = null
        message = "Sincronizando canales SEÑAL…"

        // Red primero (lista viva del VPS); si falla, caché/asset.
        var result = container.playlistSync.downloadFirstTimeIfNeeded()
        if (result.channels <= 0 || result.error != null) {
            message = "Probando copia guardada…"
            val local = container.playlistSync.loadLocalOnly()
            if (local.channels > 0) {
                result = local
            }
        }

        if (result.channels <= 0) {
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
                    fontSize = 32.sp,
                    letterSpacing = 6.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Final del Mundial",
                    color = TextMuted,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (busy) {
                    LoadingPulse(message)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Descargando catálogo del servidor…",
                        color = TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                } else if (error != null) {
                    Text(message, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = TextMuted)
                    Spacer(modifier = Modifier.height(20.dp))
                    FocusableButton(
                        label = "Reintentar",
                        onClick = { attempt += 1 }
                    )
                } else {
                    Text(message, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
