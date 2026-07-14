package com.senal.tv.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senal.tv.ui.theme.BrandOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cinematic splash modeled on FLUJO's energy (lens flare + scale + glow),
 * branded as SEÑAL — not a static screen.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.72f) }
    val tagAlpha = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }
    val infinite = rememberInfiniteTransition(label = "splashInfinity")
    val flareX by infinite.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flare"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(500)) }
        launch { logoScale.animateTo(1.08f, tween(700, easing = FastOutSlowInEasing)) }
        delay(280)
        launch { glow.animateTo(1f, tween(600)) }
        delay(200)
        launch { logoScale.animateTo(1f, tween(350)) }
        tagAlpha.animateTo(1f, tween(500))
        delay(2400)
        exitAlpha.animateTo(0f, tween(420))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { alpha = exitAlpha.value },
        contentAlignment = Alignment.Center
    ) {
        // Ambient radial glow behind logo
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandOrange.copy(alpha = 0.28f * glow.value * pulse),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.45f
                ),
                radius = size.minDimension * 0.45f,
                center = Offset(cx, cy)
            )
            // Horizontal lens-flare streak (FLUJO signature)
            val y = cy + size.height * 0.08f
            val x = cx + flareX * size.width * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFB347).copy(alpha = 0.85f * glow.value),
                        BrandOrange.copy(alpha = 0.35f * glow.value),
                        Color.Transparent
                    ),
                    center = Offset(x, y),
                    radius = 140f
                ),
                radius = 140f,
                center = Offset(x, y)
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        BrandOrange.copy(alpha = 0.55f * glow.value * pulse),
                        Color(0xFFFFE0B2).copy(alpha = 0.9f * glow.value),
                        BrandOrange.copy(alpha = 0.55f * glow.value * pulse),
                        Color.Transparent
                    ),
                    startX = x - 420f,
                    endX = x + 420f
                ),
                topLeft = Offset(x - 420f, y - 4f),
                size = androidx.compose.ui.geometry.Size(840f, 8f)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SEÑAL",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 10.sp,
                    shadow = Shadow(
                        color = BrandOrange.copy(alpha = 0.9f * glow.value),
                        blurRadius = 28f * pulse
                    )
                ),
                modifier = Modifier
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    }
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .width(160.dp)
                    .height(3.dp)
                    .alpha(tagAlpha.value)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, BrandOrange, Color.Transparent)
                        )
                    )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "DISFRUTA SIN PREOCUPACIONES",
                color = BrandOrange,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
                modifier = Modifier.alpha(tagAlpha.value)
            )
        }
    }
}
