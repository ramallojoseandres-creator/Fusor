package com.senal.tv.ui.splash

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.senal.tv.R
import com.senal.tv.ui.components.SenalBrandText
import com.senal.tv.ui.theme.SignalCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Splash cinematográfico (mockup):
 * mosaico difuminado · arco cyan · SEÑAL itálica · «Tu ventana al mundo».
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val musicPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.fromUri("android.resource://${context.packageName}/${R.raw.splash_intro}"),
            )
            volume = 0.82f
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(Unit) { onDispose { musicPlayer.release() } }

    val brandAlpha = remember { Animatable(0f) }
    val brandScale = remember { Animatable(0.9f) }
    val sloganAlpha = remember { Animatable(0f) }
    val arcProgress = remember { Animatable(0f) }

    val infinite = rememberInfiniteTransition(label = "splash")
    val pulse by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        launch { arcProgress.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
        launch {
            delay(200)
            brandAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        launch {
            delay(200)
            brandScale.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            delay(750)
            sloganAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        delay(3600)
        musicPlayer.stop()
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Ambiente: fondo + mosaico de “pantallas” difuminado a los lados.
        Image(
            painter = painterResource(R.mipmap.main_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.38f }
        )
        Canvas(Modifier.fillMaxSize()) {
            val cardW = size.width * 0.16f
            val cardH = size.height * 0.22f
            val gap = 14.dp.toPx()
            fun drawColumn(x: Float, alphaMul: Float) {
                var y = size.height * 0.08f
                repeat(4) { i ->
                    val a = (0.18f - i * 0.02f) * alphaMul
                    drawRoundRect(
                        color = Color.White.copy(alpha = a.coerceAtLeast(0.04f)),
                        topLeft = Offset(x, y),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                    // fake content bar
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                SignalCyan.copy(alpha = a * 0.35f),
                                Color.Transparent
                            )
                        ),
                        topLeft = Offset(x + 10.dp.toPx(), y + cardH * 0.7f),
                        size = Size(cardW * 0.55f, 6.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    y += cardH + gap
                }
            }
            drawColumn(size.width * 0.04f, 0.9f)
            drawColumn(size.width * 0.04f + cardW + gap, 0.55f)
            drawColumn(size.width * 0.8f, 0.55f)
            drawColumn(size.width * 0.8f + cardW * 0.15f, 0.9f)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(0.55f),
                            Color.Black.copy(0.92f)
                        )
                    )
                )
        )

        // Arco cyan a la izquierda del wordmark.
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width * 0.42f
            val cy = size.height * 0.48f
            val r = min(size.width, size.height) * 0.28f * (0.85f + 0.15f * arcProgress.value)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        SignalCyan.copy(alpha = 0f),
                        SignalCyan.copy(alpha = 0.95f * pulse),
                        SignalCyan.copy(alpha = 0.35f),
                        SignalCyan.copy(alpha = 0f)
                    ),
                    center = Offset(cx, cy)
                ),
                startAngle = 200f,
                sweepAngle = 150f * arcProgress.value,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
            // Soft glow disc
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SignalCyan.copy(alpha = 0.12f * pulse),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = r * 1.4f
                ),
                radius = r * 1.4f,
                center = Offset(cx, cy)
            )
        }

        // Scanlines sutiles
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 0.06f }) {
            var y = 0f
            val step = 3.dp.toPx()
            while (y < size.height) {
                drawLine(
                    color = Color.White,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += step
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp)
                .graphicsLayer {
                    alpha = brandAlpha.value
                    scaleX = brandScale.value
                    scaleY = brandScale.value
                }
        ) {
            SenalBrandText(
                size = 78.sp,
                letterSpacing = 8.sp,
                italic = true,
                glow = true,
                center = true
            )
            Spacer(Modifier.height(18.dp))
            androidx.compose.material3.Text(
                text = "Tu ventana al mundo",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 18.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.graphicsLayer { alpha = sloganAlpha.value }
            )
        }
    }
}
