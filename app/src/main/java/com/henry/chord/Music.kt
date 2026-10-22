package com.henry.chord

object Music {
    private val sharp = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flat = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val root = Regex("^([A-G](?:#|b)?)(.*)$")
    fun pitch(note: String): Int = sharp.indexOf(note).takeIf { it >= 0 } ?: flat.indexOf(note)
    fun transpose(chord: String, steps: Int, flats: Boolean = false): String = chord.split('/').joinToString("/") { part ->
        val m = root.matchEntire(part)
        if (m == null) part else (if (flats) flat else sharp)[Math.floorMod(pitch(m.groupValues[1]) + steps, 12)] + m.groupValues[2]
    }
    fun roman(chord: String, key: String): String = chord.split('/').joinToString("/") { part ->
        val m = root.matchEntire(part)
        val k = root.matchEntire(key)
        if (m == null || k == null) part else {
            val degree = listOf("I", "♭II", "II", "♭III", "III", "IV", "♯IV", "V", "♭VI", "VI", "♭VII", "VII")[Math.floorMod(pitch(m.groupValues[1]) - pitch(k.groupValues[1]), 12)]
            val suffix = m.groupValues[2]
            if (suffix.startsWith("m") && !suffix.startsWith("maj")) degree.lowercase() + suffix.drop(1) else degree + suffix
        }
    }
    fun chords(body: String) = Regex("\\[([^]]+)]").findAll(body).map { it.groupValues[1] }.distinct().toList()
    fun piano(chord: String): Set<Int> {
        val m = root.matchEntire(chord.substringBefore('/')) ?: return emptySet()
        val quality = m.groupValues[2]
        val third = when { quality.startsWith("sus2") -> 2; quality.startsWith("sus") -> 5; quality.startsWith("m") && !quality.startsWith("maj") -> 3; else -> 4 }
        val fifth = when { quality.startsWith("dim") || quality.contains("b5") -> 6; quality.startsWith("aug") || quality.contains("#5") -> 8; else -> 7 }
        val intervals = mutableListOf(0, if (quality.startsWith("dim")) 3 else third, fifth)
        when { quality.contains("maj7") -> intervals.add(11); quality.contains("7") -> intervals.add(if (quality.startsWith("dim")) 9 else 10); quality.contains("6") -> intervals.add(9) }
        if (quality.contains("9")) intervals.add(2)
        if (chord.contains('/')) intervals.add(Math.floorMod(pitch(chord.substringAfter('/')) - pitch(m.groupValues[1]), 12))
        return intervals.map { Math.floorMod(pitch(m.groupValues[1]) + it, 12) }.toSet()
    }
    val guitar = mapOf("C" to listOf(-1,3,2,0,1,0), "G" to listOf(3,2,0,0,0,3), "D" to listOf(-1,-1,0,2,3,2), "A" to listOf(-1,0,2,2,2,0), "E" to listOf(0,2,2,1,0,0), "F" to listOf(1,3,3,2,1,1), "Am" to listOf(-1,0,2,2,1,0), "Em" to listOf(0,2,2,0,0,0), "Dm" to listOf(-1,-1,0,2,3,1), "Bm" to listOf(-1,2,4,4,3,2), "Bb" to listOf(-1,1,3,3,3,1), "B" to listOf(-1,2,4,4,4,2), "F#m" to listOf(2,4,4,2,2,2), "C#m" to listOf(-1,4,6,6,5,4), "D7" to listOf(-1,-1,0,2,1,2), "G7" to listOf(3,2,0,0,0,1), "C7" to listOf(-1,3,2,3,1,0), "A7" to listOf(-1,0,2,0,2,0), "E7" to listOf(0,2,0,1,0,0))
}

object SongImport {
    const val MAX_BYTES = 2 * 1024 * 1024
    fun parse(text: String, filename: String): Song {
        require(text.isNotBlank()) { "The file is empty." }
        require(!text.contains('\u0000')) { "This file is not plain text." }
        fun directive(vararg names: String): String? = names.firstNotNullOfOrNull { name -> Regex("\\{$name\\s*:\\s*([^}]+)\\}", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim() }
        return Song(title = directive("title", "t") ?: filename.substringBeforeLast('.').ifBlank { "Untitled song" }, artist = directive("artist", "subtitle", "st") ?: "Imported", key = directive("key")?.takeIf { Regex("[A-G][#b]?m?").matches(it) } ?: "C", body = text.lines().filterNot { Regex("\\{(?:title|t|artist|subtitle|st|key)\\s*:.*\\}", RegexOption.IGNORE_CASE).matches(it.trim()) }.joinToString("\n").trim())
    }
    fun export(song: Song) = "{title: ${song.title.replace("}", "")}}\n{artist: ${song.artist.replace("}", "")}}\n{key: ${song.key}}\n\n${song.body}\n"
    fun searchQuery(raw: String) = Regex("[\\p{L}\\p{N}]+").findAll(raw).joinToString(" ") { "\"${it.value}\"*" }
}
