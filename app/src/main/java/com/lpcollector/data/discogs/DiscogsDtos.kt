package com.lpcollector.data.discogs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val pagination: Pagination = Pagination(),
    val results: List<SearchResult> = emptyList(),
)

@Serializable
data class Pagination(
    val page: Int = 1,
    val pages: Int = 1,
    val items: Int = 0,
)

@Serializable
data class SearchResult(
    val id: Long,
    val type: String = "",
    /** Discogs formats this as "Artist - Title". */
    val title: String = "",
    val year: String? = null,
    val country: String? = null,
    val format: List<String> = emptyList(),
    val label: List<String> = emptyList(),
    val catno: String? = null,
    val thumb: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
)

@Serializable
data class ReleaseDto(
    val id: Long,
    val title: String = "",
    val artists: List<ArtistDto> = emptyList(),
    @SerialName("artists_sort") val artistsSort: String? = null,
    val year: Int? = null,
    val released: String? = null,
    val country: String? = null,
    val labels: List<LabelDto> = emptyList(),
    val formats: List<FormatDto> = emptyList(),
    val genres: List<String> = emptyList(),
    val styles: List<String> = emptyList(),
    val identifiers: List<IdentifierDto> = emptyList(),
    val tracklist: List<TrackDto> = emptyList(),
    val images: List<ImageDto> = emptyList(),
    val uri: String? = null,
    val notes: String? = null,
)

@Serializable
data class ArtistDto(
    val name: String = "",
    val anv: String = "",
    val join: String = "",
)

@Serializable
data class LabelDto(
    val name: String = "",
    val catno: String = "",
)

@Serializable
data class FormatDto(
    val name: String = "",
    val qty: String = "",
    val descriptions: List<String> = emptyList(),
    val text: String? = null,
)

@Serializable
data class IdentifierDto(
    val type: String = "",
    val value: String = "",
)

@Serializable
data class TrackDto(
    val position: String = "",
    val title: String = "",
    val duration: String = "",
    @SerialName("type_") val type: String = "track",
)

@Serializable
data class ImageDto(
    val type: String = "",
    val uri: String = "",
    val uri150: String = "",
)
