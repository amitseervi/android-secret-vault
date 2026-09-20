package com.rignis.core.ui.routes.backup

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rignis.auth.domain.CipherManager
import com.rignis.backup.api.BackupAuthHost
import com.rignis.backup.api.BackupState
import com.rignis.backup.api.SyncTrigger
import com.rignis.core.ui.R
import com.rignis.core.ui.analytics.TrackScreen
import com.rignis.core.ui.routes.detail.ClipBoardHandler
import com.rignis.core.ui.viewmodels.backup.BackupPageAction
import com.rignis.core.ui.viewmodels.backup.BackupPageState
import com.rignis.core.ui.viewmodels.backup.BackupViewModel
import com.rignis.mysecret.analytics.api.Analytics
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupRoute(
    viewModel: BackupViewModel,
    cipherManager: CipherManager,
    clipBoardHandler: ClipBoardHandler,
    analytics: Analytics,
    onBack: () -> Unit,
    onViewIssues: () -> Unit
) {
    TrackScreen(analytics, "CloudBackup")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val host = rememberBackupAuthHost()
    val onAction: (BackupPageAction) -> Unit = viewModel::onAction

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.cloud_backup_title)) }, navigationIcon = {
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
            when (val backupState = state.backupState) {
                BackupState.NotConfigured -> NotConnectedContent(state, onAction, host)
                is BackupState.NeedsCode -> NeedsCodeContent(backupState.accountEmail, state, onAction, host, cipherManager)
                is BackupState.Ready -> ReadyContent(backupState, state, onAction, host, cipherManager, onViewIssues)
                is BackupState.NeedsReauth -> NeedsReauthContent(backupState.accountEmail, onAction, host)
                is BackupState.PasswordChangedElsewhere -> CodeReentryContent(
                    accountEmail = backupState.accountEmail,
                    message = stringResource(R.string.password_changed_elsewhere_message),
                    state = state, onAction = onAction, host = host, cipherManager = cipherManager
                )

                is BackupState.CodeMismatch -> CodeReentryContent(
                    accountEmail = backupState.accountEmail,
                    message = stringResource(R.string.code_mismatch_message),
                    state = state, onAction = onAction, host = host, cipherManager = cipherManager,
                    showSwitchAccount = true
                )
            }

            if (state.lastActionFailed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.generic_action_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    state.pendingGeneratedCode?.let { code ->
        GeneratedCodeDialog(
            code = code,
            busy = state.isBusy,
            onCopy = { clipBoardHandler.copyPassword(code) },
            onConfirm = { onAction(BackupPageAction.SubmitCode(host, cipherManager, code)) },
            onDismiss = { onAction(BackupPageAction.DismissGeneratedCode) }
        )
    }

    state.revealedCode?.let { code ->
        RevealedCodeDialog(
            code = code,
            onCopy = { clipBoardHandler.copyPassword(code) },
            onDismiss = { onAction(BackupPageAction.DismissRevealedCode) }
        )
    }
}

@Composable
private fun NotConnectedContent(
    state: BackupPageState, onAction: (BackupPageAction) -> Unit, host: BackupAuthHost
) {
    InfoCard(
        icon = Icons.Outlined.CloudOff,
        title = stringResource(R.string.cloud_backup_not_connected_title),
        body = stringResource(R.string.cloud_backup_not_connected_body)
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onAction(BackupPageAction.LinkAccount(host)) },
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.connect_google_account))
    }
}

@Composable
private fun NeedsReauthContent(
    accountEmail: String, onAction: (BackupPageAction) -> Unit, host: BackupAuthHost
) {
    InfoCard(
        icon = Icons.Outlined.Warning,
        title = accountEmail,
        body = stringResource(R.string.needs_reauth_message)
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = { onAction(BackupPageAction.LinkAccount(host)) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reauthorize_drive_access))
    }
}

@Composable
private fun NeedsCodeContent(
    accountEmail: String,
    state: BackupPageState,
    onAction: (BackupPageAction) -> Unit,
    host: BackupAuthHost,
    cipherManager: CipherManager
) {
    Text(
        text = accountEmail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.cloud_backup_needs_code_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(16.dp))

    Button(
        onClick = { onAction(BackupPageAction.GenerateNewCode) },
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.generate_new_backup_key))
    }

    Spacer(Modifier.height(12.dp))

    CodeEntryCard(
        title = stringResource(R.string.enter_existing_backup_key),
        state = state,
        onAction = onAction,
        host = host,
        cipherManager = cipherManager
    )
}

