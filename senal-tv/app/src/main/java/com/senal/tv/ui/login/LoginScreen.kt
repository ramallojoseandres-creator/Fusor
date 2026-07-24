package com.senal.tv.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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

    LaunchedEffect(Unit) {
        if (!DeviceUi.isTouchBuild) {
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

    Box(Modifier.fillMaxSize()) {
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
            size = 34.sp,
            letterSpacing = 3.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 40.dp, top = 28.dp)
        )

        Text(
            text = clock,
            color = Color.White.copy(0.92f),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 30.dp, end = 40.dp)
        )

        // Panel cristal central
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = if (DeviceUi.isTouchBuild) 520.dp else 460.dp)
                .fillMaxWidth(if (DeviceUi.isTouchBuild) 0.88f else 0.4f)
                .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(18.dp))
                .padding(horizontal = 36.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoginField(
                value = username,
                onValueChange = { username = it; error = null },
                label = "Usuario",
                modifier = Modifier.focusRequester(userFocus),
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(22.dp))
            LoginField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "Contraseña",
                isPassword = true,
                showPassword = showPassword,
                onTogglePassword = { showPassword = !showPassword },
                imeAction = ImeAction.Done,
                onDone = { submit() }
            )

            AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                error?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            EnterButton(
                label = when {
                    success -> "LISTO"
                    loading -> "CONECTANDO…"
                    else -> "ENTRAR"
                },
                enabled = !loading && !success,
                success = success,
                onClick = { submit() }
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Inicia sesión para ver en vivo",
                color = Color.White.copy(0.72f),
                fontSize = 13.sp,
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
                Text("✓", color = LiveGreen, fontSize = 52.sp, fontWeight = FontWeight.Bold)
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
    onDone: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = if (focused) SignalCyan else Color.White.copy(0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .border(
                    width = if (focused) 1.5.dp else 0.dp,
                    color = if (focused) SignalCyan else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = if (focused) 12.dp else 0.dp, vertical = if (focused) 8.dp else 0.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 17.sp,
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
                    .padding(end = if (isPassword) 36.dp else 0.dp)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Column {
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            if (value.isEmpty() && !focused) {
                                Text(
                                    text = " ",
                                    color = Color.Transparent,
                                    fontSize = 17.sp
                                )
                            }
                            inner()
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(if (focused) 2.dp else 1.dp)
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
                        .size(40.dp),
                    shape = RoundedCornerShape(4.dp),
                    containerColor = Color.Transparent,
                    focusedContainerColor = SignalCyan.copy(0.2f),
                    pressedContainerColor = SignalCyan.copy(0.25f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (showPassword) "◉" else "◎",
                            color = Color.White.copy(0.75f),
                            fontSize = 14.sp
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
    onClick: () -> Unit
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
        shape = RoundedCornerShape(12.dp),
        containerColor = fill,
        focusedContainerColor = SignalCyanHot,
        pressedContainerColor = SignalCyanHot,
        disabledContainerColor = SignalCyan.copy(alpha = 0.45f),
        onFocusedChange = { focused = it }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                letterSpacing = 2.sp
            )
        }
    }
}
