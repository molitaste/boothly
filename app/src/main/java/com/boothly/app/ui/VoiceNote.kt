package com.boothly.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.boothly.app.data.Repo
import com.boothly.app.voice.AzureDictation
import com.boothly.app.voice.Dictation
import com.boothly.app.voice.DictationListener
import com.boothly.app.voice.PhoneDictation
import com.boothly.app.voice.VOICE_LANGUAGES

/** "Voice note" button: opens the dictation dialog and appends the text to a notes field. */
@Composable
fun VoiceNoteButton(onText: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Voice note → text")
    }
    if (open) {
        VoiceNoteDialog(
            onDismiss = { open = false },
            onText = {
                open = false
                onText(it)
            },
        )
    }
}

@Composable
fun VoiceNoteDialog(onDismiss: () -> Unit, onText: (String) -> Unit) {
    val ctx = LocalContext.current
    var language by remember { mutableStateOf(Repo.voiceLanguage) }
    var text by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val useAzure = Repo.azureKey.isNotBlank()

    val listener = remember {
        object : DictationListener {
            override fun onPartial(t: String) {
                partial = t
            }

            override fun onFinal(t: String) {
                partial = ""
                text = listOf(text.trim(), t.trim()).filter { it.isNotEmpty() }.joinToString(" ")
            }

            override fun onError(message: String) {
                error = message
            }

            override fun onStopped() {
                listening = false
                partial = ""
            }
        }
    }
    val engine: Dictation = remember {
        if (useAzure) AzureDictation(Repo.azureKey, Repo.azureRegion, listener) else PhoneDictation(ctx, listener)
    }
    DisposableEffect(Unit) {
        onDispose { engine.release() }
    }

    fun begin() {
        error = null
        listening = true
        Repo.voiceLanguage = language
        engine.start(language)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else error = "Microphone permission is needed for voice notes"
    }

    fun toggle() {
        if (listening) {
            engine.stop()
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    AlertDialog(
        onDismissRequest = {
            if (listening) engine.stop()
            onDismiss()
        },
        title = { Text("Voice note") },
        text = {
            Column {
                Text(
                    "Engine: " + if (useAzure) "Microsoft" else "Phone voice typing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VOICE_LANGUAGES.forEach { l ->
                        FilterChip(
                            selected = language == l.code,
                            onClick = { if (!listening) language = l.code },
                            label = { Text(l.label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .background(if (listening) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (listening) "Stop" else "Start",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Text(
                    if (listening) "Listening… speak now, tap to stop" else "Tap the microphone and speak",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                if (partial.isNotBlank()) {
                    Text(partial, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text (you can edit)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (listening) engine.stop()
                    val all = listOf(text.trim(), partial.trim()).filter { it.isNotEmpty() }.joinToString(" ")
                    if (all.isNotBlank()) onText(all) else onDismiss()
                },
            ) { Text("Add to notes") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (listening) engine.stop()
                onDismiss()
            }) { Text("Cancel") }
        },
    )
}
