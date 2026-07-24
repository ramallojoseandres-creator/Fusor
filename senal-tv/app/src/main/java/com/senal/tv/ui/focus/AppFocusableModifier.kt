package com.senal.tv.ui.focus

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Turquesa de foco TV — brillo en D-pad (#00E5FF). */
val FocusTurquoise = Color(0xFF00E5FF)
val FocusTurquoiseSoft = Color(0x6600E5FF)

/**
 * [senalFocusable] — modificador canónico de foco SEÑAL (graphicsLayer).
 *
 * - Escala suave 1.0 → [scaleFocused] (1.06 por defecto)
 * - Borde turquesa + halo
 * - Compatible con [FocusRequester] para zapeo rápido
 */
@Composable
fun Modifier.senalFocusable(
    focused: Boolean,
    scaleFocused: Float = 1.06f,
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 2.5.dp,
    glowPadFraction: Float = 0.02f,
    animationMs: Int = 160,
    drawGlow: Boolean = true,
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleFocused else 1f,
        animationSpec = tween(durationMillis = animationMs, easing = FastOutSlowInEasing),
        label = "senalFocusScale",
    )
    val glow by animateFloatAsState(
        targetValue = if (focused && drawGlow) 1f else 0f,
        animationSpec = tween(durationMillis = animationMs, easing = FastOutSlowInEasing),
        label = "senalFocusGlow",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = if (focused) 10f else 0f
            clip = false
        }
        .drawBehind {
            if (glow <= 0.01f) return@drawBehind
            val pad = size.minDimension * glowPadFraction
            val radius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
            val glowSize = Size(size.width + pad * 2f, size.height + pad * 2f)
            val origin = Offset(-pad, -pad)
            drawRoundRect(
                color = FocusTurquoiseSoft.copy(alpha = 0.35f * glow),
                topLeft = origin,
                size = glowSize,
                cornerRadius = radius,
            )
            drawRoundRect(
                color = FocusTurquoise.copy(alpha = 0.18f * glow),
                topLeft = Offset(-pad * 1.6f, -pad * 1.6f),
                size = Size(size.width + pad * 3.2f, size.height + pad * 3.2f),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = FocusTurquoise.copy(alpha = 0.95f * glow),
                topLeft = origin,
                size = glowSize,
                cornerRadius = radius,
                style = Stroke(width = borderWidth.toPx()),
            )
        }
}

/** Alias legacy — preferir [senalFocusable]. */
@Composable
fun Modifier.appFocusableModifier(
    focused: Boolean,
    scaleFocused: Float = 1.06f,
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 2.5.dp,
    glowPadFraction: Float = 0.02f,
    animationMs: Int = 160,
): Modifier = senalFocusable(
    focused = focused,
    scaleFocused = scaleFocused,
    cornerRadius = cornerRadius,
    borderWidth = borderWidth,
    glowPadFraction = glowPadFraction,
    animationMs = animationMs,
)

/**
 * Variante stateful: registra foco + aplica [senalFocusable].
 * Devuelve el [FocusRequester] para anclar el D-pad.
 */
@Composable
fun rememberSenalFocusState(
    soft: Boolean = false,
    cornerRadius: Dp = 12.dp,
): Pair<FocusRequester, Modifier> {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val mod = Modifier
        .focusRequester(requester)
        .focusProperties { canFocus = true }
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .senalFocusable(
            focused = focused,
            scaleFocused = if (soft) 1.03f else 1.06f,
            cornerRadius = cornerRadius,
        )
        .padding(1.dp)
    return requester to mod
}

/** Alias legacy. */
@Composable
fun rememberAppFocusState(soft: Boolean = false): Pair<FocusRequester, Modifier> =
    rememberSenalFocusState(soft = soft)
