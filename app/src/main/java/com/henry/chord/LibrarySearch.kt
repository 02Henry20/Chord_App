package com.henry.chord

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

@Entity(tableName = "library_index", foreignKeys = [ForeignKey(entity = ArrangementRecord::class, parentColumns = ["id"], childColumns = ["id"], onDelete = ForeignKey.CASCADE)], indices = [Index("titleNorm"), Index("artistNorm"), Index("addedAt"), Index("originalKey")])
data class LibraryIndex(@PrimaryKey val id: Long, val album: String, val year: Int?, val tagsJson: String,
    val tonalMode: String?, val tempoBpm: Double?, val originalKey: String, val addedAt: Long,
    val titleNorm: String, val artistNorm: String, val albumNorm: String, val tagsNorm: String, val lyricsNorm: String)
@Fts4
@Entity(tableName = "library_grams")
data class LibraryGrams(@PrimaryKey @ColumnInfo(name = "rowid") val id: Long, val grams: String)
@Entity(tableName = "library_excerpts", foreignKeys = [ForeignKey(entity = ArrangementRecord::class, parentColumns = ["id"], childColumns = ["arrangementId"], onDelete = ForeignKey.CASCADE)], indices = [Index("arrangementId")])
data class LibraryExcerpt(@PrimaryKey val id: String, val arrangementId: Long, val position: Int, val text: String, val normalized: String)
data class LibraryHit(@Embedded val song: Song, val lyricsMatch: Boolean)
enum class LibrarySort(val title: String) { TITLE("Title"), ARTIST("Artist / title"), ADDED("Recently added"), OPENED("Recently opened"), FAVORITE("Favorites first") }
data class LibraryFilter(val favorite: Boolean = false, val artist: String = "", val tag: String = "", val key: String = "", val recent: Boolean = false)

object SearchText {
    private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
        .replace(Regex("\\p{M}+"), "").replace("ß", "ss").replace("æ", "ae").replace("œ", "oe").replace("ø", "o").replace("ł", "l").replace("đ", "d")
        .replace(Regex("['’ʼ]"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    fun normalize(text: String): String = fold(text).trim().replace(Regex(" +"), " ")
    fun terms(text: String) = normalize(text).split(' ').filter { it.isNotEmpty() }
    private fun gram(text: String) = "g" + text.codePoints().toArray().joinToString("z") { it.toString(16) }
    /** ASCII-encoded Unicode n-grams use ordinary FTS4 tokens, including 1/2-character fragments. */
    fun grams(text: String): String {
        val result = LinkedHashSet<String>()
        terms(text).forEach { word ->
            val chars = word.codePoints().toArray()
            chars.indices.forEach { end -> for (size in 1..minOf(3, end + 1)) result += "g" + (end - size + 1..end).joinToString("z") { chars[it].toString(16) } }
        }
        return result.joinToString(" ")
    }
    fun match(text: String): String = terms(text).flatMap { word ->
        val chars = word.codePoints().toArray()
        if (chars.size < 3) listOf(gram(word)) else (0..chars.size - 3).map { i -> "g" + (i..i + 2).joinToString("z") { chars[it].toString(16) } }
    }.distinct().joinToString(" ") { "\"$it\"" } // Implicit AND also works on Android builds without enhanced FTS syntax.

    /** Small overlapping chunks keep result previews bounded without changing canonical lyrics. */
    fun chunks(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        val ends = ArrayDeque<Int>(); ends.add(0)
        val chunks = mutableListOf<String>(); var emittedEnd = 0
        var end = boundaries.next()
        while (end != BreakIterator.DONE) {
            ends.addLast(end)
            if (ends.size == 241) { chunks += text.substring(ends.first(), end); emittedEnd = end; repeat(144) { ends.removeFirst() } }
            end = boundaries.next()
        }
        if (ends.last() > emittedEnd) chunks += text.substring(ends.first(), ends.last())
        return chunks
    }
    /** Return original UTF-16 ranges on complete grapheme boundaries, including decomposed accents. */
    fun highlights(text: String, query: String): List<IntRange> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        val mapped = StringBuilder(); val starts = mutableListOf<Int>(); val ends = mutableListOf<Int>()
        var start = iterator.first(); var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val folded = fold(text.substring(start, end))
            folded.forEach { char -> mapped.append(char); starts += start; ends += end }
            start = end; end = iterator.next()
        }
        val ranges = mutableListOf<IntRange>()
        terms(query).forEach { term ->
            var at = mapped.indexOf(term)
            while (at >= 0) { ranges += starts[at] until ends[at + term.length - 1]; at = mapped.indexOf(term, at + term.length) }
        }
        return ranges.distinct().sortedBy { it.first }
    }
}

