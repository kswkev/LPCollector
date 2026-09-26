package com.lpcollector.data.images

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Keeps downloaded cover images under `filesDir/covers/`. Paths stored in records are relative to filesDir. */
class CoverStore(private val filesDir: File, private val http: OkHttpClient) {

    fun file(relativePath: String): File? =
        relativePath.takeIf { it.isNotEmpty() }?.let { File(filesDir, it) }?.takeIf { it.exists() }

    /** Downloads [url] for release [discogsId]; returns the relative path, or "" if it failed. */
    suspend fun download(discogsId: Long, url: String): String = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext ""
        val relative = "covers/$discogsId.jpg"
        val target = File(filesDir, relative)
        val temp = File(target.parentFile, "$discogsId.jpg.part")
        try {
            target.parentFile?.mkdirs()
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext ""
                temp.outputStream().use { response.body.byteStream().copyTo(it) }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            relative
        } catch (e: Exception) {
            temp.delete()
            ""
        }
    }

    suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        if (relativePath.isNotEmpty()) File(filesDir, relativePath).delete()
    }
}
