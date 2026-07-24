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
 * Puerta del catálogo estilo Flujo:
 * - La lista va **dentro del APK** (asset) → carga local, sin servidor.
 * - Solo la primera lectura del asset tarda un instante; luego disco/memoria.
 * - «Actualizar lista» en Ajustes es lo único que usa red.
 */
@Composable
fun CatalogLoadingScreen(
    container: AppContainer,
    onReady: () -> Unit
) {
    var message by remember { mutableStateOf("Abriendo canales…") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        busy = true
        error = null
        message = "Abriendo canales…"

        // Nunca espera al servidor aquí (Flujo = lista embebida).
        val result = container.playlistSync.readyLocalCatalog()

        if (result.error != null || result.channels <= 0) {
            busy = false
            error = result.error ?: "No hay lista de canales en la app"
            message = "Sin catálogo"
            return@LaunchedEffect
        }

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
                    .width(420.dp)
                    .background(GraphiteCard.copy(alpha = 0.94f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                        text = "SEÑAL",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        letterSpacing = 6.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (busy) {
                        LoadingPulse(message)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Lista en el dispositivo",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                } else {
                    val err = error
                    if (err != null) {
                        Text(text = message, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = err, color = Color(0xFFFF8A80))
                        Spacer(modifier = Modifier.height(16.dp))
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
