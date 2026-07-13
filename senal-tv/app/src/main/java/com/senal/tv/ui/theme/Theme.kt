package com.senal.tv.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Graphite = Color(0xFF0B0B0F)
val GraphiteElevated = Color(0xFF14141A)
val GraphiteCard = Color(0xFF18181F)
val Violet = Color(0xFF7C3AED)
val VioletSoft = Color(0xFF9B6BFF)
val Teal = Color(0xFF14B8A6)
val TealSoft = Color(0xFF2DD4BF)
val TextPrimary = Color(0xFFF7F7FB)
val TextMuted = Color(0xFFA8AAB8)
val Danger = Color(0xFFFF6B7A)
val FocusRing = Color(0xFFE9D5FF)

val SenalGradient = Brush.linearGradient(
    listOf(Color(0xFF15101F), Graphite, Color(0xFF0B1414))
)

val HeroGradient = Brush.radialGradient(
    colors = listOf(Color(0xFF2A1848), Color(0xFF0F1C1C), Graphite)
)

val MotionFast = tween<Float>(durationMillis = 180)
val MotionMedium = tween<Float>(durationMillis = 280)

data class SenalTypography(
    val brand: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 54.sp,
        letterSpacing = 6.sp,
        color = TextPrimary
    ),
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = TextPrimary
    ),
    val subtitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = TextMuted
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = TextPrimary
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = TextMuted
    ),
    val button: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = TextPrimary
    )
)

val LocalSenalTypography = staticCompositionLocalOf { SenalTypography() }

private val SenalDarkScheme = darkColorScheme(
    primary = Violet,
    onPrimary = TextPrimary,
    secondary = Teal,
    onSecondary = Graphite,
    background = Graphite,
    onBackground = TextPrimary,
    surface = GraphiteElevated,
    onSurface = TextPrimary,
    border = VioletSoft.copy(alpha = 0.35f)
)

@Composable
fun SenalTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSenalTypography provides SenalTypography()) {
        MaterialTheme(
            colorScheme = SenalDarkScheme,
            content = content
        )
    }
}
