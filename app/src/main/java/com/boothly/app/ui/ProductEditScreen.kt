@file:OptIn(ExperimentalMaterial3Api::class)

package com.boothly.app.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boothly.app.data.Product
import com.boothly.app.data.Repo
import com.boothly.app.util.Images
import com.boothly.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val CURRENCIES = listOf("USD", "CNY", "EUR", "AED", "SAR", "JOD", "GBP", "HKD")

@Composable
fun ProductEditScreen(
    supplierId: String,
    productId: String,
    onBack: () -> Unit,
    onPhoto: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(productId) { if (productId == "new") null else Repo.product(productId) }
    val supplier = remember(supplierId) { Repo.supplier(supplierId) }

    var pid by rememberSaveable { mutableStateOf(existing?.id ?: UUID.randomUUID().toString()) }
    var isNew by rememberSaveable { mutableStateOf(existing == null) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var price by rememberSaveable { mutableStateOf(existing?.price ?: "") }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: Repo.lastCurrency) }
    var moq by rememberSaveable { mutableStateOf(existing?.moq ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var photos by rememberSaveable { mutableStateOf(existing?.photos?.joinToString("|") ?: "") }
    var pending by rememberSaveable { mutableStateOf("") }
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    var created by rememberSaveable { mutableStateOf(existing?.createdAt ?: 0L) }
    var busy by remember { mutableStateOf(false) }
    var currencyMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun photoList(): List<String> = photos.split("|").filter { it.isNotBlank() }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val n = pending
        pending = ""
        if (ok && n.isNotBlank()) {
            busy = true
            scope.launch {
                val good = withContext(Dispatchers.IO) { Images.normalize(Repo.photoFile(n)) }
                if (good) photos = (photoList() + n).joinToString("|")
                busy = false
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            scope.launch {
                val names = withContext(Dispatchers.IO) { uris.mapNotNull { Images.importUri(ctx, it) } }
                photos = (photoList() + names).joinToString("|")
                busy = false
            }
        }
    }

    fun openCamera() {
        val f = Images.newPhotoFile()
        pending = f.name
        try {
            cameraLauncher.launch(Images.uriFor(ctx, f))
        } catch (_: ActivityNotFoundException) {
            toast(ctx, "No camera app found")
        }
    }

    LaunchedEffect(Unit) {
        if (isNew && !autoStarted && photoList().isEmpty()) {
            autoStarted = true
            openCamera()
        }
    }

    fun save(): Boolean {
        if (name.isBlank() && price.isBlank() && photoList().isEmpty()) {
            toast(ctx, "Add a photo, name or price first")
            return false
        }
        Repo.lastCurrency = currency
        Repo.upsertProduct(
            Product(
                id = pid,
                supplierId = supplierId,
                name = name.trim(),
                price = price.trim(),
                currency = currency,
                moq = moq.trim(),
                notes = notes.trim(),
                photos = photoList(),
                createdAt = if (created > 0) created else System.currentTimeMillis(),
            )
        )
        return true
    }

    fun resetForNext() {
        pid = UUID.randomUUID().toString()
        isNew = true
        name = ""
        price = ""
        moq = ""
        notes = ""
        photos = ""
        created = 0L
        openCamera()
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete product?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteProduct(pid)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            BackTopBar(if (isNew) "New product" else "Edit product", onBack) {
                if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                TextButton(onClick = { if (save()) onBack() }, enabled = !busy) { Text("SAVE") }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (supplier != null) {
                Text(
                    supplier.displayName + (if (supplier.booth.isNotBlank()) " · Booth ${supplier.booth}" else ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            SectionTitle("Photos")
            val list = photoList()
            if (list.isNotEmpty()) {
                PhotoStrip(list, onOpen = onPhoto, onRemove = { n -> photos = photoList().filter { it != n }.joinToString("|") }, height = 130)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { openCamera() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (list.isEmpty()) "Take photo" else "Add photo")
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))

            SectionTitle("Details")
            Field("Product name / model", name, { name = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { v -> price = v.filter { it.isDigit() || it == '.' || it == ',' || it == '-' || it == ' ' } },
                    label = { Text("Price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(onClick = { currencyMenu = true }, modifier = Modifier.padding(top = 8.dp)) { Text(currency) }
                    DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
                        CURRENCIES.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = {
                                currency = c
                                currencyMenu = false
                            })
                        }
                    }
                }
            }
            Field("MOQ (minimum order)", moq, { moq = it }, hint = "e.g. 500 pcs")
            Field("Notes", notes, { notes = it }, singleLine = false, hint = "Size, material, lead time, colours…")

            Spacer(Modifier.height(16.dp))
            Button(onClick = { if (save()) onBack() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            OutlinedButton(
                onClick = {
                    if (save()) {
                        toast(ctx, "Saved – next product")
                        resetForNext()
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text("Save & add another product") }
            Spacer(Modifier.height(32.dp))
        }
    }
}
