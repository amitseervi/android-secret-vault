package com.rignis.core.ui.viewmodels.settings

import androidx.lifecycle.viewModelScope
import com.rignis.core.base.BaseViewModel
import com.rignis.store.api.SettingsRepository
import com.rignis.store.api.UserThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SettingPageUiState {
    object Loading : SettingPageUiState
    data class Success(val theme: UserThemePreference) : SettingPageUiState
}

data class SettingPageState(
    val isLoading: Boolean = true, val theme: UserThemePreference = UserThemePreference.SYSTEM
)

sealed interface SettingPageEvent {
    data class OnThemeSelected(val theme: UserThemePreference) : SettingPageEvent
}

fun SettingPageState.toUiState(): SettingPageUiState {
    if (isLoading) {
        return SettingPageUiState.Loading
    }
    return SettingPageUiState.Success(theme)
}

class SettingsViewModel(private val settingsRepository: SettingsRepository) :
    BaseViewModel<SettingPageUiState, SettingPageEvent>() {
    private val _state = MutableStateFlow<SettingPageState>(SettingPageState())
    override val state: StateFlow<SettingPageUiState>
        get() = _state.map { it.toUiState() }.stateIn(
            viewModelScope, SharingStarted.Eagerly, SettingPageUiState.Loading
        )

    init {
        viewModelScope.launch {
            settingsRepository.userPreferredTheme.collect { theme ->
                _state.update {
                    it.copy(theme = theme, isLoading = false)
                }
            }
        }
    }

    override fun onAction(e: SettingPageEvent) {
        when (e) {
            is SettingPageEvent.OnThemeSelected -> {
                viewModelScope.launch(Dispatchers.IO) {
                    settingsRepository.updateTheme(e.theme)
                }
            }
        }
    }

}