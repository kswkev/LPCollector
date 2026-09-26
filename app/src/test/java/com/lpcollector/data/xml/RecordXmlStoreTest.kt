package com.lpcollector.data.xml

import com.lpcollector.data.model.Label
import com.lpcollector.data.model.Library
import com.lpcollector.data.model.Record
import com.lpcollector.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class RecordXmlStoreTest {

    private fun roundTrip(library: Library): Library {
        val xml = RecordXmlStore.toXml(library)
        return RecordXmlStore.read(xml.byteInputStream(), KXmlParser())
    }

    private val full = Record(
        discogsId = 249504,
        title = "Rumours",
        artist = "Fleetwood Mac",
        year = 1977,
        country = "US",
        labels = listOf(Label("Warner Bros. Records", "BSK 3010")),
        formats = listOf("Vinyl, LP, Album"),
        genres = listOf("Rock"),
        styles = listOf("Pop Rock", "Soft Rock"),
        barcode = "075992731310",
        tracklist = listOf(Track("A1", "Second Hand News", "2:43"), Track("A2", "Dreams", "")),
        coverUrl = "https://i.discogs.com/abc.jpg?x=1&y=2",
        coverFile = "covers/249504.jpg",
        discogsUrl = "https://www.discogs.com/release/249504",
        addedAt = "2026-09-26T12:00:00Z",
        rating = 5,
        condition = "VG+",
        notes = "Bought at a fair.\nSleeve has ring wear.",
    )

    @Test
    fun roundTripsAllFields() {
        val lib = Library(collection = listOf(full), wishlist = listOf(full.copy(discogsId = 1, notes = "")))
        assertEquals(lib, roundTrip(lib))
    }

    @Test
    fun escapesSpecialCharacters() {
        val tricky = Record(
            discogsId = 7,
            title = "Rock & Roll <Live> \"Encore\" 'Special'",
            artist = "AC/DC & Friends",
            labels = listOf(Label("A&M", "SP-<1>")),
            tracklist = listOf(Track("B1", "Tab\there & \"there\"", "4:00")),
            notes = "émigré — ünïcödé ♫ 🎵",
        )
        val lib = Library(collection = listOf(tricky))
        assertEquals(lib, roundTrip(lib))
    }

    @Test
    fun handlesEmptyLibraryAndMinimalRecords() {
        assertEquals(Library(), roundTrip(Library()))
        val minimal = Library(wishlist = listOf(Record(discogsId = 42, title = "", artist = "")))
        assertEquals(minimal, roundTrip(minimal))
    }

    @Test
    fun ignoresUnknownElementsAndInvalidRecords() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <lpcollector version="2">
              <somethingNew><nested>x</nested></somethingNew>
              <collection>
                <record discogsId="5"><title>Ok</title><futureField a="b"><x/></futureField><artist>A</artist></record>
                <record><title>No id</title></record>
              </collection>
            </lpcollector>
        """.trimIndent()
        val lib = RecordXmlStore.read(xml.byteInputStream(), KXmlParser())
        assertEquals(listOf(Record(discogsId = 5, title = "Ok", artist = "A")), lib.collection)
        assertTrue(lib.wishlist.isEmpty())
    }

    @Test(expected = RecordXmlStore.InvalidFileException::class)
    fun rejectsForeignXml() {
        RecordXmlStore.read("<rss><channel/></rss>".byteInputStream(), KXmlParser())
    }
}
