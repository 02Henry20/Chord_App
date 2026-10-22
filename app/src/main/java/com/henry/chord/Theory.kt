package com.henry.chord

/** Native, deterministic theory. Letters are C D E F G A B; accidentals retain diatonic spelling. */
data class Note(val letter: Int, val accidental: Int = 0) {
    val pitch: Int get() = Math.floorMod(naturals[letter] + accidental, 12)
    val name: String get() = "CDEFGAB"[letter] + when { accidental > 0 -> "#".repeat(accidental); else -> "b".repeat(-accidental) }
    companion object {
        val naturals = listOf(0, 2, 4, 5, 7, 9, 11)
        fun parse(raw: String): Note? {
            val s = raw.replace("♯", "#").replace("♭", "b").replace("𝄪", "##").replace("𝄫", "bb")
            if (!Regex("[A-G](?:#{1,6}|b{1,6}|x)?").matches(s)) return null
            return Note("CDEFGAB".indexOf(s[0]), if (s.endsWith('x')) 2 else s.drop(1).sumOf { if (it == '#') 1 else -1 })
        }
        fun atPitch(pitch: Int, flats: Boolean): Note = parse((if (flats) flatNames else sharpNames)[Math.floorMod(pitch, 12)])!!
        private val sharpNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private val flatNames = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    }
}

enum class ChordQuality { MAJOR, MINOR, DIMINISHED, AUGMENTED, SUS2, SUS4 }
data class ChordTone(val degree: Int, val semitones: Int)
data class ParsedChord(val raw: String, val root: Note?, val quality: ChordQuality, val suffix: String,
                       val tones: List<ChordTone>, val bass: Note? = null, val noChord: Boolean = false,
                       val romanSuffix: String = "") {
    val notes: List<Note> get() = root?.let { r -> tones.map { t ->
        val letter = (r.letter + t.degree - 1) % 7
        val target = (r.pitch + t.semitones) % 12
        Note(letter, Theory.signed(target - Note.naturals[letter]))
    } } ?: emptyList()
    val pitches: Set<Int> get() = (notes + listOfNotNull(bass)).map { it.pitch }.toSet()
}

