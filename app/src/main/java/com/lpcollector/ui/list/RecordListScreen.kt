package com.lpcollector.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lpcollector.data.RecordRepository
import com.lpcollector.data.model.ListType
import com.lpcollector.data.model.Record
import com.lpcollector.ui.common.Cover
import com.lpcollector.ui.common.RatingBar
import com.lpcollector.ui.common.appViewModel
import com.lpcollector.ui.common.coverModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    ARTIST("Artist"), TITLE("Title"), YEAR("Year"), RECENT("Recently added")
}

class RecordListViewModel(private val repo: RecordRepository, val type: ListType) : ViewModel() {
    val filter = MutableStateFlow("")
    val sort = MutableStateFlow(SortOrder.ARTIST)

    val total = repo.library.map { it.list(type).size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val records = combine(repo.library, filter, sort) { lib, f, s ->
        val q = f.trim()
        lib.list(type)
            .filter {
                q.isEmpty() || it.title.contains(q, true) || it.artist.contains(q, true) ||
                    it.labels.any { l -> l.name.contains(q, true) } || it.year?.toString() == q
            }
            .let { list ->
                when (s) {
                    SortOrder.ARTIST -> list.sortedWith(
                        compareBy(String.CASE_INSENSITIVE_ORDER, Record::artist).thenBy { it.year ?: 0 }
                    )
                    SortOrder.TITLE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Record::title))
                    SortOrder.YEAR -> list.sortedBy { it.year ?: Int.MAX_VALUE }
                    SortOrder.RECENT -> list.sortedByDescending { it.addedAt }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val loaded = repo.loaded

    fun moveToCollection(id: Long) = viewModelScope.launch { repo.move(id, ListType.COLLECTION) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    type: ListType,
    onOpenRecord: (Long) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm = appViewModel(key = type.name) { RecordListViewModel(it.records, type) }
    val records by vm.records.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val title = if (type == ListType.COLLECTION) "Collection" else "Wishlist"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (total > 0) "$title ($total)" else title) },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    leadingIcon = { RadioButton(selected = order == sort, onClick = null) },
                                    onClick = { vm.sort.value = order; sortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (total > 0) {
                var text by rememberSaveable { mutableStateOf(filter) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; vm.filter.value = it },
                    placeholder = { Text("Filter by artist, title, label, year") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (text.isNotEmpty()) IconButton(onClick = { text = ""; vm.filter.value = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear filter")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                !loaded -> Unit
                total == 0 -> EmptyState(type, onSearch)
                records.isEmpty() -> Text(
                    "No records match \"$filter\".",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(records, key = { it.discogsId }) { record ->
                        RecordRow(
                            record = record,
                            onClick = { onOpenRecord(record.discogsId) },
                            trailing = if (type == ListType.WISHLIST) {
                                {
                                    IconButton(onClick = {
                                        vm.moveToCollection(record.discogsId)
                                        scope.launch { snackbar.showSnackbar("Moved \"${record.title}\" to your collection") }
                                    }) {
                                        Icon(Icons.Filled.LibraryAdd, contentDescription = "Got it – move to collection")
                                    }
                                }
                            } else null,
                        )
                        HorizontalDivider(Modifier.padding(start = 88.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: Record, onClick: () -> Unit, trailing: (@Composable () -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Cover(coverModel(record), size = 56.dp)
        Column(Modifier.weight(1f)) {
            Text(record.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                record.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val sub = listOfNotNull(record.year?.toString(), record.labels.firstOrNull()?.name, record.condition.ifBlank { null })
            if (sub.isNotEmpty()) Text(
                sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (record.rating > 0) RatingBar(record.rating, onChange = null, starSize = 14.dp)
        }
        trailing?.invoke()
    }
}

@Composable
private fun EmptyState(type: ListType, onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Album, contentDescription = null,
            modifier = Modifier.height(72.dp).fillMaxWidth(), tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (type == ListType.COLLECTION) "Your collection is empty" else "Your wishlist is empty",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Search Discogs by name or scan a barcode to add records.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSearch) { Text("Search Discogs") }
    }
}
