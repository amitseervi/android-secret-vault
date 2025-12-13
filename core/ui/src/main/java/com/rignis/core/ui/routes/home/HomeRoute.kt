package com.rignis.core.ui.routes.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rignis.auth.domain.CanAuthenticate
import com.rignis.core.ui.R
import com.rignis.core.ui.viewmodels.home.HomePageEvent
import com.rignis.core.ui.viewmodels.home.HomePageState
import com.rignis.core.ui.viewmodels.home.HomeViewModel
import com.rignis.store.api.EncryptedDataRef

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    navigateToAddSecret: () -> Unit,
    openDetailPage: (id: String) -> Unit,
    openDrawer: () -> Unit
) {
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
        })
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
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Text(stringResource(R.string.enroll_message))
                            Spacer(modifier = Modifier.height(8.dp))
                            BiometricEnrollButton()
                        } else {
                            Text(stringResource(R.string.biometric_not_available))
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceAround,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Can not use application as Device is not secured by biometric or credential")
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
        Text(stringResource(R.string.no_secret_saved), modifier = Modifier.align(Alignment.Center))
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun BiometricEnrollButton() {
    val context = LocalContext.current
    Button(onClick = {
        try {
            val intent = Intent(Settings.ACTION_FINGERPRINT_ENROLL)
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
        modifier = modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.data.size, key = { position -> state.data[position].id }) { itemPosition ->
            val item = state.data[itemPosition]
            MyListItem(item, openDetailPage)
        }
    }
}

@Composable
private fun MyListItem(item: EncryptedDataRef, openDetailPage: (id: String) -> Unit) {
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            .clickable {
                openDetailPage(item.id)
            }, headlineContent = {
        Text(item.title)
    })
}

@Preview
@Composable
private fun PreviewMyListItem() {
    val item = EncryptedDataRef("1", "Hello world")
    MyListItem(item) {

    }
}