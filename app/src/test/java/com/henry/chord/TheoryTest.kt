package com.henry.chord

import org.junit.Assert.*
import org.junit.Test

class TheoryTest {
    private val c = KeyContext.from("C", "major")!!
    private fun degree(chord: String, key: String = "C", mode: String = "major") = Theory.roman(chord, KeyContext.from(key, mode))
    @Test fun majorAndMinorTonalCenters() {
        assertEquals(listOf("I", "V", "IV", "ii", "iii", "vi"), listOf("C", "G", "F", "Dm", "Em", "Am").map { degree(it) })
        assertEquals(listOf("I", "V", "IV", "vi"), listOf("G", "D", "C", "Em").map { degree(it, "G") })
        assertEquals(listOf("i", "iv", "V", "#vii°", "VII"), listOf("Am", "Dm", "E", "G#dim", "G").map { degree(it, "A", "minor") })
        assertEquals("i", degree("Cm", "C", "minor"))
    }
    @Test fun spellingDistinguishesEnharmonicFunctions() {
        assertEquals("bVII", degree("Bb")); assertEquals("#VI", degree("A#"))
        assertEquals("bII", degree("Db")); assertEquals("#I", degree("C#"))
        assertEquals("I", degree("C#", "C#")); assertEquals("VII", degree("B#", "C#"))
        assertEquals("bI", degree("Cb")); assertEquals("#IV", degree("F♯"))
        assertEquals("bVII", degree("F", "G"))
        assertEquals("vii°", degree("Adim", "Bb"))
    }
    @Test fun qualitiesExtensionsAndBassConvention() {
        val pairs = mapOf("G7" to "V7", "Am7" to "vi7", "Dm7" to "ii7", "Bdim" to "vii°", "Bm7b5" to "viiø7", "Bø7" to "viiø7", "Caug" to "I+", "Bbmaj7" to "bVIImaj7", "C/E" to "I/3", "G7/B" to "V7/7", "D/F#" to "II/#4", "C7sus4" to "I7sus4", "Cadd9" to "Iadd9", "Cdim7" to "i°7", "CmMaj7" to "imaj7")
        pairs.forEach { (raw, expected) -> assertEquals(raw, expected, degree(raw)) }
        assertEquals("viihdim7", Theory.roman("Bm7b5", c, ascii = true))
        assertEquals("viiø9", degree("Bm9b5"))
        assertEquals("viiø11", degree("Bø11"))
    }
    @Test fun requiredGrammarAndAccurateToneSpelling() {
        listOf("C", "Cm", "C7", "Cmaj7", "Cm7", "Cdim", "Caug", "Csus2", "Csus4", "Cadd9", "C9", "C13", "C/E", "D/F#", "F#m7b5", "Bbmaj7", "G7sus4", "F♯m7♭5", "N.C.", "NC", "C7(b9)", "C7#9", "C𝄪m").forEach { assertNotNull(it, ChordParser.parse(it)) }
        assertEquals(listOf("Bb", "D", "F", "A"), ChordParser.parse("Bbmaj7")!!.notes.map { it.name })
        assertEquals(listOf("F#", "A", "C", "E"), ChordParser.parse("F#m7b5")!!.notes.map { it.name })
        assertEquals(listOf("C", "Eb", "Gb", "Bbb"), ChordParser.parse("Cdim7")!!.notes.map { it.name })
        assertEquals(setOf(0, 4, 7, 10, 2, 5, 9), ChordParser.parse("C13")!!.pitches)
        assertEquals(setOf(7, 0, 2, 5), ChordParser.parse("G7sus4")!!.pitches)
        listOf("", "Cweird!", "H7", "C/silly", "C7(b9", "C13garbage").forEach { assertNull(it, ChordParser.parse(it)) }
    }
    @Test fun simultaneousTranspositionPreservesDegreesAndRawData() {
        val chords = listOf("C", "G", "F", "Dm", "Am7", "Bb", "Bdim", "C/E", "F#7", "A#aug")
        for (steps in -24..24) for (spelling in Spelling.entries) for (raw in chords) {
            val moved = Theory.transpose(raw, steps, c, spelling)
            assertEquals("$raw $steps $spelling -> $moved", Theory.roman(raw, c), Theory.roman(moved, Theory.currentKey(c, steps, spelling)))
        }
        assertEquals("D (I)", Theory.label("C", 2, c, LabelMode.BOTH))
        assertEquals("A (V)", Theory.label("G", 2, c, LabelMode.BOTH))
        assertEquals("D/F#", Theory.transpose("C/E", 2, c))
        assertEquals("Db/F", Theory.transpose("C/E", 1, c))
        assertEquals("Bb", Theory.currentKey(c, -2).name)
        assertEquals("Cweird!", Theory.transpose("Cweird!", 3, c))
    }
    @Test fun unknownModeAndKeyNeverInventDegrees() {
        assertNull(KeyContext.from("C", null)); assertNull(KeyContext.from(null, "major")); assertNull(KeyContext.from("Am", "major"))
        assertNull(Theory.roman("C", null)); assertNull(Theory.roman("Cweird!", c))
        assertEquals("D", Theory.label("C", 2, null, LabelMode.ROMAN))
        val doc = SheetParser.parse("{key:C}\n[C]Test", "test.cho").document
        val unknown = doc.copy(arrangements = listOf(doc.arrangements.single().copy(tonalMode = null)))
        assertNull(Canonical.decode(Canonical.encode(unknown)).single().document.arrangements.single().tonalMode)
    }
    @Test fun voicingsContainActualNotesAndRespectBass() {
        val chord = ChordParser.parse("C")!!
        val suggestions = PianoVoicings.forChord(chord)
        assertEquals(listOf("C4", "E4", "G4"), suggestions[0].notes.map { it.label })
        assertEquals(1, suggestions.size)
        val slash = PianoVoicings.forChord(ChordParser.parse("C/E")!!).single().notes
        assertEquals("E3", slash.first().label); assertEquals(52, slash.first().midi)
        assertEquals("Cb4", VoicedNote(Note.parse("Cb")!!, 59).label)
        assertEquals("B#3", VoicedNote(Note.parse("B#")!!, 60).label)
        for (raw in listOf("Cadd9", "C9", "C13", "C6add9", "F#m7b5", "Csus2")) {
            val parsed = ChordParser.parse(raw)!!
            val root = PianoVoicings.forChord(parsed).single().notes
            assertEquals(parsed.root!!.pitch, root.first().midi % 12)
            assertEquals(parsed.pitches, root.map { it.midi % 12 }.toSet())
        }
    }
    @Test fun legacyPreferencesMigrateWithoutLosingChoice() {
        assertEquals(LabelMode.SYMBOLS, Settings.labelPreference(null, false))
        assertEquals(LabelMode.ROMAN, Settings.labelPreference(null, true))
        assertEquals(LabelMode.BOTH, Settings.labelPreference("BOTH", true))
    }
}
