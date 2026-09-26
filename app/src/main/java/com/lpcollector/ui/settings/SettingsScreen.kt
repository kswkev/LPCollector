package com.lpcollector.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lpcollector.data.RecordRepository
import com.lpcollector.data.settings.SettingsRepository
import com.lpcollector.ui.common.SectionTitle
import com.lpcollector.ui.common.appViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repo: RecordRepository,
    private val context: Context,
) : ViewModel() {
    val token = settings.token.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val counts = repo.library.map { it.collection.size to it.wishlist.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)
    val messages = Channel<String>(Channel.BUFFERED)
    val xmlPath: String = File(context.filesDir, RecordRepository.FILE_NAME).absolutePath

    fun saveToken(value: String) = viewModelScope.launch {
        settings.setToken(value)
        messages.send(if (value.isBlank()) "Token cleared" else "Token saved")
    }

    fun export(uri: Uri) = viewModelScope.launch {
        report("Export failed") {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open file")
            out.use { repo.exportTo(it) }
            "Collection exported"
        }
    }

    fun import(uri: Uri, replace: Boolean) = viewModelScope.launch {
        report("Import failed") {
            val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open file")
            val n = input.use { repo.importFrom(it, replace) }
            "Imported $n ${if (n == 1) "record" else "records"}" + if (replace) "" else " (merged)"
        }
    }

    private suspend fun report(failure: String, block: suspend () -> String) {
        val message = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "$failure: ${e.message ?: e.javaClass.simpleName}"
        }
        messages.send(message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val vm = appViewModel { SettingsViewModel(it.settings, it.records, context) }
    val savedToken by vm.token.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(vm) { vm.messages.receiveAsFlow().collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri ->
        uri?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            SectionTitle("Discogs access", Modifier.padding(top = 0.dp))
            Text(
                "Searching Discogs needs a free personal access token. Log in at discogs.com, open " +
                    "Settings → Developers and choose \"Generate new token\", then paste it here.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { uriHandler.openUri("https://www.discogs.com/settings/developers") }) {
                Text("Open discogs.com/settings/developers")
            }
            savedToken?.let { initial ->
                var token by rememberSaveable { mutableStateOf(initial) }
                var visible by rememberSaveable { mutableStateOf(false) }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Personal access token") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (visible) "Hide token" else "Show token",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveToken(token) },
                    enabled = token.trim() != initial,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save token") }
            }

            SectionTitle("Your data")
            Text(
                "${counts.first} ${if (counts.first == 1) "record" else "records"} in collection, " +
                    "${counts.second} on wishlist.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Stored on this device in ${vm.xmlPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("lpcollector-${LocalDate.now()}.xml") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null)
                    Text("  Export XML")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null)
                    Text("  Import XML")
                }
            }
            Text(
                "Exports contain all metadata and your notes. Cover images are re-downloaded from Discogs after an import.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import collection") },
            text = {
                Text(
                    "Merge adds the file's records and updates any you already have.\n\n" +
                        "Replace discards your current collection and wishlist and uses only the file."
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; vm.import(uri, replace = false) }) { Text("Merge") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
                    TextButton(onClick = { pendingImport = null; vm.import(uri, replace = true) }) {
                        Text("Replace", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }
}
