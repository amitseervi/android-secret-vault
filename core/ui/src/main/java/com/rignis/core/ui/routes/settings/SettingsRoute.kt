package com.rignis.core.ui.routes.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rignis.core.ui.R
import com.rignis.core.ui.analytics.TrackScreen
import com.rignis.core.ui.viewmodels.settings.SettingPageEvent
import com.rignis.core.ui.viewmodels.settings.SettingPageUiState
import com.rignis.core.ui.viewmodels.settings.SettingsViewModel
import com.rignis.mysecret.analytics.api.Analytics
import com.rignis.store.api.UserThemePreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel, onBack: () -> Unit = {}, analytics: Analytics
) {
    TrackScreen(analytics, "Setting")
    val onThemeSelect: (UserThemePreference) -> Unit = {
        viewModel.onAction(SettingPageEvent.OnThemeSelected(it))
    }
    val state = viewModel.state.collectAsStateWithLifecycle()
    val stateValue = state.value
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ))
        }) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {

            Text(
                text = stringResource(R.string.choose_app_theme),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                val options = listOf(
                    UserThemePreference.SYSTEM to Icons.Outlined.BrightnessAuto,
                    UserThemePreference.LIGHT to Icons.Outlined.LightMode,
                    UserThemePreference.DARK to Icons.Outlined.DarkMode,
                )
                options.forEachIndexed { index, (theme, icon) ->
                    ThemeOption(
                        theme = theme,
                        icon = icon,
                        selected = stateValue is SettingPageUiState.Success && stateValue.theme == theme,
                        onSelect = onThemeSelect
                    )
                    if (index != options.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeOption(
    theme: UserThemePreference,
    icon: ImageVector,
    selected: Boolean,
    onSelect: (UserThemePreference) -> Unit
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onSelect(theme) }
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(theme.label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        RadioButton(
            selected = selected, onClick = { onSelect(theme) })
    }
}

