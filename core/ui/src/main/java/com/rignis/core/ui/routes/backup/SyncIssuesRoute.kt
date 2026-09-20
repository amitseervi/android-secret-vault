package com.rignis.core.ui.routes.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rignis.auth.domain.CipherManager
import com.rignis.backup.api.ConflictResolution
import com.rignis.backup.api.SyncIssue
import com.rignis.backup.api.SyncIssueKind
import com.rignis.core.ui.R
import com.rignis.core.ui.analytics.TrackScreen
import com.rignis.core.ui.viewmodels.backup.BackupPageAction
import com.rignis.core.ui.viewmodels.backup.BackupViewModel
import com.rignis.mysecret.analytics.api.Analytics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncIssuesRoute(
    viewModel: BackupViewModel, cipherManager: CipherManager, analytics: Analytics, onBack: () -> Unit
) {
    TrackScreen(analytics, "SyncIssues")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val host = rememberBackupAuthHost()

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.sync_issues_title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
            }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ), actions = {
            if (state.issues.any { it.retryable }) {
                TextButton(onClick = {
                    viewModel.onAction(BackupPageAction.RetryAllFailed(host, cipherManager))
                }, enabled = !state.isBusy) {
                    Text(stringResource(R.string.retry_all_failed))
                }
            }
        })
    }) { padding ->
        if (state.issues.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.no_sync_issues))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.issues, key = { it.secretId }) { issue ->
                    SyncIssueRow(
                        issue = issue,
                        busy = state.isBusy,
                        onResolve = { resolution ->
                            viewModel.onAction(
                                BackupPageAction.ResolveIssue(issue.secretId, resolution, host, cipherManager)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncIssueRow(issue: SyncIssue, busy: Boolean, onResolve: (ConflictResolution) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = issue.title ?: stringResource(R.string.untitled_secret),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(issue.kind.label()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (issue.canPullFromCloud) {
                    OutlinedButton(onClick = { onResolve(ConflictResolution.ACCEPT_REMOTE) }, enabled = !busy) {
                        Text(stringResource(R.string.sync_issue_pull_from_cloud))
                    }
                }
                if (issue.canOverrideCloud) {
                    OutlinedButton(onClick = { onResolve(ConflictResolution.ACCEPT_LOCAL) }, enabled = !busy) {
                        Text(
                            if (issue.kind == SyncIssueKind.CONFLICT) {
                                stringResource(R.string.sync_issue_keep_this_device)
                            } else {
                                stringResource(R.string.sync_issue_retry)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun SyncIssueKind.label(): Int = when (this) {
    SyncIssueKind.CONFLICT -> R.string.sync_issue_conflict_label
    SyncIssueKind.UPLOAD_FAILED -> R.string.sync_issue_upload_failed_label
    SyncIssueKind.DOWNLOAD_FAILED -> R.string.sync_issue_upload_failed_label
    SyncIssueKind.NOT_STAGED -> R.string.sync_issue_not_staged_label
    SyncIssueKind.AUTH_REQUIRED -> R.string.sync_issue_auth_required_label
    SyncIssueKind.UNREADABLE_REMOTE -> R.string.sync_issue_unreadable_remote_label
}

