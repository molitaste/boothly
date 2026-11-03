@file:OptIn(ExperimentalMaterial3Api::class)

package com.boothly.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boothly.app.data.Repo
import com.boothly.app.export.Exporter
import com.boothly.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Kind(val title: String, val desc: String, val mime: String, val icon: ImageVector) {
    EXCEL("Excel sheet (.xlsx)", "All products with price & supplier contacts, plus a suppliers sheet.", Exporter.MIME_XLSX, Icons.Filled.TableChart),
    PDF("PDF report with photos", "One page per supplier: card, contacts and product photos with prices.", Exporter.MIME_PDF, Icons.Filled.PictureAsPdf),
    ZIP("Full backup (.zip)", "Everything incl. photos. Restore it on another phone with Boothly.", Exporter.MIME_ZIP, Icons.Filled.Archive),
}

@Composable
fun ExportScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by Repo.data.collectAsState()
    var busy by remember { mutableStateOf<String?>(null) }
    var toSave by remember { mutableStateOf<File?>(null) }

    fun onSavePicked(uri: Uri?) {
        val f = toSave
        toSave = null
        if (uri == null || f == null) return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { Exporter.copyTo(ctx, f, uri) }
                toast(ctx, "Saved ${f.name}")
            } catch (e: Exception) {
                toast(ctx, "Save failed: ${e.message}")
            }
        }
    }

    val saveXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME_XLSX)) { onSavePicked(it) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME_PDF)) { onSavePicked(it) }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME_ZIP)) { onSavePicked(it) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = "Restoring backup…"
            scope.launch {
                try {
                    val r = withContext(Dispatchers.IO) { Exporter.restore(ctx, uri) }
                    if (r == null) toast(ctx, "This is not a Boothly backup")
                    else toast(ctx, "Restored ${r.first} suppliers, ${r.second} products")
                } catch (e: Exception) {
                    toast(ctx, "Restore failed: ${e.message}")
                } finally {
                    busy = null
                }
            }
        }
    }

    fun run(kind: Kind, share: Boolean) {
        if (data.suppliers.isEmpty() && kind != Kind.ZIP) {
            toast(ctx, "Nothing to export yet")
            return
        }
        busy = "Creating ${kind.title}…"
        scope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    when (kind) {
                        Kind.EXCEL -> Exporter.excel(ctx)
                        Kind.PDF -> Exporter.pdf(ctx)
                        Kind.ZIP -> Exporter.backup(ctx)
                    }
                }
                if (share) {
                    Exporter.share(ctx, f, kind.mime)
                } else {
                    toSave = f
                    when (kind) {
                        Kind.EXCEL -> saveXlsx.launch(f.name)
                        Kind.PDF -> savePdf.launch(f.name)
                        Kind.ZIP -> saveZip.launch(f.name)
                    }
                }
            } catch (e: Exception) {
                toast(ctx, "Export failed: ${e.message}")
            } finally {
                busy = null
            }
        }
    }

    Scaffold(topBar = { BackTopBar("Export & backup", onBack) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${data.suppliers.size} suppliers · ${data.products.size} products", style = MaterialTheme.typography.titleMedium)
            busy?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Kind.values().forEach { k ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(k.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(k.title, fontWeight = FontWeight.SemiBold)
                        }
                        Text(k.desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { run(k, share = true) }, enabled = busy == null, modifier = Modifier.weight(1f)) { Text("Share") }
                            OutlinedButton(onClick = { run(k, share = false) }, enabled = busy == null, modifier = Modifier.weight(1f)) { Text("Save to phone") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { restore.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Restore from backup .zip")
            }
            Text(
                "Restoring adds the backup to what is already on this phone – nothing is deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
