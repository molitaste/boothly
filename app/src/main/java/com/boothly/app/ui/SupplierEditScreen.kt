@file:OptIn(ExperimentalMaterial3Api::class)

package com.boothly.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boothly.app.data.Repo
import com.boothly.app.data.Supplier
import com.boothly.app.ocr.CardOcr
import com.boothly.app.ocr.CardParser
import com.boothly.app.ocr.ParsedCard
import com.boothly.app.util.CityDetector
import com.boothly.app.util.Images
import com.boothly.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun SupplierEditScreen(
    id: String,
    onBack: () -> Unit,
    onSaved: (String, Boolean) -> Unit,
    onPhoto: (String) -> Unit,
    onScan: () -> Unit,
    scanned: String,
    onScannedConsumed: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(id) { if (id == "new") null else Repo.supplier(id) }
    val isNew = existing == null

    val supplierId = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var company by rememberSaveable { mutableStateOf(existing?.company ?: "") }
    var contact by rememberSaveable { mutableStateOf(existing?.contactName ?: "") }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var booth by rememberSaveable { mutableStateOf(existing?.booth ?: "") }
    var phones by rememberSaveable { mutableStateOf(existing?.phones?.joinToString("\n") ?: "") }
    var emails by rememberSaveable { mutableStateOf(existing?.emails?.joinToString("\n") ?: "") }
    var whatsapp by rememberSaveable { mutableStateOf(existing?.whatsapp ?: "") }
    var wechat by rememberSaveable { mutableStateOf(existing?.wechat ?: "") }
    var website by rememberSaveable { mutableStateOf(existing?.website ?: "") }
    var address by rememberSaveable { mutableStateOf(existing?.address ?: "") }
    var city by rememberSaveable { mutableStateOf(existing?.city ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var ocrText by rememberSaveable { mutableStateOf(existing?.ocrText ?: "") }
    var cards by rememberSaveable { mutableStateOf(existing?.cardImages?.joinToString("|") ?: "") }
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    fun cardList(): List<String> = cards.split("|").filter { it.isNotBlank() }

    fun mergeLines(current: String, items: List<String>): String {
        val out = current.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val keys = out.map { l -> l.filter { it.isLetterOrDigit() }.lowercase() }.toMutableSet()
        items.forEach { v ->
            val k = v.filter { it.isLetterOrDigit() }.lowercase()
            if (k.isNotEmpty() && keys.add(k)) out.add(v.trim())
        }
        return out.joinToString("\n")
    }

    fun applyParsed(p: ParsedCard) {
        if (company.isBlank()) company = p.company
        if (contact.isBlank()) contact = p.name
        if (title.isBlank()) title = p.title
        if (booth.isBlank()) booth = p.booth
        if (whatsapp.isBlank()) whatsapp = p.whatsapp
        if (wechat.isBlank()) wechat = p.wechat
        if (website.isBlank()) website = p.website
        if (address.isBlank()) address = p.address
        phones = mergeLines(phones, p.phones)
        emails = mergeLines(emails, p.emails)
        if (p.notes.isNotEmpty()) notes = mergeLines(notes, p.notes)
    }

    fun readCards(names: List<String>) {
        if (names.isEmpty()) return
        cards = (cardList() + names).joinToString("|")
        busy = true
        scope.launch {
            try {
                val res = CardOcr.read(ctx, names.map { Repo.photoFile(it) })
                if (res.lines.isEmpty() && res.qr.isEmpty()) {
                    toast(ctx, "No text found – try a sharper photo")
                } else {
                    ocrText = listOf(ocrText, res.raw).filter { it.isNotBlank() }.joinToString("\n---\n")
                    applyParsed(CardParser.parse(res.lines, res.qr))
                    if (city.isBlank()) city = CityDetector.detect(listOf(address, company, res.raw))
                }
            } catch (e: Exception) {
                toast(ctx, "Could not read card: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        busy = true
        scope.launch {
            val names = withContext(Dispatchers.IO) { uris.mapNotNull { Images.importUri(ctx, it) } }
            busy = false
            readCards(names)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(2)) { uris ->
        importUris(uris)
    }

    // Pictures coming back from the in-app scanner
    LaunchedEffect(scanned) {
        if (scanned.isNotBlank()) {
            val names = scanned.split("|").filter { it.isNotBlank() }
            onScannedConsumed()
            readCards(names)
        }
    }

    // New supplier: jump straight into card scanning
    LaunchedEffect(Unit) {
        if (isNew && !autoStarted && cardList().isEmpty() && scanned.isBlank()) {
            autoStarted = true
            onScan()
        }
    }

    fun save() {
        val s = Supplier(
            id = supplierId,
            company = company.trim(),
            contactName = contact.trim(),
            title = title.trim(),
            phones = phones.lines().map { it.trim() }.filter { it.isNotEmpty() },
            emails = emails.lines().map { it.trim() }.filter { it.isNotEmpty() },
            website = website.trim(),
            wechat = wechat.trim(),
            whatsapp = whatsapp.trim(),
            address = address.trim(),
            city = city.trim().ifBlank { CityDetector.detect(listOf(address, company, ocrText)) },
            booth = booth.trim(),
            notes = notes.trim(),
            cardImages = cardList(),
            ocrText = ocrText,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        if (s.company.isBlank() && s.contactName.isBlank() && s.cardImages.isEmpty() && s.phones.isEmpty()) {
            toast(ctx, "Scan a card or type a company name first")
            return
        }
        Repo.upsertSupplier(s)
        onSaved(s.id, isNew)
    }

    Scaffold(
        topBar = {
            BackTopBar(if (isNew) "New supplier" else "Edit supplier", onBack) {
                TextButton(onClick = { save() }, enabled = !busy) { Text("SAVE") }
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
            SectionTitle("Business card")
            val list = cardList()
            if (list.isNotEmpty()) {
                PhotoStrip(list, onOpen = onPhoto, onRemove = { n -> cards = cardList().filter { it != n }.joinToString("|") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onScan() }, enabled = !busy, modifier = Modifier.weight(2f)) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan card")
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
            Text(
                "Tip: scan the back side too if it has English / Arabic. Fields are filled automatically – check them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (busy) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Reading card…", style = MaterialTheme.typography.bodySmall)
            }

            SectionTitle("Supplier")
            Field("Company", company, { company = it })
            Field("Contact person", contact, { contact = it })
            Field("Job title", title, { title = it })
            Field("Booth number", booth, { booth = it }, hint = "e.g. 9.2E15")

            SectionTitle("Contact")
            Field("Phones (one per line)", phones, { phones = it }, KeyboardType.Phone, singleLine = false)
            Field("Emails (one per line)", emails, { emails = it }, KeyboardType.Email, singleLine = false)
            Field("WhatsApp", whatsapp, { whatsapp = it }, KeyboardType.Phone, hint = "+86 138 0000 0000")
            Field("WeChat ID", wechat, { wechat = it })
            Field("Website", website, { website = it }, KeyboardType.Uri)
            Field("Address", address, { address = it }, singleLine = false)
            Field("City / region (for groups)", city, { city = it }, hint = "Guangzhou")

            SectionTitle("Notes")
            Field("Notes", notes, { notes = it }, singleLine = false, hint = "Impressions, follow-up, quality…")
            VoiceNoteButton { t -> notes = listOf(notes.trim(), t).filter { it.isNotEmpty() }.joinToString("\n") }

            if (ocrText.isNotBlank()) {
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide text read from card" else "Show text read from card")
                }
                if (showRaw) {
                    SelectionContainer {
                        Text(ocrText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { save() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save supplier") }
            Spacer(Modifier.height(32.dp))
        }
    }
}
