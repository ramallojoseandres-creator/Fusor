package com.senal.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.senal.tv.R
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.focus.FocusTurquoise
import com.senal.tv.ui.focus.FocusTurquoiseSoft
import com.senal.tv.ui.focus.senalFocusable
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.BrandOrangeHot
import com.senal.tv.ui.theme.FocusWhite
import com.senal.tv.ui.theme.Graphite
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.util.formatDurationMinutes
import com.senal.tv.util.formatRating

/** Focus scale — Ultra Wow TV (1.06) + soft variant. */
const val FOCUS_SCALE = 1.06f
const val FOCUS_SCALE_SOFT = 1.03f

@Composable
fun rememberFlujoFocusModifier(focused: Boolean, big: Boolean = true): Modifier {
    return Modifier.senalFocusable(
        focused = focused,
        scaleFocused = if (big) FOCUS_SCALE else FOCUS_SCALE_SOFT,
        cornerRadius = 10.dp,
    )
}

@Composable
fun SenalBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.mipmap.main_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xCC060A14),
                            Color(0xE0060A14),
                            Color(0xF2060A14)
                        )
                    )
                )
        )
        // Subtle grid atmosphere (ATV v2)
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val step = 48.dp.toPx()
            val line = Color(0x14FFFFFF)
            var x = 0f
            while (x < size.width) {
                drawLine(line, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(line, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                y += step
            }
        }
        content()
    }
}

@Composable
fun BrandMark(compact: Boolean = false) {
    Text(
        text = "SEÑAL",
        style = if (compact) {
            LocalSenalTypography.current.title
        } else {
            LocalSenalTypography.current.brand
        },
        color = BrandOrange
    )
}

@Composable
fun FocusableButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val radius = if (compact) 8.dp else 10.dp
    val hPad = if (compact) 12.dp else 16.dp
    val vPad = if (compact) 10.dp else 12.dp
    SenalClickable(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .senalFocusable(focused = focused, scaleFocused = FOCUS_SCALE_SOFT, cornerRadius = radius)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(radius),
        containerColor = if (primary) BrandOrange else GraphiteCard,
        focusedContainerColor = if (primary) BrandOrangeHot else Color(0xFF1A2740),
        pressedContainerColor = BrandOrange,
        onFocusedChange = { focused = it }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = hPad, vertical = vPad),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = LocalSenalTypography.current.button,
                color = if (primary) Color.Black else TextPrimary
            )
        }
    }
}

/**
 * FLUJO-style horizontal category column (FocusColumView).
 * Uses real tile art + 1.13 spring zoom.
 */
@Composable
fun FocusColumnTile(
    label: String,
    normalRes: Int,
    focusedRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    SenalClickable(
        onClick = onClick,
        modifier = modifier
            .senalFocusable(focused = focused, scaleFocused = FOCUS_SCALE, cornerRadius = 12.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        containerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        onFocusedChange = { focused = it }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = if (focused) focusedRes else normalRes,
                contentDescription = label,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = label,
                style = LocalSenalTypography.current.button,
                color = FocusWhite,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun ColorTile(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp
) {
    var focused by remember { mutableStateOf(false) }
    SenalClickable(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .senalFocusable(focused = focused, scaleFocused = FOCUS_SCALE, cornerRadius = 10.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        containerColor = color,
        focusedContainerColor = color,
        onFocusedChange = { focused = it }
    ) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = label, style = LocalSenalTypography.current.button, color = FocusWhite)
        }
    }
}

@Composable
fun LoadingPulse(label: String = "Cargando…") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(color = BrandOrange, trackColor = BrandOrange.copy(alpha = 0.2f))
        Text(text = label, style = LocalSenalTypography.current.subtitle, color = TextMuted)
    }
}

@Composable
fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33FF6B7A))
            .border(1.dp, Color(0x66FF6B7A), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(text = message, color = Color(0xFFFFC9CF), style = LocalSenalTypography.current.body)
    }
}

@Composable
fun PosterCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showMeta: Boolean = true,
    onLongClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    SenalClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(170.dp)
            .senalFocusable(focused = focused, scaleFocused = FOCUS_SCALE, cornerRadius = 16.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(16.dp),
        containerColor = GraphiteCard,
        focusedContainerColor = GraphiteCard,
        onFocusedChange = { focused = it }
    ) {
        Column {
            AsyncImage(
                model = item.resolvePoster(),
                contentDescription = item.resolveTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color(0xFF22222C))
            )
            if (showMeta) {
                Text(
                    text = item.resolveTitle(),
                    style = LocalSenalTypography.current.body,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)
                )
                Text(
                    text = listOfNotNull(
                        item.year?.toString(),
                        formatDurationMinutes(item.resolveDurationMinutes()),
                        formatRating(item.rating)
                    ).joinToString(" · "),
                    style = LocalSenalTypography.current.caption,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                        top = 4.dp
                    )
                )
            }
        }
    }
}

@Composable
fun ChannelCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    SenalClickable(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .senalFocusable(focused = focused, scaleFocused = FOCUS_SCALE_SOFT, cornerRadius = 10.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        containerColor = if (focused) FocusWhite else GraphiteCard,
        focusedContainerColor = FocusWhite,
        onFocusedChange = { focused = it }
    ) {
        val titleColor = if (focused) Color.Black else TextPrimary
        val muted = if (focused) Color.Black.copy(alpha = 0.65f) else TextMuted
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (item.resolveNumber() ?: "·").toString(),
                style = LocalSenalTypography.current.caption,
                color = if (focused) BrandOrange else Teal,
                modifier = Modifier.width(42.dp)
            )
            AsyncImage(
                model = item.resolveLogo(),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101016)),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.resolveTitle(),
                    style = LocalSenalTypography.current.body,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.resolveNow().ifBlank { "EPG no disponible" },
                    style = LocalSenalTypography.current.caption,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier
                    .size(8.dp)
                    .background(Color(0xFF22C55E), RoundedCornerShape(50))
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(text = title, style = LocalSenalTypography.current.title, color = TextPrimary)
        if (!subtitle.isNullOrBlank()) {
            Text(text = subtitle, style = LocalSenalTypography.current.subtitle, color = TextMuted)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = LocalSenalTypography.current.subtitle, color = TextMuted)
    }
}

@Composable
fun PulseBorder(active: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pulse = remember { Animatable(0.4f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            pulse.animateTo(1f, tween(700))
            pulse.animateTo(0.4f, tween(700))
        }
    }
    Box(
        modifier = Modifier.border(
            width = 2.dp,
            color = BrandOrange.copy(alpha = if (active) pulse.value else 0f),
            shape = RoundedCornerShape(14.dp)
        )
    ) { content() }
}
