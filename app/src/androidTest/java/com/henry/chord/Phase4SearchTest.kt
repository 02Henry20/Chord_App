package com.henry.chord

import androidx.room.Room
import androidx.room.withTransaction
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import android.util.Log

class Phase4SearchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val migration = MigrationTestHelper(instrumentation, ChordDatabase::class.java)
    @Test fun version1And2UpgradeWithoutChangingStoredSongs() {
        val name = "phase4-migration"
        migration.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO songs VALUES (42,'Café reprise','Đường','Am','[Am]An unforgettable firefly',1,'My note',2,1,1234,'')")
            db.execSQL("INSERT INTO setlists VALUES (8,'Session')")
            db.execSQL("INSERT INTO setlist_entries VALUES (8,42,0)")
        }
        val old = migration.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).use { db -> db.query("SELECT documentJson FROM arrangements WHERE id=42").use { it.moveToFirst(); it.getString(0) } }
        migration.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT documentJson FROM arrangements WHERE id=42").use { assertTrue(it.moveToFirst()); assertEquals(old, it.getString(0)) }
            db.query("SELECT titleNorm,artistNorm,lyricsNorm FROM library_index WHERE id=42").use { assertTrue(it.moveToFirst()); assertEquals("cafe reprise",it.getString(0)); assertEquals("duong",it.getString(1)); assertTrue(it.getString(2).contains("firefly")) }
            db.query("SELECT favorite,notes,lastOpened FROM song_user_state").use { assertTrue(it.moveToFirst()); assertEquals(1,it.getInt(0)); assertEquals("My note",it.getString(1)); assertEquals(1234,it.getLong(2)) }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
    @Test fun indexedFragmentsJoinTheCorrectStableRow(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, ChordDatabase::class.java).build()
        try {
            val id = db.dao().store(SheetParser.parse("{title:Firefly}\n{artist:Local trio}\n[C]A firefly", "fixture.cho").document)
            val q = LibrarySearch.query("firefly", LibraryFilter(), LibrarySort.TITLE)
            val result = db.dao().libraryResults(q).first()
            if (result.isEmpty()) {
                println(q.sql)
                listOf("arrangements", "library_index", "library_grams", "song_user_state", "arrangement_user_state").forEach { table -> db.openHelper.writableDatabase.query("SELECT * FROM $table").use { c -> while (c.moveToNext()) println("$table: " + (0 until c.columnCount).joinToString { c.columnNames[it] + "=" + c.getString(it) }) } }
                println("QUERY MATCH: " + SearchText.match("firefly"))
            }
            assertEquals(listOf(id), result.map { it.song.id })
        } finally { db.close() }
    }
    @Test fun rankedSearchFiveThousandSongsAndTransactionalUpdates(): Unit = runBlocking {
        val base = instrumentation.targetContext
        val databaseName = "phase4-benchmark-${Canonical.uuid()}.db"
        val database = Room.databaseBuilder(base, ChordDatabase::class.java,databaseName).build()
        val dao = database.dao()
        val timings = linkedMapOf<String, Long>()
        suspend fun results(q: String, filter: LibraryFilter = LibraryFilter(), sort: LibrarySort = LibrarySort.TITLE, limit: Int = 100): List<LibraryHit> {
            val start = System.nanoTime()
            val found = dao.libraryResults(LibrarySearch.query(q, filter, sort, limit)).first()
            timings[q] = (System.nanoTime() - start) / 1_000_000
            return found
        }
        try {
            fun doc(title: String, artist: String = "Test pianist", lyric: String = "Quiet evening", album: String? = null, tags: List<String> = emptyList()): SongDocument = SheetParser.parse("{title:$title}\n{artist:$artist}\n{key:C}\n[C]$lyric", "local.cho").document.copy(album = album,tags = tags)
            database.withTransaction {
                listOf(doc("Firefly"), doc("Firefly waltz"), doc("The firefly hour"), doc("Artist study", "Firefly trio"), doc("Album study", album = "Firefly sessions"), doc("Tag study", tags = listOf("firefly")), doc("Lyric study", lyric = "A firefly lights the quiet room")).forEach { dao.store(it) }
                val verse = "Quiet windows catch the light while friends gather by the piano. We share a gentle rhythm, carry the melody together, and leave room for every voice. "
                repeat(5_000) { i -> dao.store(doc("Local study %04d".format(i), "Pianist ${i % 50}", verse.repeat(8) + "A unique lantern phrase $i gathers friends", "Café sessions", listOf("日本語", "piano"))) }
            }
            val expected = listOf("Firefly", "Firefly waltz", "The firefly hour", "Artist study", "Album study", "Tag study", "Lyric study")
            assertEquals(expected, results("firefly").map { it.song.title })
            assertEquals(expected, results("IREFL").map { it.song.title })
            assertEquals(100, results("cafe").size)
            assertEquals(5_000, dao.libraryResultCount(LibrarySearch.query("café", LibraryFilter(), LibrarySort.TITLE, count = true)).first())
            assertEquals(100, results("日本").size)
            assertEquals(100, results("an").size)
            assertEquals(100, results("a").size)
            assertTrue(results("nothing matches this phrase").isEmpty())
            val unique = results("lantern phrase 4321").single().song
            assertTrue(unique.documentJson.isEmpty() && unique.body.isEmpty())
            assertTrue(dao.excerpt(unique.id, "lantern")!!.contains("unique lantern"))
            assertEquals(100, results("", LibraryFilter(tag = "日本語")).size)
            assertEquals(100, results("", LibraryFilter(artist = "PIANIST 1")).size)
            assertEquals(100, results("", LibraryFilter(key = "C")).size)
            dao.favorite(unique.id, true); dao.markPlayed(unique.id, 99)
            assertEquals(unique.id, results("", LibraryFilter(favorite = true)).single().song.id)
            assertEquals(unique.id, results("", LibraryFilter(recent = true)).single().song.id)
            assertEquals(unique.id, results("", sort = LibrarySort.OPENED).first().song.id)
            val full = dao.song(unique.id)!!
            dao.saveEditor(full.copy(title = "Renamed étude", body = "[C]A completely new violet phrase"), "Blue album", 2026, listOf("new tag"), MusicLinks(), null)
            assertTrue(results("lantern phrase 4321").isEmpty())
            assertEquals(unique.id, results("etude").single().song.id)
            assertEquals(unique.id, results("violet").single().song.id)
            dao.clearHistory(); assertTrue(results("", LibraryFilter(recent = true)).isEmpty())
            assertTrue(dao.song(unique.id)!!.favorite)
            dao.delete(dao.song(unique.id)!!); assertTrue(results("violet").isEmpty())
            val report = org.json.JSONObject(timings.mapKeys { (key, _) -> key.ifBlank { "empty_query" } }).toString(2)
            File(base.filesDir, "phase4-search-timings.json").writeText(report)
            Log.i("Phase4Search", report)
            assertTrue("Offline search exceeded 2 seconds: $timings", timings.values.all { it < 2_000 })
        } finally { database.close(); base.deleteDatabase(databaseName) }
    }
}
