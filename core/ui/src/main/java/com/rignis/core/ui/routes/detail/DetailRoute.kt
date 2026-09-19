package com.rignis.core.ui.routes.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rignis.auth.domain.CanAuthenticate
import com.rignis.auth.domain.CipherManager
import com.rignis.auth.domain.EncryptedData
import com.rignis.core.ui.R
import com.rignis.core.ui.analytics.TrackScreen
import com.rignis.core.ui.viewmodels.detail.DetailPageAction
import com.rignis.core.ui.viewmodels.detail.DetailPageUiState
import com.rignis.core.ui.viewmodels.detail.DetailViewModel
import com.rignis.mysecret.analytics.api.Analytics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailRoute(
    viewModel: DetailViewModel,
    cipherManager: CipherManager,
    onAction: (DetailPageAction) -> Unit,
    onNavigateBack: () -> Unit,
    analytics: Analytics
) {
    TrackScreen(analytics, "Detail")
    val state = viewModel.state.collectAsStateWithLifecycle()
    val exit = when (val v = state.value) {
        is DetailPageUiState.EditMode -> v.isSubmitSuccessful
        DetailPageUiState.Loading -> false
        is DetailPageUiState.NewEntry -> v.isSubmitSuccessful
    }
    if (exit) {
        onNavigateBack()
    }
    DetailScreen(state, cipherManager, onAction, onNavigateBack)

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    state: State<DetailPageUiState>,
    cipherManager: CipherManager,
    onAction: (DetailPageAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    val s = state.value
    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Text(
                when (s) {
                    DetailPageUiState.Loading -> stringResource(R.string.top_app_bar_title)
                    is DetailPageUiState.NewEntry -> stringResource(R.string.new_secret)
                    is DetailPageUiState.EditMode -> if (s.locked) {
                        stringResource(R.string.view_secret)
                    } else {
                        stringResource(R.string.edit_secret)
                    }
                }
            )
        }, navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back Navigation")
            }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ), actions = {
            when {
                s is DetailPageUiState.NewEntry -> {
                    IconButton(
                        onClick = { onAction(DetailPageAction.OnSubmitClick(cipherManager)) },
                        enabled = s.submitEnabled
                    ) {
                        Icon(Icons.Default.Done, stringResource(R.string.save))
                    }
                }

                s is DetailPageUiState.EditMode && !s.locked -> {
                    IconButton(
                        onClick = { onAction(DetailPageAction.OnSubmitClick(cipherManager)) },
                        enabled = s.saveEnabled
                    ) {
                        Icon(Icons.Default.Done, stringResource(R.string.save))
                    }
                    IconButton(onClick = { onAction(DetailPageAction.OnDeleteClick) }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete))
                    }
                }

                else -> Unit
            }
        })
    }) { innerPadding ->
        when (s) {
            DetailPageUiState.Loading -> LoadingContent(Modifier.padding(innerPadding))

            is DetailPageUiState.NewEntry -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    EditableTitleField(s.title) { onAction(DetailPageAction.UpdateModifiedTitle(it)) }
                    EditableSecretField(
                        body = s.body,
                        secretVisible = s.secretVisible,
                        onBodyChange = { onAction(DetailPageAction.UpdateModifiedBody(it)) },
                        onToggleVisibility = { onAction(DetailPageAction.ToggleVisibilityOfSecret) }
                    )
                }
            }

            is DetailPageUiState.EditMode -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (s.locked) {
                        Text(
                            text = s.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        LockedSecretCard(onUnlock = { onAction(DetailPageAction.UnlockSecret(cipherManager)) })
                    } else {
                        EditableTitleField(s.title) { onAction(DetailPageAction.UpdateModifiedTitle(it)) }
                        EditableSecretField(
                            body = s.body,
                            secretVisible = s.secretVisible,
                            onBodyChange = { onAction(DetailPageAction.UpdateModifiedBody(it)) },
                            onToggleVisibility = { onAction(DetailPageAction.ToggleVisibilityOfSecret) }
                        )
                        OutlinedButton(
                            onClick = { onAction(DetailPageAction.CopySecret(cipherManager)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                            Text(
                                text = stringResource(R.string.copy_secret),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LockedSecretCard(onUnlock: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.secret_locked_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.secret_locked_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(20.dp))
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Lock, contentDescription = null)
                Text(
                    text = stringResource(R.string.unlock),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EditableTitleField(title: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = title,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        label = { Text(stringResource(R.string.title)) },
    )
}

@Composable
private fun EditableSecretField(
    body: String,
    secretVisible: Boolean,
    onBodyChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = body,
        onValueChange = onBodyChange,
        label = { Text(stringResource(R.string.secret)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
        visualTransformation = if (!secretVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                if (secretVisible) {
                    Icon(Icons.Default.VisibilityOff, "Hide Secret")
                } else {
                    Icon(Icons.Default.Visibility, "Show Secret")
                }
            }
        }
    )
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

        override suspend fun canAuthenticate(): CanAuthenticate {
            TODO("Not yet implemented")
        }
    }

    val state = remember {
        derivedStateOf {
            DetailPageUiState.NewEntry(
                "Hello world", "happy world", true, true, isSubmitSuccessful = false
            )
        }
    }
    DetailScreen(state, mockCipherManager, {}, {})

}

@Preview
@Composable
private fun DetailPageLockedPreview() {
    val mockCipherManager = object : CipherManager {
        override suspend fun encryptData(data: ByteArray): EncryptedData {
            TODO("Not yet implemented")
        }

        override suspend fun decryptData(
            body: ByteArray, iv: ByteArray
        ): Result<ByteArray> {
            TODO("Not yet implemented")
        }

        override suspend fun canAuthenticate(): CanAuthenticate {
            TODO("Not yet implemented")
        }
    }

    val state = remember {
        derivedStateOf {
            DetailPageUiState.EditMode(
                "Wi-Fi password", "", secretVisible = false, locked = true, isSubmitSuccessful = false
            )
        }
    }
    DetailScreen(state, mockCipherManager, {}, {})
}
