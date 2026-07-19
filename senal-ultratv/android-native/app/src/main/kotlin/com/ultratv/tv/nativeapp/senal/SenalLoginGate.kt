package com.ultratv.tv.nativeapp.senal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SenalLoginViewModel @Inject constructor(
    private val auth: SenalAuthClient,
    private val providers: ProviderRepository,
    private val prefs: UserPreferencesStore,
) : ViewModel() {

    val needsLogin: StateFlow<Boolean> = providers.observeProviders()
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun login(username: String, password: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            _status.value = "Iniciando sesión…"
            val result = auth.login(username, password)
            result.fold(
                onSuccess = {
                    _status.value = "Conectando lista_importada.m3u…"
                    runCatching {
                        val id = providers.addM3u(SenalServerConfig.listaName, SenalServerConfig.listaUrl)
                        providers.setDefault(id)
                        prefs.markOnboardingSeen()
                        // Sync in background so Live TV can open as soon as rows land.
                        launch(Dispatchers.IO) {
                            runCatching {
                                providers.syncAll(id) { msg ->
                                    _status.value = msg
                                }
                            }
                        }
                    }.onFailure {
                        _error.value = it.message ?: "No se pudo cargar la lista"
                        _busy.value = false
                        return@fold
                    }
                    _busy.value = false
                    _status.value = null
                },
                onFailure = {
                    _error.value = it.message ?: "No se pudo iniciar sesión"
                    _busy.value = false
                    _status.value = null
                },
            )
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun SenalLoginGate(
    vm: SenalLoginViewModel = hiltViewModel(),
) {
    if (!SenalServerConfig.lockToVps) return
    val needsLogin by vm.needsLogin.collectAsState()
    if (!needsLogin) return

    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val status by vm.status.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07111B), Color(0xFF0A1A24), Color(0xFF07111B)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "SEÑAL",
                color = UltraTokens.Accent,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = com.ultratv.tv.nativeapp.ui.theme.UltraFonts.Serif,
            )
            Text(
                SenalServerConfig.baseUrl,
                color = UltraTokens.Fg3,
                fontSize = 14.sp,
            )
            Text(
                "Tras el login se carga solo downloads/lista_importada.m3u del VPS.",
                color = UltraTokens.Fg2,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = UltraTokens.Fg,
                unfocusedTextColor = UltraTokens.Fg,
                focusedBorderColor = UltraTokens.Accent,
                unfocusedBorderColor = UltraTokens.Line2,
                cursorColor = UltraTokens.Accent,
                focusedLabelColor = UltraTokens.Accent,
                unfocusedLabelColor = UltraTokens.Fg3,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                enabled = !busy,
                singleLine = true,
                label = { androidx.compose.material3.Text("Usuario") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                enabled = !busy,
                singleLine = true,
                label = { androidx.compose.material3.Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(12.dp),
            )
            error?.let {
                Text(it, color = UltraTokens.Live, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            status?.let {
                Text(it, color = UltraTokens.Fg2, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            if (busy) {
                CircularProgressIndicator(color = UltraTokens.Accent)
            }
            Button(
                onClick = { vm.login(username, password) },
                enabled = !busy && username.isNotBlank() && password.isNotBlank(),
            ) {
                Text("Entrar a SEÑAL", fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
