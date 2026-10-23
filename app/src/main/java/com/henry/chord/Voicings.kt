package com.henry.chord

data class VoicedNote(val note: Note, val midi: Int) {
    // Written octaves change at C, including Cb4 = B3 and B#3 = C4.
    val label get() = note.name + ((midi - Note.naturals[note.letter] - note.accidental) / 12 - 1)
}
data class PianoVoicing(val title: String, val notes: List<VoicedNote>)
object PianoVoicings {
    /** One complete root-position chord; extensions retain their written octave above the root. */
    fun rootPosition(chord: ParsedChord): PianoVoicing? {
        val root=chord.root ?: return null
        val notes=chord.tones.zip(chord.notes).sortedBy { it.first.semitones }
            .map { (tone,note) -> VoicedNote(note,60+root.pitch+tone.semitones) }.toMutableList()
        if (notes.isEmpty()) return null
        chord.bass?.let { bass ->
            var midi=48+bass.pitch
            while (midi>=notes.first().midi) midi-=12
            notes.add(0,VoicedNote(bass,midi))
        }
        return PianoVoicing("Root position",notes)
    }
    fun forChord(chord: ParsedChord): List<PianoVoicing> = listOfNotNull(rootPosition(chord))

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
