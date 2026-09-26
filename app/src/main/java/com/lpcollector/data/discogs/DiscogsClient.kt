package com.lpcollector.data.discogs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class DiscogsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Minimal client for the Discogs REST API (https://www.discogs.com/developers).
 * [tokenProvider] supplies the user's personal access token for each request.
 */
class DiscogsClient(
    private val http: OkHttpClient,
    private val tokenProvider: suspend () -> String,
) {
    private val baseUrl = "https://api.discogs.com/".toHttpUrl()

    suspend fun search(query: String, vinylOnly: Boolean, page: Int = 1): SearchResponse =
        get(
            baseUrl.newBuilder()
                .addPathSegments("database/search")
                .addQueryParameter("q", query)
                .addQueryParameter("type", "release")
                .apply { if (vinylOnly) addQueryParameter("format", "Vinyl") }
                .addQueryParameter("per_page", "50")
                .addQueryParameter("page", page.toString())
                .build()
        )

    suspend fun searchBarcode(barcode: String): SearchResponse =
        get(
            baseUrl.newBuilder()
                .addPathSegments("database/search")
                .addQueryParameter("barcode", barcode)
                .addQueryParameter("type", "release")
                .addQueryParameter("per_page", "50")
                .build()
        )

    suspend fun release(id: Long): ReleaseDto =
        get(baseUrl.newBuilder().addPathSegment("releases").addPathSegment(id.toString()).build())

    private suspend inline fun <reified T> get(url: HttpUrl): T {
        val token = tokenProvider().trim()
        if (token.isEmpty()) throw DiscogsException("Add your Discogs personal access token in Settings first.")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Discogs token=$token")
            .header("Accept", "application/vnd.discogs.v2.discogs+json")
            .build()
        val body = withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> response.body.string()
                        response.code == 401 -> throw DiscogsException("Discogs rejected the token. Check it in Settings.")
                        response.code == 404 -> throw DiscogsException("Not found on Discogs.")
                        response.code == 429 -> throw DiscogsException("Too many requests to Discogs. Wait a minute and try again.")
                        else -> throw DiscogsException("Discogs returned an error (HTTP ${response.code}).")
                    }
                }
            } catch (e: IOException) {
                throw DiscogsException("Couldn't reach Discogs. Check your internet connection.", e)
            }
        }
        return try {
            json.decodeFromString<T>(body)
        } catch (e: SerializationException) {
            throw DiscogsException("Unexpected response from Discogs.", e)
        }
    }

    companion object {
        const val USER_AGENT = "LPCollector/1.0"

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    }
}
