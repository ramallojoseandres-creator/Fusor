package com.senal.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.senal.tv.util.DeviceUi

/**
 * Clickable that works on both TV (D-pad) and phone/tablet (touch).
 *
 * TV Material3 [Surface] often ignores finger taps — that felt like a frozen screen.
 * On touch builds we use a normal Compose [combinedClickable].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SenalClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    containerColor: Color = Color.Transparent,
    focusedContainerColor: Color = containerColor,
    pressedContainerColor: Color = focusedContainerColor,
    disabledContainerColor: Color = containerColor.copy(alpha = 0.45f),
    onFocusedChange: ((Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    if (DeviceUi.isTouchBuild) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val bg = when {
            !enabled -> disabledContainerColor
            pressed -> pressedContainerColor
            else -> containerColor
        }
        Box(
            modifier = modifier
                .clip(shape)
                .background(bg)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            content = content
        )
    } else {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            enabled = enabled,
            modifier = modifier.onFocusChanged { onFocusedChange?.invoke(it.isFocused) },
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = containerColor,
                focusedContainerColor = focusedContainerColor,
                pressedContainerColor = pressedContainerColor,
                disabledContainerColor = disabledContainerColor
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            content = { Box(content = content) }
        )
    }
}
