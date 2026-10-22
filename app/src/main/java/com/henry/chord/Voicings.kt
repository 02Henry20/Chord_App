package com.henry.chord

data class VoicedNote(val note: Note, val midi: Int) {
    // Written octaves change at C, including Cb4 = B3 and B#3 = C4.
    val label get() = note.name + ((midi - Note.naturals[note.letter] - note.accidental) / 12 - 1)
}
data class PianoVoicing(val title: String, val notes: List<VoicedNote>)
object PianoVoicings {
    fun forChord(chord: ParsedChord): List<PianoVoicing> {
        val root = chord.root ?: return emptyList()
        fun sorted(notes: List<Note>) = notes.distinctBy { it.pitch }.sortedBy { Math.floorMod(it.pitch - root.pitch, 12) }
        val tones = sorted(chord.notes)
        if (tones.isEmpty()) return emptyList()
        fun close(order: List<Note>, bass: Note? = null): List<VoicedNote> {
            var previous = 59
            val result = order.map { note ->
                var midi = 60 + note.pitch
                while (midi <= previous) midi += 12
                previous = midi
                VoicedNote(note, midi)
            }.toMutableList()
            if (bass != null) {
                var midi = 48 + bass.pitch
                while (midi >= result.first().midi) midi -= 12
                result.add(0, VoicedNote(bass, midi))
            }
            return result
        }
        // Extended chords use a practical shell, omitting fifth/11th before essential guide tones.
        if (tones.size > 4) {
            val shell = sorted(chord.tones.zip(chord.notes).filter { it.first.degree in listOf(1, 3, 6, 7, chord.tones.maxOf { t -> t.degree }) }.map { it.second })
            return listOf(PianoVoicing("Close position · shell", close(shell, chord.bass)), PianoVoicing("All chord tones", close(tones, chord.bass)))
        }
        if (chord.bass != null) return listOf(PianoVoicing("Slash bass · ${chord.bass.name}", close(tones, chord.bass)))
        return buildList {
            add(PianoVoicing("Root position · close", close(tones)))
            // Inversions refer to the third/fifth, not the next pitch (which may be an added 9th).
            if (chord.quality !in listOf(ChordQuality.SUS2, ChordQuality.SUS4)) {
                listOf(3 to "1st inversion", 5 to "2nd inversion").forEach { (degree, title) ->
                    val note = chord.tones.zip(chord.notes).find { it.first.degree == degree }?.second
                    val index = tones.indexOfFirst { it.pitch == note?.pitch }
                    if (index > 0) add(PianoVoicing(title, close(tones.drop(index) + tones.take(index))))
                }
            }
        }
    }
}

data class GuitarVoicing(val frets: List<Int>) {
    val midi get() = frets.mapIndexedNotNull { i, fret -> if (fret < 0) null else GuitarVoicings.tuning[i] + fret }
    val start get() = if ((frets.maxOrNull() ?: 0) <= 4) 1 else frets.filter { it > 0 }.minOrNull() ?: 1
}
object GuitarVoicings {
    val tuning = listOf(40, 45, 50, 55, 59, 64) // low E2 to high E4
    private val open = mapOf(
        "C" to listOf(-1,3,2,0,1,0), "G" to listOf(3,2,0,0,0,3), "D" to listOf(-1,-1,0,2,3,2),
        "A" to listOf(-1,0,2,2,2,0), "E" to listOf(0,2,2,1,0,0), "F" to listOf(1,3,3,2,1,1),
        "Am" to listOf(-1,0,2,2,1,0), "Em" to listOf(0,2,2,0,0,0), "Dm" to listOf(-1,-1,0,2,3,1),
        "C/E" to listOf(0,3,2,0,1,0), "D/F#" to listOf(2,0,0,2,3,2),
        "C7" to listOf(-1,3,2,3,1,0), "G7" to listOf(3,2,0,0,0,1), "D7" to listOf(-1,-1,0,2,1,2))
    private val movable = listOf(
        "E" to listOf(0,2,2,1,0,0), "Em" to listOf(0,2,2,0,0,0), "E7" to listOf(0,2,0,1,0,0),
        "Em7" to listOf(0,2,0,0,0,0), "Emaj7" to listOf(0,2,1,1,0,0), "Esus4" to listOf(0,2,2,2,0,0),
        "A" to listOf(-1,0,2,2,2,0), "Am" to listOf(-1,0,2,2,1,0), "A7" to listOf(-1,0,2,0,2,0),
        "Am7" to listOf(-1,0,2,0,1,0), "Amaj7" to listOf(-1,0,2,1,2,0), "Asus2" to listOf(-1,0,2,2,0,0),
        "Asus4" to listOf(-1,0,2,2,3,0), "Ddim7" to listOf(-1,-1,0,1,0,1))
    fun valid(chord: ParsedChord, voicing: GuitarVoicing): Boolean {
        if (voicing.frets.size != 6 || voicing.frets.any { it !in -1..15 } || voicing.midi.size < 3) return false
        val pitches = voicing.midi.map { it % 12 }.toSet()
        val positive = voicing.frets.filter { it > 0 }
        return pitches == chord.pitches && voicing.midi.minOrNull()?.rem(12) == (chord.bass ?: chord.root)?.pitch &&
            (positive.maxOrNull() ?: 0) - (positive.minOrNull() ?: 0) <= 4
    }
    fun forChord(chord: ParsedChord): List<GuitarVoicing> {
        val root = chord.root ?: return emptyList()
        val candidates = open.values.map { GuitarVoicing(it) }.toMutableList()
        movable.forEach { (name, frets) ->
            val base = ChordParser.parse(name)!!
            val shift = Math.floorMod(root.pitch - base.root!!.pitch, 12)
            if (base.tones.map { it.semitones }.toSet() == chord.tones.map { it.semitones }.toSet()) {
                candidates += GuitarVoicing(frets.map { if (it < 0) it else it + shift })
            }
        }
        return candidates.distinctBy { it.frets }.filter { valid(chord, it) }.sortedBy { it.frets.maxOrNull() }.take(4)
    }
}
