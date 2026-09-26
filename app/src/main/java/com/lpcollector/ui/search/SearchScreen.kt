package com.lpcollector.ui.search

import android.content.Context
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.lpcollector.data.RecordRepository
import com.lpcollector.data.discogs.DiscogsClient
import com.lpcollector.data.discogs.SearchResult
import com.lpcollector.data.model.ListType
import com.lpcollector.data.settings.SettingsRepository
import com.lpcollector.ui.common.Cover
import com.lpcollector.ui.common.appViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val lastQuery: String = "",
    val isBarcode: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val page: Int = 0,
    val pages: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canLoadMore get() = !loading && error == null && page in 1 until pages
    val searched get() = page > 0
}

class SearchViewModel(
    private val discogs: DiscogsClient,
    private val settings: SettingsRepository,
    records: RecordRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state
    private var job: Job? = null

    val hasToken = settings.token.map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val vinylOnly = settings.vinylOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Which list (if any) each Discogs release is already in. */
    val membership = records.library.map { lib ->
        lib.collection.associate { it.discogsId to ListType.COLLECTION } +
            lib.wishlist.associate { it.discogsId to ListType.WISHLIST }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        // A pasted/typed UPC or EAN is looked up as a barcode.
        val barcode = q.replace(" ", "").takeIf { it.length in 8..14 && it.all(Char::isDigit) }
        if (barcode != null) searchBarcode(barcode) else load(SearchState(lastQuery = q), 1)
    }

    fun searchBarcode(code: String) = load(SearchState(lastQuery = code, isBarcode = true), 1)

    fun setVinylOnly(value: Boolean) {
        viewModelScope.launch {
            settings.setVinylOnly(value)
            val s = _state.value
            if (s.searched && !s.isBarcode) load(SearchState(lastQuery = s.lastQuery), 1, value)
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.canLoadMore) load(s, s.page + 1)
    }

    fun retry() {
        val s = _state.value
        if (s.lastQuery.isNotEmpty()) load(s.copy(error = null), maxOf(s.page + 1, 1))
    }

    private fun load(base: SearchState, page: Int, vinyl: Boolean = vinylOnly.value) {
        job?.cancel()
        _state.value = base.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val response = if (base.isBarcode) discogs.searchBarcode(base.lastQuery)
                else discogs.search(base.lastQuery, vinyl, page)
                _state.update {
                    it.copy(
                        results = (if (page == 1) emptyList() else it.results) + response.results
                            .filter { r -> it.results.none { e -> e.id == r.id } },
                        page = page,
                        pages = if (base.isBarcode) 1 else response.pagination.pages,
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.message ?: "Search failed") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onOpenRelease: (Long) -> Unit, onSettings: () -> Unit) {
    val vm = appViewModel { SearchViewModel(it.discogs, it.settings, it.records) }
    val state by vm.state.collectAsStateWithLifecycle()
    val hasToken by vm.hasToken.collectAsStateWithLifecycle()
    val vinylOnly by vm.vinylOnly.collectAsStateWithLifecycle()
    val membership by vm.membership.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        focus.clearFocus()
        vm.search(query)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Search Discogs") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Artist, album, catalog # or barcode") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                        IconButton(onClick = {
                            scanError = null
                            scanBarcode(context, onResult = { code ->
                                query = code
                                vm.searchBarcode(code)
                            }, onError = { scanError = it })
                        }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = vinylOnly,
                    onClick = { vm.setVinylOnly(!vinylOnly) },
                    label = { Text("Vinyl only") },
                    leadingIcon = if (vinylOnly) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                )
            }
            scanError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            when {
                !hasToken -> Message(
                    "A Discogs personal access token is needed to search.",
                    "Open Settings", onSettings,
                )
                state.error != null && state.results.isEmpty() -> Message(state.error!!, "Retry", vm::retry)
                state.loading && state.results.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.searched && state.results.isEmpty() -> Message(
                    if (state.isBarcode) "No release found for barcode ${state.lastQuery}."
                    else "No results for \"${state.lastQuery}\".",
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.results, key = { _, r -> r.id }) { index, result ->
                        if (index >= state.results.size - 5) vm.loadMore()
                        ResultRow(result, membership[result.id]) { onOpenRelease(result.id) }
                        HorizontalDivider(Modifier.padding(start = 88.dp))
                    }
                    if (state.loading || state.error != null) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (state.loading) CircularProgressIndicator()
                            else Button(onClick = vm::retry) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, inList: ListType?, onClick: () -> Unit) {
    // Discogs titles are "Artist - Title".
    val artist = result.title.substringBefore(" - ", "")
    val title = result.title.substringAfter(" - ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Cover(result.thumb, size = 56.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.isNotEmpty()) Text(
                artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val sub = listOfNotNull(
                result.year?.takeIf { it.isNotBlank() && it != "0" },
                result.country,
                result.label.firstOrNull(),
                result.catno?.takeIf { it.isNotBlank() && it != "none" },
            )
            Text(
                sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.format.isNotEmpty()) Text(
                result.format.distinct().joinToString(", "), style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (inList != null) AssistChip(
            onClick = onClick,
            label = { Text(if (inList == ListType.COLLECTION) "Owned" else "Wanted") },
        )
    }
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

/** Opens Google's on-device barcode scanner UI (no camera permission needed). */
private fun scanBarcode(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onResult) }
        .addOnFailureListener { e -> onError("Barcode scanner unavailable: ${e.message}") }
}
