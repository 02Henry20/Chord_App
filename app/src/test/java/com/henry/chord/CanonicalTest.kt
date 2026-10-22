package com.henry.chord

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CanonicalTest {
    private val example = """{"schemaVersion":1,"songId":"evening-lights-demo","title":"Evening Lights","artist":"Demo Artist","originalKey":"G","tonalMode":"major","tempoBpm":96,"timeSignature":"4/4","tags":["pop","singalong"],"sections":[{"id":"v1","type":"verse","label":"Verse 1","lines":[{"segments":[{"text":"Under the "},{"chord":"G","text":"evening lights, "},{"chord":"D/F#","text":"we sing"}]},{"segments":[{"text":"And "},{"chord":"Em7","text":"let our voices "},{"chord":"C","text":"ring"}]}]}]}"""
    @Test fun acceptsExampleAndUsesExactBeforeSegmentPositioning() {
        val doc = Canonical.decode(example).single().document
        val line = doc.arrangements.single().sections.single().lines.first()
        assertEquals("Under the evening lights, we sing", line.lyric)
        assertEquals("Under the [G]evening lights, [D/F#]we sing", line.inline())
        assertEquals("major", doc.arrangements.single().tonalMode)
        assertEquals(doc, Canonical.decode(Canonical.encode(doc)).single().document)
        assertEquals(doc, Canonical.decode(example).single().document)
    }
    @Test fun unicodeWhitespaceBlankAndChordOnlyRowsSurvive() {
        val text = "{title: 夜の歌}\n{key: F#m}\n{start_of_verse: Verse 1}\n  [F#m]e\u0301 👩🏽‍🎤  [C#7]東京\u00a0\n\n[D][E]\n{end_of_verse}\n{start_of_bridge: Pont}\n[A]été  "
        val parsed = SheetParser.parse(text, "unicode.cho").document
        val a = parsed.arrangements.single()
        assertEquals("minor", a.tonalMode)
        assertEquals(listOf("verse", "bridge"), a.sections.map { it.type })
        assertEquals("  e\u0301 👩🏽‍🎤  東京\u00a0", a.sections.first().lines[0].lyric)
        assertEquals("", a.sections.first().lines[1].lyric)
        assertEquals(listOf("D", "E"), a.sections.first().lines[2].segments.mapNotNull { it.anchor?.rawChord })
        assertEquals(parsed, Canonical.decode(Canonical.encode(parsed)).single().document)
    }
    @Test fun chordproMetadataAndSectionsAreRetained() {
        val parsed = SheetParser.parse("{t: Original}\n{artist: Author}\n{album: Album}\n{key: Bb}\n{tempo: 102}\n{time: 6/8}\n{license: CC0}\n{start_of_intro}\n[Bb][F]\n{end_of_intro}\n{soc: Refrain}\n[Bb]Sing\n{eoc}\n{comment: Play gently}\n{custom_directive: Keep me}", "test.pro")
        val d = parsed.document; val a = d.arrangements.single()
        assertEquals("Album", d.album); assertEquals(102.0, a.tempoBpm!!, 0.0); assertEquals("6/8", a.timeSignature); assertEquals("CC0", a.licenseNotes)
        assertEquals(listOf("intro", "chorus", "custom"), a.sections.map { it.type })
        assertTrue(a.editorText().contains("{custom_directive: Keep me}"))
        assertTrue(parsed.warnings.isNotEmpty())
    }
    @Test fun unknownChordsArePreservedInsteadOfDropped() {
        val p = SheetParser.parse("{key: Unknown}\n[N.C.]silent [Cweird!]annotation", "test.cho")
        assertNull(p.document.arrangements.single().originalKey)
        assertTrue(p.document.arrangements.single().editorText().contains("[Cweird!]"))
        assertTrue(p.warnings.any { "Unsupported chord" in it })
    }
    @Test fun editingRecalculatesBoundariesAndKeepsUnchangedLineIds() {
        val old = SheetParser.parse("{start_of_verse: Verse}\n[C]hello [G]world\n[Am]unchanged", "edit.cho").document
        val new = SheetParser.parse("{start_of_verse: Verse}\n[C]a longer hello [G]world\n[Am]unchanged", "edit.cho", old).document
        val before = old.arrangements.single().sections.single().lines; val after = new.arrangements.single().sections.single().lines
        assertEquals(before[1].id, after[1].id)
        assertEquals(before[0].segments[1].anchor?.id, after[0].segments[1].anchor?.id)
        assertEquals("a longer hello ", after[0].segments[0].text)
        assertEquals("G", after[0].segments[1].anchor?.rawChord)
    }
    @Test fun separateChordRowsRequireReview() {
        val p = SheetParser.plainText("C     G\nHello world\nAm  F", "legacy.txt")
        assertTrue(p.needsReview); assertEquals("C     G\nHello world\nAm  F", p.rawText)
        assertTrue(p.document.arrangements.single().editorText().contains("[C]Hello [G]world"))
    }
    @Test fun insertingSectionsAndLinesDoesNotStealExistingIds() {
        val old = SheetParser.parse("{start_of_verse: Verse 1}\n[C]First line\n[G]Second line\n{start_of_chorus: Chorus}\n[F]Chorus line", "edit.cho").document
        val edited = SheetParser.parse("{start_of_intro: Intro}\n[Am]New intro\n{start_of_verse: Verse 1}\n[Dm]Inserted line\n[C]First line\n[G]Second line\n{start_of_chorus: Chorus}\n[F]Chorus line", "edit.cho", old).document
        val before = old.arrangements.single().sections; val after = edited.arrangements.single().sections
        assertEquals(before[0].id, after[1].id); assertEquals(before[1].id, after[2].id)
        assertEquals(before[0].lines[0].id, after[1].lines[1].id)
        assertEquals(before[0].lines[1].id, after[1].lines[2].id)
        val normalized = Canonical.decode(Canonical.encode(edited)).single().document
        assertEquals(edited, normalized)
    }
    @Test fun ambiguousUnicodeAndTabsNeverGetSilentOffsets() {
        val p = SheetParser.plainText("C\tG\n👩🏽‍🎤 sings", "ambiguous.txt")
        assertTrue(p.needsReview); assertTrue(p.warnings.any { "ambiguous" in it })
        assertFalse(p.document.arrangements.single().editorText().contains("[G]"))
        assertTrue(p.document.arrangements.single().editorText().contains("C\tG"))
    }
    @Test fun fingerprintIgnoresGeneratedIdsButNotSpacesOrChordBoundaries() {
        val a = SheetParser.parse("[C]Hi  [G]there", "a.cho").document
        val b = SheetParser.parse("[C]Hi  [G]there", "a.cho").document
        assertNotEquals(a.songId, b.songId); assertEquals(Canonical.fingerprint(a), Canonical.fingerprint(b))
        val c = SheetParser.parse("[C]Hi [G]there", "a.cho").document
        assertNotEquals(Canonical.fingerprint(a), Canonical.fingerprint(c))
    }
    @Test fun schemaDraftMigratesAndFutureVersionIsRejected() {
        assertTrue(Canonical.decode(example.replace("\"schemaVersion\":1", "\"schemaVersion\":0")).single().warnings.any { "Migrated" in it })
        assertThrows(IllegalArgumentException::class.java) { Canonical.decode(example.replace("\"schemaVersion\":1", "\"schemaVersion\":2")) }
    }
    @Test fun traversalAbsoluteAndWindowsPathsAreBlocked() {
        listOf("../cover.png", "a/../../file", "/etc/passwd", "C:/file", "a\\b", "a//b", "x/./y", "CON.png").forEach { assertThrows(IllegalArgumentException::class.java) { ArtworkStore.safePath(it) } }
        assertEquals("songs/夜/assets/cover.png", ArtworkStore.safePath("songs/夜/assets/cover.png"))
    }
    @Test fun relativeArtworkUsesManifestDirectoryThenArchiveRoot() {
        val root = kotlin.io.path.createTempDirectory("chord-art").toFile()
        try {
            val local = File(root, "pack/songs/Title/assets/cover.png").apply { parentFile.mkdirs(); writeText("local") }
            assertEquals(local.canonicalFile, ArtworkStore.resolve(root, "pack/songs/Title/song.json", "assets/cover.png"))
            local.delete()
            val shared = File(root, "pack/assets/cover.png").apply { parentFile.mkdirs(); writeText("root") }
            assertEquals(shared.canonicalFile, ArtworkStore.resolve(root, "pack/songs/Title/song.json", "assets/cover.png"))
            assertNull(ArtworkStore.resolve(root, "pack/song.json", "assets/missing.png"))
        } finally { root.deleteRecursively() }
    }
}