object LibrarySearch {
    fun index(id: Long, doc: SongDocument, added: Long): LibraryIndex {
        val a = doc.arrangements.single()
        return LibraryIndex(id, doc.album.orEmpty(), doc.year, org.json.JSONArray(doc.tags).toString(), a.tonalMode, a.tempoBpm, KeyContext.from(a.originalKey,a.tonalMode)?.name ?: a.originalKey.orEmpty(), added,
            SearchText.normalize(doc.title), SearchText.normalize(doc.artist), SearchText.normalize(doc.album.orEmpty()), SearchText.normalize(doc.tags.joinToString(" ")), SearchText.normalize(a.lyricText()))
    }
    fun excerpts(id: Long, lyrics: String) = SearchText.chunks(lyrics).mapIndexed { i, text -> LibraryExcerpt("$id:$i", id, i, text, SearchText.normalize(text)) }
    fun grams(index: LibraryIndex) = LibraryGrams(index.id, SearchText.grams(listOf(index.titleNorm, index.artistNorm, index.albumNorm, index.tagsNorm, index.lyricsNorm).joinToString(" ")))
    fun query(query: String, filter: LibraryFilter, sort: LibrarySort, limit: Int = 100, count: Boolean = false): SimpleSQLiteQuery {
        val normalized = SearchText.normalize(query)
        val terms = SearchText.terms(query)
        val args = mutableListOf<Any>()
        val from = StringBuilder(" FROM arrangements a JOIN catalog_songs c ON c.songUuid=a.songUuid JOIN song_user_state u ON u.songUuid=a.songUuid JOIN arrangement_user_state p ON p.arrangementId=a.id JOIN library_index m ON m.id=a.id")
        val where = mutableListOf<String>()
        if (terms.isNotEmpty()) {
            from.append(" JOIN library_grams ON library_grams.rowid=a.id")
            where += "library_grams MATCH ?"; args += SearchText.match(query)
            terms.forEach { term -> where += "(instr(m.titleNorm,?)>0 OR instr(m.artistNorm,?)>0 OR instr(m.albumNorm,?)>0 OR instr(m.tagsNorm,?)>0 OR instr(m.lyricsNorm,?)>0)"; repeat(5) { args += term } }
        }
        if (filter.favorite) where += "u.favorite=1"
        if (filter.recent) where += "u.lastOpened>0"
        if (filter.artist.isNotEmpty()) { where += "m.artistNorm=?"; args += SearchText.normalize(filter.artist) }
        if (filter.tag.isNotEmpty()) { where += "a.id IN (SELECT arrangementId FROM library_tags WHERE tag=?)"; args += filter.tag }
        if (filter.key.isNotEmpty()) { where += "m.originalKey=?"; args += filter.key }
        if (where.isNotEmpty()) from.append(" WHERE " + where.joinToString(" AND "))
        if (count) return SimpleSQLiteQuery("SELECT count(*)$from", args.toTypedArray())
        val select = "SELECT a.id,c.title,c.artist,COALESCE(a.originalKey,'') AS `key`,'' AS body,u.favorite,'' AS notes,p.transpose,p.capo,u.lastOpened,'' AS source,a.songUuid,a.arrangementUuid,'' AS documentJson,a.artworkPath,a.name AS arrangementName,m.album,m.year,m.tagsJson,m.tonalMode,m.tempoBpm,m.addedAt," + if (terms.isEmpty()) "0 AS lyricsMatch" else "instr(m.lyricsNorm,?)>0 AS lyricsMatch"
        if (terms.isNotEmpty()) args.add(0, terms.first())
        val rank = if (terms.isEmpty()) "" else {
            args.addAll(listOf(normalized, normalized, normalized, normalized, normalized, normalized))
            "CASE WHEN m.titleNorm=? THEN 0 WHEN instr(m.titleNorm,?)=1 THEN 1 WHEN instr(m.titleNorm,?)>0 THEN 2 WHEN instr(m.artistNorm,?)>0 THEN 3 WHEN instr(m.tagsNorm,?)>0 OR instr(m.albumNorm,?)>0 THEN 4 ELSE 5 END,"
        }
        val order = when (sort) { LibrarySort.TITLE -> "m.titleNorm"; LibrarySort.ARTIST -> "m.artistNorm,m.titleNorm"; LibrarySort.ADDED -> "m.addedAt DESC,m.titleNorm"; LibrarySort.OPENED -> "u.lastOpened DESC,m.titleNorm"; LibrarySort.FAVORITE -> "u.favorite DESC,m.titleNorm" }
        args += limit
        return SimpleSQLiteQuery("$select$from ORDER BY $rank$order,a.id LIMIT ?", args.toTypedArray())
    }
}

