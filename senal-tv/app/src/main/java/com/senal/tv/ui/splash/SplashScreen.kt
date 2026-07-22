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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.senal.tv.R
import com.senal.tv.ui.theme.BrandTurquoise
import com.senal.tv.ui.theme.Graphite
import com.senal.tv.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Bienvenida cinematográfica:
 * - Anillos / logo animados en Compose (sustituye el vídeo rudimentario)
 * - Música de apertura (splash_intro.ogg) como sonic logo
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
            volume = 0.85f
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            musicPlayer.release()
        }
    }

    val ringProgress = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val sloganAlpha = remember { Animatable(0f) }
    val glowPulse = remember { Animatable(0.35f) }

    val infinite = rememberInfiniteTransition(label = "splashPulse")
    val shimmer by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer",
    )
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    LaunchedEffect(Unit) {
        launch {
            ringProgress.animateTo(1f, tween(2200, easing = FastOutSlowInEasing))
        }
        launch {
            delay(350)
            logoAlpha.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            delay(350)
            logoScale.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        launch {
            delay(1100)
            sloganAlpha.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            glowPulse.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
        }
        delay(5800)
        musicPlayer.stop()
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A2A3A),
                        Graphite,
                        Color(0xFF02060A),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = min(size.width, size.height) * 0.42f
            val teal = Color(0xFF00E5FF)
            val soft = Color(0xFF00E5C8)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        teal.copy(alpha = 0.18f * glowPulse.value * shimmer),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = maxR * 1.15f,
                ),
                radius = maxR * 1.15f,
                center = Offset(cx, cy),
            )

            for (i in 0 until 4) {
                val t = ((ringProgress.value - i * 0.12f).coerceIn(0f, 1f))
                val radius = maxR * (0.35f + i * 0.18f) * (0.55f + 0.45f * t)
                val alpha = (0.55f - i * 0.1f) * t * shimmer
                drawCircle(
                    color = (if (i % 2 == 0) teal else soft).copy(alpha = alpha),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = (2.2f - i * 0.25f).dp.toPx()),
                )
            }

            val arcR = maxR * 0.78f
            val start = spin
            drawArc(
                color = teal.copy(alpha = 0.55f * ringProgress.value),
                startAngle = start,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR),
                size = Size(arcR * 2, arcR * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = soft.copy(alpha = 0.35f * ringProgress.value),
                startAngle = start + 180f,
                sweepAngle = 48f,
                useCenter = false,
                topLeft = Offset(cx - arcR * 0.92f, cy - arcR * 0.92f),
                size = Size(arcR * 1.84f, arcR * 1.84f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            for (i in 0 until 6) {
                val ang = Math.toRadians((spin + i * 60.0) % 360.0)
                val r = maxR * 0.62f
                val x = cx + (cos(ang) * r).toFloat()
                val y = cy + (sin(ang) * r).toFloat()
                drawCircle(
                    color = teal.copy(alpha = 0.7f * ringProgress.value),
                    radius = 3.5.dp.toPx(),
                    center = Offset(x, y),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .graphicsLayer {
                    alpha = logoAlpha.value
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                },
        ) {
            Text(
                text = "SEÑAL",
                color = BrandTurquoise,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.92f + 0.08f * shimmer),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "TU VENTANA AL MUNDO",
                color = TextMuted,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(sloganAlpha.value),
            )
        }
    }
}
