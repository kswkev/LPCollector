package com.lpcollector.data.discogs

import com.lpcollector.data.model.Label
import com.lpcollector.data.model.Record
import com.lpcollector.data.model.Track

private val artistNumberSuffix = Regex("""\s\(\d+\)$""")

/** Discogs disambiguates same-named artists as "Name (2)"; drop that for display. */
fun cleanArtistName(name: String) = name.replace(artistNumberSuffix, "").trim()

fun ReleaseDto.artistDisplay(): String {
    if (artists.isEmpty()) return artistsSort?.let(::cleanArtistName).orEmpty()
    return buildString {
        artists.forEachIndexed { i, a ->
            append(cleanArtistName(a.anv.ifBlank { a.name }))
            if (i < artists.lastIndex) {
                val join = a.join.trim().ifEmpty { "," }
                append(if (join == ",") ", " else " $join ")
            }
        }
    }
}

fun FormatDto.display(): String = buildList {
    add(if (qty.isNotBlank() && qty != "1") "$qty × $name" else name)
    addAll(descriptions)
    text?.takeIf { it.isNotBlank() }?.let(::add)
}.joinToString(", ")

fun ReleaseDto.primaryImageUrl(): String =
    (images.firstOrNull { it.type == "primary" } ?: images.firstOrNull())?.uri.orEmpty()

fun ReleaseDto.toRecord(): Record = Record(
    discogsId = id,
    title = title,
    artist = artistDisplay(),
    year = year?.takeIf { it > 0 },
    country = country.orEmpty(),
    labels = labels.map { Label(cleanArtistName(it.name), it.catno.takeIf { c -> c != "none" }.orEmpty()) }
        .distinct(),
    formats = formats.map { it.display() },
    genres = genres,
    styles = styles,
    barcode = identifiers.firstOrNull { it.type.equals("Barcode", ignoreCase = true) }?.value.orEmpty(),
    tracklist = tracklist.filter { it.type == "track" }.map { Track(it.position, it.title, it.duration) },
    coverUrl = primaryImageUrl(),
    discogsUrl = uri ?: "https://www.discogs.com/release/$id",
)