@Entity(tableName = "library_tags", primaryKeys = ["arrangementId", "tag"], foreignKeys = [ForeignKey(entity = ArrangementRecord::class, parentColumns = ["id"], childColumns = ["arrangementId"], onDelete = ForeignKey.CASCADE)], indices = [Index("tag")])
data class LibraryTag(val arrangementId: Long, val tag: String)

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE library_index (id INTEGER NOT NULL PRIMARY KEY, album TEXT NOT NULL, year INTEGER, tagsJson TEXT NOT NULL, tonalMode TEXT, tempoBpm REAL, originalKey TEXT NOT NULL, addedAt INTEGER NOT NULL, titleNorm TEXT NOT NULL, artistNorm TEXT NOT NULL, albumNorm TEXT NOT NULL, tagsNorm TEXT NOT NULL, lyricsNorm TEXT NOT NULL, FOREIGN KEY(id) REFERENCES arrangements(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        listOf("titleNorm", "artistNorm", "addedAt", "originalKey").forEach { db.execSQL("CREATE INDEX index_library_index_$it ON library_index($it)") }
        db.execSQL("CREATE VIRTUAL TABLE library_grams USING FTS4(grams TEXT NOT NULL)")
        db.execSQL("CREATE TABLE library_excerpts (id TEXT NOT NULL PRIMARY KEY, arrangementId INTEGER NOT NULL, position INTEGER NOT NULL, text TEXT NOT NULL, normalized TEXT NOT NULL, FOREIGN KEY(arrangementId) REFERENCES arrangements(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_library_excerpts_arrangementId ON library_excerpts(arrangementId)")
        db.execSQL("CREATE TABLE library_tags (arrangementId INTEGER NOT NULL, tag TEXT NOT NULL, PRIMARY KEY(arrangementId,tag), FOREIGN KEY(arrangementId) REFERENCES arrangements(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_library_tags_tag ON library_tags(tag)")
        db.query("SELECT a.id,a.documentJson,c.title,c.artist,a.body,a.originalKey FROM arrangements a JOIN catalog_songs c ON c.songUuid=a.songUuid ORDER BY a.id").use { c -> while (c.moveToNext()) {
            val id = c.getLong(0)
            val doc = runCatching { Canonical.decode(c.getString(1)).single().document }.getOrElse { SheetParser.parse(c.getString(4), "recovered.cho").document.copy(title = c.getString(2), artist = c.getString(3)) }
            val m = LibrarySearch.index(id, doc, id)
            db.execSQL("INSERT INTO library_index VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf(m.id,m.album,m.year,m.tagsJson,m.tonalMode,m.tempoBpm,m.originalKey,m.addedAt,m.titleNorm,m.artistNorm,m.albumNorm,m.tagsNorm,m.lyricsNorm))
            db.execSQL("INSERT INTO library_grams(rowid,grams) VALUES (?,?)", arrayOf(id, LibrarySearch.grams(m).grams))
            LibrarySearch.excerpts(id, doc.arrangements.single().lyricText()).forEach { e -> db.execSQL("INSERT INTO library_excerpts VALUES (?,?,?,?,?)", arrayOf(e.id,e.arrangementId,e.position,e.text,e.normalized)) }
            doc.tags.distinct().forEach { db.execSQL("INSERT INTO library_tags VALUES (?,?)", arrayOf(id,it)) }
        } }
    }
}
