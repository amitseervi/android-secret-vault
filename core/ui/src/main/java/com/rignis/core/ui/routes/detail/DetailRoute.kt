package com.rignis.core.ui.routes.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rignis.auth.domain.CipherManager
import com.rignis.auth.domain.EncryptedData
import com.rignis.core.ui.R
import com.rignis.core.ui.viewmodels.detail.DetailPageAction
import com.rignis.core.ui.viewmodels.detail.DetailPageState
import com.rignis.core.ui.viewmodels.detail.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailRoute(
    viewModel: DetailViewModel,
    cipherManager: CipherManager,
    onAction: (DetailPageAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    val state = viewModel.state.collectAsStateWithLifecycle()
    val exit = when (val v = state.value) {
        is DetailPageState.EditMode -> v.isSubmitSuccessful
        DetailPageState.Loading -> false
        is DetailPageState.NewEntry -> v.isSubmitSuccessful
    }
    if (exit) {
        onNavigateBack()
    }
    DetailScreen(state, cipherManager, onAction, onNavigateBack)

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    state: State<DetailPageState>,
    cipherManager: CipherManager,
    onAction: (DetailPageAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Text(stringResource(R.string.top_app_bar_title))
        }, navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back Navigation")
            }
        }, actions = {
            val s = state.value
            if (s is DetailPageState.Loading) {
                Row {

                }
            } else if (s is DetailPageState.EditMode && s.locked) {
                IconButton(onClick = {
                    onAction(DetailPageAction.UnlockSecret(cipherManager))
                }) {
                    Icon(Icons.Default.Lock, "Back Navigation")
                }
            } else {
                IconButton(onClick = {
                    onAction(DetailPageAction.OnSubmitClick(cipherManager))
                }) {
                    Icon(Icons.Default.Done, "Save Changes")
                }
            }

            if (s is DetailPageState.EditMode && !s.locked) {
                IconButton(onClick = {
                    onAction(DetailPageAction.OnDeleteClick(cipherManager))
                }) {
                    Icon(Icons.Default.Delete, "Delete")
                }
            }
        })
    }) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Title(state.value, onAction)
            Body(state.value, onAction)
        }
    }
}

@Composable
private fun Title(state: DetailPageState, onAction: (DetailPageAction) -> Unit) {
    when (state) {
        DetailPageState.Loading -> return
        is DetailPageState.EditMode -> {
            TextField(
                state.title,
                onValueChange = { s ->
                    onAction(DetailPageAction.UpdateModifiedTitle(s))
                },
                enabled = !state.locked, modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("title")
                },
            )
        }

        is DetailPageState.NewEntry -> {
            TextField(
                state.title,
                onValueChange = { s ->
                    onAction(DetailPageAction.UpdateModifiedTitle(s))
                },
                enabled = true, modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("title")
                },
            )
        }
    }
}

@Composable
private fun Body(
    state: DetailPageState, onAction: (DetailPageAction) -> Unit
) {
    when (state) {
        DetailPageState.Loading -> return
        is DetailPageState.EditMode -> {
            TextField(
                state.body.ifEmpty { if (state.locked) "*******" else "" },
                onValueChange = { s ->
                    onAction(DetailPageAction.UpdateModifiedBody(s))
                },
                label = {
                    Text("secret")
                },
                enabled = !state.locked,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (!state.secretVisible || state.locked) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = {
                    IconButton(onClick = {
                        onAction(DetailPageAction.ToggleVisibilityOfSecret)
                    }, enabled = !state.locked) {
                        if (state.secretVisible) {
                            Icon(Icons.Default.VisibilityOff, "Hide Secret")
                        } else {
                            Icon(Icons.Default.Visibility, "Show Secret")
                        }
                    }
                })
        }

        is DetailPageState.NewEntry -> {
            TextField(
                state.body,
                onValueChange = { s ->
                    onAction(DetailPageAction.UpdateModifiedBody(s))
                },
                label = {
                    Text("secret")
                },
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (!state.secretVisible) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = {
                    IconButton(onClick = {
                        onAction(DetailPageAction.ToggleVisibilityOfSecret)
                    }) {
                        if (state.secretVisible) {
                            Icon(Icons.Default.VisibilityOff, "Hide Secret")
                        } else {
                            Icon(Icons.Default.Visibility, "Show Secret")
                        }
                    }
                })
        }
    }
}


@Preview
@Composable
private fun DetailPagePreview() {
    val mockCipherManager = object : CipherManager {
        override suspend fun encryptData(data: ByteArray): EncryptedData {
            TODO("Not yet implemented")
        }

        override suspend fun decryptData(
            body: ByteArray, iv: ByteArray
        ): Result<ByteArray> {
            TODO("Not yet implemented")
        }
    }

    val state = remember {
        derivedStateOf {
            DetailPageState.NewEntry(
                "Hello world", "happy world", true, true, isSubmitSuccessful = false
            )
        }
    }
    DetailScreen(state, mockCipherManager, {}, {})

}