object ChordParser {
    private val header = Regex("^([A-G](?:#{1,6}|b{1,6}|x)?)(.*)$")
    /** Unknown annotations return null. Callers keep raw text instead of manufacturing tones. */
    fun parse(raw: String): ParsedChord? {
        val text = raw.trim().replace("♯", "#").replace("♭", "b").replace("𝄪", "##").replace("𝄫", "bb").replace("−", "m")
        if (text.uppercase().replace(".", "") == "NC") return ParsedChord(raw, null, ChordQuality.MAJOR, "", emptyList(), noChord = true)
        val parts = text.split('/')
        if (parts.size > 2) return null
        val bass = if (parts.size == 2) Note.parse(parts[1]) ?: return null else null
        val match = header.matchEntire(parts[0]) ?: return null
        val root = Note.parse(match.groupValues[1]) ?: return null
        val originalSuffix = match.groupValues[2]
        var rest = originalSuffix
        var quality = ChordQuality.MAJOR
        var halfDim = false
        var majorSeventh = false
        when {
            rest.startsWith("mMaj") -> { quality = ChordQuality.MINOR; majorSeventh = true; rest = rest.drop(4) }
            rest.startsWith("mmaj") -> { quality = ChordQuality.MINOR; majorSeventh = true; rest = rest.drop(4) }
            rest.startsWith("maj") -> { majorSeventh = true; rest = rest.drop(3) }
            rest.startsWith("M") || rest.startsWith("Δ") -> { majorSeventh = true; rest = rest.drop(1) }
            rest.startsWith("min") -> { quality = ChordQuality.MINOR; rest = rest.drop(3) }
            rest.startsWith("m") || rest.startsWith("-") -> { quality = ChordQuality.MINOR; rest = rest.drop(1) }
            rest.startsWith("dim") -> { quality = ChordQuality.DIMINISHED; rest = rest.drop(3) }
            rest.startsWith("°") || rest.startsWith("o") -> { quality = ChordQuality.DIMINISHED; rest = rest.drop(1) }
            rest.startsWith("ø") -> { quality = ChordQuality.DIMINISHED; halfDim = true; rest = rest.drop(1) }
            rest.startsWith("aug") -> { quality = ChordQuality.AUGMENTED; rest = rest.drop(3) }
            rest.startsWith("+") -> { quality = ChordQuality.AUGMENTED; rest = rest.drop(1) }
        }
        val extensionMatch = Regex("^(13|11|9|7|6)").find(rest)
        val extension = extensionMatch?.value?.toInt() ?: if (halfDim) 7 else 0
        if (extensionMatch != null) rest = rest.drop(extensionMatch.value.length)
        if (majorSeventh && extension !in listOf(7, 9, 11, 13)) return null
        var suspension = ""
        if (rest.startsWith("sus")) {
            suspension = if (rest.startsWith("sus2")) "sus2" else if (rest.startsWith("sus4")) "sus4" else "sus4"
            rest = rest.drop(if (rest.startsWith("sus2") || rest.startsWith("sus4")) 4 else 3)
            quality = if (suspension == "sus2") ChordQuality.SUS2 else ChordQuality.SUS4
        }
        // Parentheses are grouping only, never discarded unless balanced and trailing.
        if (rest.startsWith('(') && rest.endsWith(')') && rest.count { it == '(' } == 1) rest = rest.substring(1, rest.length - 1)
        val modifiers = mutableListOf<Pair<String, Int>>()
        while (rest.isNotEmpty()) {
            val token = Regex("^(add|b|#)(13|11|9|6|5|4|2)").find(rest) ?: return null
            modifiers += token.groupValues[1] to token.groupValues[2].toInt()
            rest = rest.drop(token.value.length)
        }
        val tones = linkedMapOf(1 to 0)
        tones[when (quality) { ChordQuality.SUS2 -> 2; ChordQuality.SUS4 -> 4; else -> 3 }] = when (quality) {
            ChordQuality.MINOR, ChordQuality.DIMINISHED -> 3; ChordQuality.SUS2 -> 2; ChordQuality.SUS4 -> 5; else -> 4
        }
        tones[5] = when (quality) { ChordQuality.DIMINISHED -> 6; ChordQuality.AUGMENTED -> 8; else -> 7 }
        if (extension == 6) tones[6] = 9
        if (extension >= 7) tones[7] = when { majorSeventh -> 11; quality == ChordQuality.DIMINISHED && !halfDim -> 9; else -> 10 }
        if (extension >= 9) tones[9] = 14
        if (extension >= 11) tones[11] = 17
        if (extension >= 13) tones[13] = 21
        modifiers.forEach { (kind, degree) ->
            val base = mapOf(2 to 2, 3 to 4, 4 to 5, 5 to 7, 6 to 9, 9 to 14, 11 to 17, 13 to 21)[degree] ?: return null
            tones[degree] = base + when (kind) { "b" -> -1; "#" -> 1; else -> 0 }
        }
        halfDim = halfDim || quality == ChordQuality.MINOR && tones[5] == 6 && tones[7] == 10
        val tail = buildString {
            when { halfDim -> { append("ø"); append(if (extension >= 7) extension else 7) }; quality == ChordQuality.DIMINISHED -> { append("°"); if (extension != 0) append(extension) }
                else -> { if (quality == ChordQuality.AUGMENTED) append('+'); if (majorSeventh) append("maj"); if (extension != 0) append(extension); append(suspension) } }
            modifiers.filterNot { halfDim && it == ("b" to 5) }.forEach { append(it.first); append(it.second) }
        }
        return ParsedChord(raw, root, quality, originalSuffix, tones.map { ChordTone(it.key, it.value) }, bass, romanSuffix = tail)
    }
}

