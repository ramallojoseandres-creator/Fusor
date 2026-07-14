package com.senal.tv.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.SplashGradient
import com.senal.tv.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.86f) }
    val tagAlpha = remember { Animatable(0f) }
    val sweep = remember { Animatable(-1f) }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(700)) }
        launch { logoScale.animateTo(1f, tween(850)) }
        delay(350)
        tagAlpha.animateTo(1f, tween(600))
        launch {
            sweep.animateTo(
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        delay(2100)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashGradient),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset(x = (sweep.value * 280).dp)
                .size(320.dp, 80.dp)
                .blur(48.dp)
                .alpha(0.35f)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, BrandOrange.copy(alpha = 0.55f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SEÑAL",
                style = LocalSenalTypography.current.brand,
                color = TextPrimary,
                modifier = Modifier
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "DISFRUTA SIN PREOCUPACIONES",
                style = LocalSenalTypography.current.tagline,
                color = BrandOrange,
                modifier = Modifier.alpha(tagAlpha.value)
            )
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(3.dp)
                    .alpha(tagAlpha.value * 0.85f)
                    .background(BrandOrange)
            )
        }
    }
}
