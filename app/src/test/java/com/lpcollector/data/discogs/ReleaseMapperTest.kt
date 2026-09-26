package com.lpcollector.data.discogs

import com.lpcollector.data.model.Label
import com.lpcollector.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseMapperTest {

    private val releaseJson = """
        {
          "id": 249504,
          "title": "Rumours",
          "artists": [{"name": "Fleetwood Mac", "anv": "", "join": "", "id": 1}],
          "year": 1977,
          "country": "US",
          "labels": [{"name": "Warner Bros. Records", "catno": "BSK 3010", "id": 2}],
          "formats": [{"name": "Vinyl", "qty": "1", "descriptions": ["LP", "Album"]}],
          "genres": ["Rock"],
          "styles": ["Pop Rock"],
          "identifiers": [{"type": "Matrix / Runout", "value": "X"}, {"type": "Barcode", "value": "075992731310"}],
          "tracklist": [
            {"position": "", "title": "Side A", "duration": "", "type_": "heading"},
            {"position": "A1", "title": "Second Hand News", "duration": "2:43", "type_": "track"}
          ],
          "images": [{"type": "secondary", "uri": "s.jpg"}, {"type": "primary", "uri": "p.jpg"}],
          "uri": "https://www.discogs.com/release/249504-Fleetwood-Mac-Rumours",
          "community": {"have": 1000},
          "lowest_price": null
        }
    """.trimIndent()

    @Test
    fun mapsReleaseToRecord() {
        val record = DiscogsClient.json.decodeFromString<ReleaseDto>(releaseJson).toRecord()
        assertEquals("Rumours", record.title)
        assertEquals("Fleetwood Mac", record.artist)
        assertEquals(1977, record.year)
        assertEquals(listOf(Label("Warner Bros. Records", "BSK 3010")), record.labels)
        assertEquals(listOf("Vinyl, LP, Album"), record.formats)
        assertEquals("075992731310", record.barcode)
        assertEquals(listOf(Track("A1", "Second Hand News", "2:43")), record.tracklist)
        assertEquals("p.jpg", record.coverUrl)
        assertEquals("https://www.discogs.com/release/249504-Fleetwood-Mac-Rumours", record.discogsUrl)
    }

    @Test
    fun joinsMultipleArtistsAndStripsDisambiguation() {
        val dto = ReleaseDto(
            id = 1,
            artists = listOf(ArtistDto("Nirvana (2)", join = "&"), ArtistDto("Foo (12)", anv = "Foo Band")),
        )
        assertEquals("Nirvana & Foo Band", dto.artistDisplay())
    }

    @Test
    fun parsesSearchResponse() {
        val json = """
            {"pagination": {"page": 1, "pages": 3, "items": 120, "per_page": 50, "urls": {}},
             "results": [{"id": 5, "type": "release", "title": "A - B", "year": "1999",
                          "format": ["Vinyl", "LP"], "label": ["X"], "thumb": "", "barcode": ["1"]}]}
        """.trimIndent()
        val response = DiscogsClient.json.decodeFromString<SearchResponse>(json)
        assertEquals(3, response.pagination.pages)
        assertEquals("A - B", response.results.single().title)
    }
}