enum class LabelMode(val title: String) { SYMBOLS("Chord symbols"), ROMAN("Roman numerals"), BOTH("Both") }
enum class Spelling(val title: String) { AUTO("Auto"), SHARPS("Sharps"), FLATS("Flats") }
data class KeyContext(val tonic: Note, val minor: Boolean) {
    val name: String get() = tonic.name + if (minor) "m" else ""
    val description: String get() = tonic.name + if (minor) " minor" else " major"
    companion object {
        fun from(key: String?, mode: String?): KeyContext? {
            if (key.isNullOrBlank() || mode !in listOf("major", "minor")) return null
            if (key.endsWith('m') && mode != "minor") return null
            return Note.parse(key.removeSuffix("m"))?.let { KeyContext(it, mode == "minor") }
        }
    }
}

object Theory {
    fun signed(n: Int): Int = Math.floorMod(n + 6, 12) - 6
    fun currentKey(context: KeyContext, steps: Int, spelling: Spelling = Spelling.AUTO): KeyContext {
        if (Math.floorMod(steps, 12) == 0 && spelling == Spelling.AUTO) return context
        val pitch = Math.floorMod(context.tonic.pitch + steps, 12)
        val auto = if (context.minor) listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B")
                   else listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        val tonic = if (spelling == Spelling.AUTO) Note.parse(auto[pitch])!! else Note.atPitch(pitch, spelling == Spelling.FLATS)
        return KeyContext(tonic, context.minor)
    }
    private fun move(note: Note, steps: Int, from: KeyContext?, to: KeyContext?, spelling: Spelling): Note {
        if (from != null && to != null) {
            val letter = Math.floorMod(note.letter + to.tonic.letter - from.tonic.letter, 7)
            return Note(letter, signed(note.pitch + steps - Note.naturals[letter]))
        }
        if (steps % 12 == 0 && spelling == Spelling.AUTO) return note
        return Note.atPitch(note.pitch + steps, spelling == Spelling.FLATS)
    }
    fun transpose(raw: String, steps: Int, context: KeyContext? = null, spelling: Spelling = Spelling.AUTO): String {
        val chord = ChordParser.parse(raw) ?: return raw
        if (chord.noChord) return raw
        val current = context?.let { currentKey(it, steps, spelling) }
        return move(chord.root!!, steps, context, current, spelling).name + chord.suffix + (chord.bass?.let { "/" + move(it, steps, context, current, spelling).name } ?: "")
    }
    private fun degree(note: Note, context: KeyContext, arabic: Boolean = false): String {
        val index = Math.floorMod(note.letter - context.tonic.letter, 7)
        val scale = if (context.minor) listOf(0, 2, 3, 5, 7, 8, 10) else listOf(0, 2, 4, 5, 7, 9, 11)
        val alteration = signed(note.pitch - context.tonic.pitch - scale[index])
        return (if (alteration < 0) "b".repeat(-alteration) else "#".repeat(alteration)) + if (arabic) "${index + 1}" else listOf("I", "II", "III", "IV", "V", "VI", "VII")[index]
    }
    fun roman(raw: String, context: KeyContext?, ascii: Boolean = false): String? {
        if (context == null) return null
        val chord = ChordParser.parse(raw) ?: return null
        if (chord.noChord) return raw
        var root = degree(chord.root!!, context)
        if (chord.quality in listOf(ChordQuality.MINOR, ChordQuality.DIMINISHED)) root = root.lowercase()
        val result = root + chord.romanSuffix + (chord.bass?.let { "/" + degree(it, context, true) } ?: "")
        return if (ascii) result.replace("ø", "hdim").replace("°", "dim") else result
    }
    /** Analyze displayed chords in the displayed key; respelling preserves diatonic relationships. */
    fun label(raw: String, steps: Int, context: KeyContext?, mode: LabelMode, spelling: Spelling = Spelling.AUTO): String {
        val symbol = transpose(raw, steps, context, spelling)
        val roman = roman(symbol, context?.let { currentKey(it, steps, spelling) })
        return when { mode == LabelMode.SYMBOLS || roman == null -> symbol; mode == LabelMode.ROMAN -> roman; else -> "$symbol ($roman)" }
    }
}
