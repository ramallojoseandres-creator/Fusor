package com.senal.tv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senal.tv.ui.theme.SignalCyan

/**
 * Wordmark SEÑAL — la Ñ en cyan (identidad de los mockups).
 */
@Composable
fun SenalBrandText(
    modifier: Modifier = Modifier,
    size: TextUnit = 42.sp,
    letterSpacing: TextUnit = 3.sp,
    italic: Boolean = false,
    glow: Boolean = false,
    subtitle: String? = null,
    subtitleSize: TextUnit = 13.sp,
    center: Boolean = false,
) {
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) {
            append("SE")
        }
        withStyle(SpanStyle(color = SignalCyan, fontWeight = FontWeight.Black)) {
            append("Ñ")
        }
        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) {
            append("AL")
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = annotated,
            style = TextStyle(
                fontSize = size,
                letterSpacing = letterSpacing,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                shadow = if (glow) {
                    Shadow(
                        color = SignalCyan.copy(alpha = 0.55f),
                        offset = Offset.Zero,
                        blurRadius = 34f
                    )
                } else null
            )
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle.uppercase(),
                color = SignalCyan,
                fontSize = subtitleSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
        }
    }
}
