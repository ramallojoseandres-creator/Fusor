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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Splash SEÑAL: marca héroe + un eslogan + onda de señal.
 * Corto (~3.8 s) con sonic logo; sin UI extra.
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
            volume = 0.8f
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose { musicPlayer.release() }
    }

    val ringProgress = remember { Animatable(0f) }
    val brandAlpha = remember { Animatable(0f) }
    val brandScale = remember { Animatable(0.88f) }
    val sloganAlpha = remember { Animatable(0f) }

    val infinite = rememberInfiniteTransition(label = "splash")
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    LaunchedEffect(Unit) {
        launch { ringProgress.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) }
        launch {
            delay(180)
            brandAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        launch {
            delay(180)
            brandScale.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            delay(700)
            sloganAlpha.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        delay(3800)
        musicPlayer.stop()
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0B1E2E),
                        Graphite,
                        Color(0xFF010305),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = min(size.width, size.height) * 0.38f
            val teal = BrandTurquoise

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        teal.copy(alpha = 0.14f * pulse),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = maxR * 1.35f,
                ),
                radius = maxR * 1.35f,
                center = Offset(cx, cy),
            )

            val t = ringProgress.value
            for (i in 0 until 3) {
                val radius = maxR * (0.48f + i * 0.22f) * (0.7f + 0.3f * t)
                drawCircle(
                    color = teal.copy(alpha = (0.28f - i * 0.06f) * t * pulse),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = (2.4f - i * 0.4f).dp.toPx()),
                )
            }

            val arcR = maxR * 0.86f
            drawArc(
                color = teal.copy(alpha = 0.7f * t),
                startAngle = spin,
                sweepAngle = 64f,
                useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR),
                size = Size(arcR * 2, arcR * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = teal.copy(alpha = 0.35f * t),
                startAngle = spin + 190f,
                sweepAngle = 42f,
                useCenter = false,
                topLeft = Offset(cx - arcR * 0.9f, cy - arcR * 0.9f),
                size = Size(arcR * 1.8f, arcR * 1.8f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .graphicsLayer {
                    alpha = brandAlpha.value
                    scaleX = brandScale.value
                    scaleY = brandScale.value
                },
        ) {
            Text(
                text = "SEÑAL",
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tu ventana al mundo",
                color = BrandTurquoise.copy(alpha = 0.92f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = sloganAlpha.value },
            )
        }
    }
}
