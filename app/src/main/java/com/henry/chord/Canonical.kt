package com.henry.chord

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/** A chord applies immediately BEFORE this segment's exact text, even when that text is empty. */
data class ChordAnchor(val id: String, val rawChord: String, val beat: Double? = null, val measure: Int? = null, val annotations: String? = null)
data class TextSegment(val id: String, val text: String, val anchor: ChordAnchor? = null)
data class SheetLine(val id: String, val segments: List<TextSegment>, val annotation: String? = null) {
    val lyric: String get() = segments.joinToString("") { it.text }
    fun inline(): String = annotation ?: segments.joinToString("") { (it.anchor?.let { a -> "[${a.rawChord}]" } ?: "") + it.text }
}
data class SheetSection(val id: String, val type: String, val label: String, val lines: List<SheetLine>)
data class ArtworkRef(val path: String? = null, val sourceUrl: String? = null, val credit: String? = null)
data class MusicLinks(val spotify: String? = null, val youtube: String? = null)
data class ArrangementDoc(
    val id: String, val name: String = "Main", val originalKey: String? = null, val tonalMode: String? = null,
    val tempoBpm: Double? = null, val timeSignature: String? = null,
    val sourceId: String? = null, val sourceFormat: String? = null, val provenance: String? = null,
    val licenseNotes: String? = null, val comments: String? = null, val sections: List<SheetSection>
) {
    fun editorText(): String = sections.joinToString("\n") { section ->
        (if (section.type == "custom" && section.label.isEmpty()) "" else "{start_of_${section.type}: ${section.label}}\n") + section.lines.joinToString("\n") { it.inline() }
    }
    fun lyricText(): String = sections.joinToString("\n") { it.lines.joinToString("\n") { line -> line.lyric } }
}
data class SongDocument(
    val schemaVersion: Int = 1, val songId: String, val title: String, val artist: String,
    val album: String? = null, val year: Int? = null, val language: String? = null, val genre: String? = null,
    val tags: List<String> = emptyList(), val artwork: ArtworkRef? = null, val links: MusicLinks = MusicLinks(),
    val arrangements: List<ArrangementDoc>
) {
    fun single(arrangement: ArrangementDoc) = copy(arrangements = listOf(arrangement))
}
data class ParsedDocument(val document: SongDocument, val warnings: List<String> = emptyList(), val needsReview: Boolean = false, val rawText: String? = null)

object Canonical {
    val sectionTypes = setOf("intro", "verse", "prechorus", "chorus", "postchorus", "bridge", "instrumental", "solo", "outro", "custom")
    fun uuid() = UUID.randomUUID().toString()
    fun stable(seed: String) = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
    fun identity(value: String) = runCatching { UUID.fromString(value).toString() }.getOrElse { stable(value) }
    fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    fun keyValid(key: String) = Regex("[A-G](?:#|b)?m?").matches(key)
    fun mode(key: String?): String? = key?.let { if (it.endsWith("m")) "minor" else "major" }
    fun chordValid(chord: String) = Regex("[A-G](?:[#b♯♭])?(?:(?:maj|min|dim|aug|sus|add|m|M|ø|°|[0-9#b()+−-])*)(?:/[A-G](?:[#b♯♭])?)?").matches(chord)
    fun fingerprint(doc: SongDocument): String {
        val a = doc.arrangements.single()
        // IDs and import paths are deliberately excluded. Lyric whitespace and anchor boundaries are exact.
        val content = JSONObject().put("title", normalized(doc.title)).put("artist", normalized(doc.artist))
            .put("name", normalized(a.name)).put("key", a.originalKey).put("mode", a.tonalMode)
            .put("tempo", a.tempoBpm).put("timeSignature", a.timeSignature)
            .put("sections", JSONArray(a.sections.map { s -> JSONObject().put("type", s.type).put("label", s.label).put("lines", JSONArray(s.lines.map { l ->
                JSONObject().put("annotation", l.annotation).put("segments", JSONArray(l.segments.map { t -> JSONObject().put("text", t.text).put("chord", t.anchor?.rawChord).put("beat", t.anchor?.beat).put("measure", t.anchor?.measure).put("annotations", t.anchor?.annotations) }))
            })) }))
        return hash(content.toString())
    }

