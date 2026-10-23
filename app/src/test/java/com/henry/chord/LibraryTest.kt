package com.henry.chord

import org.junit.Assert.*
import org.junit.Test

class LibraryTest {
    @Test fun explicitMinorModeAppearsInLibraryKeys() {
        val song=Song(title="Minor",body="",key="A",tonalMode="minor")
        assertEquals("Am",song.originalKeyLabel()); assertEquals("Am",song.displayKey())
        assertEquals("Am → Bm",song.copy(transpose=2).displayKey())
    }
    @Test fun normalizationAndUnicodeHighlighting() {
        assertEquals("duong dont cafe blue hour", SearchText.normalize("Đường  Don't CAFÉ — Blue–Hour"))
        val text = "A cafe\u0301 in 東京 with Straße lights"
        val ranges = SearchText.highlights(text, "CAFÉ strasse 東京")
        assertEquals(listOf("cafe\u0301", "東京", "Straße"), ranges.map { text.substring(it.first, it.last + 1) })
        assertTrue(SearchText.highlights("No match", "unheard").isEmpty())
    }
    @Test fun boundedChunksPreserveLongUnicodeMatches() {
        val original = "e\u0301 🌙 ".repeat(400) + "unique firefly fragment" + "東京 ".repeat(300)
        val chunks = SearchText.chunks(original)
        assertTrue(chunks.any { it.contains("unique firefly fragment") })
        assertTrue(chunks.all { it.length < 600 })
        assertTrue(chunks.none { it.startsWith('\u0301') || it.first().isLowSurrogate() || it.last().isHighSurrogate() })
    }
    @Test fun fragmentsAreEncodedAsSafeFtsTokens() {
        assertTrue(SearchText.grams("Évening 東京").split(' ').containsAll(SearchText.match("VEN 東京").split(' ').map { it.trim('"') }))
        assertEquals("", SearchText.match("* \" { --"))
        assertFalse(SearchText.match("love OR \"*" ).contains(" OR "))
    }
    @Test fun officialLinksValidateWithoutGuessingIdentity() {
        val id = "0123456789ABCDEFGHIJKL"
        val video = "FAKEvideo01"
        assertEquals("https://open.spotify.com/track/$id", SongLinks.valid("spotify:track:$id", LinkService.SPOTIFY))
        assertEquals("https://open.spotify.com/track/$id", SongLinks.valid("https://open.spotify.com/intl-de/track/$id?si=local", LinkService.SPOTIFY))
        for (raw in listOf("https://youtu.be/$video?t=2", "https://m.youtube.com/watch?v=$video", "https://www.youtube.com/shorts/$video", "vnd.youtube:$video")) assertEquals("https://www.youtube.com/watch?v=$video", SongLinks.valid(raw, LinkService.YOUTUBE))
        for (bad in listOf("javascript:alert(1)", "file:///private", "intent://video", "https://youtube.com.evil.test/watch?v=$video", "https://evil@youtube.com/watch?v=$video", "https://youtube.com:999/watch?v=$video", "https://youtube.com/watch?v=short", "https://youtube.com/watch?v=$video&v=$video", "https://youtube.com/watch?v=$video\nX")) assertNull(bad, SongLinks.valid(bad, LinkService.YOUTUBE))
        assertNull(SongLinks.valid("https://open.spotify.com/search/title", LinkService.SPOTIFY))
        assertTrue(SongLinks.search("Song & night", "Artist", LinkService.YOUTUBE).contains("search_query=Artist+Song+%26+night"))
    }
}
