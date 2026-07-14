package com.senal.tv.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.senal.tv.data.repository.AuthRepository
import com.senal.tv.ui.components.BrandMark
import com.senal.tv.ui.components.ErrorMessage
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.LoadingPulse
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.ui.theme.Violet
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoggedIn: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val userFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        userFocus.requestFocus()
    }

    fun submit() {
        if (loading) return
        if (username.isBlank() || password.isBlank()) {
            error = "Introduce usuario y contraseña"
            return
        }
        scope.launch {
            loading = true
            error = null
            val result = authRepository.login(username, password)
            loading = false
            result.onSuccess { onLoggedIn() }
                .onFailure { error = it.message ?: "No se pudo iniciar sesión" }
        }
    }

    SenalBackground {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .background(GraphiteCard.copy(alpha = 0.92f), RoundedCornerShape(28.dp))
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandMark()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Acceso exclusivo para Smart TV",
                    style = LocalSenalTypography.current.subtitle
                )
                Spacer(Modifier.height(28.dp))

                TvTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    label = "Usuario",
                    modifier = Modifier.focusRequester(userFocus),
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(14.dp))
                TvTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "Contraseña",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onDone = { submit() }
                )
                Spacer(Modifier.height(18.dp))

                AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                    error?.let {
                        Column {
                            ErrorMessage(it)
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }

                AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingPulse("Iniciando sesión…")
                        Spacer(Modifier.height(18.dp))
                    }
                }

                FocusableButton(
                    label = if (loading) "Conectando…" else "Entrar",
                    onClick = { submit() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = LocalSenalTypography.current.caption, color = BrandOrange)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalSenalTypography.current.body.copy(color = TextPrimary),
            cursorBrush = SolidColor(Teal),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorField, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(text = label, color = TextMuted, style = LocalSenalTypography.current.body)
                    }
                    inner()
                }
            }
        )
    }
}

private val ColorField = androidx.compose.ui.graphics.Color(0xFF101018)
