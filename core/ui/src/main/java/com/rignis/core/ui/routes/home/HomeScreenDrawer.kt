package com.rignis.core.ui.routes.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun HomeScreenDrawer(
    navigateToSetting: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToOss: () -> Unit,
    content: @Composable (modifier: Modifier, openDrawer: () -> Unit) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Home", modifier = Modifier.padding(16.dp))
                }
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .clickable(true) {
                        navigateToSetting()
                    }) {
                    Text(
                        "Settings", modifier = Modifier.padding(16.dp)
                    )
                }
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .clickable(true) {
                        navigateToAbout()
                    }) {
                    Text(
                        "About", modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }) {
        content(Modifier) {
            coroutineScope.launch {
                drawerState.open()
            }
        }
    }


}