package com.streamvault.app.ui.screens.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.app.BuildConfig
import com.streamvault.app.R
import com.streamvault.app.senal.SenalAuthClient
import com.streamvault.app.senal.SenalServerConfig
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.design.SenalDisplayFamily
import com.streamvault.app.ui.interaction.TvButton
import com.streamvault.app.ui.theme.SurfaceHighlight
import com.streamvault.data.preferences.PreferencesRepository
import com.streamvault.data.sync.SyncProgressBus
import com.streamvault.domain.model.ActiveLiveSource
import com.streamvault.domain.repository.CombinedM3uRepository
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.sync.Section
import com.streamvault.domain.sync.SyncProgress
import com.streamvault.domain.usecase.M3uProviderSetupCommand
import com.streamvault.domain.usecase.ValidateAndAddProvider
import com.streamvault.domain.usecase.ValidateAndAddProviderResult
import com.streamvault.domain.usecase.XtreamProviderSetupCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val validateAndAddProvider: ValidateAndAddProvider,
    private val senalAuthClient: SenalAuthClient,
    private val preferencesRepository: PreferencesRepository,
    private val combinedM3uRepository: CombinedM3uRepository,
    syncProgressBus: SyncProgressBus,
) : ViewModel() {

    private val _hasProviders = MutableStateFlow<Boolean?>(null)
    val hasProviders: StateFlow<Boolean?> = _hasProviders.asStateFlow()

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val acceptingProgress = MutableStateFlow(true)

    val syncProgress: StateFlow<SyncProgress?> =
        combine(syncProgressBus.flow, acceptingProgress) { progress, accept ->
            if (accept) progress else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            maybeSeedXtreamOnly()
            providerRepository.getProviders()
                .map { it.isNotEmpty() }
                .collect { _hasProviders.value = it }
        }
        viewModelScope.launch {
            _hasProviders.filterNotNull().first()
            acceptingProgress.value = false
        }
    }

    private suspend fun maybeSeedXtreamOnly() {
        if (providerRepository.getProviders().first().isNotEmpty()) return
        val xtreamServer = BuildConfig.XTREAM_DEV_SERVER
        val xtreamUser = BuildConfig.XTREAM_DEV_USERNAME
        val xtreamPass = BuildConfig.XTREAM_DEV_PASSWORD
        if (xtreamServer.isBlank() || xtreamUser.isBlank() || xtreamPass.isBlank()) return
        validateAndAddProvider.loginXtream(
            XtreamProviderSetupCommand(
                serverUrl = xtreamServer,
                username = xtreamUser,
                password = xtreamPass,
                name = BuildConfig.XTREAM_DEV_NAME.ifBlank { "Dev (seeded)" },
                xtreamFastSyncEnabled = true,
            ),
        )
    }

    /**
     * Panel login = access control. Content always from VPS lista_importada.m3u.
     */
    fun loginWithSenal(username: String, password: String) {
        if (_loginBusy.value) return
        viewModelScope.launch {
            _loginBusy.value = true
            _loginError.value = null
            acceptingProgress.value = true
            val result = senalAuthClient.login(username, password)
            result.fold(
                onSuccess = { _ ->
                    val add = validateAndAddProvider.addM3u(
                        M3uProviderSetupCommand(
                            url = SenalServerConfig.listaUrl,
                            name = SenalServerConfig.listaName,
                        ),
                    )
                    when (add) {
                        is ValidateAndAddProviderResult.Success,
                        is ValidateAndAddProviderResult.SavedWithWarning -> {
                            if (!SenalServerConfig.allowClientPlaylistAdd) {
                                preferencesRepository.setShowLiveSourceSwitcher(false)
                                val providerId = when (add) {
                                    is ValidateAndAddProviderResult.Success -> add.provider.id
                                    is ValidateAndAddProviderResult.SavedWithWarning -> add.provider.id
                                    else -> null
                                }
                                if (providerId != null) {
                                    providerRepository.setActiveProvider(providerId)
                                    combinedM3uRepository.setActiveLiveSource(
                                        ActiveLiveSource.ProviderSource(providerId),
                                    )
                                }
                            }
                        }
                        is ValidateAndAddProviderResult.ValidationError -> {
                            _loginError.value = add.message
                            acceptingProgress.value = false
                        }
                        is ValidateAndAddProviderResult.Error -> {
                            _loginError.value = add.message
                            acceptingProgress.value = false
                        }
                    }
                },
                onFailure = {
                    _loginError.value = it.message ?: "No se pudo iniciar sesión"
                    acceptingProgress.value = false
                },
            )
            _loginBusy.value = false
        }
    }
}

