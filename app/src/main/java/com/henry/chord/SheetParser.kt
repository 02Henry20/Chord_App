package com.henry.chord

/** Parsers preserve segment text verbatim. Only CRLF/CR are normalized to logical LF line breaks. */
object SheetParser {
    private val directive = Regex("^\\{([^}:]+)(?::(.*))?\\}$")
    private val marker = Regex("\\[([^]\\r\\n]+)\\]")
    private val aliases = mapOf("soc" to "chorus", "sov" to "verse", "sob" to "bridge", "soi" to "intro")
    fun parse(text: String, filename: String, previous: SongDocument? = null): ParsedDocument {
        require(text.isNotEmpty() && text.isNotBlank()) { "Empty song file." }
        require(!text.contains('\u0000')) { "Binary data is not a song." }
        val source = text.replace("\r\n", "\n").replace('\r', '\n')
        val warnings = mutableListOf<String>()
        val metadata = linkedMapOf<String, String>()
        val rawSections = mutableListOf<Triple<String, String, MutableList<String>>>()
        var current = Triple("custom", "", mutableListOf<String>())
        rawSections.add(current)
        source.split('\n').forEach { line ->
            val m = directive.matchEntire(line.trim())
            if (m == null) current.third.add(line) else {
                val name = m.groupValues[1].trim().lowercase()
                val value = m.groupValues[2].trim()
                val type = aliases[name] ?: name.removePrefix("start_of_").replace("pre_chorus", "prechorus").replace("post_chorus", "postchorus")
                when {
                    name in setOf("title", "t", "artist", "subtitle", "st", "key", "album", "year", "language", "genre", "tempo", "time", "source", "source_id", "license", "copyright") -> metadata[name] = value
                    (name.startsWith("start_of_") || name in aliases) && type in Canonical.sectionTypes -> {
                        current = Triple(type, value.ifBlank { type.replaceFirstChar { it.uppercase() } }, mutableListOf()); rawSections.add(current)
                    }
                    name.startsWith("end_of_") || name in setOf("eoc", "eov", "eob", "eoi") -> { current = Triple("custom", "", mutableListOf()); rawSections.add(current) }
                    name in setOf("comment", "c") -> {
                        val guess = Canonical.sectionTypes.firstOrNull { it != "custom" && value.lowercase().replace("-", "").replace(" ", "").startsWith(it) }
                        if (guess != null) { current = Triple(guess, value, mutableListOf()); rawSections.add(current) }
                        else current.third.add(line)
                    }
                    else -> { current.third.add(line); warnings.add("Directive {$name} preserved as an annotation.") }
                }
            }
        }
        val songId = previous?.songId ?: Canonical.uuid()
        val oldArrangement = previous?.arrangements?.singleOrNull()
        val arrangementId = oldArrangement?.id ?: Canonical.uuid()
        val usedLines = mutableSetOf<String>()
        val usedSections = mutableSetOf<String>()
        val sections = rawSections.filter { it.third.isNotEmpty() || it.first != "custom" }.mapIndexed { si, raw ->
            val available = oldArrangement?.sections?.filter { it.id !in usedSections }.orEmpty()
            val oldSection = available.firstOrNull { it.type == raw.first && it.label == raw.second }
                ?: available.maxByOrNull { old -> old.lines.count { it.inline() in raw.third } }?.takeIf { old -> old.lines.any { it.inline() in raw.third } }
            val sid = oldSection?.id ?: if (oldArrangement != null) Canonical.uuid() else Canonical.stable("$arrangementId/section/$si")
            usedSections.add(sid)
            val lines = raw.third.mapIndexed { li, line ->
                val exact = oldArrangement?.sections?.flatMap { it.lines }?.firstOrNull { it.id !in usedLines && it.inline() == line }
                val old = exact ?: oldSection?.lines?.getOrNull(li)?.takeIf { it.id !in usedLines && it.inline() !in raw.third.drop(li + 1) }
                val lid = old?.id ?: Canonical.uuid()
                usedLines.add(lid)
                if (directive.matches(line.trim())) SheetLine(lid, emptyList(), line)
                else inline(line, lid, old, warnings)
            }
            SheetSection(sid, raw.first, raw.second, lines)
        }
        val rawKey = metadata["key"]
        if (rawKey != null && !Canonical.keyValid(rawKey)) warnings.add("Unknown original key '$rawKey' retained in comments; key is unset.")
        val key = rawKey?.takeIf(Canonical::keyValid) ?: oldArrangement?.originalKey
        val tempo = metadata["tempo"]?.toDoubleOrNull()?.takeIf { it in 10.0..500.0 } ?: oldArrangement?.tempoBpm
        val a = ArrangementDoc(arrangementId, oldArrangement?.name ?: "Main", key, Canonical.mode(key), tempo, metadata["time"] ?: oldArrangement?.timeSignature,
            metadata["source_id"] ?: oldArrangement?.sourceId, oldArrangement?.sourceFormat ?: filename.substringAfterLast('.').lowercase(), metadata["source"] ?: oldArrangement?.provenance,
            metadata["license"] ?: metadata["copyright"] ?: oldArrangement?.licenseNotes,
            listOfNotNull(oldArrangement?.comments, rawKey?.takeIf { !Canonical.keyValid(it) }?.let { "Original key directive: $it" }).distinct().joinToString("\n").ifEmpty { null }, sections)
        return ParsedDocument(SongDocument(songId = songId, title = metadata["title"] ?: metadata["t"] ?: previous?.title ?: filename.substringBeforeLast('.'), artist = metadata["artist"] ?: metadata["subtitle"] ?: metadata["st"] ?: previous?.artist ?: "Imported",
            album = metadata["album"] ?: previous?.album, year = metadata["year"]?.toIntOrNull() ?: previous?.year, language = metadata["language"] ?: previous?.language, genre = metadata["genre"] ?: previous?.genre,
            tags = previous?.tags ?: emptyList(), artwork = previous?.artwork, links = previous?.links ?: MusicLinks(), arrangements = listOf(a)), warnings.distinct())
    }

