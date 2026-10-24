package com.henry.chord

import org.junit.Assert.*
import org.junit.Test

class SheetLayoutTest {
    private fun line() = SheetLine("line",listOf(TextSegment("s0","Un"),TextSegment("s1","der the ",ChordAnchor("a","F#m7b5")),TextSegment("s2","évening 日本 👩‍🎤",ChordAnchor("b","C/E"))))
    @Test fun scrollRemeasureReusesWrapAndWidthChangeKeepsExactAnchors() {
        val line=line(); val text=line.lyric; val anchors=SheetLayout.anchors(line)
        val cache=SheetRowCache(); var textMeasures=0
        fun rows(width: Int)=cache.atWidth(width) {
            SheetLayout.wrap(text,anchors,width.toFloat(),{ textMeasures++; it.codePointCount(0,it.length).toFloat() },{ 7f })
        }
        val wide=rows(28); val first=textMeasures
        assertTrue(first>0)
        repeat(300) { assertEquals(wide,rows(28)) }
        assertEquals("Scrolling must not reshape prefixes or recalculate wrap",first,textMeasures)
        val narrow=rows(10)
        assertTrue(textMeasures>first)
        assertNotEquals(wide,narrow)
        assertEquals(text,narrow.joinToString("") { text.substring(it.start,it.end) })
        assertEquals(anchors,narrow.flatMap { it.anchors })
        assertTrue(narrow.all { it.start in SheetLayout.boundaries(text) && it.end in SheetLayout.boundaries(text) })
        assertEquals(wide,rows(28))
    }
    @Test fun wrapsWithoutAddingWhitespaceOrSplittingAccentedCharacters() {
        val line=line(); val text=line.lyric
        val rows=SheetLayout.wrap(text,SheetLayout.anchors(line),12f,{ it.codePointCount(0,it.length).toFloat() },{ 7f })
        assertEquals(text,rows.joinToString("") { text.substring(it.start,it.end) })
        assertEquals(listOf("a","b"),rows.flatMap { it.anchors }.map { it.anchor.id })
        assertTrue(rows.all { it.start in SheetLayout.boundaries(text) && it.end in SheetLayout.boundaries(text) })
        assertEquals(2,rows.flatMap { it.anchors }.first().offset)
    }
    @Test fun adjacentAndChordOnlyAnchorsSurvive() {
        val anchors=listOf(PositionedAnchor(0,ChordAnchor("a","C")),PositionedAnchor(0,ChordAnchor("b","G")))
        assertEquals(anchors,SheetLayout.wrap("",anchors,10f,{ 0f },{ 5f }).single().anchors)
        val rows=SheetLayout.wrap("abcd",listOf(PositionedAnchor(3,ChordAnchor("a","F#m7b5"))),4f,{ it.length.toFloat() },{ 4f })
        assertEquals(3,rows.last().start)
    }
    @Test fun editsRelocateAnchorsAndKeepTheirIdentity() {
        val original=line(); val insert=AnchorEditor.edit(original,"One "+original.lyric)
        assertEquals(listOf(6,14),SheetLayout.anchors(insert).map { it.offset })
        assertEquals(listOf("a","b"),SheetLayout.anchors(insert).map { it.anchor.id })
        val deleted=AnchorEditor.edit(insert,"One Under 日本")
        assertTrue(SheetLayout.anchors(deleted).all { it.offset in SheetLayout.boundaries(deleted.lyric) })
        assertEquals(listOf("a","b"),SheetLayout.anchors(deleted).map { it.anchor.id })
    }
    @Test fun explicitNewlinesAndBlankLinesHaveIndependentRows() {
        val rows=SheetLayout.wrap("a\n\nb\n",emptyList(),10f,{ it.length.toFloat() },{ 1f })
        assertEquals(listOf("a","","b",""),rows.map { "a\n\nb\n".substring(it.start,it.end) })
    }
    @Test fun crlfAndTerminalChordNeverClipOrCrash() {
        val rows=SheetLayout.wrap("\r\nabcd",listOf(PositionedAnchor(6,ChordAnchor("tail","G7"))),4f,{ it.length.toFloat() },{ 3f })
        assertEquals(listOf("","abcd",""),rows.map { "\r\nabcd".substring(it.start,it.end) })
        assertEquals("tail",rows.last().anchors.single().anchor.id)
    }
    @Test fun editorReturnCreatesStableSeparateLinesAndAnchors() {
        val old=SheetLine("line",listOf(TextSegment("s0","Un"),TextSegment("s1","der\n",ChordAnchor("a","C")),TextSegment("s2","écho",ChordAnchor("b","G"))))
        val lines=AnchorEditor.splitLines(old)
        assertEquals(listOf("Under","écho"),lines.map { it.lyric })
        assertEquals("line",lines.first().id)
        assertEquals(listOf("a","b"),lines.flatMap(SheetLayout::anchors).map { it.anchor.id })
        assertEquals(listOf(2,0),lines.flatMap(SheetLayout::anchors).map { it.offset })
        val ids=lines.flatMap { l -> listOf(l.id)+l.segments.flatMap { listOfNotNull(it.id,it.anchor?.id) } }
        assertEquals(ids.size,ids.toSet().size)
    }
    @Test fun settingsRoundTripIncludesPerformancePreferences() {
        val p=Preferences(theme=ThemeMode.DARK,dark=true,instrument="Piano",fontSize=24,scrollSpeed=61f,leadIn=3,lineSpacing=1.6f,chordColor="Amber",sheetDisplay=SheetDisplay.LYRICS,chordLabels=LabelMode.BOTH,sheetMargin=28f,songLineGap=14f,chordLineGap=5f,sectionGap=21f)
        assertEquals(p,PreferenceFormat.decode(PreferenceFormat.encode(p)))
    }
    @Test fun oldBackupSettingsGainCompactMarginsWithoutLosingPreferences() {
        val old=PreferenceFormat.encode(Preferences(fontSize=23))
        listOf("sheetMargin","songLineGap","chordLineGap","sectionGap").forEach { old.remove(it) }
        assertEquals(Preferences(fontSize=23),PreferenceFormat.decode(old))
    }
    @Test fun backupMarginsRejectInvalidValues() {
        listOf("sheetMargin" to -1,"songLineGap" to 25,"chordLineGap" to 13,"sectionGap" to 33).forEach { (key,value) ->
            try { PreferenceFormat.decode(PreferenceFormat.encode(Preferences()).put(key,value)); fail("Accepted $key") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun emojiSequencesAreSingleEditingUnits() {
        assertEquals(listOf(0,7),SheetLayout.boundaries("👩🏽‍🎤"))
        assertEquals(listOf(0,4,8),SheetLayout.boundaries("🇩🇪🇯🇵"))
        assertEquals(listOf(0,2),SheetLayout.boundaries("e\u0301"))
    }
    @Test fun colorfulThemesHaveReadableTextAndChordAccents() {
        listOf(ChordPalette.LightInk,ChordPalette.LightMuted,ChordPalette.LightPrimary,ChordPalette.LightSecondary,ChordPalette.LightTertiary).forEach { foreground ->
            assertTrue("Light contrast $foreground",ChordPalette.contrast(foreground,ChordPalette.LightBackground)>=4.5)
            assertTrue("Light raised surface $foreground",ChordPalette.contrast(foreground,0xFFF0EAF8)>=4.5)
        }
        listOf(ChordPalette.DarkInk,ChordPalette.DarkMuted,ChordPalette.DarkPrimary,ChordPalette.DarkSecondary,ChordPalette.DarkTertiary).forEach { foreground ->
            assertTrue("Dark contrast $foreground",ChordPalette.contrast(foreground,ChordPalette.DarkBackground)>=4.5)
            assertTrue("Dark raised surface $foreground",ChordPalette.contrast(foreground,0xFF2D283C)>=4.5)
        }
    }
}
