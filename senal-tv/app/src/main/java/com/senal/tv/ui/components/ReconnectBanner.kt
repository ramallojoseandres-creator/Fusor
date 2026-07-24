package com.senal.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.senal.tv.network.NetworkMonitor
import com.senal.tv.ui.focus.FocusTurquoise

/**
 * Barra superior flotante: no congela ni cierra la app si cae el servidor.
 */
@Composable
fun ReconnectBanner(
    state: NetworkMonitor.UiState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.reconnecting,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(220, easing = FastOutSlowInEasing),
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(180),
        ) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .zIndex(40f),
    ) {
        val pulse = rememberInfiniteTransition(label = "reconnectPulse")
        val alpha by pulse.animateFloat(
            initialValue = 0.65f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "reconnectAlpha",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xEE001018), Color(0xCC000000)),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .background(Color(0x3300E5FF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .alpha(alpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = FocusTurquoise,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = if (state.attempt > 0) {
                        "Reconectando con el servidor de Señal… (intento ${state.attempt})"
                    } else {
                        "Reconectando con el servidor de Señal…"
                    },
                    color = FocusTurquoise,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}