    private fun inline(line: String, id: String, old: SheetLine?, warnings: MutableList<String>): SheetLine {
        val matches = marker.findAll(line).toList()
        val parts = mutableListOf<TextSegment>()
        fun segment(text: String, chord: String?) {
            val index = parts.size
            val before = old?.segments?.getOrNull(index)
            val tid = before?.id ?: Canonical.uuid()
            if (chord != null && !Canonical.chordValid(chord)) warnings.add("Unsupported chord '$chord' preserved verbatim.")
            parts.add(TextSegment(tid, text, chord?.let { ChordAnchor(before?.anchor?.takeIf { it.rawChord == chord }?.id ?: Canonical.uuid(), it, before?.anchor?.beat, before?.anchor?.measure, before?.anchor?.annotations) }))
        }
        if (matches.isEmpty()) segment(line, null) else {
            if (matches.first().range.first > 0) segment(line.substring(0, matches.first().range.first), null)
            matches.forEachIndexed { i, m -> segment(line.substring(m.range.last + 1, matches.getOrNull(i + 1)?.range?.first ?: line.length), m.groupValues[1]) }
        }
        return SheetLine(id, parts)
    }

    /** Space alignment is only a proposed conversion; the UI requires an explicit review before commit. */
    fun plainText(text: String, filename: String): ParsedDocument {
        if (marker.containsMatchIn(text) || text.lines().any { directive.matches(it.trim()) }) return parse(text, filename)
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val output = mutableListOf<String>(); val warnings = mutableListOf<String>(); var i = 0; var proposed = false
        while (i < lines.size) {
            val chordTokens = Regex("\\S+").findAll(lines[i]).toList()
            if (chordTokens.isNotEmpty() && chordTokens.all { Canonical.chordValid(it.value) } && lines[i].any { it in 'A'..'G' }) {
                if (i + 1 < lines.size && lines[i + 1].isNotBlank() && !Regex("\\S+").findAll(lines[i + 1]).all { Canonical.chordValid(it.value) }) {
                    val lyric = lines[i + 1]
                    if (lines[i].contains('\t') || lyric.contains('\t') || lyric.any { it.code > 127 } || chordTokens.any { it.range.first > lyric.length }) {
                        warnings.add("Line ${i + 1}: ambiguous tab/Unicode/overrun alignment; original rows retained without guessed anchors.")
                        output.add(lines[i]); output.add(lyric)
                    } else {
                        val converted = StringBuilder(lyric)
                        chordTokens.reversed().forEach { converted.insert(it.range.first, "[${it.value}]") }
                        output.add(converted.toString())
                        warnings.add("Line ${i + 1}: proposed chord-above-lyric positions. Review every chord before accepting.")
                    }
                    proposed = true; i += 2; continue
                } else { output.add(chordTokens.joinToString("") { "[${it.value}]" }); proposed = true; warnings.add("Line ${i + 1}: chord-only row recognized; review before accepting.") }
            } else output.add(lines[i])
            i++
        }
        val parsed = parse(output.joinToString("\n"), filename)
        return parsed.copy(warnings = parsed.warnings + warnings, needsReview = proposed, rawText = text)
    }
}
