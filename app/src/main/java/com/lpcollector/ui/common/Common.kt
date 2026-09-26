package com.lpcollector.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.SubcomposeAsyncImage
import com.lpcollector.AppContainer
import com.lpcollector.LPCollectorApp
import com.lpcollector.data.model.Record
import com.lpcollector.data.model.Track
import java.io.File

/** Creates a ViewModel scoped to the current navigation entry, built from the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as LPCollectorApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Square album art; [model] can be a URL, a File, or null for a placeholder. */
@Composable
fun Cover(model: Any?, size: Dp?, modifier: Modifier = Modifier) {
    val base = (if (size != null) modifier.size(size) else modifier.fillMaxWidth())
        .clip(RoundedCornerShape(if (size != null && size < 100.dp) 6.dp else 12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Album, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.5f),
            )
        }
    }
    Box(if (size == null) base.aspectRatio(1f) else base) {
        if (model == null || (model is String && model.isBlank())) placeholder()
        else SubcomposeAsyncImage(
            model = model,
            contentDescription = "Cover",
            contentScale = ContentScale.Crop,
            loading = { placeholder() },
            error = { placeholder() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The locally cached cover if present, else the Discogs URL. */
@Composable
fun coverModel(record: Record): Any? {
    val filesDir = LocalContext.current.filesDir
    return remember(record.coverFile, record.coverUrl) {
        record.coverFile.takeIf { it.isNotEmpty() }?.let { File(filesDir, it) }?.takeIf { it.exists() }
            ?: record.coverUrl
    }
}

private val StarGold = Color(0xFFFFB300)

@Composable
fun RatingBar(rating: Int, onChange: ((Int) -> Unit)?, starSize: Dp = 32.dp) {
    Row {
        for (i in 1..5) {
            val filled = i <= rating
            Icon(
                if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (onChange != null) "Rate $i" else null,
                tint = if (filled) StarGold else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(starSize)
                    .then(
                        if (onChange != null) Modifier.clickable { onChange(if (rating == i) 0 else i) }
                        else Modifier
                    ),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(items: List<String>) {
    if (items.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
    }
}

@Composable
fun Tracklist(tracks: List<Track>) {
    Column {
        tracks.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.position, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp),
                )
                Text(t.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    t.duration, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Metadata block shared by the Discogs preview and the saved-record screens. */
@Composable
fun RecordDetails(record: Record) {
    Text(record.title, style = MaterialTheme.typography.headlineSmall)
    Text(record.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    SectionTitle("Details")
    InfoRow("Year", record.year?.toString().orEmpty())
    InfoRow("Country", record.country)
    InfoRow("Label", record.labels.joinToString("\n") { l ->
        if (l.catalogNumber.isNotEmpty()) "${l.name} – ${l.catalogNumber}" else l.name
    })
    InfoRow("Format", record.formats.joinToString("\n"))
    InfoRow("Barcode", record.barcode)
    if (record.genres.isNotEmpty() || record.styles.isNotEmpty()) {
        SectionTitle("Genre & style")
        ChipRow(record.genres + record.styles)
    }
    if (record.tracklist.isNotEmpty()) {
        SectionTitle("Tracklist")
        Tracklist(record.tracklist)
    }
}
