package com.rignis.core.ui.viewmodels.detail

import androidx.lifecycle.viewModelScope
import com.rignis.auth.domain.CipherManager
import com.rignis.core.base.BaseViewModel
import com.rignis.core.ui.routes.detail.ClipBoardHandler
import com.rignis.store.api.DataStore
import com.rignis.store.api.EncryptedDataEntry
import com.rignis.store.api.EncryptedDataItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DetailPageUiState {
    data class NewEntry(
        val title: String,
        val body: String,
        val secretVisible: Boolean,
        val submitEnabled: Boolean,
        val isSubmitSuccessful: Boolean
    ) : DetailPageUiState

    data class EditMode(
        val title: String,
        val body: String,
        val secretVisible: Boolean,
        val saveEnabled: Boolean = false,
        val locked: Boolean = true,
        val isSubmitSuccessful: Boolean
    ) : DetailPageUiState

    object Loading : DetailPageUiState
}

private data class DetailViewModelState(
    val title: String,
    val body: String,
    val secretVisible: Boolean,
    val isEditLocked: Boolean,
    val isInEditMode: Boolean,
    val isSubmissionSuccessful: Boolean,
    val submitButtonEnabled: Boolean,
    val isEditModeItemLoaded: Boolean,
)

sealed interface DetailPageAction {
    object ToggleVisibilityOfSecret : DetailPageAction
    data class CopySecret(val cipherManager: CipherManager) : DetailPageAction
    data class UpdateModifiedTitle(val value: String) : DetailPageAction
    data class UpdateModifiedBody(val value: String) : DetailPageAction
    class UnlockSecret(val cipherManager: CipherManager) : DetailPageAction
    class OnSubmitClick(val cipherManager: CipherManager) : DetailPageAction
    object OnDeleteClick : DetailPageAction
}

private fun DetailViewModelState.toUiState(): DetailPageUiState {
    return if (isInEditMode) {
        if (isEditModeItemLoaded) {
            DetailPageUiState.EditMode(
                title = title,
                body = body,
                secretVisible = secretVisible,
                saveEnabled = submitButtonEnabled,
                locked = isEditLocked,
                isSubmitSuccessful = isSubmissionSuccessful
            )
        } else {
            DetailPageUiState.Loading
        }
    } else {
        DetailPageUiState.NewEntry(
            title = title,
            body = body,
            secretVisible = secretVisible,
            submitEnabled = submitButtonEnabled,
            isSubmitSuccessful = isSubmissionSuccessful
        )
    }
}

class DetailViewModel(
    private val dataStore: DataStore, private val clipBoardHandler: ClipBoardHandler
) : BaseViewModel<DetailPageUiState, DetailPageAction>() {
    companion object {
        private val initialUiPageState = DetailPageUiState.Loading
    }

    private var _existingId: String? = null
    private var encryptedDataItem: EncryptedDataItem? = null

    private val _state: MutableStateFlow<DetailViewModelState> = MutableStateFlow(
        DetailViewModelState(
            title = "",
            body = "",
            secretVisible = false,
            isEditLocked = true,
            isInEditMode = false,
            isSubmissionSuccessful = false,
            submitButtonEnabled = false,
            isEditModeItemLoaded = false
        )
    )
    override val state: StateFlow<DetailPageUiState>
        get() = _state.map { it.toUiState() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialUiPageState)

    fun initializeWithEditMode(id: String) {
        if (!_existingId.isNullOrEmpty()) {
            return
        }
        _existingId = id
        viewModelScope.launch {
            val item = dataStore.getDataById(id)
            encryptedDataItem = item
            _state.update { current ->
                current.copy(title = item.title, isEditModeItemLoaded = true, isInEditMode = true)
            }
        }
    }

    fun initializeForNewEntry() {
        viewModelScope.launch {
            encryptedDataItem = null
            _state.update { current ->
                current.copy(isInEditMode = false)
            }
        }
    }

    override fun onAction(e: DetailPageAction) {
        when (e) {
            DetailPageAction.ToggleVisibilityOfSecret -> {
                onSecretVisibilityToggled()
            }

            is DetailPageAction.UpdateModifiedBody -> {
                updateBody(e.value)
            }

            is DetailPageAction.UpdateModifiedTitle -> {
                updateTitle(e.value)
            }

            is DetailPageAction.UnlockSecret -> {
                unlockSecret(e.cipherManager)
            }

            is DetailPageAction.OnSubmitClick -> {
                onSubmit(e.cipherManager)
            }

            is DetailPageAction.CopySecret -> {
                copySecret(e.cipherManager)
            }

            is DetailPageAction.OnDeleteClick -> {
                deleteSecret()
            }
        }
    }

    private fun deleteSecret() {
        viewModelScope.launch {
            encryptedDataItem?.let { data ->
                val s = _state.value
                if (!s.isInEditMode || s.isEditLocked) {
                    return@let
                }
                with(Dispatchers.IO) {
                    dataStore.deleteDataById(data.id)
                }
                _state.update { currentState ->
                    currentState.copy(isSubmissionSuccessful = true)
                }

            }
        }
    }

    private fun copySecret(cipherManager: CipherManager) {
        viewModelScope.launch {
            encryptedDataItem?.let { data ->
                val result = cipherManager.decryptData(
                    data.encryptedBody, data.initializationVector
                )
                if (result.isSuccess) {
                    clipBoardHandler.copyPassword(String(result.getOrThrow()))
                }
            }

        }
    }

    private fun unlockSecret(cipherManager: CipherManager) {
        viewModelScope.launch {
            _state.update { current ->
                if (current.isInEditMode) {
                    val data = encryptedDataItem!!
                    val cipherDecryptedResult = cipherManager.decryptData(
                        data.encryptedBody, data.initializationVector
                    )
                    if (cipherDecryptedResult.isSuccess) {
                        current.copy(
                            body = String(cipherDecryptedResult.getOrThrow()), isEditLocked = false
                        )
                    } else {
                        current
                    }
                } else {
                    current
                }
            }
        }
    }


    private fun updateBody(v: String) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    body = v,
                    submitButtonEnabled = v.isNotEmpty() && current.title.isNotEmpty() && (!current.isInEditMode || !current.isEditLocked)
                )
            }
        }
    }


    private fun updateTitle(v: String) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    title = v,
                    submitButtonEnabled = v.isNotEmpty() && current.body.isNotEmpty() && (!current.isInEditMode || !current.isEditLocked)
                )
            }
        }
    }

    private fun onSecretVisibilityToggled() {
        _state.update { current ->
            current.copy(secretVisible = !current.secretVisible)
        }
    }

    private fun onSubmit(cipherManager: CipherManager) {
        viewModelScope.launch {
            try {
                val currentState = _state.value
                val encryptedBody = cipherManager.encryptData(currentState.body.toByteArray())
                if (currentState.isInEditMode) {
                    val currentItem = encryptedDataItem!!
                    dataStore.updateExisting(
                        currentItem.id, EncryptedDataEntry(
                            currentState.title, encryptedBody.encryptedBody, encryptedBody.iv
                        )
                    )
                } else {
                    dataStore.insertItem(
                        EncryptedDataEntry(
                            currentState.title, encryptedBody.encryptedBody, encryptedBody.iv
                        )
                    )
                }
                _state.update { currentState ->
                    currentState.copy(isSubmissionSuccessful = true)
                }
            } catch (e: Exception) {

            }
        }
    }

}