package com.senal.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.senal.tv.AppContainer
import com.senal.tv.BuildConfig
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.SectionHeader
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onLogout: () -> Unit
) {
    val settings by container.settingsStore.settings.collectAsState(
        initial = com.senal.tv.data.local.AppSettings()
    )
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader("AJUSTES", "Preferencias locales · sin guardar credenciales Xtream")
        Text("Aspecto: ${settings.preferredAspect}", style = LocalSenalTypography.current.body)
        Text("Velocidad: ${settings.playbackSpeed}x", style = LocalSenalTypography.current.body)
        Text("API: ${BuildConfig.API_BASE_URL}", style = LocalSenalTypography.current.caption, color = TextMuted)
        Text(
            "Seguridad: solo JWT, favoritos, historial y preferencias",
            style = LocalSenalTypography.current.caption,
            color = Teal
        )
        Spacer(Modifier.height(8.dp))
        FocusableButton(
            label = "Aspecto: adecuar",
            onClick = { scope.launch { container.settingsStore.setAspect("fit") } },
            primary = false
        )
        FocusableButton(
            label = "Aspecto: rellenar",
            onClick = { scope.launch { container.settingsStore.setAspect("zoom") } },
            primary = false
        )
        FocusableButton(
            label = "Velocidad 1.0x",
            onClick = { scope.launch { container.settingsStore.setSpeed(1f) } },
            primary = false
        )
        FocusableButton(
            label = "Velocidad 1.25x",
            onClick = { scope.launch { container.settingsStore.setSpeed(1.25f) } },
            primary = false
        )
        Spacer(Modifier.height(12.dp))
        FocusableButton(
            label = "Cerrar sesión",
            onClick = {
                scope.launch {
                    container.authRepository.logout()
                    container.catalogRepository.clearMemory()
                    onLogout()
                }
            }
        )
    }
}
