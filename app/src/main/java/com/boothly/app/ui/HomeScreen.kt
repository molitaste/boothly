@file:OptIn(ExperimentalMaterial3Api::class)

package com.boothly.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boothly.app.data.Product
import com.boothly.app.data.Repo
import com.boothly.app.data.Supplier
import com.boothly.app.util.CityDetector

@Composable
fun HomeScreen(
    onOpenSupplier: (String) -> Unit,
    onNewSupplier: () -> Unit,
    onOpenProduct: (Product) -> Unit,
    onExport: () -> Unit,
    onSettings: () -> Unit,
) {
    val data by Repo.data.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var groupMode by rememberSaveable { mutableIntStateOf(1) }

    val suppliersById = data.suppliers.associateBy { it.id }
    val suppliers = data.suppliers
        .filter { s -> s.matches(query) || data.products.any { it.supplierId == s.id && it.matches(query) } }
        .sortedByDescending { it.createdAt }
    val products = data.products
        .filter { p -> p.matches(query) || (suppliersById[p.supplierId]?.matches(query) ?: false) }
        .sortedByDescending { it.createdAt }

    Scaffold(
        topBar = {
            Column(Modifier.background(headerColor())) {
                TopAppBar(
                    title = { Text("Boothly", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onExport) { Icon(Icons.Filled.Share, contentDescription = "Export & backup") }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                    },
                    colors = oceanTopBarColors(),
                )
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search…", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x33FFFFFF),
                        unfocusedContainerColor = Color(0x26FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = Color(0xCCFFFFFF),
                        unfocusedPlaceholderColor = Color(0xB3FFFFFF),
                        focusedLeadingIconColor = Color.White,
                        unfocusedLeadingIconColor = Color(0xCCFFFFFF),
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    indicator = { pos ->
                        TabRowDefaults.Indicator(Modifier.tabIndicatorOffset(pos[tab]), height = 3.dp, color = OceanOrange)
                    },
                    divider = {},
                ) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, selectedContentColor = Color.White, unselectedContentColor = Color(0xB3FFFFFF), text = { Text("Suppliers (${data.suppliers.size})", fontWeight = FontWeight.SemiBold) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, selectedContentColor = Color.White, unselectedContentColor = Color(0xB3FFFFFF), text = { Text("Products (${data.products.size})", fontWeight = FontWeight.SemiBold) })
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewSupplier,
                containerColor = OceanOrange,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                text = { Text("Scan new supplier") },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            if (tab == 0) {
                if (suppliers.isEmpty()) {
                    Empty(if (data.suppliers.isEmpty()) "No suppliers yet.\nTap “Scan new supplier” and photograph a business card." else "Nothing matches your search.")
                } else {
                    val groups: List<Pair<String, List<Supplier>>> = when (groupMode) {
                        1 -> suppliers.groupBy { CityDetector.cityOf(it).ifBlank { "Unknown location" } }
                            .toList().sortedWith(compareBy<Pair<String, List<Supplier>>>({ it.first == "Unknown location" }, { -it.second.size }, { it.first }))
                        2 -> suppliers.groupBy { CityDetector.hallOf(it).ifBlank { "No booth number" } }
                            .toList().sortedWith(compareBy<Pair<String, List<Supplier>>>({ it.first == "No booth number" }, { it.first }))
                        else -> listOf("" to suppliers)
                    }
                    Row(
                        Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Group:", style = MaterialTheme.typography.labelMedium)
                        listOf("None", "City", "Hall").forEachIndexed { i, label ->
                            FilterChip(selected = groupMode == i, onClick = { groupMode = i }, label = { Text(label) })
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        groups.forEach { (name, list) ->
                            if (name.isNotEmpty()) {
                                item(key = "group_$name") {
                                    Text(
                                        "$name  (${list.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                                    )
                                }
                            }
                            items(list, key = { it.id }) { s ->
                                SupplierRow(s, data.products.count { it.supplierId == s.id }) { onOpenSupplier(s.id) }
                            }
                        }
                    }
                }
            } else {
                if (products.isEmpty()) {
                    Empty(if (data.products.isEmpty()) "No products yet.\nOpen a supplier and tap “Add product”." else "Nothing matches your search.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(products, key = { it.id }) { p ->
                            ProductTile(p, suppliersById[p.supplierId]) { onOpenProduct(p) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SupplierRow(s: Supplier, productCount: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor()),
        border = BorderStroke(0.5.dp, cardBorderColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val card = s.cardImages.firstOrNull()
            if (card != null) {
                Thumb(card, Modifier.size(width = 84.dp, height = 54.dp))
            } else {
                Box(Modifier.size(width = 84.dp, height = 54.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val who = listOf(s.contactName, s.title).filter { it.isNotBlank() }.joinToString(" · ")
                if (who.isNotBlank()) Text(who, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (s.booth.isNotBlank()) Tag("Booth ${s.booth}", TagOrangeBg, TagOrangeText)
                    val city = CityDetector.cityOf(s)
                    if (city.isNotBlank()) Tag(city, TagBlueBg, TagBlueText)
                    Tag("$productCount product" + if (productCount == 1) "" else "s", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProductTile(p: Product, s: Supplier?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = cardColor()),
        border = BorderStroke(0.5.dp, cardBorderColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            val photo = p.photos.firstOrNull()
            if (photo != null) {
                Thumb(photo, Modifier.fillMaxWidth().aspectRatio(1f))
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Inventory, contentDescription = null)
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(p.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.priceLabel.isNotBlank()) Text(p.priceLabel, color = OceanOrange, fontWeight = FontWeight.Bold)
                Text(
                    s?.displayName ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