    fun encode(doc: SongDocument): String = json(doc).toString()
    fun json(doc: SongDocument): JSONObject = JSONObject().put("schemaVersion", doc.schemaVersion).put("songId", doc.songId)
        .put("title", doc.title).put("artist", doc.artist).put("album", doc.album).put("year", doc.year).put("language", doc.language).put("genre", doc.genre)
        .put("tags", JSONArray(doc.tags)).put("artwork", doc.artwork?.let { JSONObject().put("path", it.path).put("sourceUrl", it.sourceUrl).put("credit", it.credit) })
        .put("musicLinks", JSONObject().put("spotifyTrackUrl", doc.links.spotify).put("youtubeVideoUrl", doc.links.youtube))
        .put("arrangements", JSONArray(doc.arrangements.map { a -> JSONObject().put("arrangementId", a.id).put("name", a.name).put("originalKey", a.originalKey).put("tonalMode", a.tonalMode)
            .put("tempoBpm", a.tempoBpm).put("timeSignature", a.timeSignature).put("sourceId", a.sourceId).put("sourceFormat", a.sourceFormat).put("provenance", a.provenance).put("licenseNotes", a.licenseNotes).put("comments", a.comments)
            .put("sections", JSONArray(a.sections.map { s -> JSONObject().put("id", s.id).put("type", s.type).put("label", s.label).put("lines", JSONArray(s.lines.map { l ->
                JSONObject().put("id", l.id).put("annotation", l.annotation).put("segments", JSONArray(l.segments.map { seg -> JSONObject().put("id", seg.id).put("text", seg.text).apply {
                    seg.anchor?.let { anchor -> put("anchor", JSONObject().put("id", anchor.id).put("rawChord", anchor.rawChord).put("beat", anchor.beat).put("measure", anchor.measure).put("annotations", anchor.annotations)) }
                } }))
            })) })) }))

