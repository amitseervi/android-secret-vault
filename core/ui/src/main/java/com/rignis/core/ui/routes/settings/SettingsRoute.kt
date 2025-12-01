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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rignis.core.ui.viewmodels.settings.SettingPageEvent
import com.rignis.core.ui.viewmodels.settings.SettingPageUiState
import com.rignis.core.ui.viewmodels.settings.SettingsViewModel
import com.rignis.store.api.UserThemePreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel, onBack: () -> Unit = {}
) {
    val onThemeSelect: (UserThemePreference) -> Unit = {
        viewModel.onAction(SettingPageEvent.OnThemeSelected(it))
    }
    val state = viewModel.state.collectAsStateWithLifecycle()
    val stateValue = state.value
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                }
            })
        }) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {

            Text(
                text = "Choose App Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(24.dp))

            ThemeOption(
                theme = UserThemePreference.SYSTEM,
                selected = stateValue is SettingPageUiState.Success && stateValue.theme == UserThemePreference.SYSTEM,
                onSelect = onThemeSelect
            )

            ThemeOption(
                theme = UserThemePreference.LIGHT,
                selected = stateValue is SettingPageUiState.Success && stateValue.theme == UserThemePreference.LIGHT,
                onSelect = onThemeSelect
            )

            ThemeOption(
                theme = UserThemePreference.DARK,
                selected = stateValue is SettingPageUiState.Success && stateValue.theme == UserThemePreference.DARK,
                onSelect = onThemeSelect
            )
        }
    }
}

@Composable
fun ThemeOption(
    theme: UserThemePreference, selected: Boolean, onSelect: (UserThemePreference) -> Unit
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onSelect(theme) }
        .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected, onClick = { onSelect(theme) })
        Spacer(Modifier.width(12.dp))
        Text(theme.name, style = MaterialTheme.typography.bodyLarge)
    }
}

