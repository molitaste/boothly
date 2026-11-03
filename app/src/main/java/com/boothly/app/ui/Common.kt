@file:OptIn(ExperimentalMaterial3Api::class)

package com.boothly.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Shapes
import androidx.compose.foundation.border
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.boothly.app.data.Repo

// ---- Ocean theme: deep blue + warm orange ----
val OceanBlue = Color(0xFF14508C)
val OceanOrange = Color(0xFFF28C28)
val OceanBg = Color(0xFFF4F7FB)
val OceanCardBorder = Color(0xFFD6E2F0)
val OceanInk = Color(0xFF0C2A4A)
val OceanMuted = Color(0xFF5F6B7A)
val TagOrangeBg = Color(0xFFFDEBD8)
val TagOrangeText = Color(0xFF8A4A0B)
val TagBlueBg = Color(0xFFE1ECF8)
val TagBlueText = Color(0xFF14508C)

private val LightOcean = lightColorScheme(
    primary = OceanBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F7),
    onPrimaryContainer = OceanInk,
    secondary = OceanOrange,
    onSecondary = Color.White,
    secondaryContainer = TagOrangeBg,
    onSecondaryContainer = TagOrangeText,
    tertiary = OceanOrange,
    onTertiary = Color.White,
    background = OceanBg,
    onBackground = OceanInk,
    surface = OceanBg,
    onSurface = OceanInk,
    surfaceVariant = Color(0xFFE6EEF7),
    onSurfaceVariant = OceanMuted,
    outline = Color(0xFF9DB0C6),
    outlineVariant = OceanCardBorder,
)

private val DarkOcean = darkColorScheme(
    primary = Color(0xFF9CC4F0),
    onPrimary = Color(0xFF0C2A4A),
    primaryContainer = Color(0xFF1F3B5C),
    onPrimaryContainer = Color(0xFFD6E6F7),
    secondary = OceanOrange,
    onSecondary = Color(0xFF2A1500),
    secondaryContainer = Color(0xFF4A2A0A),
    onSecondaryContainer = Color(0xFFFDD9B5),
    background = Color(0xFF10161F),
    surface = Color(0xFF10161F),
    surfaceVariant = Color(0xFF1C2532),
    outlineVariant = Color(0xFF2C3747),
)

@Composable
fun isDark() = isSystemInDarkTheme()

/** Header colour: ocean blue by day, deep navy in dark mode. */
@Composable
fun headerColor(): Color = if (isDark()) Color(0xFF17283D) else OceanBlue

@Composable
fun cardColor(): Color = if (isDark()) Color(0xFF1A2330) else Color.White

@Composable
fun cardBorderColor(): Color = if (isDark()) Color(0xFF2C3747) else OceanCardBorder

@Composable
fun BoothlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark()) DarkOcean else LightOcean,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp),
        ),
        content = content,
    )
}

/** Small rounded coloured tag (booth, city…). */
@Composable
fun Tag(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun oceanTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = headerColor(),
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)

@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = actions,
        colors = oceanTopBarColors(),
    )
}

@Composable
fun Thumb(name: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val base = modifier
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    AsyncImage(
        model = Repo.photoFile(name),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
    )
}

/** Horizontal row of photos with a small remove button on each. */
@Composable
fun PhotoStrip(names: List<String>, onOpen: (String) -> Unit, onRemove: (String) -> Unit, height: Int = 110) {
    LazyRow(contentPadding = PaddingValues(vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        items(names, key = { it }) { n ->
            Box {
                Thumb(n, Modifier.size(width = (height * 1.4f).dp, height = height.dp)) { onOpen(n) }
                IconButton(
                    onClick = { onRemove(n) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(Color(0x99000000), CircleShape),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (hint != null) ({ Text(hint) }) else null,
        singleLine = singleLine,
        minLines = 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}
