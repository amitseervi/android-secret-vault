package com.rignis.core.ui.routes.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rignis.auth.domain.CanAuthenticate
import com.rignis.auth.domain.CipherManager
import com.rignis.core.ui.R
import com.rignis.core.ui.analytics.TrackScreen
import com.rignis.core.ui.routes.backup.AutoSyncOnOpenEffect
import com.rignis.core.ui.routes.backup.rememberBackupAuthHost
import com.rignis.core.ui.viewmodels.backup.BackupViewModel
import com.rignis.core.ui.viewmodels.home.HomePageEvent
import com.rignis.core.ui.viewmodels.home.HomePageState
import com.rignis.core.ui.viewmodels.home.HomeViewModel
import com.rignis.mysecret.analytics.api.Analytics
import com.rignis.mysecret.analytics.api.AnalyticsEvent
import com.rignis.mysecret.analytics.api.AnalyticsParam
import com.rignis.store.api.EncryptedDataRef

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    backupViewModel: BackupViewModel,
    cipherManager: CipherManager,
    navigateToAddSecret: () -> Unit,
    openDetailPage: (id: String) -> Unit,
    openDrawer: () -> Unit,
    analytics: Analytics
) {
    TrackScreen(analytics, "Home")
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAuthenticationCapability()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val backupAuthHost = rememberBackupAuthHost()
    AutoSyncOnOpenEffect(backupViewModel, cipherManager, backupAuthHost)

    val state = viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::onAction, navigateToAddSecret, openDetailPage, openDrawer)

}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreen(
    state: State<HomePageState>,
    onAction: (HomePageEvent) -> Unit,
    navigateToAddSecret: () -> Unit,
    openDetailPage: (id: String) -> Unit,
    openDrawer: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Text(stringResource(R.string.top_app_bar_title))
        }, navigationIcon = {
            IconButton(onClick = openDrawer) {
                Icon(Icons.Default.Menu, "Drawer Menu")
            }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ))
    }, floatingActionButton = {
        if (state.value.canAuthenticate == CanAuthenticate.YES) {
            FloatingActionButton(navigateToAddSecret) {
                Icon(
                    imageVector = Icons.Default.Add, contentDescription = "Add"
                )
            }
        }

    }) { innerPadding ->
        if (state.value.canAuthenticate != CanAuthenticate.YES) {
            when (state.value.canAuthenticate) {
                CanAuthenticate.NOT_ENROLLED -> {
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Text(stringResource(R.string.enroll_message), textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            BiometricEnrollButton()
                        } else {
                            Text(
                                stringResource(R.string.biometric_not_available),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.biometric_not_available),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            when (val stateValue = state.value) {
                is HomePageState.Empty -> {
                    HomePageEmpty(modifier = Modifier.padding(innerPadding))
                }

                is HomePageState.Loaded -> {
                    HomePage(
                        modifier = Modifier.padding(innerPadding),
                        state = stateValue,
                        openDetailPage = openDetailPage
                    )
                }
            }
        }

    }
}

@Composable
private fun HomePageEmpty(modifier: Modifier) {
    return Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.no_secret_saved),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.no_secret_saved_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun BiometricEnrollButton() {
    val context = LocalContext.current
    Button(onClick = {
        try {
            val intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }) {
        Text(stringResource(R.string.enroll_biometrics))
    }
}

@Composable
private fun HomePage(
    state: HomePageState.Loaded, modifier: Modifier, openDetailPage: (id: String) -> Unit
) {
    return LazyColumn(
        modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.data.size, key = { position -> state.data[position].id }) { itemPosition ->
            val item = state.data[itemPosition]
            MyListItem(item, openDetailPage)
        }
    }
}

@Composable
private fun MyListItem(item: EncryptedDataRef, openDetailPage: (id: String) -> Unit) {
    Card(
        onClick = { openDetailPage(item.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.tap_to_view),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview
@Composable
private fun PreviewMyListItem() {
    val item = EncryptedDataRef("1", "Hello world")
    MyListItem(item) {

    }
}