    /** v0 was an unversioned Chordroom draft, accepted only by an explicit migration. Future versions fail closed. */
    fun decode(text: String): List<ParsedDocument> {
        val root = JSONObject(text)
        val version = root.optInt("schemaVersion", 0)
        require(version in 0..1) { "Unsupported schemaVersion $version. This app reads versions 0 and 1." }
        val roots = if (root.optString("format") == "chordroom-collection") {
            require(version == 1) { "Collection manifests require schemaVersion 1." }
            root.getJSONArray("songs").objects()
        } else listOf(root)
        require(roots.size <= 2000) { "A manifest may contain at most 2000 songs." }
        return roots.map { obj ->
            val v = obj.optInt("schemaVersion", version)
            require(v in 0..1) { "Unsupported song schemaVersion $v" }
            val songId = identity(obj.string("songId") ?: obj.string("id") ?: error("Missing songId"))
            val title = obj.getString("title").also { require(it.isNotBlank()) { "Song title is empty" } }
            val artist = obj.string("artist") ?: "Unknown artist"
            val warnings = mutableListOf<String>()
            if (v == 0) warnings.add("Migrated draft JSON schema 0 to schema 1.")
            val arrangements = obj.optJSONArray("arrangements")?.objects() ?: listOf(obj)
            require(arrangements.isNotEmpty() && arrangements.size <= 100) { "Expected 1–100 arrangements per song." }
            val parsed = arrangements.mapIndexed { index, a ->
                val arrangementId = identity(a.string("arrangementId") ?: a.string("arrangementUuid") ?: "$songId/arrangement/$index")
                val key = a.string("originalKey") ?: a.string("key")
                require(key == null || keyValid(key)) { "Invalid originalKey: $key" }
                val mode = a.string("tonalMode") ?: mode(key)
                require(mode == null || mode in setOf("major", "minor")) { "tonalMode must be major, minor, or null." }
                require(key == null || mode == mode(key)) { "Key and tonalMode disagree." }
                val tempo = a.number("tempoBpm")
                require(tempo == null || tempo.isFinite() && tempo in 10.0..500.0) { "tempoBpm must be 10–500." }
                val time = a.string("timeSignature")
                require(time == null || Regex("(?:[1-9]|[1-2][0-9]|3[0-2])/(?:1|2|4|8|16|32)").matches(time)) { "Invalid timeSignature" }
                val sections = a.getJSONArray("sections").objects().mapIndexed { si, section ->
                    val sid = section.string("id") ?: stable("$arrangementId/section/$si")
                    val type = section.string("type") ?: "custom"
                    require(type in sectionTypes) { "Unknown section type $type; use custom." }
                    val lines = section.getJSONArray("lines").objects().mapIndexed { li, line ->
                        val lid = line.string("id") ?: stable("$sid/line/$li")
                        val segments = line.optJSONArray("segments")?.objects()?.mapIndexed { ti, seg ->
                            val tid = seg.string("id") ?: stable("$lid/segment/$ti")
                            val lyric = seg.optString("text", "")
                            require(!lyric.contains('\n') && !lyric.contains('\r')) { "Use separate lines for line breaks." }
                            val anchorJson = seg.optJSONObject("anchor")
                            val raw = anchorJson?.string("rawChord") ?: seg.string("chord")
                            val anchor = raw?.let { chord ->
                                require(chord.isNotBlank() && chord.length <= 128 && !chord.contains('\n')) { "Invalid chord annotation length" }
                                if (!chordValid(chord)) warnings.add("Unsupported chord '$chord' preserved verbatim.")
                                val beat = anchorJson?.number("beat"); val measure = anchorJson?.integer("measure")
                                require(beat == null || beat.isFinite() && beat >= 0) { "Anchor beat must be finite and non-negative." }
                                require(measure == null || measure >= 0) { "Anchor measure must be non-negative." }
                                ChordAnchor(anchorJson?.string("id") ?: stable("$tid/anchor"), chord, beat, measure, anchorJson?.string("annotations"))
                            }
                            TextSegment(tid, lyric, anchor)
                        } ?: emptyList()
                        SheetLine(lid, segments, line.string("annotation"))
                    }
                    SheetSection(sid, type, section.optString("label", type.replaceFirstChar { it.uppercase() }), lines)
                }
                val allIds = sections.flatMap { s -> listOf(s.id) + s.lines.flatMap { l -> listOf(l.id) + l.segments.flatMap { t -> listOfNotNull(t.id, t.anchor?.id) } } }
                require(allIds.none { it.isBlank() } && allIds.size == allIds.distinct().size) { "Section, line, segment and anchor IDs must be nonempty and unique within an arrangement." }
                ArrangementDoc(arrangementId, a.optString("name", "Main"), key, mode, tempo, time, a.string("sourceId"), a.string("sourceFormat") ?: "json", a.string("provenance"), a.string("licenseNotes"), a.string("comments"), sections)
            }
            require(parsed.map { it.id }.distinct().size == parsed.size) { "Arrangement IDs must be unique." }
            val art = obj.optJSONObject("artwork")?.let { ArtworkRef(it.string("path"), it.string("sourceUrl"), it.string("credit")) }
            val links = obj.optJSONObject("musicLinks") ?: obj.optJSONObject("links")
            fun safeUrl(value: String?): String? { require(value == null || value.startsWith("https://") || value.startsWith("http://")) { "Links must use http(s)." }; return value }
            val document = SongDocument(songId = songId, title = title, artist = artist, album = obj.string("album"), year = obj.integer("year"), language = obj.string("language"), genre = obj.string("genre"), tags = obj.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(), artwork = art,
                links = MusicLinks(safeUrl(links?.string("spotifyTrackUrl") ?: links?.string("spotify")), safeUrl(links?.string("youtubeVideoUrl") ?: links?.string("youtube"))), arrangements = parsed)
            ParsedDocument(document, warnings.distinct())
        }
    }
}

internal fun JSONObject.string(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
internal fun JSONObject.number(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
internal fun JSONObject.integer(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
