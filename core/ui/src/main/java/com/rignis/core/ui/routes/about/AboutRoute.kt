package com.rignis.core.ui.routes.about

import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutRoute(onBack: () -> Unit = {}) {
    val scrollState = rememberScrollState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("About Secret Vault") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                text = "Secret Vault",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = """
Secret Vault securely stores your private information using industry-standard encryption. Your secrets remain encrypted at all times and can only be viewed after biometric authentication.
                """.trimIndent(), style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            SectionTitle("How your data is protected")

            BulletPoint("AES encryption is used to securely protect all stored secrets.")
            BulletPoint("Encrypted data is stored locally using Room database.")
            BulletPoint("Your decryption key is stored inside AndroidKeyStore hardware, the most secure place on your device.")
            BulletPoint("Only your biometric authentication can unlock your secret information.")

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            SectionTitle("Privacy first")

            BulletPoint("Your secrets never leave your device.")
            BulletPoint("No secret data is logged, tracked, or sent to servers.")
            BulletPoint("Even after authentication, your decrypted data is never stored, logged, or shared by Secret Vault.")
            BulletPoint("You control what you store and when you delete it.")

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Version 1.0.0",
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
        Text("• ", style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
