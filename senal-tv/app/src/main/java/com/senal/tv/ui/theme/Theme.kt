package com.senal.tv.ui.theme

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

/** Near-black canvas used in Magis-style IPTV UIs. */
val Graphite = Color(0xFF050508)
val GraphiteElevated = Color(0xFF121218)
val GraphiteCard = Color(0xFF18181F)

/** Brand / focus accents from reference UX (orange) + brief accents. */
val BrandOrange = Color(0xFFFF8C00)
val BrandOrangeHot = Color(0xFFFF6A00)
val Violet = Color(0xFF7C3AED)
val Teal = Color(0xFF14B8A6)
val TilePurple = Color(0xFF8B5CF6)
val TileCyan = Color(0xFF22D3EE)
val TileGreen = Color(0xFF22C55E)
val TileCoral = Color(0xFFFF6B7A)
val FocusWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF7F7FB)
val TextMuted = Color(0xFFA8AAB8)
val Danger = Color(0xFFFF6B7A)

val SplashGradient = Brush.radialGradient(
    colors = listOf(Color(0xFF1A1208), Graphite, Color(0xFF050508))
)

data class SenalTypography(
    val brand: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 64.sp,
        letterSpacing = 8.sp,
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
    ),
    val tagline: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 2.sp,
        color = TextPrimary
    )
)

val LocalSenalTypography = staticCompositionLocalOf { SenalTypography() }

private val SenalDarkScheme = darkColorScheme(
    primary = BrandOrange,
    onPrimary = Color.Black,
    secondary = Teal,
    onSecondary = Graphite,
    background = Graphite,
    onBackground = TextPrimary,
    surface = GraphiteElevated,
    onSurface = TextPrimary,
    border = BrandOrange.copy(alpha = 0.45f)
)

@Composable
fun SenalTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSenalTypography provides SenalTypography()) {
        MaterialTheme(colorScheme = SenalDarkScheme, content = content)
    }
}
