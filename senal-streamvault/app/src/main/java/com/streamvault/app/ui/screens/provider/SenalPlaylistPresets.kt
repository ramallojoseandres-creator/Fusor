package com.streamvault.app.ui.screens.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.app.senal.SenalServerConfig
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.app.ui.theme.SurfaceHighlight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SenalPlaylistPresets(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onApply: (name: String, url: String) -> Unit,
) {
    var showAccountFields by rememberSaveable { mutableStateOf(false) }
    var accountError by rememberSaveable { mutableStateOf<String?>(null) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.TextPrimary,
        unfocusedTextColor = AppColors.TextPrimary,
        focusedBorderColor = AppColors.Brand,
        unfocusedBorderColor = SurfaceHighlight,
        focusedLabelColor = AppColors.Brand,
        unfocusedLabelColor = AppColors.TextTertiary,
        cursorColor = AppColors.Brand,
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Listas SEÑAL",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.BrandStrong,
        )
        Text(
            text = "Servidor ${SenalServerConfig.baseUrl}",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextTertiary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SenalServerConfig.presets().forEach { preset ->
                TvClickableSurface(
                    onClick = {
                        when {
                            preset.needsCredentials -> {
                                showAccountFields = true
                                accountError = null
                            }
                            !preset.url.isNullOrBlank() -> {
                                showAccountFields = false
                                onApply(preset.title, preset.url)
                            }
                            else -> {
                                showAccountFields = false
                                onApply(preset.title, "")
                            }
                        }
                    },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = AppColors.TextPrimary,
                        )
                        Text(
                            text = preset.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                        )
                    }
                }
            }
        }

        if (showAccountFields) {
            Text(
                text = "Usuario y clave del panel SEÑAL",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { androidx.compose.material3.Text("Usuario SEÑAL") },
                singleLine = true,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { androidx.compose.material3.Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )
            TvClickableSurface(
                onClick = {
                    try {
                        val url = SenalServerConfig.accountPlaylistUrl(username, password)
                        accountError = null
                        onApply("SEÑAL ($username)", url)
                    } catch (e: Exception) {
                        accountError = e.message ?: "Completa usuario y contraseña"
                    }
                },
            ) {
                Text(
                    text = "Usar cuenta → get.php",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.Brand,
                )
            }
            accountError?.let {
                Text(text = it, color = AppColors.Live, style = MaterialTheme.typography.bodySmall)
            }
        }

        androidx.compose.material3.HorizontalDivider(color = SurfaceHighlight.copy(alpha = 0.6f))
    }
}