@Composable
fun WelcomeScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSetup: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedSetup = onNavigateToSetup
    val hasProviders by viewModel.hasProviders.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val loginBusy by viewModel.loginBusy.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    LaunchedEffect(hasProviders) {
        when (hasProviders) {
            true -> onNavigateToHome()
            false -> Unit
            null -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WelcomeAtmosphere()

        when (hasProviders) {
            false -> WelcomeLoginHero(
                loginBusy = loginBusy,
                loginError = loginError,
                onLogin = viewModel::loginWithSenal,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp, vertical = 32.dp),
            )

            else -> WelcomeLoadingHero(
                syncProgress = syncProgress,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp, vertical = 32.dp),
            )
        }
    }
}

@Composable
private fun WelcomeAtmosphere() {
    val pulse = rememberInfiniteTransition(label = "senal_pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val drift by pulse.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF041018),
                        AppColors.Canvas,
                        Color(0xFF061520),
                        AppColors.HeroBottom,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (40 + drift).dp, y = (-80).dp)
                .size(420.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AppColors.Brand.copy(alpha = glow * 0.55f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-60 - drift).dp, y = 40.dp)
                .size(360.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A6B7A).copy(alpha = glow * 0.35f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun WelcomeLoginHero(
    loginBusy: Boolean,
    loginError: String?,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val brandAlpha = remember { Animatable(0f) }
    val brandScale = remember { Animatable(0.92f) }
    val formAlpha = remember { Animatable(0f) }
    val formOffset = remember { Animatable(28f) }

    LaunchedEffect(Unit) {
        brandAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        brandScale.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        formAlpha.animateTo(1f, tween(650, delayMillis = 180, easing = FastOutSlowInEasing))
        formOffset.animateTo(0f, tween(700, delayMillis = 180, easing = FastOutSlowInEasing))
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.TextPrimary,
        unfocusedTextColor = AppColors.TextPrimary,
        focusedBorderColor = AppColors.Brand,
        unfocusedBorderColor = SurfaceHighlight,
        focusedLabelColor = AppColors.Brand,
        unfocusedLabelColor = AppColors.TextTertiary,
        cursorColor = AppColors.Brand,
        focusedContainerColor = Color.Black.copy(alpha = 0.28f),
        unfocusedContainerColor = Color.Black.copy(alpha = 0.18f),
    )

    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(brandAlpha.value)
                .scale(brandScale.value),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = SenalDisplayFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 64.sp,
                    letterSpacing = (-1.2).sp,
                ),
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(3.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                AppColors.Brand,
                                Color.Transparent,
                            ),
                        ),
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextTertiary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(formAlpha.value)
                .offset(y = formOffset.value.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loginBusy,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                label = { androidx.compose.material3.Text(stringResource(R.string.welcome_username_label)) },
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loginBusy,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                label = { androidx.compose.material3.Text(stringResource(R.string.welcome_password_label)) },
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )
            loginError?.let {
                Text(
                    text = it,
                    color = AppColors.Live,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            if (loginBusy) {
                CircularProgressIndicator(
                    color = AppColors.Brand,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                )
            }
            TvButton(
                onClick = { onLogin(username, password) },
                enabled = !loginBusy && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.welcome_enter_app))
            }
        }
    }
}

@Composable
private fun WelcomeLoadingHero(
    syncProgress: SyncProgress?,
    modifier: Modifier = Modifier,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .alpha(appear.value),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = SenalDisplayFamily,
                fontSize = 48.sp,
            ),
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.welcome_loading_title),
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val subtitle = when {
            syncProgress != null && syncProgress.currentLabel.isNotBlank() -> syncProgress.currentLabel
            syncProgress != null -> stringResource(sectionLabelRes(syncProgress.section))
            else -> stringResource(R.string.welcome_loading_subtitle)
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(22.dp))
        if (syncProgress != null && syncProgress.total > 0) {
            LinearProgressIndicator(
                progress = { syncProgress.current.toFloat() / syncProgress.total.toFloat() },
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(4.dp),
                color = AppColors.Brand,
                trackColor = AppColors.BrandMuted,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.sync_items_indexed_format,
                    syncProgress.itemsIndexed,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.TextTertiary,
            )
        } else {
            CircularProgressIndicator(
                color = AppColors.Brand,
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
            )
        }
    }
}

private fun sectionLabelRes(section: Section): Int = when (section) {
    Section.LIVE -> R.string.sync_section_live
    Section.VOD -> R.string.sync_section_vod
    Section.SERIES -> R.string.sync_section_series
}
