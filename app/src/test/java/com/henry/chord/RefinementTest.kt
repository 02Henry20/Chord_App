package com.henry.chord

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RefinementTest {
    private fun valid()=Canonical.json(SheetParser.parse("{title: Original example}\n{artist: Original Artist}\n{key: C}\n{start_of_verse: Verse 1}\nUn[C]der [G]écho 日本\n{start_of_solo: Solo}\n[C] [F] [G]","sample.cho").document)
    private fun rejected(text: String,part: String) {
        try { ImportSyntax.native(text); fail("Accepted invalid input: $part") }
        catch (e: IllegalArgumentException) { assertTrue("Expected $part in ${e.message}",e.message.orEmpty().contains(part)) }
    }
    @Test fun strictJsonRejectsLenientSyntaxAndReportsLocation() {
        for (text in listOf("{'title':'x'}","{\"a\":1,}","{\"a\":[1,]}","{\"a\":01}","{\"a\":NaN}","{\"a\":\"broken\ntext\"}","{\"a\":1,\"a\":2}","{\"a\":1,\"\\u0061\":2}","{} trailing")) rejected(text,"JSON line")
    }
    @Test fun validatesNativeTypesUnknownFieldsAndRequiredFields() {
        ImportSyntax.native(valid().toString())
        rejected(valid().put("schemaVersion",4294967297L).toString(),"schemaVersion")
        rejected(valid().put("title",17).toString(),"$.title")
        rejected(valid().put("artist","").toString(),"$.artist")
        rejected(valid().put("mystery",true).toString(),"$.mystery")
        rejected(valid().put("year",2026.5).toString(),"$.year")
        rejected(valid().put("year",26).toString(),"$.year")
        rejected(valid().apply { remove("schemaVersion") }.toString(),"schemaVersion")
    }
    @Test fun catchesPreciseChordAndDuplicateIdPaths() {
        val bad=valid(); val arr=bad.getJSONArray("arrangements").getJSONObject(0); val sections=arr.getJSONArray("sections")
        val seg=sections.getJSONObject(0).getJSONArray("lines").getJSONObject(0).getJSONArray("segments").getJSONObject(1)
        seg.getJSONObject("anchor").put("rawChord","H7")
        rejected(bad.toString(),"$.arrangements[0].sections[0].lines[0].segments[1].anchor.rawChord")
        seg.getJSONObject("anchor").put("rawChord","C")
        sections.getJSONObject(1).put("id",sections.getJSONObject(0).getString("id"))
        rejected(bad.toString(),"duplicate")
    }
    @Test fun chordOnlyRowsAndExactUnicodeRemainValid() {
        val parsed=valid(); ImportSyntax.native(parsed.toString())
        val before=Canonical.decode(parsed.toString()).single().document
        val after=Canonical.decode(Canonical.encode(before)).single().document
        assertEquals(before,after)
        assertEquals(listOf("C","F","G"),after.arrangements.single().sections.last().lines.flatMap { l -> l.segments.mapNotNull { it.anchor?.rawChord } })
        val segment=parsed.getJSONArray("arrangements").getJSONObject(0).getJSONArray("sections").getJSONObject(0).getJSONArray("lines").getJSONObject(0).getJSONArray("segments").getJSONObject(0)
        segment.put("text","line\nbreak"); rejected(parsed.toString(),"separate line")
        segment.put("text","\uD800"); rejected(parsed.toString(),"Unicode")
    }
    @Test fun validatesLinksAssetPathsAndArrangementFields() {
        rejected(valid().put("musicLinks",JSONObject().put("spotifyTrackUrl","javascript:bad")).toString(),"spotifyTrackUrl")
        rejected(valid().put("artwork",JSONObject().put("path","../cover.png")).toString(),"artwork.path")
        val value=valid(); value.getJSONArray("arrangements").getJSONObject(0).put("tempoBpm","96")
        rejected(value.toString(),"tempoBpm")
        value.getJSONArray("arrangements").getJSONObject(0).put("tempoBpm",96).put("timeSignature","4/3")
        rejected(value.toString(),"timeSignature")
    }
    @Test fun allChildrenOfManifestMustValidateBeforeImport() {
        val manifest=JSONObject().put("format","chordroom-collection").put("schemaVersion",1).put("songs",org.json.JSONArray().put(valid()).put(valid().put("artist",false)))
        rejected(manifest.toString(),"$.songs[1].artist")
        manifest.getJSONArray("songs").getJSONObject(1).put("artist","Artist")
        ImportSyntax.native(manifest.toString())
    }
    @Test fun strictChordProCatchesMalformedMarkersAndMetadata() {
        val accepted="{title: Original}\n{key: Am}\n{start_of_verse}\nUn[Am]der [E]the stars\n{end_of_verse}\n{start_of_solo}\n[Am] [Dm] [E]"
        ImportSyntax.chordPro(accepted)
        for ((input,part) in listOf("[C" to "missing closing ]","[H7]text" to "unsupported chord","text]" to "opening [","{title: broken" to "directive","{tempo: fast}" to "tempo","{key: H}" to "invalid key","{time: 4/3}" to "time signature","{start_of_verse}\n{end_of_chorus}" to "does not match")) {
            try { ImportSyntax.chordPro(input); fail(input) } catch (e: IllegalArgumentException) { assertTrue(e.message,e.message!!.contains(part)); assertTrue(e.message!!.contains("Line")) }
        }
    }
    @Test fun pianoAlwaysShowsFullRootPositionIncludingExtensionsAndSlashBass() {
        assertEquals(listOf("C4","E4","G4"),PianoVoicings.rootPosition(ChordParser.parse("C")!!)!!.notes.map { it.label })
        assertEquals(listOf("C4","E4","G4","Bb4","D5","F5","A5"),PianoVoicings.rootPosition(ChordParser.parse("C13")!!)!!.notes.map { it.label })
        assertEquals(listOf("E3","C4","E4","G4"),PianoVoicings.rootPosition(ChordParser.parse("C/E")!!)!!.notes.map { it.label })
        assertNull(PianoVoicings.rootPosition(ChordParser.parse("N.C.")!!))
    }
    @Test fun sectionColorFamiliesRemainConsistentAndDistinct() {
        assertEquals("teal",sectionFamily("verse")); assertEquals("violet",sectionFamily("chorus"))
        assertEquals(sectionFamily("intro"),sectionFamily("outro"))
        assertNotEquals(sectionFamily("verse"),sectionFamily("chorus")); assertNotEquals(sectionFamily("verse"),sectionFamily("intro"))
    }
    @Test fun downloadableGuideContainsAnActuallyValidCompleteExample() {
        val guide=listOf(File("../docs/SONG-IMPORT-GUIDE.txt"),File("docs/SONG-IMPORT-GUIDE.txt")).first { it.isFile }.readText()
        val start=guide.indexOf("{\n  \"schemaVersion\""); val end=guide.indexOf("\n}\n",start)+2
        assertTrue(start>=0 && end>start); ImportSyntax.native(guide.substring(start,end))
    }
}