@Composable
private fun CodeReentryContent(
    accountEmail: String,
    message: String,
    state: BackupPageState,
    onAction: (BackupPageAction) -> Unit,
    host: BackupAuthHost,
    cipherManager: CipherManager,
    showSwitchAccount: Boolean = false
) {
    InfoCard(icon = Icons.Outlined.Warning, title = accountEmail, body = message)
    Spacer(Modifier.height(16.dp))
    CodeEntryCard(
        title = stringResource(R.string.backup_key_label),
        state = state,
        onAction = onAction,
        host = host,
        cipherManager = cipherManager
    )
    if (showSwitchAccount) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { onAction(BackupPageAction.LinkAccount(host)) }) {
            Text(stringResource(R.string.switch_google_account))
        }
    }
}

@Composable
private fun CodeEntryCard(
    title: String,
    state: BackupPageState,
    onAction: (BackupPageAction) -> Unit,
    host: BackupAuthHost,
    cipherManager: CipherManager
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.enteredCode,
                onValueChange = { onAction(BackupPageAction.UpdateEnteredCode(it)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                label = { Text(stringResource(R.string.backup_key_label)) },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onAction(BackupPageAction.SubmitCode(host, cipherManager, state.enteredCode)) },
                enabled = !state.isBusy && state.enteredCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.submit_backup_key))
            }
        }
    }
}

@Composable
private fun ReadyContent(
    ready: BackupState.Ready,
    state: BackupPageState,
    onAction: (BackupPageAction) -> Unit,
    host: BackupAuthHost,
    cipherManager: CipherManager,
    onViewIssues: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.cloud_backup_connected_as, ready.accountEmail),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (ready.lastSyncAt > 0) {
                            stringResource(R.string.last_synced_at, formatTimestamp(ready.lastSyncAt))
                        } else {
                            stringResource(R.string.last_synced_never)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onAction(BackupPageAction.SyncNow(host, cipherManager, SyncTrigger.USER_REQUESTED)) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sync_in_progress))
                } else {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.back_up_now))
                }
            }

            if (state.notBackedUpCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.not_backed_up_count, state.notBackedUpCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.issues.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onViewIssues) {
                    Text(stringResource(R.string.view_sync_issues, state.issues.size))
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            SettingsRow(
                label = stringResource(R.string.show_my_backup_key),
                onClick = { onAction(BackupPageAction.RevealStoredCode(cipherManager)) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            var showDisconnectDialog by remember { mutableStateOf(false) }
            SettingsRow(
                label = stringResource(R.string.disconnect_backup),
                onClick = { showDisconnectDialog = true },
                destructive = true
            )
            if (showDisconnectDialog) {
                DisconnectDialog(
                    onKeepCloudCopy = {
                        showDisconnectDialog = false
                        onAction(BackupPageAction.DisableBackup(deleteCloudCopy = false))
                    },
                    onDeleteCloudCopy = {
                        showDisconnectDialog = false
                        onAction(BackupPageAction.DisableBackup(deleteCloudCopy = true))
                    },
                    onDismiss = { showDisconnectDialog = false }
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .then(Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = label,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DisconnectDialog(
    onKeepCloudCopy: () -> Unit, onDeleteCloudCopy: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.disconnect_backup_confirm_title)) },
        text = { Text(stringResource(R.string.disconnect_backup_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onKeepCloudCopy) { Text(stringResource(R.string.disconnect_backup_keep_cloud_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDeleteCloudCopy) { Text(stringResource(R.string.disconnect_backup_delete_cloud_copy)) }
        }
    )
}

@Composable
private fun GeneratedCodeDialog(code: String, busy: Boolean, onCopy: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generate_new_backup_key)) },
        text = {
            Column {
                Text(
                    text = code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copy))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.backup_key_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) { Text(stringResource(R.string.ive_saved_this_key)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun RevealedCodeDialog(code: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_key_label)) },
        text = {
            Column {
                Text(text = code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copy))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
