package com.lpcollector.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lpcollector.data.RecordRepository
import com.lpcollector.data.discogs.DiscogsClient
import com.lpcollector.data.discogs.toRecord
import com.lpcollector.data.model.ListType
import com.lpcollector.data.model.Record
import com.lpcollector.ui.common.Cover
import com.lpcollector.ui.common.RecordDetails
import com.lpcollector.ui.common.appViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReleaseState(
    val record: Record? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val saving: Boolean = false,
)

class ReleaseViewModel(
    private val discogs: DiscogsClient,
    private val repo: RecordRepository,
    val discogsId: Long,
) : ViewModel() {
    val state = MutableStateFlow(ReleaseState())
    val messages = Channel<String>(Channel.BUFFERED)

    val inList = repo.library.map { it.find(discogsId)?.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load() }

    fun load() {
        state.value = ReleaseState(loading = true)
        viewModelScope.launch {
            state.value = try {
                ReleaseState(record = discogs.release(discogsId).toRecord(), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReleaseState(loading = false, error = e.message ?: "Couldn't load release")
            }
        }
    }

    fun add(type: ListType) {
        if (state.value.saving) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val current = inList.value
                if (current != null) repo.move(discogsId, type) else repo.addFromDiscogs(discogsId, type)
                messages.send(if (type == ListType.COLLECTION) "Added to your collection" else "Added to your wishlist")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.send(e.message ?: "Couldn't save")
            } finally {
                state.update { it.copy(saving = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDetailScreen(discogsId: Long, onBack: () -> Unit, onOpenRecord: (Long) -> Unit) {
    val vm = appViewModel { ReleaseViewModel(it.discogs, it.records, discogsId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val inList by vm.inList.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(vm) { vm.messages.receiveAsFlow().collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discogs release") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    state.record?.discogsUrl?.let { url ->
                        IconButton(onClick = { uriHandler.openUri(url) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open on Discogs")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val record = state.record
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            record == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = vm::load) { Text("Retry") }
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                Cover(record.coverUrl, size = null)
                Spacer(Modifier.height(16.dp))
                Actions(inList, state.saving, onAdd = vm::add, onOpen = { onOpenRecord(discogsId) })
                Spacer(Modifier.height(16.dp))
                RecordDetails(record)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Actions(inList: ListType?, saving: Boolean, onAdd: (ListType) -> Unit, onOpen: () -> Unit) {
    if (saving) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text("Saving record and cover…")
        }
        return
    }
    when (inList) {
        ListType.COLLECTION -> Column {
            Text("✓ In your collection", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open in collection") }
        }
        ListType.WISHLIST -> Column {
            Text("♥ On your wishlist", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAdd(ListType.COLLECTION) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.LibraryAdd, contentDescription = null)
                    Text("  Got it")
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open in wishlist") }
            }
        }
        null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAdd(ListType.COLLECTION) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.LibraryAdd, contentDescription = null)
                Text("  Collection")
            }
            OutlinedButton(onClick = { onAdd(ListType.WISHLIST) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Favorite, contentDescription = null)
                Text("  Wishlist")
            }
        }
    }
}
