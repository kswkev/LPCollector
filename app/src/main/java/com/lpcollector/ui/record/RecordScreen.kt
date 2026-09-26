package com.lpcollector.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lpcollector.data.RecordRepository
import com.lpcollector.data.model.CONDITION_GRADES
import com.lpcollector.data.model.ListType
import com.lpcollector.data.model.Record
import com.lpcollector.ui.common.Cover
import com.lpcollector.ui.common.RatingBar
import com.lpcollector.ui.common.RecordDetails
import com.lpcollector.ui.common.SectionTitle
import com.lpcollector.ui.common.appViewModel
import com.lpcollector.ui.common.coverModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordViewModel(private val repo: RecordRepository, discogsId: Long) : ViewModel() {
    val entry = repo.library.map { it.find(discogsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repo.library.value.find(discogsId))

    fun update(record: Record) = viewModelScope.launch { repo.update(record) }
    fun move(id: Long, to: ListType) = viewModelScope.launch { repo.move(id, to) }
    fun remove(id: Long, then: () -> Unit) = viewModelScope.launch { repo.remove(id); then() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordScreen(discogsId: Long, onBack: () -> Unit) {
    val vm = appViewModel { RecordViewModel(it.records, discogsId) }
    val entry by vm.entry.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // Record disappeared (e.g. replaced by an import) – nothing to show.
    LaunchedEffect(entry) { if (entry == null && !deleting) onBack() }
    val (type, record) = entry ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (type == ListType.COLLECTION) "In collection" else "On wishlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (record.discogsUrl.isNotEmpty()) IconButton(onClick = { uriHandler.openUri(record.discogsUrl) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open on Discogs")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (type == ListType.COLLECTION) DropdownMenuItem(
                                text = { Text("Move to wishlist") },
                                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                                onClick = { menu = false; vm.move(record.discogsId, ListType.WISHLIST) },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Cover(coverModel(record), size = null)
            Spacer(Modifier.height(16.dp))

            if (type == ListType.WISHLIST) {
                Button(onClick = { vm.move(record.discogsId, ListType.COLLECTION) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.LibraryAdd, contentDescription = null)
                    Text("  Got it – move to collection")
                }
                Spacer(Modifier.height(16.dp))
            }

            RecordDetails(record)

            SectionTitle("My notes")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rating", modifier = Modifier.padding(end = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                RatingBar(record.rating, onChange = { vm.update(record.copy(rating = it)) })
            }
            Spacer(Modifier.height(12.dp))
            Text("Condition", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CONDITION_GRADES.forEach { grade ->
                    FilterChip(
                        selected = record.condition == grade,
                        onClick = { vm.update(record.copy(condition = if (record.condition == grade) "" else grade)) },
                        label = { Text(grade) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            var notes by rememberSaveable(record.discogsId) { mutableStateOf(record.notes) }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                placeholder = { Text("Where you bought it, pressing details, …") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (notes != record.notes) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { notes = record.notes }) { Text("Discard") }
                    Button(onClick = { vm.update(record.copy(notes = notes)) }) { Text("Save notes") }
                }
            }
            if (record.addedAt.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Added ${record.addedAt.replace('T', ' ').removeSuffix("Z")} UTC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove record?") },
        text = { Text("\"${record.title}\" and its notes will be removed from your ${if (type == ListType.COLLECTION) "collection" else "wishlist"}.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                deleting = true
                vm.remove(record.discogsId, onBack)
            }) { Text("Remove") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
