package com.henry.chord

/** Compatibility facade; parsing, spelling, degrees and voicings live in pure theory modules. */
object Music {
    fun pitch(note: String): Int = Note.parse(note)?.pitch ?: -1
    fun transpose(chord: String, steps: Int, flats: Boolean = false): String = Theory.transpose(chord, steps, spelling = if (flats) Spelling.FLATS else Spelling.SHARPS)
    fun roman(chord: String, key: String): String = Theory.roman(chord, KeyContext.from(key, Canonical.mode(key))) ?: chord
    fun chords(body: String) = Regex("\\[([^]]+)]").findAll(body).map { it.groupValues[1] }.distinct().toList()
    fun piano(chord: String): Set<Int> = ChordParser.parse(chord)?.pitches ?: emptySet()
}

object SongImport {
    const val MAX_BYTES = 2 * 1024 * 1024
    fun parse(text: String, filename: String): Song {
        require(text.isNotBlank()) { "The file is empty." }
        require(!text.contains('\u0000')) { "This file is not plain text." }
        fun directive(vararg names: String): String? = names.firstNotNullOfOrNull { name -> Regex("\\{$name\\s*:\\s*([^}]+)\\}", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim() }
        return Song(title = directive("title", "t") ?: filename.substringBeforeLast('.').ifBlank { "Untitled song" }, artist = directive("artist", "subtitle", "st") ?: "Imported", key = directive("key")?.takeIf { Regex("[A-G][#b]?m?").matches(it) } ?: "C", body = text.lines().filterNot { Regex("\\{(?:title|t|artist|subtitle|st|key)\\s*:.*\\}", RegexOption.IGNORE_CASE).matches(it.trim()) }.joinToString("\n").trim())
    }
    fun export(song: Song) = "{title: ${song.title.replace("}", "")}}\n{artist: ${song.artist.replace("}", "")}}\n{key: ${song.originalKeyLabel()}}\n\n${song.body}\n"
    fun searchQuery(raw: String) = Regex("[\\p{L}\\p{N}]+").findAll(raw).joinToString(" ") { "\"${it.value}\"*" }
}
