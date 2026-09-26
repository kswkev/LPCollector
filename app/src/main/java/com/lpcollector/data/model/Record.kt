package com.lpcollector.data.model

enum class ListType { COLLECTION, WISHLIST }

data class Track(
    val position: String,
    val title: String,
    val duration: String = "",
)

data class Label(
    val name: String,
    val catalogNumber: String = "",
)

/** A release saved locally, with its Discogs metadata and the user's own annotations. */
data class Record(
    val discogsId: Long,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val country: String = "",
    val labels: List<Label> = emptyList(),
    val formats: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val styles: List<String> = emptyList(),
    val barcode: String = "",
    val tracklist: List<Track> = emptyList(),
    val coverUrl: String = "",
    /** Path of the downloaded cover, relative to the app's files directory. */
    val coverFile: String = "",
    val discogsUrl: String = "",
    val addedAt: String = "",
    /** 0 = unrated, otherwise 1..5. */
    val rating: Int = 0,
    val condition: String = "",
    val notes: String = "",
)

data class Library(
    val collection: List<Record> = emptyList(),
    val wishlist: List<Record> = emptyList(),
) {
    fun list(type: ListType) = when (type) {
        ListType.COLLECTION -> collection
        ListType.WISHLIST -> wishlist
    }

    fun find(discogsId: Long): Pair<ListType, Record>? =
        collection.firstOrNull { it.discogsId == discogsId }?.let { ListType.COLLECTION to it }
            ?: wishlist.firstOrNull { it.discogsId == discogsId }?.let { ListType.WISHLIST to it }

    fun with(type: ListType, records: List<Record>) = when (type) {
        ListType.COLLECTION -> copy(collection = records)
        ListType.WISHLIST -> copy(wishlist = records)
    }
}

/** Standard Goldmine media grades used on Discogs. */
val CONDITION_GRADES = listOf("M", "NM", "VG+", "VG", "G+", "G", "F", "P")
