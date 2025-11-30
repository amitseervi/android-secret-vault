package com.rignis.core.ui.viewmodels.home

import androidx.lifecycle.viewModelScope
import com.rignis.core.base.BaseViewModel
import com.rignis.store.api.DataStore
import com.rignis.store.api.EncryptedDataRef
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomePageState {
    val isLoading: Boolean

    data class Empty(override val isLoading: Boolean) : HomePageState
    data class Loaded(override val isLoading: Boolean, val data: ImmutableList<EncryptedDataRef>) :
        HomePageState
}

private data class HomePageStateInternal(
    val data: ImmutableList<EncryptedDataRef>, val isLoading: Boolean
)

sealed interface HomePageEvent {
    data object OnBiometricScanClicked : HomePageEvent
}

class HomeViewModel(private val store: DataStore) : BaseViewModel<HomePageState, HomePageEvent>() {

    private val _state: MutableStateFlow<HomePageStateInternal> =
        MutableStateFlow(HomePageStateInternal(persistentListOf<EncryptedDataRef>(), true))


    override val state: StateFlow<HomePageState>
        get() = _state.map { it.internalStateToUiState() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomePageState.Empty(true))

    companion object {
        private fun HomePageStateInternal.internalStateToUiState(): HomePageState {
            if (data.isEmpty()) {
                return HomePageState.Empty(isLoading)
            }
            return HomePageState.Loaded(isLoading, data)
        }
    }

    init {
        viewModelScope.launch {
            store.getAllData().collect { newData ->
                _state.update { current ->
                    current.copy(data = newData.toImmutableList(), isLoading = false)
                }
            }
        }
    }

    override fun onAction(e: HomePageEvent) {
        when (e) {
            HomePageEvent.OnBiometricScanClicked -> {

            }
        }
    }
}