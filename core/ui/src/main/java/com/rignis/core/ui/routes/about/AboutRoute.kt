package com.rignis.core.ui.routes.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rignis.core.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutRoute(onBack: () -> Unit = {}, versionDetailProvider: VersionDetailProvider) {
    val scrollState = rememberScrollState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_secret_vault)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                })
        }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize(), verticalArrangement = Arrangement.Top
        ) {

            Text(
                text = stringResource(R.string.about_page_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.about_page_description).trimIndent(),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.how_data_is_protected))

            BulletPoint(stringResource(R.string.about_page_b1))
            BulletPoint(stringResource(R.string.about_page_b2))
            BulletPoint(stringResource(R.string.about_page_b3))
            BulletPoint(stringResource(R.string.about_page_b4))

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.privacy_first))

            BulletPoint(stringResource(R.string.privacy_b1))
            BulletPoint(stringResource(R.string.privacy_b2))
            BulletPoint(stringResource(R.string.privacy_b3))
            BulletPoint(stringResource(R.string.privacy_b4))

            Spacer(Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.version, versionDetailProvider.versionName),
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
fun BulletPoint(text: String) {
    Row(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(stringResource(R.string.bullet_point), style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
