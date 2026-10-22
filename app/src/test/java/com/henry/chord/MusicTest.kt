package com.henry.chord

import org.junit.Assert.*
import org.junit.Test

class MusicTest {
    @Test fun transposesRootsSuffixesAndSlashBass() {
        assertEquals("Dmaj7/F#", Music.transpose("Cmaj7/E", 2))
        assertEquals("Bb", Music.transpose("C", -2, true))
        assertEquals("C#m", Music.transpose("Bm", 14))
        assertEquals("N.C.", Music.transpose("N.C.", 3))
        assertEquals("F#m", Music.transpose("Em", 2))
    }
    @Test fun romanDegreesRetainQualityAndBass() {
        assertEquals("vi", Music.roman("Am", "C"))
        assertEquals("V7/7", Music.roman("G7/B", "C"))
        assertEquals("Imaj7", Music.roman("Dmaj7", "D"))
        assertEquals("bVII", Music.roman("F", "G"))
    }
    @Test fun chordTonesAreMusicallyCorrect() {
        assertEquals(setOf(0, 4, 7), Music.piano("C"))
        assertEquals(setOf(9, 0, 4, 7), Music.piano("Am7"))
        assertEquals(setOf(2, 6, 9, 0), Music.piano("D7"))
        assertEquals(setOf(0, 3, 6, 9), Music.piano("Cdim7"))
    }
    @Test fun parsesMetadataAndRoundTripsOriginalMaterial() {
        val song = SongImport.parse("{t: Morning}\n{st: Original Artist}\n{key: Am}\n{comment:Verse}\n[Am]Hello [F]world", "fallback.cho")
        assertEquals("Morning", song.title); assertEquals("Original Artist", song.artist); assertEquals("Am", song.key)
        assertFalse(song.body.contains("{t:")); assertTrue(song.body.contains("{comment:Verse}"))
        assertEquals(song, SongImport.parse(SongImport.export(song), "roundtrip.cho"))
    }
    @Test fun searchEscapesFtsOperatorsAndPunctuation() {
        assertEquals("\"love\"* \"OR\"* \"blue\"*", SongImport.searchQuery("love OR \"blue\"*"))
        assertEquals("", SongImport.searchQuery("*** \""))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsBinaryImport() { SongImport.parse("abc\u0000", "bad.txt") }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyImport() { SongImport.parse("   ", "empty.txt") }
}
