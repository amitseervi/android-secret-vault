package com.rignis.core.ui.routes.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.rignis.core.ui.R
import com.rignis.core.ui.viewmodels.home.HomePageEvent
import com.rignis.core.ui.viewmodels.home.HomePageState
import com.rignis.store.api.EncryptedDataRef

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    state: State<HomePageState>,
    onAction: (HomePageEvent) -> Unit,
    navigateToAddSecret: () -> Unit,
    openDetailPage: (id: String) -> Unit,
    openDrawer: () -> Unit
) {
    LocalContext.current as FragmentActivity
    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Text(stringResource(R.string.top_app_bar_title))
        }, navigationIcon = {
            IconButton(onClick = openDrawer) {
                Icon(Icons.Default.Menu, "Drawer Menu")
            }
        })
    }, floatingActionButton = {
        FloatingActionButton(navigateToAddSecret) {
            Icon(
                imageVector = Icons.Default.Add, contentDescription = "Add"
            )
        }
    }) { innerPadding ->
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

@Composable
private fun HomePageEmpty(modifier: Modifier) {
    return Box(modifier = modifier.fillMaxSize()) {
        Text("Empty", modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun HomePage(
    state: HomePageState.Loaded, modifier: Modifier, openDetailPage: (id: String) -> Unit
) {
    return LazyColumn(modifier = modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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