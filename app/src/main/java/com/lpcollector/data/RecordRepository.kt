package com.lpcollector.data

import android.util.AtomicFile
import android.util.Xml
import com.lpcollector.data.discogs.DiscogsClient
import com.lpcollector.data.discogs.toRecord
import com.lpcollector.data.images.CoverStore
import com.lpcollector.data.model.Library
import com.lpcollector.data.model.ListType
import com.lpcollector.data.model.Record
import com.lpcollector.data.xml.RecordXmlStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Owns the user's collection and wishlist. The in-memory [library] is the source of truth
 * for the UI; every change is persisted to `filesDir/collection.xml`.
 *
 * A release lives in at most one list: adding a wishlisted release to the collection moves it.
 */
class RecordRepository(
    filesDir: File,
    private val discogs: DiscogsClient,
    private val covers: CoverStore,
    private val scope: CoroutineScope,
) {
    private val file = AtomicFile(File(filesDir, FILE_NAME))
    private val mutex = Mutex()
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                _library.value = withContext(Dispatchers.IO) {
                    if (file.baseFile.exists()) {
                        runCatching { file.openRead().use { RecordXmlStore.read(it, Xml.newPullParser()) } }
                            .getOrDefault(Library())
                    } else Library()
                }
                _loaded.value = true
            }
        }
    }

    /** Fetches full release details from Discogs, caches the cover and saves it to [type]. */
    suspend fun addFromDiscogs(discogsId: Long, type: ListType): Record {
        val fetched = discogs.release(discogsId).toRecord()
        val existing = _library.value.find(discogsId)?.second
        val coverFile = existing?.coverFile?.takeIf { covers.file(it) != null }
            ?: covers.download(discogsId, fetched.coverUrl)
        val record = fetched.copy(
            coverFile = coverFile,
            addedAt = now(),
            rating = existing?.rating ?: 0,
            condition = existing?.condition.orEmpty(),
            notes = existing?.notes.orEmpty(),
        )
        mutate { lib -> lib.without(discogsId).let { it.with(type, it.list(type) + record) } }
        return record
    }

    suspend fun update(record: Record) = mutate { lib ->
        val (type, _) = lib.find(record.discogsId) ?: return@mutate lib
        lib.with(type, lib.list(type).map { if (it.discogsId == record.discogsId) record else it })
    }

    suspend fun move(discogsId: Long, to: ListType) = mutate { lib ->
        val (from, record) = lib.find(discogsId) ?: return@mutate lib
        if (from == to) return@mutate lib
        lib.without(discogsId).let { it.with(to, it.list(to) + record.copy(addedAt = now())) }
    }

    suspend fun remove(discogsId: Long) {
        val record = _library.value.find(discogsId)?.second ?: return
        mutate { it.without(discogsId) }
        covers.delete(record.coverFile)
    }

    suspend fun exportTo(out: OutputStream) = withContext(Dispatchers.IO) {
        RecordXmlStore.write(_library.value, out)
    }

    /** Parses [input] fully before changing anything; throws if it isn't a valid file. Returns records imported. */
    suspend fun importFrom(input: InputStream, replace: Boolean): Int {
        val imported = withContext(Dispatchers.IO) { RecordXmlStore.read(input, Xml.newPullParser()) }
        mutate { current ->
            if (replace) imported
            else {
                val ids = (imported.collection + imported.wishlist).map { it.discogsId }.toSet()
                val kept = Library(
                    current.collection.filter { it.discogsId !in ids },
                    current.wishlist.filter { it.discogsId !in ids },
                )
                Library(kept.collection + imported.collection, kept.wishlist + imported.wishlist)
            }
        }
        scope.launch { restoreMissingCovers() }
        return imported.collection.size + imported.wishlist.size
    }

    /** Re-downloads covers that are referenced but not on this device (e.g. after an import). */
    private suspend fun restoreMissingCovers() {
        val missing = (_library.value.collection + _library.value.wishlist)
            .filter { it.coverUrl.isNotEmpty() && covers.file(it.coverFile) == null }
        for (r in missing) {
            val path = covers.download(r.discogsId, r.coverUrl)
            if (path.isNotEmpty()) {
                mutate { lib ->
                    val (type, current) = lib.find(r.discogsId) ?: return@mutate lib
                    lib.with(type, lib.list(type).map { if (it === current) it.copy(coverFile = path) else it })
                }
            }
        }
    }

    private suspend fun mutate(change: (Library) -> Library) {
        loaded.first { it }
        mutateLoaded(change)
    }

    private suspend fun mutateLoaded(change: (Library) -> Library) = mutex.withLock {
        val updated = change(_library.value)
        if (updated == _library.value) return@withLock
        withContext(Dispatchers.IO) {
            val out = file.startWrite()
            try {
                RecordXmlStore.write(updated, out)
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        }
        _library.value = updated
    }

    private fun Library.without(discogsId: Long) = Library(
        collection.filter { it.discogsId != discogsId },
        wishlist.filter { it.discogsId != discogsId },
    )

    private fun now() = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

    companion object {
        const val FILE_NAME = "collection.xml"
    }
}
