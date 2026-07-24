package com.senal.tv.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senal.tv.R
import com.senal.tv.data.repository.AuthRepository
import com.senal.tv.ui.components.SenalBrandText
import com.senal.tv.ui.components.SenalClickable
import com.senal.tv.ui.theme.LiveGreen
import com.senal.tv.ui.theme.SignalCyan
import com.senal.tv.ui.theme.SignalCyanHot
import com.senal.tv.util.DeviceUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Login mockup SEÑAL:
 * fondo bokeh · wordmark top-left · reloj · panel cristal · campos · ENTRAR cyan.
 * En TV el panel es grande (10-ft UI); en móvil/tablet más compacto.
 */
@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoggedIn: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val userFocus = remember { FocusRequester() }
    var clock by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    val tv = !DeviceUi.isTouchBuild

    LaunchedEffect(Unit) {
        if (tv) {
            runCatching { userFocus.requestFocus() }
        }
        while (true) {
            clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(15_000)
        }
    }

    fun submit() {
        if (loading || success) return
        if (username.isBlank() || password.isBlank()) {
            error = "Introduce usuario y contraseña"
            return
        }
        scope.launch {
            loading = true
            error = null
            val result = authRepository.login(username, password)
            loading = false
            result.onSuccess {
                success = true
                delay(550)
                onLoggedIn()
            }.onFailure {
                val raw = it.message.orEmpty()
                error = when {
                    raw.contains("Unable to resolve", true) ||
                        raw.contains("Failed to connect", true) ||
                        raw.contains("timeout", true) ||
                        raw.contains("Connection", true) ||
                        raw.contains("UnknownHost", true) ->
                        "Sin conexión con el servidor"
                    else -> raw.ifBlank { "No se pudo iniciar sesión" }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        val panelMax = when {
            tv && wide -> 760.dp
            tv -> 640.dp
            else -> 520.dp
        }
        val panelFraction = when {
            tv && wide -> 0.52f
            tv -> 0.62f
            else -> 0.88f
        }
        val brandSize = if (tv) 48.sp else 34.sp
        val clockSize = if (tv) 32.sp else 22.sp
        val edgePad = if (tv) 56.dp else 40.dp
        val panelPadH = if (tv) 52.dp else 36.dp
        val panelPadV = if (tv) 48.dp else 34.dp
        val fieldGap = if (tv) 28.dp else 22.dp
        val hintSize = if (tv) 18.sp else 13.sp

        Image(
            painter = painterResource(R.mipmap.main_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.85f; scaleX = 1.08f; scaleY = 1.08f }
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(0.45f),
                        0.4f to Color.Black.copy(0.62f),
                        1f to Color.Black.copy(0.88f)
                    )
                )
        )

        SenalBrandText(
            size = brandSize,
            letterSpacing = if (tv) 4.sp else 3.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = edgePad, top = if (tv) 36.dp else 28.dp)
        )

        Text(
            text = clock,
            color = Color.White.copy(0.92f),
            fontSize = clockSize,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (tv) 40.dp else 30.dp, end = edgePad)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(min = if (tv) 520.dp else 280.dp, max = panelMax)
                .fillMaxWidth(panelFraction)
                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(if (tv) 22.dp else 18.dp))
                .padding(horizontal = panelPadH, vertical = panelPadV),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoginField(
                value = username,
                onValueChange = { username = it; error = null },
                label = "Usuario",
                modifier = Modifier.focusRequester(userFocus),
                imeAction = ImeAction.Next,
                large = tv
            )
            Spacer(Modifier.height(fieldGap))
            LoginField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "Contraseña",
                isPassword = true,
                showPassword = showPassword,
                onTogglePassword = { showPassword = !showPassword },
                imeAction = ImeAction.Done,
                onDone = { submit() },
                large = tv
            )

            AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                error?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFF8A80),
                        fontSize = if (tv) 18.sp else 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (tv) 18.dp else 14.dp)
                    )
                }
            }

            Spacer(Modifier.height(if (tv) 34.dp else 26.dp))
            EnterButton(
                label = when {
                    success -> "LISTO"
                    loading -> "CONECTANDO…"
                    else -> "ENTRAR"
                },
                enabled = !loading && !success,
                success = success,
                large = tv,
                onClick = { submit() }
            )

            Spacer(Modifier.height(if (tv) 22.dp else 18.dp))
            Text(
                text = "Inicia sesión para ver en vivo",
                color = Color.White.copy(0.72f),
                fontSize = hintSize,
                fontWeight = FontWeight.Normal
            )
        }

        if (success) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✓",
                    color = LiveGreen,
                    fontSize = if (tv) 72.sp else 52.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    large: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val labelSize = if (large) 20.sp else 14.sp
    val inputSize = if (large) 24.sp else 17.sp
    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = if (focused) SignalCyan else Color.White.copy(0.9f),
            fontSize = labelSize,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(if (large) 12.dp else 8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) SignalCyan else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(
                    horizontal = if (focused) 14.dp else 0.dp,
                    vertical = if (focused) (if (large) 12.dp else 8.dp) else 0.dp
                )
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = inputSize,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(SignalCyan),
                visualTransformation = when {
                    isPassword && !showPassword -> PasswordVisualTransformation()
                    else -> VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (isPassword) 44.dp else 0.dp)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (large) 10.dp else 6.dp)
                        ) {
                            if (value.isEmpty() && !focused) {
                                Text(
                                    text = " ",
                                    color = Color.Transparent,
                                    fontSize = inputSize
                                )
                            }
                            inner()
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(if (focused) 2.5.dp else 1.5.dp)
                                .background(
                                    if (focused) SignalCyan else Color.White.copy(0.55f)
                                )
                        )
                    }
                }
            )
            if (isPassword && onTogglePassword != null) {
                SenalClickable(
                    onClick = onTogglePassword,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(if (large) 48.dp else 40.dp),
                    shape = RoundedCornerShape(4.dp),
                    containerColor = Color.Transparent,
                    focusedContainerColor = SignalCyan.copy(0.2f),
                    pressedContainerColor = SignalCyan.copy(0.25f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (showPassword) "◉" else "◎",
                            color = Color.White.copy(0.75f),
                            fontSize = if (large) 18.sp else 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnterButton(
    label: String,
    enabled: Boolean,
    success: Boolean,
    onClick: () -> Unit,
    large: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        success -> LiveGreen
        focused -> SignalCyanHot
        else -> SignalCyan
    }
    SenalClickable(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(if (large) 14.dp else 12.dp),
        containerColor = fill,
        focusedContainerColor = SignalCyanHot,
        pressedContainerColor = SignalCyanHot,
        disabledContainerColor = SignalCyan.copy(alpha = 0.45f),
        onFocusedChange = { focused = it }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = if (large) 22.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = if (large) 22.sp else 16.sp,
                letterSpacing = if (large) 3.sp else 2.sp
            )
        }
    }
}
