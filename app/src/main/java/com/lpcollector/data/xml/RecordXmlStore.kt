package com.lpcollector.data.xml

import com.lpcollector.data.model.Label
import com.lpcollector.data.model.Library
import com.lpcollector.data.model.Record
import com.lpcollector.data.model.Track
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream

/**
 * Converts a [Library] to and from the app's XML format:
 *
 * ```
 * <lpcollector version="1">
 *   <collection><record discogsId=".." addedAt="..">...</record></collection>
 *   <wishlist>...</wishlist>
 * </lpcollector>
 * ```
 *
 * Writing is done by hand so the output is stable and readable; reading takes an
 * [XmlPullParser] so JVM unit tests can supply a non-Android implementation.
 */
object RecordXmlStore {
    const val FORMAT_VERSION = 1

    class InvalidFileException(message: String) : Exception(message)

    // ---------------------------------------------------------------- writing

    fun write(library: Library, out: OutputStream) {
        out.write(toXml(library).toByteArray(Charsets.UTF_8))
    }

    fun toXml(library: Library): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<lpcollector version=\"$FORMAT_VERSION\">\n")
        appendList("collection", library.collection)
        appendList("wishlist", library.wishlist)
        append("</lpcollector>\n")
    }

    private fun StringBuilder.appendList(tag: String, records: List<Record>) {
        if (records.isEmpty()) {
            append("  <$tag />\n")
            return
        }
        append("  <$tag>\n")
        records.forEach { appendRecord(it) }
        append("  </$tag>\n")
    }

    private fun StringBuilder.appendRecord(r: Record) {
        append("    <record discogsId=\"${r.discogsId}\"")
        if (r.addedAt.isNotEmpty()) append(" addedAt=\"${esc(r.addedAt)}\"")
        append(">\n")
        element("title", r.title)
        element("artist", r.artist)
        r.year?.let { element("year", it.toString()) }
        element("country", r.country)
        group("labels", r.labels) { l ->
            append("        <label")
            if (l.catalogNumber.isNotEmpty()) append(" catno=\"${esc(l.catalogNumber)}\"")
            append(">${esc(l.name)}</label>\n")
        }
        group("formats", r.formats) { append("        <format>${esc(it)}</format>\n") }
        group("genres", r.genres) { append("        <genre>${esc(it)}</genre>\n") }
        group("styles", r.styles) { append("        <style>${esc(it)}</style>\n") }
        element("barcode", r.barcode)
        group("tracklist", r.tracklist) { t ->
            append("        <track position=\"${esc(t.position)}\"")
            if (t.duration.isNotEmpty()) append(" duration=\"${esc(t.duration)}\"")
            append(">${esc(t.title)}</track>\n")
        }
        element("coverUrl", r.coverUrl)
        element("coverFile", r.coverFile)
        element("discogsUrl", r.discogsUrl)
        if (r.rating > 0) element("rating", r.rating.toString())
        element("condition", r.condition)
        element("notes", r.notes)
        append("    </record>\n")
    }

    private fun StringBuilder.element(tag: String, value: String) {
        if (value.isNotEmpty()) append("      <$tag>${esc(value)}</$tag>\n")
    }

    private fun <T> StringBuilder.group(tag: String, items: List<T>, item: StringBuilder.(T) -> Unit) {
        if (items.isEmpty()) return
        append("      <$tag>\n")
        items.forEach { item(it) }
        append("      </$tag>\n")
    }

    /** Escapes markup characters and drops characters that are illegal in XML 1.0. */
    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when {
            c == '&' -> append("&amp;")
            c == '<' -> append("&lt;")
            c == '>' -> append("&gt;")
            c == '"' -> append("&quot;")
            c == '\'' -> append("&apos;")
            c == '\n' || c == '\r' || c == '\t' -> append("&#${c.code};")
            c < ' ' || c == '￾' || c == '￿' -> Unit
            else -> append(c)
        }
    }

    // ---------------------------------------------------------------- reading

    fun read(input: InputStream, parser: XmlPullParser): Library {
        parser.setInput(input, null)
        var collection = emptyList<Record>()
        var wishlist = emptyList<Record>()
        var sawRoot = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "lpcollector" -> sawRoot = true
                    "collection" -> if (sawRoot) collection = readRecords(parser)
                    "wishlist" -> if (sawRoot) wishlist = readRecords(parser)
                }
            }
            event = parser.next()
        }
        if (!sawRoot) throw InvalidFileException("Not an LP Collector file (missing <lpcollector> root)")
        return Library(collection, wishlist)
    }

    /** Reads the <record> children of the current element, leaving the parser on its end tag. */
    private fun readRecords(p: XmlPullParser): List<Record> {
        val records = mutableListOf<Record>()
        forEachChild(p) {
            if (p.name == "record") readRecord(p)?.let(records::add) else skip(p)
        }
        return records
    }

    private fun readRecord(p: XmlPullParser): Record? {
        val id = p.getAttributeValue(null, "discogsId")?.toLongOrNull()
        val addedAt = p.getAttributeValue(null, "addedAt").orEmpty()
        var r = Record(discogsId = id ?: 0, title = "", artist = "", addedAt = addedAt)
        forEachChild(p) {
            r = when (p.name) {
                "title" -> r.copy(title = p.nextText())
                "artist" -> r.copy(artist = p.nextText())
                "year" -> r.copy(year = p.nextText().trim().toIntOrNull())
                "country" -> r.copy(country = p.nextText())
                "labels" -> r.copy(labels = readItems(p, "label") {
                    val catno = p.getAttributeValue(null, "catno").orEmpty()
                    Label(p.nextText(), catno)
                })
                "formats" -> r.copy(formats = readItems(p, "format") { p.nextText() })
                "genres" -> r.copy(genres = readItems(p, "genre") { p.nextText() })
                "styles" -> r.copy(styles = readItems(p, "style") { p.nextText() })
                "barcode" -> r.copy(barcode = p.nextText())
                "tracklist" -> r.copy(tracklist = readItems(p, "track") {
                    val pos = p.getAttributeValue(null, "position").orEmpty()
                    val dur = p.getAttributeValue(null, "duration").orEmpty()
                    Track(pos, p.nextText(), dur)
                })
                "coverUrl" -> r.copy(coverUrl = p.nextText())
                "coverFile" -> r.copy(coverFile = p.nextText())
                "discogsUrl" -> r.copy(discogsUrl = p.nextText())
                "rating" -> r.copy(rating = (p.nextText().trim().toIntOrNull() ?: 0).coerceIn(0, 5))
                "condition" -> r.copy(condition = p.nextText())
                "notes" -> r.copy(notes = p.nextText())
                else -> r.also { skip(p) }
            }
        }
        return if (id == null) null else r
    }

    private fun <T> readItems(p: XmlPullParser, tag: String, read: () -> T): List<T> {
        val items = mutableListOf<T>()
        forEachChild(p) { if (p.name == tag) items += read() else skip(p) }
        return items
    }

    /** Calls [onStart] positioned on each direct child start tag of the current element. */
    private inline fun forEachChild(p: XmlPullParser, onStart: () -> Unit) {
        val depth = p.depth
        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && p.depth == depth) return
            if (event == XmlPullParser.START_TAG && p.depth == depth + 1) onStart()
        }
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }
}
