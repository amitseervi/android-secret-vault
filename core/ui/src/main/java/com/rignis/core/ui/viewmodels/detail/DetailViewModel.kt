package com.rignis.core.ui.viewmodels.detail

import androidx.lifecycle.viewModelScope
import com.rignis.auth.domain.CipherManager
import com.rignis.core.base.BaseViewModel
import com.rignis.core.ui.routes.detail.ClipBoardHandler
import com.rignis.store.api.DataStore
import com.rignis.store.api.EncryptedDataEntry
import com.rignis.store.api.EncryptedDataItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DetailPageState {
    data class NewEntry(
        val title: String, val body: String, val secretVisible: Boolean, val submitEnabled: Boolean
    ) : DetailPageState

    data class EditMode(
        val title: String,
        val body: String,
        val secretVisible: Boolean,
        val saveEnabled: Boolean = false,
        val locked: Boolean = true
    ) : DetailPageState

    object Loading : DetailPageState
}

/**
 * PageState --> 1. Only View 2. Add New 3. Modify Title 4. Modify secret
 */

sealed interface DetailPageAction {
    object ToggleVisibilityOfSecret : DetailPageAction
    data class CopySecret(val cipherManager: CipherManager) : DetailPageAction

    data class UpdateModifiedTitle(val value: String) : DetailPageAction

    data class UpdateModifiedBody(val value: String) : DetailPageAction

    class UnlockSecret(val cipherManager: CipherManager) : DetailPageAction
    class OnSubmitClick(val cipherManager: CipherManager) : DetailPageAction
}

class DetailViewModel(
    private val dataStore: DataStore, private val clipBoardHandler: ClipBoardHandler
) : BaseViewModel<DetailPageState, DetailPageAction>() {
    companion object {
        private val initialPageState = DetailPageState.Loading
    }

    private var _existingId: String? = null
    private var encryptedDataItem: EncryptedDataItem? = null

    private val _state: MutableStateFlow<DetailPageState> = MutableStateFlow(initialPageState)
    override val state: StateFlow<DetailPageState>
        get() = _state.stateIn(viewModelScope, SharingStarted.Eagerly, initialPageState)

    fun initializeWithEditMode(id: String) {
        if (!_existingId.isNullOrEmpty()) {
            return
        }
        _existingId = id
        viewModelScope.launch {
            val item = dataStore.getDataById(id)
            encryptedDataItem = item
            _state.update { current ->
                DetailPageState.EditMode(
                    title = item.title, body = "", secretVisible = false, saveEnabled = false
                )
            }
        }
    }

    fun initializeForNewEntry() {
        viewModelScope.launch {
            encryptedDataItem = null
            _state.update { current ->
                DetailPageState.NewEntry(
                    title = "", body = "", secretVisible = false, submitEnabled = false
                )
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
                when (current) {
                    is DetailPageState.EditMode -> {
                        val data = encryptedDataItem!!
                        val cipherDecryptedResult = cipherManager.decryptData(
                            data.encryptedBody, data.initializationVector
                        )
                        if (cipherDecryptedResult.isSuccess) {
                            current.copy(
                                body = String(cipherDecryptedResult.getOrThrow()), locked = false
                            )
                        } else {
                            current
                        }
                    }

                    else -> current
                }
            }
        }
    }


    private fun updateBody(v: String) {
        viewModelScope.launch {
            _state.update { current ->
                when (current) {
                    is DetailPageState.EditMode -> current.copy(
                        body = v,
                        saveEnabled = v.isNotEmpty() && current.title.isNotEmpty() && !current.locked
                    )

                    DetailPageState.Loading -> current
                    is DetailPageState.NewEntry -> current.copy(
                        body = v, submitEnabled = v.isNotEmpty() && current.title.isNotEmpty()
                    )
                }
            }
        }
    }


    private fun updateTitle(v: String) {
        viewModelScope.launch {
            _state.update { current ->
                when (current) {
                    is DetailPageState.EditMode -> current.copy(
                        title = v,
                        saveEnabled = v.isNotEmpty() && current.body.isNotEmpty() && !current.locked
                    )

                    DetailPageState.Loading -> current
                    is DetailPageState.NewEntry -> current.copy(
                        title = v, submitEnabled = v.isNotEmpty() && current.body.isNotEmpty()
                    )
                }
            }
        }
    }

    private fun onSecretVisibilityToggled() {
        _state.update { current ->
            when (current) {
                is DetailPageState.EditMode -> {
                    current.copy(secretVisible = !current.secretVisible)
                }

                is DetailPageState.NewEntry -> {
                    current.copy(secretVisible = !current.secretVisible)
                }

                else -> {
                    current
                }
            }
        }
    }

    private fun onSubmit(cipherManager: CipherManager) {
        viewModelScope.launch {
            when (val currentState = _state.value) {
                is DetailPageState.EditMode -> {
                    val currentItem = encryptedDataItem!!
                    val encryptedBody = cipherManager.encryptData(currentState.body.toByteArray())
                    dataStore.updateExisting(
                        currentItem.id, EncryptedDataEntry(
                            currentState.title, encryptedBody.encryptedBody, encryptedBody.iv
                        )
                    )
                }

                is DetailPageState.NewEntry -> {
                    val encryptedBody = cipherManager.encryptData(currentState.body.toByteArray())
                    dataStore.insertItem(
                        EncryptedDataEntry(
                            currentState.title, encryptedBody.encryptedBody, encryptedBody.iv
                        )
                    )
                }
                DetailPageState.Loading -> Unit
            }
        }
    }

}