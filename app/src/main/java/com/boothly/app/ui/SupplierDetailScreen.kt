@file:OptIn(ExperimentalMaterial3Api::class)

package com.boothly.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boothly.app.data.Repo
import com.boothly.app.util.CityDetector
import com.boothly.app.util.Contact
import com.boothly.app.util.MapApps

@Composable
fun SupplierDetailScreen(
    id: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddProduct: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onPhoto: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val data by Repo.data.collectAsState()
    val s = data.suppliers.find { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }

    if (s == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val products = data.products.filter { it.supplierId == id }.sortedByDescending { it.createdAt }

    if (showMap) {
        MapPickerDialog(query = MapApps.queryFor(s), onDismiss = { showMap = false })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete supplier?") },
            text = { Text("This deletes ${s.displayName} and its ${products.size} product(s).") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteSupplier(id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            BackTopBar(s.displayName, onBack) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProduct,
                icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                text = { Text("Add product") },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        ) {
            if (s.cardImages.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.cardImages) { n ->
                            Thumb(n, Modifier.size(width = 260.dp, height = 165.dp)) { onPhoto(n) }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(s.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val who = listOf(s.contactName, s.title).filter { it.isNotBlank() }.joinToString(" · ")
                    if (who.isNotBlank()) Text(who, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (s.booth.isNotBlank()) {
                            AssistChip(onClick = {}, label = { Text("Booth ${s.booth}") }, modifier = Modifier.padding(top = 4.dp))
                        }
                        val city = CityDetector.cityOf(s)
                        if (city.isNotBlank()) {
                            AssistChip(
                                onClick = { showMap = true },
                                label = { Text(city) },
                                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            items(s.phones) { ph ->
                ContactRow(Icons.Filled.Call, ph, "Phone", onClick = { Contact.dial(ctx, ph) }) {
                    IconButton(onClick = { Contact.whatsapp(ctx, ph) }) {
                        Icon(Icons.Filled.Chat, contentDescription = "WhatsApp", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (s.whatsapp.isNotBlank()) item {
                ContactRow(Icons.Filled.Chat, s.whatsapp, "WhatsApp – tap to chat", onClick = { Contact.whatsapp(ctx, s.whatsapp) })
            }
            if (s.wechat.isNotBlank()) item {
                ContactRow(Icons.Filled.Forum, s.wechat, "WeChat – tap to copy ID & open WeChat", onClick = { Contact.wechat(ctx, s.wechat) })
            }
            items(s.emails) { em ->
                ContactRow(Icons.Filled.Email, em, "Email", onClick = { Contact.email(ctx, em, "Follow-up from the fair – ${s.displayName}") })
            }
            if (s.website.isNotBlank()) item {
                ContactRow(Icons.Filled.Language, s.website, "Website", onClick = { Contact.web(ctx, s.website) })
            }
            if (s.address.isNotBlank()) item {
                ContactRow(Icons.Filled.Place, s.address, "Address – tap to choose map app", onClick = { showMap = true })
            }
            item {
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { Contact.saveToPhoneContacts(ctx, s) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Contact")
                    }
                    FilledTonalButton(onClick = { showMap = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Map")
                    }
                    FilledTonalButton(onClick = { Contact.shareText(ctx, s) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
            if (s.notes.isNotBlank()) item {
                SectionTitle("Notes")
                Text(s.notes)
            }

            item { SectionTitle("Products (${products.size})") }
            if (products.isEmpty()) item {
                Text(
                    "No products yet – tap “Add product” to photograph one and note its price.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(products, key = { it.id }) { p ->
                Card(onClick = { onOpenProduct(p.id) }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val photo = p.photos.firstOrNull()
                        if (photo != null) Thumb(photo, Modifier.size(72.dp))
                        else Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Inventory, null) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.priceLabel.isNotBlank()) Text(p.priceLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            if (p.moq.isNotBlank()) Text("MOQ ${p.moq}", style = MaterialTheme.typography.bodySmall)
                            if (p.photos.size > 1) Text("${p.photos.size} photos", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ContactRow(
    icon: ImageVector,
    text: String,
    label: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}
