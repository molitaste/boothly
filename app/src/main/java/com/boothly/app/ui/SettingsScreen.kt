@file:OptIn(ExperimentalMaterial3Api::class)

package com.boothly.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boothly.app.data.Repo
import com.boothly.app.util.toast
import com.boothly.app.voice.PhoneDictation

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(Repo.azureKey) }
    var region by remember { mutableStateOf(Repo.azureRegion) }
    val phoneEngine = remember { PhoneDictation.available(ctx) }

    Scaffold(topBar = { BackTopBar("Settings", onBack) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionTitle("Voice notes – Microsoft speech")
            Text(
                "Optional. With a Microsoft Azure key, voice notes use Microsoft speech recognition " +
                    "(English, Chinese, Arabic). It works in China and needs internet. Without a key, the phone's own voice typing is used.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Field("Azure Speech key", key, { key = it })
            Field("Region", region, { region = it }, hint = "eastasia")
            Text(
                "Get it at portal.azure.com → Create a resource → \"Speech\" → pricing tier Free F0 → " +
                    "open it → Keys and Endpoint → copy KEY 1 and Location/Region.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    Repo.azureKey = key
                    Repo.azureRegion = region.ifBlank { "eastasia" }
                    toast(ctx, if (key.isBlank()) "Saved – using phone voice typing" else "Saved – using Microsoft speech")
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }

            SectionTitle("Phone voice typing")
            Text(
                if (phoneEngine) "Available on this phone." else "Not available on this phone – use a Microsoft key.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
